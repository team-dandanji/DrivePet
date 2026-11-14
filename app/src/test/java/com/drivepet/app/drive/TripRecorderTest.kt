package com.drivepet.app.drive

import com.drivepet.app.data.FakeRecordStore
import com.drivepet.app.data.TripPayload
import com.drivepet.app.data.db.TripInProgressEntity
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.HealthResult
import com.drivepet.app.domain.MeasurementStatus
import com.drivepet.app.domain.SafetyItem
import com.drivepet.app.drive.RecorderOutput.Settled
import com.drivepet.vehicle.model.DriveEvent.BrakeReleased
import com.drivepet.vehicle.model.DriveEvent.TripEnded
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.TripSummary
import com.drivepet.vehicle.model.VehicleState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEOUL_0830 = 1_794_267_000_000L // 2026-11-10T08:30:00+09:00

class TripRecorderTest {
    private var now = 0L
    private var healthCalls = 0
    private val store = FakeRecordStore()
    private val r = TripRecorder(store, { healthCalls++; HealthResult(null, MeasurementStatus.UNMEASURED) }, { now })
    private val profile = ProfileSnapshot("p", "acc", FuelType.EV, 60.0)

    private fun summary(
        startKm: Double? = 12034.0, endKm: Double? = 12046.0, sharpSteer: Int = 0, bigTurn: Int = 3, noSignal: Int = 0,
        startedAt: Long = 0, endedAt: Long? = 120_000,
    ) = TripSummary(
        startedAt = startedAt, endedAt = endedAt, startOdometerKm = startKm, endOdometerKm = endKm,
        sharpSteerCount = sharpSteer, bigTurnCount = bigTurn, turnWithoutSignalCount = noSignal,
        missingData = setOf("brake"),
    )

    private fun inProgress(startedAt: Long = 0) = summary(endKm = null, endedAt = null, startedAt = startedAt)
    private fun speed(v: Double, at: Long) = VehicleState(speedKmh = Signal.Ok(v, at))
    private fun energy(v: Int) = VehicleState(energyPct = Signal.Ok(v, 0))
    private fun line(s: com.drivepet.app.domain.Settlement, item: SafetyItem) = s.breakdown!!.first { it.item == item }

    @Test fun `trip end stores settlement and reports level up`() = runTest {
        store.setSeed("p", 370_000)
        val out = r.onEvent(profile, TripEnded(summary(startedAt = SEOUL_0830, endedAt = SEOUL_0830 + 600_000), 0)) as Settled
        assertEquals(1, out.levelBefore); assertEquals(2, out.levelAfter)
        val rec = store.records("p").first().single()
        assertEquals("TRIP", rec.kind); assertEquals("acc", rec.accountId); assertEquals(SEOUL_0830 + 600_000, rec.occurredAt)
        assertEquals(12_034_000_000L, rec.startMm); assertEquals(35_000L, rec.bonusMilli); assertEquals(true, rec.valid)
        val payload = Json.decodeFromString<TripPayload>(rec.payloadJson)
        assertEquals("2026-11-10T08:30:00+09:00", payload.startedAt)
        assertEquals("2026-11-10T08:40:00+09:00", payload.endedAt)
        assertEquals("VALID", payload.distanceStatus)
        assertEquals(1, healthCalls)
    }

    @Test fun `missing odometer is unverified`() = runTest {
        r.onEvent(profile, TripEnded(summary(endKm = null), 0))
        val payload = Json.decodeFromString<TripPayload>(store.records("p").first().single().payloadJson)
        assertEquals("UNVERIFIED", payload.distanceStatus)
    }

    @Test fun `same trip end twice stores once`() = runTest {
        val end = TripEnded(summary(), 0)
        assertNotNull(r.onEvent(profile, end)); assertNull(r.onEvent(profile, end))
        assertEquals(1, store.records("p").first().size); assertEquals(1, healthCalls)
    }

    private fun restarted() = TripRecorder(store, { HealthResult(null, MeasurementStatus.UNMEASURED) }, { now }) // 앱 재시작: 메모리 상태 없음
    private suspend fun interrupted() = store.interruptedTrips("p").first()

    @Test fun `interrupted trip is kept without exp`() = runTest {
        r.onTrip(profile, inProgress()); restarted().recoverOnStart("p")
        assertNull(store.inProgress("p")); assertEquals(listOf(0L), interrupted().map { it.startedAt })
        assertTrue(store.records("p").first().isEmpty())
    }

    @Test fun `interrupted trip survives a following new trip`() = runTest {
        now = 5_000; r.onTrip(profile, summary(endKm = 12040.5, endedAt = null)); restarted().recoverOnStart("p") // 앱이 죽었다 다시 열림
        now = 600_000; r.onTrip(profile, inProgress(startedAt = 500_000)) // 계속 주행해 새 트립 시작
        assertEquals(500_000L, store.inProgress("p")!!.startedAt)
        val kept = interrupted().single()
        assertEquals(0L, kept.startedAt); assertEquals(6.5, kept.distanceKm!!, 1e-9); assertEquals(5_000L, kept.interruptedAt)
    }

    @Test fun `old in progress row is kept when a new trip starts before recovery ran`() = runTest {
        val summaryJson = """{"startedAt":0,"endedAt":null,"startOdometerKm":1.0,"endOdometerKm":3.0,"sharpSteerCount":0,""" +
            """"brakeCount":0,"brakeHoldTotalMs":0,"turnSignalCount":0,"bigTurnCount":0,"turnWithoutSignalCount":0,"missingData":[]}"""
        store.saveInProgress(TripInProgressEntity("p", 0, summaryJson, "{}", false, 1_000))
        now = 600_000; r.onTrip(profile, inProgress(startedAt = 500_000))
        assertEquals(500_000L, store.inProgress("p")!!.startedAt)
        assertEquals(2.0, interrupted().single().distanceKm!!, 1e-9)
    }

    @Test fun `recovery leaves the trip being recorded alone`() = runTest {
        r.onTrip(profile, inProgress()); r.onTrip(profile, inProgress())
        r.recoverOnStart("p")
        assertEquals(0L, store.inProgress("p")!!.startedAt); assertTrue(interrupted().isEmpty())
    }

    @Test fun `legacy interrupted row is moved to the interrupted table`() = runTest {
        store.saveInProgress(TripInProgressEntity("p", 7, "{}", "{}", true, 1_000))
        r.recoverOnStart("p")
        assertNull(store.inProgress("p")); assertEquals(null, interrupted().single().distanceKm)
    }

    @Test fun `source switch mid trip interrupts`() = runTest {
        now = 60_000; r.onTrip(profile, inProgress()); r.onTrip(profile, null)
        assertNull(store.inProgress("p")); assertEquals(listOf(0L), interrupted().map { it.startedAt })
        assertNull(interrupted().single().distanceKm) // 진행 중 요약에 도착 누적거리가 없으면 거리 미상
    }

    @Test fun `late trip end settles an interrupted trip and drops it from the list`() = runTest {
        now = 60_000; r.onTrip(profile, inProgress()); r.onTrip(profile, null)
        assertNotNull(r.onEvent(profile, TripEnded(summary(), 60_000)))
        assertTrue(interrupted().isEmpty()); assertEquals(1, store.records("p").first().size)
    }

    @Test fun `trip under 30 seconds is cleared when it disappears`() = runTest {
        now = 20_000; r.onTrip(profile, inProgress()); r.onTrip(profile, null)
        assertNull(store.inProgress("p")); assertTrue(interrupted().isEmpty())
    }

    @Test fun `null after 40 seconds of wall clock interrupts even if seen only once`() = runTest {
        r.onTrip(profile, inProgress()); now = 40_000; r.onTrip(profile, null)
        assertNull(store.inProgress("p")); assertEquals(1, interrupted().size)
    }

    @Test fun `stale trip end does not clear a newer trip row`() = runTest {
        now = 600_000; r.onTrip(profile, inProgress(startedAt = 500_000))
        assertNotNull(r.onEvent(profile, TripEnded(summary(startedAt = 0), 0))) // 옛 트립 늦은 종료
        assertEquals(500_000L, store.inProgress("p")!!.startedAt)
        assertNull(r.onEvent(profile, TripEnded(summary(startedAt = 0), 0))) // 중복 경로도 동일
        assertEquals(500_000L, store.inProgress("p")!!.startedAt)
    }

    @Test fun `trip end after null still settles and clears in progress`() = runTest {
        now = 60_000; r.onTrip(profile, inProgress()); r.onTrip(profile, null)
        assertNotNull(r.onEvent(profile, TripEnded(summary(), 60_000)))
        assertNull(store.inProgress("p"))
    }

    @Test fun `null after trip end does not interrupt`() = runTest {
        now = 60_000; r.onTrip(profile, inProgress())
        r.onEvent(profile, TripEnded(summary(), 60_000)); r.onTrip(profile, null)
        assertNull(store.inProgress("p"))
    }

    @Test fun `in progress saved first then at most every 10 seconds`() = runTest {
        r.onTrip(profile, inProgress())
        assertEquals(0L, store.inProgress("p")!!.updatedAt)
        now = 9_999; r.onTrip(profile, inProgress())
        assertEquals(0L, store.inProgress("p")!!.updatedAt)
        now = 10_000; r.onTrip(profile, inProgress())
        assertEquals(10_000L, store.inProgress("p")!!.updatedAt)
    }

    @Test fun `zero speed all trip is unmeasured`() = runTest {
        r.onTrip(profile, inProgress())
        r.onState(VehicleState(), speed(0.0, 0)); r.onState(speed(0.0, 0), speed(0.0, 1_000))
        val out = r.onEvent(profile, TripEnded(summary(), 0)) as Settled
        assertEquals(MeasurementStatus.UNMEASURED, line(out.settlement, SafetyItem.ACCEL_BRAKE).status)
    }

    @Test fun `speed changes count harsh accel`() = runTest {
        r.onTrip(profile, inProgress())
        r.onState(VehicleState(), speed(0.0, 0)); r.onState(speed(0.0, 0), speed(15.0, 1_000))
        val out = r.onEvent(profile, TripEnded(summary(), 0)) as Settled
        val l = line(out.settlement, SafetyItem.ACCEL_BRAKE)
        assertEquals(MeasurementStatus.MEASURED, l.status); assertEquals(31, l.score)
    }

    @Test fun `speed drop counts harsh decel`() = runTest {
        r.onTrip(profile, inProgress())
        r.onState(VehicleState(), speed(15.0, 0)); r.onState(speed(15.0, 0), speed(0.0, 1_000))
        val out = r.onEvent(profile, TripEnded(summary(), 0)) as Settled
        assertEquals(31, line(out.settlement, SafetyItem.ACCEL_BRAKE).score)
    }

    /** (v, atMs) 표본을 차례로 넣고 (급가속, 급감속) 횟수를 진행 저장 행에서 읽는다. */
    private var harshTrip = 0L

    private suspend fun harsh(vararg samples: Pair<Double, Long>): Pair<Int, Int> {
        harshTrip += 1_000_000; now = harshTrip; r.onTrip(profile, inProgress(harshTrip)) // 호출마다 새 트립
        samples.forEach { (v, at) -> r.onState(VehicleState(), speed(v, at)) }
        now += 10_000; r.onTrip(profile, inProgress(harshTrip)) // 10초 지나면 카운터를 다시 저장한다
        val c = Json.parseToJsonElement(store.inProgress("p")!!.metricsJson).jsonObject
        // 0인 값은 JSON에서 빠진다(기본값)
        return (c["harshAccel"]?.jsonPrimitive?.int ?: 0) to (c["harshDecel"]?.jsonPrimitive?.int ?: 0)
    }

    @Test fun `eTAS accel threshold is 11 kmh per second`() = runTest {
        assertEquals(1 to 0, harsh(0.0 to 0L, 11.0 to 1_000L))
        assertEquals(0 to 0, harsh(0.0 to 0L, 10.9 to 1_000L))
    }

    @Test fun `eTAS decel threshold is 7_5 kmh per second`() = runTest {
        assertEquals(0 to 1, harsh(20.0 to 0L, 12.5 to 1_000L))
        assertEquals(0 to 0, harsh(20.0 to 0L, 12.6 to 1_000L))
    }

    @Test fun `interval under 1 second is treated as 1 second`() = runTest {
        assertEquals(0 to 0, harsh(0.0 to 0L, 8.0 to 500L)) // 8 / 1s = 8
        assertEquals(1 to 0, harsh(0.0 to 0L, 30.0 to 500L))
    }

    @Test fun `interval over 2 seconds is skipped but exactly 2 seconds is judged`() = runTest {
        assertEquals(0 to 0, harsh(0.0 to 0L, 40.0 to 2_500L))
        assertEquals(1 to 0, harsh(0.0 to 0L, 22.0 to 2_000L)) // 22 / 2s = 11
    }

    /** 0.2초마다 [step]km/h씩 바뀌는 표본 [n]+1개(시작 [from]km/h, 시각 [t0]ms부터). 차속은 바뀔 때마다 촘촘히 들어온다. */
    private fun dense(from: Double, step: Double, n: Int, t0: Long = 0L) = (0..n).map { i -> (from + step * i) to (t0 + i * 200L) }

    @Test fun `dense samples are judged against the speed about 1 second earlier`() = runTest {
        assertEquals(1 to 0, harsh(*dense(0.0, 3.0, 20).toTypedArray())) // 초당 15km/h로 4초 → 이어진 급가속 1회
        assertEquals(0 to 0, harsh(*dense(0.0, 2.0, 20).toTypedArray())) // 초당 10km/h → 아님
        assertEquals(0 to 1, harsh(*dense(60.0, -6.0, 10).toTypedArray())) // 초당 30km/h 감속 → 1회
    }

    @Test fun `separate harsh accels count separately`() = runTest {
        val samples = dense(0.0, 3.0, 10) + dense(30.0, 0.0, 10, 2_200L) + dense(30.0, 3.0, 10, 4_400L)
        assertEquals(2 to 0, harsh(*samples.toTypedArray())) // 급가속 → 2초 정속 → 급가속
    }

    @Test fun `demo levelup scenario gives exactly one harsh accel and no harsh decel`() = runTest {
        val root = Json.parseToJsonElement(File("src/main/assets/scenarios/demo_levelup.json").readText()).jsonObject
        val factor = root["speed"]!!.jsonPrimitive.double
        val samples = mutableListOf(root["start"]!!.jsonObject["speedKmh"]!!.jsonPrimitive.double to 0L)
        for (step in root["steps"]!!.jsonArray.map { it.jsonObject }.sortedBy { it["t"]!!.jsonPrimitive.double }) {
            val v = step["set"]!!.jsonObject["speedKmh"]?.jsonPrimitive?.double ?: continue
            samples += v to (step["t"]!!.jsonPrimitive.double * 1000 / factor).toLong()
        }
        assertEquals(1 to 0, harsh(*samples.toTypedArray()))
    }

    @Test fun `steering in missing data is unmeasured`() = runTest {
        val out = r.onEvent(profile, TripEnded(summary().copy(missingData = setOf("steering")), 0)) as Settled
        assertEquals(MeasurementStatus.UNMEASURED, line(out.settlement, SafetyItem.STEERING).status)
    }

    @Test fun `slow speed samples and long brakes`() = runTest {
        r.onTrip(profile, inProgress())
        r.onState(VehicleState(), speed(0.0, 0)); r.onState(speed(0.0, 0), speed(15.0, 3_000)) // 간격 > 2초: 판정 안 함
        r.onEvent(profile, BrakeReleased(3_000, 0)); r.onEvent(profile, BrakeReleased(2_999, 0))
        val out = r.onEvent(profile, TripEnded(summary(), 0)) as Settled
        assertEquals(34, line(out.settlement, SafetyItem.ACCEL_BRAKE).score)
    }

    @Test fun `energy start and end feed efficiency and reset per trip`() = runTest {
        r.onTrip(profile, inProgress(0))
        r.onState(VehicleState(), energy(90)); r.onState(energy(90), energy(80)) // 12km / (10% x 60kWh) = 2 km/kWh
        val out = r.onEvent(profile, TripEnded(summary(startedAt = 0), 0)) as Settled
        assertEquals(12, line(out.settlement, SafetyItem.EFFICIENCY).score)

        r.onTrip(profile, inProgress(500_000)) // 새 트립: 잔량은 출발 시점 값(80)에서 시작
        r.onState(energy(80), energy(75))
        val out2 = r.onEvent(profile, TripEnded(summary(startKm = 12046.0, endKm = 12058.0, startedAt = 500_000), 0)) as Settled
        assertEquals(24, line(out2.settlement, SafetyItem.EFFICIENCY).score) // 12km / (5% x 60kWh) = 4 km/kWh
    }
}
