package com.drivepet.app.domain

enum class MeasurementStatus { MEASURED, UNMEASURED, UNSUPPORTED, PERMISSION_DENIED, STALE }

enum class FuelType { EV, ICE, HEV }

// 만점 배분(합 100): 국토교통부·한국교통안전공단 버스 사고 배수(정책브리핑 2022.5.20) 급가속 1.98 + 급감속 2.1 = 4.08 vs 급진로변경 2.08
// → 사고 관련 55점을 4.08:2.08로 나눠 36 : 19(급조향 10 + 방향지시등 9). 연비·냉난방은 사고 근거 없는 효율 항목이라 자체 기준(30, 15).
// 근거: docs/research/2026-11-10-health-score-basis.md
enum class SafetyItem(val maxScore: Int) {
    ACCEL_BRAKE(36), STEERING(10), EFFICIENCY(30), HVAC_OVERLAP(15), TURN_SIGNAL(9)
}

data class TripMetrics(
    val startMm: Long?, val endMm: Long?,
    val speedMeasured: Boolean, val harshAccel: Int, val harshDecel: Int, val longBrakes: Int,
    val steeringMeasured: Boolean, val sharpSteers: Int, val bigTurns: Int, val turnsWithoutSignal: Int,
    val fuelType: FuelType, val energyStartPct: Int?, val energyEndPct: Int?, val batteryKwh: Double?,
)

data class SafetyLine(val item: SafetyItem, val score: Int?, val status: MeasurementStatus, val value: Double?)

data class Settlement(
    val distanceMm: Long?, val valid: Boolean, val baseDrivingExpMilli: Long?, val bonusMilli: Long,
    val safetyScore: Int?, val safetyStatus: MeasurementStatus, val breakdown: List<SafetyLine>?,
    val healthScore: Int?, val healthStatus: MeasurementStatus,
)

private const val STEERING_BONUS_MILLI = 15_000L
private const val SIGNAL_BONUS_MILLI = 20_000L
private const val SIGNAL_BONUS_PERCENT = 80
private const val FULL_EFFICIENCY_KM_PER_KWH = 5.0

private fun measured(item: SafetyItem, score: Int, value: Double?) = SafetyLine(item, score, MeasurementStatus.MEASURED, value)
private fun unmeasured(item: SafetyItem) = SafetyLine(item, null, MeasurementStatus.UNMEASURED, null)

fun settle(m: TripMetrics, health: HealthResult): Settlement {
    val start = m.startMm
    val end = m.endMm
    val valid = start != null && end != null && end >= start
    val distanceMm = if (valid) end!! - start!! else null

    val accelBrake = if (!m.speedMeasured) unmeasured(SafetyItem.ACCEL_BRAKE)
    else measured(SafetyItem.ACCEL_BRAKE, (SafetyItem.ACCEL_BRAKE.maxScore - 5 * (m.harshAccel + m.harshDecel) - 2 * m.longBrakes).coerceAtLeast(0), null)

    val steering = if (!m.steeringMeasured) unmeasured(SafetyItem.STEERING)
    else measured(SafetyItem.STEERING, (SafetyItem.STEERING.maxScore - 5 * m.sharpSteers).coerceAtLeast(0), m.sharpSteers.toDouble())

    val efficiency = efficiencyLine(m, distanceMm)

    val signalRatioPercent = if (m.bigTurns > 0) {
        (m.bigTurns - m.turnsWithoutSignal).coerceAtLeast(0) * 100.0 / m.bigTurns
    } else null
    val turnSignal = if (signalRatioPercent == null) unmeasured(SafetyItem.TURN_SIGNAL)
    else measured(SafetyItem.TURN_SIGNAL, Math.round(SafetyItem.TURN_SIGNAL.maxScore * signalRatioPercent / 100).toInt(), signalRatioPercent)

    val breakdown = listOf(accelBrake, steering, efficiency, unmeasured(SafetyItem.HVAC_OVERLAP), turnSignal)
    val done = breakdown.filter { it.status == MeasurementStatus.MEASURED }
    val score = if (done.isEmpty()) null
    else Math.round(done.sumOf { it.score!! } * 100.0 / done.sumOf { it.item.maxScore }).toInt()

    var bonus = 0L
    if (steering.status == MeasurementStatus.MEASURED && m.sharpSteers == 0) bonus += STEERING_BONUS_MILLI
    if (signalRatioPercent != null && signalRatioPercent >= SIGNAL_BONUS_PERCENT) bonus += SIGNAL_BONUS_MILLI

    return Settlement(
        distanceMm = distanceMm,
        valid = valid,
        baseDrivingExpMilli = if (valid) drivingExpMilli(listOf(start!!..end!!)) else null,
        bonusMilli = bonus,
        safetyScore = score,
        safetyStatus = if (done.isEmpty()) MeasurementStatus.UNMEASURED else MeasurementStatus.MEASURED,
        breakdown = breakdown,
        healthScore = health.score,
        healthStatus = health.status,
    )
}

// km/kWh = km / (energy drop% * capacity / 100); only EV with a positive drop, positive capacity and positive verified distance.
private fun efficiencyLine(m: TripMetrics, distanceMm: Long?): SafetyLine {
    val item = SafetyItem.EFFICIENCY
    val s = m.energyStartPct
    val e = m.energyEndPct
    val kwh = m.batteryKwh
    if (m.fuelType != FuelType.EV || distanceMm == null || distanceMm <= 0 || s == null || e == null || kwh == null || kwh <= 0.0 || s - e <= 0) return unmeasured(item)
    val kmPerKwh = (distanceMm / 1_000_000.0) / ((s - e) * kwh / 100)
    return measured(item, Math.round(item.maxScore * minOf(1.0, kmPerKwh / FULL_EFFICIENCY_KM_PER_KWH)).toInt(), kmPerKwh)
}
