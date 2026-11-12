package com.drivepet.app.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivepet.app.AppContainer
import com.drivepet.app.data.CareRow
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.ReplacementPayload
import com.drivepet.app.data.careItemEntity
import com.drivepet.app.data.careRows
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.db.VehicleCareEntity
import com.drivepet.app.data.defaultCareItems
import com.drivepet.app.data.growthTotalMilli
import com.drivepet.app.data.odometerMm
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.inspectionDaysLeft
import com.drivepet.app.domain.seoulDate
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class GarageUi(
    val fuel: FuelType,
    val odometerKm: Int?,
    val rows: List<CareRow>,
    val inspectionDate: LocalDate?,
    val inspectionDays: Int?,
    val tpmsOn: Boolean,
)

/**
 * D6 정비 기록 완료 화면에 보일 것. paid = 이 기록이 그 항목·그날 첫 교체라 +50을 받았는지.
 * nextDueKm = 다음 점검까지 남은 km, baseOdometerKm = 기준 누적거리, sinceKm = 관리 후 누적거리, before/afterMilli = 이 기록 전·후 총 EXP.
 */
data class CareDoneUi(
    val title: String, val date: LocalDate, val odometerKm: Int?, val lifePct: Int?, val paid: Boolean, val tpmsOn: Boolean,
    val nextDueKm: Int?, val baseOdometerKm: Int?, val sinceKm: Int?, val beforeMilli: Long, val afterMilli: Long,
)

/** 내 차 정비소, 관리 기록, 관리 이력 수정이 함께 쓴다. 정비 설정은 기기 안에만 두고 동기화하지 않는다. */
@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModel(
    private val store: RecordStore,
    private val state: StateFlow<VehicleState>,
    activeProfileId: Flow<String?>,
    private val clock: () -> Instant,
) : ViewModel() {
    private val profile: StateFlow<ProfileEntity?> = combine(activeProfileId, store.profiles()) { id, list -> list.firstOrNull { it.profileId == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // 이번 업데이트 전에 만든 프로필은 항목이 없다: 미등록 기본 항목을 한 벌 만든다(이미 있으면 그대로).
        viewModelScope.launch {
            profile.filterNotNull().collect { p -> store.ensureMaintenanceItems(p.profileId) { defaultCareItems(p.profileId, fuelOf(p)) } }
        }
    }

    val ui: StateFlow<GarageUi?> = profile.flatMapLatest { p ->
        if (p == null) return@flatMapLatest flowOf(null)
        combine(store.maintenanceItems(p.profileId), store.records(p.profileId), store.vehicleCare(p.profileId), state) { items, records, care, s ->
            val due = care?.inspectionDueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            GarageUi(
                fuel = fuelOf(p),
                odometerKm = s.odometerKm.valueOrNull()?.toInt(),
                rows = careRows(items, records, s.odometerMm()),
                inspectionDate = due,
                inspectionDays = inspectionDaysLeft(due, seoulDate(clock())),
                tpmsOn = s.warnings.valueOrNull().orEmpty().any { "TPMS" in it },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 교체 기록을 남기고 recordId를 돌려준다. 누적거리를 못 읽으면 odometerMm = null(남은 주기는 미산출로 남는다).
     * [id]가 이미 있으면 새로 넣지 않고 그 id를 돌려준다(정비 기록 팝업의 재시도가 기록을 두 번 만들지 않게).
     */
    suspend fun replace(itemId: String, id: String = UUID.randomUUID().toString()): String? {
        val p = profile.value ?: return null
        val now = clock()
        val date = seoulDate(now).toString()
        store.insertIfAbsent(
            RecordEntity(
                recordId = id, profileId = p.profileId, accountId = p.accountId, kind = "REPLACEMENT", occurredAt = now.toEpochMilli(),
                payloadJson = Json.encodeToString(ReplacementPayload(itemId, date, state.value.odometerMm())),
                localDate = date, itemId = itemId,
            ),
        )
        return id
    }

    fun done(recordId: String): Flow<CareDoneUi?> = combine(
        ui, profile.filterNotNull().flatMapLatest { p -> combine(store.records(p.profileId), store.seed(p.profileId)) { r, seed -> r to seed } },
    ) { u, (records, seed) ->
        val r = records.firstOrNull { it.recordId == recordId } ?: return@combine null
        val row = u?.rows?.firstOrNull { it.item.itemId == r.itemId }
        // 원장(totalExpMilli)은 (itemId, localDate)마다 한 번만 준다: 그날 그 항목의 가장 이른 기록이 받은 것으로 본다.
        val first = records.filter { it.kind == "REPLACEMENT" && it.itemId == r.itemId && it.localDate == r.localDate }
            .minWithOrNull(compareBy({ it.occurredAt }, { it.recordId }))
        val mm = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<ReplacementPayload>(r.payloadJson).odometerMm }.getOrNull()
        CareDoneUi(
            title = row?.care?.title ?: "소모품",
            date = LocalDate.parse(r.localDate),
            odometerKm = mm?.let { (it / 1_000_000).toInt() },
            lifePct = if (mm == null) null else row?.life?.lifePct,
            paid = first?.recordId == recordId,
            tpmsOn = u?.tpmsOn == true,
            // 누적거리를 못 읽고 남긴 기록이면 남은 거리·기준은 미산출로 둔다(lifePct와 같은 규칙).
            nextDueKm = if (mm == null) null else row?.life?.remainingKm,
            baseOdometerKm = if (mm == null) null else row?.life?.baseOdometerMm?.let { (it / 1_000_000).toInt() },
            sinceKm = if (mm == null) null else row?.life?.sinceKm,
            beforeMilli = growthTotalMilli(records.filter { it.recordId != recordId }, seed),
            afterMilli = growthTotalMilli(records, seed),
        )
    }

    /** 관리 이력 수정 저장. itemId는 프로필+항목에서 정해지므로 같은 행을 덮어쓴다. */
    fun saveEdit(baselines: Map<CareItem, CareBaseline>, inspection: LocalDate?) {
        viewModelScope.launch {
            val p = profile.value ?: return@launch
            store.saveMaintenanceItems(baselines.map { (item, b) -> careItemEntity(p.profileId, item, b) })
            store.setVehicleCare(VehicleCareEntity(p.profileId, inspection?.toString()))
        }
    }

    private fun fuelOf(p: ProfileEntity) = runCatching { FuelType.valueOf(p.fuelType) }.getOrDefault(FuelType.EV)

    companion object {
        fun factory(c: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GarageViewModel(c.store, VehicleModule.repository.state, c.settings.activeProfileId, Instant::now) as T
        }
    }
}
