package com.drivepet.app.drive

import com.drivepet.app.data.RecordStore
import com.drivepet.app.data.TripPayload
import com.drivepet.app.data.db.InterruptedTripEntity
import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.data.db.TripInProgressEntity
import com.drivepet.app.data.toJson
import com.drivepet.app.data.growthTotalMilli
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.Growth
import com.drivepet.app.domain.HealthResult
import com.drivepet.app.domain.Settlement
import com.drivepet.app.domain.TripMetrics
import com.drivepet.app.domain.isoSeoul
import com.drivepet.app.domain.settle
import com.drivepet.vehicle.engine.EventEngine
import com.drivepet.vehicle.model.DriveEvent
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.TripSummary
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.valueOrNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.roundToLong

data class ProfileSnapshot(val profileId: String, val accountId: String?, val fuelType: FuelType, val batteryKwh: Double?)

sealed interface RecorderOutput {
    /** [paidMilli]는 원장이 이번 트립에 실제로 지급한 EXP(정산 전후 총합의 차). */
    data class Settled(
        val recordId: String, val settlement: Settlement, val levelBefore: Int, val levelAfter: Int, val paidMilli: Long,
    ) : RecorderOutput
}

private const val SAVE_INTERVAL_MS = 10_000L
private const val MIN_TRIP_MS = 30_000L // vehicle 모듈은 30초 미만 트립을 버린다
// 한국교통안전공단 eTAS 위험운전행동 기준 (docs/research/2026-11-10-health-score-basis.md)
private const val HARSH_ACCEL_KMH_PER_S = 11.0 // 급가속: 초당 11km/h 이상 가속
private const val HARSH_DECEL_KMH_PER_S = 7.5 // 급감속: 초당 7.5km/h 이상 감속(급제동 구분 없이 모두 7.5)
private const val HARSH_MIN_INTERVAL_MS = 1_000L // eTAS는 1초 자료 기준: 약 1초 전 표본과 비교하고, 1초보다 가까운 표본뿐이면 1초로 본다
private const val HARSH_MAX_INTERVAL_MS = 2_000L // 2초 안에 비교할 표본이 없으면 판정하지 않는다
private const val LONG_BRAKE_MS = 3_000L

/** TripSummary는 @Serializable이 아니라 trip_in_progress.summaryJson용 사본을 둔다. */
@Serializable
private data class SummaryJson(
    val startedAt: Long, val endedAt: Long?, val startOdometerKm: Double?, val endOdometerKm: Double?,
    val sharpSteerCount: Int, val brakeCount: Int, val brakeHoldTotalMs: Long, val turnSignalCount: Int,
    val bigTurnCount: Int, val turnWithoutSignalCount: Int, val missingData: Set<String>,
)

private fun TripSummary.toJson() = SummaryJson(
    startedAt, endedAt, startOdometerKm, endOdometerKm, sharpSteerCount, brakeCount, brakeHoldTotalMs,
    turnSignalCount, bigTurnCount, turnWithoutSignalCount, missingData,
)

/** trip_in_progress.metricsJson. */
@Serializable
private data class Counters(
    var harshAccel: Int = 0, var harshDecel: Int = 0, var longBrakes: Int = 0, var nonZeroSpeed: Boolean = false,
    var energyStartPct: Int? = null, var energyEndPct: Int? = null,
)

private fun SummaryJson.distanceKm(): Double? =
    if (startOdometerKm != null && endOdometerKm != null) (endOdometerKm - startOdometerKm).coerceAtLeast(0.0) else null

private fun kmToMm(km: Double?) = km?.let { (it * 1_000_000).roundToLong() }

/**
 * 트립 진행 저장·정산. EXP 합산은 원장(records)에 개발자 시작 EXP(store.seed)를 단순 덧셈해 레벨을 구한다.
 * 동시 호출은 가정하지 않는다(한 코루틴에서 순서대로).
 */
class TripRecorder(
    private val store: RecordStore,
    private val healthNow: suspend (profileId: String) -> HealthResult,
    private val clock: () -> Long,
) {
    private var activeStartedAt: Long? = null // 카운터가 속한 트립. TripEnded 정산 후 null.
    private var counters = Counters()
    private var lastTrip: TripSummary? = null
    private var lastSavedAt: Long? = null
    private val recentSpeeds = ArrayDeque<Signal.Ok<Double>>() // 최근 2초 차속 표본(급가속·급감속 판정용)
    private var inHarshAccel = false
    private var inHarshDecel = false
    private var latestEnergyPct: Int? = null

    /** 앱이 다시 열렸을 때 남은 진행 중 행(옛 interrupted 표시 포함)은 중단된 트립으로 보관한다. 지금 기록 중인 트립은 건드리지 않는다. */
    suspend fun recoverOnStart(profileId: String) {
        val row = store.inProgress(profileId) ?: return
        if (row.startedAt != activeStartedAt) interrupt(row)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onState(prev: VehicleState, next: VehicleState) {
        next.energyPct.valueOrNull()?.let { latestEnergyPct = it }
        if (activeStartedAt == null) return
        val energy = next.energyPct.valueOrNull()
        if (energy != null) {
            if (counters.energyStartPct == null) counters.energyStartPct = energy
            counters.energyEndPct = energy
        }
        val speed = next.speedKmh as? Signal.Ok<Double> ?: return
        val last = recentSpeeds.lastOrNull()
        if (last != null && last.at == speed.at && last.value == speed.value) return
        if (speed.value != 0.0) counters.nonZeroSpeed = true
        while (recentSpeeds.lastOrNull()?.let { it.at >= speed.at } == true) recentSpeeds.removeLast()
        while (recentSpeeds.firstOrNull()?.let { speed.at - it.at > HARSH_MAX_INTERVAL_MS } == true) recentSpeeds.removeFirst()
        // 차속은 바뀔 때마다 0.1~0.5초 간격으로 들어온다. 바로 앞 표본이 아니라 약 1초 전 값과 비교해야 초당 변화량이 된다.
        val ref = recentSpeeds.lastOrNull { speed.at - it.at >= HARSH_MIN_INTERVAL_MS } ?: recentSpeeds.firstOrNull()
        val rate = ref?.let { (speed.value - it.value) / (maxOf(speed.at - it.at, HARSH_MIN_INTERVAL_MS) / 1000.0) }
        // 기준을 넘는 동안 표본마다 세지 않고, 넘기 시작할 때 1회로 센다.
        val accel = rate != null && rate >= HARSH_ACCEL_KMH_PER_S
        val decel = rate != null && rate <= -HARSH_DECEL_KMH_PER_S
        if (accel && !inHarshAccel) counters.harshAccel++
        if (decel && !inHarshDecel) counters.harshDecel++
        inHarshAccel = accel
        inHarshDecel = decel
        recentSpeeds.addLast(speed)
    }

    suspend fun onTrip(profile: ProfileSnapshot, trip: TripSummary?) {
        val now = clock()
        if (trip != null) {
            if (trip.startedAt != activeStartedAt) {
                // 남아 있던 다른 트립의 진행 행은 새 트립이 덮어쓰기 전에 중단된 트립으로 보관한다.
                store.inProgress(profile.profileId)?.takeIf { it.startedAt != trip.startedAt }?.let { interrupt(it) }
                activeStartedAt = trip.startedAt
                // 출발 전에 이미 받아 둔 잔량을 시작값으로 쓴다(값이 안 바뀌면 다시 안 들어오므로).
                counters = Counters(energyStartPct = latestEnergyPct, energyEndPct = latestEnergyPct)
                recentSpeeds.clear()
                inHarshAccel = false
                inHarshDecel = false
                lastSavedAt = null
            }
            lastTrip = trip
            if (lastSavedAt.let { it == null || now - it >= SAVE_INTERVAL_MS }) save(profile, trip, now)
            return
        }
        // TripEnded가 먼저 왔으면 activeStartedAt은 이미 null.
        val started = activeStartedAt ?: return
        val last = lastTrip ?: return
        if (now - started < MIN_TRIP_MS) {
            clearInProgressOf(profile.profileId, started)
        } else {
            val json = Json.encodeToString(last.toJson())
            store.saveInterrupted(InterruptedTripEntity(profile.profileId, last.startedAt, json, last.distanceKm, now))
            clearInProgressOf(profile.profileId, started, keepInterrupted = true)
        }
    }

    suspend fun onEvent(profile: ProfileSnapshot, e: DriveEvent): RecorderOutput? {
        when (e) {
            is DriveEvent.BrakeReleased -> if (activeStartedAt != null && e.holdMs >= LONG_BRAKE_MS) counters.longBrakes++
            is DriveEvent.TripEnded -> return settle(profile, e)
            else -> Unit
        }
        return null
    }

    private suspend fun interrupt(row: TripInProgressEntity) {
        val km = runCatching { Json.decodeFromString<SummaryJson>(row.summaryJson).distanceKm() }.getOrNull()
        store.saveInterrupted(InterruptedTripEntity(row.profileId, row.startedAt, row.summaryJson, km, clock()))
        store.clearInProgress(row.profileId)
    }

    private suspend fun save(profile: ProfileSnapshot, trip: TripSummary, now: Long) {
        store.saveInProgress(
            TripInProgressEntity(
                profile.profileId, trip.startedAt,
                Json.encodeToString(trip.toJson()), Json.encodeToString(counters), false, now,
            ),
        )
        lastSavedAt = now
    }

    private suspend fun settle(profile: ProfileSnapshot, e: DriveEvent.TripEnded): RecorderOutput? {
        val trip = e.trip
        val pid = profile.profileId
        val recordId = UUID.nameUUIDFromBytes("trip:$pid:${trip.startedAt}".toByteArray()).toString()
        if (store.records(pid).first().any { it.recordId == recordId }) {
            clearInProgressOf(pid, trip.startedAt)
            return null
        }
        val c = if (activeStartedAt == trip.startedAt) counters else Counters()
        val metrics = TripMetrics(
            startMm = kmToMm(trip.startOdometerKm), endMm = kmToMm(trip.endOdometerKm),
            speedMeasured = c.nonZeroSpeed, harshAccel = c.harshAccel, harshDecel = c.harshDecel, longBrakes = c.longBrakes,
            steeringMeasured = EventEngine.DATA_STEERING !in trip.missingData, sharpSteers = trip.sharpSteerCount,
            bigTurns = trip.bigTurnCount, turnsWithoutSignal = trip.turnWithoutSignalCount,
            fuelType = profile.fuelType, energyStartPct = c.energyStartPct, energyEndPct = c.energyEndPct,
            batteryKwh = profile.batteryKwh,
        )
        val settlement = settle(metrics, healthNow(pid))
        val endedAt = trip.endedAt ?: e.at
        val payload = TripPayload(
            startedAt = isoSeoul(trip.startedAt), endedAt = isoSeoul(endedAt),
            startOdometerMm = metrics.startMm, endOdometerMm = metrics.endMm,
            distanceStatus = if (settlement.valid) "VALID" else "UNVERIFIED",
            settlement = settlement.toJson(),
        )
        val before = total(pid)
        val inserted = store.insertIfAbsent(
            RecordEntity(
                recordId = recordId, profileId = pid, accountId = profile.accountId, kind = "TRIP", occurredAt = endedAt,
                payloadJson = Json.encodeToString(payload),
                startMm = metrics.startMm, endMm = metrics.endMm, valid = settlement.valid, bonusMilli = settlement.bonusMilli,
            ),
        )
        clearInProgressOf(pid, trip.startedAt)
        if (activeStartedAt == trip.startedAt) activeStartedAt = null
        val after = total(pid)
        return if (inserted) RecorderOutput.Settled(recordId, settlement, Growth.level(before), Growth.level(after), after - before) else null
    }

    // 늦게 온 옛 TripEnded가 더 새 트립의 진행 행을 지우지 않게 startedAt이 같을 때만 지운다.
    // 정산된 트립은 중단 목록에 남지 않게 같은 startedAt의 중단 행도 지운다.
    private suspend fun clearInProgressOf(profileId: String, startedAt: Long, keepInterrupted: Boolean = false) {
        if (store.inProgress(profileId)?.startedAt == startedAt) store.clearInProgress(profileId)
        if (!keepInterrupted) store.deleteInterrupted(profileId, startedAt)
    }

    private suspend fun total(profileId: String): Long =
        growthTotalMilli(store.records(profileId).first(), store.seed(profileId).first())
}
