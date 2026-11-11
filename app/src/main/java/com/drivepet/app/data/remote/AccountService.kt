package com.drivepet.app.data.remote

import com.drivepet.app.data.sync.SyncOutcome
import com.drivepet.app.data.sync.SyncStore
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** 카카오 계정 연결·해제. QR 세션은 서버가 만들고, 앱은 상태를 조회해 연결을 마무리한다. */
class AccountService(
    private val api: suspend () -> DrivePetApi,
    private val tokens: TokenStore,
    private val store: SyncStore,
    private val sync: suspend () -> SyncOutcome,
) {
    suspend fun createQr(): QrSession = api().createQr().data ?: throw IOException("QR 세션 응답에 data가 없습니다")

    /**
     * LINKED면: 토큰 즉시 저장 → accountId·닉네임 저장 → [onLinked](닉네임) → adoptUnowned → sync(복원 포함).
     * 같은 세션을 LINKED로 다시 조회하면 앞서 준 토큰이 폐기되므로 호출하는 쪽은 LINKED를 받으면 조회를 멈춘다.
     * 돌려주는 hasSyncedProfiles는 "서버에 기록이 있었고 내려받기도 성공했다"(= 복원됨)는 뜻이다. 내려받기 실패는 다음 동기화가 이어 받는다.
     */
    suspend fun poll(sessionId: String, onLinked: suspend (nickname: String?) -> Unit = {}): QrStatus {
        val s = api().qrStatus(sessionId).data ?: throw IOException("QR 상태 응답에 data가 없습니다")
        if (s.status != "LINKED") return s
        val pair = s.tokens ?: throw IOException("LINKED 응답에 토큰이 없습니다")
        val account = s.accountId ?: throw IOException("LINKED 응답에 accountId가 없습니다")
        val nickname = s.user?.nickname
        tokens.update { it.copy(accessToken = pair.accessToken, refreshToken = pair.refreshToken, accountId = account, nickname = nickname) }
        onLinked(nickname)
        // 토큰을 저장한 뒤에는 무슨 오류든 여기서 삼킨다. 밖으로 새면 호출 쪽이 같은 세션을 다시 조회해 토큰이 계속 바뀐다.
        val downloaded = try {
            store.adoptUnowned(account)
            sync() is SyncOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        return s.copy(hasSyncedProfiles = s.hasSyncedProfiles == true && downloaded)
    }

    /** 미연결 토큰으로 바꾸고 계정 표시만 지운다. Room 데이터는 그대로 둔다. */
    suspend fun unlink() {
        val r = api().unlink().data ?: throw IOException("연결 해제 응답에 data가 없습니다")
        tokens.update { it.copy(accessToken = r.tokens.accessToken, refreshToken = r.tokens.refreshToken, accountId = null, nickname = null) }
    }
}
