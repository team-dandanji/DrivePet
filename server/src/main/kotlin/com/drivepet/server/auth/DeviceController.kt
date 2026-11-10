package com.drivepet.server.auth

import com.drivepet.server.common.ApiException
import com.drivepet.server.common.ApiResponse
import com.drivepet.server.common.Secrets
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.util.UUID

data class DeviceRegistrationRequest(
    @field:NotBlank @field:Pattern(regexp = UUID_PATTERN) val deviceId: String?,
    @field:NotBlank @field:Size(min = 32, max = 128) @field:Pattern(regexp = "^[A-Za-z0-9_-]+$") val deviceSecret: String?,
    @field:NotBlank @field:Size(max = 40) val appVersion: String?,
) {
    // 로그·예외 메시지에 비밀값이 찍히지 않게 한다
    override fun toString() = "DeviceRegistrationRequest(deviceId=$deviceId, appVersion=$appVersion)"
}

data class RefreshRequest(@field:NotBlank @field:Size(max = 200) val refreshToken: String?) {
    override fun toString() = "RefreshRequest(***)"
}

data class DeviceRegistrationResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val accountId: String?,
    val hasAccount: Boolean,
)

const val UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

@Service
class DeviceService(
    private val devices: DeviceRepository,
    private val tokens: TokenService,
    private val clock: Clock,
) {
    @Transactional
    fun register(req: DeviceRegistrationRequest): DeviceRegistrationResult {
        val now = clock.instant()
        val deviceId = UUID.fromString(req.deviceId)
        val hash = Secrets.sha256(req.deviceSecret!!)
        devices.insertIfAbsent(deviceId, hash, req.appVersion!!, now)
        val device = devices.lock(deviceId)!!
        // 비밀값이 다르면 기존 연결·비밀값을 바꾸지 않고 거부한다
        if (!Secrets.same(device.secretHash, hash)) throw ApiException.invalidToken()
        devices.updateAppVersion(deviceId, req.appVersion, now)
        val pair = tokens.issue(deviceId, device.accountId, device.bindingVersion, UUID.randomUUID())
        return DeviceRegistrationResult(pair.accessToken, pair.refreshToken, pair.expiresIn, device.accountId, device.accountId != null)
    }

    @Transactional
    fun refresh(refreshToken: String): TokenPair {
        val now = clock.instant()
        val hash = Secrets.sha256(refreshToken)
        val peek = devices.findRefresh(hash) ?: throw ApiException.invalidToken()
        val device = devices.lock(peek.deviceId) ?: throw ApiException.invalidToken()
        val row = devices.findRefresh(hash, forUpdate = true) ?: throw ApiException.invalidToken()
        val valid = row.revokedAt == null && row.expiresAt.isAfter(now) &&
            row.bindingVersion == device.bindingVersion && row.accountId == device.accountId
        if (!valid) throw ApiException.invalidToken()
        devices.revokeRefresh(hash, now)
        return tokens.issue(device.deviceId, device.accountId, device.bindingVersion, row.familyId)
    }
}

@RestController
@RequestMapping("/api/v1")
class DeviceController(private val service: DeviceService) {
    @PostMapping("/devices")
    fun register(@Valid @RequestBody req: DeviceRegistrationRequest) = ApiResponse.ok(service.register(req))

    @PostMapping("/auth/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest) = ApiResponse.ok(service.refresh(req.refreshToken!!))
}
