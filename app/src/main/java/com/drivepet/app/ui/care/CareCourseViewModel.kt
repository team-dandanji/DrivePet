package com.drivepet.app.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivepet.app.AppContainer
import com.drivepet.app.data.LocationSource
import com.drivepet.app.data.careRows
import com.drivepet.app.data.odometerMm
import com.drivepet.app.data.remote.PlaceDto
import com.drivepet.app.data.remote.PlacesRepo
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.careTarget
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** 정류장 하나: 정비소와 걸리는 분. */
data class Stop(val place: PlaceDto, val minutes: Int)

sealed interface CourseUi {
    data object Loading : CourseUi
    data class Ready(val stops: List<Stop>, val selected: Int, val target: CareItem) : CourseUi
    data object Empty : CourseUi
    data object Failed : CourseUi
}

/** 내비 ETA가 없을 때 쓰는 시내 평균 속도(18km/h = 300m/분). 피그마 2.1km → 7분. */
private const val METERS_PER_MIN = 300.0
private const val MAX_STOPS = 3

/** 펫 돌보기: 현재 위치 주변 정비소(가까운 순 3곳)와 고른 곳. 위치가 없으면 Failed. */
class CareCourseViewModel(
    private val places: PlacesRepo,
    private val location: suspend () -> Pair<Double, Double>?,
    private val eta: suspend (PlaceDto) -> Int?,
    private val target: CareItem,
) : ViewModel() {
    private val _ui = MutableStateFlow<CourseUi>(CourseUi.Loading)
    val ui: StateFlow<CourseUi> = _ui

    init { load() }

    fun retry() = load()

    fun select(index: Int) {
        val r = _ui.value as? CourseUi.Ready ?: return
        if (index in r.stops.indices) _ui.value = r.copy(selected = index)
    }

    fun remove(index: Int) {
        val r = _ui.value as? CourseUi.Ready ?: return
        if (index !in r.stops.indices) return
        val rest = r.stops.filterIndexed { i, _ -> i != index }
        val sel = if (index < r.selected) r.selected - 1 else r.selected.coerceAtMost(rest.lastIndex)
        _ui.value = if (rest.isEmpty()) CourseUi.Empty else r.copy(stops = rest, selected = sel)
    }

    private fun load() {
        _ui.value = CourseUi.Loading
        viewModelScope.launch {
            _ui.value = try {
                val (lat, lng) = location() ?: error("위치 없음") // Failed(주변 정비소를 찾지 못했어요 + 다시 찾기)
                // 내비 질문은 하나씩만 보낼 수 있어 차례로 묻는다.
                val stops = places.nearby(lat, lng).sortedBy { it.distanceM }.take(MAX_STOPS).map { Stop(it, minutesTo(it)) }
                if (stops.isEmpty()) CourseUi.Empty else CourseUi.Ready(stops, 0, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CourseUi.Failed
            }
        }
    }

    private suspend fun minutesTo(p: PlaceDto): Int = eta(p)?.let { ceil(it / 60.0).toInt() } ?: ceil(p.distanceM / METERS_PER_MIN).toInt()

    companion object {
        fun factory(c: AppContainer, target: CareItem) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CareCourseViewModel(
                c.places,
                { c.navi.currentLocation() ?: withContext(Dispatchers.IO) { c.location.latLngOrNull() } }, // 내비 위치가 없으면 마지막 위치, 그것도 없으면 null(서울시청으로 대신하지 않는다)
                c.navi::eta,
                target,
            ) as T
        }

        /** 활성 프로필의 경고·남은 주기로 고른 정비 항목. 프로필이 없으면 타이어. */
        suspend fun targetFor(c: AppContainer, profileId: String?): CareItem {
            val s = VehicleModule.repository.state.value
            val now = s.warnings.valueOrNull().orEmpty()
            if (profileId == null) return careTarget(now, emptyMap())
            val seen = c.store.warningsSeen(profileId).first().map { it.name }
            val rows = careRows(c.store.maintenanceItems(profileId).first(), c.store.records(profileId).first(), s.odometerMm())
            return careTarget(seen + now, rows.associate { it.care to it.life.lifePct })
        }
    }
}
