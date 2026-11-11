package com.drivepet.app.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 인증이 붙는 서버 클라이언트. 토큰 없으면 기기 등록, 401이면 갱신 → 재등록 → 새 기기 순으로 복구한다.
 * baseUrl이 바뀌면 다음 api() 호출에서 클라이언트를 새로 만든다.
 */
class Remote(
    private val baseUrl: suspend () -> String,
    private val tokens: TokenStore,
    private val appVersion: String,
) {
    private val lock = Any() // 인증 복구 전용(네트워크 I/O를 쥔다)
    private val cacheLock = Any() // URL·클라이언트 캐시 전용, api()가 인증 I/O를 기다리지 않게 분리
    private var cachedUrl: String? = null
    private var cachedApi: DrivePetApi? = null
    @Volatile private var authApi: AuthApi? = null

    suspend fun api(): DrivePetApi {
        val url = baseUrl().let { if (it.endsWith("/")) it else "$it/" }
        return synchronized(cacheLock) {
            if (url != cachedUrl) {
                val plain = OkHttpClient.Builder().timeouts().build()
                authApi = retrofit(url, plain).create(AuthApi::class.java)
                val authed = plain.newBuilder().addInterceptor(bearer).authenticator(recover).build()
                cachedApi = retrofit(url, authed).create(DrivePetApi::class.java)
                cachedUrl = url
            }
            cachedApi!!
        }
    }

    private fun OkHttpClient.Builder.timeouts() = connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)

    private fun retrofit(url: String, client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(url)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private val bearer = Interceptor { chain ->
        val s = runBlocking { tokens.get() }.takeIf { it.accessToken != null } ?: synchronized(lock) { ensureRegistered() }
        val req = chain.request().authorizedBy(s) ?: throw IOException("계정이 바뀌었거나 토큰이 없어 요청을 보내지 않습니다")
        chain.proceed(req)
    }

    private val recover = Authenticator { _: Route?, response: Response ->
        if (response.priorResponse != null) return@Authenticator null
        val failed = response.request.header("Authorization")?.removePrefix("Bearer ")
        synchronized(lock) {
            val s = runBlocking { tokens.get() }
            // 다른 요청이 이미 갱신했으면 저장된 토큰으로 다시 보낸다.
            if (s.accessToken != null && s.accessToken != failed) return@synchronized response.request.authorizedBy(s)
            s.refreshToken?.let { refresh(it) } ?: registerWithFallback() ?: return@synchronized null
            // 재등록은 계정을 바꿀 수 있다: 다시 읽은 상태 하나로 계정을 대조하고 그 토큰을 붙인다.
            response.request.authorizedBy(runBlocking { tokens.get() })
        }
    }

    /**
     * [s]의 토큰을 붙인 요청. 동기화 요청([ExpectedAccount] 태그)인데 [s]의 계정과 다르면 null
     * (A의 기록을 B의 토큰으로 보내지 않는다). 토큰이 없어도 null.
     */
    private fun Request.authorizedBy(s: AuthState): Request? {
        val expected = tag(ExpectedAccount::class.java)
        if (expected != null && expected.id != s.accountId) return null
        return s.accessToken?.let { newBuilder().header("Authorization", "Bearer $it").build() }
    }

    /** lock 안에서만 부른다. 다른 요청이 먼저 등록했을 수 있어 다시 확인한다. 토큰이 있는 상태를 돌려준다. */
    private fun ensureRegistered(): AuthState {
        runBlocking { tokens.get() }.let { if (it.accessToken != null) return it }
        registerWithFallback() ?: throw IOException("기기 등록 실패")
        return runBlocking { tokens.get() }
    }

    /** 갱신 성공이면 새 access, 401이면 null. 그 밖의 오류는 IOException. */
    private fun refresh(refreshToken: String): String? {
        val r = authApi!!.refresh(RefreshRequest(refreshToken)).execute()
        if (r.code() == 401) return null
        val pair = r.body()?.data ?: throw IOException("토큰 갱신 실패 ${r.code()}")
        runBlocking { tokens.update { it.copy(accessToken = pair.accessToken, refreshToken = pair.refreshToken) } }
        return pair.accessToken
    }

    /** 저장된 id·secret으로 등록, 비밀값이 맞지 않으면(401) 새 기기로 등록한다. */
    private fun registerWithFallback(): String? {
        val s = runBlocking { tokens.get() }
        val id = s.deviceId ?: UUID.randomUUID().toString()
        val secret = s.deviceSecret ?: newSecret()
        return register(id, secret) ?: register(UUID.randomUUID().toString(), newSecret())
    }

    private fun register(id: String, secret: String): String? {
        val r = authApi!!.register(RegisterRequest(id, secret, appVersion)).execute()
        if (r.code() == 401) return null
        val res = r.body()?.data ?: throw IOException("기기 등록 실패 ${r.code()}")
        runBlocking {
            tokens.update {
                it.copy(
                    deviceId = id, deviceSecret = secret,
                    accessToken = res.accessToken, refreshToken = res.refreshToken, accountId = res.accountId,
                    nickname = if (id == it.deviceId && res.accountId == it.accountId) it.nickname else null,
                )
            }
        }
        return res.accessToken
    }

    private fun newSecret(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
}
