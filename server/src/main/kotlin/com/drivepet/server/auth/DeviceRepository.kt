package com.drivepet.server.auth

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class Device(val deviceId: UUID, val secretHash: ByteArray, val accountId: String?, val bindingVersion: Long)

data class RefreshRow(
    val deviceId: UUID,
    val accountId: String?,
    val bindingVersion: Long,
    val familyId: UUID,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

@Repository
class DeviceRepository(private val jdbc: JdbcClient) {
    private fun device(rs: ResultSet) = Device(
        rs.getObject("device_id", UUID::class.java),
        rs.getBytes("secret_hash"),
        rs.getString("account_id"),
        rs.getLong("binding_version"),
    )

    /** 처음 보는 기기면 등록(이미 있으면 그대로 둔다). 같은 UUID 동시 최초 등록은 먼저 들어온 비밀값이 이긴다 */
    fun insertIfAbsent(deviceId: UUID, secretHash: ByteArray, appVersion: String, now: Instant) {
        jdbc.sql(
            """insert into devices(device_id, secret_hash, app_version, created_at, updated_at)
               values (:d, :h, :v, :now, :now) on conflict (device_id) do nothing""",
        ).param("d", deviceId).param("h", secretHash).param("v", appVersion).param("now", Timestamp.from(now)).update()
    }

    fun find(deviceId: UUID): Device? =
        jdbc.sql("select * from devices where device_id = :d").param("d", deviceId).query { rs, _ -> device(rs) }.optional().orElse(null)

    /** 기기 행 잠금. 잠금 순서는 항상 기기 → 계정 → 자원 */
    fun lock(deviceId: UUID): Device? =
        jdbc.sql("select * from devices where device_id = :d for update").param("d", deviceId).query { rs, _ -> device(rs) }.optional().orElse(null)

    fun updateAppVersion(deviceId: UUID, appVersion: String, now: Instant) {
        jdbc.sql("update devices set app_version = :v, updated_at = :now where device_id = :d")
            .param("v", appVersion).param("now", Timestamp.from(now)).param("d", deviceId).update()
    }

    /** 연결 계정 변경(연결·해제). binding_version을 올려 이전 토큰을 모두 무효로 만든다 */
    fun rebind(deviceId: UUID, accountId: String?, now: Instant): Long {
        val bv = jdbc.sql(
            """update devices set account_id = :a, binding_version = binding_version + 1, updated_at = :now
               where device_id = :d returning binding_version""",
        ).param("a", accountId).param("now", Timestamp.from(now)).param("d", deviceId).query(Long::class.java).single()
        revokeAllRefresh(deviceId, now)
        return bv
    }

    fun revokeAllRefresh(deviceId: UUID, now: Instant) {
        jdbc.sql("update refresh_tokens set revoked_at = :now where device_id = :d and revoked_at is null")
            .param("now", Timestamp.from(now)).param("d", deviceId).update()
    }

    fun revokeFamily(familyId: UUID, now: Instant) {
        jdbc.sql("update refresh_tokens set revoked_at = :now where family_id = :f and revoked_at is null")
            .param("now", Timestamp.from(now)).param("f", familyId).update()
    }

    fun findRefresh(hash: ByteArray, forUpdate: Boolean = false): RefreshRow? =
        jdbc.sql("select * from refresh_tokens where token_hash = :h" + if (forUpdate) " for update" else "")
            .param("h", hash)
            .query { rs, _ ->
                RefreshRow(
                    rs.getObject("device_id", UUID::class.java),
                    rs.getString("account_id"),
                    rs.getLong("binding_version"),
                    rs.getObject("family_id", UUID::class.java),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("revoked_at")?.toInstant(),
                )
            }.optional().orElse(null)

    fun revokeRefresh(hash: ByteArray, now: Instant) {
        jdbc.sql("update refresh_tokens set revoked_at = :now where token_hash = :h")
            .param("now", Timestamp.from(now)).param("h", hash).update()
    }
}
