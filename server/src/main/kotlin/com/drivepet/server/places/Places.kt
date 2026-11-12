package com.drivepet.server.places

import com.drivepet.server.auth.DevicePrincipal
import com.drivepet.server.common.ApiException
import com.drivepet.server.common.ApiResponse
import com.drivepet.server.common.ErrorCode
import com.drivepet.server.kakao.KakaoProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID

data class PlaceDto(val id: String, val name: String, val address: String, val lat: Double, val lng: Double, val distanceM: Int)

/** 외부 조회 실패. 메시지에 URL·키·좌표를 넣지 않는다 */
class ExternalPlacesException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface PlacesClient {
    /** 좌표에서 가까운 정비소 최대 3곳, 가까운 순. 실패는 ExternalPlacesException */
    fun nearbyRepairShops(lat: Double, lng: Double): List<PlaceDto>
}

class HttpKakaoPlacesClient(private val props: KakaoProperties, private val json: JsonMapper) : PlacesClient {
    private val http = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply { setConnectTimeout(props.timeout); setReadTimeout(props.timeout) })
        .build()

    override fun nearbyRepairShops(lat: Double, lng: Double): List<PlaceDto> {
        val uri = UriComponentsBuilder.fromUriString("${props.localBaseUrl}/v2/local/search/keyword.json")
            .queryParam("query", "자동차정비").queryParam("x", lng).queryParam("y", lat)
            .queryParam("radius", 5000).queryParam("sort", "distance").queryParam("size", 3)
            .encode().build().toUri()
        return try {
            val raw = http.get().uri(uri).header("Authorization", "KakaoAK ${props.restApiKey}")
                .retrieve().body(ByteArray::class.java)!!.toString(Charsets.UTF_8)
            json.readTree(raw).path("documents").toList().map { d ->
                PlaceDto(
                    id = d.path("id").asString(""),
                    name = d.path("place_name").asString(""),
                    address = d.path("road_address_name").asString("").ifBlank { d.path("address_name").asString("") },
                    lat = d.path("y").asString("").toDouble(),
                    lng = d.path("x").asString("").toDouble(),
                    distanceM = d.path("distance").asString("").toInt(),
                )
            }
        } catch (e: Exception) {
            // 원인 예외 메시지에 요청 URL이 들어갈 수 있어 클래스 이름만 남긴다
            throw ExternalPlacesException("카카오 로컬 조회 실패(${e.javaClass.simpleName})")
        }
    }
}

/** 개발용 가짜 카카오 로컬 (local-fake·test 전용). 받은 좌표의 북쪽으로 고정 거리에 3곳을 돌려준다 */
class FakePlacesClient : PlacesClient {
    @Volatile var failing = false

    override fun nearbyRepairShops(lat: Double, lng: Double): List<PlaceDto> {
        if (failing) throw ExternalPlacesException("가짜 카카오 로컬 실패")
        return listOf("현대 블루핸즈 A점" to 2100, "차량 정비센터 B점" to 3400, "블루핸즈 C점" to 4800).mapIndexed { i, (name, d) ->
            PlaceDto("fake-${i + 1}", name, "가짜시 가짜로 ${i + 1}", lat + d / 111_000.0, lng, d)
        }
    }
}

@Configuration(proxyBeanMethods = false)
class PlacesConfig {
    @Bean
    @ConditionalOnProperty("drivepet.kakao.mode", havingValue = "fake", matchIfMissing = true)
    fun fakePlacesClient() = FakePlacesClient()

    @Bean
    @ConditionalOnProperty("drivepet.kakao.mode", havingValue = "live")
    fun placesClient(props: KakaoProperties, json: JsonMapper): PlacesClient {
        require(props.restApiKey.isNotBlank()) { "live 모드에는 KAKAO_REST_API_KEY가 필요합니다" }
        return HttpKakaoPlacesClient(props, json)
    }
}

/**
 * 카카오 로컬 호출량 제한(1분 고정 창): 기기당 [PER_DEVICE]회, 서버 전체 [GLOBAL]회. 기기 토큰은 공개 /devices로 받을 수 있어 인증만으로는 공유 할당량을 지키지 못한다.
 * 서버 한 대의 메모리 카운터라, 여러 대로 늘리면 각자 따로 센다. 그때는 Redis 같은 공용 저장소로 옮긴다.
 */
@Component
class PlacesRateLimiter(private val clock: Clock) {
    private var window = -1L
    private var total = 0
    private val perDevice = HashMap<UUID, Int>() // 창이 바뀔 때 비우고, 거절된 기기는 넣지 않아 최대 GLOBAL개

    @Synchronized
    fun tryAcquire(deviceId: UUID): Boolean {
        val w = clock.millis() / WINDOW_MS
        if (w != window) { window = w; total = 0; perDevice.clear() }
        val n = perDevice[deviceId] ?: 0
        if (n >= PER_DEVICE || total >= GLOBAL) return false
        perDevice[deviceId] = n + 1
        total++
        return true
    }

    companion object {
        const val WINDOW_MS = 60_000L
        const val PER_DEVICE = 10
        const val GLOBAL = 300
    }
}

@RestController
@RequestMapping("/api/v1")
class PlacesController(private val client: PlacesClient, private val limiter: PlacesRateLimiter) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/places")
    fun places(@AuthenticationPrincipal p: DevicePrincipal, @RequestParam lat: Double, @RequestParam lng: Double): ApiResponse<List<PlaceDto>> {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0 || lat.isNaN() || lng.isNaN()) throw ApiException.validation("위치 값이 올바르지 않습니다")
        if (!limiter.tryAcquire(p.deviceId)) throw ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요")
        val shops = try {
            client.nearbyRepairShops(lat, lng)
        } catch (e: ExternalPlacesException) {
            log.warn("주변 정비소 조회 실패: {}", e.message)
            throw ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.EXTERNAL_API_FAILED, "주변 정비소를 가져오지 못했습니다")
        }
        return ApiResponse.ok(shops.sortedBy { it.distanceM }.take(3))
    }
}
