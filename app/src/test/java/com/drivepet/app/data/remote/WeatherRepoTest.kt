package com.drivepet.app.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** weather만 응답하는 가짜. 나머지 호출은 QrApi에 맡긴다(쓰지 않음). */
private class WeatherApi : DrivePetApi by QrApi() {
    var calls = 0
    override suspend fun weather(lat: Double, lng: Double): Envelope<WeatherDto> {
        calls++
        return Envelope(WeatherDto(12.0, "CLEAR", null, emptyList(), null, false))
    }
}

class WeatherRepoTest {
    @Test fun `동의하면 위치로 날씨를 불러온다`() = runTest {
        val api = WeatherApi()
        assertEquals(12.0, WeatherRepo({ api }, { 37.5 to 127.0 }, { true }).load()!!.tempC, 0.0)
        assertEquals(1, api.calls)
    }

    @Test fun `동의하지 않으면 null, 위치도 서버도 건드리지 않는다`() = runTest {
        val api = WeatherApi()
        var locationReads = 0
        val repo = WeatherRepo({ api }, { locationReads++; 37.5 to 127.0 }, { false })
        assertNull(repo.load())
        assertEquals(0, locationReads)
        assertEquals(0, api.calls)
    }
}
