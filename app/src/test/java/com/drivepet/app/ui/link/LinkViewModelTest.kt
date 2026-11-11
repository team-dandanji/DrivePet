package com.drivepet.app.ui.link

import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.remote.AccountService
import com.drivepet.app.data.remote.MemoryTokens
import com.drivepet.app.data.remote.OwnerStore
import com.drivepet.app.data.remote.QrApi
import com.drivepet.app.data.remote.QrStatus
import com.drivepet.app.data.remote.anonymous
import com.drivepet.app.data.remote.linked
import com.drivepet.app.data.sync.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LinkViewModelTest {
    private val sched = TestCoroutineScheduler()
    private val base = Instant.parse("2026-01-01T00:00:00Z")
    private val api = QrApi()
    private val store = OwnerStore()
    private val activated = mutableListOf<String>()
    private var uiAtActivate: LinkUi? = null
    private var uiAtSync: LinkUi? = null
    private lateinit var vm: LinkViewModel
    private var owned: List<ProfileEntity> = emptyList()

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher(sched)) }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun newVm(): LinkViewModel {
        val account = AccountService({ api }, MemoryTokens(anonymous()), store) {
            uiAtSync = vm.ui.value
            SyncOutcome.Done(0, 0, 0)
        }
        vm = LinkViewModel(account, { base.plusMillis(sched.currentTime) }, { owned }, { uiAtActivate = vm.ui.value; activated += it })
        return vm
    }

    private fun advance(ms: Long) { sched.advanceTimeBy(ms); sched.runCurrent() }

    private fun profile(id: String, account: String?, createdAt: Long, deletedAt: Long? = null) =
        ProfileEntity(id, account, "차", "EV", "STARTER_1", "펫", true, null, createdAt, null, deletedAt)

    @Test fun pollsEveryTwoSeconds() {
        newVm().start()
        advance(6_001)
        assertEquals(1, api.createCalls)
        assertEquals(3, api.statusCalls)
        assertTrue(vm.ui.value is LinkUi.Showing)
    }

    @Test fun showsRemainingSeconds() {
        newVm().start()
        advance(1)
        assertEquals(LinkUi.Showing("http://fake/qr/1", 180), vm.ui.value)
        advance(1_000)
        assertEquals(LinkUi.Showing("http://fake/qr/1", 179), vm.ui.value)
    }

    @Test fun expiresAtExpiresAt() {
        newVm().start()
        advance(179_999)
        assertTrue(vm.ui.value is LinkUi.Showing)
        advance(2)
        assertEquals(LinkUi.Expired, vm.ui.value)
        val calls = api.statusCalls
        advance(30_000)
        assertEquals(calls, api.statusCalls)
    }

    @Test fun stopsPollingWhenLinked() {
        api.script = { n -> if (n >= 2) linked() else QrStatus("PENDING") }
        newVm().start()
        advance(20_000)
        assertEquals(2, api.statusCalls)
        assertEquals(LinkUi.Linked("지우", restored = true), vm.ui.value)
    }

    @Test fun linkedWithoutSyncedProfilesIsNotRestored() {
        api.script = { linked(synced = false) }
        newVm().start()
        advance(2_001)
        assertEquals(LinkUi.Linked("지우", restored = false), vm.ui.value)
        assertTrue(activated.isEmpty())
    }

    @Test fun showsRestoringWhileSyncing() {
        api.script = { linked() }
        newVm().start()
        advance(2_001)
        assertEquals(LinkUi.Restoring("지우"), uiAtSync)
    }

    @Test fun restoredActivatesNewestOwnedProfileBeforeLinked() {
        owned = listOf(
            profile("p1", "acc_1", 1), profile("p2", "acc_1", 5), profile("p3", "acc_1", 9, deletedAt = 99),
            profile("p4", "other", 10), profile("p5", null, 11),
        )
        api.script = { linked() }
        newVm().start()
        advance(2_001)
        assertEquals(listOf("p2"), activated)
        assertTrue(uiAtActivate is LinkUi.Restoring) // 저장이 끝난 뒤에 Linked로 바뀐다
        assertEquals(LinkUi.Linked("지우", restored = true), vm.ui.value)
    }

    @Test fun retryCreatesNewSession() {
        newVm().start()
        advance(180_001)
        assertEquals(LinkUi.Expired, vm.ui.value)
        vm.retry()
        advance(1)
        assertEquals(2, api.createCalls)
        assertEquals("http://fake/qr/2", (vm.ui.value as LinkUi.Showing).qrUrl)
    }

    @Test fun retryWhileShowingStopsOldPolling() {
        newVm().start()
        advance(4_001)
        assertEquals(2, api.statusCalls)
        vm.retry()
        advance(4_001) // 새 세션 기준으로 다시 2초마다, 옛 루프는 없다
        assertEquals(4, api.statusCalls)
    }

    @Test fun startIsIdempotent() {
        newVm().start(); advance(1)
        vm.start(); advance(1)
        assertEquals(1, api.createCalls)
    }
}
