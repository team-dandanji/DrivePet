package com.drivepet.server.weather

import com.drivepet.server.support.IntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherIntegrationTest : IntegrationTest() {
    @Autowired lateinit var fake: FakeWeatherSource
    @Autowired lateinit var service: WeatherService

    private lateinit var token: String
    private val seoul = "/api/v1/weather?lat=37.5665&lng=126.9780"

    @BeforeEach
    fun setUp() {
        fake.reset()
        service.clearCache()
        token = register().access
    }

    /** 검증이 실패해도 남은 조회가 다음 테스트의 캐시·가짜 상태를 건드리지 않게 끝까지 기다린다 */
    @AfterEach
    fun drain() {
        assertTrue(service.awaitBackground(Duration.ofSeconds(5)), "남은 날씨 조회가 끝나지 않음")
    }

    @Test
    fun `정상 응답은 명세 모양을 따른다`() {
        val r = get(seoul, token)
        assertEquals(200, r.status, r.raw)
        val d = r.data
        assertEquals(37.5665, d["location"]["lat"].asDouble())
        assertEquals("서울특별시", d["location"]["address"].asString())
        assertEquals(6.5, d["tempC"].asDouble())
        assertEquals("CLOUDY", d["sky"].asString())
        assertEquals("RAIN", d["precipitationType"].asString())
        assertEquals(70, d["precipitationProbPct"].asInt())
        assertTrue(d["warnings"].isArray && d["warnings"].isEmpty)
        assertEquals("NORMAL", d["airQuality"]["grade"].asString())
        assertEquals(false, d["stale"].asBoolean())
        assertTrue(d["observedAt"].asString().endsWith("+09:00"))
    }

    @Test
    fun `위치 값이 없거나 범위를 벗어나면 400`() {
        listOf("/api/v1/weather", "/api/v1/weather?lat=37.5", "/api/v1/weather?lat=91&lng=127", "/api/v1/weather?lat=37&lng=abc").forEach {
            val r = get(it, token)
            assertEquals(400, r.status, it)
            assertEquals("VALIDATION_FAILED", r.errorCode)
        }
    }

    @Test
    fun `같은 격자는 캐시를 쓰고 TTL이 지나면 다시 조회한다`() {
        get(seoul, token)
        get("/api/v1/weather?lat=37.5668&lng=126.9783", token) // 같은 격자
        assertEquals(1, fake.kmaCalls.get())
        clock.advance(Duration.ofMinutes(11))
        get(seoul, token)
        assertEquals(2, fake.kmaCalls.get())
    }

    @Test
    fun `에어코리아만 실패하거나 측정소가 없으면 airQuality는 null`() {
        fake.failAir = true
        val r = get(seoul, token)
        assertEquals(200, r.status)
        assertTrue(r.data["airQuality"].isNull)
        assertEquals(6.5, r.data["tempC"].asDouble())

        service.clearCache(); fake.failAir = false; fake.noStations = true
        assertTrue(get(seoul, token).data["airQuality"].isNull)
    }

    @Test
    fun `기상청이 실패하면 남은 캐시를 stale로, 캐시도 없으면 502`() {
        fake.failNow = true
        val none = get(seoul, token)
        assertEquals(502, none.status)
        assertEquals("EXTERNAL_API_FAILED", none.errorCode)

        fake.failNow = false
        get(seoul, token)
        clock.advance(Duration.ofMinutes(11))
        fake.tempC = 1.0
        fake.failForecast = true
        val stale = get(seoul, token)
        assertEquals(200, stale.status)
        assertEquals(true, stale.data["stale"].asBoolean())
        assertEquals(6.5, stale.data["tempC"].asDouble())
    }

    @Test
    fun `기상청이 요청 시간 한도(테스트 500ms)를 넘기면 502로 끊고, 늦게 온 결과는 캐시에 남는다`() {
        fake.kmaDelayMs = 1200
        val started = System.nanoTime()
        val slow = get(seoul, token)
        assertEquals(502, slow.status, slow.raw)
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofMillis(1100))
        assertTrue(service.awaitBackground(Duration.ofSeconds(5))) // 뒤에서 계속 받던 조회가 캐시에 저장될 때까지
        fake.kmaDelayMs = 0
        val next = get(seoul, token)
        assertEquals(200, next.status)
        assertEquals(1, fake.kmaCalls.get())
    }

    @Test
    fun `에어코리아만 느리면 기다리지 않고 airQuality는 null`() {
        fake.airDelayMs = 1200
        val r = get(seoul, token)
        assertEquals(200, r.status)
        assertTrue(r.data["airQuality"].isNull)
        assertEquals(6.5, r.data["tempC"].asDouble())
    }

    @Test
    fun `특보 조회가 실패해도 날씨는 특보 없이 응답하고, 곧 다시 조회한다`() {
        fake.failWarnings = true
        val r = get(seoul, token)
        assertEquals(200, r.status, r.raw)
        assertEquals(6.5, r.data["tempC"].asDouble())
        assertTrue(r.data["warnings"].isArray && r.data["warnings"].isEmpty)
        assertEquals(false, r.data["stale"].asBoolean())

        fake.failWarnings = false
        fake.warnings = listOf(WarningItem("강풍주의보", "서울"))
        clock.advance(Duration.ofMinutes(2)) // 실패한 특보는 캐시되지 않고 격자 캐시도 1분만 산다
        assertEquals(1, get(seoul, token).data["warnings"].size())
    }

    @Test
    fun `특보 조회가 한도보다 오래 걸려도 날씨는 한도 안에 특보 없이 응답한다`() {
        fake.warningsDelayMs = 1500
        val started = System.nanoTime()
        val r = get(seoul, token)
        assertEquals(200, r.status, r.raw)
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofMillis(500)) // 테스트 요청 한도 500ms
        assertEquals(6.5, r.data["tempC"].asDouble())
        assertTrue(r.data["warnings"].isArray && r.data["warnings"].isEmpty)
        assertEquals(false, r.data["stale"].asBoolean())
    }

    @Test
    fun `격자 캐시는 담긴 특보보다 오래 살지 않는다`() {
        fake.warnings = listOf(WarningItem("강풍주의보", "서울"))
        get("/api/v1/weather?lat=35.1796&lng=129.0756", token) // 부산 조회로 전국 특보 캐시
        clock.advance(Duration.ofMinutes(9))
        get(seoul, token) // 서울 격자는 9분 된 특보를 담는다
        fake.warnings = emptyList()
        clock.advance(Duration.ofMinutes(2)) // 특보는 만료, 서울 격자 자체는 2분
        assertTrue(get(seoul, token).data["warnings"].isEmpty)
    }

    @Test
    fun `먼저 시작해 늦게 끝난 특보 조회가 나중 조회의 새 특보를 덮지 않는다`() {
        fake.warnings = listOf(WarningItem("강풍주의보", "서울"))
        fake.warningsDelayMs = 1500
        assertEquals(200, get("/api/v1/weather?lat=35.1796&lng=129.0756", token).status) // 부산: 특보는 포기하고 응답, 옛 특보 조회는 뒤에서 계속 돈다
        fake.warningsDelayMs = 0
        fake.warnings = emptyList()
        clock.advance(Duration.ofMinutes(1))
        assertEquals(200, get("/api/v1/weather?lat=35.8714&lng=128.6014", token).status) // 대구: 새 특보(없음) 저장
        assertTrue(service.awaitBackground(Duration.ofSeconds(5))) // 옛 조회가 늦게 끝남
        assertTrue(get(seoul, token).data["warnings"].isEmpty)
    }

    @Test
    fun `시·도 범위의 육상 특보만 보여 준다`() {
        fake.warnings = listOf(
            WarningItem("강풍주의보", "서울, 인천, 경기도(김포, 파주)"),
            WarningItem("건조주의보", "강원도(강원북부산지, 강원중부산지), 경상북도(울진평지)"),
            WarningItem("풍랑주의보", "서해중부먼바다"),
        )
        val w = get(seoul, token).data["warnings"]
        assertEquals(1, w.size())
        assertEquals("강풍주의보 (서울특별시 범위)", w[0].asString())
    }

    @Test
    fun `미연결 기기 토큰으로도 날씨를 쓸 수 있다`() {
        assertEquals(200, get(seoul, token).status)
    }
}
