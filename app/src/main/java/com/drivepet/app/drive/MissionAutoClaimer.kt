package com.drivepet.app.drive

import com.drivepet.app.data.MissionClaimPayload
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.missionTrip
import com.drivepet.app.domain.MISSION_MILLI
import com.drivepet.app.domain.MissionStatus
import com.drivepet.app.domain.apiExp
import com.drivepet.app.domain.dailyMissions
import com.drivepet.app.domain.missionStatus
import com.drivepet.app.domain.seoulDate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** 오늘 이미 받은 미션 id. */
fun claimedMissionIds(records: List<RecordEntity>, today: LocalDate): Set<String> =
    records.filter { it.kind == "MISSION_CLAIM" && it.periodKey == today.toString() }.mapNotNull { it.missionId }.toSet()

/** 오늘의 미션 상태. 라운지 화면과 자동 지급이 같은 판정을 쓴다. */
fun todayMissions(profileId: String, records: List<RecordEntity>, today: LocalDate): List<MissionStatus> {
    val attendanceId = records.firstOrNull { it.kind == "ATTENDANCE" && it.localDate == today.toString() }?.recordId
    val todayTrips = records.filter { seoulDate(Instant.ofEpochMilli(it.occurredAt)) == today }.mapNotNull { it.missionTrip() }
    return dailyMissions(profileId, today).map { missionStatus(it, todayTrips, attendanceId, claimedMissionIds(records, today)) }
}

/** 달성했고 아직 안 받은 미션을 자동으로 +30. 같은 미션·같은 날은 recordId가 같아 다시 불러도 한 건이다. */
class MissionAutoClaimer(private val store: RecordStore, private val clock: () -> Instant) {
    /** 새로 넣은 MISSION_CLAIM 개수. */
    suspend fun claimDone(profileId: String, accountId: String?): Int {
        val now = clock()
        val today = seoulDate(now)
        val date = today.toString()
        return todayMissions(profileId, store.records(profileId).first(), today)
            .filter { it.done && !it.claimed }
            .count { m ->
                store.insertIfAbsent(
                    RecordEntity(
                        recordId = UUID.nameUUIDFromBytes("mission:$profileId:${m.kind.id}:$date".toByteArray()).toString(),
                        profileId = profileId,
                        accountId = accountId,
                        kind = "MISSION_CLAIM",
                        occurredAt = now.toEpochMilli(),
                        payloadJson = Json.encodeToString(MissionClaimPayload(m.kind.id, date, apiExp(MISSION_MILLI), m.evidence)),
                        missionId = m.kind.id,
                        periodKey = date,
                        rewardMilli = MISSION_MILLI,
                    ),
                )
            }
    }
}
