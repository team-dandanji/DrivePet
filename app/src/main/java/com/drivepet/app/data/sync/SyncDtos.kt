package com.drivepet.app.data.sync

import com.drivepet.app.data.remote.ApiJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// docs/api/schemas/sync.yaml 그대로. 필수 키는 기본값을 두지 않는다(기본값이 있으면 직렬화에서 빠진다).
@Serializable data class ProfileStateDto(
    val vehicleName: String,
    val fuelType: String,
    val starterType: String,
    val petNickname: String,
    val onboardingCompleted: Boolean,
    val inspectionDueDate: String?,
    val maintenanceItems: List<JsonObject>,
)
@Serializable data class SyncRecordDto(val recordId: String, val kind: String, val occurredAt: String, val ruleVersion: String, val payload: JsonObject)

@Serializable sealed interface SyncOp { val operationId: String; val profileId: String }
@Serializable @SerialName("CREATE_PROFILE")
data class CreateProfileOp(override val operationId: String, override val profileId: String, val state: ProfileStateDto) : SyncOp
@Serializable @SerialName("APPEND_RECORD")
data class AppendRecordOp(override val operationId: String, override val profileId: String, val record: SyncRecordDto) : SyncOp

@Serializable data class UploadRequest(val schemaVersion: Int, val clientEventId: String, val operations: List<SyncOp>)
@Serializable data class OpError(val code: String, val message: String)
@Serializable data class OpResult(val operationId: String, val status: String, val revision: Long? = null, val error: OpError? = null)
@Serializable data class UploadResult(val clientEventId: String, val serverRevision: Long, val results: List<OpResult>)
@Serializable data class ChangeDto(val revision: Long, val profileId: String, val type: String, val savedAt: String, val payload: JsonObject)
@Serializable data class DownloadResult(val changes: List<ChangeDto>, val nextCursor: String, val hasMore: Boolean)

// Retrofit 변환기는 ApiJson을 쓴다. kotlinx 기본 판별자도 "type"이라 두 설정의 인코딩이 같다.
val SyncJson: Json = Json(ApiJson) { classDiscriminator = "type" }
