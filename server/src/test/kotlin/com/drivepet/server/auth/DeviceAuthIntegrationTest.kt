package com.drivepet.server.auth

import com.drivepet.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceAuthIntegrationTest : IntegrationTest() {
    @Autowired
    lateinit var tokens: TokenService

    @Autowired
    lateinit var devices: DeviceRepository

    @Test
    fun `처음 등록하면 미연결 토큰을 준다`() {
        val r = register()
        assertEquals(3600, r.res.data["expiresIn"].asInt())
        assertTrue(r.res.data["accountId"].isNull)
        assertEquals(false, r.res.data["hasAccount"].asBoolean())
        assertNull(r.res.body!!.get("error").takeIf { !it.isNull })
    }

    @Test
    fun `같은 비밀값으로 다시 등록하면 성공, 다른 비밀값이면 401이고 기존 값은 그대로`() {
        val first = register()
        val again = register(first.deviceId, first.secret)
        assertNotEquals(first.access, again.access)

        val wrong = post("/api/v1/devices", mapOf("deviceId" to first.deviceId, "deviceSecret" to "x".repeat(43), "appVersion" to "1.0.0"))
        assertEquals(401, wrong.status)
        assertEquals("AUTH_INVALID_TOKEN", wrong.errorCode)
        // 원래 비밀값은 여전히 통한다
        register(first.deviceId, first.secret)
    }

    @Test
    fun `같은 UUID를 서로 다른 비밀값으로 동시에 처음 등록하면 하나만 성공한다`() {
        val id = UUID.randomUUID().toString()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = (1..2).map { i ->
            pool.submit<Int> {
                start.await()
                post("/api/v1/devices", mapOf("deviceId" to id, "deviceSecret" to "$i".repeat(40), "appVersion" to "1.0.0")).status
            }
        }
        start.countDown()
        val statuses = results.map { it.get() }.sorted()
        assertEquals(listOf(200, 401), statuses)
        pool.shutdown()
    }

    @Test
    fun `요청 값이 틀리면 400`() {
        val bad = listOf(
            mapOf("deviceId" to "not-uuid", "deviceSecret" to "a".repeat(40), "appVersion" to "1"),
            mapOf("deviceId" to UUID.randomUUID().toString(), "deviceSecret" to "short", "appVersion" to "1"),
            mapOf("deviceId" to UUID.randomUUID().toString(), "deviceSecret" to "a".repeat(39) + "!", "appVersion" to "1"),
            mapOf("deviceId" to UUID.randomUUID().toString(), "appVersion" to "1"),
        )
        bad.forEach {
            val r = post("/api/v1/devices", it)
            assertEquals(400, r.status, r.raw)
            assertEquals("VALIDATION_FAILED", r.errorCode)
        }
        assertEquals(400, post("/api/v1/devices", "{not json").status)
    }

    @Test
    fun `refresh는 회전하고 이전 값은 다시 못 쓴다`() {
        val r = register()
        val next = post("/api/v1/auth/refresh", mapOf("refreshToken" to r.refresh))
        assertEquals(200, next.status, next.raw)
        val reuse = post("/api/v1/auth/refresh", mapOf("refreshToken" to r.refresh))
        assertEquals(401, reuse.status)
        assertEquals(200, post("/api/v1/auth/refresh", mapOf("refreshToken" to next.data["refreshToken"].asString())).status)
        assertEquals(401, post("/api/v1/auth/refresh", mapOf("refreshToken" to "nope")).status)
    }

    @Test
    fun `같은 refresh 동시 요청은 하나만 성공한다`() {
        val r = register()
        val pool = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        val futures = (1..3).map { pool.submit<Int> { start.await(); post("/api/v1/auth/refresh", mapOf("refreshToken" to r.refresh)).status } }
        start.countDown()
        assertEquals(listOf(200, 401, 401), futures.map { it.get() }.sorted())
        pool.shutdown()
    }

    @Test
    fun `만료된 refresh는 401`() {
        val r = register()
        jdbc.sql("update refresh_tokens set expires_at = now() - interval '1 second'").update()
        assertEquals(401, post("/api/v1/auth/refresh", mapOf("refreshToken" to r.refresh)).status)
    }

    @Test
    fun `토큰이 없거나 변조·만료·다른 발급자면 401`() {
        val r = register()
        assertEquals(401, get("/api/v1/weather?lat=37.5&lng=127.0").status)
        assertEquals("AUTH_INVALID_TOKEN", get("/api/v1/weather?lat=37.5&lng=127.0", "garbage").errorCode)
        val tampered = r.access.dropLast(2) + if (r.access.endsWith("AA")) "BB" else "AA"
        assertEquals(401, get("/api/v1/weather?lat=37.5&lng=127.0", tampered).status)

        // 만료 토큰: 과거 시각으로 발급
        val past = TokenService(
            JwtProperties(secret = "test-only-secret-0123456789-abcdefghijklmnop"),
            jdbc,
            java.time.Clock.fixed(Instant.now().minusSeconds(7200), java.time.ZoneOffset.UTC),
        ).issue(UUID.fromString(r.deviceId), null, 0, UUID.randomUUID()).accessToken
        assertEquals("AUTH_EXPIRED_TOKEN", get("/api/v1/weather?lat=37.5&lng=127.0", past).errorCode)

        // 다른 키로 서명한 토큰
        val other = TokenService(JwtProperties(secret = "another-secret-0123456789-abcdefghijklmnopq"), jdbc, java.time.Clock.systemUTC())
            .issue(UUID.fromString(r.deviceId), null, 0, UUID.randomUUID()).accessToken
        assertEquals(401, get("/api/v1/weather?lat=37.5&lng=127.0", other).status)
    }

    @Test
    fun `기기 연결이 바뀌면 이전 access·refresh는 401`() {
        val r = register()
        val id = UUID.fromString(r.deviceId)
        jdbc.sql("insert into accounts(account_id, created_at) values ('acc_a', now())").update()
        devices.rebind(id, "acc_a", Instant.now())
        assertEquals(401, get("/api/v1/weather?lat=37.5&lng=127.0", r.access).status)
        assertEquals(401, post("/api/v1/auth/refresh", mapOf("refreshToken" to r.refresh)).status)
        // 재등록하면 현재 연결(acc_a) 토큰
        val again = register(r.deviceId, r.secret)
        assertEquals("acc_a", again.res.data["accountId"].asString())
        assertEquals(true, again.res.data["hasAccount"].asBoolean())
    }

    @Test
    fun `로그에 비밀값이 찍히지 않게 toString을 가린다`() {
        val req = DeviceRegistrationRequest(UUID.randomUUID().toString(), "secret-value-should-not-appear-0000000", "1.0")
        assertTrue("secret-value" !in req.toString())
        assertTrue("tok" !in RefreshRequest("tok-123").toString())
    }
}
