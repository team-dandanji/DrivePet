package com.drivepet.server.weather

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.OffsetDateTime

@ConfigurationProperties("drivepet.weather")
data class WeatherProperties(
    val mode: String = "fake",
    val serviceKey: String = "",
    val cacheTtl: Duration = Duration.ofMinutes(10),
    val cacheMaxEntries: Int = 500,
    // 호출 1번의 연결·응답 대기. 공공데이터포털은 가끔 4초를 넘겨 응답한다
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(6),
    // 연결 실패·응답 지연·5xx만 다시 시도하는 횟수 (결과 코드 오류는 다시 시도하지 않음)
    val retries: Int = 1,
    // 날씨 요청 하나가 기다리는 최대 시간. 넘으면 기상청은 이전 캐시 또는 502, 에어코리아는 null. 늦게 온 결과는 캐시에 남긴다
    val requestBudget: Duration = Duration.ofSeconds(8),
    val kmaBaseUrl: String = "https://apis.data.go.kr/1360000",
    val airKoreaBaseUrl: String = "https://apis.data.go.kr/B552584",
)

/** 외부 조회 실패. 메시지에 URL·키·좌표를 넣지 않는다 */
class ExternalWeatherException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 초단기실황 */
data class KmaNow(val tempC: Double, val precipitationCode: Int, val observedAt: OffsetDateTime)

/** 단기예보에서 가장 가까운 시각의 하늘·강수확률 */
data class KmaForecast(val skyCode: Int, val precipitationProbPct: Int?)

/** 현재 발효 중인 특보 1건 (예: 강풍주의보, 지역 목록) */
data class WarningItem(val name: String, val regions: String)

data class Station(val name: String, val distanceKm: Double?)

data class AirMeasure(val pm10: Int?, val pm25: Int?, val pm10Grade: Int?, val pm25Grade: Int?, val observedAt: OffsetDateTime?)

interface KmaClient {
    fun now(grid: Grid, at: OffsetDateTime): KmaNow
    fun forecast(grid: Grid, at: OffsetDateTime): KmaForecast

    /** 전국 현재 발효 특보. 특보가 없으면 빈 목록(실패와 구분) */
    fun activeWarnings(at: OffsetDateTime): List<WarningItem>
}

interface AirKoreaClient {
    fun nearbyStations(tmX: Double, tmY: Double): List<Station>
    fun measure(stationName: String): AirMeasure?
}
