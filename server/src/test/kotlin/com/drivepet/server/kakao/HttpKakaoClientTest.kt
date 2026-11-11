package com.drivepet.server.kakao

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 로컬 스텁으로 카카오 토큰 교환·사용자 조회 요청 모양과 파싱을 확인한다 */
class HttpKakaoClientTest {
    private lateinit var server: HttpServer
    private var tokenBody = ""
    private var tokenResponse = """{"access_token":"kakao-at","token_type":"bearer"}"""
    private var meAuth = ""

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/oauth/token") { ex ->
            tokenBody = ex.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val b = tokenResponse.toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) }
        }
        server.createContext("/v2/user/me") { ex ->
            meAuth = ex.requestHeaders.getFirst("Authorization") ?: ""
            val b = """{"id":123456789,"kakao_account":{"profile":{"nickname":"지우"}}}""".toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) }
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun client(secret: String = "") = HttpKakaoClient(
        KakaoProperties(mode = "live", restApiKey = "rest-key", clientSecret = secret,
            authBaseUrl = "http://127.0.0.1:${server.address.port}", apiBaseUrl = "http://127.0.0.1:${server.address.port}"),
        JsonMapper.builder().build(),
    )

    @Test
    fun `인가 URL과 코드 교환`() {
        val url = client().authorizeUrl("st8", "https://x.example/api/v1/auth/kakao/callback")
        assertTrue("response_type=code" in url && "client_id=rest-key" in url && "state=st8" in url)
        assertTrue("redirect_uri=https://x.example/api/v1/auth/kakao/callback" in url || "redirect_uri=https%3A%2F%2Fx.example" in url)

        val user = client("sec").exchange("the-code", "https://x.example/cb")
        assertEquals(KakaoUser("123456789", "지우"), user)
        assertTrue("grant_type=authorization_code" in tokenBody && "code=the-code" in tokenBody && "client_secret=sec" in tokenBody)
        assertEquals("Bearer kakao-at", meAuth)
    }

    @Test
    fun `토큰이 없으면 실패하고 메시지에 코드·키가 없다`() {
        tokenResponse = """{"error":"invalid_grant"}"""
        val e = assertThrows<KakaoExchangeException> { client().exchange("the-code", "https://x.example/cb") }
        assertTrue("the-code" !in e.message!! && "rest-key" !in e.message!!)
    }
}
