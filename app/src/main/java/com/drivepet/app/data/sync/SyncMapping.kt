package com.drivepet.app.data.sync

import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.domain.isoSeoul
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

// shortcut: 검사일·소모품은 비워 보낸다, 정비 화면 계획에서 채운다
fun ProfileEntity.toState() = ProfileStateDto(
    vehicleName = vehicleName, fuelType = fuelType, starterType = starterType, petNickname = petNickname,
    onboardingCompleted = onboardingCompleted, inspectionDueDate = null, maintenanceItems = emptyList(),
)

/** payloadJson은 키·null·숫자 표기를 그대로 둔 채 JsonObject로 옮긴다(서버는 같은 recordId의 내용 동일성을 본다). */
fun RecordEntity.toSyncRecord() = SyncRecordDto(
    recordId = recordId, kind = kind, occurredAt = isoSeoul(occurredAt), ruleVersion = ruleVersion,
    payload = SyncJson.parseToJsonElement(payloadJson).jsonObject,
)

private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.long(key: String): Long? = get(key)?.jsonPrimitive?.longOrNull
private fun expMilli(exp: String): Long = BigDecimal(exp).movePointRight(3).longValueExact()
private fun epochMs(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

/** 서버 기록 → 원장 행. 모르는 kind이거나 읽을 수 없으면 null. */
fun SyncRecordDto.toEntity(profileId: String, accountId: String): RecordEntity? = runCatching {
    val base = RecordEntity(
        recordId = recordId, profileId = profileId, accountId = accountId, kind = kind, occurredAt = epochMs(occurredAt),
        ruleVersion = ruleVersion, payloadJson = payload.toString(), syncState = "SYNCED",
    )
    when (kind) {
        "TRIP" -> base.copy(
            startMm = payload.long("startOdometerMm"), endMm = payload.long("endOdometerMm"),
            valid = payload.str("distanceStatus") == "VALID",
            bonusMilli = expMilli(payload.getValue("settlement").jsonObject.str("bonusExp")),
        )
        "ATTENDANCE" -> base.copy(localDate = payload.str("localDate"))
        "REPLACEMENT" -> base.copy(itemId = payload.str("itemId"), localDate = payload.str("localDate"))
        "MISSION_CLAIM" -> base.copy(
            missionId = payload.str("missionId"), periodKey = payload.str("periodKey"), rewardMilli = expMilli(payload.str("rewardExp")),
        )
        else -> null
    }
}.getOrNull()

/** 내려받은 변경 1건 → 로컬 반영 단위. 반영할 것이 없거나 다른 계정 프로필이면 null. */
fun ChangeDto.toLocal(accountId: String, existing: ProfileEntity?): LocalChange? {
    if (existing != null && existing.accountId != accountId) return null // 다른 계정 것은 건드리지 않는다
    return runCatching {
        when (type) {
            "PROFILE_CREATED", "STATE_UPDATED" -> {
                val s = SyncJson.decodeFromJsonElement<ProfileStateDto>(payload.getValue("state"))
                LocalChange.UpsertProfile(
                    ProfileEntity(
                        profileId = profileId, accountId = accountId, vehicleName = s.vehicleName, fuelType = s.fuelType,
                        starterType = s.starterType, petNickname = s.petNickname, onboardingCompleted = s.onboardingCompleted,
                        batteryKwh = existing?.batteryKwh, createdAt = existing?.createdAt ?: epochMs(savedAt), stateRevision = revision,
                    ),
                )
            }
            "RECORD_APPENDED" -> SyncJson.decodeFromJsonElement<SyncRecordDto>(payload.getValue("record"))
                .toEntity(profileId, accountId)?.let { LocalChange.InsertRecord(it) }
            "PROFILE_DELETED" -> existing?.let { LocalChange.DeleteProfile(profileId, epochMs(payload.str("deletedAt"))) }
            else -> null
        }
    }.getOrNull()
}

private fun nameUuid(s: String) = UUID.nameUUIDFromBytes(s.toByteArray()).toString()
fun createOpId(profileId: String): String = nameUuid("create:$profileId")
fun appendOpId(recordId: String): String = nameUuid("append:$recordId")
/** 같은 작업 묶음은 같은 id → 응답을 못 받고 다시 보내도 서버가 첫 응답을 돌려준다. */
fun clientEventId(accountId: String, ops: List<SyncOp>): String = nameUuid("$accountId|" + ops.joinToString(",") { it.operationId })
