package com.drivepet.app.data.sync

import androidx.room.withTransaction
import com.drivepet.app.data.db.AppDatabase
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.db.SyncCursorEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 서버에서 받은 변경을 로컬에 반영하는 단위. */
sealed interface LocalChange {
    /** 있으면 batteryKwh·createdAt은 기존 값 유지, 나머지 덮어씀. */
    data class UpsertProfile(val profile: ProfileEntity) : LocalChange
    /** 같은 recordId면 그대로(IGNORE). */
    data class InsertRecord(val record: RecordEntity) : LocalChange
    data class DeleteProfile(val profileId: String, val deletedAt: Long) : LocalChange
}

interface SyncStore {
    /** accountId 일치, stateRevision null, 미삭제. */
    suspend fun profilesToCreate(accountId: String): List<ProfileEntity>
    /** accountId 일치, syncState null, 프로필이 stateRevision != null·미삭제. occurredAt, recordId 순. */
    suspend fun pendingRecords(accountId: String, limit: Int): List<RecordEntity>
    /** 위 두 개의 합(UI·트리거용). */
    fun pendingCount(accountId: String): Flow<Int>
    suspend fun markProfileCreated(profileId: String, stateRevision: Long)
    /** recordId → syncState. */
    suspend fun markRecords(states: Map<String, String>)
    /** accountId IS NULL인 profile·record를 이 계정으로. */
    suspend fun adoptUnowned(accountId: String)
    suspend fun cursor(accountId: String): String?
    /** 한 트랜잭션 안에서 반영하고 커서를 저장한다. 같은 입력을 다시 적용해도 결과가 같다. */
    suspend fun applyPage(accountId: String, changes: List<LocalChange>, nextCursor: String)
}

class RoomSyncStore(private val db: AppDatabase) : SyncStore {
    private val dao = db.syncDao()

    override suspend fun profilesToCreate(accountId: String) = dao.profilesToCreate(accountId)
    override suspend fun pendingRecords(accountId: String, limit: Int) = dao.pendingRecords(accountId, limit)
    override fun pendingCount(accountId: String): Flow<Int> =
        combine(dao.profilesToCreateCount(accountId), dao.pendingRecordsCount(accountId)) { a, b -> a + b }

    override suspend fun markProfileCreated(profileId: String, stateRevision: Long) =
        dao.markProfileCreated(profileId, stateRevision)

    override suspend fun markRecords(states: Map<String, String>) {
        db.withTransaction { states.forEach { (id, s) -> dao.markRecord(id, s) } }
    }

    override suspend fun adoptUnowned(accountId: String) {
        db.withTransaction { dao.adoptProfiles(accountId); dao.adoptRecords(accountId) }
    }

    override suspend fun cursor(accountId: String) = dao.cursor(accountId)

    override suspend fun applyPage(accountId: String, changes: List<LocalChange>, nextCursor: String) {
        db.withTransaction {
            for (c in changes) when (c) {
                is LocalChange.UpsertProfile -> {
                    val old = dao.profile(c.profile.profileId)
                    dao.putProfile(if (old == null) c.profile else c.profile.copy(batteryKwh = old.batteryKwh, createdAt = old.createdAt))
                }
                is LocalChange.InsertRecord -> dao.insertRecord(c.record)
                is LocalChange.DeleteProfile -> dao.markProfileDeleted(c.profileId, c.deletedAt)
            }
            dao.saveCursor(SyncCursorEntity(accountId, nextCursor))
        }
    }
}
