package com.drivepet.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.sync.LocalChange
import com.drivepet.app.data.sync.RoomSyncStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var sync: RoomSyncStore
    private lateinit var store: RoomRecordStore

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        sync = RoomSyncStore(db)
        store = RoomRecordStore(db)
    }

    @After fun tearDown() = db.close()

    private fun profile(id: String, account: String?, revision: Long? = 1, battery: Double? = 77.4, nick: String = "모모") = ProfileEntity(
        profileId = id, accountId = account, vehicleName = "차", fuelType = "EV", starterType = "STARTER_1",
        petNickname = nick, onboardingCompleted = true, batteryKwh = battery, createdAt = 100, stateRevision = revision,
    )

    private fun record(id: String, profileId: String, account: String?, at: Long = 1) = RecordEntity(
        recordId = id, profileId = profileId, accountId = account, kind = "ATTENDANCE", occurredAt = at, payloadJson = "{}",
    )

    @Test fun pendingRecordsOnlyForAccount() = runTest {
        store.upsertProfile(profile("p", "A"))
        store.insertIfAbsent(record("a", "p", "A"))
        store.insertIfAbsent(record("b", "p", "B"))
        store.insertIfAbsent(record("n", "p", null))
        assertEquals(listOf("a"), sync.pendingRecords("A", 10).map { it.recordId })
        assertEquals(1, sync.pendingCount("A").first())
    }

    @Test fun recordsWaitForProfileCreation() = runTest {
        store.upsertProfile(profile("p", "A", revision = null))
        store.insertIfAbsent(record("r", "p", "A"))
        assertTrue(sync.pendingRecords("A", 10).isEmpty())
        assertEquals(listOf("p"), sync.profilesToCreate("A").map { it.profileId })
        assertEquals(1, sync.pendingCount("A").first()) // 프로필 1, 기록은 아직 대기
        sync.markProfileCreated("p", 3)
        assertEquals(listOf("r"), sync.pendingRecords("A", 10).map { it.recordId })
        assertTrue(sync.profilesToCreate("A").isEmpty())
        assertEquals(3L, store.profiles().first().single().stateRevision)
    }

    @Test fun markRecordsRemovesFromPending() = runTest {
        store.upsertProfile(profile("p", "A"))
        store.insertIfAbsent(record("r1", "p", "A", at = 1))
        store.insertIfAbsent(record("r2", "p", "A", at = 2))
        sync.markRecords(mapOf("r1" to "SYNCED"))
        assertEquals(listOf("r2"), sync.pendingRecords("A", 10).map { it.recordId })
    }

    @Test fun adoptAssignsOnlyUnowned() = runTest {
        store.upsertProfile(profile("pn", null))
        store.upsertProfile(profile("pa", "A"))
        store.insertIfAbsent(record("rn", "pn", null))
        store.insertIfAbsent(record("ra", "pa", "A"))
        sync.adoptUnowned("B")
        assertEquals(mapOf("pn" to "B", "pa" to "A"), store.profiles().first().associate { it.profileId to it.accountId })
        assertEquals("B", store.records("pn").first().single().accountId)
        assertEquals("A", store.records("pa").first().single().accountId)
    }

    @Test fun deletedProfileHidden() = runTest {
        store.upsertProfile(profile("p", "A"))
        store.insertIfAbsent(record("r", "p", "A"))
        sync.applyPage("A", listOf(LocalChange.DeleteProfile("p", 50)), "c1")
        assertTrue(store.profiles().first().isEmpty())
        assertTrue(sync.pendingRecords("A", 10).isEmpty())
        assertEquals("c1", sync.cursor("A"))
    }

    @Test fun upsertKeepsLocalOnlyFields() = runTest {
        store.upsertProfile(profile("p", "A", battery = 77.4).copy(createdAt = 5))
        sync.applyPage("A", listOf(LocalChange.UpsertProfile(profile("p", "A", battery = null, nick = "새이름").copy(createdAt = 999))), "c")
        val p = store.profiles().first().single()
        assertEquals(77.4, p.batteryKwh!!, 0.0)
        assertEquals(5L, p.createdAt)
        assertEquals("새이름", p.petNickname)
    }

    @Test fun applyPageTwiceSameResult() = runTest {
        val changes = listOf(
            LocalChange.UpsertProfile(profile("p", "A")),
            LocalChange.InsertRecord(record("r", "p", "A").copy(syncState = "SYNCED")),
        )
        repeat(2) { sync.applyPage("A", changes, "c") }
        assertEquals(1, store.records("p").first().size)
        assertEquals(1, store.profiles().first().size)
        assertEquals("c", sync.cursor("A"))
        assertNull(sync.cursor("B"))
    }
}
