package com.drivepet.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.drivepet.app.ui.AppNav
import com.drivepet.app.ui.theme.DrivePetTheme
import com.drivepet.vehicle.VehicleModule

class MainActivity : ComponentActivity() {

    private val coordinator get() = (application as DrivePetApp).container.coordinator
    private var carSpeedGrantedAtLaunch = false

    // 허용 여부와 관계없이 앱은 이미 시작돼 있다(DrivePetApp). 못 받은 값은 vehicle 모듈이 '권한 없음'·'미측정'으로 표시한다.
    // 실차 모드에서 CAR_SPEED를 이번에 처음 받았으면 권한을 반영하도록 한 번 다시 연결한다.
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (!carSpeedGrantedAtLaunch && granted[CAR_SPEED] == true && coordinator.mode == VehicleModule.Mode.REAL) {
            coordinator.start(VehicleModule.Mode.REAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        carSpeedGrantedAtLaunch = checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED
        setContent {
            DrivePetTheme { AppNav((application as DrivePetApp).container, onFinish = { finishAndRemoveTask() }) }
        }
        if (savedInstanceState == null) {
            permissions.launch(arrayOf(CAR_SPEED, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    override fun onStart() {
        super.onStart()
        coordinator.appInForeground.value = true
        coordinator.ensureStarted()
    }

    override fun onStop() {
        coordinator.appInForeground.value = false
        super.onStop()
    }

    private companion object {
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
