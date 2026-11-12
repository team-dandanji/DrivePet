package com.drivepet.app.data

import com.drivepet.app.data.db.InterruptedTripEntity
import com.drivepet.app.data.db.MaintenanceItemEntity
import com.drivepet.app.data.db.VehicleCareEntity
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.db.TripInProgressEntity
import com.drivepet.app.data.db.WarningSeenEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** 메모리 RecordStore. 단위 테스트용. */
class FakeRecordStore : RecordStore {
    private val profiles = MutableStateFlow<Map<String, ProfileEntity>>(emptyMap())
    private val records = MutableStateFlow<Map<String, RecordEntity>>(emptyMap())
    private val inProgress = MutableStateFlow<Map<String, TripInProgressEntity>>(emptyMap())
    private val interrupted = MutableStateFlow<List<InterruptedTripEntity>>(emptyList())
    private val warnings = MutableStateFlow<Map<String, List<WarningSeenEntity>>>(emptyMap())
    private val seeds = MutableStateFlow<Map<String, Long>>(emptyMap())

    override fun profiles(): Flow<List<ProfileEntity>> = profiles.map { it.values.toList() }
    override suspend fun upsertProfile(p: ProfileEntity) = profiles.update { it + (p.profileId to p) }
    override fun records(profileId: String): Flow<List<RecordEntity>> =
        records.map { m -> m.values.filter { it.profileId == profileId }.sortedBy { it.occurredAt } }

    override suspend fun insertIfAbsent(r: RecordEntity): Boolean {
        var inserted = false
        records.update { inserted = r.recordId !in it; if (inserted) it + (r.recordId to r) else it }
        return inserted
    }

    override suspend fun saveInProgress(t: TripInProgressEntity) = inProgress.update { it + (t.profileId to t) }
    override suspend fun inProgress(profileId: String) = inProgress.value[profileId]
    override suspend fun clearInProgress(profileId: String) = inProgress.update { it - profileId }

    override suspend fun saveInterrupted(t: InterruptedTripEntity) =
        interrupted.update { l -> l.filterNot { it.profileId == t.profileId && it.startedAt == t.startedAt } + t }
    override fun interruptedTrips(profileId: String): Flow<List<InterruptedTripEntity>> =
        interrupted.map { l -> l.filter { it.profileId == profileId }.sortedByDescending { it.startedAt } }
    override suspend fun deleteInterrupted(profileId: String, startedAt: Long) =
        interrupted.update { l -> l.filterNot { it.profileId == profileId && it.startedAt == startedAt } }

    override suspend fun syncWarnings(profileId: String, names: List<String>, today: LocalDate) {
        warnings.update { m ->
            val old = m[profileId].orEmpty().filter { it.name in names }
            val added = names.filter { n -> old.none { it.name == n } }
                .map { WarningSeenEntity(profileId, it, today.toString()) }
            m + (profileId to old + added)
        }
    }

    override fun warningsSeen(profileId: String): Flow<List<WarningSeenEntity>> =
        warnings.map { it[profileId].orEmpty() }

    override suspend fun setSeed(profileId: String, milli: Long) = seeds.update { it + (profileId to milli) }
    override fun seed(profileId: String): Flow<Long> = seeds.map { it[profileId] ?: 0L }

    private val items = MutableStateFlow<Map<String, MaintenanceItemEntity>>(emptyMap())
    private val cares = MutableStateFlow<Map<String, VehicleCareEntity>>(emptyMap())
    override fun maintenanceItems(profileId: String): Flow<List<MaintenanceItemEntity>> =
        items.map { m -> m.values.filter { it.profileId == profileId && !it.archived } }
    override suspend fun saveMaintenanceItems(items: List<MaintenanceItemEntity>) = this.items.update { m -> m + items.associateBy { it.itemId } }
    override suspend fun ensureMaintenanceItems(profileId: String, defaults: () -> List<MaintenanceItemEntity>) =
        items.update { m -> if (m.values.any { it.profileId == profileId }) m else m + defaults().associateBy { it.itemId } }
    override fun vehicleCare(profileId: String): Flow<VehicleCareEntity?> = cares.map { it[profileId] }
    override suspend fun setVehicleCare(c: VehicleCareEntity) = cares.update { it + (c.profileId to c) }
}
