package com.drivepet.server.weather

import com.drivepet.server.common.ApiException
import com.drivepet.server.common.ApiResponse
import com.drivepet.server.common.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WeatherProperties::class)
class WeatherConfig {
    @Bean
    @ConditionalOnProperty("drivepet.weather.mode", havingValue = "fake", matchIfMissing = true)
    fun fakeWeatherSource() = FakeWeatherSource()

    @Bean
    @ConditionalOnProperty("drivepet.weather.mode", havingValue = "live")
    fun kmaClient(props: WeatherProperties, json: JsonMapper): KmaClient {
        require(props.serviceKey.isNotBlank()) { "live 모드에는 DATA_GO_KR_SERVICE_KEY가 필요합니다" }
        return HttpKmaClient(DataGoKrHttp(props.kmaBaseUrl, props.serviceKey, props, json))
    }

    @Bean
    @ConditionalOnProperty("drivepet.weather.mode", havingValue = "live")
    fun airKoreaClient(props: WeatherProperties, json: JsonMapper): AirKoreaClient {
        require(props.serviceKey.isNotBlank()) { "live 모드에는 DATA_GO_KR_SERVICE_KEY가 필요합니다" }
        return HttpAirKoreaClient(DataGoKrHttp(props.airKoreaBaseUrl, props.serviceKey, props, json))
    }
}

data class LocationDto(val lat: Double, val lng: Double, val address: String?)
data class AirQualityDto(val pm10: Int, val pm25: Int, val grade: String, val observedAt: OffsetDateTime)
data class WeatherDto(
    val location: LocationDto,
    val tempC: Double,
    val sky: String,
    val precipitationType: String,
    val precipitationProbPct: Int?,
    val warnings: List<String>,
    val airQuality: AirQualityDto?,
    val observedAt: OffsetDateTime,
    val fetchedAt: OffsetDateTime,
    val stale: Boolean,
)

/** 격자 단위 캐시 값. 요청 위경도는 담지 않는다 */
private data class KmaEntry(
    val tempC: Double, val sky: String, val precipitationType: String, val pop: Int?,
    val warnings: List<String>, val observedAt: OffsetDateTime, val fetchedAt: Instant,
    /** 담긴 특보를 받은 시각. 격자 캐시가 특보 원본보다 오래 살지 않게 만료는 둘 중 이른 쪽으로 본다 */
    val warningsFetchedAt: Instant,
) {
    val validFrom: Instant get() = minOf(fetchedAt, warningsFetchedAt)
}
private data class AirEntry(val value: AirQualityDto?, val fetchedAt: Instant)
private data class WarningEntry(val items: List<WarningItem>, val fetchedAt: Instant)

/** 값마다 조회 시작 시각(fetchedAt)을 가진 캐시. 늦게 끝난 옛 조회가 새 값을 덮지 않게 더 새로운 값만 저장한다 */
private class LruCache<V>(private val max: Int, private val fetchedAt: (V) -> Instant) {
    private val map = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?) = size > max
    }
    @Synchronized fun get(k: String): V? = map[k]
    @Synchronized fun putIfNewer(k: String, v: V) {
        val old = map[k]
        if (old == null || !fetchedAt(v).isBefore(fetchedAt(old))) map[k] = v
    }
    @Synchronized fun clear() = map.clear()
}

@Service
class WeatherService(
    private val kma: KmaClient,
    private val air: AirKoreaClient,
    private val props: WeatherProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val kmaCache = LruCache<KmaEntry>(props.cacheMaxEntries) { it.fetchedAt }
    private val airCache = LruCache<AirEntry>(props.cacheMaxEntries) { it.fetchedAt }
    private val seoul = ZoneId.of("Asia/Seoul")

    private val warningCache = LruCache<WarningEntry>(1) { it.fetchedAt }
    // 기상청·에어코리아 호출을 동시에 보내고, 요청 시간 한도를 넘겨도 끝까지 받아 캐시에 남긴다
    private val pool = Executors.newVirtualThreadPerTaskExecutor()

    fun clearCache() { kmaCache.clear(); airCache.clear(); warningCache.clear() }

    fun weather(lat: Double, lng: Double): WeatherDto {
        val now = clock.instant()
        val deadline = System.nanoTime() + props.requestBudget.toNanos()
        val grid = KmaGrid.toGrid(lat, lng)
        val (cLat, cLng) = KmaGrid.center(grid)
        val sido = Sido.of(grid, cLat, cLng)

        val cached = kmaCache.get(grid.key)
        val kmaFuture = if (cached != null && fresh(cached.validFrom, now)) CompletableFuture.completedFuture(cached)
            else async { fetchKma(grid, sido, now).also { kmaCache.putIfNewer(grid.key, it) } }
        val airFuture = airCache.get(grid.key)?.takeIf { fresh(it.fetchedAt, now) }?.let { CompletableFuture.completedFuture(it) }
            ?: async { AirEntry(fetchAir(cLat, cLng), now).also { airCache.putIfNewer(grid.key, it) } }

        var stale = false
        val kmaEntry = try {
            await(kmaFuture, deadline, "기상청")
        } catch (e: ExternalWeatherException) {
            log.warn("기상청 조회 실패: {}", e.message)
            if (cached == null) throw ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.EXTERNAL_API_FAILED, "날씨 정보를 가져오지 못했습니다")
            stale = true
            cached
        }
        val airEntry = try {
            await(airFuture, deadline, "에어코리아")
        } catch (e: ExternalWeatherException) {
            log.warn("에어코리아 조회 실패: {}", e.message)
            AirEntry(null, now)
        }
        return WeatherDto(
            location = LocationDto(lat, lng, sido.label),
            tempC = kmaEntry.tempC,
            sky = kmaEntry.sky,
            precipitationType = kmaEntry.precipitationType,
            precipitationProbPct = kmaEntry.pop,
            warnings = kmaEntry.warnings,
            airQuality = airEntry.value,
            observedAt = kmaEntry.observedAt,
            fetchedAt = kmaEntry.fetchedAt.atZone(seoul).toOffsetDateTime(),
            stale = stale,
        )
    }

    private fun fresh(at: Instant, now: Instant) = at.plus(props.cacheTtl).isAfter(now)

    private val running = AtomicInteger()

    /** 특보 대기 한도: 요청 한도보다 2초 짧게(한도가 짧으면 절반) */
    private val warningsBound = maxOf(props.requestBudget.dividedBy(2), props.requestBudget.minusSeconds(2))

    private fun <T> async(task: () -> T): CompletableFuture<T> {
        running.incrementAndGet()
        return CompletableFuture.supplyAsync({ try { task() } finally { running.decrementAndGet() } }, pool)
    }

    /** 테스트용: 한도 뒤에도 계속 도는 조회(캐시 저장 포함)가 모두 끝날 때까지 기다린다 */
    internal fun awaitBackground(timeout: java.time.Duration): Boolean {
        val until = System.nanoTime() + timeout.toNanos()
        while (running.get() > 0) {
            if (System.nanoTime() > until) return false
            Thread.sleep(10)
        }
        return true
    }

    /** 한도 시각까지 기다린다. 넘으면 외부 실패로 보고, 작업 안의 예외는 그대로 던진다 */
    private fun <T> await(f: CompletableFuture<T>, deadline: Long, what: String): T = try {
        f.get(maxOf(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
    } catch (e: TimeoutException) {
        throw ExternalWeatherException("$what 응답 시간 초과")
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

    private fun <T> join(f: CompletableFuture<T>): T = try { f.join() } catch (e: CompletionException) { throw e.cause ?: e }

    /** 실황·예보·특보를 동시에 부른다. 실황·예보가 하나라도 실패하면 기상청 실패, 특보 실패는 빈 특보로 넘어간다 */
    private fun fetchKma(grid: Grid, sido: Sido, now: Instant): KmaEntry {
        val at = now.atZone(seoul).toOffsetDateTime()
        val n = async { kma.now(grid, at) }
        val f = async { kma.forecast(grid, at) }
        val w = async { nationalWarnings(at, now) }
        // 특보만 요청 한도보다 일찍 포기해, 특보가 걸려도 날씨는 한도 안에 응답한다
        val national = try { await(w, System.nanoTime() + warningsBound.toNanos(), "기상특보") } catch (e: ExternalWeatherException) {
            log.warn("기상특보 조회 실패, 특보 없이 응답: {}", e.message)
            noWarnings(now)
        }
        val nowValue = join(n)
        val forecast = join(f)
        return KmaEntry(
            nowValue.tempC, sky(forecast.skyCode), precipitation(nowValue.precipitationCode), forecast.precipitationProbPct,
            warningsFor(national.items, sido), nowValue.observedAt, now, national.fetchedAt,
        )
    }

    /**
     * 특보 현황은 전국 하나라 격자와 따로 캐시한다.
     * 특보를 못 읽으면 날씨는 살리고 이번 응답만 빈 특보로 한다. 특보 캐시에는 넣지 않아 다음 요청이 다시 조회하고,
     * 담기는 격자 캐시는 [WARNING_RETRY]만 살도록 특보 시각을 TTL보다 그만큼만 남기고 당겨 둔다.
     */
    private fun nationalWarnings(at: OffsetDateTime, now: Instant): WarningEntry {
        warningCache.get("KR")?.takeIf { fresh(it.fetchedAt, now) }?.let { return it }
        return try {
            WarningEntry(kma.activeWarnings(at), now).also { warningCache.putIfNewer("KR", it) }
        } catch (e: Exception) {
            log.warn("기상특보 조회 실패, 특보 없이 응답: {}", e.message)
            noWarnings(now)
        }
    }

    private fun noWarnings(now: Instant) =
        WarningEntry(emptyList(), now.minus(props.cacheTtl).plus(minOf(WARNING_RETRY, props.cacheTtl)))

    private fun fetchAir(lat: Double, lng: Double): AirQualityDto? {
        val (tmX, tmY) = AirKoreaTm.toTm(lat, lng)
        for (station in air.nearbyStations(tmX, tmY).take(3)) {
            val m = air.measure(station.name) ?: continue
            if (m.pm10 == null || m.pm25 == null) continue
            val grade = listOfNotNull(m.pm10Grade, m.pm25Grade).maxOrNull() ?: continue
            return AirQualityDto(m.pm10, m.pm25, grade(grade) ?: continue, m.observedAt ?: continue)
        }
        return null
    }

    companion object {
        private val WARNING_RETRY = java.time.Duration.ofMinutes(1)

        fun sky(code: Int) = when (code) { 1 -> "CLEAR"; 3 -> "PARTLY_CLOUDY"; else -> "CLOUDY" }

        fun precipitation(code: Int) = when (code) {
            0 -> "NONE"; 1, 5 -> "RAIN"; 2, 6 -> "RAIN_SNOW"; 3, 7 -> "SNOW"; 4 -> "SHOWER"; else -> "NONE"
        }

        fun grade(code: Int) = when (code) { 1 -> "GOOD"; 2 -> "NORMAL"; 3 -> "BAD"; 4 -> "VERY_BAD"; else -> null }

        /** 시·도 범위의 육상 특보만. 괄호 안 세부 구역은 떼고 맨 앞 지역명으로 시·도를 찾는다 */
        fun warningsFor(items: List<WarningItem>, sido: Sido): List<String> = items
            .filter { "풍랑" !in it.name }
            .filter { item ->
                item.regions.replace(Regex("\\([^)]*\\)"), "").split(",").map { it.trim() }
                    .filter { it.isNotEmpty() && "바다" !in it }
                    .any { region -> sido.aliases.any { region.startsWith(it) } }
            }
            .map { "${it.name} (${sido.label} 범위)" }
            .distinct()
    }
}

@RestController
@RequestMapping("/api/v1")
class WeatherController(private val service: WeatherService) {
    @GetMapping("/weather")
    fun weather(@RequestParam lat: Double, @RequestParam lng: Double): ApiResponse<WeatherDto> {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0 || lat.isNaN() || lng.isNaN()) throw ApiException.validation("위치 값이 올바르지 않습니다")
        return ApiResponse.ok(service.weather(lat, lng))
    }
}
