package com.drivepet.vehicle.source

import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.TurnSide
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull

/**
 * 버튼으로 차 상태를 직접 바꾸는 가짜 차.
 * UI 팀원이 에뮬레이터 없이 화면·캐릭터 반응을 개발할 때 쓴다.
 */
class FakeVehicleSource(
    private val initialOdometerKm: Double = 12_034.0,
    private val clock: () -> Long = System::currentTimeMillis,
) : VehicleSource {

    override val name = "가짜 데이터"
    @Volatile private var sink: VehicleSink? = null

    override fun start(sink: VehicleSink) {
        this.sink = sink
        val now = clock()
        sink.update {
            VehicleState(
                gear = Signal.Ok(Gear.P, now),
                uxRestricted = Signal.Ok(false, now),
                odometerKm = Signal.Ok(initialOdometerKm, now),
                speedKmh = Signal.Ok(0.0, now),
                brakePressed = Signal.Ok(false, now),
                steeringDeg = Signal.Ok(0.0, now),
                turnSignal = Signal.Ok(TurnSide.OFF, now),
                driverDoorOpen = Signal.Ok(false, now),
                doorsLocked = Signal.Ok(true, now),
                driverWindow = Signal.Ok(0, now),
                lowBeam = Signal.Ok(false, now),
                highBeam = Signal.Ok(false, now),
                cabinLight = Signal.Ok(false, now),
                driverBeltBuckled = Signal.Ok(false, now),
                energyPct = Signal.Ok(80, now),
                warnings = Signal.Ok(emptyList(), now),
            )
        }
    }

    override fun stop() {
        sink = null
    }

    private fun set(patch: (VehicleState, Long) -> VehicleState) {
        val now = clock()
        sink?.update { patch(it, now) }
    }

    /** 기어를 바꾸면 주행 제한도 실제 차처럼 함께 바뀐다(P만 제한 없음). */
    fun setGear(gear: Gear) = set { s, t ->
        s.copy(gear = Signal.Ok(gear, t), uxRestricted = Signal.Ok(gear != Gear.P, t))
    }

    fun addKm(km: Double) = set { s, t ->
        s.copy(odometerKm = Signal.Ok((s.odometerKm.valueOrNull() ?: initialOdometerKm) + km, t))
    }

    fun setSpeed(kmh: Double) = set { s, t -> s.copy(speedKmh = Signal.Ok(kmh, t)) }
    fun setBrake(pressed: Boolean) = set { s, t -> s.copy(brakePressed = Signal.Ok(pressed, t)) }
    fun setSteering(deg: Double) = set { s, t -> s.copy(steeringDeg = Signal.Ok(deg, t)) }
    fun setTurnSignal(side: TurnSide) = set { s, t -> s.copy(turnSignal = Signal.Ok(side, t)) }
    fun setDriverDoor(open: Boolean) = set { s, t -> s.copy(driverDoorOpen = Signal.Ok(open, t)) }
    fun setLocked(locked: Boolean) = set { s, t -> s.copy(doorsLocked = Signal.Ok(locked, t)) }
    fun setWindow(level: Int) = set { s, t -> s.copy(driverWindow = Signal.Ok(level.coerceIn(0, 10), t)) }
    fun setLowBeam(on: Boolean) = set { s, t -> s.copy(lowBeam = Signal.Ok(on, t)) }
    fun setHighBeam(on: Boolean) = set { s, t -> s.copy(highBeam = Signal.Ok(on, t)) }
    fun setCabinLight(on: Boolean) = set { s, t -> s.copy(cabinLight = Signal.Ok(on, t)) }
    fun setBelt(buckled: Boolean) = set { s, t -> s.copy(driverBeltBuckled = Signal.Ok(buckled, t)) }
    fun setEnergy(pct: Int) = set { s, t -> s.copy(energyPct = Signal.Ok(pct.coerceIn(0, 100), t)) }
    fun setWarnings(names: List<String>) = set { s, t -> s.copy(warnings = Signal.Ok(names, t)) }

    /** 값 하나를 '미측정'처럼 만들어 화면의 예외 처리를 확인할 때. */
    fun markUnsupported(field: String) = set { s, _ -> Scenario.applyUnsupported(s, field) }
}
