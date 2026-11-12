package com.drivepet.server.places

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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper

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

@RestController
@RequestMapping("/api/v1")
class PlacesController(private val client: PlacesClient) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/places")
    fun places(@RequestParam lat: Double, @RequestParam lng: Double): ApiResponse<List<PlaceDto>> {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0 || lat.isNaN() || lng.isNaN()) throw ApiException.validation("위치 값이 올바르지 않습니다")
        val shops = try {
            client.nearbyRepairShops(lat, lng)
        } catch (e: ExternalPlacesException) {
            log.warn("주변 정비소 조회 실패: {}", e.message)
            throw ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.EXTERNAL_API_FAILED, "주변 정비소를 가져오지 못했습니다")
        }
        return ApiResponse.ok(shops.sortedBy { it.distanceM }.take(3))
    }
}
