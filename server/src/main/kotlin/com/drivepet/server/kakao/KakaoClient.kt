package com.drivepet.server.kakao

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

@ConfigurationProperties("drivepet.kakao")
data class KakaoProperties(
    val mode: String = "fake",
    val restApiKey: String = "",
    val clientSecret: String = "",
    val authBaseUrl: String = "https://kauth.kakao.com",
    val apiBaseUrl: String = "https://kapi.kakao.com",
    val timeout: Duration = Duration.ofSeconds(5),
)

data class KakaoUser(val subject: String, val nickname: String)

class KakaoExchangeException(message: String) : RuntimeException(message)

interface KakaoClient {
    val fake: Boolean get() = false

    /** 폰에서 QR로 여는 로그인 URL */
    fun authorizeUrl(state: String, redirectUri: String): String

    /** 인가 코드 → 카카오 사용자(고유 ID, 닉네임). 실패는 KakaoExchangeException (메시지에 코드·키를 넣지 않음) */
    fun exchange(code: String, redirectUri: String): KakaoUser
}

class HttpKakaoClient(private val props: KakaoProperties, private val json: JsonMapper) : KakaoClient {
    private val http = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply { setConnectTimeout(props.timeout); setReadTimeout(props.timeout) })
        .build()

    override fun authorizeUrl(state: String, redirectUri: String): String =
        UriComponentsBuilder.fromUriString("${props.authBaseUrl}/oauth/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", props.restApiKey)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state)
            .encode().build().toUriString()

    override fun exchange(code: String, redirectUri: String): KakaoUser {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", props.restApiKey)
            add("redirect_uri", redirectUri)
            add("code", code)
            if (props.clientSecret.isNotBlank()) add("client_secret", props.clientSecret)
        }
        val token = try {
            val raw = http.post().uri("${props.authBaseUrl}/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(ByteArray::class.java)!!.toString(Charsets.UTF_8)
            json.readTree(raw).path("access_token").asString("")
        } catch (e: Exception) {
            throw KakaoExchangeException("카카오 토큰 교환 실패(${e.javaClass.simpleName})")
        }
        if (token.isBlank()) throw KakaoExchangeException("카카오 토큰 없음")
        val me = try {
            val raw = http.get().uri("${props.apiBaseUrl}/v2/user/me").header("Authorization", "Bearer $token")
                .retrieve().body(ByteArray::class.java)!!.toString(Charsets.UTF_8)
            json.readTree(raw)
        } catch (e: Exception) {
            throw KakaoExchangeException("카카오 사용자 조회 실패(${e.javaClass.simpleName})")
        }
        val id = me.path("id").asString("").ifBlank { throw KakaoExchangeException("카카오 사용자 ID 없음") }
        val nickname = me.path("kakao_account").path("profile").path("nickname").asString("")
            .ifBlank { me.path("properties").path("nickname").asString("") }
            .ifBlank { "카카오 사용자" }
        return KakaoUser(id, nickname)
    }
}

/**
 * 개발용 가짜 카카오 (local-fake·test 전용). QR URL은 서버 콜백 경로의 가짜 로그인 페이지를 가리키고,
 * 계정 A/B를 고르면 code=fake-A / fake-B로 같은 콜백에 들어온다. 실제 모드에서는 이 빈이 없다.
 */
class FakeKakaoClient : KakaoClient {
    override val fake = true
    @Volatile var failExchange = false

    override fun authorizeUrl(state: String, redirectUri: String): String =
        UriComponentsBuilder.fromUriString(redirectUri).queryParam("fake", "1").queryParam("state", state).encode().build().toUriString()

    override fun exchange(code: String, redirectUri: String): KakaoUser {
        if (failExchange) throw KakaoExchangeException("가짜 카카오 실패")
        return when (code) {
            "fake-A" -> KakaoUser("fake-A", "가짜 사용자 A")
            "fake-B" -> KakaoUser("fake-B", "가짜 사용자 B")
            else -> throw KakaoExchangeException("알 수 없는 가짜 코드")
        }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KakaoProperties::class)
class KakaoConfig {
    @Bean
    @ConditionalOnProperty("drivepet.kakao.mode", havingValue = "fake", matchIfMissing = true)
    fun fakeKakaoClient() = FakeKakaoClient()

    @Bean
    @ConditionalOnProperty("drivepet.kakao.mode", havingValue = "live")
    fun kakaoClient(props: KakaoProperties, json: JsonMapper): KakaoClient {
        require(props.restApiKey.isNotBlank()) { "live 모드에는 KAKAO_REST_API_KEY가 필요합니다" }
        return HttpKakaoClient(props, json)
    }
}
