package com.drivepet.vehicle.model

/**
 * 차 상태 변화에서 만든 이벤트. 캐릭터 반응·점수·정산은 이것을 구독한다.
 * 만드는 규칙과 기준 숫자는 [com.drivepet.vehicle.engine.RuleConfig].
 */
sealed class DriveEvent {
    abstract val at: Long

    /** P → D/R. [odometerKm]가 null이면 출발 거리를 아직 못 받은 것(나중에 처음 받은 값으로 보정). */
    data class TripStarted(val odometerKm: Double?, override val at: Long) : DriveEvent()

    /** D/R → P가 일정 시간 유지되어 주행이 끝남. 정산 화면으로. */
    data class TripEnded(val trip: TripSummary, override val at: Long) : DriveEvent()

    /** 주행 중 짧은 시간 안에 조향각이 크게 바뀜. */
    data class SharpSteer(val deltaDeg: Double, override val at: Long) : DriveEvent()

    /** 주행 중 브레이크를 밟았다 뗌. [holdMs]는 밟고 있던 시간. */
    data class BrakeReleased(val holdMs: Long, override val at: Long) : DriveEvent()

    data class TurnSignalOn(val side: TurnSide, override val at: Long) : DriveEvent()

    /** 크게 꺾었는데 직전에 방향지시등이 없었음. */
    data class TurnWithoutSignal(val steeringDeg: Double, override val at: Long) : DriveEvent()

    data class WindowOpened(val level: Int, override val at: Long) : DriveEvent()

    data class HeadlightOn(override val at: Long) : DriveEvent()

    /** 운전석 문 열림. [parked]가 true면 하차 상황. */
    data class DriverDoorOpened(val parked: Boolean, override val at: Long) : DriveEvent()

    /** P에서 운전석 문이 열릴 때 확인할 것. [items]가 비어 있으면 모두 정상. */
    data class PreExitCheck(val items: List<ExitItem>, override val at: Long) : DriveEvent()

    /** 출발하는데 벨트 미착용(값을 읽을 수 있을 때만). */
    data class BeltReminder(override val at: Long) : DriveEvent()

    /** 주행 제한 상태 변경. true면 '주행 중 최소 화면', false면 '정차 화면'. */
    data class DrivingModeChanged(val restricted: Boolean, override val at: Long) : DriveEvent()

    data class LowEnergy(val pct: Int, override val at: Long) : DriveEvent()

    /** 새로 켜진 경고등(개발 단계 전용 데이터). */
    data class VehicleWarning(val names: List<String>, override val at: Long) : DriveEvent()
}

enum class ExitItem { WINDOW_OPEN, HEADLIGHT_ON, DOORS_UNLOCKED }
