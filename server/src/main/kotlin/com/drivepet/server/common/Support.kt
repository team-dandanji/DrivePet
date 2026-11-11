package com.drivepet.server.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

@Configuration(proxyBeanMethods = false)
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

object Secrets {
    private val random = SecureRandom()

    /** base64url 무작위 문자열 (bytes 바이트) */
    fun randomToken(bytes: Int = 32): String {
        val b = ByteArray(bytes).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    fun same(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
}
