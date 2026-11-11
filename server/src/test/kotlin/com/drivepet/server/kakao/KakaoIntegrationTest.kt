package com.drivepet.server.kakao

import com.drivepet.server.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KakaoIntegrationTest : IntegrationTest() {
    @Autowired lateinit var fakeKakao: FakeKakaoClient

    private val weather = "/api/v1/weather?lat=37.5665&lng=126.9780"

    @BeforeEach
    fun reset() { fakeKakao.failExchange = false }

    data class Qr(val id: String, val state: String, val qrUrl: String)

    private fun newQr(token: String): Qr {
        val r = post("/api/v1/auth/kakao/qr-sessions", token = token)
        assertEquals(200, r.status, r.raw)
        val url = r.data["qrUrl"].asString()
        val state = UriComponentsBuilder.fromUriString(url).build().queryParams.getFirst("state")!!
        return Qr(r.data["sessionId"].asString(), java.net.URLDecoder.decode(state, Charsets.UTF_8), url)
    }

    private fun callback(state: String, code: String? = null, extra: String = "") =
        get("/api/v1/auth/kakao/callback?state=${URLEncoder.encode(state, Charsets.UTF_8)}" + (code?.let { "&code=$it" } ?: "") + extra)

    /** 기기 토큰으로 QR을 만들고 가짜 계정으로 연결한 뒤 LINKED 응답을 돌려준다 */
    private fun link(token: String, who: String = "A"): Pair<Qr, com.drivepet.server.support.Res> {
        val qr = newQr(token)
        assertEquals(200, callback(qr.state, "fake-$who").status)
        return qr to get("/api/v1/auth/kakao/qr-sessions/${qr.id}", token)
    }

    @Test
    fun `QR 생성 - 3분 유효, 가짜 모드 qrUrl은 콜백의 개발용 로그인 페이지`() {
        val dev = register()
        val r = post("/api/v1/auth/kakao/qr-sessions", token = dev.access)
        assertEquals("PENDING", r.data["status"].asString())
        assertTrue(r.data["qrUrl"].asString().startsWith("https://test.drivepet.local/api/v1/auth/kakao/callback?fake=1&state="))
        assertTrue(r.data["expiresAt"].asString().endsWith("+09:00"))
        val qr = newQr(dev.access)
        val chooser = callback(qr.state, extra = "&fake=1")
        assertEquals(200, chooser.status)
        assertTrue("가짜 사용자 A로 연결" in chooser.raw)
        assertEquals("PENDING", get("/api/v1/auth/kakao/qr-sessions/${qr.id}", dev.access).data["status"].asString())
    }

    @Test
    fun `PENDING에서 LINKED, 새 토큰은 계정에 묶이고 이전 토큰은 동기화 등에서 401`() {
        val dev = register()
        val (_, linked) = link(dev.access)
        assertEquals("LINKED", linked.data["status"].asString(), linked.raw)
        val accountId = linked.data["accountId"].asString()
        assertTrue(accountId.startsWith("acc_"))
        assertEquals("가짜 사용자 A", linked.data["user"]["nickname"].asString())
        assertEquals(false, linked.data["hasSyncedProfiles"].asBoolean())
        val newAccess = linked.data["tokens"]["accessToken"].asString()
        assertEquals(200, get(weather, newAccess).status)
        assertEquals(401, get(weather, dev.access).status)
        assertEquals(401, post("/api/v1/auth/refresh", mapOf("refreshToken" to dev.refresh)).status)
        // 재등록하면 연결 계정이 보인다
        assertEquals(accountId, register(dev.deviceId, dev.secret).res.data["accountId"].asString())
    }

    @Test
    fun `같은 카카오 계정은 같은 accountId, 다른 계정이면 다른 accountId`() {
        val a1 = link(register().access).second.data["accountId"].asString()
        val a2 = link(register().access).second.data["accountId"].asString()
        val b = link(register().access, "B").second.data["accountId"].asString()
        assertEquals(a1, a2)
        assertNotEquals(a1, b)
    }

    @Test
    fun `LINKED 재조회는 새 토큰을 주고 이전에 받은 refresh는 폐기된다`() {
        val dev = register()
        val (qr, first) = link(dev.access)
        val second = get("/api/v1/auth/kakao/qr-sessions/${qr.id}", dev.access)
        assertEquals("LINKED", second.data["status"].asString())
        assertEquals(401, post("/api/v1/auth/refresh", mapOf("refreshToken" to first.data["tokens"]["refreshToken"].asString())).status)
        val fresh = post("/api/v1/auth/refresh", mapOf("refreshToken" to second.data["tokens"]["refreshToken"].asString()))
        assertEquals(200, fresh.status)
        // 회전된 후속 refresh도 같은 계열이라 다음 재조회 때 폐기된다
        get("/api/v1/auth/kakao/qr-sessions/${qr.id}", dev.access)
        assertEquals(401, post("/api/v1/auth/refresh", mapOf("refreshToken" to fresh.data["refreshToken"].asString())).status)
    }

    @Test
    fun `만료 - 상태는 200 EXPIRED, 콜백은 400 안내 페이지, LINKED도 수령 기간이 지나면 EXPIRED`() {
        val dev = register()
        val qr = newQr(dev.access)
        clock.advance(Duration.ofMinutes(3).plusSeconds(1))
        assertEquals("EXPIRED", get("/api/v1/auth/kakao/qr-sessions/${qr.id}", dev.access).data["status"].asString())
        val cb = callback(qr.state, "fake-A")
        assertEquals(400, cb.status)
        assertTrue("만료" in cb.raw)

        clock.reset()
        val (qr2, _) = link(dev.access)
        clock.advance(Duration.ofMinutes(4))
        // dev.access는 연결 전 토큰이지만 상태 조회는 허용된다
        assertEquals("EXPIRED", get("/api/v1/auth/kakao/qr-sessions/${qr2.id}", dev.access).data["status"].asString())
    }

    @Test
    fun `잘못된 state·재사용·로그인 취소·카카오 실패`() {
        val dev = register()
        assertEquals(400, callback("nope", "fake-A").status)
        assertEquals(400, get("/api/v1/auth/kakao/callback?code=fake-A").status)
        val qr = newQr(dev.access)
        val cancel = callback(qr.state, extra = "&error=access_denied")
        assertEquals(400, cancel.status)
        assertTrue("취소" in cancel.raw)
        fakeKakao.failExchange = true
        assertEquals(502, callback(qr.state, "fake-A").status)
        fakeKakao.failExchange = false
        assertEquals(200, callback(qr.state, "fake-A").status)
        val reuse = callback(qr.state, "fake-B")
        assertEquals(400, reuse.status)
        assertTrue("이미 사용한" in reuse.raw)
    }

    @Test
    fun `다른 기기의 세션은 404, 없는 세션도 404`() {
        val a = register()
        val b = register()
        val qr = newQr(a.access)
        assertEquals(404, get("/api/v1/auth/kakao/qr-sessions/${qr.id}", b.access).status)
        assertEquals(404, get("/api/v1/auth/kakao/qr-sessions/qr_none", a.access).status)
    }

    @Test
    fun `A→B→A 전환 - 과거 토큰과 과거 세션으로 토큰을 받을 수 없다`() {
        val dev = register()
        val (qrA, la) = link(dev.access, "A")
        val tokenA = la.data["tokens"]["accessToken"].asString()
        val (qrB, lb) = link(tokenA, "B")
        val tokenB = lb.data["tokens"]["accessToken"].asString()
        assertEquals(401, get(weather, tokenA).status)
        // A 세션은 이후 B 연결로 기기 연결이 바뀌었으니 EXPIRED
        assertEquals("EXPIRED", get("/api/v1/auth/kakao/qr-sessions/${qrA.id}", tokenB).data["status"].asString())
        val (_, la2) = link(tokenB, "A")
        assertEquals(la.data["accountId"].asString(), la2.data["accountId"].asString())
        assertEquals(401, get(weather, tokenA).status) // 같은 계정으로 돌아와도 과거 토큰은 부활하지 않음
        assertEquals("EXPIRED", get("/api/v1/auth/kakao/qr-sessions/${qrB.id}", la2.data["tokens"]["accessToken"].asString()).data["status"].asString())
    }

    @Test
    fun `QR을 두 개 만들고 동시에 연결하면 하나만 성공하고 나머지는 덮어쓰지 못한다`() {
        val dev = register()
        val q1 = newQr(dev.access)
        val q2 = newQr(dev.access)
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val f1 = pool.submit<Int> { start.await(); callback(q1.state, "fake-A").status }
        val f2 = pool.submit<Int> { start.await(); callback(q2.state, "fake-B").status }
        start.countDown()
        assertEquals(listOf(200, 400), listOf(f1.get(), f2.get()).sorted())
        pool.shutdown()
        val statuses = listOf(q1, q2).map { get("/api/v1/auth/kakao/qr-sessions/${it.id}", dev.access).data["status"].asString() }.sorted()
        assertEquals(listOf("EXPIRED", "LINKED"), statuses)
    }

    @Test
    fun `연결 해제 - 미연결 새 토큰, 이전 토큰 401, 날씨는 되고 다른 기기 연결은 유지`() {
        val d1 = register()
        val d2 = register()
        val t1 = link(d1.access).second.data["tokens"]["accessToken"].asString()
        val t2 = link(d2.access).second.data["tokens"]["accessToken"].asString()
        val un = delete("/api/v1/auth/kakao", t1)
        assertEquals(200, un.status, un.raw)
        assertEquals(false, un.data["kakaoLinked"].asBoolean())
        val unlinkedAccess = un.data["tokens"]["accessToken"].asString()
        assertEquals(401, get(weather, t1).status)
        assertEquals(200, get(weather, unlinkedAccess).status)
        assertEquals(200, get(weather, t2).status)
        assertFalse(register(d1.deviceId, d1.secret).res.data["hasAccount"].asBoolean())
        // 이미 미연결이어도 미연결 토큰을 준다
        assertEquals(200, delete("/api/v1/auth/kakao", unlinkedAccess).status)
    }
}
