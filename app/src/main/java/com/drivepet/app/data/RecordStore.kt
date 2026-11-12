package com.drivepet.app.data

import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.db.DevSeedEntity
import com.drivepet.app.data.db.InterruptedTripEntity
import com.drivepet.app.data.db.MaintenanceItemEntity
import com.drivepet.app.data.db.VehicleCareEntity
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.db.TripInProgressEntity
import com.drivepet.app.data.db.WarningSeenEntity
import com.drivepet.app.domain.AttendanceLedger
import com.drivepet.app.domain.LedgerRecord
import com.drivepet.app.domain.MissionLedger
import com.drivepet.app.domain.ReplacementLedger
import com.drivepet.app.domain.TripLedger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

interface RecordStore {
    fun profiles(): Flow<List<ProfileEntity>>
    suspend fun upsertProfile(p: ProfileEntity)
    fun records(profileId: String): Flow<List<RecordEntity>>
    /** 같은 recordId면 false. */
    suspend fun insertIfAbsent(r: RecordEntity): Boolean
    suspend fun saveInProgress(t: TripInProgressEntity)
    suspend fun inProgress(profileId: String): TripInProgressEntity?
    suspend fun clearInProgress(profileId: String)
    /** 같은 (profileId, startedAt)이면 덮어쓴다. */
    suspend fun saveInterrupted(t: InterruptedTripEntity)
    /** 최신 시작순. */
    fun interruptedTrips(profileId: String): Flow<List<InterruptedTripEntity>>
    suspend fun deleteInterrupted(profileId: String, startedAt: Long)
    /** 새 이름은 today로 추가, 꺼진 이름은 삭제. */
    suspend fun syncWarnings(profileId: String, names: List<String>, today: LocalDate)
    fun warningsSeen(profileId: String): Flow<List<WarningSeenEntity>>
    suspend fun setSeed(profileId: String, milli: Long)
    fun seed(profileId: String): Flow<Long>
    /** archived 제외. */
    fun maintenanceItems(profileId: String): Flow<List<MaintenanceItemEntity>>
    /** 같은 itemId면 덮어쓴다. */
    suspend fun saveMaintenanceItems(items: List<MaintenanceItemEntity>)
    /** 그 프로필 항목이 0개일 때만 [defaults]를 넣는다. */
    suspend fun ensureMaintenanceItems(profileId: String, defaults: () -> List<MaintenanceItemEntity>)
    fun vehicleCare(profileId: String): Flow<VehicleCareEntity?>
    suspend fun setVehicleCare(c: VehicleCareEntity)
}

class RoomRecordStore(db: AppDatabase) : RecordStore {
    private val profiles = db.profileDao()
    private val records = db.recordDao()
    private val inProgress = db.tripInProgressDao()
    private val interrupted = db.interruptedTripDao()
    private val warnings = db.warningSeenDao()
    private val seeds = db.devSeedDao()
    private val care = db.maintenanceDao()

    override fun profiles() = profiles.profiles()
    override suspend fun upsertProfile(p: ProfileEntity) = profiles.upsert(p)
    override fun records(profileId: String) = records.records(profileId)
    override suspend fun insertIfAbsent(r: RecordEntity) = records.insertIfAbsent(r) != -1L
    override suspend fun saveInProgress(t: TripInProgressEntity) = inProgress.save(t)
    override suspend fun inProgress(profileId: String) = inProgress.get(profileId)
    override suspend fun clearInProgress(profileId: String) = inProgress.clear(profileId)
    override suspend fun saveInterrupted(t: InterruptedTripEntity) = interrupted.save(t)
    override fun interruptedTrips(profileId: String) = interrupted.trips(profileId)
    override suspend fun deleteInterrupted(profileId: String, startedAt: Long) = interrupted.delete(profileId, startedAt)
    override suspend fun syncWarnings(profileId: String, names: List<String>, today: LocalDate) =
        warnings.sync(profileId, names, today.toString())
    override fun warningsSeen(profileId: String) = warnings.seen(profileId)
    override suspend fun setSeed(profileId: String, milli: Long) = seeds.set(DevSeedEntity(profileId, milli))
    override fun seed(profileId: String): Flow<Long> = seeds.seed(profileId).map { it ?: 0L }
    override fun maintenanceItems(profileId: String) = care.items(profileId)
    override suspend fun saveMaintenanceItems(items: List<MaintenanceItemEntity>) = care.upsertAll(items)
    override suspend fun ensureMaintenanceItems(profileId: String, defaults: () -> List<MaintenanceItemEntity>) = care.ensure(profileId, defaults())
    override fun vehicleCare(profileId: String) = care.care(profileId)
    override suspend fun setVehicleCare(c: VehicleCareEntity) = care.setCare(c)
}

fun RecordEntity.toLedger(): LedgerRecord {
    val at = Instant.ofEpochMilli(occurredAt)
    return when (kind) {
        "TRIP" -> TripLedger(recordId, at, startMm, endMm, valid ?: false, bonusMilli ?: 0L)
        "ATTENDANCE" -> AttendanceLedger(recordId, at, LocalDate.parse(localDate))
        "REPLACEMENT" -> ReplacementLedger(recordId, at, itemId!!, LocalDate.parse(localDate))
        "MISSION_CLAIM" -> MissionLedger(recordId, at, missionId!!, periodKey!!, rewardMilli!!)
        else -> throw IllegalStateException("unknown record kind: $kind")
    }
}
