package com.drivepet.server.account

import com.drivepet.server.kakao.FakeKakaoClient
import com.drivepet.server.support.IntegrationTest
import com.drivepet.server.sync.SyncFixtures.append
import com.drivepet.server.sync.SyncFixtures.attendance
import com.drivepet.server.sync.SyncFixtures.batch
import com.drivepet.server.sync.SyncFixtures.create
import com.drivepet.server.sync.SyncFixtures.mission
import com.drivepet.server.sync.SyncFixtures.trip
import com.drivepet.server.sync.SyncFixtures.uuid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccountDeletionIntegrationTest : IntegrationTest() {
    @Autowired lateinit var fakeKakao: FakeKakaoClient

    private val weather = "/api/v1/weather?lat=37.5665&lng=126.9780"

    @BeforeEach
    fun reset() { fakeKakao.failUnlink = false; fakeKakao.unlinked.clear() }

    private fun upload(access: String) {
        val pid = uuid()
        val r = put("/api/v1/sync/backup", batch(create(pid), append(pid, attendance()), append(pid, trip()), append(pid, mission())), access)
        assertEquals(200, r.status, r.raw)
        assertTrue(r.data["results"].all { it["status"].asString() == "APPLIED" }, r.raw)
    }

    /** 계정에 딸린 행 수 (모든 계정 테이블 합) */
    private fun rows(accountId: String): Long = listOf(
        "select count(*) from accounts where account_id = :a",
        "select count(*) from profiles where account_id = :a",
        "select count(*) from sync_records where account_id = :a",
        "select count(*) from sync_changes where account_id = :a",
        "select count(*) from sync_batches where account_id = :a",
        "select count(*) from mission_definitions where account_id = :a",
        "select count(*) from qr_sessions where linked_account_id = :a",
        "select count(*) from refresh_tokens where account_id = :a",
        "select count(*) from devices where account_id = :a",
    ).sumOf { jdbc.sql(it).param("a", accountId).query(Long::class.java).single() }

    @Test
    fun `탈퇴하면 계정 데이터가 모두 지워지고 연결된 모든 기기가 미연결이 된다`() {
        val car1 = linkedDevice("A")
        val car2 = linkedDevice("A")
        val other = linkedDevice("B")
        assertEquals(car1.accountId, car2.accountId)
        upload(car1.access)
        upload(other.access)
        assertTrue(rows(car1.accountId) > 0)
        val otherBefore = rows(other.accountId)

        val r = delete("/api/v1/account", car1.access)
        assertEquals(200, r.status, r.raw)
        val fresh = r.data["tokens"]["accessToken"].asString()

        assertEquals(0, rows(car1.accountId))
        assertEquals(otherBefore, rows(other.accountId))
        assertEquals(listOf("fake-A"), fakeKakao.unlinked.toList())
        // 기기 등록은 남고 미연결 토큰으로 계속 쓴다. 이전 토큰은 두 기기 모두 무효
        assertEquals(200, get(weather, fresh).status)
        assertEquals(403, put("/api/v1/sync/backup", batch(create(uuid())), fresh).status)
        for (c in listOf(car1, car2)) {
            assertEquals(401, get(weather, c.access).status)
            assertEquals(401, post("/api/v1/auth/refresh", mapOf("refreshToken" to c.refresh)).status)
        }
        assertEquals(2, jdbc.sql("select count(*) from devices where device_id in (:ds) and account_id is null")
            .param("ds", listOf(car1.deviceId, car2.deviceId).map(java.util.UUID::fromString)).query(Long::class.java).single())
        // 같은 카카오 계정으로 다시 연결하면 새 계정이고 복원할 기록이 없다
        val again = linkedDevice("A", existing = register(car2.deviceId, car2.secret))
        assertNotEquals(car1.accountId, again.accountId)
        assertEquals(200, get(weather, other.access).status)
    }

    @Test
    fun `미연결 기기는 403, 이전 연결 토큰은 401`() {
        val dev = register()
        val r = delete("/api/v1/account", dev.access)
        assertEquals(403, r.status)
        assertEquals("KAKAO_NOT_LINKED", r.body!!["error"]["code"].asString())
        val linked = linkedDevice("A", existing = dev)
        assertEquals(401, delete("/api/v1/account", dev.access).status)
        assertEquals(200, delete("/api/v1/account", linked.access).status)
        assertEquals(401, delete("/api/v1/account", linked.access).status)
    }

    @Test
    fun `카카오 연결 끊기가 실패해도 DB 파기는 유지된다`() {
        val car = linkedDevice("A")
        upload(car.access)
        fakeKakao.failUnlink = true
        assertEquals(200, delete("/api/v1/account", car.access).status)
        assertEquals(0, rows(car.accountId))
    }

    @Test
    fun `서비스 종료 스크립트는 모든 사용자 데이터를 지운다`() {
        val a = linkedDevice("A")
        upload(a.access)
        register()
        val sql = java.io.File("ops/purge-all-user-data.sql").readLines().filterNot { it.startsWith("--") }.joinToString("\n")
        jdbc.sql(sql).update()
        val left = listOf("accounts", "devices", "refresh_tokens", "qr_sessions", "profiles", "sync_records", "sync_changes", "sync_batches", "mission_definitions")
            .sumOf { jdbc.sql("select count(*) from $it").query(Long::class.java).single() }
        assertEquals(0, left)
    }
}
