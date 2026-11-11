package com.drivepet.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE deletedAt IS NULL ORDER BY createdAt")
    fun profiles(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProfileEntity)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM record WHERE profileId = :profileId ORDER BY occurredAt, recordId")
    fun records(profileId: String): Flow<List<RecordEntity>>

    /** 같은 recordId면 -1L. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(r: RecordEntity): Long
}

@Dao
interface TripInProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(t: TripInProgressEntity)

    @Query("SELECT * FROM trip_in_progress WHERE profileId = :profileId")
    suspend fun get(profileId: String): TripInProgressEntity?

    @Query("DELETE FROM trip_in_progress WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}

@Dao
interface InterruptedTripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(t: InterruptedTripEntity)

    @Query("SELECT * FROM interrupted_trip WHERE profileId = :profileId ORDER BY startedAt DESC")
    fun trips(profileId: String): Flow<List<InterruptedTripEntity>>

    @Query("DELETE FROM interrupted_trip WHERE profileId = :profileId AND startedAt = :startedAt")
    suspend fun delete(profileId: String, startedAt: Long)
}

@Dao
interface WarningSeenDao {
    @Query("SELECT * FROM warning_seen WHERE profileId = :profileId")
    fun seen(profileId: String): Flow<List<WarningSeenEntity>>

    @Query("SELECT name FROM warning_seen WHERE profileId = :profileId")
    suspend fun names(profileId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<WarningSeenEntity>)

    @Query("DELETE FROM warning_seen WHERE profileId = :profileId AND name NOT IN (:keep)")
    suspend fun deleteExcept(profileId: String, keep: List<String>)

    /** 새 이름은 today로 추가(기존 이름은 첫 날짜 유지), 꺼진 이름은 삭제. */
    @Transaction
    suspend fun sync(profileId: String, names: List<String>, today: String) {
        insertAll(names.map { WarningSeenEntity(profileId, it, today) })
        deleteExcept(profileId, names)
    }
}

@Dao
interface DevSeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(s: DevSeedEntity)

    @Query("SELECT seedMilli FROM dev_seed WHERE profileId = :profileId")
    fun seed(profileId: String): Flow<Long?>
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM profile WHERE accountId = :accountId AND stateRevision IS NULL AND deletedAt IS NULL ORDER BY createdAt")
    suspend fun profilesToCreate(accountId: String): List<ProfileEntity>

    @Query(
        "SELECT r.* FROM record r JOIN profile p ON p.profileId = r.profileId " +
            "WHERE r.accountId = :accountId AND r.syncState IS NULL AND p.stateRevision IS NOT NULL AND p.deletedAt IS NULL " +
            "ORDER BY r.occurredAt, r.recordId LIMIT :limit",
    )
    suspend fun pendingRecords(accountId: String, limit: Int): List<RecordEntity>

    @Query("SELECT COUNT(*) FROM profile WHERE accountId = :accountId AND stateRevision IS NULL AND deletedAt IS NULL")
    fun profilesToCreateCount(accountId: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM record r JOIN profile p ON p.profileId = r.profileId " +
            "WHERE r.accountId = :accountId AND r.syncState IS NULL AND p.stateRevision IS NOT NULL AND p.deletedAt IS NULL",
    )
    fun pendingRecordsCount(accountId: String): Flow<Int>

    @Query("UPDATE profile SET stateRevision = :stateRevision WHERE profileId = :profileId")
    suspend fun markProfileCreated(profileId: String, stateRevision: Long)

    @Query("UPDATE record SET syncState = :syncState WHERE recordId = :recordId")
    suspend fun markRecord(recordId: String, syncState: String)

    @Query("UPDATE profile SET accountId = :accountId WHERE accountId IS NULL")
    suspend fun adoptProfiles(accountId: String)

    @Query("UPDATE record SET accountId = :accountId WHERE accountId IS NULL")
    suspend fun adoptRecords(accountId: String)

    @Query("SELECT cursor FROM sync_cursor WHERE accountId = :accountId")
    suspend fun cursor(accountId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCursor(c: SyncCursorEntity)

    @Query("SELECT * FROM profile WHERE profileId = :profileId")
    suspend fun profile(profileId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProfile(p: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(r: RecordEntity)

    @Query("UPDATE profile SET deletedAt = :deletedAt WHERE profileId = :profileId")
    suspend fun markProfileDeleted(profileId: String, deletedAt: Long)
}
