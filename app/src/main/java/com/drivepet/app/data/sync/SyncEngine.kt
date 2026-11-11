package com.drivepet.app.data.sync

import com.drivepet.app.data.remote.DrivePetApi
import com.drivepet.app.data.remote.Envelope
import com.drivepet.app.data.remote.TokenStore
import com.drivepet.app.data.remote.apiCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

sealed interface SyncOutcome {
    data object NotLinked : SyncOutcome
    data class Done(val uploaded: Int, val rejected: Int, val downloaded: Int) : SyncOutcome
}

private const val SCHEMA_VERSION = 1
private const val UPLOAD_CHUNK = 100
private const val DOWNLOAD_LIMIT = 200
private const val NOT_LINKED = "KAKAO_NOT_LINKED"
private val OK = setOf("APPLIED", "DUPLICATE")
private val FAILED = setOf("CONFLICT", "REJECTED")

/** 다른 곳에서 카카오 연결이 풀림(403 KAKAO_NOT_LINKED). */
private class Unlinked : Exception()

/**
 * 올리기(프로필 생성 → 기록 추가) → 내려받기. 미연결이면 아무것도 하지 않는다.
 * IOException·HTTP 오류는 밖으로 던진다(워커가 재시도). 이미 표시한 것은 표시된 채로 남는다.
 */
class SyncEngine(
    private val api: suspend () -> DrivePetApi,
    private val store: SyncStore,
    private val tokens: TokenStore,
) {
    private val mutex = Mutex()

    suspend fun run(): SyncOutcome = mutex.withLock {
        val account = tokens.get().accountId ?: return SyncOutcome.NotLinked
        try {
            sync(account)
        } catch (e: Unlinked) {
            // 그사이 다른 계정으로 연결됐으면 그대로 둔다.
            tokens.update { if (it.accountId == account) it.copy(accountId = null, nickname = null) else it }
            SyncOutcome.NotLinked
        }
    }

    private suspend fun sync(account: String): SyncOutcome {
        var uploaded = 0
        var rejected = 0

        for (chunk in store.profilesToCreate(account).chunked(UPLOAD_CHUNK)) {
            val ops = chunk.map { CreateProfileOp(createOpId(it.profileId), it.profileId, it.toState()) }
            val byOp = ops.associate { it.operationId to it.profileId }
            for (r in upload(account, ops).results) {
                val profileId = byOp[r.operationId] ?: continue
                val rev = when {
                    r.status in OK -> r.revision
                    // 이미 내 계정에 있음(앞 응답을 못 받은 뒤 상태가 바뀜): 서버 상태를 따르고 내려받기가 덮어쓴다
                    r.status == "CONFLICT" && r.error?.code == "PROFILE_ALREADY_EXISTS" -> r.currentStateRevision
                    else -> null
                }
                if (rev != null) { store.markProfileCreated(profileId, rev); uploaded++ } else rejected++
            }
        }

        while (true) {
            val records = store.pendingRecords(account, UPLOAD_CHUNK)
            if (records.isEmpty()) break
            val ops = records.map { AppendRecordOp(appendOpId(it.recordId), it.profileId, it.toSyncRecord()) }
            val byOp = ops.associate { it.operationId to it.record.recordId }
            val marks = mutableMapOf<String, String>()
            for (r in upload(account, ops).results) {
                val recordId = byOp[r.operationId] ?: continue
                when (r.status) {
                    in OK -> { marks[recordId] = "SYNCED"; uploaded++ }
                    in FAILED -> { marks[recordId] = r.error?.code ?: r.status; rejected++ }
                }
            }
            if (marks.isEmpty()) break // 서버가 아무것도 처리하지 않았으면 같은 묶음을 끝없이 보내지 않는다
            store.markRecords(marks)
        }

        var downloaded = 0
        var cursor = store.cursor(account)
        do {
            val page = call { it.download(SCHEMA_VERSION, cursor, DOWNLOAD_LIMIT) }
            store.applyPage(account, page.changes.mapNotNull { it.toLocal(account, store.profile(it.profileId)) }, page.nextCursor)
            downloaded += page.changes.size
            val stuck = page.nextCursor == cursor // 커서가 안 움직이면 hasMore여도 멈춘다
            cursor = page.nextCursor
        } while (page.hasMore && !stuck)

        return SyncOutcome.Done(uploaded, rejected, downloaded)
    }

    /** 같은 clientEventId에 다른 본문이 이미 있으면(409) 새 id로 한 번 다시 보낸다. */
    private suspend fun upload(account: String, ops: List<SyncOp>): UploadResult {
        val req = UploadRequest(SCHEMA_VERSION, clientEventId(account, ops), ops)
        return try {
            call { it.upload(req) }
        } catch (e: HttpException) {
            if (e.code() != 409 || e.apiCode() != "IDEMPOTENCY_CONFLICT") throw e
            call { it.upload(req.copy(clientEventId = UUID.randomUUID().toString())) }
        }
    }

    private suspend fun <T> call(block: suspend (DrivePetApi) -> Envelope<T>): T {
        val env = try {
            block(api())
        } catch (e: HttpException) {
            if (e.apiCode() == NOT_LINKED) throw Unlinked()
            throw e
        }
        if (env.error?.code == NOT_LINKED) throw Unlinked()
        return env.data ?: throw IOException("동기화 응답에 data가 없습니다: ${env.error?.code}")
    }
}
