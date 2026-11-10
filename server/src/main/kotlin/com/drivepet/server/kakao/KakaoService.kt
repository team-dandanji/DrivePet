package com.drivepet.server.kakao

import com.drivepet.server.auth.DevicePrincipal
import com.drivepet.server.auth.DeviceRepository
import com.drivepet.server.auth.TokenPair
import com.drivepet.server.auth.TokenService
import com.drivepet.server.common.ApiException
import com.drivepet.server.common.ApiResponse
import com.drivepet.server.common.Secrets
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

data class QrSessionDto(val sessionId: String, val qrUrl: String, val expiresAt: OffsetDateTime, val status: String)

data class KakaoUserDto(val nickname: String)

/** PENDING·EXPIRED는 status만, LINKED는 나머지 필드도 채운다 (null 필드는 내보내지 않음) */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
data class QrStatusDto(
    val status: String,
    val accountId: String? = null,
    val user: KakaoUserDto? = null,
    val hasSyncedProfiles: Boolean? = null,
    val tokens: TokenPair? = null,
)

data class UnlinkDto(val kakaoLinked: Boolean, val tokens: TokenPair)

private data class QrRow(
    val sessionId: String,
    val deviceId: UUID,
    val createdBindingVersion: Long,
    val status: String,
    val familyId: UUID,
    val expiresAt: Instant,
    val linkedAt: Instant?,
    val linkedAccountId: String?,
    val linkedBindingVersion: Long?,
)

/** 콜백 결과: 폰 브라우저에 보여 줄 HTML */
data class CallbackPage(val status: HttpStatus, val title: String, val message: String, val chooser: String? = null)

@Service
class KakaoService(
    private val jdbc: JdbcClient,
    private val devices: DeviceRepository,
    private val tokens: TokenService,
    private val kakao: KakaoClient,
    private val clock: Clock,
    private val tx: TransactionTemplate,
    @Value("\${drivepet.public-base-url}") publicBaseUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val redirectUri = publicBaseUrl.trimEnd('/') + "/api/v1/auth/kakao/callback"
    private val seoul = ZoneId.of("Asia/Seoul")

    companion object {
        val SESSION_TTL: Duration = Duration.ofMinutes(3)

        /** 만료 직전에 연결돼도 앱이 토큰을 받을 수 있게, 연결 시각부터 최소 이만큼은 LINKED 조회를 허용한다 */
        val LINKED_GRACE: Duration = Duration.ofMinutes(3)
    }

    private fun row(rs: java.sql.ResultSet) = QrRow(
        rs.getString("session_id"),
        rs.getObject("device_id", UUID::class.java),
        rs.getLong("created_binding_version"),
        rs.getString("status"),
        rs.getObject("family_id", UUID::class.java),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getTimestamp("linked_at")?.toInstant(),
        rs.getString("linked_account_id"),
        rs.getObject("linked_binding_version") as Long?,
    )

    private fun findByState(state: String, forUpdate: Boolean = false): QrRow? =
        jdbc.sql("select * from qr_sessions where state_hash = :h" + if (forUpdate) " for update" else "")
            .param("h", Secrets.sha256(state)).query { rs, _ -> row(rs) }.optional().orElse(null)

    private fun findById(id: String, forUpdate: Boolean = false): QrRow? =
        jdbc.sql("select * from qr_sessions where session_id = :id" + if (forUpdate) " for update" else "")
            .param("id", id).query { rs, _ -> row(rs) }.optional().orElse(null)

    @Transactional
    fun create(principal: DevicePrincipal): QrSessionDto {
        val now = clock.instant()
        val device = devices.lock(principal.deviceId) ?: throw ApiException.invalidToken()
        // 필터 검증 뒤 다른 요청이 연결을 바꿨을 수 있으니 잠금 아래에서 다시 확인한다
        if (device.bindingVersion != principal.bindingVersion || device.accountId != principal.accountId) throw ApiException.invalidToken()
        val id = "qr_" + Secrets.randomToken(12)
        val state = Secrets.randomToken(24)
        val expires = now.plus(SESSION_TTL)
        jdbc.sql(
            """insert into qr_sessions(session_id, device_id, created_binding_version, state_hash, status, family_id, created_at, expires_at)
               values (:id, :d, :bv, :h, 'PENDING', :f, :now, :exp)""",
        ).param("id", id).param("d", device.deviceId).param("bv", device.bindingVersion).param("h", Secrets.sha256(state))
            .param("f", UUID.randomUUID()).param("now", Timestamp.from(now)).param("exp", Timestamp.from(expires)).update()
        return QrSessionDto(id, kakao.authorizeUrl(state, redirectUri), expires.atZone(seoul).toOffsetDateTime(), "PENDING")
    }

    /** 같은 기기의 세션만. LINKED 재조회마다 새 토큰을 주고 같은 세션 계열의 이전 refresh는 폐기한다 */
    @Transactional
    fun status(principal: DevicePrincipal, sessionId: String): QrStatusDto {
        val now = clock.instant()
        val peek = findById(sessionId)
        if (peek == null || peek.deviceId != principal.deviceId) throw ApiException.notFound("QR 세션을 찾을 수 없습니다")
        val device = devices.lock(principal.deviceId) ?: throw ApiException.invalidToken()
        val s = findById(sessionId, forUpdate = true)!!
        if (s.status == "PENDING") {
            val expired = !now.isBefore(s.expiresAt) || device.bindingVersion != s.createdBindingVersion
            return QrStatusDto(if (expired) "EXPIRED" else "PENDING")
        }
        val deadline = maxOf(s.expiresAt, s.linkedAt!!.plus(LINKED_GRACE))
        // 토큰 수령 기간이 지났거나, 그 뒤 다른 연결·해제로 기기 연결이 바뀌었으면 이 세션은 더 이상 토큰을 주지 않는다
        if (!now.isBefore(deadline) || device.bindingVersion != s.linkedBindingVersion || device.accountId != s.linkedAccountId) {
            return QrStatusDto("EXPIRED")
        }
        devices.revokeFamily(s.familyId, now)
        val pair = tokens.issue(device.deviceId, device.accountId, device.bindingVersion, s.familyId)
        val nickname = jdbc.sql("select nickname from accounts where account_id = :a").param("a", s.linkedAccountId)
            .query(String::class.java).optional().orElse(null) ?: "카카오 사용자"
        val hasProfiles = jdbc.sql("select exists(select 1 from profiles where account_id = :a and deleted_at is null)")
            .param("a", s.linkedAccountId).query(Boolean::class.java).single()
        return QrStatusDto("LINKED", s.linkedAccountId, KakaoUserDto(nickname), hasProfiles, pair)
    }

    fun callback(code: String?, state: String?, error: String?, fake: Boolean): CallbackPage {
        val now = clock.instant()
        if (state.isNullOrBlank()) return CallbackPage(HttpStatus.BAD_REQUEST, "연결 실패", "잘못된 요청입니다. 차량 화면에서 QR을 다시 받아 주세요.")
        val s = findByState(state)
            ?: return CallbackPage(HttpStatus.BAD_REQUEST, "연결 실패", "잘못된 요청입니다. 차량 화면에서 QR을 다시 받아 주세요.")
        if (s.status != "PENDING") return CallbackPage(HttpStatus.BAD_REQUEST, "연결 실패", "이미 사용한 QR입니다. 차량 화면을 확인해 주세요.")
        if (!now.isBefore(s.expiresAt)) return expiredPage()
        if (!error.isNullOrBlank()) return CallbackPage(HttpStatus.BAD_REQUEST, "연결 취소", "카카오 로그인이 취소되었습니다. 차량 화면에서 QR을 다시 받아 주세요.")
        if (code.isNullOrBlank()) {
            if (fake && kakao.fake) return CallbackPage(HttpStatus.OK, "가짜 카카오 로그인 (개발용)", "연결할 계정을 고르세요.", chooser = state)
            return CallbackPage(HttpStatus.BAD_REQUEST, "연결 실패", "잘못된 요청입니다. 차량 화면에서 QR을 다시 받아 주세요.")
        }
        // 외부 호출 동안 DB 잠금을 잡지 않는다
        val user = try {
            kakao.exchange(code, redirectUri)
        } catch (e: KakaoExchangeException) {
            log.warn("카카오 연결 실패: {}", e.message)
            return CallbackPage(HttpStatus.BAD_GATEWAY, "연결 실패", "카카오 로그인 확인에 실패했습니다. 잠시 뒤 QR을 다시 받아 주세요.")
        }
        // 같은 빈 안의 호출이라 @Transactional 대신 TransactionTemplate로 묶는다
        return tx.execute { link(state, user) }!!
    }

    private fun expiredPage() = CallbackPage(HttpStatus.BAD_REQUEST, "연결 실패", "QR이 만료되었습니다. 차량 화면에서 QR을 다시 받아 주세요.")

    private fun link(state: String, user: KakaoUser): CallbackPage {
        val now = clock.instant()
        val peek = findByState(state) ?: return CallbackPage(HttpStatus.BAD_REQUEST, "연결 실패", "잘못된 요청입니다.")
        val device = devices.lock(peek.deviceId)!!
        val s = findByState(state, forUpdate = true)!!
        if (s.status != "PENDING") return CallbackPage(HttpStatus.BAD_REQUEST, "연결 실패", "이미 사용한 QR입니다. 차량 화면을 확인해 주세요.")
        // QR을 만든 뒤 다른 연결·해제가 먼저 확정됐으면 오래된 세션으로 덮어쓰지 않는다
        if (!now.isBefore(s.expiresAt) || device.bindingVersion != s.createdBindingVersion) return expiredPage()
        val accountId = jdbc.sql(
            """insert into accounts(account_id, kakao_subject, nickname, created_at) values (:id, :sub, :nick, :now)
               on conflict (kakao_subject) do update set nickname = excluded.nickname
               returning account_id""",
        ).param("id", "acc_" + Secrets.randomToken(9)).param("sub", user.subject).param("nick", user.nickname)
            .param("now", Timestamp.from(now)).query(String::class.java).single()
        val bv = devices.rebind(device.deviceId, accountId, now)
        jdbc.sql(
            """update qr_sessions set status = 'LINKED', linked_at = :now, linked_account_id = :a, linked_binding_version = :bv
               where session_id = :id""",
        ).param("now", Timestamp.from(now)).param("a", accountId).param("bv", bv).param("id", s.sessionId).update()
        return CallbackPage(HttpStatus.OK, "카카오 연결 완료", "카카오 연결이 완료되었습니다. 차량 화면으로 돌아가세요.")
    }

    /** 현재 기기만 해제. 이미 미연결이면 연결 버전을 그대로 두고 미연결 토큰만 새로 준다 */
    @Transactional
    fun unlink(principal: DevicePrincipal): UnlinkDto {
        val now = clock.instant()
        val device = devices.lock(principal.deviceId) ?: throw ApiException.invalidToken()
        if (device.bindingVersion != principal.bindingVersion) throw ApiException.invalidToken()
        val bv = if (device.accountId != null) devices.rebind(device.deviceId, null, now) else device.bindingVersion
        return UnlinkDto(false, tokens.issue(device.deviceId, null, bv, UUID.randomUUID()))
    }
}

@RestController
@RequestMapping("/api/v1/auth/kakao")
class KakaoController(private val service: KakaoService) {
    @PostMapping("/qr-sessions")
    fun create(@AuthenticationPrincipal p: DevicePrincipal) = ApiResponse.ok(service.create(p))

    @GetMapping("/qr-sessions/{id}")
    fun status(@AuthenticationPrincipal p: DevicePrincipal, @PathVariable id: String) = ApiResponse.ok(service.status(p, id))

    @DeleteMapping
    fun unlink(@AuthenticationPrincipal p: DevicePrincipal) = ApiResponse.ok(service.unlink(p))

    @GetMapping("/callback", produces = [MediaType.TEXT_HTML_VALUE])
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false) fake: String?,
    ): ResponseEntity<String> {
        val page = service.callback(code, state, error, fake == "1")
        return ResponseEntity.status(page.status).contentType(MediaType("text", "html", Charsets.UTF_8)).body(html(page))
    }

    private fun html(p: CallbackPage): String {
        val chooser = p.chooser?.let { state ->
            val enc = java.net.URLEncoder.encode(state, Charsets.UTF_8)
            """<p><a href="?code=fake-A&amp;state=$enc">가짜 사용자 A로 연결</a></p><p><a href="?code=fake-B&amp;state=$enc">가짜 사용자 B로 연결</a></p>"""
        } ?: ""
        return """<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${p.title}</title></head><body style="font-family:sans-serif;padding:24px"><h2>${p.title}</h2><p>${p.message}</p>$chooser</body></html>"""
    }
}
