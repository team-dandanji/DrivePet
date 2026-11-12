package com.drivepet.app.data.remote

import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.sync.DownloadResult
import com.drivepet.app.data.sync.LocalChange
import com.drivepet.app.data.sync.SyncStore
import com.drivepet.app.data.sync.UploadRequest
import com.drivepet.app.data.sync.UploadResult
import com.drivepet.app.data.db.RecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/** AccountService·LinkViewModel 테스트용 가짜들. */
class MemoryTokens(initial: AuthState) : TokenStore {
    private val s = MutableStateFlow(initial)
    override suspend fun get() = s.value
    override suspend fun update(f: (AuthState) -> AuthState) { s.value = f(s.value) }
    override val state: Flow<AuthState> = s
}

class OwnerStore : SyncStore {
    val profiles = mutableMapOf<String, ProfileEntity>()
    val adopted = mutableListOf<String>()
    var calls = 0

    override suspend fun adoptUnowned(accountId: String) {
        calls++; adopted += accountId
        profiles.replaceAll { _, p -> if (p.accountId == null) p.copy(accountId = accountId) else p }
    }
    override suspend fun profilesToCreate(accountId: String): List<ProfileEntity> = error("unused")
    override suspend fun pendingRecords(accountId: String, limit: Int): List<RecordEntity> = error("unused")
    override fun pendingCount(accountId: String): Flow<Int> = flowOf(0)
    override suspend fun markProfileCreated(profileId: String, stateRevision: Long) = error("unused")
    override suspend fun markRecords(states: Map<String, String>) = error("unused")
    override suspend fun profile(profileId: String): ProfileEntity? = error("unused")
    override suspend fun cursor(accountId: String): String? = error("unused")
    override suspend fun applyPage(accountId: String, changes: List<LocalChange>, nextCursor: String) = error("unused")
}

class QrApi : DrivePetApi {
    /** 서버 시계. 새 세션마다 expiresAt = 이 시각 + 5분. */
    var now: () -> Instant = { Instant.parse("2026-01-01T00:00:00Z") }
    var expiresAt: () -> String = { now().plusSeconds(300).toString() }
    var createCalls = 0
    var statusCalls = 0
    /** n번째(1부터) qrStatus 호출의 응답. */
    var script: (Int) -> QrStatus = { QrStatus("PENDING") }
    var unlinkResult: UnlinkResult? = null
    var deleteResult: AccountDeleted? = null

    override suspend fun createQr(): Envelope<QrSession> {
        createCalls++
        return Envelope(QrSession("s$createCalls", "http://fake/qr/$createCalls", expiresAt(), "PENDING"))
    }
    override suspend fun qrStatus(id: String): Envelope<QrStatus> = Envelope(script(++statusCalls))
    override suspend fun unlink(): Envelope<UnlinkResult> = Envelope(unlinkResult)
    override suspend fun deleteAccount(): Envelope<AccountDeleted> = Envelope(deleteResult)
    override suspend fun upload(r: UploadRequest, a: ExpectedAccount): Envelope<UploadResult> = error("unused")
    override suspend fun download(v: Int, cursor: String?, limit: Int, a: ExpectedAccount): Envelope<DownloadResult> = error("unused")
    override suspend fun weather(lat: Double, lng: Double): Envelope<WeatherDto> = error("unused")
    override suspend fun places(lat: Double, lng: Double): Envelope<List<PlaceDto>> = error("unused")
}

fun linked(account: String = "acc_1", nick: String? = "지우", synced: Boolean = true) = QrStatus(
    "LINKED", account, nick?.let(::KakaoUser), synced, TokenPair("a9", "r9", 3600),
)

fun anonymous() = AuthState("d1", "sec", "a0", "r0", null, null)
