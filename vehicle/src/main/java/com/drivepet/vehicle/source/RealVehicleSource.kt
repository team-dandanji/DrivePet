package com.drivepet.vehicle.source

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.TurnSide
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.updated

/**
 * 실제 차(에뮬레이터 포함)에서 값을 받는다.
 *
 * - 변경 리스너: 브레이크, 운전석 문·잠금, 운전석 창문, 전조등, 방향지시등 (11/7 실측·다른 팀 실측으로 확인된 형태)
 * - 주기 조회: 기어·조향각 0.5초, 누적거리·안전벨트·경고등 2초
 * - AAOS: 주행 제한(CarUxRestrictions, 권한 불필요), 차속(CAR_SPEED 허용 시에만, 보조)
 *
 * 리스너 등록·해제는 이 클래스 한 곳에서만 한다. 같은 속성 리스너를 하나라도 해제하면 앱 전체 구독이
 * 끊기는 SDK 특성이 있어서, 화면에서 SDK를 직접 부르지 않는다.
 */
class RealVehicleSource(
    context: Context,
    /** 경고등(PRIVATE_CAR_SAFETY). 개발 단계에서만 true. 배포 승인 전에는 false. */
    private val usePrivateSafety: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis,
) : VehicleSource {

    override val name = "실제 차"

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var sink: VehicleSink? = null
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null
    private val unregisters = mutableListOf<() -> Unit>()

    override fun start(sink: VehicleSink) {
        this.sink = sink
        startPleos()
        startAaos()
        handler.post(fastPoll)
        handler.post(slowPoll)
    }

    override fun stop() {
        handler.removeCallbacks(fastPoll)
        handler.removeCallbacks(slowPoll)
        unregisters.forEach { runCatching(it) }
        unregisters.clear()
        runCatching { vehicle?.release() }
        vehicle = null
        runCatching { uxManager?.unregisterListener() }
        runCatching { propertyManager?.unregisterCallback(speedCallback) }
        runCatching { car?.disconnect() }
        car = null
        sink = null
    }

    private fun set(patch: (VehicleState, Long) -> VehicleState) {
        val now = clock()
        sink?.update { patch(it, now) }
    }

    /** 실패 원인을 상태로 바꾼다. 일시적인 실패는 기존 값을 유지한다. */
    private fun fail(field: String, mark: (VehicleState, Signal<Nothing>) -> VehicleState): (Exception?) -> Unit = { e ->
        val msg = e?.message.orEmpty()
        Log.w(TAG, "$field 실패: ${e?.javaClass?.simpleName}: $msg")
        val status: Signal<Nothing>? = when {
            msg.contains("NOT_SUPPORTED", ignoreCase = true) -> Signal.Unsupported
            e is SecurityException || msg.contains("permission", ignoreCase = true) -> Signal.NoPermission
            else -> null
        }
        if (status != null) set { s, _ -> mark(s, status) }
    }

    // ---------------- Pleos Vehicle SDK ----------------

    private fun startPleos() {
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle SDK 초기화 실패", e)
            return
        }
        vehicle = v
        val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
        val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)

        guard("brake") {
            val api = v.getBrake()
            val l = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                    pressed?.let { p -> set { s, t -> s.copy(brakePressed = s.brakePressed.updated(p, t)) } }
                }
                override fun onFailed(e: Exception) = fail("brake") { s, x -> s.copy(brakePressed = x) }(e)
            }
            api.registerBrakePedalStatus(l)
            unregisters += { api.unregisterBrakePedalStatus(l) }
        }

        guard("driverDoor") {
            val api = v.getDoor()
            api.getCurrentDoorPosition(driverDoor, { p -> onDoor(p) }, fail("driverDoor") { s, x -> s.copy(driverDoorOpen = x) })
            val l = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) = onDoor(pos)
                override fun onFailed(e: Exception) = fail("driverDoor") { s, x -> s.copy(driverDoorOpen = x) }(e)
            }
            api.registerDoorPosition(driverDoor, l)
            unregisters += { api.unregisterDoorPosition(driverDoor, l) }
        }

        // 문 잠금은 개별 도어 Area를 지원하지 않음(11/9 실측: "area ID 0x1 not supported").
        // 지원 Area는 [HOOD], [4도어 묶음] 두 개라서, 운전석이 포함된 묶음 Area를 골라 쓴다.
        guard("doorLock") {
            val api = v.getDoor()
            api.checkDoorLockCapabilities({ _: CapabilityConfig, areas: List<DoorArea> ->
                val area = areas.firstOrNull { a -> a.values.contains(DoorValue.ROW_1_LEFT) }
                Log.i(TAG, "doorLock 영역 목록=${areas.map { it.values.toList() }}, 사용=${area?.values?.toList()}")
                if (area == null) {
                    set { s, _ -> s.copy(doorsLocked = Signal.Unsupported) }
                } else {
                    api.getDoorLockState(area, { b -> onLock(b) }, fail("doorLock") { s, x -> s.copy(doorsLocked = x) })
                    val l = object : DoorLockStateListener {
                        override fun onDoorLockStateUpdated(state: Boolean?) = onLock(state)
                        override fun onFailed(e: Exception) = fail("doorLock") { s, x -> s.copy(doorsLocked = x) }(e)
                    }
                    api.registerDoorLockState(area, l)
                    unregisters += { api.unregisterDoorLockState(area, l) }
                }
            }, fail("doorLock") { s, x -> s.copy(doorsLocked = x) })
        }

        guard("driverWindow") {
            val api = v.getWindow()
            api.getCurrentWindowPosition(driverWindow, { p -> onWindow(p) }, fail("driverWindow") { s, x -> s.copy(driverWindow = x) })
            val l = object : WindowPositionListener {
                override fun onWindowPositionUpdated(position: Int?) = onWindow(position)
                override fun onFailed(e: Exception) = fail("driverWindow") { s, x -> s.copy(driverWindow = x) }(e)
            }
            api.registerWindowPosition(driverWindow, l)
            unregisters += { api.unregisterWindowPosition(driverWindow, l) }
        }

        guard("lowBeam") {
            val api = v.getLight()
            api.getCurrentLowBeamLightState({ st -> onLight(st, high = false) }, fail("lowBeam") { s, x -> s.copy(lowBeam = x) })
            val l = object : LightStateListener {
                override fun onLightStateUpdated(state: LightState?) = onLight(state, high = false)
                override fun onFailed(e: Exception) = fail("lowBeam") { s, x -> s.copy(lowBeam = x) }(e)
            }
            api.registerLowBeamLightState(l)
            unregisters += { api.unregisterLowBeamLightState(l) }
        }

        guard("highBeam") {
            val api = v.getLight()
            api.getCurrentHighBeamLightState({ st -> onLight(st, high = true) }, fail("highBeam") { s, x -> s.copy(highBeam = x) })
            val l = object : LightStateListener {
                override fun onLightStateUpdated(state: LightState?) = onLight(state, high = true)
                override fun onFailed(e: Exception) = fail("highBeam") { s, x -> s.copy(highBeam = x) }(e)
            }
            api.registerHighBeamLightState(l)
            unregisters += { api.unregisterHighBeamLightState(l) }
        }

        guard("turnSignal") {
            val api = v.getTurnSignal()
            api.getCurrentTurnSignalState({ st -> onTurn(st) }, fail("turnSignal") { s, x -> s.copy(turnSignal = x) })
            val l = object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = onTurn(state)
                override fun onFailed(e: Exception) = fail("turnSignal") { s, x -> s.copy(turnSignal = x) }(e)
            }
            api.registerTurnSignalState(l)
            unregisters += { api.unregisterTurnSignalState(l) }
        }
    }

    private fun onDoor(pos: Int?) { pos?.let { p -> set { s, t -> s.copy(driverDoorOpen = s.driverDoorOpen.updated(p > 0, t)) } } }
    private fun onLock(locked: Boolean?) { Log.i(TAG, "doorLock 값=$locked"); locked?.let { b -> set { s, t -> s.copy(doorsLocked = s.doorsLocked.updated(b, t)) } } }
    private fun onWindow(pos: Int?) { pos?.let { p -> set { s, t -> s.copy(driverWindow = s.driverWindow.updated(p.coerceIn(0, 10), t)) } } }

    private fun onLight(state: Any?, high: Boolean) {
        val on = state?.toString() ?: return
        val isOn = on.equals("ON", ignoreCase = true)
        set { s, t -> if (high) s.copy(highBeam = s.highBeam.updated(isOn, t)) else s.copy(lowBeam = s.lowBeam.updated(isOn, t)) }
    }

    private fun onTurn(state: Any?) {
        val side = when (state?.toString()?.uppercase()) {
            null -> return
            "LEFT" -> TurnSide.LEFT
            "RIGHT" -> TurnSide.RIGHT
            else -> TurnSide.OFF
        }
        set { s, t -> s.copy(turnSignal = s.turnSignal.updated(side, t)) }
    }

    /** 기어·조향: 0.5초 주기 조회. (Gear 리스너 클래스 경로가 확인되면 리스너로 바꿀 것) */
    private val fastPoll = object : Runnable {
        override fun run() {
            val v = vehicle
            if (v != null) {
                guard("gear") {
                    v.getGear().getCurrentGearSelectionState({ g -> onGear(g) }, fail("gear") { s, x -> s.copy(gear = x) })
                }
                guard("steering") {
                    v.getSteeringWheel().getCurrentSteeringWheelAngle(
                        { a -> (a as? Number)?.toDouble()?.let { d -> set { s, t -> s.copy(steeringDeg = s.steeringDeg.updated(d, t)) } } },
                        fail("steering") { s, x -> s.copy(steeringDeg = x) },
                    )
                }
            }
            handler.postDelayed(this, FAST_MS)
        }
    }

    private fun onGear(g: Any?) {
        val name = g?.toString()?.uppercase() ?: return
        val gear = when {
            "PARK" in name -> Gear.P
            "DRIVE" in name -> Gear.D
            "REVERSE" in name -> Gear.R
            "NEUTRAL" in name -> Gear.N
            else -> return
        }
        set { s, t -> s.copy(gear = s.gear.updated(gear, t)) }
    }

    /** 누적거리·안전벨트·경고등: 2초 주기 조회. */
    private val slowPoll = object : Runnable {
        override fun run() {
            val v = vehicle
            if (v != null) {
                guard("odometer") {
                    v.getOdometer().getCurrentDistanceDriven(
                        { d -> (d as? Number)?.toDouble()?.let { km -> set { s, t -> s.copy(odometerKm = s.odometerKm.updated(km, t)) } } },
                        fail("odometer") { s, x -> s.copy(odometerKm = x) },
                    )
                }
                guard("belt") {
                    v.getSeat().getCurrentSeatBeltBuckledState(
                        SeatArea(SeatValue.ROW_1_LEFT),
                        { b -> (b as? Boolean)?.let { on -> set { s, t -> s.copy(driverBeltBuckled = s.driverBeltBuckled.updated(on, t)) } } },
                        fail("belt") { s, x -> s.copy(driverBeltBuckled = x) },
                    )
                }
                if (usePrivateSafety) {
                    guard("warnings") {
                        v.getSafety().getTelltalesInfo(
                            { info -> set { s, t -> s.copy(warnings = s.warnings.updated(parseTelltales(info), t)) } },
                            fail("warnings") { s, x -> s.copy(warnings = x) },
                        )
                    }
                }
            }
            handler.postDelayed(this, SLOW_MS)
        }
    }

    /**
     * 켜진 경고등 이름만 뽑는다. 예: Telltales(telltales=[TelltaleData(type=PARKING_BRAKE, state=ON, ...)])
     * 객체 구조가 확인되면 필드 접근으로 바꿀 것.
     */
    private fun parseTelltales(info: Any?): List<String> =
        TELLTALE_ON.findAll(info?.toString().orEmpty()).map { it.groupValues[1] }.toList()

    /** SDK 호출이 즉시 예외를 던져도 다른 신호는 계속 받도록. */
    private fun guard(field: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$field 호출 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ---------------- AAOS ----------------

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        set { s, t -> s.copy(uxRestricted = s.uxRestricted.updated(r.isRequiresDistractionOptimization, t)) }
    }

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val mps = (value.value as? Float) ?: return
            set { s, t -> s.copy(speedKmh = s.speedKmh.updated(mps * 3.6, t)) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
    }

    private fun startAaos() {
        try {
            car = Car.createCar(appContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) onCarReady(c)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AAOS Car 연결 실패", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun onCarReady(c: Car) {
        runCatching {
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            uxManager = ux
            ux.registerListener(uxListener)
            ux.currentCarUxRestrictions?.let { r ->
                set { s, t -> s.copy(uxRestricted = s.uxRestricted.updated(r.isRequiresDistractionOptimization, t)) }
            }
        }.onFailure { Log.w(TAG, "주행 제한 구독 실패: ${it.message}") }

        val speedGranted = appContext.checkSelfPermission(PERMISSION_CAR_SPEED) == PackageManager.PERMISSION_GRANTED
        if (!speedGranted) {
            set { s, _ -> s.copy(speedKmh = Signal.NoPermission) }
            return
        }
        runCatching {
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            propertyManager = pm
            pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        }.onFailure { Log.w(TAG, "차속 구독 실패: ${it.message}") }
    }

    companion object {
        private const val TAG = "DrivePetVehicle"
        private const val FAST_MS = 500L
        private const val SLOW_MS = 2_000L
        const val PERMISSION_CAR_SPEED = "android.car.permission.CAR_SPEED"
        private val TELLTALE_ON = Regex("""type=(\w+), state=ON""")
    }
}