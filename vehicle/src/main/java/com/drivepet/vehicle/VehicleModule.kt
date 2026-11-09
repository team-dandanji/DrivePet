package com.drivepet.vehicle

import android.content.Context
import android.content.Intent
import com.drivepet.vehicle.source.FakeVehicleSource
import com.drivepet.vehicle.source.RealVehicleSource
import com.drivepet.vehicle.source.ReplayVehicleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 어디서든 쓰는 진입점.
 *
 * ```kotlin
 * VehicleModule.start(context, VehicleModule.Mode.FAKE)   // 시작(포그라운드 서비스 포함)
 * VehicleModule.repository.state                            // 차 상태
 * VehicleModule.repository.events                           // 이벤트
 * VehicleModule.fake.setGear(Gear.D)                        // 가짜 모드에서 값 바꾸기
 * ```
 */
object VehicleModule {

    enum class Mode { REAL, FAKE, REPLAY }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: VehicleRepository by lazy { VehicleRepository(scope) }

    /** FAKE 모드에서 버튼으로 값을 바꿀 때 쓴다. */
    val fake: FakeVehicleSource by lazy { FakeVehicleSource() }

    /** 경고등(PRIVATE_CAR_SAFETY) 사용. 개발 단계 전용 — 배포 승인 전에는 false로. */
    @Volatile var usePrivateSafety: Boolean = true

    /** 기본 시연 시나리오 경로(assets). */
    @Volatile var scenarioAsset: String = "scenarios/commute_12km.json"

    @Volatile var mode: Mode = Mode.FAKE
        private set

    /** 포그라운드 서비스를 켜고 [mode]로 데이터를 받기 시작한다. */
    fun start(context: Context, mode: Mode) {
        this.mode = mode
        context.startForegroundService(Intent(context, DriveService::class.java))
        attach(context)
    }

    fun stop(context: Context) {
        repository.stop()
        context.stopService(Intent(context, DriveService::class.java))
    }

    /** 현재 [mode]에 맞는 출처를 저장소에 연결한다. 서비스와 [start]에서 부른다. */
    internal fun attach(context: Context) {
        val source = when (mode) {
            Mode.REAL -> RealVehicleSource(context, usePrivateSafety)
            Mode.FAKE -> fake
            Mode.REPLAY -> ReplayVehicleSource(ReplayVehicleSource.fromAsset(context, scenarioAsset), scope)
        }
        repository.use(source)
    }
}
