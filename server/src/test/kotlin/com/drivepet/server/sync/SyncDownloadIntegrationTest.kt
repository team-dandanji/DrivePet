package com.drivepet.server.sync

import com.drivepet.server.support.IntegrationTest
import com.drivepet.server.sync.SyncFixtures.append
import com.drivepet.server.sync.SyncFixtures.attendance
import com.drivepet.server.sync.SyncFixtures.batch
import com.drivepet.server.sync.SyncFixtures.create
import com.drivepet.server.sync.SyncFixtures.delete
import com.drivepet.server.sync.SyncFixtures.trip
import com.drivepet.server.sync.SyncFixtures.uuid
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncDownloadIntegrationTest : IntegrationTest() {
    private val path = "/api/v1/sync/backup"

    private fun download(token: String, cursor: String? = null, limit: Int = 200) =
        get("$path?schemaVersion=1&limit=$limit" + (cursor?.let { "&cursor=" + URLEncoder.encode(it, Charsets.UTF_8) } ?: ""), token)

    private fun revisions(r: com.drivepet.server.support.Res) = r.data["changes"].toList().map { it["revision"].asLong() }

    @Test
    fun `새 설치 전체 복원과 페이지 넘김, 순회 중 새 업로드는 다음 순회에 온다`() {
        val a = linkedDevice()
        val pid = uuid()
        put(path, batch(create(pid), append(pid, attendance()), append(pid, attendance()), append(pid, attendance())), a.access) // 1..4
        val p1 = download(a.access, limit = 3)
        assertEquals(listOf(1L, 2L, 3L), revisions(p1))
        assertEquals(true, p1.data["hasMore"].asBoolean())
        assertEquals(4, p1.data["snapshotRevision"].asLong())
        assertEquals("PROFILE_CREATED", p1.data["changes"][0]["type"].asString())
        assertEquals("드리", p1.data["changes"][0]["payload"]["state"]["petNickname"].asString())
        assertEquals(a.deviceId, p1.data["changes"][0]["sourceDeviceId"].asString())

        put(path, batch(append(pid, attendance())), a.access) // 5: 순회 도중 업로드
        val p2 = download(a.access, p1.data["nextCursor"].asString(), limit = 3)
        assertEquals(listOf(4L), revisions(p2))
        assertEquals(false, p2.data["hasMore"].asBoolean())
        assertEquals(4, p2.data["snapshotRevision"].asLong())

        val p3 = download(a.access, p2.data["nextCursor"].asString())
        assertEquals(listOf(5L), revisions(p3))
        assertEquals(5, p3.data["snapshotRevision"].asLong())
        val empty = download(a.access, p3.data["nextCursor"].asString())
        assertTrue(revisions(empty).isEmpty())
        assertEquals(false, empty.data["hasMore"].asBoolean())
    }

    @Test
    fun `삭제하면 이전 변경은 빠지고 삭제 표식만 남으며 같은 ID는 되살릴 수 없다`() {
        val a = linkedDevice()
        val keep = uuid()
        val gone = uuid()
        put(path, batch(create(gone), append(gone, attendance()), create(keep)), a.access) // 1,2,3
        val stale = put(path, batch(delete(gone, 1)), a.access).data["results"][0]
        assertEquals("PROFILE_CONFLICT", stale["error"]["code"].asString())
        assertEquals(2, stale["currentProfileRevision"].asLong())
        val del = put(path, batch(delete(gone, 2)), a.access).data["results"][0]
        assertEquals(4, del["revision"].asLong())

        val all = download(a.access)
        assertEquals(listOf(3L, 4L), revisions(all)) // revision 1·2는 빠져 연속이 아님
        assertEquals("PROFILE_DELETED", all.data["changes"][1]["type"].asString())
        assertTrue(all.data["changes"][1]["payload"]["deletedAt"].asString().endsWith("+09:00"))

        val after = put(path, batch(append(gone, attendance()), create(gone), delete(gone, 4)), a.access).data["results"].toList()
        assertEquals("PROFILE_DELETED", after[0]["error"]["code"].asString())
        assertEquals("PROFILE_DELETED", after[1]["error"]["code"].asString())
        assertEquals("DUPLICATE", after[2]["status"].asString())
        assertEquals(0, jdbc.sql("select count(*) from sync_records where profile_id = cast(:p as uuid)").param("p", gone).query(Long::class.java).single())
    }

    @Test
    fun `페이지를 넘기는 중에 삭제돼도 순회가 끝나고 삭제 표식은 다음 순회에 온다`() {
        val a = linkedDevice()
        val pid = uuid()
        put(path, batch(create(pid), append(pid, attendance()), append(pid, attendance())), a.access) // 1..3
        val p1 = download(a.access, limit = 1)
        assertEquals(listOf(1L), revisions(p1))
        put(path, batch(delete(pid, 3)), a.access) // 4
        val p2 = download(a.access, p1.data["nextCursor"].asString(), limit = 1)
        assertTrue(revisions(p2).isEmpty())
        assertEquals(false, p2.data["hasMore"].asBoolean())
        val p3 = download(a.access, p2.data["nextCursor"].asString())
        assertEquals(listOf(4L), revisions(p3))
        assertEquals("PROFILE_DELETED", p3.data["changes"][0]["type"].asString())
    }

    @Test
    fun `커서·버전·limit 검증과 계정 분리`() {
        val a = linkedDevice("A")
        val b = linkedDevice("B")
        put(path, batch(create(uuid())), a.access)
        val ca = download(a.access).data["nextCursor"].asString()
        assertEquals(400, download(b.access, ca).status) // 다른 계정 커서
        assertEquals(400, download(a.access, ca.dropLast(1) + if (ca.endsWith("A")) "B" else "A").status) // 변조
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", get("$path?schemaVersion=2", a.access).errorCode)
        assertEquals(400, get(path, a.access).status)
        assertEquals(400, download(a.access, limit = 501).status)
        assertTrue(revisions(download(b.access)).isEmpty()) // B는 A의 변경을 받지 않는다
        assertEquals(403, download(register().access).status)
    }

    @Test
    fun `미측정 트립의 safetyBreakdown·safetyScore null이 올리고 내려받아도 그대로다`() {
        val a = linkedDevice()
        val pid = uuid()
        val base = trip()
        @Suppress("UNCHECKED_CAST")
        val payload = base["payload"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val settlement = payload["settlement"] as Map<String, Any?>
        val unmeasured = base + ("payload" to payload + ("settlement" to settlement +
            mapOf("safetyScore" to null, "safetyStatus" to "UNMEASURED", "safetyBreakdown" to null)))
        val res = put(path, batch(create(pid), append(pid, unmeasured)), a.access).data["results"].toList()
        assertEquals(listOf("APPLIED", "APPLIED"), res.map { it["status"].asString() })

        val s = download(a.access).data["changes"][1]["payload"]["record"]["payload"]["settlement"]
        assertTrue(s.has("safetyBreakdown") && s["safetyBreakdown"].isNull)
        assertTrue(s.has("safetyScore") && s["safetyScore"].isNull)
        assertEquals("UNMEASURED", s["safetyStatus"].asString())
    }
}
