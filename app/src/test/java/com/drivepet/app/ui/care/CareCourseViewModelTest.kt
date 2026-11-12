package com.drivepet.app.ui.care

import com.drivepet.app.data.remote.DrivePetApi
import com.drivepet.app.data.remote.Envelope
import com.drivepet.app.data.remote.PlaceDto
import com.drivepet.app.data.remote.PlacesRepo
import com.drivepet.app.data.remote.QrApi
import com.drivepet.app.domain.CareItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** places만 응답하는 가짜. 나머지 호출은 QrApi에 맡긴다(쓰지 않음). */
private class PlacesApi(var result: () -> List<PlaceDto>) : DrivePetApi by QrApi() {
    var lastLat = 0.0
    var calls = 0
    override suspend fun places(lat: Double, lng: Double): Envelope<List<PlaceDto>> { calls++; lastLat = lat; return Envelope(result()) }
}

private fun place(id: String, m: Int) = PlaceDto(id, "정비소 $id", "주소 $id", 37.4, 127.1, m)

@OptIn(ExperimentalCoroutinesApi::class)
class CareCourseViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(api: PlacesApi, eta: (PlaceDto) -> Int? = { null }, location: () -> Pair<Double, Double>? = { 37.44 to 127.13 }, consent: Boolean = true) =
        CareCourseViewModel(PlacesRepo { api }, { location() }, { eta(it) }, CareItem.TIRE, { consent })

    @Test fun `shows three stops sorted by distance with minutes`() {
        val api = PlacesApi { listOf(place("c", 4800), place("a", 2100), place("b", 3400)) }
        val ui = vm(api).ui.value as CourseUi.Ready
        assertEquals(listOf("a", "b", "c"), ui.stops.map { it.place.id })
        assertEquals(listOf(7, 12, 16), ui.stops.map { it.minutes }) // ceil(m / 300)
        assertEquals(0, ui.selected)
        assertEquals(CareItem.TIRE, ui.target)
        assertEquals(37.44, api.lastLat, 0.0)
    }

    @Test fun `uses navi eta when present`() {
        val api = PlacesApi { listOf(place("a", 9000)) }
        val ui = vm(api, eta = { 420 }).ui.value as CourseUi.Ready
        assertEquals(7, ui.stops.single().minutes)
    }

    @Test fun `empty result is Empty`() {
        assertEquals(CourseUi.Empty, vm(PlacesApi { emptyList() }).ui.value)
    }

    @Test fun `repo throws is Failed and retry recovers`() {
        val api = PlacesApi { error("502") }
        val vm = vm(api)
        assertEquals(CourseUi.Failed, vm.ui.value)
        api.result = { listOf(place("a", 2100)) }
        vm.retry()
        assertEquals(1, (vm.ui.value as CourseUi.Ready).stops.size)
    }

    @Test fun `no location is Failed without asking the server, retry recovers`() {
        val api = PlacesApi { listOf(place("a", 2100)) }
        var loc: Pair<Double, Double>? = null
        val vm = vm(api, location = { loc })
        assertEquals(CourseUi.Failed, vm.ui.value)
        assertEquals(0, api.calls)
        loc = 37.44 to 127.13
        vm.retry()
        assertEquals(1, (vm.ui.value as CourseUi.Ready).stops.size)
    }

    @Test fun `동의하지 않으면 NoConsent, 위치도 서버도 쓰지 않는다`() {
        val api = PlacesApi { listOf(place("a", 2100)) }
        var locationReads = 0
        val vm = vm(api, location = { locationReads++; 37.44 to 127.13 }, consent = false)
        assertEquals(CourseUi.NoConsent, vm.ui.value)
        assertEquals(0, locationReads)
        assertEquals(0, api.calls)
    }

    @Test fun `remove last stop gives Empty`() {
        val vm = vm(PlacesApi { listOf(place("a", 2100), place("b", 3400)) })
        vm.select(1)
        vm.remove(1)
        val ui = vm.ui.value as CourseUi.Ready
        assertEquals(listOf("a"), ui.stops.map { it.place.id })
        assertEquals(0, ui.selected)
        vm.remove(0)
        assertEquals(CourseUi.Empty, vm.ui.value)
    }
}
