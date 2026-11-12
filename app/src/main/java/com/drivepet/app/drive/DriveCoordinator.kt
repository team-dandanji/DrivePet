package com.drivepet.app.drive

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.drivepet.app.MainActivity
import com.drivepet.app.data.PendingCare
import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.Settings
import com.drivepet.app.data.careRows
import com.drivepet.app.data.lifePcts
import com.drivepet.app.data.odometerMm
import com.drivepet.app.data.recentTripSafety
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.HealthResult
import com.drivepet.app.domain.formatExp
import com.drivepet.app.domain.health
import com.drivepet.app.domain.seoulDate
import com.drivepet.app.domain.toWarningInput
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.model.DriveEvent
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

private const val TAG = "DriveCoordinator"
private const val CHANNEL_SETTLEMENT = "settlement"
private const val NOTIFICATION_SETTLEMENT = 2001

/** 피그마 규칙: 기어가 P가 아니면(N 포함) 주행 중. 주행 제한(uxRestricted)이 켜져도 주행 중. 기어를 못 읽으면 제한 값만 따르고, 둘 다 못 읽으면 false. */
fun isDriving(s: VehicleState): Boolean {
    val gear = s.gear.valueOrNull()
    return (gear != null && gear != Gear.P) || s.uxRestricted.valueOrNull() == true
}

/** 정비소 안내를 시작한 뒤 처음 주행하면 팝업을 켠다(armed). 이미 켰거나 대기가 없으면 쓰지 않는다. */
fun shouldArm(pending: PendingCare?, driving: Boolean): Boolean = driving && pending != null && !pending.armed

/** 라운지 진입만으로 출석시켜도 되는 때: 실차(REAL)에서 벨트를 못 읽고 정차 중. FAKE·REPLAY는 제외. */
fun shouldAutoCheckIn(mode: VehicleModule.Mode?, beltReadable: Boolean, parked: Boolean): Boolean =
    mode == VehicleModule.Mode.REAL && !beltReadable && parked

/**
 * VehicleModule의 state·events·trip을 받아 기록·출석·하차 점검·말풍선·정산 알림으로 나눠 준다.
 * TripRecorder 등은 스레드 안전하지 않아 세 흐름을 하나의 [mutex] 아래에서만 처리한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveCoordinator(
    private val context: Context,
    private val store: RecordStore,
    private val settings: Settings,
    private val speaker: Speaker,
    scope: CoroutineScope,
) {
    private val repository get() = VehicleModule.repository
    private val mutex = Mutex()
    private val reactionFilter = ReactionFilter(System::currentTimeMillis)
    private val attendance = AttendanceWatcher(store, Instant::now)
    private val exitTracker = ExitCheckTracker()
    private val missionClaimer = MissionAutoClaimer(store, Instant::now)
    private val recorder = TripRecorder(store, ::healthNow, System::currentTimeMillis)
    private var lastSyncedWarnings: Pair<String, List<String>>? = null

    val activeProfile: StateFlow<ProfileSnapshot?> =
        combine(settings.activeProfileId, store.profiles()) { id, list ->
            list.firstOrNull { it.profileId == id }?.let {
                runCatching { ProfileSnapshot(it.profileId, it.accountId, FuelType.valueOf(it.fuelType), it.batteryKwh) }.getOrNull()
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val pendingCare: StateFlow<PendingCare?> = settings.pendingCare.stateIn(scope, SharingStarted.Eagerly, null)

    private val _pendingSettlement = MutableStateFlow<RecorderOutput.Settled?>(null)
    val pendingSettlement: StateFlow<RecorderOutput.Settled?> = _pendingSettlement
    fun consumeSettlement() { _pendingSettlement.value = null }

    val exitCheck: StateFlow<ExitCheck?> = exitTracker.open
    suspend fun dismissExitCheck() = mutex.withLock { exitTracker.dismiss() }
    val beltReadable: StateFlow<Boolean> = attendance.beltReadable
    val appInForeground = MutableStateFlow(false)

    @Volatile var mode: VehicleModule.Mode? = null
        private set
    private var attempted = false
    private var started = false

    init {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_SETTLEMENT, "주행 정산", NotificationManager.IMPORTANCE_DEFAULT))
        scope.launch {
            activeProfile.map { it?.profileId }.distinctUntilChanged().filterNotNull()
                .collect { id -> guarded { mutex.withLock { recorder.recoverOnStart(id) } } }
        }
        scope.launch {
            var prev = repository.state.value
            repository.state.collect { next -> guarded { mutex.withLock { onState(prev, next) } }; prev = next }
        }
        scope.launch { repository.trip.collect { t -> guarded { mutex.withLock { activeProfile.value?.let { recorder.onTrip(it, t) } } } } }
        // 기록이 바뀔 때마다 달성한 미션을 자동 지급. 지급이 기록을 다시 흘려도 recordId가 같아 두 번 생기지 않는다.
        scope.launch {
            activeProfile.filterNotNull().flatMapLatest { p -> store.records(p.profileId).map { p } }
                .collect { p -> guarded { mutex.withLock { missionClaimer.claimDone(p.profileId, p.accountId) } } }
        }
        scope.launch { repository.events.collect { e -> guarded { mutex.withLock { onEvent(e) } } } }
    }

    /** 백그라운드에서 포그라운드 서비스를 못 켜 실패했을 수 있어 호출 쪽(MainActivity.onStart)에서 재시도한다. */
    @Synchronized
    fun start(mode: VehicleModule.Mode) {
        attempted = true
        this.mode = mode
        started = runCatching { VehicleModule.start(context, mode) }
            .onFailure { Log.w(TAG, "VehicleModule.start failed", it) }.isSuccess
        reactionFilter.sourceStarted()
        Log.i(TAG, "started mode=$mode ok=$started")
    }

    /** 성공한 시작이 없고 첫 시작 시도(DrivePetApp)가 이미 있었을 때만 다시 시작한다. */
    @Synchronized
    fun ensureStarted() {
        if (started || !attempted) return
        mode?.let { start(it) }
    }

    /** 벨트를 못 읽는 실차가 정차 중일 때 라운지에 들어오면 자동 출석. 이미 출석했으면 false. */
    suspend fun checkInOnLounge(): Boolean = mutex.withLock {
        val p = activeProfile.value ?: return false
        val s = repository.state.value
        if (!shouldAutoCheckIn(mode, beltReadable.value, s.isParked)) return false
        attendance.manualCheckIn(p.profileId, p.accountId, s).also { if (it) speaker.say(ATTENDANCE_TEXT) }
    }

    private suspend fun onState(prev: VehicleState, next: VehicleState) {
        exitTracker.onState(next)
        recorder.onState(prev, next)
        if (shouldArm(pendingCare.value, isDriving(next))) settings.armPendingCare()
        val p = activeProfile.value ?: return
        val warnings = (next.warnings as? Signal.Ok)?.value
        if (warnings != null && lastSyncedWarnings != p.profileId to warnings) {
            store.syncWarnings(p.profileId, warnings, seoulDate(Instant.now()))
            lastSyncedWarnings = p.profileId to warnings
        }
        if (attendance.onState(p.profileId, p.accountId, next)) speaker.say(ATTENDANCE_TEXT)
    }

    private suspend fun onEvent(e: DriveEvent) {
        exitTracker.onEvent(e)
        val p = activeProfile.value
        if (p != null) (recorder.onEvent(p, e) as? RecorderOutput.Settled)?.let(::onSettled)
        react(e)
    }

    // 주행 중 반응은 음성 전용(화면 반응 없음). 말풍선 표시 여부는 화면이 정한다.
    private fun react(e: DriveEvent) {
        if (!reactionFilter.allowReaction(e)) return
        val text = when (e) {
            is DriveEvent.TripStarted -> "출발! 안전운전 해요"
            is DriveEvent.SharpSteer -> "깜짝이야! 천천히 돌아요"
            is DriveEvent.TurnWithoutSignal -> "방향지시등을 켜 주세요"
            is DriveEvent.BeltReminder -> "벨트를 매 주세요"
            is DriveEvent.DriverDoorOpened -> if (e.parked) return else "문이 열렸어요! 확인해 주세요"
            else -> return
        }
        speaker.say(text, voice = true)
    }

    private fun onSettled(out: RecorderOutput.Settled) {
        _pendingSettlement.value = out
        if (appInForeground.value) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val exp = "+" + formatExp(out.paidMilli)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_SETTLEMENT)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("주행 정산 완료")
            .setContentText("$exp EXP · 눌러서 확인")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_SETTLEMENT, n)
    }

    private suspend fun healthNow(profileId: String): HealthResult {
        val warnings = repository.state.value.warnings.toWarningInput()
        val records = store.records(profileId).first()
        val lifePcts = careRows(store.maintenanceItems(profileId).first(), records, repository.state.value.odometerMm()).lifePcts()
        return health(warnings, lifePcts, records.recentTripSafety())
    }

    private inline fun guarded(block: () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e(TAG, "handler failed", e) }
    }

    private companion object {
        const val ATTENDANCE_TEXT = "벨트 착용 완료! 오늘도 출석했어요"
    }
}
