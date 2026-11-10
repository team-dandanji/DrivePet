package com.drivepet.server.sync

import com.drivepet.server.support.IntegrationTest
import com.drivepet.server.sync.SyncFixtures.append
import com.drivepet.server.sync.SyncFixtures.attendance
import com.drivepet.server.sync.SyncFixtures.batch
import com.drivepet.server.sync.SyncFixtures.create
import com.drivepet.server.sync.SyncFixtures.delete
import com.drivepet.server.sync.SyncFixtures.state
import com.drivepet.server.sync.SyncFixtures.update
import com.drivepet.server.sync.SyncFixtures.uuid
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 같은 계정 두 기기, 계정 전환·해제와 업로드 경합 */
class SyncConcurrencyIntegrationTest : IntegrationTest() {
    private val path = "/api/v1/sync/backup"

    private fun <T> race(vararg jobs: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(jobs.size)
        val start = CountDownLatch(1)
        val futures = jobs.map { job -> pool.submit<T> { start.await(); job() } }
        start.countDown()
        return futures.map { it.get() }.also { pool.shutdown() }
    }

    @Test
    fun `같은 계정 두 기기의 동시 업로드는 revision이 겹치지 않는다`() {
        val d1 = linkedDevice("A")
        val d2 = linkedDevice("A")
        val pid = uuid()
        put(path, batch(create(pid)), d1.access)
        val out = race(
            { put(path, batch(append(pid, attendance()), append(pid, attendance())), d1.access) },
            { put(path, batch(append(pid, attendance()), append(pid, attendance())), d2.access) },
        )
        val revs = out.flatMap { r -> r.data["results"].toList().map { it["revision"].asLong() } }.sorted()
        assertEquals(listOf(2L, 3L, 4L, 5L), revs)
        assertEquals(5, jdbc.sql("select last_revision from accounts").query(Long::class.java).single())
    }

    @Test
    fun `다른 두 계정이 같은 프로필 ID 둘을 반대 순서로 동시에 만들어도 교착 없이 ID마다 한쪽만 생성된다`() {
        val a = linkedDevice("A")
        val b = linkedDevice("B")
        repeat(10) {
            val p1 = uuid()
            val p2 = uuid()
            val out = race(
                { put(path, batch(create(p1), create(p2)), a.access) },
                { put(path, batch(create(p2), create(p1)), b.access) },
            )
            out.forEach { assertEquals(200, it.status, it.raw) }
            val applied = out.flatMap { r -> r.data["results"].toList().filter { it["status"].asString() == "APPLIED" } }
            assertEquals(2, applied.size)
        }
        assertEquals(20, jdbc.sql("select count(*) from profiles").query(Long::class.java).single())
    }

    @Test
    fun `같은 기준 상태로 동시에 수정하면 한쪽만 적용된다`() {
        val d1 = linkedDevice("A")
        val d2 = linkedDevice("A")
        val pid = uuid()
        put(path, batch(create(pid)), d1.access)
        val out = race(
            { put(path, batch(update(pid, 1, state(nickname = "모모"))), d1.access) },
            { put(path, batch(update(pid, 1, state(nickname = "보리"))), d2.access) },
        )
        val statuses = out.map { it.data["results"][0]["status"].asString() }.sorted()
        assertEquals(listOf("APPLIED", "CONFLICT"), statuses)
    }

    @Test
    fun `같은 clientEventId 동시 요청은 같은 응답을 받고 한 번만 반영된다`() {
        val a = linkedDevice()
        val pid = uuid()
        val body = batch(create(pid), append(pid, attendance()))
        val out = race({ put(path, body, a.access) }, { put(path, body, a.access) }, { put(path, body, a.access) })
        assertTrue(out.all { it.status == 200 })
        assertEquals(1, out.map { it.data }.toSet().size)
        assertEquals(2, jdbc.sql("select count(*) from sync_changes").query(Long::class.java).single())
    }

    @Test
    fun `삭제와 기록 추가가 겹치면 삭제 뒤의 추가는 PROFILE_DELETED이고 고아 기록이 없다`() {
        val a = linkedDevice("A")
        val b = linkedDevice("A")
        val pid = uuid()
        put(path, batch(create(pid)), a.access)
        val out = race(
            { put(path, batch(delete(pid, 1)), a.access) },
            { put(path, batch(append(pid, attendance())), b.access) },
        )
        val del = out[0].data["results"][0]
        val add = out[1].data["results"][0]
        if (del["status"].asString() == "APPLIED") {
            // 추가가 먼저였다면 삭제는 PROFILE_CONFLICT였을 것이므로, 삭제 적용 = 추가가 뒤
            assertEquals("PROFILE_DELETED", add["error"]["code"].asString())
        } else {
            assertEquals("PROFILE_CONFLICT", del["error"]["code"].asString())
            assertEquals("APPLIED", add["status"].asString())
        }
        val orphans = jdbc.sql("select count(*) from sync_records r join profiles p using (profile_id) where p.deleted_at is not null").query(Long::class.java).single()
        assertEquals(0, orphans)
    }

    @Test
    fun `A→B 전환 - A 토큰 업로드는 401, A 기록은 B에 섞이지 않고, A로 돌아오면 이어서 올린다`() {
        val dev = register()
        val a = linkedDevice("A", dev)
        val pid = uuid()
        val pending = attendance()
        put(path, batch(create(pid)), a.access)
        val b = linkedDevice("B", dev, token = a.access)
        val late = put(path, batch(append(pid, pending)), a.access)
        assertEquals(401, late.status)
        assertTrue(get("$path?schemaVersion=1", b.access).data["changes"].isEmpty)
        assertEquals("NOT_FOUND", put(path, batch(append(pid, pending)), b.access).data["results"][0]["error"]["code"].asString())

        val a2 = linkedDevice("A", dev, token = b.access)
        assertEquals(a.accountId, a2.accountId)
        assertEquals("APPLIED", put(path, batch(append(pid, pending)), a2.access).data["results"][0]["status"].asString())
        assertEquals(2, get("$path?schemaVersion=1", a2.access).data["changes"].size())
    }

    @Test
    fun `업로드와 연결 해제가 겹쳐도 해제 뒤 이전 토큰으로는 저장되지 않는다`() {
        val a = linkedDevice()
        val pid = uuid()
        put(path, batch(create(pid)), a.access)
        val out = race(
            { put(path, batch(append(pid, attendance())), a.access).status },
            { delete("/api/v1/auth/kakao", a.access).status },
        )
        assertEquals(200, out[1])
        val stored = jdbc.sql("select count(*) from sync_records").query(Long::class.java).single()
        // 업로드가 먼저 확정됐으면 200·1건, 해제가 먼저면 401·0건
        assertEquals(if (out[0] == 200) 1L else 0L, stored)
        assertTrue(out[0] == 200 || out[0] == 401)
        assertEquals(401, put(path, batch(append(pid, attendance())), a.access).status)
    }

    @Test
    fun `연결 해제는 현재 기기만, 다른 기기는 계속 동기화한다`() {
        val d1 = linkedDevice()
        val d2 = linkedDevice()
        val pid = uuid()
        put(path, batch(create(pid)), d1.access)
        val unlinked = delete("/api/v1/auth/kakao", d1.access).data["tokens"]["accessToken"].asString()
        assertEquals(403, put(path, batch(append(pid, attendance())), unlinked).status)
        assertEquals("APPLIED", put(path, batch(append(pid, attendance())), d2.access).data["results"][0]["status"].asString())
    }
}
