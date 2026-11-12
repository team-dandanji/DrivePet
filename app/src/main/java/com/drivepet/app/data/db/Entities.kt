package com.drivepet.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val profileId: String,
    val accountId: String?,
    val vehicleName: String,
    val fuelType: String,
    val starterType: String, // STARTER_1..3
    val petNickname: String,
    val onboardingCompleted: Boolean,
    val batteryKwh: Double?,
    val createdAt: Long,
    /** 서버가 준 최신 stateRevision. null = 아직 CREATE_PROFILE 전. */
    val stateRevision: Long? = null,
    /** 다른 기기에서 삭제됨(PROFILE_DELETED). */
    val deletedAt: Long? = null,
)

/** kind: TRIP | ATTENDANCE | REPLACEMENT | MISSION_CLAIM. payloadJson은 동기화 body, 나머지 열은 원장 계산용 사본. */
@Entity(tableName = "record", indices = [Index("profileId")])
data class RecordEntity(
    @PrimaryKey val recordId: String,
    val profileId: String,
    val accountId: String?,
    val kind: String,
    val occurredAt: Long,
    val ruleVersion: String = "growth-v1",
    val payloadJson: String,
    val startMm: Long? = null,
    val endMm: Long? = null,
    val valid: Boolean? = null,
    val bonusMilli: Long? = null,
    val localDate: String? = null,
    val itemId: String? = null,
    val missionId: String? = null,
    val periodKey: String? = null,
    val rewardMilli: Long? = null,
    /** null = 올릴 대상, "SYNCED", 또는 서버 오류 코드(다시 보내지 않음). */
    val syncState: String? = null,
)

/** 계정별 마지막으로 받은 서버 커서. */
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val accountId: String,
    val cursor: String,
)

@Entity(tableName = "trip_in_progress")
data class TripInProgressEntity(
    @PrimaryKey val profileId: String,
    val startedAt: Long,
    val summaryJson: String,
    val metricsJson: String,
    val interrupted: Boolean,
    val updatedAt: Long,
)

/** 끝나지 못하고 중단된 트립(보관만, EXP 없음). 이 기기에만 남고 동기화하지 않는다. */
@Entity(tableName = "interrupted_trip", primaryKeys = ["profileId", "startedAt"])
data class InterruptedTripEntity(
    val profileId: String,
    val startedAt: Long,
    val summaryJson: String,
    val distanceKm: Double?,
    val interruptedAt: Long,
)

@Entity(tableName = "warning_seen", primaryKeys = ["profileId", "name"])
data class WarningSeenEntity(
    val profileId: String,
    val name: String,
    val firstSeenDate: String,
)

/** 개발자 메뉴 "시작 EXP" (debug/demo만 사용, 동기화 안 함). */
@Entity(tableName = "dev_seed")
data class DevSeedEntity(
    @PrimaryKey val profileId: String,
    val seedMilli: Long,
)

/** 소모품 한 항목의 처음 등록 기준(API MaintenanceItemSetting 모양). 기기 안에만 두고 동기화하지 않는다. kind = CareItem 이름. */
@Entity(tableName = "maintenance_item", indices = [Index("profileId")])
data class MaintenanceItemEntity(
    @PrimaryKey val itemId: String,
    val profileId: String,
    val kind: String,
    val intervalKm: Int,
    val baselineOdometerMm: Long?,
    val baselineServicedOn: String?,
    val baselineServicedPeriod: String?,
    val archived: Boolean = false,
)

/** 프로필별 정기검사 예정일(yyyy-MM-dd). profile 열로 두지 않는 이유: ProfileDao.upsert(REPLACE)가 stateRevision을 지운다. */
@Entity(tableName = "vehicle_care")
data class VehicleCareEntity(
    @PrimaryKey val profileId: String,
    val inspectionDueDate: String?,
)
