package com.drivepet.server.weather

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 키 없이 개발·테스트하는 가짜 기상청·에어코리아. 값은 고정이고, 테스트가 실패·특보·결측을 켤 수 있다.
 * local-fake / test 프로필 전용 (실제 모드에서는 빈으로 만들지 않는다).
 */
class FakeWeatherSource : KmaClient, AirKoreaClient {
    @Volatile var failNow = false
    @Volatile var failForecast = false
    @Volatile var failWarnings = false
    @Volatile var failAir = false
    @Volatile var noStations = false
    @Volatile var warnings: List<WarningItem> = emptyList()
    @Volatile var tempC = 6.5
    @Volatile var kmaDelayMs = 0L
    @Volatile var airDelayMs = 0L
    @Volatile var warningsDelayMs = 0L
    val kmaCalls = AtomicInteger()
    val airCalls = AtomicInteger()

    fun reset() {
        failNow = false; failForecast = false; failWarnings = false; failAir = false; noStations = false
        warnings = emptyList(); tempC = 6.5; kmaDelayMs = 0; airDelayMs = 0; warningsDelayMs = 0; kmaCalls.set(0); airCalls.set(0)
    }

    override fun now(grid: Grid, at: OffsetDateTime): KmaNow {
        kmaCalls.incrementAndGet()
        if (kmaDelayMs > 0) Thread.sleep(kmaDelayMs)
        if (failNow) throw ExternalWeatherException("가짜 기상청 실황 실패")
        return KmaNow(tempC, 1, at.truncatedTo(ChronoUnit.HOURS))
    }

    override fun forecast(grid: Grid, at: OffsetDateTime): KmaForecast {
        if (failForecast) throw ExternalWeatherException("가짜 기상청 예보 실패")
        return KmaForecast(4, 70)
    }

    override fun activeWarnings(at: OffsetDateTime): List<WarningItem> {
        val snapshot = warnings // 호출 시작 시점의 특보 (늦게 끝나는 옛 조회 흉내)
        if (warningsDelayMs > 0) Thread.sleep(warningsDelayMs)
        if (failWarnings) throw ExternalWeatherException("가짜 기상특보 실패")
        return snapshot
    }

    override fun nearbyStations(tmX: Double, tmY: Double): List<Station> {
        airCalls.incrementAndGet()
        if (airDelayMs > 0) Thread.sleep(airDelayMs)
        if (failAir) throw ExternalWeatherException("가짜 에어코리아 실패")
        return if (noStations) emptyList() else listOf(Station("가짜측정소", 1.2))
    }

    override fun measure(stationName: String): AirMeasure? {
        if (failAir) throw ExternalWeatherException("가짜 에어코리아 실패")
        return AirMeasure(45, 22, 2, 2, OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS))
    }
}
