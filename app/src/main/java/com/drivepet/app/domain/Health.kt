package com.drivepet.app.domain

import com.drivepet.vehicle.model.Signal

sealed interface WarningInput {
    data class Known(val names: List<String>) : WarningInput
    data class Unavailable(val status: MeasurementStatus) : WarningInput
}

fun Signal<List<String>>.toWarningInput(): WarningInput = when (this) {
    is Signal.Ok -> WarningInput.Known(value)
    Signal.NoPermission -> WarningInput.Unavailable(MeasurementStatus.PERMISSION_DENIED)
    Signal.Unsupported -> WarningInput.Unavailable(MeasurementStatus.UNSUPPORTED)
    Signal.Unknown -> WarningInput.Unavailable(MeasurementStatus.UNMEASURED)
}

data class HealthResult(val score: Int?, val status: MeasurementStatus)

private const val WARNING_MAX = 40.0
private const val CONSUMABLE_MAX = 30.0
private const val HABIT_MAX = 30.0
private const val TPMS_DEDUCTION = 20.0
private const val OTHER_DEDUCTION = 10.0
private const val HABIT_WINDOW = 5

/** recentSafety는 오래된 것 → 최신 순서. 측정된 항목의 합 ÷ 측정된 배점 × 100을 마지막에만 반올림한다. */
fun health(warnings: WarningInput, consumableLifePct: List<Int>, recentSafety: List<Int>): HealthResult {
    var got = 0.0
    var max = 0.0
    if (warnings is WarningInput.Known) {
        val tpms = warnings.names.any { "TPMS" in it }
        val others = warnings.names.count { "TPMS" !in it }
        got += (WARNING_MAX - (if (tpms) TPMS_DEDUCTION else 0.0) - others * OTHER_DEDUCTION).coerceAtLeast(0.0)
        max += WARNING_MAX
    }
    if (consumableLifePct.isNotEmpty()) {
        got += consumableLifePct.map { it.coerceIn(0, 100) }.average() * 0.3
        max += CONSUMABLE_MAX
    }
    if (recentSafety.isNotEmpty()) {
        got += recentSafety.takeLast(HABIT_WINDOW).average() * 0.3
        max += HABIT_MAX
    }
    if (max == 0.0) return HealthResult(null, MeasurementStatus.UNMEASURED)
    return HealthResult(Math.round(got / max * 100).toInt(), MeasurementStatus.MEASURED)
}

fun satiety(energyPct: Int?): Int? = energyPct?.coerceIn(0, 100)

enum class Mood { SICK_1, SICK_2, SICK_3, HUNGRY, WORRIED, HAPPY, NORMAL }

data class MoodInput(
    val warningDaysMax: Int?,
    val satietyPct: Int?,
    val consumableMinLifePct: Int?,
    val inspectionDaysLeft: Int?,
    val happyToday: Boolean,
)

fun mood(i: MoodInput): Mood {
    i.warningDaysMax?.let { days ->
        return when {
            days <= 0 -> Mood.SICK_1
            days <= 2 -> Mood.SICK_2
            else -> Mood.SICK_3
        }
    }
    return when {
        i.satietyPct != null && i.satietyPct <= 20 -> Mood.HUNGRY
        (i.consumableMinLifePct != null && i.consumableMinLifePct <= 10) ||
            (i.inspectionDaysLeft != null && i.inspectionDaysLeft <= 30) -> Mood.WORRIED
        i.happyToday -> Mood.HAPPY
        else -> Mood.NORMAL
    }
}
