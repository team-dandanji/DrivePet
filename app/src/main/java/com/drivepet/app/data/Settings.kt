package com.drivepet.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivepet.app.BuildConfig
import com.drivepet.vehicle.VehicleModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** 앞뒤 공백 제거, http(s)://로 시작해야 하고 끝에 "/"를 보장한다. 아니면 null. */
fun normalizeServerUrl(input: String): String? {
    val t = input.trim()
    if (!t.startsWith("http://") && !t.startsWith("https://")) return null
    return if (t.endsWith("/")) t else "$t/"
}

/** 내비로 안내를 시작한 정비(itemId = CareItem 이름). 방문 후 기록 팝업이 쓴다. armed = 안내를 시작한 뒤 실제로 주행했는지(그 전에는 묻지 않는다). */
data class PendingCare(val itemId: String, val placeName: String, val armed: Boolean = false)

class Settings(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val modeKey = stringPreferencesKey("mode")
    private val profileKey = stringPreferencesKey("active_profile_id")
    private val greetKey = stringPreferencesKey("last_greet_date")
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val pendingItemKey = stringPreferencesKey("pending_care_item")
    private val pendingPlaceKey = stringPreferencesKey("pending_care_place")
    private val pendingArmedKey = booleanPreferencesKey("pending_care_armed")

    // 저장된 값이 없으면 모든 빌드에서 실차. 리플레이는 22·개발자 메뉴에서만 시작한다.
    private val defaultMode = VehicleModule.Mode.REAL

    val mode: Flow<VehicleModule.Mode> = store.data.map { p ->
        p[modeKey]?.let { runCatching { VehicleModule.Mode.valueOf(it) }.getOrNull() } ?: defaultMode
    }

    suspend fun setMode(m: VehicleModule.Mode) { store.edit { it[modeKey] = m.name } }

    val activeProfileId: Flow<String?> = store.data.map { it[profileKey] }

    suspend fun setActiveProfile(id: String) { store.edit { it[profileKey] = id } }

    /** 마지막으로 전체 인사를 한 서울 날짜(yyyy-MM-dd). */
    val lastGreetDate: Flow<String?> = store.data.map { it[greetKey] }

    suspend fun setLastGreetDate(date: String) { store.edit { it[greetKey] = date } }

    val serverUrl: Flow<String> = store.data.map { it[serverUrlKey] ?: BuildConfig.DEFAULT_SERVER_URL }

    suspend fun setServerUrl(url: String) { store.edit { it[serverUrlKey] = url } }

    val pendingCare: Flow<PendingCare?> = store.data.map { p ->
        val item = p[pendingItemKey] ?: return@map null
        PendingCare(item, p[pendingPlaceKey].orEmpty(), p[pendingArmedKey] == true)
    }

    /** null이면 지운다. */
    suspend fun setPendingCare(c: PendingCare?) {
        store.edit {
            if (c == null) { it.remove(pendingItemKey); it.remove(pendingPlaceKey); it.remove(pendingArmedKey) }
            else { it[pendingItemKey] = c.itemId; it[pendingPlaceKey] = c.placeName; it[pendingArmedKey] = c.armed }
        }
    }

    /** 대기 중인 정비가 있을 때만 armed로 바꾼다(없으면 아무것도 쓰지 않는다). */
    suspend fun armPendingCare() {
        store.edit { if (it[pendingItemKey] != null) it[pendingArmedKey] = true }
    }

    /** 개발자 메뉴 "로컬 데이터 초기화": 서버 주소만 남기고 설정을 지운다(지우면 시연 빌드가 기본 http 주소로 돌아가 서버 기능이 끊긴다). */
    suspend fun clear() {
        store.edit { p ->
            val url = p[serverUrlKey]
            p.clear()
            url?.let { p[serverUrlKey] = it }
        }
    }
}
