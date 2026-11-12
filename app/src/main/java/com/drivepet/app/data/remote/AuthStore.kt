package com.drivepet.app.data.remote

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AuthState(
    val deviceId: String?,
    val deviceSecret: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val accountId: String?,
    val nickname: String?,
)

interface TokenStore {
    suspend fun get(): AuthState
    suspend fun update(f: (AuthState) -> AuthState)
    val state: Flow<AuthState>
}

private val Context.authDataStore by preferencesDataStore(name = "auth")

// shortcut: 비밀값을 암호화 없이 DataStore에 둔다, 실차 배포 전 Keystore로 옮긴다.
class AuthStore(context: Context) : TokenStore {
    private val store = context.applicationContext.authDataStore
    private val keys = listOf("device_id", "device_secret", "access_token", "refresh_token", "account_id", "nickname")
        .map { stringPreferencesKey(it) }

    private fun Preferences.toState() = AuthState(
        this[keys[0]], this[keys[1]], this[keys[2]], this[keys[3]], this[keys[4]], this[keys[5]],
    )

    override val state: Flow<AuthState> = store.data.map { it.toState() }

    override suspend fun get(): AuthState = state.first()

    override suspend fun update(f: (AuthState) -> AuthState) {
        store.edit { p ->
            val n = f(p.toState())
            listOf(n.deviceId, n.deviceSecret, n.accessToken, n.refreshToken, n.accountId, n.nickname)
                .forEachIndexed { i, v -> if (v == null) p.remove(keys[i]) else p[keys[i]] = v }
        }
    }
}
