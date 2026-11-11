package com.drivepet.app.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivepet.app.AppContainer
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.db.ProfileEntity
import com.drivepet.app.data.growthTotalMilli
import com.drivepet.app.domain.Growth
import com.drivepet.app.ui.onboarding.FUELS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileCard(val profileId: String, val petNickname: String, val vehicleName: String, val fuelLabel: String, val level: Int)

/** 01 · 저장된 프로필 불러오기. kakaoNickname이 null이면 카카오 계정 미연결. */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModel(
    store: RecordStore,
    private val activeId: Flow<String?>,
    private val setActive: suspend (String) -> Unit,
    kakaoNickname: Flow<String?>,
) : ViewModel() {
    val kakao: StateFlow<String?> = kakaoNickname.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // createdAt 순 카드. 레벨은 프로필마다 원장 합계로 구한다.
    private val cards: Flow<List<Pair<ProfileEntity, ProfileCard>>> = store.profiles().flatMapLatest { list ->
        val sorted = list.sortedBy { it.createdAt }
        if (sorted.isEmpty()) flowOf(emptyList())
        else combine(sorted.map { p ->
            combine(store.records(p.profileId), store.seed(p.profileId)) { records, seed ->
                p to ProfileCard(p.profileId, p.petNickname, p.vehicleName, fuelLabel(p.fuelType), Growth.level(growthTotalMilli(records, seed)))
            }
        }) { it.toList() }
    }

    // 활성 프로필, 없으면 createdAt이 가장 늦은 프로필.
    private val currentId: Flow<String?> = combine(cards, activeId) { list, id ->
        (list.firstOrNull { it.first.profileId == id } ?: list.lastOrNull())?.first?.profileId
    }

    val current: StateFlow<ProfileCard?> = combine(cards, currentId) { list, id -> list.firstOrNull { it.first.profileId == id }?.second }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val others: StateFlow<List<ProfileCard>> = combine(cards, currentId) { list, id -> list.filter { it.first.profileId != id }.map { it.second } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** current를 활성으로 저장한 뒤 [then]을 부른다(저장 전에 화면을 옮기면 쓰기가 취소되거나 이전 프로필이 먼저 보인다). */
    fun enter(then: () -> Unit) {
        viewModelScope.launch {
            val id = current.value?.profileId ?: return@launch
            if (activeId.first() != id) setActive(id)
            then()
        }
    }

    /** [profileId]를 활성으로 저장한 뒤 [then]을 부른다. */
    fun choose(profileId: String, then: () -> Unit) { viewModelScope.launch { setActive(profileId); then() } }

    private fun fuelLabel(fuelType: String) = FUELS.firstOrNull { it.first.name == fuelType }?.second ?: fuelType

    companion object {
        fun factory(c: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WelcomeViewModel(
                c.store, c.settings.activeProfileId, c.settings::setActiveProfile, c.auth.state.map { it.nickname },
            ) as T
        }
    }
}
