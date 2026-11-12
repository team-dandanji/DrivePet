package com.drivepet.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** 소모품 기본 항목(API MaintenanceItemSetting 설명의 기본 항목). 관리 주기는 실제 부품 수명이 아니다. */
enum class CareItem(val title: String, val intervalKm: Int, val doneLabel: String) {
    ENGINE_OIL("엔진오일 및 필터", 10_000, "방금 교체했어요"),
    TIRE("타이어 위치교환 및 마모 점검", 10_000, "위치교환·점검 완료"),
    CABIN_FILTER("에어컨(캐빈) 필터", 15_000, "방금 교체했어요"),
    WIPER("와이퍼 블레이드", 10_000, "방금 교체했어요"),
}

fun careItemsFor(fuel: FuelType): List<CareItem> = when (fuel) {
    FuelType.EV -> listOf(CareItem.TIRE, CareItem.CABIN_FILTER, CareItem.WIPER)
    FuelType.ICE, FuelType.HEV -> listOf(CareItem.ENGINE_OIL, CareItem.TIRE, CareItem.CABIN_FILTER, CareItem.WIPER)
}

/** 항목 하나의 배점(합 30). 화면 표시용. */
fun careScore(fuel: FuelType): Double = if (fuel == FuelType.EV) 10.0 else 7.5

enum class ServicedPeriod(val label: String) {
    WITHIN_3_MONTHS("최근 3개월 내"), ABOUT_6_MONTHS("6개월 전쯤"), OVER_1_YEAR("1년 이상 지남"), UNKNOWN("기억 안 남"),
}

/** 처음 등록한 기준. 셋 다 null이면 미등록. */
data class CareBaseline(val odometerMm: Long?, val servicedOn: LocalDate?, val period: ServicedPeriod?)

data class Replacement(val recordId: String, val occurredAt: Instant, val odometerMm: Long?)

/** null = 미산출(기준·현재 누적거리를 모르거나 현재가 기준보다 작음). lastServiced null = 미등록. */
data class CareLife(val lastServiced: String?, val baseOdometerMm: Long?, val sinceKm: Int?, val remainingKm: Int?, val lifePct: Int?)

private val DOTTED = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private const val MM_PER_KM = 1_000_000L

/**
 * 기준 누적거리 = occurredAt이 가장 늦고(같으면 recordId가 큰 쪽) odometerMm이 있는 교체 → 없으면 baseline.odometerMm → 없으면 모름(계약 규칙).
 * 남은 주기 % = (주기 − 관리 후 거리) ÷ 주기, 0~100.
 */
fun careLife(item: CareItem, baseline: CareBaseline, replacements: List<Replacement>, currentOdometerMm: Long?): CareLife {
    val order = compareBy<Replacement>({ it.occurredAt }, { it.recordId })
    val latest = replacements.maxWithOrNull(order)
    val base = replacements.filter { it.odometerMm != null }.maxWithOrNull(order)?.odometerMm ?: baseline.odometerMm
    val last = latest?.let { DOTTED.format(seoulDate(it.occurredAt)) }
        ?: baseline.servicedOn?.let(DOTTED::format)
        ?: baseline.period?.label
    if (base == null || currentOdometerMm == null || currentOdometerMm < base) return CareLife(last, base, null, null, null)
    val since = ((currentOdometerMm - base) / MM_PER_KM).toInt()
    val remaining = (item.intervalKm - since).coerceAtLeast(0)
    return CareLife(last, base, since, remaining, (remaining * 100.0 / item.intervalKm).roundToInt().coerceIn(0, 100))
}

fun inspectionDaysLeft(due: LocalDate?, today: LocalDate): Int? = due?.let { ChronoUnit.DAYS.between(today, it).toInt() }

/** 펫 돌보기에서 정비소에 맡길 항목: TPMS 경고면 타이어, 아니면 남은 주기 %가 가장 낮은 항목, 정보가 없으면 타이어. */
fun careTarget(warnings: List<String>, lifePcts: Map<CareItem, Int?>): CareItem {
    if (warnings.any { "TPMS" in it }) return CareItem.TIRE
    return lifePcts.mapNotNull { (item, pct) -> pct?.let { item to it } }.minByOrNull { it.second }?.first ?: CareItem.TIRE
}

/** 태그·안내 문구용 짧은 이름(예 「타이어 점검 필요」). */
val CareItem.shortName: String
    get() = when (this) {
        CareItem.ENGINE_OIL -> "엔진오일"
        CareItem.TIRE -> "타이어"
        CareItem.CABIN_FILTER -> "에어컨 필터"
        CareItem.WIPER -> "와이퍼"
    }
