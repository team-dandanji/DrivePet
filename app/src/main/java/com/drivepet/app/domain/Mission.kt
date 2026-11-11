package com.drivepet.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.random.Random

/** 일일 미션(설계 5-4, 3부에서 "하차 점검 모두 정상"을 "안전점수 90점 이상"으로 바꿈). id는 서버 MISSION_CLAIM의 missionId. */
enum class MissionKind(val id: String, val title: String) {
    NO_SHARP_STEER_5KM("daily-no-sharp-steer-5km", "급조향 없이 5km 달리기"),
    SMOOTH_ACCEL("daily-smooth-accel", "급가속·급제동 없이 달리기"),
    /** 차속을 못 받은 날 SMOOTH_ACCEL 대신. 풀에는 없다. */
    NO_SHARP_STEER_TRIP("daily-no-sharp-steer-trip", "급조향 없이 달리기"),
    TURN_SIGNAL("daily-turn-signal", "방향지시등 매너 지키기"),
    SAFETY_90("daily-safety-90", "안전점수 90점 이상으로 달리기"),
    ATTENDANCE("daily-attendance", "오늘 출석하기"),
}

private val POOL = listOf(MissionKind.NO_SHARP_STEER_5KM, MissionKind.SMOOTH_ACCEL, MissionKind.TURN_SIGNAL, MissionKind.SAFETY_90, MissionKind.ATTENDANCE)
private const val FIVE_KM_MM = 5_000_000L

/** 오늘 트립 하나에서 미션 판정에 쓰는 값. null = 그 항목 미측정. */
data class MissionTrip(
    val recordId: String,
    val valid: Boolean,
    val distanceMm: Long?,
    val safetyScore: Int?,
    val sharpSteers: Int?,
    val accelBrakeFull: Boolean?,
    val turnSignalPct: Double?,
)

data class MissionStatus(val kind: MissionKind, val done: Boolean, val claimed: Boolean, val evidence: List<String>, val progress: String)

/** 서울 날짜마다 풀에서 3개. 시드가 profileId+날짜라 어느 기기에서도 같다. */
fun dailyMissions(profileId: String, date: LocalDate): List<MissionKind> =
    POOL.shuffled(Random("$profileId:$date".hashCode())).take(3)

fun missionStatus(kind: MissionKind, todayTrips: List<MissionTrip>, attendanceRecordId: String?, claimedIds: Set<String>): MissionStatus {
    val k = if (kind == MissionKind.SMOOTH_ACCEL && todayTrips.isNotEmpty() && todayTrips.all { it.accelBrakeFull == null }) {
        MissionKind.NO_SHARP_STEER_TRIP
    } else kind
    var progress = ""
    val evidence: List<String> = when (k) {
        MissionKind.NO_SHARP_STEER_5KM -> {
            val clean = todayTrips.filter { it.valid && it.sharpSteers == 0 && it.distanceMm != null }
            val sum = clean.sumOf { it.distanceMm!! }
            progress = "${km1(minOf(sum, FIVE_KM_MM))} / 5km"
            if (sum >= FIVE_KM_MM) clean.map { it.recordId } else emptyList()
        }
        MissionKind.SMOOTH_ACCEL -> todayTrips.filter { it.accelBrakeFull == true }.take(1).map { it.recordId }
        MissionKind.NO_SHARP_STEER_TRIP -> todayTrips.filter { it.sharpSteers == 0 }.take(1).map { it.recordId }
        MissionKind.TURN_SIGNAL -> todayTrips.filter { it.turnSignalPct == 100.0 }.take(1).map { it.recordId }
        MissionKind.SAFETY_90 -> todayTrips.filter { it.valid && (it.safetyScore ?: -1) >= 90 }.take(1).map { it.recordId }
        MissionKind.ATTENDANCE -> listOfNotNull(attendanceRecordId)
    }
    val done = evidence.isNotEmpty()
    if (done) progress = ""
    return MissionStatus(k, done, k.id in claimedIds, evidence, progress)
}

private fun km1(mm: Long) = BigDecimal(mm).movePointLeft(6).setScale(1, RoundingMode.HALF_UP).toPlainString()
