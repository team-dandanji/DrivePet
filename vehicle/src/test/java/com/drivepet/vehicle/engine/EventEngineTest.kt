package com.drivepet.vehicle.engine

import com.drivepet.vehicle.model.DriveEvent
import com.drivepet.vehicle.model.ExitItem
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.TurnSide
import com.drivepet.vehicle.model.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Android Studio에서 이 파일을 열고 클래스 옆 ▶ 를 누르면 실행된다(에뮬레이터 불필요). */
class EventEngineTest {

    private lateinit var engine: EventEngine
    private var state = VehicleState()
    private var now = 1_000_000L
    private val events = mutableListOf<DriveEvent>()

    @Before
    fun setUp() {
        engine = EventEngine(RuleConfig())
        state = VehicleState(
            gear = Signal.Ok(Gear.P, now),
            odometerKm = Signal.Ok(100.0, now),
            steeringDeg = Signal.Ok(0.0, now),
            brakePressed = Signal.Ok(false, now),
            turnSignal = Signal.Ok(TurnSide.OFF, now),
            driverDoorOpen = Signal.Ok(false, now),
            driverWindow = Signal.Ok(0, now),
            lowBeam = Signal.Ok(false, now),
            highBeam = Signal.Ok(false, now),
            driverBeltBuckled = Signal.Ok(true, now),
        )
        events.clear()
    }

    private fun change(afterMs: Long = 0, patch: (VehicleState) -> VehicleState) {
        now += afterMs
        val next = patch(state)
        events += engine.onState(state, next, now)
        state = next
    }

    private fun wait(ms: Long) {
        val end = now + ms
        while (now < end) {
            now += 1_000
            events += engine.tick(state, now)
        }
    }

    private fun gear(g: Gear, afterMs: Long = 0) = change(afterMs) { it.copy(gear = Signal.Ok(g, now)) }
    private fun steer(deg: Double, afterMs: Long) = change(afterMs) { it.copy(steeringDeg = Signal.Ok(deg, now)) }
    private inline fun <reified T : DriveEvent> only(): List<T> = events.filterIsInstance<T>()

    @Test
    fun `출발하면 TripStarted, P 5초 유지 후 TripEnded와 거리`() {
        gear(Gear.D)
        assertEquals(100.0, only<DriveEvent.TripStarted>().single().odometerKm!!, 0.0)

        change(60_000) { it.copy(odometerKm = Signal.Ok(112.0, now)) }
        gear(Gear.P)
        wait(4_000)
        assertTrue(only<DriveEvent.TripEnded>().isEmpty())

        wait(2_000)
        val trip = only<DriveEvent.TripEnded>().single().trip
        assertEquals(12.0, trip.distanceKm!!, 0.0001)
        assertNull(engine.currentTrip())
    }

    @Test
    fun `잠깐 P를 거쳤다 다시 출발하면 같은 주행`() {
        gear(Gear.D)
        gear(Gear.P, afterMs = 40_000)
        wait(2_000)
        gear(Gear.D)
        assertEquals(1, only<DriveEvent.TripStarted>().size)
        wait(10_000)
        assertTrue(only<DriveEvent.TripEnded>().isEmpty())
    }

    @Test
    fun `30초 미만 주행은 기록하지 않음`() {
        gear(Gear.D)
        gear(Gear.P, afterMs = 10_000)
        wait(10_000)
        assertTrue(only<DriveEvent.TripEnded>().isEmpty())
    }

    @Test
    fun `1초 안에 90도 이상 꺾으면 급조향`() {
        gear(Gear.D)
        steer(-40.0, 300)
        steer(60.0, 300)
        val sharp = only<DriveEvent.SharpSteer>()
        assertEquals(1, sharp.size)
        assertEquals(100.0, sharp.single().deltaDeg, 0.0)
        assertEquals(1, engine.currentTrip()!!.sharpSteerCount)
    }

    @Test
    fun `천천히 꺾으면 급조향 아님`() {
        gear(Gear.D)
        steer(30.0, 800)
        steer(60.0, 800)
        steer(90.0, 800)
        assertTrue(only<DriveEvent.SharpSteer>().isEmpty())
    }

    @Test
    fun `방향지시등 없이 크게 꺾으면 TurnWithoutSignal, 켜고 꺾으면 매너 OK`() {
        gear(Gear.D)
        steer(50.0, 2_000)
        assertEquals(1, only<DriveEvent.TurnWithoutSignal>().size)
        steer(0.0, 2_000)

        change(2_000) { it.copy(turnSignal = Signal.Ok(TurnSide.LEFT, now)) }
        change(1_000) { it.copy(turnSignal = Signal.Ok(TurnSide.OFF, now)) }
        steer(-60.0, 2_000)
        assertEquals(1, only<DriveEvent.TurnWithoutSignal>().size)

        val trip = engine.currentTrip()!!
        assertEquals(2, trip.bigTurnCount)
        assertEquals(1, trip.turnWithoutSignalCount)
        assertEquals(1, trip.turnSignalCount)
    }

    @Test
    fun `주행 중 브레이크 밟은 시간 기록`() {
        gear(Gear.D)
        change(1_000) { it.copy(brakePressed = Signal.Ok(true, now)) }
        change(1_500) { it.copy(brakePressed = Signal.Ok(false, now)) }
        assertEquals(1_500, only<DriveEvent.BrakeReleased>().single().holdMs)
        assertEquals(1, engine.currentTrip()!!.brakeCount)
    }

    @Test
    fun `P에서 문 열면 하차 점검, 창문·전조등 켜짐 알려 줌`() {
        change { it.copy(driverWindow = Signal.Ok(3, now), lowBeam = Signal.Ok(true, now)) }
        change(1_000) { it.copy(driverDoorOpen = Signal.Ok(true, now)) }
        val check = only<DriveEvent.PreExitCheck>().single()
        assertEquals(listOf(ExitItem.WINDOW_OPEN, ExitItem.HEADLIGHT_ON), check.items)
        assertTrue(only<DriveEvent.DriverDoorOpened>().single().parked)
    }

    @Test
    fun `벨트 안 매고 출발하면 BeltReminder`() {
        change { it.copy(driverBeltBuckled = Signal.Ok(false, now)) }
        gear(Gear.D)
        assertEquals(1, only<DriveEvent.BeltReminder>().size)
    }

    @Test
    fun `벨트 값을 못 읽으면 BeltReminder 없음`() {
        change { it.copy(driverBeltBuckled = Signal.Unsupported) }
        gear(Gear.D)
        assertTrue(only<DriveEvent.BeltReminder>().isEmpty())
    }

    @Test
    fun `출발 때 거리를 못 받았으면 처음 받은 값으로 보정`() {
        change { it.copy(odometerKm = Signal.Unknown) }
        gear(Gear.D)
        assertNull(only<DriveEvent.TripStarted>().single().odometerKm)
        change(1_000) { it.copy(odometerKm = Signal.Ok(200.0, now)) }
        change(60_000) { it.copy(odometerKm = Signal.Ok(205.0, now)) }
        gear(Gear.P)
        wait(6_000)
        assertEquals(5.0, only<DriveEvent.TripEnded>().single().trip.distanceKm!!, 0.0001)
    }

    @Test
    fun `주행 중 한 번도 못 받은 데이터는 missingData`() {
        change { it.copy(steeringDeg = Signal.Unsupported) }
        gear(Gear.D)
        gear(Gear.P, afterMs = 60_000)
        wait(6_000)
        val trip = only<DriveEvent.TripEnded>().single().trip
        assertTrue(EventEngine.DATA_STEERING in trip.missingData)
        assertTrue(EventEngine.DATA_ODOMETER !in trip.missingData)
    }
}
