package com.drivepet.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

// 필드명은 docs/api/schemas/account.yaml 그대로.
@Serializable data class Envelope<T>(val data: T? = null, val error: ApiError? = null)
@Serializable data class ApiError(val code: String, val message: String)
@Serializable data class RegisterRequest(val deviceId: String, val deviceSecret: String, val appVersion: String)
@Serializable data class RegisterResult(val accessToken: String, val refreshToken: String, val expiresIn: Long, val accountId: String?, val hasAccount: Boolean)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class TokenPair(val accessToken: String, val refreshToken: String, val expiresIn: Long)
@Serializable data class QrSession(val sessionId: String, val qrUrl: String, val expiresAt: String, val status: String)
@Serializable data class KakaoUser(val nickname: String)
@Serializable data class QrStatus(
    val status: String,
    val accountId: String? = null,
    val user: KakaoUser? = null,
    val hasSyncedProfiles: Boolean? = null,
    val tokens: TokenPair? = null,
)
@Serializable data class UnlinkResult(val kakaoLinked: Boolean, val tokens: TokenPair)
@Serializable data class AccountDeleted(val tokens: TokenPair)

val ApiJson = Json { ignoreUnknownKeys = true }

/** 오류 응답 본문 봉투의 error.code. 읽을 수 없으면 null. */
fun HttpException.apiCode(): String? = runCatching {
    val body = response()?.errorBody()?.source()?.peek()?.readUtf8() ?: return null
    ApiJson.decodeFromString<Envelope<kotlinx.serialization.json.JsonElement>>(body).error?.code
}.getOrNull()
