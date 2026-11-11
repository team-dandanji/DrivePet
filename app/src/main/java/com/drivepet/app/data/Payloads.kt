package com.drivepet.app.data

import com.drivepet.app.domain.MeasurementStatus
import com.drivepet.app.domain.MissionTrip
import com.drivepet.app.domain.SafetyItem
import com.drivepet.app.domain.SafetyLine
import com.drivepet.app.domain.Settlement
import com.drivepet.app.domain.apiExp
import com.drivepet.app.domain.totalExpMilli
import com.drivepet.app.data.db.RecordEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** API 명세(팀 노션 「DrivePet API 명세서」) TripRecordPayload. */
@Serializable
data class TripPayload(
    val startedAt: String,
    val endedAt: String,
    val startOdometerMm: Long?,
    val endOdometerMm: Long?,
    val distanceStatus: String, // VALID | UNVERIFIED
    val settlement: SettlementJson,
    val reflection: String? = null,
)

/** API 명세(팀 노션 「DrivePet API 명세서」) TripSettlement. */
@Serializable
data class SettlementJson(
    val distanceMm: Long?,
    val baseDrivingExp: String?,
    val bonusExp: String,
    val safetyScore: Int?,
    val safetyStatus: String,
    val safetyBreakdown: List<SafetyLineJson>?,
    val healthScore: Int?,
    val healthStatus: String,
)

@Serializable
data class SafetyLineJson(
    val item: String,
    val score: Int?,
    val maxScore: Int,
    val status: String,
    val value: Double?,
)

fun SafetyLine.toJson() = SafetyLineJson(item.name, score, item.maxScore, status.name, value)

fun Settlement.toJson() = SettlementJson(
    distanceMm = distanceMm,
    baseDrivingExp = baseDrivingExpMilli?.let(::apiExp),
    bonusExp = apiExp(bonusMilli),
    safetyScore = safetyScore,
    safetyStatus = safetyStatus.name,
    // API 계약: 측정된 항목이 없으면 키는 두고 null. 화면용 domain breakdown(5줄)은 그대로 둔다.
    safetyBreakdown = if (safetyStatus == MeasurementStatus.MEASURED) breakdown?.map { it.toJson() } else null,
    healthScore = healthScore,
    healthStatus = healthStatus.name,
)

/** API 명세(팀 노션 「DrivePet API 명세서」) AttendanceRecordPayload. */
@Serializable
data class AttendancePayload(val localDate: String)

private val lenientJson = Json { ignoreUnknownKeys = true }

/** TRIP 기록의 payload. TRIP이 아니거나 읽을 수 없으면 null. */
fun RecordEntity.tripPayload(): TripPayload? =
    if (kind != "TRIP") null else runCatching { lenientJson.decodeFromString<TripPayload>(payloadJson) }.getOrNull()

/** VALID 거리 TRIP(`valid` 열 == true)의 안전 점수를 오래된 것 → 최신 순으로, 최근 [n]개. UNVERIFIED·읽을 수 없는 기록은 건너뛴다. */
fun List<RecordEntity>.recentTripSafety(n: Int = 5): List<Int> =
    filter { it.kind == "TRIP" && it.valid == true }.sortedBy { it.occurredAt }
        .mapNotNull { it.tripPayload()?.settlement?.safetyScore }
        .takeLast(n)

/** 프로필의 총 EXP(milli): 원장 합산 + 개발자 시작 EXP. 레벨·게이지는 모두 이 값에서 구한다. */
fun growthTotalMilli(records: List<RecordEntity>, seedMilli: Long): Long = totalExpMilli(records.map { it.toLedger() }) + seedMilli

/** API 명세 MissionClaimRecordPayload. */
@Serializable
data class MissionClaimPayload(val missionId: String, val periodKey: String, val rewardExp: String, val evidenceRecordIds: List<String>)

/** TRIP 기록 → 미션 판정 입력. 측정 안 된 항목(breakdown 없음 포함)은 null. TRIP이 아니면 null. */
fun RecordEntity.missionTrip(): MissionTrip? {
    if (kind != "TRIP") return null
    val st = tripPayload()?.settlement
    val lines = st?.safetyBreakdown.orEmpty().filter { it.status == MeasurementStatus.MEASURED.name }.associateBy { it.item }
    return MissionTrip(
        recordId = recordId,
        valid = valid == true,
        distanceMm = st?.distanceMm,
        safetyScore = st?.safetyScore,
        sharpSteers = lines[SafetyItem.STEERING.name]?.value?.toInt(),
        accelBrakeFull = lines[SafetyItem.ACCEL_BRAKE.name]?.let { it.score == SafetyItem.ACCEL_BRAKE.maxScore },
        turnSignalPct = lines[SafetyItem.TURN_SIGNAL.name]?.value,
    )
}
