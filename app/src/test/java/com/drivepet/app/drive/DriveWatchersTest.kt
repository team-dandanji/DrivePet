package com.drivepet.app.drive

import com.drivepet.app.data.AttendancePayload
import com.drivepet.app.data.FakeRecordStore
import com.drivepet.vehicle.model.DriveEvent.*
import com.drivepet.vehicle.model.ExitItem
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.VehicleState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun state(
    gear: Gear? = null,
    belt: Signal<Boolean> = Signal.Unknown,
    window: Int? = null,
    lowBeam: Boolean? = null,
    highBeam: Boolean? = null,
    locked: Signal<Boolean> = Signal.Unknown,
) = VehicleState(
    gear = gear?.let { Signal.Ok(it, 0) } ?: Signal.Unknown,
    driverBeltBuckled = belt,
    driverWindow = window?.let { Signal.Ok(it, 0) } ?: Signal.Unknown,
    lowBeam = lowBeam?.let { Signal.Ok(it, 0) } ?: Signal.Unknown,
    highBeam = highBeam?.let { Signal.Ok(it, 0) } ?: Signal.Unknown,
    doorsLocked = locked,
)

private fun belt(v: Boolean) = state(belt = Signal.Ok(v, 0))

class DriveWatchersTest {
    private var now = 0L
    private val f = ReactionFilter({ now })

    @Test fun `reactions ignored for 3s after source start`() {
        assertTrue(f.allowReaction(HeadlightOn(0))) // 시작 전에는 모두 허용
        f.sourceStarted()
        assertFalse(f.allowReaction(HeadlightOn(0)))
        assertFalse(f.allowReaction(WindowOpened(5, 0)))
        assertFalse(f.allowReaction(VehicleWarning(listOf("x"), 0)))
        assertFalse(f.allowReaction(DriverDoorOpened(true, 0)))
        assertTrue(f.allowReaction(TripStarted(1.0, 0)))
        now = 2_999
        assertFalse(f.allowReaction(HeadlightOn(2_999)))
        now = 3_000
        assertTrue(f.allowReaction(HeadlightOn(3_000)))
    }

    private val store = FakeRecordStore()
    private var instant = Instant.parse("2026-11-10T00:00:00Z")
    private val w = AttendanceWatcher(store, { instant })

    @Test fun `belt checks in once per day`() = runTest {
        assertFalse(w.onState("p", "acc", belt(false)))
        assertTrue(w.onState("p", "acc", belt(true)))
        assertFalse(w.onState("p", "acc", belt(true)))
        val rec = store.records("p").first().single()
        assertEquals("ATTENDANCE", rec.kind)
        assertEquals("2026-11-10", rec.localDate)
        assertEquals("acc", rec.accountId)
        assertEquals("2026-11-10", Json.decodeFromString<AttendancePayload>(rec.payloadJson).localDate)
        instant = instant.plusSeconds(86_400) // 2026-11-11 09:00 KST
        assertTrue(w.onState("p", "acc", belt(true)))
        assertEquals(2, store.records("p").first().size)
    }

    @Test fun `seoul midnight splits attendance days`() = runTest {
        instant = Instant.parse("2026-11-10T14:59:00Z") // KST 11-10 23:59
        assertTrue(w.onState("p", "acc", belt(true)))
        instant = Instant.parse("2026-11-10T15:01:00Z") // KST 11-11 00:01
        assertTrue(w.onState("p", "acc", belt(true)))
        assertEquals(listOf("2026-11-10", "2026-11-11"), store.records("p").first().map { it.localDate })
    }

    @Test fun `manual check in only when belt unreadable and parked`() = runTest {
        assertFalse(w.manualCheckIn("p", "acc", state(gear = Gear.D)))
        assertTrue(w.manualCheckIn("p", "acc", state(gear = Gear.P)))
        assertFalse(w.manualCheckIn("p", "acc", state(gear = Gear.P)))
    }

    @Test fun `belt stuck at false stays unreadable and manual check in works`() = runTest {
        val stuck = state(belt = Signal.Ok(false, 0), gear = Gear.P)
        repeat(3) { assertFalse(w.onState("p", "acc", stuck)) }
        assertFalse(w.beltReadable.value) // 버튼이 계속 보인다
        assertTrue(w.manualCheckIn("p", "acc", stuck))
        assertEquals(1, store.records("p").first().size)
    }

    @Test fun `manual check in blocked once belt was buckled`() = runTest {
        w.onState("p", "acc", belt(true))
        assertTrue(w.beltReadable.value)
        assertFalse(w.manualCheckIn("p", "acc", state(gear = Gear.P)))
        w.onState("p", "acc", belt(false))
        w.onState("p", "acc", state())
        assertTrue(w.beltReadable.value)
    }

    private val t = ExitCheckTracker()

    @Test fun `exit check resolves live`() {
        t.onEvent(PreExitCheck(listOf(ExitItem.WINDOW_OPEN, ExitItem.HEADLIGHT_ON), 0))
        assertFalse(t.open.value!!.allResolved)
        t.onState(state(window = 0, lowBeam = false, highBeam = false, locked = Signal.Unknown))
        assertTrue(t.open.value!!.allResolved)
        assertTrue(t.open.value!!.lockUnknown)
    }

    @Test fun `unknown lock keeps doors unresolved and resolved items stay`() {
        t.onEvent(PreExitCheck(listOf(ExitItem.WINDOW_OPEN, ExitItem.DOORS_UNLOCKED), 0))
        t.onState(state(window = 0))
        assertFalse(t.open.value!!.allResolved)
        assertTrue(t.open.value!!.lockUnknown)
        t.onState(state(window = 5, locked = Signal.Ok(true, 0)))
        assertTrue(t.open.value!!.allResolved)
        assertFalse(t.open.value!!.lockUnknown)
    }

    @Test fun `lockUnknown follows latest doorsLocked`() {
        t.onEvent(PreExitCheck(listOf(ExitItem.DOORS_UNLOCKED), 0))
        t.onState(state())
        assertTrue(t.open.value!!.lockUnknown)
        t.onState(state(locked = Signal.Ok(true, 0)))
        assertFalse(t.open.value!!.lockUnknown)
        t.onState(state())
        assertTrue(t.open.value!!.lockUnknown)
        assertTrue(t.open.value!!.allResolved) // 이미 해결된 항목은 유지
    }

    @Test fun `dismiss closes check and ignores later state until new check`() {
        t.onEvent(PreExitCheck(listOf(ExitItem.WINDOW_OPEN), 0))
        t.dismiss()
        assertNull(t.open.value)
        t.onState(state(window = 0))
        assertNull(t.open.value)
        t.onEvent(PreExitCheck(listOf(ExitItem.WINDOW_OPEN), 1))
        assertFalse(t.open.value!!.allResolved)
    }

    @Test fun `gear leaving P closes check and new check replaces old`() {
        t.onEvent(PreExitCheck(listOf(ExitItem.WINDOW_OPEN), 0))
        t.onEvent(PreExitCheck(listOf(ExitItem.HEADLIGHT_ON), 1))
        assertEquals(setOf(ExitItem.HEADLIGHT_ON), t.open.value!!.items.keys)
        t.onState(state(gear = Gear.D))
        assertNull(t.open.value)
    }
}
