package com.drivepet.vehicle.model

enum class Gear { P, R, N, D }

enum class TurnSide { OFF, LEFT, RIGHT }

/**
 * 지금 차 상태 한 덩어리. 화면은 이것만 구독하면 된다.
 * 필드별 출처 API·권한·실측 결과는 노션 'DrivePet — 차량 데이터 모듈 설계' 2절 참고.
 */
data class VehicleState(
    /** 기어. Pleos Gear가 주 출처 (AAOS 권한은 앱마켓 승인 미확정). */
    val gear: Signal<Gear> = Signal.Unknown,
    /** 주행 제한(CarUxRestrictions requiresDO). true면 주행 중 화면 최소화. */
    val uxRestricted: Signal<Boolean> = Signal.Unknown,
    /** 누적 주행거리(km). 트립 거리는 출발·도착 값의 차이로 계산한다(트립 API 미지원). */
    val odometerKm: Signal<Double> = Signal.Unknown,
    /** 차속(km/h). 보조 데이터. 에뮬레이터에서는 0 고정이라 점수 필수 입력으로 쓰지 않는다. */
    val speedKmh: Signal<Double> = Signal.Unknown,
    /** 브레이크 밟음 여부. 답력(%)은 제공되지 않음. */
    val brakePressed: Signal<Boolean> = Signal.Unknown,
    /** 조향각(도). 주의: 에뮬레이터 콘솔과 부호가 반대로 들어온다는 다른 팀 실측이 있음. */
    val steeringDeg: Signal<Double> = Signal.Unknown,
    val turnSignal: Signal<TurnSide> = Signal.Unknown,
    val driverDoorOpen: Signal<Boolean> = Signal.Unknown,
    val doorsLocked: Signal<Boolean> = Signal.Unknown,
    /** 운전석 창문 열림 정도 0(닫힘)~10. */
    val driverWindow: Signal<Int> = Signal.Unknown,
    val lowBeam: Signal<Boolean> = Signal.Unknown,
    val highBeam: Signal<Boolean> = Signal.Unknown,
    /** 실내등(Cabin light) 켜짐. Pleos Light.getCurrentCabinLightState, 권한 CAR_LIGHTS. */
    val cabinLight: Signal<Boolean> = Signal.Unknown,
    /** 운전석 안전벨트 착용. 에뮬레이터에서는 읽기만 확인됨(조작 항목 없음). */
    val driverBeltBuckled: Signal<Boolean> = Signal.Unknown,
    /** 연료 또는 배터리 잔량(%). V-09 확인 전까지 Unknown. */
    val energyPct: Signal<Int> = Signal.Unknown,
    /** 켜진 경고등 이름 목록. PRIVATE_CAR_SAFETY 필요 → 개발 단계 전용. */
    val warnings: Signal<List<String>> = Signal.Unknown,
) {
    val isParked: Boolean get() = gear.valueOrNull() == Gear.P
    val isDriving: Boolean get() = gear.valueOrNull().let { it == Gear.D || it == Gear.R }
    val headlightOn: Boolean get() = lowBeam.valueOrNull() == true || highBeam.valueOrNull() == true
}

/** 상태를 바꾸는 함수. 데이터 출처는 이 함수만 넘긴다. */
typealias StatePatch = (VehicleState) -> VehicleState
