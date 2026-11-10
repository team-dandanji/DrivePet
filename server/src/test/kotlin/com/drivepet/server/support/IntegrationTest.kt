package com.drivepet.server.support

import com.drivepet.server.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

data class Res(val status: Int, val body: JsonNode?, val raw: String) {
    val data: JsonNode get() = body!!.get("data")
    val errorCode: String? get() = body?.get("error")?.takeIf { !it.isNull }?.get("code")?.asString()
}

/** 실제 HTTP + 실제 PostgreSQL(Testcontainers) 통합 테스트 기반. 테스트마다 테이블을 비운다 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@ActiveProfiles("test")
abstract class IntegrationTest {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var json: JsonMapper

    private val client by lazy {
        RestClient.builder().baseUrl("http://localhost:$port").build()
    }

    @Autowired
    lateinit var clock: TestClock

    @BeforeEach
    fun cleanDb() {
        clock.reset()
        val tables = jdbc.sql(
            "select string_agg(quote_ident(tablename), ',') from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
        ).query(String::class.java).optional().orElse(null)
        if (!tables.isNullOrBlank()) jdbc.sql("truncate $tables restart identity cascade").update()
    }

    fun call(method: HttpMethod, path: String, body: Any? = null, token: String? = null): Res {
        val spec = client.method(method).uri(path)
        if (token != null) spec.header("Authorization", "Bearer $token")
        if (body != null) spec.contentType(MediaType.APPLICATION_JSON).body(if (body is String) body else json.writeValueAsString(body))
        return spec.exchange { _, res ->
            val raw = res.body.readAllBytes().toString(Charsets.UTF_8)
            val node = runCatching { json.readTree(raw) }.getOrNull()
            Res(res.statusCode.value(), node, raw)
        }
    }

    fun get(path: String, token: String? = null) = call(HttpMethod.GET, path, token = token)
    fun post(path: String, body: Any? = null, token: String? = null) = call(HttpMethod.POST, path, body, token)
    fun put(path: String, body: Any? = null, token: String? = null) = call(HttpMethod.PUT, path, body, token)
    fun delete(path: String, token: String? = null) = call(HttpMethod.DELETE, path, token = token)

    data class Linked(val deviceId: String, val secret: String, val access: String, val refresh: String, val accountId: String)

    /** 기기를 등록하고 가짜 카카오 계정(A/B)에 연결한 뒤 계정 토큰을 돌려준다 */
    fun linkedDevice(who: String = "A", existing: Registered? = null, token: String? = null): Linked {
        val dev = existing ?: register()
        val t = token ?: dev.access
        val qr = post("/api/v1/auth/kakao/qr-sessions", token = t)
        check(qr.status == 200) { qr.raw }
        val url = qr.data["qrUrl"].asString()
        val state = org.springframework.web.util.UriComponentsBuilder.fromUriString(url).build().queryParams.getFirst("state")!!
        val cb = get("/api/v1/auth/kakao/callback?code=fake-$who&state=$state")
        check(cb.status == 200) { cb.raw }
        val st = get("/api/v1/auth/kakao/qr-sessions/${qr.data["sessionId"].asString()}", t)
        check(st.data["status"].asString() == "LINKED") { st.raw }
        return Linked(dev.deviceId, dev.secret, st.data["tokens"]["accessToken"].asString(), st.data["tokens"]["refreshToken"].asString(), st.data["accountId"].asString())
    }

    data class Registered(val deviceId: String, val secret: String, val access: String, val refresh: String, val res: Res)

    fun register(deviceId: String = UUID.randomUUID().toString(), secret: String = "s".repeat(10) + UUID.randomUUID().toString().replace("-", "")): Registered {
        val r = post("/api/v1/devices", mapOf("deviceId" to deviceId, "deviceSecret" to secret, "appVersion" to "1.0.0"))
        check(r.status == 200) { r.raw }
        return Registered(deviceId, secret, r.data["accessToken"].asString(), r.data["refreshToken"].asString(), r)
    }
}
