package com.drivepet.app.data.remote

import kotlinx.coroutines.CancellationException
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
class WeatherRepo(private val api: suspend () -> DrivePetApi, private val location: () -> Pair<Double, Double>) {
    suspend fun load(): WeatherDto? = try {
        val (lat, lng) = location()
        api().weather(lat, lng).data
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
