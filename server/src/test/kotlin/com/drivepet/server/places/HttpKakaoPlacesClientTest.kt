package com.drivepet.server.places

import com.drivepet.server.kakao.KakaoProperties
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 로컬 스텁으로 카카오 로컬 키워드 검색 요청 모양과 파싱을 확인한다 */
class HttpKakaoPlacesClientTest {
    private lateinit var server: HttpServer
    private var auth = ""
    private var rawQuery = ""
    private var status = 200
    private var response = """
        {"documents":[
          {"id":"1","place_name":"A 정비","road_address_name":"서울 중구 세종대로 1","address_name":"서울 중구 태평로 1","x":"126.9781","y":"37.5666","distance":"2100"},
          {"id":"2","place_name":"B 정비","road_address_name":"","address_name":"서울 중구 지번 2","x":"126.9790","y":"37.5700","distance":"3400"}
        ]}
    """.trimIndent()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v2/local/search/keyword.json") { ex ->
            auth = ex.requestHeaders.getFirst("Authorization") ?: ""
            rawQuery = ex.requestURI.rawQuery ?: ""
            val b = response.toByteArray(); ex.sendResponseHeaders(status, b.size.toLong()); ex.responseBody.use { it.write(b) }
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun client() = HttpKakaoPlacesClient(
        KakaoProperties(mode = "live", restApiKey = "rest-key", localBaseUrl = "http://127.0.0.1:${server.address.port}"),
        JsonMapper.builder().build(),
    )

    @Test
    fun `요청 모양과 응답 변환 - KakaoAK 헤더, 도로명 없으면 지번, distance는 Int`() {
        val r = client().nearbyRepairShops(37.5665, 126.9780)
        assertEquals("KakaoAK rest-key", auth)
        listOf("query=%EC%9E%90%EB%8F%99%EC%B0%A8%EC%A0%95%EB%B9%84", "x=126.978", "y=37.5665", "radius=5000", "sort=distance", "size=3")
            .forEach { assertTrue(it in rawQuery, "$it / $rawQuery") }
        assertEquals(
            listOf(
                PlaceDto("1", "A 정비", "서울 중구 세종대로 1", 37.5666, 126.9781, 2100),
                PlaceDto("2", "B 정비", "서울 중구 지번 2", 37.5700, 126.9790, 3400),
            ),
            r,
        )
    }

    @Test
    fun `카카오가 오류를 주면 예외, 메시지에 키가 없다`() {
        status = 401; response = """{"errorType":"AccessDeniedError"}"""
        val e = assertThrows<ExternalPlacesException> { client().nearbyRepairShops(37.5, 127.0) }
        assertTrue("rest-key" !in e.message!!)
    }
}
