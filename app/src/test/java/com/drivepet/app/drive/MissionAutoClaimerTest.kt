package com.drivepet.app.drive

import com.drivepet.app.data.FakeRecordStore
import com.drivepet.app.data.SafetyLineJson
import com.drivepet.app.data.SettlementJson
import com.drivepet.app.data.TripPayload
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.domain.MissionKind
import com.drivepet.app.domain.dailyMissions
import com.drivepet.vehicle.VehicleModule.Mode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class MissionAutoClaimerTest {
    private val store = FakeRecordStore()
    // 출석 미션이 뽑힌 날짜를 찾는다
    private val date = (0L..60L).map { LocalDate.of(2026, 11, 1).plusDays(it) }.first { MissionKind.ATTENDANCE in dailyMissions("p", it) }
    private val now = date.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()
    private val claimer = MissionAutoClaimer(store, { now })

    private suspend fun attend() {
        store.insertIfAbsent(RecordEntity("att1", "p", null, "ATTENDANCE", now.toEpochMilli(), payloadJson = "{}", localDate = date.toString()))
    }

    @Test fun `claims done mission once`() = runTest {
        attend()
        assertEquals(1, claimer.claimDone("p", null))
        val c = store.records("p").first().single { it.kind == "MISSION_CLAIM" }
        assertEquals("daily-attendance", c.missionId)
        assertEquals(30_000L, c.rewardMilli)
        assertEquals(UUID.nameUUIDFromBytes("mission:p:daily-attendance:$date".toByteArray()).toString(), c.recordId)
    }

    @Test fun `second call claims nothing`() = runTest {
        attend()
        claimer.claimDone("p", null)
        val before = store.records("p").first().size
        assertEquals(0, claimer.claimDone("p", null))
        assertEquals(before, store.records("p").first().size)
    }

    @Test fun `undone mission not claimed`() = runTest {
        assertEquals(0, claimer.claimDone("p", null))
        assertTrue(store.records("p").first().isEmpty())
    }

    @Test fun `trip-claimed slot is not claimed again as smooth accel`() = runTest {
        val d = (0L..60L).map { LocalDate.of(2026, 11, 1).plusDays(it) }.first { MissionKind.SMOOTH_ACCEL in dailyMissions("p", it) }
        val t = d.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()
        val c = MissionAutoClaimer(store, { t })
        fun trip(id: String, accel: Boolean) = RecordEntity(
            id, "p", null, "TRIP", t.toEpochMilli(), valid = true,
            payloadJson = Json.encodeToString(
                TripPayload(
                    "s", "e", 0, 1_000_000, "VALID",
                    SettlementJson(
                        1_000_000, "1.000", "1.000", 80, "MEASURED",
                        listOfNotNull(
                            SafetyLineJson("STEERING", 10, 10, "MEASURED", 0.0),
                            if (accel) SafetyLineJson("ACCEL_BRAKE", 36, 36, "MEASURED", null) else null,
                        ),
                        null, "UNMEASURED",
                    ),
                ),
            ),
        )
        store.insertIfAbsent(trip("a", accel = false))
        c.claimDone("p", null)
        assertEquals(listOf("daily-no-sharp-steer-trip"), store.records("p").first().filter { it.kind == "MISSION_CLAIM" }.map { it.missionId })
        store.insertIfAbsent(trip("b", accel = true))
        assertEquals(0, c.claimDone("p", null))
        assertFalse(store.records("p").first().any { it.missionId == "daily-smooth-accel" })
    }
}

class ShouldAutoCheckInTest {
    @Test fun `real, belt unreadable, parked checks in`() = assertTrue(shouldAutoCheckIn(Mode.REAL, false, true))
    @Test fun `fake does not`() = assertFalse(shouldAutoCheckIn(Mode.FAKE, false, true))
    @Test fun `replay does not`() = assertFalse(shouldAutoCheckIn(Mode.REPLAY, false, true))
    @Test fun `readable belt does not`() = assertFalse(shouldAutoCheckIn(Mode.REAL, true, true))
    @Test fun `driving does not`() = assertFalse(shouldAutoCheckIn(Mode.REAL, false, false))
    @Test fun `unknown mode does not`() = assertFalse(shouldAutoCheckIn(null, false, true))
}
