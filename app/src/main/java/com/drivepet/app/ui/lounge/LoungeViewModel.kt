package com.drivepet.app.ui.lounge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivepet.app.AppContainer
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.careRows
import com.drivepet.app.data.lifePcts
import com.drivepet.app.data.odometerMm
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.recentTripSafety
import com.drivepet.app.data.growthTotalMilli
import com.drivepet.app.data.remote.WeatherDto
import com.drivepet.app.data.toLedger
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.Gauge
import com.drivepet.app.domain.Growth
import com.drivepet.app.domain.MissionStatus
import com.drivepet.app.domain.Mood
import com.drivepet.app.domain.MoodInput
import com.drivepet.app.domain.health
import com.drivepet.app.domain.inspectionDaysLeft
import com.drivepet.app.domain.mood
import com.drivepet.app.domain.satiety
import com.drivepet.app.domain.seoulDate
import com.drivepet.app.domain.toWarningInput
import com.drivepet.app.domain.totalExpMilli
import com.drivepet.app.drive.Speaker
import com.drivepet.app.drive.claimedMissionIds
import com.drivepet.app.drive.todayMissions
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class LoungeUi(
    val nickname: String,
    val vehicleLine: String,
    val starter: String,
    val stage: Int,
    val mood: Mood,
    val gauge: Gauge,
    val healthPct: Int?,
    val satietyPct: Int?,
    val parked: Boolean,
    val attendedToday: Boolean,
    val warnings: List<String>,
    val missions: List<MissionStatus> = emptyList(),
)

data class WeatherUi(val label: String, val temp: String, val air: String?, val warning: String?, val stale: Boolean, val greetLine: String)

fun weatherUi(w: WeatherDto): WeatherUi {
    val label = when (w.precipitationType) {
        "RAIN" -> "비"
        "SNOW" -> "눈"
        "RAIN_SNOW" -> "비나 눈"
        "SHOWER" -> "소나기"
        else -> when (w.sky) {
            "CLEAR" -> "맑음"
            "PARTLY_CLOUDY" -> "구름 조금"
            else -> "흐림"
        }
    }
    val temp = "${w.tempC.roundToInt()}°C"
    val air = w.airQuality?.let {
        when (it.grade) {
            "GOOD" -> "미세먼지 좋음"
            "NORMAL" -> "미세먼지 보통"
            "BAD" -> "미세먼지 나쁨"
            "VERY_BAD" -> "미세먼지 매우 나쁨"
            else -> null
        }
    }
    val warning = w.warnings.firstOrNull()
    val greet = "오늘은 $label, ${temp}야." + (warning?.let { " $it 발효 중이니 조심해!" } ?: "") + if (w.stale) " (이전 정보)" else ""
    return WeatherUi(label, temp, air, warning, w.stale, greet)
}

/** 날씨를 기다리는 최대 시간(하루 첫 인사). */
private const val WEATHER_WAIT_MS = 3_000L

/** 하루 첫 인사의 미션 줄. 미션이 없으면 null. */
fun missionLine(missions: List<MissionStatus>): String? {
    if (missions.isEmpty()) return null
    return missions.firstOrNull { !it.done }?.let { "오늘 미션: ${it.kind.title}" } ?: "오늘 미션 다 했어!"
}

fun moodLine(m: Mood): String = when (m) {
    Mood.SICK_1, Mood.SICK_2, Mood.SICK_3 -> "몸이 좀 안 좋아…"
    Mood.HUNGRY -> "배고파…"
    Mood.WORRIED -> "정비할 때가 됐어"
    Mood.HAPPY -> "오늘 기분 최고!"
    Mood.NORMAL -> "오늘도 같이 달려요"
}

/** Android 전용 의존(Settings·코디네이터)은 람다로 받아 단위 테스트에서 바꿔 끼운다. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoungeViewModel(
    private val store: RecordStore,
    private val state: StateFlow<VehicleState>,
    activeProfileId: Flow<String?>,
    private val speaker: Speaker,
    private val checkIn: suspend () -> Boolean,
    private val lastGreetDate: Flow<String?>,
    private val setLastGreetDate: suspend (String) -> Unit,
    private val clock: () -> Instant,
    loadWeather: suspend () -> WeatherDto?,
) : ViewModel() {
    // 만들어질 때 한 번만 부른다. 어떤 실패든 null이면 날씨 칸도 인사 줄도 없다.
    private val weatherLoad = viewModelScope.async {
        try { loadWeather()?.let(::weatherUi) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }
    private val _weather = MutableStateFlow<WeatherUi?>(null)
    val weather: StateFlow<WeatherUi?> = _weather
    init { viewModelScope.launch { _weather.value = weatherLoad.await() } }

    private val profile: StateFlow<ProfileEntity?> = combine(activeProfileId, store.profiles()) { id, list -> list.firstOrNull { it.profileId == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 활성 프로필이 없으면 null. */
    val ui: StateFlow<LoungeUi?> = profile
        .flatMapLatest { p ->
            if (p == null) return@flatMapLatest flowOf(null)
            val itemsAndCare = combine(store.maintenanceItems(p.profileId), store.vehicleCare(p.profileId)) { items, care -> items to care }
            combine(
                store.records(p.profileId), store.warningsSeen(p.profileId), store.seed(p.profileId), state, itemsAndCare,
            ) { records, seen, seed, s, (items, care) ->
                val today = seoulDate(clock())
                val total = growthTotalMilli(records, seed)
                val todayExp = totalExpMilli(records.filter { seoulDate(Instant.ofEpochMilli(it.occurredAt)) == today }.map { it.toLedger() })
                val attended = records.any { it.kind == "ATTENDANCE" && it.localDate == today.toString() }
                val claimedIds = claimedMissionIds(records, today)
                val missions = todayMissions(p.profileId, records, today)
                val level = Growth.level(total)
                val names = seen.map { it.name }
                val warningDays = seen.maxOfOrNull { ChronoUnit.DAYS.between(LocalDate.parse(it.firstSeenDate), today).toInt() }
                val satietyPct = satiety(s.energyPct.valueOrNull())
                val lifePcts = careRows(items, records, s.odometerMm()).lifePcts()
                val due = care?.inspectionDueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val petMood = mood(
                    MoodInput(
                        warningDays, satietyPct, lifePcts.minOrNull(), inspectionDaysLeft(due, today),
                        attended || claimedIds.isNotEmpty() || level > Growth.level(total - todayExp),
                    ),
                )
                LoungeUi(
                    nickname = p.petNickname,
                    vehicleLine = "${p.vehicleName} | ${fuelLabel(p.fuelType)}",
                    starter = p.starterType,
                    stage = level,
                    mood = petMood,
                    gauge = Growth.gauge(total),
                    healthPct = health(s.warnings.toWarningInput(), lifePcts, records.recentTripSafety()).score,
                    satietyPct = satietyPct,
                    parked = s.isParked,
                    attendedToday = attended,
                    warnings = names,
                    missions = missions,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var watchingPark = false

    /**
     * 라운지에 들어올 때마다 부른다. 자동 출석 감시는 한 번만 띄우고, 하루 첫 진입은 자기소개를 붙인다.
     * 기어 신호가 늦게 오는 콜드스타트도 있어 "정차 중"이 되는 순간마다 출석을 시도한다(checkIn은 멱등).
     */
    fun onEnter() {
        if (!watchingPark) {
            watchingPark = true
            viewModelScope.launch {
                combine(state.map { it.isParked }, profile.map { it?.profileId }) { parked, id -> parked && id != null }
                    .distinctUntilChanged().filter { it }
                    .collect { try { checkIn() } catch (e: CancellationException) { throw e } catch (e: Exception) { /* 출석 실패가 인사·화면을 막지 않게 */ } }
            }
        }
        viewModelScope.launch {
            val u = ui.filterNotNull().first()
            val date = seoulDate(clock()).toString()
            val first = lastGreetDate.first() != date
            val base = (if (first) "안녕, 나 ${u.nickname}! " else "") + moodLine(u.mood)
            val greetLine = if (first) withTimeoutOrNull(WEATHER_WAIT_MS) { weatherLoad.await() }?.greetLine else null
            val missionLine = if (first) missionLine(u.missions) else null
            speaker.say(listOfNotNull(base, greetLine, missionLine).joinToString(" "))
            if (first) setLastGreetDate(date)
        }
    }

    private fun fuelLabel(fuelType: String) = when (runCatching { FuelType.valueOf(fuelType) }.getOrNull()) {
        FuelType.EV -> "전기차(EV)"
        FuelType.HEV -> "하이브리드"
        FuelType.ICE -> "내연기관"
        null -> fuelType
    }

    companion object {
        fun factory(c: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LoungeViewModel(
                c.store, VehicleModule.repository.state, c.settings.activeProfileId, c.speaker,
                c.coordinator::checkInOnLounge, c.settings.lastGreetDate, c.settings::setLastGreetDate, Instant::now, c.weather::load,
            ) as T
        }
    }
}
