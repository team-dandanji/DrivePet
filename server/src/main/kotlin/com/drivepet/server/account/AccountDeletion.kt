package com.drivepet.server.account

import com.drivepet.server.auth.DevicePrincipal
import com.drivepet.server.auth.TokenPair
import com.drivepet.server.auth.TokenService
import com.drivepet.server.common.ApiException
import com.drivepet.server.common.ApiResponse
import com.drivepet.server.common.ErrorCode
import com.drivepet.server.kakao.KakaoClient
import com.drivepet.server.kakao.KakaoExchangeException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

/** 탈퇴 후 이 기기가 쓸 미연결 토큰 */
data class AccountDeletedDto(val tokens: TokenPair)

/**
 * 회원 탈퇴: 계정에 딸린 서버 데이터(카카오 고유 ID·닉네임, 프로필·기록·변경 로그, QR 세션, 계정 토큰)를 한 트랜잭션으로 지운다.
 * 이 계정에 연결된 모든 기기는 미연결로 돌리고(토큰 무효), 기기 등록(기기 ID·secret 해시)은 미연결 기기로 계속 쓰이므로 남긴다.
 * 커밋 뒤 카카오 쪽 앱 연결도 끊는다(실패해도 DB 파기는 되돌리지 않는다).
 */
@Service
class AccountDeletionService(
    private val jdbc: JdbcClient,
    private val tokens: TokenService,
    private val kakao: KakaoClient,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun delete(p: DevicePrincipal): AccountDeletedDto {
        val (dto, subject) = tx.execute { deleteData(p) }!!
        // 외부 호출은 DB 잠금을 놓은 뒤에. 로그에는 카카오 회원번호를 남기지 않는다
        if (subject != null) {
            try {
                if (!kakao.unlink(subject)) log.warn("카카오 Admin 키가 없어 카카오 쪽 연결은 끊지 않았습니다")
            } catch (e: KakaoExchangeException) {
                log.warn("탈퇴 후 카카오 연결 끊기 실패: {}", e.message)
            }
        }
        return dto
    }

    private fun deleteData(p: DevicePrincipal): Pair<AccountDeletedDto, String?> {
        val now = Timestamp.from(clock.instant())
        val a = p.accountId ?: throw ApiException(HttpStatus.FORBIDDEN, ErrorCode.KAKAO_NOT_LINKED, "연결된 카카오 계정이 없습니다")
        // 잠금 순서는 기기 → 계정 → 자원. 이 계정의 다른 기기도 device_id 순으로 함께 잠가 동기화·연결 요청과 순서를 정한다
        jdbc.sql("select device_id from devices where device_id = :d or account_id = :a order by device_id for update")
            .param("d", p.deviceId).param("a", a).query(UUID::class.java).list()
        val me = jdbc.sql("select account_id, binding_version from devices where device_id = :d").param("d", p.deviceId)
            .query { rs, _ -> rs.getString("account_id") to rs.getLong("binding_version") }.optional().orElse(null)
        if (me == null || me.first != a || me.second != p.bindingVersion) throw ApiException.invalidToken()
        val subject = jdbc.sql("select kakao_subject from accounts where account_id = :a for update").param("a", a)
            .query(String::class.java).optional().orElse(null)

        for (sql in listOf(
            "delete from sync_records where account_id = :a",
            "delete from mission_definitions where account_id = :a",
            "delete from sync_changes where account_id = :a",
            "delete from sync_batches where account_id = :a",
            "delete from profiles where account_id = :a",
            "delete from qr_sessions where linked_account_id = :a",
            "delete from refresh_tokens where account_id = :a",
        )) jdbc.sql(sql).param("a", a).update()
        val unlinked = jdbc.sql(
            """update devices set account_id = null, binding_version = binding_version + 1, updated_at = :now
               where account_id = :a returning device_id""",
        ).param("now", now).param("a", a).query(UUID::class.java).list()
        // 연결 전에 받은 미연결 토큰도 남기지 않는다 (연결·해제 때와 같은 규칙)
        if (unlinked.isNotEmpty()) {
            jdbc.sql("update refresh_tokens set revoked_at = :now where device_id in (:ds) and revoked_at is null")
                .param("now", now).param("ds", unlinked).update()
        }
        jdbc.sql("delete from accounts where account_id = :a").param("a", a).update()

        val bv = jdbc.sql("select binding_version from devices where device_id = :d").param("d", p.deviceId).query(Long::class.java).single()
        return AccountDeletedDto(tokens.issue(p.deviceId, null, bv, UUID.randomUUID())) to subject
    }
}

@RestController
@RequestMapping("/api/v1/account")
class AccountController(private val service: AccountDeletionService) {
    @DeleteMapping
    fun delete(@AuthenticationPrincipal p: DevicePrincipal) = ApiResponse.ok(service.delete(p))
}
