package com.drivepet.app.data

import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.HealthResult
import com.drivepet.app.domain.MeasurementStatus
import com.drivepet.app.domain.TripMetrics
import com.drivepet.app.domain.settle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadsTest {
    private fun metrics(steeringMeasured: Boolean) = TripMetrics(
        0, 12_000_000, false, 0, 0, 0, steeringMeasured, 0, 0, 0, FuelType.ICE, null, null, null,
    )

    private fun trip(id: String, at: Long, valid: Boolean, score: Int) = RecordEntity(
        recordId = id, profileId = "p", accountId = null, kind = "TRIP", occurredAt = at, valid = valid,
        payloadJson = Json.encodeToString(
            TripPayload(
                "s", "e", null, null, if (valid) "VALID" else "UNVERIFIED",
                settle(metrics(true), HealthResult(null, MeasurementStatus.UNMEASURED)).toJson().copy(safetyScore = score),
            ),
        ),
    )

    @Test fun `unmeasured settlement sends safetyBreakdown null but domain keeps 5 lines`() {
        val s = settle(metrics(steeringMeasured = false), HealthResult(null, MeasurementStatus.UNMEASURED))
        assertEquals(5, s.breakdown!!.size)
        val json = Json.encodeToString(s.toJson())
        assertTrue(json, json.contains("\"safetyBreakdown\":null"))
        assertTrue(json, json.contains("\"safetyScore\":null"))
    }

    @Test fun `measured settlement keeps breakdown`() {
        val json = Json.encodeToString(settle(metrics(steeringMeasured = true), HealthResult(null, MeasurementStatus.UNMEASURED)).toJson())
        assertTrue(json, json.contains("\"safetyBreakdown\":[{"))
    }

    @Test fun `recent trip safety skips unverified trips`() {
        val records = listOf(trip("a", 1, true, 90), trip("b", 2, false, 10), trip("c", 3, true, 80))
        assertEquals(listOf(90, 80), records.recentTripSafety())
    }

    @Test fun missionTripReadsBreakdown() {
        val measured = TripPayload(
            "s", "e", 0, 12_000_000, "VALID",
            SettlementJson(
                12_000_000, "24.000", "35.000", 95, "MEASURED",
                listOf(
                    SafetyLineJson("ACCEL_BRAKE", 36, 36, "MEASURED", null),
                    SafetyLineJson("STEERING", 10, 10, "MEASURED", 0.0),
                    SafetyLineJson("TURN_SIGNAL", 9, 9, "MEASURED", 100.0),
                ),
                null, "UNMEASURED",
            ),
        )
        val r = RecordEntity("t1", "p", null, "TRIP", 0L, payloadJson = Json.encodeToString(measured), valid = true)
        val m = r.missionTrip()!!
        assertEquals(0, m.sharpSteers)
        assertEquals(true, m.accelBrakeFull)
        assertEquals(100.0, m.turnSignalPct!!, 0.0)
        assertEquals(95, m.safetyScore)
        val none = r.copy(payloadJson = Json.encodeToString(measured.copy(settlement = measured.settlement.copy(safetyBreakdown = null, safetyScore = null))))
        val n = none.missionTrip()!!
        assertEquals(null, n.sharpSteers)
        assertEquals(null, n.accelBrakeFull)
        assertEquals(null, n.turnSignalPct)
    }
}
