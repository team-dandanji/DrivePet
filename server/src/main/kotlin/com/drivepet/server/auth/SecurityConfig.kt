package com.drivepet.server.auth

import com.drivepet.server.common.ApiResponse
import com.drivepet.server.common.ErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/** 인증된 차량 기기. current=false면 토큰이 이전 연결 상태에 묶여 있다(QR 상태 조회에서만 허용) */
data class DevicePrincipal(val deviceId: UUID, val accountId: String?, val bindingVersion: Long, val current: Boolean)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity, tokens: TokenService, devices: DeviceRepository, json: JsonMapper): SecurityFilterChain {
        val writer = ErrorWriter(json)
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/v1/devices", "/api/v1/auth/refresh").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/auth/kakao/callback").permitAll()
                it.requestMatchers("/error").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, res, _ -> writer.write(res, 401, ErrorCode.AUTH_INVALID_TOKEN, "인증이 필요합니다") }
                it.accessDeniedHandler { _, res, _ -> writer.write(res, 401, ErrorCode.AUTH_INVALID_TOKEN, "인증 정보가 올바르지 않습니다") }
            }
            .addFilterBefore(JwtDeviceFilter(tokens, devices, writer), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}

class ErrorWriter(private val json: JsonMapper) {
    fun write(res: HttpServletResponse, status: Int, code: ErrorCode, message: String) {
        res.status = status
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.characterEncoding = "UTF-8"
        res.writer.write(json.writeValueAsString(ApiResponse.fail(code, message)))
    }
}

/**
 * Bearer JWT 검증 + 기기의 현재 연결과 대조.
 * 서명이 맞아도 binding_version이 다르면(다른 계정 연결·해제 이후) 401. QR 상태 조회만 예외로 통과시킨다.
 */
class JwtDeviceFilter(
    private val tokens: TokenService,
    private val devices: DeviceRepository,
    private val writer: ErrorWriter,
) : OncePerRequestFilter() {
    private val qrStatusPath = Regex("^/api/v1/auth/kakao/qr-sessions/[^/]+$")

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val header = req.getHeader("Authorization")
        if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) {
            chain.doFilter(req, res)
            return
        }
        val claims = try {
            tokens.parse(tokens.decoder.decode(header.substring(7).trim()))
        } catch (e: JwtValidationException) {
            val expired = e.errors.any { it.description?.contains("expired", ignoreCase = true) == true }
            if (expired) writer.write(res, 401, ErrorCode.AUTH_EXPIRED_TOKEN, "토큰이 만료되었습니다")
            else writer.write(res, 401, ErrorCode.AUTH_INVALID_TOKEN, "인증 정보가 올바르지 않습니다")
            return
        } catch (e: Exception) {
            if (e is JwtException || e is IllegalArgumentException || e is NullPointerException || e is ClassCastException) {
                writer.write(res, 401, ErrorCode.AUTH_INVALID_TOKEN, "인증 정보가 올바르지 않습니다")
                return
            }
            throw e
        }
        val device = devices.find(claims.deviceId)
        if (device == null) {
            writer.write(res, 401, ErrorCode.AUTH_INVALID_TOKEN, "인증 정보가 올바르지 않습니다")
            return
        }
        val current = device.bindingVersion == claims.bindingVersion && device.accountId == claims.accountId
        val qrStatus = req.method == "GET" && qrStatusPath.matches(req.requestURI)
        if (!current && !qrStatus) {
            writer.write(res, 401, ErrorCode.AUTH_INVALID_TOKEN, "인증 정보가 올바르지 않습니다")
            return
        }
        val principal = DevicePrincipal(claims.deviceId, claims.accountId, claims.bindingVersion, current)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        try {
            chain.doFilter(req, res)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
