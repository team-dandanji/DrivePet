package com.drivepet.vehicle.model

/**
 * 한 번의 주행 기록. 점수·EXP 계산(기획서 6장)은 이 모듈 밖에서 한다.
 * 진행 중에도 [com.drivepet.vehicle.VehicleRepository.trip]으로 계속 나오므로 주기적으로 저장하면 된다.
 */
data class TripSummary(
    val startedAt: Long,
    val endedAt: Long?,
    val startOdometerKm: Double?,
    val endOdometerKm: Double?,
    val sharpSteerCount: Int = 0,
    val brakeCount: Int = 0,
    val brakeHoldTotalMs: Long = 0,
    val turnSignalCount: Int = 0,
    /** 크게 꺾은 횟수(방향지시등 매너 비율의 분모). */
    val bigTurnCount: Int = 0,
    val turnWithoutSignalCount: Int = 0,
    /** 주행 중 한 번도 정상 값을 받지 못한 데이터 이름. 점수에서 '미측정' 처리할 항목. */
    val missingData: Set<String> = emptySet(),
) {
    /** 주행 거리(km). 출발·도착 누적거리를 모두 받았을 때만. */
    val distanceKm: Double?
        get() = if (startOdometerKm != null && endOdometerKm != null) {
            (endOdometerKm - startOdometerKm).coerceAtLeast(0.0)
        } else {
            null
        }

    val durationMs: Long? get() = endedAt?.let { it - startedAt }
}
