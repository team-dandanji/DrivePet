package com.drivepet.app.ui.lounge

import com.drivepet.app.data.FakeRecordStore
import com.drivepet.app.data.db.ProfileEntity
import kotlinx.coroutines.flow.first
import com.drivepet.app.domain.dailyMissions
import com.drivepet.app.domain.Mood
import com.drivepet.app.drive.Bubble
import com.drivepet.app.data.remote.WeatherDto
import com.drivepet.app.drive.Speaker
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.VehicleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LoungeViewModelTest {
    private class FakeSpeaker : Speaker {
        override val bubble = MutableStateFlow<Bubble?>(null)
        val said = mutableListOf<String>()
        override fun say(text: String, voice: Boolean) { said += text }
    }

    private val store = FakeRecordStore()
    private val state = MutableStateFlow(VehicleState(gear = Signal.Ok(Gear.P, 0L)))
    private val belt = MutableStateFlow(false)
    private val speaker = FakeSpeaker()
    private var greetDate: String? = null
    private var now = Instant.parse("2026-11-10T03:00:00Z") // 서울 12시
    private val today = LocalDate.parse("2026-11-10")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        kotlinx.coroutines.runBlocking {
            store.upsertProfile(ProfileEntity("p1", null, "아이오닉5", "EV", "STARTER_1", "모모", true, 72.6, 0L))
        }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    /** 아무것도 안 한 날의 미션 줄(첫 미션). */
    private fun ml(d: LocalDate) = "오늘 미션: ${dailyMissions("p1", d).first().title}"

    private fun vm(checkIn: suspend () -> Boolean = { true }, loadWeather: suspend () -> WeatherDto? = { null }) = LoungeViewModel(
        store, state, MutableStateFlow("p1"), belt, speaker, checkIn,
        kotlinx.coroutines.flow.flow { emit(greetDate) }, { greetDate = it }, { now }, loadWeather,
    )

    @Test fun `370 seed는 Lv1 92퍼센트`() = runTest {
        store.setSeed("p1", 370_000L)
        val ui = vm().ui.value!!
        assertEquals(1, ui.stage)
        assertEquals(92, ui.gauge.percent)
        assertEquals("아이오닉5 | 전기차(EV)", ui.vehicleLine)
    }

    @Test fun `TPMS 경고 2일째면 SICK_2와 HOSPITAL`() = runTest {
        store.syncWarnings("p1", listOf("TPMS"), today.minusDays(2))
        val ui = vm().ui.value!!
        assertEquals(Mood.SICK_2, ui.mood)
        assertEquals(CareAction.HOSPITAL, ui.careAction)
        assertEquals(listOf("TPMS"), ui.warnings)
    }

    @Test fun `onEnter checks in when parked, also later when parked`() = runTest {
        state.value = VehicleState(gear = Signal.Ok(Gear.D, 0L))
        var calls = 0
        val v = vm(checkIn = { calls++; true })
        v.onEnter()
        assertEquals(0, calls)
        state.value = VehicleState(gear = Signal.Ok(Gear.P, 1L))
        assertEquals(1, calls)
        v.onEnter() // 감시는 한 번만 띄운다
        state.value = VehicleState(gear = Signal.Ok(Gear.P, 2L))
        assertEquals(1, calls)
    }

    @Test fun `checkIn failure does not kill greeting`() = runTest {
        val v = vm(checkIn = { error("boom") })
        v.onEnter()
        assertEquals(1, speaker.said.size)
    }

    @Test fun `하루 첫 진입만 전체 인사`() = runTest {
        val v = vm()
        v.onEnter()
        assertEquals(listOf("안녕, 나 모모! 오늘도 같이 달려요 ${ml(today)}"), speaker.said)
        assertEquals("2026-11-10", greetDate)
        v.onEnter()
        assertEquals("오늘도 같이 달려요", speaker.said.last())
        now = Instant.parse("2026-11-11T03:00:00Z")
        state.value = VehicleState(gear = Signal.Ok(Gear.P, 1L)) // 차 상태가 갱신되면 화면 값(오늘 미션)도 새 날짜로 다시 계산된다
        v.onEnter()
        assertEquals("안녕, 나 모모! 오늘도 같이 달려요 ${ml(today.plusDays(1))}", speaker.said.last())
        assertNotNull(greetDate)
    }

    @Test fun `포만 20 이하여도 돌보기 버튼은 없다`() = runTest {
        state.value = VehicleState(gear = Signal.Ok(Gear.P, 0L), energyPct = Signal.Ok(18, 0L))
        val ui = vm().ui.value!!
        assertEquals(Mood.HUNGRY, ui.mood)
        assertNull(ui.careAction)
    }

    private val clear12 = WeatherDto(12.0, "CLEAR", null, emptyList(), null, false)

    @Test fun `greetIncludesWeatherWhenReady`() = runTest {
        val v = vm { clear12 }
        v.onEnter()
        assertEquals("안녕, 나 모모! 오늘도 같이 달려요 오늘은 맑음, 12°C야. ${ml(today)}", speaker.said.single())
        assertEquals("맑음", v.weather.value!!.label)
    }

    @Test fun `greetWithoutWeatherAfterTimeout`() = runTest {
        val v = vm { awaitCancellation() }
        v.onEnter()
        runCurrent()
        assertTrue(speaker.said.isEmpty())
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(listOf("안녕, 나 모모! 오늘도 같이 달려요 ${ml(today)}"), speaker.said)
        assertEquals(null, v.weather.value)
    }

    @Test fun `weatherFailureHidesCard`() = runTest {
        val v = vm { null }
        v.onEnter()
        assertEquals(null, v.weather.value)
        assertEquals(listOf("안녕, 나 모모! 오늘도 같이 달려요 ${ml(today)}"), speaker.said)
    }

    @Test fun worriedWhenCareLow() = runTest {
        state.value = VehicleState(gear = Signal.Ok(Gear.P, 0L), odometerKm = Signal.Ok(24_500.0, 0L), warnings = Signal.Ok(emptyList(), 0L))
        val before = vm().ui.value!!
        assertEquals(Mood.NORMAL, before.mood)
        store.saveMaintenanceItems(listOf(com.drivepet.app.data.careItemEntity("p1", com.drivepet.app.domain.CareItem.TIRE, com.drivepet.app.domain.CareBaseline(15_000_000_000, null, null))))
        val ui = vm().ui.value!!
        assertEquals(Mood.WORRIED, ui.mood) // 9,500km 지남 → 남은 주기 5%
        assertEquals(100, before.healthPct) // 경고등만: 40/40
        assertEquals(Math.round((40 + 5 * 0.3) / 70 * 100).toInt(), ui.healthPct)
    }
}
