package com.drivepet.server.sync

import com.drivepet.server.auth.DevicePrincipal
import com.drivepet.server.auth.DeviceRepository
import com.drivepet.server.common.ApiException
import com.drivepet.server.common.ApiResponse
import com.drivepet.server.common.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class OpError(val code: String, val message: String)
data class OpResult(
    val operationId: String,
    val status: String,
    val revision: Long?,
    val currentStateRevision: Long?,
    val currentProfileRevision: Long?,
    val error: OpError?,
)
data class UploadResult(val clientEventId: String, val serverRevision: Long, val results: List<OpResult>)

private data class ProfileRow(
    val profileId: UUID,
    val accountId: String,
    val state: String?,
    val stateRevision: Long,
    val profileRevision: Long,
    val deletedAt: Instant?,
    val deletedRevision: Long?,
)

@Service
class SyncService(
    private val jdbc: JdbcClient,
    private val devices: DeviceRepository,
    private val json: JsonMapper,
    private val cursors: SyncCursorCodec,
    private val clock: Clock,
) {
    private val seoul = ZoneId.of("Asia/Seoul")

    private fun applied(op: ParsedOperation, rev: Long) = OpResult(op.operationId, "APPLIED", rev, null, null, null)
    private fun duplicate(op: ParsedOperation, rev: Long) = OpResult(op.operationId, "DUPLICATE", rev, null, null, null)
    private fun conflict(op: ParsedOperation, code: String, msg: String, stateRev: Long? = null, profileRev: Long? = null) =
        OpResult(op.operationId, "CONFLICT", null, stateRev, profileRev, OpError(code, msg))
    private fun rejected(op: ParsedOperation, code: String, msg: String) = OpResult(op.operationId, "REJECTED", null, null, null, OpError(code, msg))

    private fun requireLinked(p: DevicePrincipal): String =
        p.accountId ?: throw ApiException(HttpStatus.FORBIDDEN, ErrorCode.KAKAO_NOT_LINKED, "카카오 계정을 연결해야 동기화할 수 있습니다")

    /** 기기 잠금 후 토큰의 연결이 아직 현재인지 다시 확인 (계정 전환·해제와 순서를 정한다) */
    private fun lockDevice(p: DevicePrincipal, shared: Boolean) {
        val row = jdbc.sql("select account_id, binding_version from devices where device_id = :d " + if (shared) "for share" else "for update")
            .param("d", p.deviceId).query { rs, _ -> rs.getString("account_id") to rs.getLong("binding_version") }.optional().orElse(null)
        if (row == null || row.first != p.accountId || row.second != p.bindingVersion) throw ApiException.invalidToken()
    }

    /** 내 계정 프로필만 조회·잠금 (다른 계정 행은 잠그지 않는다) */
    private fun profile(id: UUID, accountId: String): ProfileRow? =
        jdbc.sql("select * from profiles where profile_id = :p and account_id = :a for update").param("p", id).param("a", accountId).query { rs, _ ->
            ProfileRow(
                rs.getObject("profile_id", UUID::class.java), rs.getString("account_id"), rs.getString("state"),
                rs.getLong("state_revision"), rs.getLong("profile_revision"),
                rs.getTimestamp("deleted_at")?.toInstant(), rs.getObject("deleted_revision") as Long?,
            )
        }.optional().orElse(null)

    @Transactional
    fun upload(p: DevicePrincipal, body: JsonNode): JsonNode {
        val accountId = requireLinked(p)
        val (clientEventId, ops) = try {
            SyncValidator.parseEnvelope(body)
        } catch (e: EnvelopeInvalid) {
            if (e.unsupportedVersion) throw ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UNSUPPORTED_SCHEMA_VERSION, e.message!!)
            throw ApiException.validation(e.message!!)
        }
        val now = clock.instant()
        lockDevice(p, shared = false)
        // 같은 계정의 업로드는 계정 행 잠금으로 직렬화한다
        val last = jdbc.sql("select last_revision from accounts where account_id = :a for update").param("a", accountId).query(Long::class.java).single()
        val requestHash = CanonicalJson.hash(body, json)
        val previous = jdbc.sql("select request_hash, response::text as response from sync_batches where account_id = :a and client_event_id = :c")
            .param("a", accountId).param("c", clientEventId)
            .query { rs, _ -> rs.getBytes("request_hash") to rs.getString("response") }.optional().orElse(null)
        if (previous != null) {
            if (!java.security.MessageDigest.isEqual(previous.first, requestHash)) {
                throw ApiException(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT, "같은 clientEventId로 다른 내용을 보냈습니다")
            }
            return json.readTree(previous.second)
        }

        // 다른 계정이 같은 profileId 여럿을 반대 순서로 동시에 만들면 고유 인덱스 대기끼리 교착된다.
        // 생성할 ID를 정렬된 순서로 먼저 잠가(트랜잭션 끝에 풀림) 순서를 맞춘다
        ops.filter { it.type == "CREATE_PROFILE" }
            .map { it.profileId.mostSignificantBits xor it.profileId.leastSignificantBits }
            .distinct().sorted()
            .forEach { key -> jdbc.sql("select pg_advisory_xact_lock(:k)").param("k", key).query { _, _ -> }.list() }

        val ctx = OpContext(accountId, p.deviceId, now, last)
        val results = ops.map { op ->
            try {
                when (op.type) {
                    "CREATE_PROFILE" -> create(ctx, op)
                    "UPDATE_STATE" -> update(ctx, op)
                    "APPEND_RECORD" -> append(ctx, op)
                    else -> delete(ctx, op)
                }
            } catch (e: OperationInvalid) {
                rejected(op, "VALIDATION_FAILED", e.message ?: "작업 내용이 올바르지 않습니다")
            }
        }
        jdbc.sql("update accounts set last_revision = :r where account_id = :a").param("r", ctx.current).param("a", accountId).update()
        val response: JsonNode = json.valueToTree(UploadResult(clientEventId.toString(), ctx.current, results))
        jdbc.sql(
            """insert into sync_batches(account_id, client_event_id, request_hash, response, committed_at)
               values (:a, :c, :h, cast(:r as jsonb), :now)""",
        ).param("a", accountId).param("c", clientEventId).param("h", requestHash)
            .param("r", json.writeValueAsString(response)).param("now", Timestamp.from(now)).update()
        return response
    }

    private class OpContext(val accountId: String, val deviceId: UUID, val now: Instant, private var revision: Long) {
        val current get() = revision
        /** 다음 revision 후보 (실제로 반영할 때만 consume) */
        fun peek() = revision + 1
        fun nextRevision() = ++revision
    }

    private fun change(ctx: OpContext, rev: Long, profileId: UUID, type: String, payload: Map<String, Any?>) {
        jdbc.sql(
            """insert into sync_changes(account_id, revision, profile_id, type, saved_at, source_device_id, payload)
               values (:a, :r, :p, :t, :now, :d, cast(:payload as jsonb))""",
        ).param("a", ctx.accountId).param("r", rev).param("p", profileId).param("t", type)
            .param("now", Timestamp.from(ctx.now)).param("d", ctx.deviceId).param("payload", json.writeValueAsString(payload)).update()
    }

    private fun create(ctx: OpContext, op: ParsedOperation): OpResult {
        val state = op.node.get("state")
        SyncValidator.validateState(state)
        // 다른 계정이 같은 ID를 동시에 만들어도 예외 없이 한쪽만 들어가게 한다 (다른 계정 행은 잠그지 않음)
        val rev = ctx.peek()
        val inserted = jdbc.sql(
            """insert into profiles(profile_id, account_id, state, state_revision, profile_revision, created_at)
               values (:p, :a, cast(:s as jsonb), :r, :r, :now) on conflict (profile_id) do nothing""",
        ).param("p", op.profileId).param("a", ctx.accountId).param("s", json.writeValueAsString(state)).param("r", rev)
            .param("now", Timestamp.from(ctx.now)).update() == 1
        if (inserted) {
            ctx.nextRevision()
            change(ctx, rev, op.profileId, "PROFILE_CREATED", mapOf("state" to state))
            return applied(op, rev)
        }
        val existing = profile(op.profileId, ctx.accountId)
            ?: return conflict(op, "PROFILE_ALREADY_EXISTS", "같은 profileId가 이미 있습니다")
        if (existing.deletedAt != null) return rejected(op, "PROFILE_DELETED", "삭제된 프로필입니다")
        val same = CanonicalJson.write(json.readTree(existing.state), json) == CanonicalJson.write(state, json)
        return if (same) duplicate(op, existing.stateRevision)
        else conflict(op, "PROFILE_ALREADY_EXISTS", "같은 profileId가 다른 상태로 이미 있습니다", stateRev = existing.stateRevision)
    }

    /** 내 계정의 살아 있는 프로필만. 없거나 다른 계정 → NOT_FOUND, 삭제됨 → PROFILE_DELETED */
    private fun ownedProfile(ctx: OpContext, op: ParsedOperation): Pair<ProfileRow?, OpResult?> {
        val row = profile(op.profileId, ctx.accountId) ?: return null to rejected(op, "NOT_FOUND", "프로필을 찾을 수 없습니다")
        if (row.deletedAt != null) return null to rejected(op, "PROFILE_DELETED", "삭제된 프로필입니다")
        return row to null
    }

    private fun update(ctx: OpContext, op: ParsedOperation): OpResult {
        val state = op.node.get("state")
        val newItems = SyncValidator.validateState(state).toSet()
        val (row, err) = ownedProfile(ctx, op)
        if (err != null) return err
        val current = json.readTree(row!!.state)
        if (current.get("starterType").asString() != state.get("starterType").asString()) throw OperationInvalid("starterType 은 바꿀 수 없습니다")
        val oldItems = current.get("maintenanceItems").toList().map { it.get("itemId").asString().lowercase() }
        if (!newItems.containsAll(oldItems)) throw OperationInvalid("소모품 itemId는 지울 수 없습니다(archived로 관리)")
        val base = op.node.get("baseStateRevision").longValue()
        if (base != row.stateRevision) return conflict(op, "STATE_CONFLICT", "상태가 그사이 바뀌었습니다", stateRev = row.stateRevision)
        val rev = ctx.nextRevision()
        jdbc.sql("update profiles set state = cast(:s as jsonb), state_revision = :r, profile_revision = :r where profile_id = :p")
            .param("s", json.writeValueAsString(state)).param("r", rev).param("p", op.profileId).update()
        change(ctx, rev, op.profileId, "STATE_UPDATED", mapOf("state" to state))
        return applied(op, rev)
    }

    private fun append(ctx: OpContext, op: ParsedOperation): OpResult {
        val record = op.node.get("record")
        val info = SyncValidator.validateRecord(record)
        val (_, err) = ownedProfile(ctx, op)
        if (err != null) return err
        val hash = CanonicalJson.hash(record, json)
        val existing = jdbc.sql("select content_hash, revision from sync_records where account_id = :a and profile_id = :p and record_id = :r")
            .param("a", ctx.accountId).param("p", op.profileId).param("r", info.recordId)
            .query { rs, _ -> rs.getBytes("content_hash") to rs.getLong("revision") }.optional().orElse(null)
        if (existing != null) {
            return if (java.security.MessageDigest.isEqual(existing.first, hash)) duplicate(op, existing.second)
            else conflict(op, "RECORD_CONTENT_CONFLICT", "같은 recordId에 다른 내용이 있습니다")
        }
        info.mission?.let { m ->
            val def = jdbc.sql(
                "select rule_version, reward_exp from mission_definitions where account_id = :a and profile_id = :p and mission_id = :m and period_key = :k",
            ).param("a", ctx.accountId).param("p", op.profileId).param("m", m.missionId).param("k", m.periodKey)
                .query { rs, _ -> rs.getString("rule_version") to rs.getString("reward_exp") }.optional().orElse(null)
            if (def != null && def != (info.ruleVersion to m.rewardExp)) {
                return conflict(op, "REWARD_DEFINITION_CONFLICT", "같은 미션·기간에 다른 보상 정의가 있습니다")
            }
            if (def == null) {
                jdbc.sql(
                    """insert into mission_definitions(account_id, profile_id, mission_id, period_key, rule_version, reward_exp)
                       values (:a, :p, :m, :k, :v, :e)""",
                ).param("a", ctx.accountId).param("p", op.profileId).param("m", m.missionId).param("k", m.periodKey)
                    .param("v", info.ruleVersion).param("e", m.rewardExp).update()
            }
        }
        val rev = ctx.nextRevision()
        jdbc.sql(
            """insert into sync_records(account_id, profile_id, record_id, kind, occurred_at, rule_version, record, content_hash, revision)
               values (:a, :p, :r, :k, :o, :v, cast(:rec as jsonb), :h, :rev)""",
        ).param("a", ctx.accountId).param("p", op.profileId).param("r", info.recordId).param("k", info.kind)
            .param("o", Timestamp.from(info.occurredAt.toInstant())).param("v", info.ruleVersion)
            .param("rec", json.writeValueAsString(record)).param("h", hash).param("rev", rev).update()
        jdbc.sql("update profiles set profile_revision = :r where profile_id = :p").param("r", rev).param("p", op.profileId).update()
        change(ctx, rev, op.profileId, "RECORD_APPENDED", mapOf("record" to record))
        return applied(op, rev)
    }

    private fun delete(ctx: OpContext, op: ParsedOperation): OpResult {
        val row = profile(op.profileId, ctx.accountId) ?: return rejected(op, "NOT_FOUND", "프로필을 찾을 수 없습니다")
        if (row.deletedAt != null) return duplicate(op, row.deletedRevision!!)
        val base = op.node.get("baseProfileRevision").longValue()
        if (base != row.profileRevision) {
            return conflict(op, "PROFILE_CONFLICT", "모르는 서버 변경이 있습니다", profileRev = row.profileRevision)
        }
        val rev = ctx.nextRevision()
        val p = op.profileId
        // 이전 본문은 지우고 삭제 표식만 영구 보관한다
        jdbc.sql("delete from sync_records where profile_id = :p").param("p", p).update()
        jdbc.sql("delete from mission_definitions where profile_id = :p").param("p", p).update()
        jdbc.sql("delete from sync_changes where account_id = :a and profile_id = :p").param("a", ctx.accountId).param("p", p).update()
        jdbc.sql("update profiles set state = null, deleted_at = :now, deleted_revision = :r, profile_revision = :r where profile_id = :p")
            .param("now", Timestamp.from(ctx.now)).param("r", rev).param("p", p).update()
        change(ctx, rev, p, "PROFILE_DELETED", mapOf("deletedAt" to ctx.now.atZone(seoul).toOffsetDateTime().toString()))
        return applied(op, rev)
    }

    data class ChangeDto(val revision: Long, val profileId: UUID, val type: String, val savedAt: String, val sourceDeviceId: String, val payload: JsonNode)
    data class DownloadResult(val schemaVersion: Int, val changes: List<ChangeDto>, val nextCursor: String, val hasMore: Boolean, val snapshotRevision: Long)

    @Transactional // FOR SHARE 잠금을 쓰므로 읽기 전용 트랜잭션이 아니다
    fun download(p: DevicePrincipal, schemaVersion: Int, cursor: String?, limit: Int): DownloadResult {
        val accountId = requireLinked(p)
        if (schemaVersion != 1) throw ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UNSUPPORTED_SCHEMA_VERSION, "지원하지 않는 동기화 버전입니다")
        if (limit !in 1..500) throw ApiException.validation("limit은 1~500입니다")
        val c = cursor?.let { cursors.decode(it, accountId) ?: throw ApiException.validation("커서가 올바르지 않습니다") }
        lockDevice(p, shared = true)
        // 업로드(계정 FOR UPDATE)·삭제와 순서를 맞추기 위해 조회 동안 공유 잠금
        val current = jdbc.sql("select last_revision from accounts where account_id = :a for share").param("a", accountId).query(Long::class.java).single()
        val (after, snapshot) = when {
            c == null -> 0L to current
            c.done -> c.snapshot to current
            else -> c.after to c.snapshot
        }
        val rows = jdbc.sql(
            """select revision, profile_id, type, saved_at, source_device_id, payload::text as payload from sync_changes
               where account_id = :a and revision > :after and revision <= :snap order by revision limit :lim""",
        ).param("a", accountId).param("after", after).param("snap", snapshot).param("lim", limit + 1).query { rs: ResultSet, _ ->
            ChangeDto(
                rs.getLong("revision"), rs.getObject("profile_id", UUID::class.java), rs.getString("type"),
                rs.getTimestamp("saved_at").toInstant().atZone(seoul).toOffsetDateTime().toString(),
                rs.getObject("source_device_id", UUID::class.java).toString(), json.readTree(rs.getString("payload")),
            )
        }.list()
        val hasMore = rows.size > limit
        val page = rows.take(limit)
        val next = if (hasMore) Cursor(accountId, page.last().revision, snapshot, false) else Cursor(accountId, snapshot, snapshot, true)
        return DownloadResult(1, page, cursors.encode(next), hasMore, snapshot)
    }
}

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(private val service: SyncService) {
    @PutMapping("/backup")
    fun upload(@AuthenticationPrincipal p: DevicePrincipal, @RequestBody body: JsonNode) = ApiResponse.ok(service.upload(p, body))

    @GetMapping("/backup")
    fun download(
        @AuthenticationPrincipal p: DevicePrincipal,
        @RequestParam schemaVersion: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "200") limit: Int,
    ) = ApiResponse.ok(service.download(p, schemaVersion, cursor, limit))
}
