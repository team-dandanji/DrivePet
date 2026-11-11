package com.drivepet.app.data.sync

import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.remote.ApiJson
import com.drivepet.app.data.remote.AuthState
import com.drivepet.app.data.remote.DrivePetApi
import com.drivepet.app.data.remote.Envelope
import com.drivepet.app.data.remote.QrSession
import com.drivepet.app.data.remote.QrStatus
import com.drivepet.app.data.remote.TokenStore
import com.drivepet.app.data.remote.UnlinkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

private class MemoryTokens(initial: AuthState) : TokenStore {
    private val s = MutableStateFlow(initial)
    override suspend fun get() = s.value
    override suspend fun update(f: (AuthState) -> AuthState) { s.value = f(s.value) }
    override val state: Flow<AuthState> = s
}

private class MemoryStore : SyncStore {
    val profiles = mutableMapOf<String, ProfileEntity>()
    val records = mutableMapOf<String, RecordEntity>()
    val cursors = mutableMapOf<String, String>()
    val pages = mutableListOf<List<LocalChange>>()

    override suspend fun profilesToCreate(accountId: String) =
        profiles.values.filter { it.accountId == accountId && it.stateRevision == null && it.deletedAt == null }
    override suspend fun pendingRecords(accountId: String, limit: Int) = records.values
        .filter { r -> r.accountId == accountId && r.syncState == null && profiles[r.profileId]?.let { it.stateRevision != null && it.deletedAt == null } == true }
        .sortedWith(compareBy({ it.occurredAt }, { it.recordId })).take(limit)
    override fun pendingCount(accountId: String): Flow<Int> = flowOf(0)
    override suspend fun markProfileCreated(profileId: String, stateRevision: Long) {
        profiles[profileId] = profiles.getValue(profileId).copy(stateRevision = stateRevision)
    }
    override suspend fun markRecords(states: Map<String, String>) {
        states.forEach { (id, s) -> records[id] = records.getValue(id).copy(syncState = s) }
    }
    override suspend fun adoptUnowned(accountId: String) = Unit
    override suspend fun profile(profileId: String) = profiles[profileId]
    override suspend fun cursor(accountId: String) = cursors[accountId]
    override suspend fun applyPage(accountId: String, changes: List<LocalChange>, nextCursor: String) {
        pages += changes; cursors[accountId] = nextCursor
    }
}

private fun applied(r: UploadRequest) = Envelope(
    UploadResult(r.clientEventId, 9, r.operations.mapIndexed { i, op -> OpResult(op.operationId, "APPLIED", i + 1L) }),
)

private fun httpError(code: Int, errorCode: String) = HttpException(
    Response.error<Any>(code, """{"data":null,"error":{"code":"$errorCode","message":"m"}}""".toResponseBody("application/json".toMediaType())),
)

private class FakeApi : DrivePetApi {
    val uploads = mutableListOf<UploadRequest>()
    val downloads = mutableListOf<String?>()
    var onUpload: (UploadRequest) -> Envelope<UploadResult> = ::applied
    val pages = ArrayDeque<DownloadResult>()

    override suspend fun upload(r: UploadRequest): Envelope<UploadResult> { uploads += r; return onUpload(r) }
    override suspend fun download(v: Int, cursor: String?, limit: Int): Envelope<DownloadResult> {
        downloads += cursor
        return Envelope(pages.removeFirstOrNull() ?: DownloadResult(emptyList(), "c-end", false))
    }
    override suspend fun createQr(): Envelope<QrSession> = TODO()
    override suspend fun qrStatus(id: String): Envelope<QrStatus> = TODO()
    override suspend fun unlink(): Envelope<UnlinkResult> = TODO()
}

class SyncEngineTest {
    private val api = FakeApi()
    private val store = MemoryStore()
    private val tokens = MemoryTokens(AuthState("dev", "secret", "a", "r", "acc", "닉"))
    private val engine = SyncEngine({ api }, store, tokens)

    private fun profile(id: String = "p-1", rev: Long? = 1) =
        ProfileEntity(id, "acc", "차", "EV", "STARTER_1", "드리", true, null, 0L, stateRevision = rev).also { store.profiles[id] = it }

    private fun record(id: String, at: Long, profileId: String = "p-1") =
        RecordEntity(id, profileId, "acc", "ATTENDANCE", at, payloadJson = """{"localDate":"2026-11-10"}""")
            .also { store.records[id] = it }

    @Test fun notLinkedDoesNothing() = runBlocking {
        tokens.update { it.copy(accountId = null) }
        profile(rev = null); record("r1", 1)
        assertEquals(SyncOutcome.NotLinked, engine.run())
        assertTrue(api.uploads.isEmpty())
        assertTrue(api.downloads.isEmpty())
    }

    @Test fun createsProfileThenAppendsRecords() = runBlocking {
        profile(rev = null); record("r1", 200); record("r2", 100)
        val out = engine.run()
        assertEquals(2, api.uploads.size)
        assertEquals(listOf("p-1"), api.uploads[0].operations.map { (it as CreateProfileOp).profileId })
        assertEquals(listOf("r2", "r1"), api.uploads[1].operations.map { (it as AppendRecordOp).record.recordId })
        assertEquals(1L, store.profiles["p-1"]!!.stateRevision)
        assertTrue(store.pendingRecords("acc", 100).isEmpty())
        assertEquals(SyncOutcome.Done(uploaded = 3, rejected = 0, downloaded = 0), out)
    }

    @Test fun conflictMarksAndStopsRetry() = runBlocking {
        profile(); record("r1", 1)
        api.onUpload = { r ->
            Envelope(UploadResult(r.clientEventId, 9, r.operations.map { OpResult(it.operationId, "CONFLICT", null, OpError("RECORD_CONTENT_CONFLICT", "m")) }))
        }
        assertEquals(SyncOutcome.Done(0, 1, 0), engine.run())
        assertEquals("RECORD_CONTENT_CONFLICT", store.records["r1"]!!.syncState)
        engine.run()
        assertEquals(1, api.uploads.size)
    }

    @Test fun sameOpsSameClientEventId() = runBlocking {
        profile(); record("r1", 1)
        api.onUpload = { throw IOException("끊김") }
        assertThrows(IOException::class.java) { runBlocking { engine.run() } }
        api.onUpload = ::applied
        engine.run()
        assertEquals(2, api.uploads.size)
        assertEquals(api.uploads[0], api.uploads[1])
    }

    @Test fun idempotencyConflictRetriesWithNewId() = runBlocking {
        profile(); record("r1", 1)
        var first = true
        api.onUpload = { r -> if (first) { first = false; throw httpError(409, "IDEMPOTENCY_CONFLICT") } else applied(r) }
        engine.run()
        assertEquals(2, api.uploads.size)
        assertEquals(api.uploads[0].operations, api.uploads[1].operations)
        assertNotEquals(api.uploads[0].clientEventId, api.uploads[1].clientEventId)
        assertEquals("SYNCED", store.records["r1"]!!.syncState)
    }

    @Test fun chunksAt100() = runBlocking {
        profile()
        repeat(150) { record("r%03d".format(it), it.toLong()) }
        engine.run()
        assertEquals(listOf(100, 50), api.uploads.map { it.operations.size })
        assertTrue(store.records.values.all { it.syncState == "SYNCED" })
    }

    @Test fun networkErrorLeavesPending() = runBlocking {
        profile(); record("r1", 1); record("r2", 2); record("r3", 3)
        api.onUpload = { throw IOException("끊김") }
        assertThrows(IOException::class.java) { runBlocking { engine.run() } }
        assertTrue(store.records.values.all { it.syncState == null })
    }

    @Test fun downloadPagesUntilDone() = runBlocking {
        store.cursors["acc"] = "c0"
        val rec = buildJsonObject {
            put("recordId", "r9"); put("kind", "ATTENDANCE"); put("occurredAt", "2026-11-10T07:00:00+09:00"); put("ruleVersion", "growth-v1")
            put("payload", buildJsonObject { put("localDate", "2026-11-10") })
        }
        api.pages += DownloadResult(listOf(ChangeDto(3, "p-1", "RECORD_APPENDED", "2026-11-10T07:00:01+09:00", JsonObject(mapOf("record" to rec)))), "c1", true)
        api.pages += DownloadResult(emptyList(), "c2", false)
        val out = engine.run()
        assertEquals(listOf("c0", "c1"), api.downloads)
        assertEquals(2, store.pages.size)
        assertEquals("r9", (store.pages[0].single() as LocalChange.InsertRecord).record.recordId)
        assertEquals("c2", store.cursors["acc"])
        assertEquals(SyncOutcome.Done(0, 0, 1), out)
    }

    @Test fun notLinkedErrorClearsAccount() = runBlocking {
        profile(rev = null)
        api.onUpload = { throw httpError(403, "KAKAO_NOT_LINKED") }
        assertEquals(SyncOutcome.NotLinked, engine.run())
        assertNull(tokens.get().accountId)
        assertNull(tokens.get().nickname)
        assertEquals("dev", tokens.get().deviceId)
    }

    @Test fun alreadyExistsConflictMarksCreated() = runBlocking {
        profile(rev = null); record("r1", 1)
        api.onUpload = { r ->
            if (r.operations.first() is CreateProfileOp) Envelope(UploadResult(r.clientEventId, 9, r.operations.map {
                OpResult(it.operationId, "CONFLICT", null, OpError("PROFILE_ALREADY_EXISTS", "m"), currentStateRevision = 3)
            })) else applied(r)
        }
        engine.run()
        assertEquals(3L, store.profiles["p-1"]!!.stateRevision)
        assertEquals(listOf("r1"), api.uploads[1].operations.map { (it as AppendRecordOp).record.recordId })
        assertEquals("SYNCED", store.records["r1"]!!.syncState)
    }

    @Test fun downloadStopsWhenCursorDoesNotMove() = runBlocking {
        store.cursors["acc"] = "c0"
        repeat(2) { api.pages += DownloadResult(emptyList(), "c0", true) }
        engine.run()
        assertEquals(listOf("c0"), api.downloads)
    }

    @Test fun notLinkedErrorKeepsAccountChangedMidSync() = runBlocking {
        profile(rev = null)
        api.onUpload = {
            runBlocking { tokens.update { it.copy(accountId = "acc2", nickname = "새") } }
            throw httpError(403, "KAKAO_NOT_LINKED")
        }
        assertEquals(SyncOutcome.NotLinked, engine.run())
        assertEquals("acc2", tokens.get().accountId)
        assertEquals("새", tokens.get().nickname)
    }

    /** 가짜 API가 건너뛰는 실제 Retrofit 변환: 판별자 "type", null 커서 생략, 서버 전용 키 무시. */
    @Test fun retrofitWireFormat() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val real = Retrofit.Builder().baseUrl(server.url("/api/v1/"))
                .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType())).build().create(DrivePetApi::class.java)
            fun ok(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            server.enqueue(ok("""{"data":{"clientEventId":"e","serverRevision":3,"results":[{"operationId":"o","status":"APPLIED","revision":3,""" +
                """"currentStateRevision":null,"currentProfileRevision":null,"error":null}]},"error":null}"""))
            server.enqueue(ok("""{"data":{"schemaVersion":1,"changes":[],"nextCursor":"c1","hasMore":false,"snapshotRevision":3},"error":null}"""))
            profile(rev = null)
            SyncEngine({ real }, store, tokens).run()

            val up = server.takeRequest()
            assertEquals("PUT", up.method)
            assertEquals("/api/v1/sync/backup", up.path)
            val op = ApiJson.parseToJsonElement(up.body.readUtf8()).jsonObject.getValue("operations").jsonArray.single().jsonObject
            assertEquals("\"CREATE_PROFILE\"", op.getValue("type").toString())
            assertEquals("/api/v1/sync/backup?schemaVersion=1&limit=200", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}
