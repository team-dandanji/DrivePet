package com.drivepet.vehicle.engine

import com.drivepet.vehicle.model.DriveEvent
import com.drivepet.vehicle.model.ExitItem
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.TripSummary
import com.drivepet.vehicle.model.TurnSide
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull
import kotlin.math.abs

/** 이벤트 기준 숫자. 기획과 상의해 여기만 바꾸면 된다. */
data class RuleConfig(
    /** 급조향: [sharpSteerWindowMs] 안에 조향각이 이만큼 이상 변하면. */
    val sharpSteerDeg: Double = 90.0,
    val sharpSteerWindowMs: Long = 1_000,
    /** 주행 종료: P가 이 시간 이상 유지되면(잠깐 P를 거치는 경우 무시). */
    val tripEndHoldMs: Long = 5_000,
    /** 이보다 짧은 주행은 기록하지 않음(실수로 D를 넣은 경우). */
    val minTripMs: Long = 30_000,
    /** '크게 꺾음' 기준 조향각(절댓값). */
    val bigTurnDeg: Double = 45.0,
    /** 크게 꺾기 전 이 시간 안에 방향지시등이 켜졌으면 매너 OK. */
    val signalLookbackMs: Long = 5_000,
    /** 잔량 부족 기준(%). */
    val lowEnergyPct: Int = 20,
)

/**
 * 차 상태 변화를 [DriveEvent]로 바꾸는 규칙 엔진.
 * Android에 의존하지 않는 순수 Kotlin이라 단위 테스트로 검증한다(src/test).
 * 스레드 안전하지 않으므로 호출하는 쪽에서 한 번에 하나씩 부른다.
 */
class EventEngine(private val config: RuleConfig = RuleConfig()) {

    private var trip: TripSummary? = null
    private var parkedSince: Long? = null
    private val tripSeen = mutableSetOf<String>()
    private val steerSamples = ArrayDeque<Pair<Long, Double>>()
    private var lastSharpSteerAt = Long.MIN_VALUE / 2
    private var lastSignalOnAt = Long.MIN_VALUE / 2
    private var brakeDownAt: Long? = null
    private var inBigTurn = false

    /** 진행 중인 주행(없으면 null). */
    fun currentTrip(): TripSummary? = trip

    fun reset() {
        trip = null
        parkedSince = null
        tripSeen.clear()
        steerSamples.clear()
        brakeDownAt = null
        inBigTurn = false
    }

    /** 상태가 [prev] → [next]로 바뀌었을 때 호출. */
    fun onState(prev: VehicleState, next: VehicleState, now: Long): List<DriveEvent> {
        val out = mutableListOf<DriveEvent>()
        gear(prev, next, now, out)
        trackQuality(next)
        backfillStartOdometer(next)
        steering(next, now, out)
        turnSignal(prev, next, now, out)
        brake(prev, next, now, out)
        window(prev, next, now, out)
        headlight(prev, next, now, out)
        door(prev, next, now, out)
        uxRestriction(prev, next, now, out)
        energy(prev, next, now, out)
        warnings(prev, next, now, out)
        return out
    }

    /** 1초마다 호출. 시간이 지나야 결정되는 이벤트(주행 종료)를 처리한다. */
    fun tick(state: VehicleState, now: Long): List<DriveEvent> {
        val since = parkedSince ?: return emptyList()
        val current = trip ?: return emptyList()
        if (now - since < config.tripEndHoldMs) return emptyList()

        parkedSince = null
        val ended = current.copy(
            endedAt = since,
            endOdometerKm = state.odometerKm.valueOrNull() ?: current.endOdometerKm,
            missingData = missingNames(),
        )
        trip = null
        tripSeen.clear()
        return if ((ended.durationMs ?: 0) >= config.minTripMs) listOf(DriveEvent.TripEnded(ended, now)) else emptyList()
    }

    // ---------------- 규칙 ----------------

    private fun gear(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val before = prev.gear.valueOrNull()
        val after = next.gear.valueOrNull() ?: return
        if (before == after) return

        val driving = after == Gear.D || after == Gear.R
        when {
            // 주행 중 잠깐 P를 거쳤다가 다시 출발 → 같은 주행으로 이어 감
            driving && trip != null -> parkedSince = null

            driving && trip == null -> {
                val odo = next.odometerKm.valueOrNull()
                trip = TripSummary(startedAt = now, endedAt = null, startOdometerKm = odo, endOdometerKm = odo)
                tripSeen.clear()
                parkedSince = null
                out += DriveEvent.TripStarted(odo, now)
                if (next.driverBeltBuckled.valueOrNull() == false) out += DriveEvent.BeltReminder(now)
            }

            after == Gear.P && trip != null -> parkedSince = now
        }
    }

    private fun trackQuality(s: VehicleState) {
        if (trip == null) return
        fun seen(name: String, sig: Signal<*>) { if (sig is Signal.Ok<*>) tripSeen += name }
        seen(DATA_ODOMETER, s.odometerKm)
        seen(DATA_STEERING, s.steeringDeg)
        seen(DATA_BRAKE, s.brakePressed)
        seen(DATA_TURN_SIGNAL, s.turnSignal)
    }

    private fun missingNames(): Set<String> = TRACKED_DATA - tripSeen

    private fun backfillStartOdometer(s: VehicleState) {
        val t = trip ?: return
        val odo = s.odometerKm.valueOrNull() ?: return
        trip = t.copy(
            startOdometerKm = t.startOdometerKm ?: odo,
            endOdometerKm = odo,
        )
    }

    private fun steering(next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val deg = next.steeringDeg.valueOrNull() ?: return
        if (!next.isDriving || trip == null) {
            steerSamples.clear()
            inBigTurn = false
            return
        }

        // 급조향: 최근 window 안의 최대·최소 차이
        steerSamples.addLast(now to deg)
        while (steerSamples.isNotEmpty() && now - steerSamples.first().first > config.sharpSteerWindowMs) {
            steerSamples.removeFirst()
        }
        val maxDeg = steerSamples.maxOf { it.second }
        val minDeg = steerSamples.minOf { it.second }
        val delta = maxDeg - minDeg
        if (delta >= config.sharpSteerDeg && now - lastSharpSteerAt > config.sharpSteerWindowMs) {
            lastSharpSteerAt = now
            out += DriveEvent.SharpSteer(delta, now)
            trip = trip?.let { it.copy(sharpSteerCount = it.sharpSteerCount + 1) }
        }

        // 크게 꺾음: 기준선을 넘는 순간 한 번만 판단
        val big = abs(deg) >= config.bigTurnDeg
        if (big && !inBigTurn) {
            val signalled = next.turnSignal.valueOrNull().let { it == TurnSide.LEFT || it == TurnSide.RIGHT } ||
                now - lastSignalOnAt <= config.signalLookbackMs
            trip = trip?.let {
                it.copy(
                    bigTurnCount = it.bigTurnCount + 1,
                    turnWithoutSignalCount = it.turnWithoutSignalCount + if (signalled) 0 else 1,
                )
            }
            if (!signalled) out += DriveEvent.TurnWithoutSignal(deg, now)
        }
        inBigTurn = big
    }

    private fun turnSignal(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val before = prev.turnSignal.valueOrNull()
        val after = next.turnSignal.valueOrNull() ?: return
        if (after == TurnSide.OFF || before == after) return
        lastSignalOnAt = now
        out += DriveEvent.TurnSignalOn(after, now)
        if (next.isDriving) trip = trip?.let { it.copy(turnSignalCount = it.turnSignalCount + 1) }
    }

    private fun brake(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val before = prev.brakePressed.valueOrNull()
        val after = next.brakePressed.valueOrNull() ?: return
        if (before == after) return
        if (after) {
            brakeDownAt = now
            return
        }
        val down = brakeDownAt ?: return
        brakeDownAt = null
        if (!next.isDriving || trip == null) return
        val hold = now - down
        out += DriveEvent.BrakeReleased(hold, now)
        trip = trip?.let { it.copy(brakeCount = it.brakeCount + 1, brakeHoldTotalMs = it.brakeHoldTotalMs + hold) }
    }

    private fun window(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val before = prev.driverWindow.valueOrNull() ?: 0
        val after = next.driverWindow.valueOrNull() ?: return
        if (before == 0 && after > 0) out += DriveEvent.WindowOpened(after, now)
    }

    private fun headlight(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        if (!prev.headlightOn && next.headlightOn) out += DriveEvent.HeadlightOn(now)
    }

    private fun door(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val opened = prev.driverDoorOpen.valueOrNull() != true && next.driverDoorOpen.valueOrNull() == true
        if (!opened) return
        out += DriveEvent.DriverDoorOpened(next.isParked, now)
        if (!next.isParked) return
        val items = buildList {
            if ((next.driverWindow.valueOrNull() ?: 0) > 0) add(ExitItem.WINDOW_OPEN)
            if (next.headlightOn) add(ExitItem.HEADLIGHT_ON)
            if (next.cabinLight.valueOrNull() == true) add(ExitItem.CABIN_LIGHT_ON)
            if (next.doorsLocked.valueOrNull() == false) add(ExitItem.DOORS_UNLOCKED)
        }
        out += DriveEvent.PreExitCheck(items, now)
    }

    private fun uxRestriction(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val after = next.uxRestricted.valueOrNull() ?: return
        if (prev.uxRestricted.valueOrNull() != after) out += DriveEvent.DrivingModeChanged(after, now)
    }

    private fun energy(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val after = next.energyPct.valueOrNull() ?: return
        val before = prev.energyPct.valueOrNull()
        if (after <= config.lowEnergyPct && (before == null || before > config.lowEnergyPct)) {
            out += DriveEvent.LowEnergy(after, now)
        }
    }

    private fun warnings(prev: VehicleState, next: VehicleState, now: Long, out: MutableList<DriveEvent>) {
        val after = next.warnings.valueOrNull() ?: return
        val before = prev.warnings.valueOrNull().orEmpty()
        val added = after - before.toSet()
        if (added.isNotEmpty()) out += DriveEvent.VehicleWarning(added, now)
    }

    companion object {
        const val DATA_ODOMETER = "odometer"
        const val DATA_STEERING = "steering"
        const val DATA_BRAKE = "brake"
        const val DATA_TURN_SIGNAL = "turnSignal"
        private val TRACKED_DATA = setOf(DATA_ODOMETER, DATA_STEERING, DATA_BRAKE, DATA_TURN_SIGNAL)
    }
}
