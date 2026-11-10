package com.drivepet.server.sync

import java.util.UUID

/** 동기화 요청 조각 (명세 예시와 같은 모양) */
object SyncFixtures {
    fun uuid() = UUID.randomUUID().toString()

    /** 기본 소모품 ID는 고정 (상태를 다시 만들어도 같은 소모품) */
    const val ITEM_ID = "00000000-0000-4000-8000-000000000021"

    fun item(id: String = ITEM_ID, name: String = "타이어 위치교환 및 마모 점검") = mapOf(
        "itemId" to id, "name" to name, "intervalKm" to 10000, "baselineOdometerMm" to 10_000_000_000L,
        "baselineServicedOn" to "2026-08-04", "baselineServicedPeriod" to null, "archived" to false,
    )

    fun state(nickname: String = "드리", starter: String = "STARTER_1", items: List<Map<String, Any?>> = listOf(item())) = mapOf(
        "vehicleName" to "출퇴근 차량", "fuelType" to "EV", "starterType" to starter, "petNickname" to nickname,
        "onboardingCompleted" to true, "inspectionDueDate" to null, "maintenanceItems" to items,
    )

    fun create(profileId: String, state: Map<String, Any?> = state()) =
        mapOf("operationId" to uuid(), "type" to "CREATE_PROFILE", "profileId" to profileId, "state" to state)

    fun update(profileId: String, base: Long, state: Map<String, Any?>) =
        mapOf("operationId" to uuid(), "type" to "UPDATE_STATE", "profileId" to profileId, "baseStateRevision" to base, "state" to state)

    fun delete(profileId: String, base: Long) =
        mapOf("operationId" to uuid(), "type" to "DELETE_PROFILE", "profileId" to profileId, "baseProfileRevision" to base)

    fun append(profileId: String, record: Map<String, Any?>) =
        mapOf("operationId" to uuid(), "type" to "APPEND_RECORD", "profileId" to profileId, "record" to record)

    fun attendance(recordId: String = uuid(), occurredAt: String = "2026-11-09T12:00:00+09:00", localDate: String = "2026-11-09") = mapOf(
        "recordId" to recordId, "kind" to "ATTENDANCE", "occurredAt" to occurredAt, "ruleVersion" to "growth-v1",
        "payload" to mapOf("localDate" to localDate),
    )

    fun trip(recordId: String = uuid(), start: Long? = 12_345_600_000L, end: Long? = 12_360_000_000L, distance: Long? = 14_400_000L,
             status: String = "VALID", baseExp: String? = "28.800") = mapOf(
        "recordId" to recordId, "kind" to "TRIP", "occurredAt" to "2026-11-09T08:40:00+09:00", "ruleVersion" to "growth-v1",
        "payload" to mapOf(
            "startedAt" to "2026-11-09T08:05:00+09:00", "endedAt" to "2026-11-09T08:40:00+09:00",
            "startOdometerMm" to start, "endOdometerMm" to end, "distanceStatus" to status,
            "settlement" to mapOf(
                "distanceMm" to distance, "baseDrivingExp" to baseExp, "bonusExp" to "50.000",
                "safetyScore" to 92, "safetyStatus" to "MEASURED",
                "safetyBreakdown" to listOf(mapOf("item" to "STEERING", "score" to 15, "maxScore" to 15, "status" to "MEASURED", "value" to 0)),
                "healthScore" to 86, "healthStatus" to "MEASURED",
            ),
            "reflection" to null,
        ),
    )

    fun mission(recordId: String = uuid(), reward: String = "30.000", period: String = "2026-11-09") = mapOf(
        "recordId" to recordId, "kind" to "MISSION_CLAIM", "occurredAt" to "2026-11-09T09:00:00+09:00", "ruleVersion" to "growth-v1",
        "payload" to mapOf("missionId" to "daily-no-sharp-steering", "periodKey" to period, "rewardExp" to reward, "evidenceRecordIds" to listOf(uuid())),
    )

    fun batch(vararg ops: Map<String, Any?>, clientEventId: String = uuid()) =
        mapOf("schemaVersion" to 1, "clientEventId" to clientEventId, "operations" to ops.toList())
}
