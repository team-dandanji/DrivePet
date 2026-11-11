package com.drivepet.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.domain.TripLedger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RecordStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: RecordStore

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        store = RoomRecordStore(db)
    }

    @After fun tearDown() = db.close()

    private fun trip(id: String) = RecordEntity(recordId = id, profileId = "p", accountId = null, kind = "TRIP", occurredAt = 1L, payloadJson = "{}")

    @Test fun insertIfAbsentStoresOnce() = runTest {
        assertTrue(store.insertIfAbsent(trip("r1")))
        assertFalse(store.insertIfAbsent(trip("r1")))
        assertEquals(1, store.records("p").first().size)
    }

    @Test fun warningsKeepFirstSeenDate() = runTest {
        store.syncWarnings("p", listOf("TPMS"), LocalDate.of(2026, 11, 10))
        store.syncWarnings("p", listOf("TPMS", "ABS"), LocalDate.of(2026, 11, 12))
        assertEquals(mapOf("TPMS" to "2026-11-10", "ABS" to "2026-11-12"), store.warningsSeen("p").first().associate { it.name to it.firstSeenDate })
        store.syncWarnings("p", emptyList(), LocalDate.of(2026, 11, 13))
        assertTrue(store.warningsSeen("p").first().isEmpty())
    }

    @Test fun tripPayloadRoundTripsToLedger() = runTest {
        val payload = TripPayload(
            startedAt = "2026-11-10T01:00:00Z", endedAt = "2026-11-10T01:30:00Z",
            startOdometerMm = 1_000_000L, endOdometerMm = 6_000_000L, distanceStatus = "VALID",
            settlement = SettlementJson(5_000_000L, "10.000", "35.000", 80, "MEASURED", null, 90, "MEASURED"),
            reflection = null,
        )
        val json = Json.encodeToString(payload)
        val at = Instant.parse("2026-11-10T01:30:00Z")
        store.insertIfAbsent(trip("t1").copy(occurredAt = at.toEpochMilli(), payloadJson = json, startMm = 1_000_000L, endMm = 6_000_000L, valid = true, bonusMilli = 35_000L))
        val row = store.records("p").first().single()
        assertEquals(payload, Json.decodeFromString<TripPayload>(row.payloadJson))
        assertEquals(TripLedger("t1", at, 1_000_000L, 6_000_000L, true, 35_000L), row.toLedger())
    }
}
