package com.drivepet.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MissionTest {
    private val day = LocalDate.of(2026, 11, 11)

    private fun trip(
        id: String, km: Double? = 3.0, valid: Boolean = true, safety: Int? = 80, steers: Int? = 0,
        accelFull: Boolean? = null, signal: Double? = null,
    ) = MissionTrip(id, valid, km?.let { (it * 1_000_000).toLong() }, safety, steers, accelFull, signal)

    @Test fun sameProfileAndDateGiveSameThreeMissions() {
        val a = dailyMissions("p1", day)
        assertEquals(a, dailyMissions("p1", day))
        assertEquals(3, a.size)
        assertEquals(3, a.toSet().size)
        assertFalse(MissionKind.NO_SHARP_STEER_TRIP in a)
    }

    @Test fun missionsFollowSeoulDate() {
        val before = seoulDate(Instant.parse("2026-11-11T14:59:00Z"))
        val after = seoulDate(Instant.parse("2026-11-11T15:01:00Z"))
        assertEquals(LocalDate.of(2026, 11, 11), before)
        assertEquals(LocalDate.of(2026, 11, 12), after)
        assertEquals(dailyMissions("p1", LocalDate.of(2026, 11, 11)), dailyMissions("p1", before))
        assertEquals(dailyMissions("p1", LocalDate.of(2026, 11, 12)), dailyMissions("p1", after))
        // 날마다 바뀐다(일주일 안에 적어도 한 번은 다른 조합)
        assertNotEquals(1, (0L..6L).map { dailyMissions("p1", day.plusDays(it)).toSet() }.toSet().size)
    }

    @Test fun noSharpSteer5kmSumsValidTrips() {
        val done = missionStatus(MissionKind.NO_SHARP_STEER_5KM, listOf(trip("a", 3.0), trip("b", 2.0)), null, emptySet())
        assertTrue(done.done)
        assertEquals(listOf("a", "b"), done.evidence)
        val notDone = missionStatus(MissionKind.NO_SHARP_STEER_5KM, listOf(trip("a", 3.0), trip("b", 4.0, steers = 1)), null, emptySet())
        assertFalse(notDone.done)
        assertEquals("3.0 / 5km", notDone.progress)
    }

    @Test fun smoothAccelFallsBackWhenSpeedUnmeasured() {
        val s = missionStatus(MissionKind.SMOOTH_ACCEL, listOf(trip("a", accelFull = null, steers = 0)), null, emptySet())
        assertEquals(MissionKind.NO_SHARP_STEER_TRIP, s.kind)
        assertTrue(s.done)
        val none = missionStatus(MissionKind.SMOOTH_ACCEL, emptyList(), null, emptySet())
        assertEquals(MissionKind.SMOOTH_ACCEL, none.kind)
        assertFalse(none.done)
        assertTrue(missionStatus(MissionKind.SMOOTH_ACCEL, listOf(trip("a", accelFull = true)), null, emptySet()).done)
    }

    @Test fun safety90IgnoresNullScore() {
        assertFalse(missionStatus(MissionKind.SAFETY_90, listOf(trip("a", safety = null)), null, emptySet()).done)
        assertTrue(missionStatus(MissionKind.SAFETY_90, listOf(trip("a", safety = 92)), null, emptySet()).done)
        assertFalse(missionStatus(MissionKind.SAFETY_90, listOf(trip("a", safety = 95, valid = false)), null, emptySet()).done)
    }

    @Test fun turnSignalNeeds100() {
        assertFalse(missionStatus(MissionKind.TURN_SIGNAL, listOf(trip("a", signal = 88.0)), null, emptySet()).done)
        assertTrue(missionStatus(MissionKind.TURN_SIGNAL, listOf(trip("a", signal = 100.0)), null, emptySet()).done)
        assertFalse(missionStatus(MissionKind.TURN_SIGNAL, listOf(trip("a", signal = null)), null, emptySet()).done)
    }

    @Test fun attendanceAndClaimedFlag() {
        val s = missionStatus(MissionKind.ATTENDANCE, emptyList(), "att1", setOf("daily-attendance"))
        assertTrue(s.done)
        assertTrue(s.claimed)
        assertEquals(listOf("att1"), s.evidence)
        assertFalse(missionStatus(MissionKind.ATTENDANCE, emptyList(), null, emptySet()).done)
    }
}
