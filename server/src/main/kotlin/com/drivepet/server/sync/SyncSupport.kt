package com.drivepet.server.sync

import com.drivepet.server.auth.JwtProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 멱등·기록 비교용 정규화: 객체 키는 정렬, 공백 제거, 배열 순서는 유지 */
object CanonicalJson {
    fun write(node: JsonNode, json: JsonMapper): String = buildString { append(node, json) }

    private fun StringBuilder.append(node: JsonNode, json: JsonMapper) {
        when {
            node.isObject -> {
                append('{')
                node.propertyNames().sorted().forEachIndexed { i, k ->
                    if (i > 0) append(',')
                    append(json.writeValueAsString(k)).append(':')
                    append(node.get(k), json)
                }
                append('}')
            }
            node.isArray -> {
                append('[')
                node.forEachIndexed { i, v -> if (i > 0) append(','); append(v, json) }
                append(']')
            }
            else -> append(json.writeValueAsString(node))
        }
    }

    fun hash(node: JsonNode, json: JsonMapper): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(write(node, json).toByteArray())
}

data class Cursor(val accountId: String, val after: Long, val snapshot: Long, val done: Boolean)

/** 서버가 서명한 불투명 다운로드 커서. 다른 계정·변조 커서는 null */
@Component
class SyncCursorCodec(props: JwtProperties) {
    private val key = SecretKeySpec(("sync-cursor:" + props.secret).toByteArray(), "HmacSHA256")
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    private fun sign(payload: String): String = b64.encodeToString(Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(payload.toByteArray()))

    fun encode(c: Cursor): String {
        val payload = b64.encodeToString("${c.accountId}|${c.after}|${c.snapshot}|${if (c.done) 1 else 0}".toByteArray())
        return "$payload.${sign(payload)}"
    }

    fun decode(token: String, accountId: String): Cursor? = runCatching {
        val (payload, sig) = token.split(".").also { require(it.size == 2) }
        if (!MessageDigest.isEqual(sign(payload).toByteArray(), sig.toByteArray())) return null
        val parts = String(Base64.getUrlDecoder().decode(payload)).split("|")
        val c = Cursor(parts[0], parts[1].toLong(), parts[2].toLong(), parts[3] == "1")
        if (c.accountId != accountId || c.after < 0 || c.after > c.snapshot) null else c
    }.getOrNull()
}
