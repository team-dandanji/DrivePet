package com.drivepet.app.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

// 필드명은 docs/api/schemas/driving.yaml 의 Weather. 나머지 키는 무시한다.
@Serializable data class AirQualityDto(val pm10: Int, val pm25: Int, val grade: String, val observedAt: String)
@Serializable data class WeatherDto(
    val tempC: Double,
    val sky: String,
    val precipitationType: String? = null,
    val warnings: List<String>,
    val airQuality: AirQualityDto?,
    val stale: Boolean,
)

/** 어떤 실패든 null. 날씨는 없어도 앱이 그대로 동작해야 한다. */
class WeatherRepo(
    private val api: suspend () -> DrivePetApi,
    private val location: () -> Pair<Double, Double>,
    private val consent: suspend () -> Boolean = { true },
) {
    /** 위치 정보 이용에 동의하지 않았으면 위치도 서버도 건드리지 않고 null. */
    suspend fun load(): WeatherDto? = try {
        if (!consent()) {
            null
        } else {
            val (lat, lng) = withContext(Dispatchers.IO) { location() } // getLastKnownLocation은 메인 스레드를 피한다
            api().weather(lat, lng).data
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
