package com.drivepet.app.ui.onboarding

import com.drivepet.app.data.FakeRecordStore
import com.drivepet.app.domain.FuelType
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
    private lateinit var vm: OnboardingViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = OnboardingViewModel(store, state) { activated = it }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `nickname 1자·9자는 오류, 2자·8자는 통과`() {
        for ((nick, ok) in listOf("가" to false, "가나" to true, "가나다라마바사아" to true, "가나다라마바사아자" to false, "  가 " to false)) {
            vm.nickname = nick
            assertEquals(nick, if (ok) null else "2~8자로 지어 주세요", vm.nicknameError())
        }
    }

    @Test fun `finish는 onboardingCompleted=true 프로필을 저장하고 활성화한다`() = runTest {
        vm.vehicleName = " 아이오닉 "
        vm.starter = "STARTER_2"
        vm.nickname = " 모모 "
        vm.batteryText = "72.6"
        val id = vm.finish()
        val p = store.profiles().first().single()
        assertEquals(id, p.profileId)
        assertEquals(id, activated)
        assertTrue(p.onboardingCompleted)
        assertNull(p.accountId)
        assertEquals("아이오닉", p.vehicleName)
        assertEquals("EV", p.fuelType)
        assertEquals("STARTER_2", p.starterType)
        assertEquals("모모", p.petNickname)
        assertEquals(72.6, p.batteryKwh!!, 0.0)
        assertTrue(p.createdAt > 0)
    }

    @Test fun `기본값과 canFinish는 스타터 선택과 닉네임이 있어야 true`() {
        assertEquals("내 차", vm.vehicleName)
        assertEquals(FuelType.EV, vm.fuelType)
        assertFalse(vm.canFinish)
        vm.nickname = "모모"
        assertFalse(vm.canFinish)
        vm.starter = "STARTER_1"
        assertTrue(vm.canFinish)
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

    @Test fun `차량 이름은 1~20자`() {
        vm.vehicleName = ""; assertFalse(vm.canNext)
        vm.vehicleName = "   "; assertFalse(vm.canNext)
        vm.vehicleName = "가".repeat(20); assertTrue(vm.canNext)
        vm.vehicleName = "가".repeat(21); assertFalse(vm.canNext)
    }

    @Test fun `capabilities는 상태 흐름을 따라 이름·상태 문구를 낸다`() {
        val speed = { vm.capabilities.value.first { it.name == "차속" }.status }
        val gear = { vm.capabilities.value.first { it.name == "기어" }.status }
        assertEquals(15, vm.capabilities.value.size)
        assertEquals("미측정", speed())
        state.value = state.value.copy(speedKmh = Signal.Ok(30.0, 0L), gear = Signal.Unsupported, odometerKm = Signal.NoPermission)
        assertEquals("읽힘", speed())
        assertEquals("지원 안 함", gear())
        assertEquals("권한 없음", vm.capabilities.value.first { it.name == "누적거리" }.status)
    }

    @Test fun `동시에 두 번 finish해도 프로필은 하나`() = runTest {
        val slow = OnboardingViewModel(store, state) { delay(100); activated = it }
        slow.starter = "STARTER_1"; slow.nickname = "모모"
        val a = async { slow.finish() }
        val b = async { slow.finish() }
        assertEquals(a.await(), b.await())
        assertEquals(1, store.profiles().first().size)
    }

    @Test fun `활성화가 실패하면 예외를 던지고, 다시 눌러도 같은 프로필 하나`() = runTest {
        var fail = true
        val flaky = OnboardingViewModel(store, state) { if (fail) throw IllegalStateException("x"); activated = it }
        flaky.starter = "STARTER_1"; flaky.nickname = "모모"
        assertTrue(runCatching { flaky.finish() }.isFailure)
        fail = false
        val id = flaky.finish()
        assertEquals(id, store.profiles().first().single().profileId)
        assertEquals(id, activated)
    }

    @Test fun `스타터가 없거나 입력이 틀리면 finish는 저장하지 않는다`() = runTest {
        vm.nickname = "모모"
        assertTrue(runCatching { vm.finish() }.isFailure) // 스타터 없음
        vm.starter = "STARTER_1"; vm.nickname = "모"
        assertTrue(runCatching { vm.finish() }.isFailure) // 닉네임 오류
        assertTrue(store.profiles().first().isEmpty())
    }
}
