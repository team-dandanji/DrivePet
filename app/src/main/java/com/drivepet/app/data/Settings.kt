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
import java.util.UUID

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** 앞뒤 공백 제거, http(s)://로 시작해야 하고 끝에 "/"를 보장한다. 아니면 null. */
fun normalizeServerUrl(input: String): String? {
    val t = input.trim()
    if (!t.startsWith("http://") && !t.startsWith("https://")) return null
    return if (t.endsWith("/")) t else "$t/"
}

/** 내비로 안내를 시작한 정비(itemId = CareItem 이름, profileId = 안내를 시작한 활성 프로필; 다른 프로필에는 묻지 않는다). 방문 후 기록 팝업이 쓴다. armed = 안내를 시작한 뒤 실제로 주행했는지(그 전에는 묻지 않는다).
 * recordId = 이 방문의 교체 기록 id. 대기를 만들 때 정해 두고 [네]를 다시 눌러도(취소·프로세스 종료 뒤 재시도) 같은 id로 넣어 기록이 두 번 생기지 않게 한다. */
data class PendingCare(
    val itemId: String, val placeName: String, val profileId: String, val armed: Boolean = false,
    val recordId: String = UUID.randomUUID().toString(),
)

class Settings(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val modeKey = stringPreferencesKey("mode")
    private val profileKey = stringPreferencesKey("active_profile_id")
    private val greetKey = stringPreferencesKey("last_greet_date")
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val pendingItemKey = stringPreferencesKey("pending_care_item")
    private val pendingPlaceKey = stringPreferencesKey("pending_care_place")
    private val locationConsentKey = booleanPreferencesKey("location_consent")
    private val pendingProfileKey = stringPreferencesKey("pending_care_profile")
    private val pendingArmedKey = booleanPreferencesKey("pending_care_armed")
    private val pendingRecordKey = stringPreferencesKey("pending_care_record")

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

    /** 선택 약관 「위치 정보 및 날씨, 주변장소」 동의. 저장된 값이 없으면 true(기존 사용자·약관을 거치지 않는 로그인 복원은 그대로 쓴다). */
    val locationConsent: Flow<Boolean> = store.data.map { it[locationConsentKey] ?: true }

    suspend fun setLocationConsent(on: Boolean) { store.edit { it[locationConsentKey] = on } }

    val pendingCare: Flow<PendingCare?> = store.data.map { p ->
        val item = p[pendingItemKey] ?: return@map null
        val profile = p[pendingProfileKey].orEmpty()
        // id가 없던 이전 버전의 대기는 항목·프로필로 고정 id를 만든다(읽을 때마다 바뀌면 중복 방지가 안 된다).
        PendingCare(item, p[pendingPlaceKey].orEmpty(), profile, p[pendingArmedKey] == true, p[pendingRecordKey] ?: "pending-$profile-$item")
    }

    /** null이면 지운다. */
    suspend fun setPendingCare(c: PendingCare?) {
        store.edit {
            if (c == null) { it.remove(pendingItemKey); it.remove(pendingPlaceKey); it.remove(pendingProfileKey); it.remove(pendingArmedKey); it.remove(pendingRecordKey) }
            else { it[pendingItemKey] = c.itemId; it[pendingPlaceKey] = c.placeName; it[pendingProfileKey] = c.profileId; it[pendingArmedKey] = c.armed; it[pendingRecordKey] = c.recordId }
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
