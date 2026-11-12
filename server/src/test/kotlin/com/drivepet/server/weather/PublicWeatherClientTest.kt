package com.drivepet.server.weather

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 로컬 스텁 HTTP 서버로 공공데이터포털 응답 파싱·오류 처리를 확인한다 (키 없이) */
class PublicWeatherClientTest {
    private lateinit var server: HttpServer
    private val executor = java.util.concurrent.Executors.newCachedThreadPool()
    private val responses = mutableMapOf<String, String>()
    private val queries = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val hits = java.util.concurrent.ConcurrentHashMap<String, Int>()
    /** 경로별로 n번째 요청까지 응답을 늦춘다 (읽기 제한 시간 넘기기) */
    private val slowUntil = mutableMapOf<String, Int>()
    /** 경로별로 n번째 요청까지 헤더는 바로, 본문만 늦게 보낸다 */
    private val slowBodyUntil = mutableMapOf<String, Int>()
    private val json = JsonMapper.builder().build()
    private val at = OffsetDateTime.parse("2026-11-09T08:50:00+09:00")

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            queries += ex.requestURI.rawQuery ?: ""
            val n = hits.merge(ex.requestURI.path, 1, Int::plus)!!
            if (n <= (slowUntil[ex.requestURI.path] ?: 0)) Thread.sleep(1000)
            val body = responses[ex.requestURI.path] ?: "{}"
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            if (n <= (slowBodyUntil[ex.requestURI.path] ?: 0)) {
                ex.responseBody.flush()
                Thread.sleep(1000)
            }
            ex.responseBody.use { it.write(bytes) }
        }
        server.executor = executor
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
    }

    private fun http() = DataGoKrHttp("http://127.0.0.1:${server.address.port}", "key+with/special=", WeatherProperties(readTimeout = java.time.Duration.ofMillis(300)), json)

    private fun ok(items: String) = """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"items":{"item":$items}}}}"""

    @Test
    fun `초단기실황과 단기예보를 읽는다`() {
        responses["/VilageFcstInfoService_2.0/getUltraSrtNcst"] = ok("""[{"category":"T1H","obsrValue":"6.5"},{"category":"PTY","obsrValue":"1"}]""")
        responses["/VilageFcstInfoService_2.0/getVilageFcst"] = ok(
            """[{"category":"SKY","fcstDate":"20261109","fcstTime":"0700","fcstValue":"1"},
                {"category":"SKY","fcstDate":"20261109","fcstTime":"0800","fcstValue":"4"},
                {"category":"POP","fcstDate":"20261109","fcstTime":"0800","fcstValue":"70"}]""",
        )
        val c = HttpKmaClient(http())
        val now = c.now(Grid(60, 127), at)
        assertEquals(6.5, now.tempC)
        assertEquals(1, now.precipitationCode)
        assertEquals(OffsetDateTime.parse("2026-11-09T08:00:00+09:00").toInstant(), now.observedAt.toInstant())
        val f = c.forecast(Grid(60, 127), at)
        assertEquals(4, f.skyCode)
        assertEquals(70, f.precipitationProbPct)
        // 실황 기준 시각(08:50 → 08:00), 예보 발표 시각(08:50 → 0800)
        assertTrue(queries.any { "base_time=0800" in it && "base_date=20261109" in it })
        // 서비스키의 특수문자는 인코딩된다
        assertTrue(queries.all { "key%2Bwith%2Fspecial%3D" in it })
    }

    @Test
    fun `특보 NO_DATA는 빈 목록, 키 오류(XML)·결과 코드 오류는 실패`() {
        responses["/WthrWrnInfoService/getPwnStatus"] = """{"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"}}}"""
        assertTrue(HttpKmaClient(http()).activeWarnings(at).isEmpty())

        responses["/WthrWrnInfoService/getPwnStatus"] = "<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>"
        assertThrows<ExternalWeatherException> { HttpKmaClient(http()).activeWarnings(at) }

        responses["/VilageFcstInfoService_2.0/getUltraSrtNcst"] = """{"response":{"header":{"resultCode":"10","resultMsg":"INVALID"}}}"""
        val e = assertThrows<ExternalWeatherException> { HttpKmaClient(http()).now(Grid(60, 127), at) }
        assertTrue("key" !in e.message!! && "127.0.0.1" !in e.message!!)
    }

    @Test
    fun `응답 지연은 한 번 다시 시도하고, 결과 코드 오류는 다시 시도하지 않는다`() {
        val path = "/VilageFcstInfoService_2.0/getUltraSrtNcst"
        responses[path] = ok("""[{"category":"T1H","obsrValue":"6.5"}]""")
        slowUntil[path] = 1
        assertEquals(6.5, HttpKmaClient(http()).now(Grid(60, 127), at).tempC)
        assertEquals(2, hits[path])

        hits.clear(); slowUntil[path] = 2
        assertThrows<ExternalWeatherException> { HttpKmaClient(http()).now(Grid(60, 127), at) }
        assertEquals(2, hits[path])

        hits.clear(); slowUntil.clear(); slowBodyUntil[path] = 1
        assertEquals(6.5, HttpKmaClient(http()).now(Grid(60, 127), at).tempC) // 본문만 늦어도 다시 시도
        assertEquals(2, hits[path])

        hits.clear(); slowBodyUntil.clear()
        responses[path] = """{"response":{"header":{"resultCode":"10","resultMsg":"INVALID"}}}"""
        assertThrows<ExternalWeatherException> { HttpKmaClient(http()).now(Grid(60, 127), at) }
        assertEquals(1, hits[path])
    }

    @Test
    fun `특보 정상 코드인데 자료가 없거나 해석이 안 되면 실패, 없음은 빈 목록`() {
        responses["/WthrWrnInfoService/getPwnStatus"] = ok("[]")
        assertThrows<ExternalWeatherException> { HttpKmaClient(http()).activeWarnings(at) }
        responses["/WthrWrnInfoService/getPwnStatus"] = ok("""[{"tmFc":"202611090600"}]""")
        assertThrows<ExternalWeatherException> { HttpKmaClient(http()).activeWarnings(at) }
        responses["/WthrWrnInfoService/getPwnStatus"] = ok("""[{"t6":"알 수 없는 형식"}]""")
        assertThrows<ExternalWeatherException> { HttpKmaClient(http()).activeWarnings(at) }
        responses["/WthrWrnInfoService/getPwnStatus"] = ok("""[{"t6":"o 없음"}]""")
        assertTrue(HttpKmaClient(http()).activeWarnings(at).isEmpty())
    }

    @Test
    fun `특보 줄 하나라도 이름이나 지역이 비면 일부만 버리지 않고 실패`() {
        assertNull(HttpKmaClient.parseWarnings("o 강풍주의보 : 서울\no 호우경보 :"))
        assertNull(HttpKmaClient.parseWarnings("o 강풍주의보 : 서울\no : 경기도"))
        assertEquals(1, HttpKmaClient.parseWarnings("o 강풍주의보 : 서울\n(참고) 해제 예고 없음")!!.size)
        assertEquals(emptyList(), HttpKmaClient.parseWarnings("o 없 음"))
        assertEquals(emptyList(), HttpKmaClient.parseWarnings("o  없  음"))
    }

    @Test
    fun `특보 현황 문구를 나눈다`() {
        responses["/WthrWrnInfoService/getPwnStatus"] = ok(
            """[{"tmFc":"202611090600","t6":"o 강풍주의보 : 서울, 경기도(김포, 파주)\no 풍랑주의보 : 서해중부먼바다\n"}]""",
        )
        val w = HttpKmaClient(http()).activeWarnings(at)
        assertEquals(listOf("강풍주의보", "풍랑주의보"), w.map { it.name })
        assertEquals("서울, 경기도(김포, 파주)", w[0].regions)
    }

    @Test
    fun `에어코리아 근접 측정소와 결측값`() {
        responses["/MsrstnInfoInqireSvc/getNearbyMsrstnList"] = """{"response":{"header":{"resultCode":"00"},"body":{"items":[{"stationName":"중구","tm":1.1},{"stationName":"종로구","tm":2.3}]}}}"""
        responses["/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty"] = """{"response":{"header":{"resultCode":"00"},"body":{"items":[{"dataTime":"2026-11-09 24:00","pm10Value":"-","pm25Value":"22","pm10Grade1h":"","pm25Grade1h":"2"}]}}}"""
        val c = HttpAirKoreaClient(http())
        assertEquals(listOf("중구", "종로구"), c.nearbyStations(198000.0, 451000.0).map { it.name })
        val m = c.measure("중구")!!
        assertNull(m.pm10)
        assertEquals(22, m.pm25)
        assertEquals(OffsetDateTime.parse("2026-11-10T00:00:00+09:00").toInstant(), m.observedAt!!.toInstant())
    }

    @Test
    fun `격자·TM·시도 변환`() {
        assertEquals(Grid(60, 127), KmaGrid.toGrid(37.5665, 126.9780)) // 서울 중구
        assertEquals(Grid(98, 76), KmaGrid.toGrid(35.1796, 129.0756)) // 부산
        val (cLat, cLng) = KmaGrid.center(Grid(60, 127))
        assertEquals(Grid(60, 127), KmaGrid.toGrid(cLat, cLng))
        val (x, y) = AirKoreaTm.toTm(37.5665, 126.9780)
        assertTrue(x in 197_000.0..199_000.0, "tmX=$x")
        assertTrue(y in 451_000.0..453_000.0, "tmY=$y")
        assertEquals(Sido.SEOUL, Sido.of(KmaGrid.toGrid(37.5665, 126.9780), 37.5665, 126.9780))
        assertEquals(Sido.JEJU, Sido.of(KmaGrid.toGrid(33.45, 126.56), 33.45, 126.56))
        // 명세 예시 성남(수정구)은 서울 대표점이 더 가깝지만 경기도여야 한다
        val seongnam = KmaGrid.toGrid(37.4127, 127.0947)
        val (sLat, sLng) = KmaGrid.center(seongnam)
        assertEquals(Sido.GYEONGGI, Sido.of(seongnam, sLat, sLng))
        assertEquals(Sido.JEONNAM_GWANGJU, Sido.of(KmaGrid.toGrid(35.1595, 126.8526), 35.1595, 126.8526))
    }
}
