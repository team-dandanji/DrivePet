package com.drivepet.app

import android.app.Application
import android.util.Log
import com.drivepet.vehicle.VehicleModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DrivePetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            // 설정을 못 읽어도 앱이 죽지 않게 REAL로 시작한다.
            val mode = runCatching { container.settings.mode.first() }
                .onFailure { Log.e("DrivePetApp", "settings.mode read failed, starting REAL", it) }
                .getOrDefault(VehicleModule.Mode.REAL)
            container.coordinator.start(mode)
        }
        container.kickSync()
        // 보낼 기록이 쌓이면 2초 모아서 동기화를 한 번 부른다. 미연결(accountId null)이면 0건.
        container.scope.launch {
            container.auth.state.map { it.accountId }.distinctUntilChanged()
                .flatMapLatest { a -> a?.let(container.syncStore::pendingCount) ?: flowOf(0) }
                .collectLatest { n ->
                    if (n > 0) {
                        delay(2_000)
                        container.kickSync()
                    }
                }
        }
    }
}
