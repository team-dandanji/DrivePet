package com.drivepet.app.data.remote

import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.sync.SyncOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AccountServiceTest {
    private val api = QrApi()
    private val tokens = MemoryTokens(anonymous())
    private val store = OwnerStore()
    private var syncCalls = 0
    private var tokenAtSync: String? = null
    private var syncFails = false

    private val service = AccountService({ api }, tokens, store) {
        syncCalls++
        tokenAtSync = tokens.get().accessToken
        if (syncFails) throw IOException("offline")
        SyncOutcome.Done(0, 0, 3)
    }

    private fun profile(id: String, account: String?) = ProfileEntity(id, account, "차", "EV", "STARTER_1", "펫", true, null, 1L)

    @Test fun linkedSavesTokensAccountAndSyncs() = runBlocking {
        api.script = { linked() }
        val s = service.poll("s1")
        val t = tokens.get()
        assertEquals("LINKED", s.status)
        assertEquals("a9", t.accessToken)
        assertEquals("r9", t.refreshToken)
        assertEquals("acc_1", t.accountId)
        assertEquals("지우", t.nickname)
        assertEquals(listOf("acc_1"), store.adopted)
        assertEquals(1, syncCalls)
        assertEquals("a9", tokenAtSync) // 토큰을 먼저 저장하고 동기화한다
        assertEquals(true, s.hasSyncedProfiles)
    }

    @Test fun linkAdoptsOnlyUnowned() = runBlocking {
        store.profiles["pa"] = profile("pa", "A")
        store.profiles["pn"] = profile("pn", null)
        api.script = { linked("B") }
        service.poll("s1")
        assertEquals("A", store.profiles.getValue("pa").accountId)
        assertEquals("B", store.profiles.getValue("pn").accountId)
    }

    @Test fun pendingAndExpiredChangeNothing() = runBlocking {
        for (status in listOf("PENDING", "EXPIRED")) {
            api.script = { QrStatus(status) }
            assertEquals(status, service.poll("s1").status)
        }
        assertEquals(anonymous(), tokens.get())
        assertEquals(0, syncCalls)
        assertEquals(0, store.calls)
    }

    @Test fun downloadFailureStillLinkedButNotRestored() = runBlocking {
        syncFails = true
        api.script = { linked() }
        val s = service.poll("s1")
        assertEquals("acc_1", tokens.get().accountId)
        assertEquals(false, s.hasSyncedProfiles)
    }

    @Test fun anyPostSaveFailureStillReturnsLinked() = runBlocking {
        val svc = AccountService({ api }, tokens, store) { throw RuntimeException("boom") }
        api.script = { linked() }
        val s = svc.poll("s1")
        assertEquals("LINKED", s.status)
        assertEquals("a9", tokens.get().accessToken)
        assertEquals(false, s.hasSyncedProfiles)
    }

    @Test fun missingUserStoresNullNickname() = runBlocking {
        api.script = { linked(nick = null) }
        service.poll("s1")
        assertNull(tokens.get().nickname)
    }

    @Test fun notLinkedOutcomeIsNotRestored() = runBlocking {
        val svc = AccountService({ api }, tokens, store) { SyncOutcome.NotLinked }
        api.script = { linked() }
        assertEquals(false, svc.poll("s1").hasSyncedProfiles)
    }

    @Test fun unlinkKeepsDataAndClearsAccount() = runBlocking {
        tokens.update { it.copy(accountId = "acc_1", nickname = "지우") }
        store.profiles["pa"] = profile("pa", "acc_1")
        api.unlinkResult = UnlinkResult(false, TokenPair("u1", "ur1", 3600))
        service.unlink()
        val t = tokens.get()
        assertEquals("u1", t.accessToken)
        assertEquals("ur1", t.refreshToken)
        assertNull(t.accountId)
        assertNull(t.nickname)
        assertEquals("acc_1", store.profiles.getValue("pa").accountId)
        assertEquals(0, store.calls)
    }
}
