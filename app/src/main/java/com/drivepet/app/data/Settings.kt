package com.drivepet.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivepet.app.BuildConfig
import com.drivepet.vehicle.VehicleModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class Settings(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val modeKey = stringPreferencesKey("mode")
    private val profileKey = stringPreferencesKey("active_profile_id")
    private val greetKey = stringPreferencesKey("last_greet_date")
    private val serverUrlKey = stringPreferencesKey("server_url")

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

    /** 개발자 메뉴 "로컬 데이터 초기화": 설정을 모두 지운다. */
    suspend fun clear() { store.edit { it.clear() } }
}
