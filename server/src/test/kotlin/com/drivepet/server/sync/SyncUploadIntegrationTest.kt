package com.drivepet.server.sync

import com.drivepet.server.support.IntegrationTest
import com.drivepet.server.sync.SyncFixtures.append
import com.drivepet.server.sync.SyncFixtures.attendance
import com.drivepet.server.sync.SyncFixtures.batch
import com.drivepet.server.sync.SyncFixtures.create
import com.drivepet.server.sync.SyncFixtures.item
import com.drivepet.server.sync.SyncFixtures.mission
import com.drivepet.server.sync.SyncFixtures.state
import com.drivepet.server.sync.SyncFixtures.trip
import com.drivepet.server.sync.SyncFixtures.update
import com.drivepet.server.sync.SyncFixtures.uuid
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncUploadIntegrationTest : IntegrationTest() {
    private val path = "/api/v1/sync/backup"

    private fun results(r: com.drivepet.server.support.Res): List<JsonNode> {
        assertEquals(200, r.status, r.raw)
        return r.data["results"].toList()
    }

    private fun JsonNode.st() = get("status").asString()
    private fun JsonNode.code() = get("error").takeIf { !it.isNull }?.get("code")?.asString()

    @Test
    fun `프로필 생성·출석·트립을 순서대로 적용하고 revision을 매긴다`() {
        val a = linkedDevice()
        val pid = uuid()
        val r = put(path, batch(create(pid), append(pid, attendance()), append(pid, trip())), a.access)
        val res = results(r)
        assertEquals(listOf("APPLIED", "APPLIED", "APPLIED"), res.map { it.st() })
        assertEquals(listOf(1L, 2L, 3L), res.map { it["revision"].asLong() })
        assertEquals(3, r.data["serverRevision"].asLong())
        assertTrue(res.all { it["error"].isNull && it["currentStateRevision"].isNull && it["currentProfileRevision"].isNull })
    }

    @Test
    fun `카카오 미연결이면 403`() {
        val dev = register()
        val r = put(path, batch(create(uuid())), dev.access)
        assertEquals(403, r.status)
        assertEquals("KAKAO_NOT_LINKED", r.errorCode)
    }

    @Test
    fun `같은 clientEventId·같은 본문은 처음 응답, 키 순서만 달라도 같고, 다른 본문은 409`() {
        val a = linkedDevice()
        val pid = uuid()
        val body = batch(create(pid), append(pid, attendance()))
        val first = put(path, body, a.access)
        val again = put(path, body, a.access)
        assertEquals(first.data, again.data)
        // 객체 키 순서를 뒤집은 같은 본문
        val reordered = json.readTree(json.writeValueAsString(body)).let { n ->
            val m = linkedMapOf<String, Any?>()
            n.propertyNames().reversed().forEach { m[it] = json.treeToValue(n.get(it), Any::class.java) }
            m
        }
        assertEquals(first.data, put(path, reordered, a.access).data)
        val changed = batch(create(uuid()), clientEventId = body["clientEventId"] as String)
        val conflict = put(path, changed, a.access)
        assertEquals(409, conflict.status)
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.errorCode)
        assertEquals(2, jdbc.sql("select count(*) from sync_changes").query(Long::class.java).single())
    }

    @Test
    fun `다른 묶음의 같은 기록은 DUPLICATE(원래 revision), 내용이 다르면 RECORD_CONTENT_CONFLICT`() {
        val a = linkedDevice()
        val pid = uuid()
        val rid = uuid()
        put(path, batch(create(pid), append(pid, attendance(rid))), a.access)
        val dup = results(put(path, batch(append(pid, attendance(rid))), a.access)).single()
        assertEquals("DUPLICATE", dup.st())
        assertEquals(2, dup["revision"].asLong())
        val other = results(put(path, batch(append(pid, attendance(rid, "2026-11-10T12:00:00+09:00", "2026-11-10"))), a.access)).single()
        assertEquals("CONFLICT", other.st())
        assertEquals("RECORD_CONTENT_CONFLICT", other.code())
        // 서로 다른 recordId의 같은 날 출석은 둘 다 보존(보상 중복 판단은 앱)
        assertEquals("APPLIED", results(put(path, batch(append(pid, attendance())), a.access)).single().st())
    }

    @Test
    fun `프로필 생성 중복과 충돌`() {
        val a = linkedDevice()
        val pid = uuid()
        put(path, batch(create(pid)), a.access)
        val same = results(put(path, batch(create(pid)), a.access)).single()
        assertEquals("DUPLICATE", same.st())
        assertEquals(1, same["revision"].asLong())
        val diff = results(put(path, batch(create(pid, state(nickname = "모모"))), a.access)).single()
        assertEquals("PROFILE_ALREADY_EXISTS", diff.code())
        assertEquals(1, diff["currentStateRevision"].asLong())
    }

    @Test
    fun `상태 충돌 뒤에도 독립된 기록 추가는 성공하고 실패 작업은 흔적이 없다`() {
        val a = linkedDevice()
        val pid = uuid()
        put(path, batch(create(pid)), a.access) // rev 1
        val res = results(put(path, batch(update(pid, 0, state(nickname = "모모")), append(pid, attendance())), a.access))
        assertEquals("STATE_CONFLICT", res[0].code())
        assertEquals(1, res[0]["currentStateRevision"].asLong())
        assertEquals("APPLIED", res[1].st())
        assertEquals(2, res[1]["revision"].asLong())
        val nick = jdbc.sql("select state->>'petNickname' from profiles where profile_id = cast(:p as uuid)").param("p", pid).query(String::class.java).single()
        assertEquals("드리", nick)
        assertEquals("APPLIED", results(put(path, batch(update(pid, 1, state(nickname = "모모"))), a.access)).single().st())
    }

    @Test
    fun `내용 검증 실패는 200 안의 REJECTED`() {
        val a = linkedDevice()
        val pid = uuid()
        val itemId = uuid()
        put(path, batch(create(pid, state(items = listOf(item(itemId))))), a.access)
        val cases = listOf(
            // 서울 자정 경계: UTC 23:30은 서울 다음날 08:30
            append(pid, attendance(occurredAt = "2026-11-09T23:30:00Z", localDate = "2026-11-09")),
            append(pid, trip(distance = 1)),
            append(pid, trip(status = "UNVERIFIED", start = null, end = null)),
            append(pid, trip(start = 20, end = 10, distance = -10)),
            update(pid, 1, state(starter = "STARTER_2", items = listOf(item(itemId)))),
            update(pid, 1, state(items = listOf(item(uuid())))), // 기존 itemId 삭제
            create(uuid(), state(nickname = "모")),
            create(uuid(), state(items = listOf(item(itemId), item(itemId)))),
            create(uuid(), state() + ("extra" to 1)),
            // 미측정 상태인데 점수가 있음
            append(pid, trip().let { t ->
                @Suppress("UNCHECKED_CAST")
                val payload = t["payload"] as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val settlement = payload["settlement"] as Map<String, Any?>
                t + ("payload" to payload + ("settlement" to settlement + ("healthStatus" to "PERMISSION_DENIED")))
            }),
        )
        cases.forEach { op ->
            val r = results(put(path, batch(op), a.access)).single()
            assertEquals("REJECTED", r.st(), op.toString())
            assertEquals("VALIDATION_FAILED", r.code())
        }
        // 서울 날짜가 맞으면 통과
        assertEquals("APPLIED", results(put(path, batch(append(pid, attendance(occurredAt = "2026-11-09T23:30:00Z", localDate = "2026-11-10"))), a.access)).single().st())
        // 미측정 값은 그대로 보존
        val unverified = trip(status = "UNVERIFIED", start = null, end = null, distance = null, baseExp = null)
        assertEquals("APPLIED", results(put(path, batch(append(pid, unverified)), a.access)).single().st())
    }

    @Test
    fun `봉투·작업 구조 오류는 400, 지원하지 않는 버전은 UNSUPPORTED_SCHEMA_VERSION`() {
        val a = linkedDevice()
        val pid = uuid()
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", put(path, batch(create(pid)) + ("schemaVersion" to 2), a.access).errorCode)
        listOf(
            batch(create(pid) + ("record" to emptyMap<String, Any>())),
            batch(*Array(101) { create(uuid()) }),
            mapOf("schemaVersion" to 1, "clientEventId" to "x", "operations" to listOf(create(pid))),
            mapOf("schemaVersion" to 1, "clientEventId" to uuid(), "operations" to emptyList<Any>()),
            batch(mapOf("operationId" to uuid(), "type" to "RENAME", "profileId" to pid)),
        ).forEach {
            val r = put(path, it, a.access)
            assertEquals(400, r.status, r.raw)
            assertEquals("VALIDATION_FAILED", r.errorCode)
        }
    }

    @Test
    fun `같은 미션·기간에 다른 보상 정의는 REWARD_DEFINITION_CONFLICT`() {
        val a = linkedDevice()
        val pid = uuid()
        put(path, batch(create(pid), append(pid, mission())), a.access)
        assertEquals("APPLIED", results(put(path, batch(append(pid, mission())), a.access)).single().st())
        assertEquals("REWARD_DEFINITION_CONFLICT", results(put(path, batch(append(pid, mission(reward = "20.000"))), a.access)).single().code())
    }

    @Test
    fun `다른 계정의 프로필은 NOT_FOUND, 본문의 기기·계정 위조는 무시된다`() {
        val a = linkedDevice("A")
        val b = linkedDevice("B")
        val pid = uuid()
        put(path, batch(create(pid)), a.access)
        assertEquals("NOT_FOUND", results(put(path, batch(append(pid, attendance())), b.access)).single().code())
        val forged = batch(create(uuid())) + ("accountId" to a.accountId)
        assertEquals(400, put(path, forged, b.access).status)
    }
}
