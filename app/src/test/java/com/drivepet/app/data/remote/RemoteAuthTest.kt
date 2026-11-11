package com.drivepet.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.util.UUID

private class MemoryTokenStore(initial: AuthState = AuthState(null, null, null, null, null, null)) : TokenStore {
    private val s = MutableStateFlow(initial)
    override suspend fun get() = s.value
    override suspend fun update(f: (AuthState) -> AuthState) { s.value = f(s.value) }
    override val state: Flow<AuthState> = s
}

class RemoteAuthTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun remote(store: TokenStore) = Remote({ server.url("/api/v1/").toString() }, store, "1.0.0")

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val qr = """{"data":{"sessionId":"s","qrUrl":"u","expiresAt":"t","status":"PENDING"},"error":null}"""
    private fun register(a: String, r: String) =
        """{"data":{"accessToken":"$a","refreshToken":"$r","expiresIn":3600,"accountId":null,"hasAccount":false},"error":null}"""
    private fun pair(a: String, r: String) =
        """{"data":{"accessToken":"$a","refreshToken":"$r","expiresIn":3600},"error":null}"""
    private val expired = """{"data":null,"error":{"code":"AUTH_EXPIRED_TOKEN","message":"만료"}}"""
    private val invalid = """{"data":null,"error":{"code":"AUTH_INVALID_TOKEN","message":"무효"}}"""

    private fun stored(a: String = "a1", r: String = "r1") =
        MemoryTokenStore(AuthState("dev-1", "s".repeat(43), a, r, null, null))

    private fun RecordedRequest.registerBody() = ApiJson.decodeFromString<RegisterRequest>(body.readUtf8())

    @Test fun registersOnFirstCallAndSendsBearer() = runBlocking {
        val store = MemoryTokenStore()
        server.enqueue(json(200, register("a1", "r1")))
        server.enqueue(json(200, qr))

        remote(store).api().createQr()

        val first = server.takeRequest()
        assertEquals("POST", first.method)
        assertEquals("/api/v1/devices", first.path)
        val body = first.registerBody()
        UUID.fromString(body.deviceId)
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(body.deviceSecret))
        assertEquals("1.0.0", body.appVersion)
        val second = server.takeRequest()
        assertEquals("/api/v1/auth/kakao/qr-sessions", second.path)
        assertEquals("Bearer a1", second.getHeader("Authorization"))
        assertEquals("a1", store.get().accessToken)
        assertEquals(body.deviceId, store.get().deviceId)
    }

    @Test fun refreshesOn401ThenRetries() = runBlocking {
        val store = stored()
        server.enqueue(json(401, expired))
        server.enqueue(json(200, pair("a2", "r2")))
        server.enqueue(json(200, qr))

        remote(store).api().createQr()

        server.takeRequest()
        val refresh = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertEquals("r1", ApiJson.decodeFromString<RefreshRequest>(refresh.body.readUtf8()).refreshToken)
        assertEquals("Bearer a2", server.takeRequest().getHeader("Authorization"))
        assertEquals("r2", store.get().refreshToken)
        assertEquals("a2", store.get().accessToken)
    }

    @Test fun reRegistersWhenRefreshRejected() = runBlocking {
        val store = stored()
        server.enqueue(json(401, expired))
        server.enqueue(json(401, invalid))
        server.enqueue(json(200, register("a3", "r3")))
        server.enqueue(json(200, qr))

        remote(store).api().createQr()

        server.takeRequest(); server.takeRequest()
        val devices = server.takeRequest()
        assertEquals("/api/v1/devices", devices.path)
        assertEquals("dev-1", devices.registerBody().deviceId)
        assertEquals("Bearer a3", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun newDeviceWhenSecretRejected() = runBlocking {
        val store = stored()
        server.enqueue(json(401, expired))
        server.enqueue(json(401, invalid))
        server.enqueue(json(401, invalid))
        server.enqueue(json(200, register("a4", "r4")))
        server.enqueue(json(200, qr))

        remote(store).api().createQr()

        server.takeRequest(); server.takeRequest()
        val first = server.takeRequest().registerBody()
        val second = server.takeRequest().registerBody()
        assertNotEquals(first.deviceId, second.deviceId)
        assertNotEquals(first.deviceSecret, second.deviceSecret)
        assertEquals(second.deviceId, store.get().deviceId)
        assertEquals("Bearer a4", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun givesUpAfterSecond401() = runBlocking {
        val store = stored()
        server.enqueue(json(401, expired))
        server.enqueue(json(200, pair("a2", "r2")))
        server.enqueue(json(401, invalid))

        try {
            remote(store).api().createQr()
            fail("HttpException expected")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
            assertEquals("AUTH_INVALID_TOKEN", e.apiCode())
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun concurrent401sRefreshOnce() = runBlocking {
        val store = stored()
        val refreshes = java.util.concurrent.atomic.AtomicInteger()
        // 두 요청이 모두 Bearer a1로 도착할 때까지 401을 붙잡아, "이미 갱신됨" 분기를 반드시 거치게 한다.
        val bothArrived = java.util.concurrent.CountDownLatch(2)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/auth/refresh" -> { refreshes.incrementAndGet(); json(200, pair("a2", "r2")) }
                request.getHeader("Authorization") == "Bearer a2" -> json(200, qr)
                else -> {
                    bothArrived.countDown()
                    check(bothArrived.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "두 요청이 함께 도착하지 않음" }
                    json(401, expired)
                }
            }
        }
        val api = remote(store).api()

        listOf(async(Dispatchers.IO) { api.createQr() }, async(Dispatchers.IO) { api.createQr() }).awaitAll()

        assertEquals(1, refreshes.get())
        assertEquals("a2", store.get().accessToken)
    }
}
