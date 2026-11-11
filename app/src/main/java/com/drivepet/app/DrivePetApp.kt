package com.drivepet.app

import android.app.Application
import android.util.Log
import com.drivepet.vehicle.VehicleModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    }
}
