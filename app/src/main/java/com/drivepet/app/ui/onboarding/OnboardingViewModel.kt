package com.drivepet.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivepet.app.AppContainer
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.careItemEntity
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.ServicedPeriod
import com.drivepet.app.domain.careItemsFor
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
    "전조등" to s.lowBeam, "상향등" to s.highBeam, "실내등" to s.cabinLight, "안전벨트" to s.driverBeltBuckled,
    "배터리·연료" to s.energyPct, "경고등" to s.warnings,
).map { (name, signal: Signal<*>) -> Capability(name, statusOf(signal)) }

private fun statusOf(s: Signal<*>) = when (s) {
    is Signal.Ok<*> -> "읽힘"
    Signal.Unsupported -> "지원 안 함"
    Signal.NoPermission -> "권한 없음"
    Signal.Unknown -> "미측정"
}

/** 약관(저장하지 않는다). SERVICE·VEHICLE_DATA가 필수. */
enum class Term { SERVICE, VEHICLE_DATA, LOCATION }

/** 소모품 질문 보기 문구(피그마 O5). ServicedPeriod.label은 정비소 탭이 쓰므로 그대로 둔다. */
fun periodLabel(p: ServicedPeriod): String = when (p) {
    ServicedPeriod.WITHIN_3_MONTHS -> "최근 3개월 내"
    ServicedPeriod.ABOUT_6_MONTHS -> "3-6개월"
    ServicedPeriod.OVER_1_YEAR -> "1년 이상"
    ServicedPeriod.UNKNOWN -> "기억 안남"
}

private const val BATTERY_ERROR = "올바른 용량을 입력해 주세요"
private val DECIMAL = Regex("""\d+(\.\d+)?""")
private const val MAX_BATTERY_KWH = 200.0

/** 차량 정보·소모품 질문·주행거리·메이트 화면이 함께 쓴다(약관은 같은 클래스의 따로 된 인스턴스). setActive는 Settings.setActiveProfile(테스트에서는 람다). */
class OnboardingViewModel(
    private val store: RecordStore,
    val state: StateFlow<VehicleState>,
    private val currentAccountId: suspend () -> String?,
    private val setActive: suspend (String) -> Unit,
) : ViewModel() {
    val capabilities: StateFlow<List<Capability>> =
        state.map(::capabilitiesOf).stateIn(viewModelScope, SharingStarted.Eagerly, capabilitiesOf(state.value))

    var fuelType by mutableStateOf(FuelType.EV)
    var batteryText by mutableStateOf("")
    var starter by mutableStateOf("STARTER_1") // STARTER_1..3, 기본은 노란 알
    var nickname by mutableStateOf("")
    var terms by mutableStateOf(setOf<Term>())
    val termsOk: Boolean get() = Term.SERVICE in terms && Term.VEHICLE_DATA in terms

    /** 한 화면에 하나씩 묻는 소모품(EV 3, ICE·HEV 4 — 엔진오일 먼저). */
    val careQuestions: List<CareItem> get() = careItemsFor(fuelType)
    /** 05·20 소모품 이력. 고르지 않은 항목은 미등록으로 저장한다. */
    var careBaselines by mutableStateOf(mapOf<CareItem, CareBaseline>())
        private set

    /** 앞서 입력한 당시 주행거리는 남긴다. */
    fun setPeriod(item: CareItem, p: ServicedPeriod) { careBaselines = careBaselines + (item to baseline(item).copy(servicedOn = null, period = p)) }

    /** 주행거리 화면. period는 그대로, null이면 거리만 지운다. */
    fun setOdometerKm(item: CareItem, km: Int?) { careBaselines = careBaselines + (item to baseline(item).copy(odometerMm = km?.let { it * 1_000_000L })) }

    private fun baseline(item: CareItem) = careBaselines[item] ?: CareBaseline(null, null, null)

    /** EV일 때만 의미가 있다. 비었거나 잘못된 값은 null. */
    val batteryKwh: Double? get() =
        if (fuelType == FuelType.EV && batteryError() == null) batteryText.trim().toDoubleOrNull() else null

    fun batteryError(): String? {
        val t = batteryText.trim()
        if (fuelType != FuelType.EV || t.isEmpty()) return null
        // 아주 긴 숫자는 Infinity가 되지만 상한 비교에서 걸러진다.
        return if (DECIMAL.matches(t) && t.toDouble().let { it > 0.0 && it <= MAX_BATTERY_KWH }) null else BATTERY_ERROR
    }

    fun nicknameError(): String? = if (nickname.trim().length in 2..8) null else "2~8자 이내로 입력해주세요"

    val canNext: Boolean get() = batteryError() == null
    val canFinish: Boolean get() = nicknameError() == null

    private val profileId = UUID.randomUUID().toString() // 실패 뒤 다시 눌러도 같은 id로 덮어쓴다
    private var finished = false
    private val finishLock = Mutex()

    /** 프로필 저장 후 활성화하고 profileId를 돌려준다. 동시에 여러 번 불려도 프로필은 하나만 만든다. */
    suspend fun finish(): String = finishLock.withLock {
        if (finished) return@withLock profileId
        check(canNext && nicknameError() == null) { "입력이 올바르지 않다" }
        store.upsertProfile(
            ProfileEntity(
                profileId = profileId, accountId = currentAccountId(), vehicleName = VEHICLE_NAME, fuelType = fuelType.name,
                starterType = starter, petNickname = nickname.trim(), onboardingCompleted = true,
                batteryKwh = batteryKwh, createdAt = System.currentTimeMillis(),
            ),
        )
        val empty = CareBaseline(null, null, null)
        store.saveMaintenanceItems(careItemsFor(fuelType).map { careItemEntity(profileId, it, careBaselines[it] ?: empty) })
        setActive(profileId)
        finished = true
        profileId
    }

    companion object {
        /** 차에서 차 이름을 읽지 못하므로(VehicleState에 없음) 늘 이 이름이다. */
        const val VEHICLE_NAME = "내 차"

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OnboardingViewModel(
                    container.store, VehicleModule.repository.state, { container.auth.get().accountId }, container.settings::setActiveProfile,
                ) as T
        }
    }
}
