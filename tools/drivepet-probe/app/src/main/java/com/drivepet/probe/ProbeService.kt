package com.drivepet.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * P0 검증용 포그라운드 서비스.
 * - AAOS: 기어(V-01), 차속(V-02), 주차브레이크(V-08), 주행 제한(V-04)
 * - Vehicle SDK: 기어(V-01), 브레이크(V-03, 실시간 수신), 조향각(V-03), 누적·트립 주행거리(V-08), 경고등(V-08)
 * - 앱이 백그라운드에 있어도 기록이 이어지는지(V-05), P 전환 때 화면이 뜨는지(V-06)
 */
class ProbeService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var brakeListener: BrakePedalStatusListener? = null
    private var lastGear: Int? = null

    /** Vehicle SDK 조회 목록. (화면 이름, 도메인 getter, 조회 메서드, 영역 또는 null) */
    private val polls: List<Poll> by lazy {
        listOf(
            Poll("기어 (SDK)", "getGear", "getCurrentGearSelectionState"),
            Poll("브레이크 페달 (SDK 조회)", "getBrake", "getCurrentBrakePedalStatus"),
            Poll("조향각 (SDK)", "getSteeringWheel", "getCurrentSteeringWheelAngle"),
            Poll("누적 주행거리 km (SDK)", "getOdometer", "getCurrentDistanceDriven"),
            Poll("트립 거리 (SDK)", "getOdometer", "getTripCurrentDrivingDistance"),
            Poll("경고등 (SDK)", "getSafety", "getTelltalesInfo"),
            // 깜빡이 반응용
            Poll("운전석 안전벨트 (SDK)", "getSeat", "getCurrentSeatBeltBuckledState", runCatching { SeatArea(SeatValue.ROW_1_LEFT) }.getOrNull()),
            Poll("운전석 문 위치 (SDK)", "getDoor", "getCurrentDoorPosition", runCatching { DoorArea(DoorValue.ROW_1_LEFT) }.getOrNull()),
            Poll("운전석 창문 위치 (SDK)", "getWindow", "getCurrentWindowPosition", runCatching { WindowArea(WindowValue.ROW_1_LEFT) }.getOrNull()),
            Poll("전조등 하향 (SDK)", "getLight", "getCurrentLowBeamLightState"),
            Poll("전조등 상향 (SDK)", "getLight", "getCurrentHighBeamLightState"),
            Poll("방향지시등 (SDK)", "getTurnSignal", "getCurrentTurnSignalState"),
        )
    }

    private data class Poll(val label: String, val domain: String, val method: String, val area: Any? = null)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Probe.init(this)
        startInForeground()
        Probe.log("검증 서비스 시작 (포그라운드 서비스)")
        startAaos()
        startVehicle()
        handler.post(pollTask)
        handler.postDelayed(heartbeat, HEARTBEAT_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(pollTask)
        handler.removeCallbacks(heartbeat)
        brakeListener?.let { l -> runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(l) } }
        runCatching { vehicle?.release() }
        runCatching { uxManager?.unregisterListener() }
        runCatching { propertyManager?.unregisterCallback(propertyCallback) }
        runCatching { car?.disconnect() }
        Probe.log("검증 서비스 중지")
        super.onDestroy()
    }

    // ---------------- 포그라운드 서비스 ----------------

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "검증 서비스", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_PARK, "P 전환 알림", NotificationManager.IMPORTANCE_HIGH))
        val notification = Notification.Builder(this, CH_SERVICE)
            .setContentTitle("DrivePet 검증 중")
            .setContentText("차량 신호를 기록하고 있어요")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTI_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTI_SERVICE, notification)
            }
        } catch (e: Exception) {
            Probe.log("포그라운드 서비스 시작 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            Probe.log(
                "살아 있음 · 기어=${Probe.value("기어 (AAOS)")} · 차속=${Probe.value("차속 m/s (AAOS)")} " +
                    "· 브레이크=${Probe.value("브레이크 페달 (SDK 수신)")}",
            )
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    // ---------------- AAOS ----------------

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> applyUx(r) }

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
                Probe.set(nameOf(value.propertyId), "상태 코드 ${value.status}", "AAOS 수신")
                return
            }
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> onGear(value.value as? Int, "AAOS 수신")
                VehiclePropertyIds.PARKING_BRAKE_ON -> Probe.set("주차브레이크 (AAOS)", value.value.toString(), "AAOS 수신")
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> Probe.set("차속 m/s (AAOS)", value.value.toString(), "AAOS 수신")
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Probe.set(nameOf(propertyId), "오류 이벤트 (area=$areaId)", "AAOS 수신")
        }
    }

    private fun startAaos() {
        try {
            car = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) onCarReady(c) else Probe.log("AAOS Car 서비스 연결 끊김")
            }
        } catch (e: Exception) {
            Probe.log("AAOS Car 연결 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun onCarReady(c: Car) {
        Probe.log("AAOS Car 서비스 연결됨")

        val ux = runCatching { c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager }.getOrNull()
        if (ux == null) {
            Probe.set("주행 제한 (AAOS)", "서비스 없음", "AAOS")
        } else {
            uxManager = ux
            runCatching {
                ux.registerListener(uxListener)
                applyUx(ux.currentCarUxRestrictions)
            }.onFailure { Probe.set("주행 제한 (AAOS)", "실패: ${it.message}", "AAOS") }
        }

        val pm = runCatching { c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }.getOrNull()
        if (pm == null) {
            Probe.log("CarPropertyManager 없음")
            return
        }
        propertyManager = pm

        readOnce("기어 (AAOS)") { onGear(pm.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0), "AAOS 조회") }
        readOnce("주차브레이크 (AAOS)") {
            Probe.set("주차브레이크 (AAOS)", pm.getBooleanProperty(VehiclePropertyIds.PARKING_BRAKE_ON, 0).toString(), "AAOS 조회")
        }
        readOnce("차속 m/s (AAOS)") {
            Probe.set("차속 m/s (AAOS)", pm.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0).toString(), "AAOS 조회")
        }

        subscribe(pm, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(pm, VehiclePropertyIds.PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(pm, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
    }

    private fun readOnce(key: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // 권한이 없으면 SecurityException
            Probe.set(key, "실패: ${e.javaClass.simpleName}: ${e.message}", "AAOS 조회")
        }
    }

    @Suppress("DEPRECATION")
    private fun subscribe(pm: CarPropertyManager, propertyId: Int, rate: Float) {
        try {
            val ok = pm.registerCallback(propertyCallback, propertyId, rate)
            Probe.log("${nameOf(propertyId)} 구독 = $ok")
        } catch (e: Exception) {
            Probe.log("${nameOf(propertyId)} 구독 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun applyUx(r: CarUxRestrictions?) {
        if (r == null) return
        Probe.set(
            "주행 제한 (AAOS)",
            "화면 제한 필요=${r.isRequiresDistractionOptimization}, 제한 코드=0x${Integer.toHexString(r.activeRestrictions)}",
            "AAOS 수신",
        )
    }

    /** 기어 변경. D/R/N에서 P로 바뀌면 V-06 확인을 위해 화면 띄우기와 알림을 시도한다. */
    private fun onGear(gear: Int?, source: String) {
        Probe.set("기어 (AAOS)", gearName(gear), source)
        val previous = lastGear
        lastGear = gear
        if (previous != null && previous != GEAR_PARK && gear == GEAR_PARK) onParked()
    }

    private fun onParked() {
        Probe.log("P 전환 감지 → 앱 화면 띄우기 시도 (V-06)")
        Probe.parkRequestedAt = System.currentTimeMillis()
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_FROM_PARK, true),
            )
            Probe.log("화면 띄우기 요청 보냄 (실제로 떴는지는 다음 줄 확인)")
        } catch (e: Exception) {
            Probe.log("화면 띄우기 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
        try {
            val open = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(this, CH_PARK)
                .setContentTitle("주차 완료")
                .setContentText("눌러서 오늘의 드라이브 보기")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTI_PARK, n)
            Probe.log("P 전환 알림 게시함 (화면에 보였는지 직접 확인)")
        } catch (e: Exception) {
            Probe.log("알림 게시 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ---------------- Pleos Vehicle SDK ----------------

    private fun startVehicle() {
        try {
            val v = Vehicle(this)
            v.initialize()
            vehicle = v
            Probe.log("Vehicle SDK 초기화 완료")
            registerBrake(v)
        } catch (e: Exception) {
            Probe.log("Vehicle SDK 초기화 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 브레이크는 리스너로 받아 '변경 수신'까지 확인한다 (V-03). */
    private fun registerBrake(v: Vehicle) {
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                Probe.set("브레이크 페달 (SDK 수신)", pressed.toString(), "SDK 수신")
            }

            override fun onFailed(e: Exception) {
                Probe.set("브레이크 페달 (SDK 수신)", "실패: ${e.javaClass.simpleName}: ${e.message}", "SDK 수신")
            }
        }
        try {
            v.getBrake().registerBrakePedalStatus(listener)
            brakeListener = listener
            Probe.log("브레이크 페달 리스너 등록")
        } catch (e: Exception) {
            Probe.log("브레이크 페달 리스너 등록 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private val pollTask = object : Runnable {
        override fun run() {
            vehicle?.let { v -> polls.forEach { p -> sdkGet(v, p.label, p.domain, p.method, p.area) } }
            handler.postDelayed(this, POLL_MS)
        }
    }

    /**
     * 조회 메서드 `method([area,] onComplete, onFailed)`를 이름으로 찾아 호출한다.
     * 메서드 시그니처 차이로 컴파일이 깨지지 않게 리플렉션을 쓴다.
     */
    private fun sdkGet(v: Vehicle, label: String, domainGetter: String, method: String, area: Any? = null) {
        try {
            val domain = v.javaClass.getMethod(domainGetter).invoke(v)
                ?: return Probe.set(label, "도메인 없음 ($domainGetter)", "SDK 조회")
            val m = domain.javaClass.methods.firstOrNull { it.name == method && it.parameterTypes.size == (if (area == null) 2 else 3) }
                ?: return Probe.set(label, "메서드 없음 ($method)", "SDK 조회")
            m.isAccessible = true
            val onComplete: (Any?) -> Unit = { value -> Probe.set(label, value?.toString() ?: "null", "SDK 조회") }
            val onFailed: (Exception) -> Unit = { e ->
                Probe.set(label, "실패: ${e.javaClass.simpleName}: ${e.message}", "SDK 조회")
            }
            if (area == null) m.invoke(domain, onComplete, onFailed) else m.invoke(domain, area, onComplete, onFailed)
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            Probe.set(label, "호출 실패: ${cause.javaClass.simpleName}: ${cause.message}", "SDK 조회")
        }
    }

    // ---------------- 기타 ----------------

    private fun nameOf(propertyId: Int): String = when (propertyId) {
        VehiclePropertyIds.GEAR_SELECTION -> "기어 (AAOS)"
        VehiclePropertyIds.PARKING_BRAKE_ON -> "주차브레이크 (AAOS)"
        VehiclePropertyIds.PERF_VEHICLE_SPEED -> "차속 m/s (AAOS)"
        else -> VehiclePropertyIds.toString(propertyId)
    }

    private fun gearName(gear: Int?): String = when (gear) {
        null -> "null"
        GEAR_NEUTRAL -> "N"
        GEAR_REVERSE -> "R"
        GEAR_PARK -> "P"
        GEAR_DRIVE -> "D"
        else -> "기타($gear)"
    }

    companion object {
        const val EXTRA_FROM_PARK = "fromPark"
        private const val CH_SERVICE = "probe_service"
        private const val CH_PARK = "probe_park"
        private const val NOTI_SERVICE = 1
        private const val NOTI_PARK = 2
        private const val POLL_MS = 2_000L
        private const val HEARTBEAT_MS = 10_000L

        // android.car.VehicleGear 값
        private const val GEAR_NEUTRAL = 1
        private const val GEAR_REVERSE = 2
        private const val GEAR_PARK = 4
        private const val GEAR_DRIVE = 8
    }
}
