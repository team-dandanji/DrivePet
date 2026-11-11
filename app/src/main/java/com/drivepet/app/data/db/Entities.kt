package com.drivepet.app.data.db

import androidx.room.Entity
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
)

/** kind: TRIP | ATTENDANCE | REPLACEMENT | MISSION_CLAIM. payloadJson은 동기화 body, 나머지 열은 원장 계산용 사본. */
@Entity(tableName = "record")
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
