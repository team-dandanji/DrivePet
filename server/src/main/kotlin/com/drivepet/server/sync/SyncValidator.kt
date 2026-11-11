package com.drivepet.server.sync

import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/** 작업 내용 검증 실패 → HTTP 200 안의 REJECTED(VALIDATION_FAILED) */
class OperationInvalid(message: String) : RuntimeException(message)

/** 요청 봉투·작업 구조 오류 → HTTP 400 */
class EnvelopeInvalid(message: String, val unsupportedVersion: Boolean = false) : RuntimeException(message)

data class ParsedOperation(
    val operationId: String,
    val type: String,
    val profileId: UUID,
    val node: JsonNode,
)

/**
 * 명세(팀 노션 「DrivePet API 명세서」의 동기화·펫 케어·주행 스키마)의 동기화 스키마 검증.
 * 서버는 EXP·점수를 다시 계산하지 않고 형식과 일관성(거리 차이, 서울 날짜 등)만 본다.
 */
object SyncValidator {
    private val SEOUL = ZoneId.of("Asia/Seoul")
    private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val EXP_RE = Regex("^(0|[1-9][0-9]*)\\.[0-9]{3}$")
    private val PERIOD_RE = Regex("^(\\d{4}-\\d{2}-\\d{2}|ONCE)$")
    private val MEASUREMENT = setOf("MEASURED", "UNMEASURED", "UNSUPPORTED", "PERMISSION_DENIED", "STALE")

    private val OP_FIELDS = mapOf(
        "CREATE_PROFILE" to setOf("operationId", "type", "profileId", "state"),
        "UPDATE_STATE" to setOf("operationId", "type", "profileId", "baseStateRevision", "state"),
        "APPEND_RECORD" to setOf("operationId", "type", "profileId", "record"),
        "DELETE_PROFILE" to setOf("operationId", "type", "profileId", "baseProfileRevision"),
    )

    fun isUuid(s: String?) = s != null && UUID_RE.matches(s)

    // ---- 봉투 (400) ----

    fun parseEnvelope(body: JsonNode): Pair<UUID, List<ParsedOperation>> {
        if (!body.isObject) throw EnvelopeInvalid("요청 본문이 객체가 아닙니다")
        val extra = body.propertyNames().toSet() - setOf("schemaVersion", "clientEventId", "operations")
        if (extra.isNotEmpty()) throw EnvelopeInvalid("알 수 없는 필드가 있습니다")
        val version = body.get("schemaVersion")
        if (version == null || !version.isIntegralNumber) throw EnvelopeInvalid("schemaVersion이 필요합니다")
        if (version.longValue() != 1L) throw EnvelopeInvalid("지원하지 않는 동기화 버전입니다", unsupportedVersion = true)
        val clientEventId = body.get("clientEventId")?.takeIf { it.isString }?.asString()
        if (!isUuid(clientEventId)) throw EnvelopeInvalid("clientEventId가 올바르지 않습니다")
        val ops = body.get("operations")
        if (ops == null || !ops.isArray || ops.size() < 1 || ops.size() > 100) throw EnvelopeInvalid("operations는 1~100개여야 합니다")
        return UUID.fromString(clientEventId) to ops.toList().map(::parseOperation)
    }

    private fun parseOperation(op: JsonNode): ParsedOperation {
        if (!op.isObject) throw EnvelopeInvalid("작업이 객체가 아닙니다")
        val type = op.get("type")?.takeIf { it.isString }?.asString()
        val fields = OP_FIELDS[type] ?: throw EnvelopeInvalid("알 수 없는 작업 종류입니다")
        val names = op.propertyNames().toSet()
        if (names != fields) throw EnvelopeInvalid("$type 작업의 필드가 올바르지 않습니다")
        val operationId = op.get("operationId").takeIf { it.isString }?.asString()
        val profileId = op.get("profileId").takeIf { it.isString }?.asString()
        if (!isUuid(operationId) || !isUuid(profileId)) throw EnvelopeInvalid("operationId·profileId가 올바르지 않습니다")
        when (type) {
            "CREATE_PROFILE", "UPDATE_STATE" -> if (!op.get("state").isObject) throw EnvelopeInvalid("state는 객체여야 합니다")
            "APPEND_RECORD" -> if (!op.get("record").isObject) throw EnvelopeInvalid("record는 객체여야 합니다")
        }
        listOf("baseStateRevision", "baseProfileRevision").forEach { f ->
            op.get(f)?.let { if (!it.isIntegralNumber || it.longValue() < 0) throw EnvelopeInvalid("$f 값이 올바르지 않습니다") }
        }
        return ParsedOperation(operationId!!, type!!, UUID.fromString(profileId), op)
    }

    // ---- 작업 내용 (REJECTED) ----

    private fun fail(msg: String): Nothing = throw OperationInvalid(msg)

    private fun JsonNode.req(name: String): JsonNode = get(name) ?: fail("$name 필드가 없습니다")

    private fun onlyFields(node: JsonNode, allowed: Set<String>, what: String) {
        val extra = node.propertyNames().toSet() - allowed
        if (extra.isNotEmpty()) fail("$what 에 허용되지 않는 필드가 있습니다")
    }

    private fun str(node: JsonNode, name: String, min: Int = 0, max: Int = Int.MAX_VALUE): String {
        val v = node.req(name)
        if (!v.isString) fail("$name 은 문자열이어야 합니다")
        val s = v.asString()
        if (s.length < min || s.length > max) fail("$name 길이가 올바르지 않습니다")
        return s
    }

    private fun nullable(node: JsonNode, name: String): JsonNode? = node.req(name).takeIf { !it.isNull }

    private fun int(v: JsonNode, name: String, min: Long? = null, max: Long? = null): Long {
        if (!v.isIntegralNumber || !v.canConvertToLong()) fail("$name 은 정수여야 합니다")
        val n = v.longValue()
        if ((min != null && n < min) || (max != null && n > max)) fail("$name 범위가 올바르지 않습니다")
        return n
    }

    private fun date(s: String, name: String): LocalDate = runCatching { LocalDate.parse(s) }.getOrElse { fail("$name 날짜 형식이 올바르지 않습니다") }

    private fun dateTime(s: String, name: String): OffsetDateTime =
        runCatching { OffsetDateTime.parse(s) }.getOrElse { fail("$name 시각 형식이 올바르지 않습니다(오프셋 포함)") }

    private fun exp(v: JsonNode, name: String) {
        if (!v.isString || !EXP_RE.matches(v.asString())) fail("$name 은 소수 셋째 자리 EXP 문자열이어야 합니다")
    }

    private fun enum(v: JsonNode, name: String, values: Set<String>): String {
        if (!v.isString || v.asString() !in values) fail("$name 값이 올바르지 않습니다")
        return v.asString()
    }

    /** ProfileState 전체 검증. 반환: 소모품 itemId 목록 */
    fun validateState(state: JsonNode): List<String> {
        onlyFields(state, setOf("vehicleName", "fuelType", "starterType", "petNickname", "onboardingCompleted", "inspectionDueDate", "maintenanceItems"), "state")
        str(state, "vehicleName", 1, 40)
        enum(state.req("fuelType"), "fuelType", setOf("EV", "ICE", "HEV"))
        enum(state.req("starterType"), "starterType", setOf("STARTER_1", "STARTER_2", "STARTER_3"))
        str(state, "petNickname", 2, 8)
        if (!state.req("onboardingCompleted").isBoolean) fail("onboardingCompleted 는 참·거짓이어야 합니다")
        nullable(state, "inspectionDueDate")?.let { if (!it.isString) fail("inspectionDueDate 형식 오류"); date(it.asString(), "inspectionDueDate") }
        val items = state.req("maintenanceItems")
        if (!items.isArray) fail("maintenanceItems 는 배열이어야 합니다")
        val ids = items.toList().map { item ->
            if (!item.isObject) fail("소모품 항목이 객체가 아닙니다")
            onlyFields(item, setOf("itemId", "name", "intervalKm", "baselineOdometerMm", "baselineServicedOn", "baselineServicedPeriod", "archived"), "소모품")
            val id = str(item, "itemId")
            if (!isUuid(id)) fail("itemId 가 UUID가 아닙니다")
            str(item, "name", 1, 40)
            nullable(item, "intervalKm")?.let { int(it, "intervalKm", min = 1) }
            nullable(item, "baselineOdometerMm")?.let { int(it, "baselineOdometerMm", min = 0) }
            val on = nullable(item, "baselineServicedOn")?.also { if (!it.isString) fail("baselineServicedOn 형식 오류"); date(it.asString(), "baselineServicedOn") }
            val period = nullable(item, "baselineServicedPeriod")?.let { enum(it, "baselineServicedPeriod", setOf("WITHIN_3_MONTHS", "ABOUT_6_MONTHS", "OVER_1_YEAR", "UNKNOWN")) }
            if (on != null && period != null) fail("baselineServicedOn 이 있으면 baselineServicedPeriod 는 null이어야 합니다")
            if (!item.req("archived").isBoolean) fail("archived 는 참·거짓이어야 합니다")
            id.lowercase()
        }
        if (ids.toSet().size != ids.size) fail("같은 itemId가 두 번 나옵니다")
        return ids
    }

    data class RecordInfo(val recordId: UUID, val kind: String, val occurredAt: OffsetDateTime, val ruleVersion: String, val mission: Mission?)
    data class Mission(val missionId: String, val periodKey: String, val rewardExp: String)

    fun validateRecord(record: JsonNode): RecordInfo {
        val recordId = str(record, "recordId")
        if (!isUuid(recordId)) fail("recordId 가 UUID가 아닙니다")
        val kind = enum(record.req("kind"), "kind", setOf("TRIP", "ATTENDANCE", "REPLACEMENT", "MISSION_CLAIM"))
        val occurredAt = dateTime(str(record, "occurredAt"), "occurredAt")
        val ruleVersion = enum(record.req("ruleVersion"), "ruleVersion", setOf("growth-v1"))
        val payload = record.req("payload")
        if (!payload.isObject) fail("payload 는 객체여야 합니다")
        val seoulDate = occurredAt.atZoneSameInstant(SEOUL).toLocalDate()
        var mission: Mission? = null
        when (kind) {
            "ATTENDANCE" -> {
                onlyFields(payload, setOf("localDate"), "출석 payload")
                if (date(str(payload, "localDate"), "localDate") != seoulDate) fail("localDate 가 occurredAt의 서울 날짜와 다릅니다")
            }
            "REPLACEMENT" -> {
                onlyFields(payload, setOf("itemId", "localDate", "odometerMm"), "교체 payload")
                if (!isUuid(str(payload, "itemId"))) fail("itemId 가 UUID가 아닙니다")
                if (date(str(payload, "localDate"), "localDate") != seoulDate) fail("localDate 가 occurredAt의 서울 날짜와 다릅니다")
                nullable(payload, "odometerMm")?.let { int(it, "odometerMm", min = 0) }
            }
            "MISSION_CLAIM" -> {
                onlyFields(payload, setOf("missionId", "periodKey", "rewardExp", "evidenceRecordIds"), "미션 payload")
                val missionId = str(payload, "missionId", 1)
                val periodKey = str(payload, "periodKey")
                if (!PERIOD_RE.matches(periodKey)) fail("periodKey 형식이 올바르지 않습니다")
                exp(payload.req("rewardExp"), "rewardExp")
                val evidence = payload.req("evidenceRecordIds")
                if (!evidence.isArray || evidence.any { !it.isString || !isUuid(it.asString()) }) fail("evidenceRecordIds 는 UUID 배열이어야 합니다")
                mission = Mission(missionId, periodKey, payload.get("rewardExp").asString())
            }
            "TRIP" -> validateTrip(payload)
        }
        return RecordInfo(UUID.fromString(recordId), kind, occurredAt, ruleVersion, mission)
    }

    /** 점수는 MEASURED일 때만 있고, 아니면 null (미측정을 0점 등으로 바꾸지 않는다) */
    private fun measured(score: Long?, status: String, name: String) {
        if (status == "MEASURED" && score == null) fail("$name 상태가 MEASURED인데 값이 없습니다")
        if (status != "MEASURED" && score != null) fail("$name 상태가 $status 이면 값은 null이어야 합니다")
    }

    private fun validateTrip(p: JsonNode) {
        val started = dateTime(str(p, "startedAt"), "startedAt")
        val ended = dateTime(str(p, "endedAt"), "endedAt")
        if (ended.isBefore(started)) fail("endedAt 이 startedAt 보다 이릅니다")
        val start = nullable(p, "startOdometerMm")?.let { int(it, "startOdometerMm", min = 0) }
        val end = nullable(p, "endOdometerMm")?.let { int(it, "endOdometerMm", min = 0) }
        val status = enum(p.req("distanceStatus"), "distanceStatus", setOf("VALID", "UNVERIFIED"))
        val s = p.req("settlement")
        if (!s.isObject) fail("settlement 는 객체여야 합니다")
        val distance = nullable(s, "distanceMm")?.let { int(it, "distanceMm", min = 0) }
        val base = nullable(s, "baseDrivingExp")
        base?.let { exp(it, "baseDrivingExp") }
        exp(s.req("bonusExp"), "bonusExp")
        val safety = nullable(s, "safetyScore")?.let { int(it, "safetyScore", 0, 100) }
        measured(safety, enum(s.req("safetyStatus"), "safetyStatus", MEASUREMENT), "safetyScore")
        nullable(s, "safetyBreakdown")?.let { arr ->
            if (!arr.isArray) fail("safetyBreakdown 은 배열이어야 합니다")
            arr.forEach { item ->
                if (!item.isObject) fail("safetyBreakdown 항목이 객체가 아닙니다")
                onlyFields(item, setOf("item", "score", "maxScore", "status", "value"), "safetyBreakdown 항목")
                enum(item.req("item"), "item", setOf("ACCEL_BRAKE", "STEERING", "EFFICIENCY", "HVAC_OVERLAP", "TURN_SIGNAL"))
                val score = nullable(item, "score")?.let { int(it, "score", min = 0) }
                val max = int(item.req("maxScore"), "maxScore", min = 1)
                if (score != null && score > max) fail("score 가 maxScore 보다 큽니다")
                measured(score, enum(item.req("status"), "status", MEASUREMENT), "score")
                nullable(item, "value")?.let { if (!it.isNumber) fail("value 는 숫자여야 합니다") }
            }
        }
        val health = nullable(s, "healthScore")?.let { int(it, "healthScore", 0, 100) }
        measured(health, enum(s.req("healthStatus"), "healthStatus", MEASUREMENT), "healthScore")
        p.get("reflection")?.takeIf { !it.isNull }?.let { if (!it.isString || it.asString().length > 2000) fail("reflection 은 2000자 이하 문자열이어야 합니다") }
        if (status == "VALID") {
            if (start == null || end == null || end < start) fail("VALID 트립은 시작·종료 누적거리가 있고 종료 ≥ 시작이어야 합니다")
            if (distance != end - start) fail("VALID 트립의 distanceMm 이 누적거리 차이와 다릅니다")
            if (base == null) fail("VALID 트립은 baseDrivingExp 가 필요합니다")
        } else {
            if (distance != null || base != null) fail("UNVERIFIED 트립은 distanceMm·baseDrivingExp 가 null이어야 합니다")
        }
    }
}
