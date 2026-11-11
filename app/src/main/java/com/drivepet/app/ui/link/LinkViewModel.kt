package com.drivepet.app.ui.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivepet.app.AppContainer
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.remote.AccountService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.ceil

sealed interface LinkUi {
    data object Loading : LinkUi
    data class Showing(val qrUrl: String, val remainingSec: Int) : LinkUi
    data class Restoring(val nickname: String) : LinkUi
    /** restored = 서버에 기록이 있었고 내려받기도 성공. */
    data class Linked(val nickname: String, val restored: Boolean) : LinkUi
    data object Expired : LinkUi
    data class Failed(val message: String) : LinkUi
}

private const val POLL_EVERY_TICKS = 2 // 1초 틱 2번 = 2초마다 조회

/**
 * 03 · 카카오 QR 연결. 세션 하나당 루프 하나가 1초마다 남은 시간을 갱신하고 2초마다 상태를 조회한다(조회는 한 번에 하나).
 * 복원돼 연결되면(Linked.restored) 그 계정의 가장 최근 프로필을 활성으로 저장한 뒤 Linked를 내보낸다.
 */
class LinkViewModel(
    private val account: AccountService,
    private val clock: () -> Instant,
    private val profiles: suspend () -> List<ProfileEntity> = { emptyList() },
    private val setActive: suspend (String) -> Unit = {},
) : ViewModel() {
    private val _ui = MutableStateFlow<LinkUi>(LinkUi.Loading)
    val ui: StateFlow<LinkUi> = _ui.asStateFlow()
    private var job: Job? = null

    /** 화면이 다시 그려져도(회전 등) 세션을 새로 만들지 않는다. */
    fun start() { if (job == null) launchSession() }

    /** "QR 다시 받기": 어느 상태에서든 폴링을 멈추고 새 세션. */
    fun retry() = launchSession()

    /** "나중에 연결하기": 화면을 떠나도 폴링이 남지 않게 멈춘다. */
    fun stop() { job?.cancel() }

    private fun launchSession() {
        job?.cancel()
        job = viewModelScope.launch { run() }
    }

    private suspend fun run() {
        _ui.value = LinkUi.Loading
        val qr = try {
            account.createQr()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.value = LinkUi.Failed(FAILED_MESSAGE)
            return
        }
        val expiresAt = OffsetDateTime.parse(qr.expiresAt).toInstant()
        _ui.value = LinkUi.Showing(qr.qrUrl, remainingSec(expiresAt))
        var tick = 0
        while (true) {
            delay(1_000)
            val left = remainingSec(expiresAt)
            if (left <= 0) { _ui.value = LinkUi.Expired; return }
            _ui.value = LinkUi.Showing(qr.qrUrl, left)
            if (++tick % POLL_EVERY_TICKS == 0 && pollOnce(qr.sessionId)) return
        }
    }

    private fun remainingSec(expiresAt: Instant): Int =
        ceil((expiresAt.toEpochMilli() - clock().toEpochMilli()) / 1000.0).toInt().coerceAtLeast(0)

    /** 끝났으면(연결·만료) true. 일시적인 오류는 다음 주기에 다시 조회한다. */
    private suspend fun pollOnce(sessionId: String): Boolean {
        val s = try {
            account.poll(sessionId) { _ui.value = LinkUi.Restoring(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        when (s.status) {
            "LINKED" -> {
                val restored = s.hasSyncedProfiles == true
                if (restored) s.accountId?.let { activateNewest(it) }
                _ui.value = LinkUi.Linked(s.user?.nickname.orEmpty(), restored)
            }
            "EXPIRED" -> _ui.value = LinkUi.Expired
            else -> return false
        }
        return true
    }

    private suspend fun activateNewest(accountId: String) {
        try {
            profiles().filter { it.accountId == accountId && it.deletedAt == null }.maxByOrNull { it.createdAt }
                ?.let { setActive(it.profileId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 활성 프로필을 못 정해도 연결은 끝났다. 01이 가장 최근 프로필을 대신 보여 준다.
        }
    }

    companion object {
        const val FAILED_MESSAGE = "연결하지 못했어요. QR 다시 받기를 눌러 주세요"

        fun factory(c: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LinkViewModel(c.account, Instant::now, { c.store.profiles().first() }, c.settings::setActiveProfile) as T
        }
    }
}
