package com.drivepet.server.auth

import com.drivepet.server.common.Secrets
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@ConfigurationProperties("drivepet.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "drivepet",
    val audience: String = "drivepet-vehicle",
    val accessTtl: Duration = Duration.ofHours(1),
    val refreshTtl: Duration = Duration.ofDays(30),
) {
    init {
        require(secret.toByteArray().size >= 32) { "drivepet.jwt.secret(DRIVEPET_JWT_SECRET)은 32바이트 이상이어야 합니다" }
    }
}

data class TokenPair(val accessToken: String, val refreshToken: String, val expiresIn: Long)

/** 액세스 토큰(JWT)에 담는 기기 연결 정보 */
data class AccessClaims(val deviceId: UUID, val accountId: String?, val bindingVersion: Long)

@Service
class TokenService(
    private val props: JwtProperties,
    private val jdbc: JdbcClient,
    private val clock: Clock,
) {
    private val key = SecretKeySpec(props.secret.toByteArray(), "HmacSHA256")
    private val encoder = NimbusJwtEncoder(ImmutableSecret(key))
    val decoder: JwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().apply {
        setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(Duration.ofSeconds(30)).also { it.setClock(clock) },
                JwtIssuerValidator(props.issuer),
                JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { it != null && props.audience in it },
            ),
        )
    }

    /** 새 토큰 쌍 발급. refresh는 해시만 저장한다. familyId는 같은 발급 계열(QR 세션 등) 추적용 */
    fun issue(deviceId: UUID, accountId: String?, bindingVersion: Long, familyId: UUID): TokenPair {
        val now = clock.instant()
        val claims = JwtClaimsSet.builder()
            .issuer(props.issuer)
            .audience(listOf(props.audience))
            .subject(deviceId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(props.accessTtl))
            .id(UUID.randomUUID().toString())
            .claim("bv", bindingVersion)
            .apply { if (accountId != null) claim("acc", accountId) }
            .build()
        val access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
        val refresh = Secrets.randomToken()
        jdbc.sql(
            """insert into refresh_tokens(token_hash, device_id, account_id, binding_version, family_id, issued_at, expires_at)
               values (:h, :d, :a, :bv, :f, :now, :exp)""",
        )
            .param("h", Secrets.sha256(refresh)).param("d", deviceId).param("a", accountId)
            .param("bv", bindingVersion).param("f", familyId)
            .param("now", Timestamp.from(now)).param("exp", Timestamp.from(now.plus(props.refreshTtl)))
            .update()
        return TokenPair(access, refresh, props.accessTtl.seconds)
    }

    fun parse(jwt: Jwt) = AccessClaims(
        deviceId = UUID.fromString(jwt.subject),
        accountId = jwt.getClaimAsString("acc"),
        bindingVersion = (jwt.claims["bv"] as Number).toLong(),
    )
}
