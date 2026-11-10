package com.drivepet.app.ui.lounge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivepet.app.AppContainer
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.recentTripSafety
import com.drivepet.app.data.growthTotalMilli
import com.drivepet.app.data.toLedger
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.Gauge
import com.drivepet.app.domain.Growth
import com.drivepet.app.domain.Mood
import com.drivepet.app.domain.MoodInput
import com.drivepet.app.domain.health
import com.drivepet.app.domain.mood
import com.drivepet.app.domain.satiety
import com.drivepet.app.domain.seoulDate
import com.drivepet.app.domain.toWarningInput
import com.drivepet.app.domain.totalExpMilli
import com.drivepet.app.drive.Speaker
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CareAction(val label: String) { HOSPITAL("병원 가기"), FEED("밥 주러 가기") }

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
    val showCheckInButton: Boolean,
    val warnings: List<String>,
    val careAction: CareAction?,
)

/** 포만·건강 모두 이 값 이하면 "부족". */
const val LOW_PCT = 20

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
    beltReadable: Flow<Boolean>,
    private val speaker: Speaker,
    private val checkIn: suspend () -> Boolean,
    private val lastGreetDate: Flow<String?>,
    private val setLastGreetDate: suspend (String) -> Unit,
    private val clock: () -> Instant,
) : ViewModel() {
    /** 활성 프로필이 없으면 null. */
    val ui: StateFlow<LoungeUi?> = combine(activeProfileId, store.profiles()) { id, list -> list.firstOrNull { it.profileId == id } }
        .flatMapLatest { p ->
            if (p == null) return@flatMapLatest flowOf(null)
            combine(
                store.records(p.profileId), store.warningsSeen(p.profileId), store.seed(p.profileId), state, beltReadable,
            ) { records, seen, seed, s, belt ->
                val today = seoulDate(clock())
                val total = growthTotalMilli(records, seed)
                val todayExp = totalExpMilli(records.filter { seoulDate(Instant.ofEpochMilli(it.occurredAt)) == today }.map { it.toLedger() })
                val attended = records.any { it.kind == "ATTENDANCE" && it.localDate == today.toString() }
                val level = Growth.level(total)
                val names = seen.map { it.name }
                val warningDays = seen.maxOfOrNull { ChronoUnit.DAYS.between(LocalDate.parse(it.firstSeenDate), today).toInt() }
                val satietyPct = satiety(s.energyPct.valueOrNull())
                val petMood = mood(MoodInput(warningDays, satietyPct, null, null, attended || level > Growth.level(total - todayExp)))
                LoungeUi(
                    nickname = p.petNickname,
                    vehicleLine = "${p.vehicleName} | ${fuelLabel(p.fuelType)}",
                    starter = p.starterType,
                    stage = level,
                    mood = petMood,
                    gauge = Growth.gauge(total),
                    healthPct = health(s.warnings.toWarningInput(), emptyList(), records.recentTripSafety()).score,
                    satietyPct = satietyPct,
                    parked = s.isParked,
                    attendedToday = attended,
                    showCheckInButton = !belt && !attended && s.isParked,
                    warnings = names,
                    careAction = when {
                        names.isNotEmpty() -> CareAction.HOSPITAL
                        satietyPct != null && satietyPct <= LOW_PCT -> CareAction.FEED
                        else -> null
                    },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 라운지에 들어올 때마다 부른다. 하루 첫 진입은 자기소개를 붙인다. */
    fun greetIfFirstToday() {
        viewModelScope.launch {
            val u = ui.filterNotNull().first()
            val date = seoulDate(clock()).toString()
            val first = lastGreetDate.first() != date
            speaker.say((if (first) "안녕, 나 ${u.nickname}! " else "") + moodLine(u.mood))
            if (first) setLastGreetDate(date)
        }
    }

    fun checkInNow() { viewModelScope.launch { checkIn() } } // 출석 말풍선은 코디네이터가 띄운다.

    fun careTapped() = speaker.say("정비소 안내는 곧 열려요")

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
                c.store, VehicleModule.repository.state, c.settings.activeProfileId, c.coordinator.beltReadable, c.speaker,
                c.coordinator::manualCheckIn, c.settings.lastGreetDate, c.settings::setLastGreetDate, Instant::now,
            ) as T
        }
    }
}
