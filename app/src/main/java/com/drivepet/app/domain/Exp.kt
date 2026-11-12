package com.drivepet.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val ATTENDANCE_MILLI = 10_000L
const val REPLACEMENT_MILLI = 50_000L
const val MISSION_MILLI = 30_000L
const val BONUS_MIN_DISTANCE_MM = 1_000_000L

private val SEOUL = ZoneId.of("Asia/Seoul")

data class Gauge(val level: Int, val inLevelMilli: Long, val needMilli: Long?, val percent: Int)

object Growth {
    const val LV2_MILLI = 400_000L
    const val LV3_MILLI = 900_000L

    fun level(totalMilli: Long): Int = when {
        totalMilli >= LV3_MILLI -> 3
        totalMilli >= LV2_MILLI -> 2
        else -> 1
    }

    fun gauge(totalMilli: Long): Gauge = when (level(totalMilli)) {
        1 -> gauge(1, totalMilli, LV2_MILLI)
        2 -> gauge(2, totalMilli - LV2_MILLI, LV3_MILLI - LV2_MILLI)
        else -> Gauge(3, totalMilli - LV3_MILLI, null, 100)
    }

    private fun gauge(level: Int, inLevel: Long, need: Long) = Gauge(level, inLevel, need, (inLevel * 100 / need).toInt())
}

sealed interface LedgerRecord {
    val recordId: String
    val occurredAt: Instant
}

data class TripLedger(
    override val recordId: String,
    override val occurredAt: Instant,
    val startMm: Long?,
    val endMm: Long?,
    val valid: Boolean,
    val bonusMilli: Long,
) : LedgerRecord

data class AttendanceLedger(
    override val recordId: String,
    override val occurredAt: Instant,
    val localDate: LocalDate,
) : LedgerRecord

data class ReplacementLedger(
    override val recordId: String,
    override val occurredAt: Instant,
    val itemId: String,
    val localDate: LocalDate,
) : LedgerRecord

data class MissionLedger(
    override val recordId: String,
    override val occurredAt: Instant,
    val missionId: String,
    val periodKey: String,
    val rewardMilli: Long,
) : LedgerRecord

/** 구간 [first, last)의 합집합 거리 D(mm)를 500mm당 1 EXP(=1000 milli)로 환산해 한 번만 ROUND_HALF_UP. */
fun drivingExpMilli(intervals: List<LongRange>): Long {
    var distance = 0L
    var curStart = 0L
    var curEnd = 0L
    var open = false
    for (r in intervals.filter { it.last > it.first }.sortedBy { it.first }) {
        if (open && r.first <= curEnd) {
            curEnd = maxOf(curEnd, r.last)
        } else {
            if (open) distance += curEnd - curStart
            curStart = r.first; curEnd = r.last; open = true
        }
    }
    if (open) distance += curEnd - curStart
    return BigDecimal(distance).divide(BigDecimal(500), 0, RoundingMode.HALF_UP).toLong()
}

fun totalExpMilli(records: List<LedgerRecord>): Long {
    // 거리·보너스 대상: VALID이고 start/end가 있으며 end >= start인 트립만.
    val trips = records.filterIsInstance<TripLedger>()
        .filter { it.valid && it.startMm != null && it.endMm != null && it.endMm >= it.startMm }
        .sortedWith(compareBy({ it.occurredAt }, { it.recordId }))

    var bonus = 0L
    trips.forEachIndexed { i, t ->
        val s = t.startMm!!; val e = t.endMm!!
        val overlapsEarlier = (0 until i).any { j -> s < trips[j].endMm!! && trips[j].startMm!! < e }
        if (e - s >= BONUS_MIN_DISTANCE_MM && !overlapsEarlier) bonus += t.bonusMilli
    }
    val driving = drivingExpMilli(trips.map { it.startMm!!..it.endMm!! })

    val attendance = records.filterIsInstance<AttendanceLedger>().distinctBy { it.localDate }.size * ATTENDANCE_MILLI
    val replacement = records.filterIsInstance<ReplacementLedger>().distinctBy { it.itemId to it.localDate }.size * REPLACEMENT_MILLI
    val mission = records.filterIsInstance<MissionLedger>().distinctBy { it.missionId to it.periodKey }.sumOf { it.rewardMilli }
    return driving + bonus + attendance + replacement + mission
}

/**
 * 트립별로 원장이 실제 준 EXP(milli). [trips]와 같은 순서로 돌려준다.
 * [totalExpMilli]와 같은 규칙으로, 오래된 순으로 한 번 훑으며 트립 하나가 늘린 합계를 그 트립의 몫으로 본다
 * (거리는 합집합 환산값의 증가분, 보너스는 1km 이상이고 앞선 트립과 안 겹칠 때만). O(n² log n).
 */
fun tripPaidMilli(trips: List<TripLedger>): List<Long> {
    val paid = LongArray(trips.size)
    val counted = mutableListOf<TripLedger>()
    var driving = 0L
    for (idx in trips.indices.sortedWith(compareBy({ trips[it].occurredAt }, { trips[it].recordId }))) {
        val t = trips[idx]
        val s = t.startMm
        val e = t.endMm
        if (!t.valid || s == null || e == null || e < s) continue
        val overlapsEarlier = counted.any { s < it.endMm!! && it.startMm!! < e }
        counted += t
        val newDriving = drivingExpMilli(counted.map { it.startMm!!..it.endMm!! })
        val bonus = if (e - s >= BONUS_MIN_DISTANCE_MM && !overlapsEarlier) t.bonusMilli else 0L
        paid[idx] = newDriving - driving + bonus
        driving = newDriving
    }
    return paid.toList()
}

fun formatExp(milli: Long): String = BigDecimal(milli).movePointLeft(3).setScale(1, RoundingMode.HALF_UP).toPlainString()

/** 레벨 막대 아래 「200 / 500 EXP」(피그마 「000 / 000 EXP」, 419:50656). 정수면 「.0」을 떼고, 소수가 있으면 한 자리까지 남긴다. */
fun gaugeExpText(inLevelMilli: Long, needMilli: Long): String =
    "${formatExp(inLevelMilli).removeSuffix(".0")} / ${formatExp(needMilli).removeSuffix(".0")} EXP"

fun apiExp(milli: Long): String = BigDecimal(milli).movePointLeft(3).setScale(3).toPlainString()

fun seoulDate(at: Instant): LocalDate = at.atZone(SEOUL).toLocalDate()

/** 서울 오프셋 ISO 시각, 예: "2026-11-10T08:31:20+09:00". */
fun isoSeoul(epochMillis: Long): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(SEOUL))
