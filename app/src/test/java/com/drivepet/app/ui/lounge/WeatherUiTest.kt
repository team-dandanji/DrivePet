package com.drivepet.app.ui.lounge

import com.drivepet.app.data.remote.AirQualityDto
import com.drivepet.app.data.remote.WeatherDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherUiTest {
    private fun dto(
        temp: Double = 18.0, sky: String = "CLEAR", precip: String? = null, warnings: List<String> = emptyList(),
        air: AirQualityDto? = null, stale: Boolean = false,
    ) = WeatherDto(temp, sky, precip, warnings, air, stale)

    @Test fun rainBeatsSky() {
        assertEquals("비", weatherUi(dto(sky = "CLEAR", precip = "RAIN")).label)
        assertEquals("맑음", weatherUi(dto(sky = "CLEAR", precip = "NONE")).label)
        assertEquals("구름 조금", weatherUi(dto(sky = "PARTLY_CLOUDY")).label)
    }

    @Test fun roundsTemp() {
        assertEquals("12°C", weatherUi(dto(temp = 11.6)).temp)
        assertEquals("-3°C", weatherUi(dto(temp = -2.6)).temp)
    }

    @Test fun airNullWhenMissing() {
        assertNull(weatherUi(dto(air = null)).air)
        assertEquals("미세먼지 나쁨", weatherUi(dto(air = AirQualityDto(80, 40, "BAD", "2026-11-11T00:00:00Z"))).air)
    }

    @Test fun greetLineWithWarningAndStale() {
        val ui = weatherUi(dto(temp = 5.0, sky = "CLOUDY", warnings = listOf("한파주의보"), stale = true))
        assertEquals("오늘은 흐림, 5°C야. 한파주의보 발효 중이니 조심해! (이전 정보)", ui.greetLine)
    }
}
