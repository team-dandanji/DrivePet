package com.drivepet.server.places

import com.drivepet.server.support.IntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlacesIntegrationTest : IntegrationTest() {
    @Autowired lateinit var fake: FakePlacesClient

    private lateinit var token: String
    private val seoul = "/api/v1/places?lat=37.5665&lng=126.9780"

    @BeforeEach
    fun setUp() {
        fake.failing = false
        token = register().access
    }

    @AfterEach
    fun tearDown() { fake.failing = false }

    @Test
    fun `정상 응답은 3곳, 가까운 순`() {
        val r = get(seoul, token)
        assertEquals(200, r.status, r.raw)
        val d = r.data
        assertEquals(3, d.size())
        assertEquals(listOf("현대 블루핸즈 A점", "차량 정비센터 B점", "블루핸즈 C점"), d.toList().map { it["name"].asString() })
        val dist = d.toList().map { it["distanceM"].asInt() }
        assertEquals(listOf(2100, 3400, 4800), dist)
        assertEquals(dist.sorted(), dist)
        d.toList().forEach { p -> assertTrue(p["id"].asString().isNotBlank() && p["address"].asString().isNotBlank() && p.has("lat") && p.has("lng")) }
    }

    @Test
    fun `좌표가 없거나 범위를 벗어나면 400`() {
        listOf("/api/v1/places", "/api/v1/places?lat=37.5", "/api/v1/places?lat=91&lng=127", "/api/v1/places?lat=37&lng=181", "/api/v1/places?lat=37&lng=abc").forEach {
            val r = get(it, token)
            assertEquals(400, r.status, it)
            assertEquals("VALIDATION_FAILED", r.errorCode)
        }
    }

    @Test
    fun `토큰이 없으면 401`() {
        assertEquals(401, get(seoul).status)
    }

    @Test
    fun `한 기기가 1분에 11번째 요청하면 429, 다른 기기는 그대로, 1분 뒤 다시 허용`() {
        clock.advance(Duration.ofMillis(PlacesRateLimiter.WINDOW_MS - clock.millis() % PlacesRateLimiter.WINDOW_MS)) // 창 시작으로 맞춰 경계에 걸리지 않게
        repeat(PlacesRateLimiter.PER_DEVICE) { assertEquals(200, get(seoul, token).status) }
        val r = get(seoul, token)
        assertEquals(429, r.status, r.raw)
        assertEquals("RATE_LIMITED", r.errorCode)
        assertEquals(200, get(seoul, register().access).status)
        clock.advance(Duration.ofMillis(PlacesRateLimiter.WINDOW_MS))
        assertEquals(200, get(seoul, token).status)
    }

    @Test
    fun `카카오가 실패하면 502`() {
        fake.failing = true
        val r = get(seoul, token)
        assertEquals(502, r.status, r.raw)
        assertEquals("EXTERNAL_API_FAILED", r.errorCode)
    }
}
