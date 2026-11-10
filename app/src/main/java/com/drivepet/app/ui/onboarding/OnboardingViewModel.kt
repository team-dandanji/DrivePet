package com.drivepet.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivepet.app.AppContainer
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.domain.FuelType
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.VehicleState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** 차에서 읽을 수 있는지 확인하는 항목 하나. name은 화면 이름, status는 읽힘/지원 안 함/권한 없음/미측정. */
data class Capability(val name: String, val status: String)

fun capabilitiesOf(s: VehicleState): List<Capability> = listOf(
    "기어" to s.gear, "주행 제한" to s.uxRestricted, "누적거리" to s.odometerKm, "차속" to s.speedKmh,
    "브레이크" to s.brakePressed, "조향각" to s.steeringDeg, "방향지시등" to s.turnSignal,
    "운전석 문" to s.driverDoorOpen, "문 잠금" to s.doorsLocked, "운전석 창문" to s.driverWindow,
    "전조등" to s.lowBeam, "상향등" to s.highBeam, "안전벨트" to s.driverBeltBuckled,
    "배터리·연료" to s.energyPct, "경고등" to s.warnings,
).map { (name, signal: Signal<*>) -> Capability(name, statusOf(signal)) }

private fun statusOf(s: Signal<*>) = when (s) {
    is Signal.Ok<*> -> "읽힘"
    Signal.Unsupported -> "지원 안 함"
    Signal.NoPermission -> "권한 없음"
    Signal.Unknown -> "미측정"
}

private const val BATTERY_ERROR = "올바른 용량을 입력해 주세요"
private val DECIMAL = Regex("""\d+(\.\d+)?""")
private const val MAX_BATTERY_KWH = 200.0

/** 04(차량 정보)와 06(메이트) 두 화면이 함께 쓴다. setActive는 Settings.setActiveProfile(테스트에서는 람다). */
class OnboardingViewModel(
    private val store: RecordStore,
    val state: StateFlow<VehicleState>,
    private val setActive: suspend (String) -> Unit,
) : ViewModel() {
    val capabilities: StateFlow<List<Capability>> =
        state.map(::capabilitiesOf).stateIn(viewModelScope, SharingStarted.Eagerly, capabilitiesOf(state.value))

    var vehicleName by mutableStateOf("내 차")
    var fuelType by mutableStateOf(FuelType.EV)
    var batteryText by mutableStateOf("")
    /** 누적거리를 읽지 못할 때 직접 입력하는 값. 확인용이라 저장하지 않는다(ProfileEntity에 열이 없음). */
    var odometerText by mutableStateOf("")
    var starter by mutableStateOf<String?>(null) // STARTER_1..3
    var nickname by mutableStateOf("")

    /** EV일 때만 의미가 있다. 비었거나 잘못된 값은 null. */
    val batteryKwh: Double? get() =
        if (fuelType == FuelType.EV && batteryError() == null) batteryText.trim().toDoubleOrNull() else null

    fun batteryError(): String? {
        val t = batteryText.trim()
        if (fuelType != FuelType.EV || t.isEmpty()) return null
        // 아주 긴 숫자는 Infinity가 되지만 상한 비교에서 걸러진다.
        return if (DECIMAL.matches(t) && t.toDouble().let { it > 0.0 && it <= MAX_BATTERY_KWH }) null else BATTERY_ERROR
    }

    fun nicknameError(): String? = if (nickname.trim().length in 2..8) null else "2~8자로 지어 주세요"

    val canNext: Boolean get() = vehicleName.trim().length in 1..20 && batteryError() == null
    val canFinish: Boolean get() = starter != null && nicknameError() == null

    private val profileId = UUID.randomUUID().toString() // 실패 뒤 다시 눌러도 같은 id로 덮어쓴다
    private var finished = false
    private val finishLock = Mutex()

    /** 프로필 저장 후 활성화하고 profileId를 돌려준다. 동시에 여러 번 불려도 프로필은 하나만 만든다. */
    suspend fun finish(): String = finishLock.withLock {
        if (finished) return@withLock profileId
        val chosen = checkNotNull(starter) { "스타터를 고르지 않았다" }
        check(canNext && nicknameError() == null) { "입력이 올바르지 않다" }
        store.upsertProfile(
            ProfileEntity(
                profileId = profileId, accountId = null, vehicleName = vehicleName.trim(), fuelType = fuelType.name,
                starterType = chosen, petNickname = nickname.trim(), onboardingCompleted = true,
                batteryKwh = batteryKwh, createdAt = System.currentTimeMillis(),
            ),
        )
        setActive(profileId)
        finished = true
        profileId
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OnboardingViewModel(container.store, VehicleModule.repository.state, container.settings::setActiveProfile) as T
        }
    }
}
