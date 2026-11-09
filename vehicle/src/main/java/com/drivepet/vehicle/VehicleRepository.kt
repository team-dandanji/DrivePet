package com.drivepet.vehicle

import com.drivepet.vehicle.engine.EventEngine
import com.drivepet.vehicle.engine.RuleConfig
import com.drivepet.vehicle.model.DriveEvent
import com.drivepet.vehicle.model.TripSummary
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.source.VehicleSink
import com.drivepet.vehicle.source.VehicleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 팀원이 쓰는 유일한 창구.
 * - [state]: 지금 차 상태
 * - [events]: 캐릭터 반응·점수·정산용 이벤트
 * - [trip]: 진행 중인 주행(주기적으로 저장하면 앱이 죽어도 복구 가능)
 *
 * 화면 갱신은 이 Flow를 구독해서 한다. Activity의 onResume에만 묶으면
 * Pleos 화면에서 앱이 보이면서도 일시정지될 때 캐릭터가 멈춰 보인다(11/7 실측).
 */
class VehicleRepository(
    private val scope: CoroutineScope,
    config: RuleConfig = RuleConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val engine = EventEngine(config)
    private val lock = Any()

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DriveEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DriveEvent> = _events.asSharedFlow()

    private val _trip = MutableStateFlow<TripSummary?>(null)
    val trip: StateFlow<TripSummary?> = _trip.asStateFlow()

    private val _sourceName = MutableStateFlow("없음")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    private var source: VehicleSource? = null
    private var ticker: Job? = null

    private val sink = VehicleSink { patch ->
        synchronized(lock) {
            val prev = _state.value
            val next = patch(prev)
            if (next == prev) return@synchronized
            _state.value = next
            publish(engine.onState(prev, next, clock()))
            _trip.value = engine.currentTrip()
        }
    }

    /** 데이터 출처를 바꾼다. 이전 출처는 멈추고 상태·진행 중 주행을 초기화한다. */
    fun use(newSource: VehicleSource) {
        stop()
        synchronized(lock) {
            engine.reset()
            _state.value = VehicleState()
            _trip.value = null
        }
        source = newSource
        _sourceName.value = newSource.name
        newSource.start(sink)
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                synchronized(lock) {
                    publish(engine.tick(_state.value, clock()))
                    _trip.value = engine.currentTrip()
                }
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        source?.stop()
        source = null
        _sourceName.value = "없음"
    }

    private fun publish(list: List<DriveEvent>) {
        list.forEach { _events.tryEmit(it) }
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
