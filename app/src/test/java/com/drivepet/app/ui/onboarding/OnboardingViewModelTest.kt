package com.drivepet.app.ui.onboarding

import com.drivepet.app.data.FakeRecordStore
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.ServicedPeriod
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.VehicleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val store = FakeRecordStore()
    private val state = MutableStateFlow(VehicleState())
    private var activated: String? = null
    private var locationConsent: Boolean? = null
    private var account: String? = null
    private lateinit var vm: OnboardingViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = OnboardingViewModel(store, state, { account }, { locationConsent = it }) { activated = it }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `nickname 1자·9자는 오류, 2자·8자는 통과`() {
        for ((nick, ok) in listOf("가" to false, "가나" to true, "가나다라마바사아" to true, "가나다라마바사아자" to false, "  가 " to false)) {
            vm.nickname = nick
            assertEquals(nick, if (ok) null else "2~8자 이내로 입력해주세요", vm.nicknameError())
        }
    }

    @Test fun `finish는 연결된 계정을 프로필 accountId로 쓴다`() = runTest {
        account = "acc"
        vm.starter = "STARTER_1"
        vm.nickname = "모모"
        vm.finish()
        assertEquals("acc", store.profiles().first().single().accountId)
    }

    @Test fun `finish는 onboardingCompleted=true 프로필을 저장하고 활성화한다`() = runTest {
        vm.starter = "STARTER_2"
        vm.nickname = " 모모 "
        vm.batteryText = "72.6"
        val id = vm.finish()
        val p = store.profiles().first().single()
        assertEquals(id, p.profileId)
        assertEquals(id, activated)
        assertTrue(p.onboardingCompleted)
        assertNull(p.accountId)
        assertEquals("내 차", p.vehicleName) // 차에서 읽은 이름이 없으면 「내 차」
        assertEquals("EV", p.fuelType)
        assertEquals("STARTER_2", p.starterType)
        assertEquals("모모", p.petNickname)
        assertEquals(72.6, p.batteryKwh!!, 0.0)
        assertTrue(p.createdAt > 0)
    }

    @Test fun `기본값과 canFinish는 닉네임이 있어야 true`() {
        assertEquals(FuelType.EV, vm.fuelType)
        assertFalse(vm.canFinish)
        vm.nickname = "모모"
        assertTrue(vm.canFinish)
    }

    @Test fun `starter defaults to STARTER_1`() = assertEquals("STARTER_1", vm.starter)

    @Test fun `ice asks four with engine oil first`() {
        vm.fuelType = FuelType.ICE
        assertEquals(listOf(CareItem.ENGINE_OIL, CareItem.TIRE, CareItem.CABIN_FILTER, CareItem.WIPER), vm.careQuestions)
        vm.fuelType = FuelType.HEV
        assertEquals(CareItem.ENGINE_OIL, vm.careQuestions.first())
    }

    @Test fun `ev asks three`() {
        vm.fuelType = FuelType.EV
        assertEquals(listOf(CareItem.TIRE, CareItem.CABIN_FILTER, CareItem.WIPER), vm.careQuestions)
    }

    @Test fun `setOdometerKm keeps period`() {
        vm.setPeriod(CareItem.TIRE, ServicedPeriod.WITHIN_3_MONTHS)
        vm.setOdometerKm(CareItem.TIRE, 24_000)
        assertEquals(CareBaseline(24_000_000_000, null, ServicedPeriod.WITHIN_3_MONTHS), vm.careBaselines[CareItem.TIRE])
        vm.setPeriod(CareItem.TIRE, ServicedPeriod.OVER_1_YEAR) // 질문으로 돌아가 다시 골라도 입력한 거리는 남는다
        assertEquals(CareBaseline(24_000_000_000, null, ServicedPeriod.OVER_1_YEAR), vm.careBaselines[CareItem.TIRE])
        vm.setOdometerKm(CareItem.TIRE, null)
        assertEquals(CareBaseline(null, null, ServicedPeriod.OVER_1_YEAR), vm.careBaselines[CareItem.TIRE])
    }

    @Test fun `terms need both required`() {
        assertFalse(vm.termsOk)
        vm.terms = setOf(Term.SERVICE, Term.LOCATION)
        assertFalse(vm.termsOk)
        vm.terms = setOf(Term.SERVICE, Term.VEHICLE_DATA)
        assertTrue(vm.termsOk)
    }

    @Test fun `선택 약관 위치를 거부하면 동의 false를 저장한다`() = runTest {
        vm.terms = setOf(Term.SERVICE, Term.VEHICLE_DATA)
        vm.saveTerms()
        assertEquals(false, locationConsent)
    }

    @Test fun `선택 약관 위치에 동의하면 동의 true를 저장한다`() = runTest {
        vm.terms = Term.entries.toSet()
        vm.saveTerms()
        assertEquals(true, locationConsent)
    }

    @Test fun `periodLabel은 피그마 보기 문구`() {
        assertEquals(listOf("최근 3개월 내", "3-6개월", "1년 이상", "기억 안남"), ServicedPeriod.entries.map(::periodLabel))
    }

    @Test fun `배터리 용량은 비우면 null, 숫자가 아니거나 0 이하면 오류와 다음 비활성`() {
        assertNull(vm.batteryKwh); assertNull(vm.batteryError()); assertTrue(vm.canNext)
        for (bad in listOf("abc", "0", "-5", "NaN", "1f", "1e3")) {
            vm.batteryText = bad
            assertEquals(bad, "올바른 용량을 입력해 주세요", vm.batteryError())
            assertFalse(bad, vm.canNext)
        }
        for (big in listOf("200.1", "201", "9".repeat(400))) {
            vm.batteryText = big
            assertEquals(big.take(10), "올바른 용량을 입력해 주세요", vm.batteryError())
        }
        vm.batteryText = "200"; assertNull(vm.batteryError())
        vm.batteryText = "64"
        assertEquals(64.0, vm.batteryKwh!!, 0.0); assertTrue(vm.canNext)
        vm.fuelType = FuelType.ICE // EV가 아니면 입력과 오류를 무시한다
        vm.batteryText = "abc"
        assertNull(vm.batteryKwh); assertNull(vm.batteryError()); assertTrue(vm.canNext)
    }

    @Test fun `capabilities는 상태 흐름을 따라 이름·상태 문구를 낸다`() {
        val speed = { vm.capabilities.value.first { it.name == "차속" }.status }
        val gear = { vm.capabilities.value.first { it.name == "기어" }.status }
        assertEquals(16, vm.capabilities.value.size) // 실내등 추가로 16개
        assertEquals("미측정", speed())
        state.value = state.value.copy(speedKmh = Signal.Ok(30.0, 0L), gear = Signal.Unsupported, odometerKm = Signal.NoPermission)
        assertEquals("읽힘", speed())
        assertEquals("지원 안 함", gear())
        assertEquals("권한 없음", vm.capabilities.value.first { it.name == "누적거리" }.status)
    }

    @Test fun `동시에 두 번 finish해도 프로필은 하나`() = runTest {
        val slow = OnboardingViewModel(store, state, { account }) { delay(100); activated = it }
        slow.starter = "STARTER_1"; slow.nickname = "모모"
        val a = async { slow.finish() }
        val b = async { slow.finish() }
        assertEquals(a.await(), b.await())
        assertEquals(1, store.profiles().first().size)
    }

    @Test fun `활성화가 실패하면 예외를 던지고, 다시 눌러도 같은 프로필 하나`() = runTest {
        var fail = true
        val flaky = OnboardingViewModel(store, state, { account }) { if (fail) throw IllegalStateException("x"); activated = it }
        flaky.starter = "STARTER_1"; flaky.nickname = "모모"
        assertTrue(runCatching { flaky.finish() }.isFailure)
        fail = false
        val id = flaky.finish()
        assertEquals(id, store.profiles().first().single().profileId)
        assertEquals(id, activated)
    }

    @Test fun `입력이 틀리면 finish는 저장하지 않는다`() = runTest {
        vm.nickname = "모"
        assertTrue(runCatching { vm.finish() }.isFailure) // 닉네임 오류
        assertTrue(store.profiles().first().isEmpty())
    }

    @Test fun finishSavesCareItems() = runTest {
        vm.fuelType = FuelType.EV
        vm.starter = "STARTER_1"
        vm.nickname = "모모"
        vm.setPeriod(com.drivepet.app.domain.CareItem.TIRE, com.drivepet.app.domain.ServicedPeriod.WITHIN_3_MONTHS)
        vm.setOdometerKm(CareItem.CABIN_FILTER, 20_000)
        val id = vm.finish()
        vm.finish() // 다시 눌러도 같은 세 항목
        val items = store.maintenanceItems(id).first().associateBy { it.kind }
        assertEquals(setOf("TIRE", "CABIN_FILTER", "WIPER"), items.keys)
        assertEquals("WITHIN_3_MONTHS", items["TIRE"]!!.baselineServicedPeriod)
        assertNull(items["CABIN_FILTER"]!!.baselineServicedOn)
        assertEquals(20_000_000_000, items["CABIN_FILTER"]!!.baselineOdometerMm)
        val wiper = items["WIPER"]!!
        assertNull(wiper.baselineOdometerMm)
        assertNull(wiper.baselineServicedOn)
        assertNull(wiper.baselineServicedPeriod)
    }
}
