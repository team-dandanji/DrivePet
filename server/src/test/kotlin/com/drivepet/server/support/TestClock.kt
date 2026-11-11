package com.drivepet.server.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 테스트에서 시간을 앞으로 돌릴 수 있는 시계 (기본은 실제 시각) */
class TestClock : Clock() {
    @Volatile private var offset: Duration = Duration.ZERO
    fun advance(d: Duration) { offset = offset.plus(d) }
    fun reset() { offset = Duration.ZERO }
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.now().plus(offset)
}

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {
    @Bean
    @Primary
    fun testClock() = TestClock()
}
