package com.drivepet.app.domain

import com.drivepet.app.domain.SafetyItem.ACCEL_BRAKE
import com.drivepet.app.domain.SafetyItem.EFFICIENCY
import com.drivepet.app.domain.SafetyItem.HVAC_OVERLAP
import com.drivepet.app.domain.SafetyItem.STEERING
import com.drivepet.app.domain.SafetyItem.TURN_SIGNAL
import com.drivepet.app.domain.MeasurementStatus.MEASURED
import com.drivepet.app.domain.MeasurementStatus.UNMEASURED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {
    private val healthUnmeasured = HealthResult(null, UNMEASURED)
    private val h = healthUnmeasured

    private fun metrics(
        start: Long? = 12_034_000_000,
        end: Long? = 12_046_000_000,
        speedMeasured: Boolean = false,
        harshAccel: Int = 0,
        harshDecel: Int = 0,
        longBrakes: Int = 0,
        steeringMeasured: Boolean = true,
        sharpSteers: Int = 0,
        bigTurns: Int = 0,
        turnsWithoutSignal: Int = 0,
        fuelType: FuelType = FuelType.EV,
        energyStartPct: Int? = null,
        energyEndPct: Int? = null,
        batteryKwh: Double? = null,
    ) = TripMetrics(
        start, end, speedMeasured, harshAccel, harshDecel, longBrakes,
        steeringMeasured, sharpSteers, bigTurns, turnsWithoutSignal,
        fuelType, energyStartPct, energyEndPct, batteryKwh,
    )

    private fun Settlement.line(item: SafetyItem) = breakdown!!.single { it.item == item }

    @Test fun `commute without speed or energy`() {
        val s = settle(metrics(speedMeasured = false, sharpSteers = 1, bigTurns = 3, turnsWithoutSignal = 2), healthUnmeasured)
        assertEquals(12_000_000L, s.distanceMm); assertEquals(24_000L, s.baseDrivingExpMilli)
        assertEquals(UNMEASURED, s.line(ACCEL_BRAKE).status); assertNull(s.line(ACCEL_BRAKE).score)
        assertEquals(5, s.line(STEERING).score); assertEquals(3, s.line(TURN_SIGNAL).score) // 10-5, round(9*33.33/100)
        assertEquals(42, s.safetyScore) // round((5+3)/(10+9)*100)
        assertEquals(0L, s.bonusMilli)
    }

    @Test fun `perfect ev trip earns 35 bonus`() {
        // 12km, 64%->63%, 77.4kWh -> 15.5 km/kWh -> 30
        val s = settle(
            metrics(speedMeasured = true, sharpSteers = 0, bigTurns = 3, turnsWithoutSignal = 0,
                energyStartPct = 64, energyEndPct = 63, batteryKwh = 77.4),
            healthUnmeasured,
        )
        assertEquals(30, s.line(EFFICIENCY).score)
        assertEquals(100, s.safetyScore); assertEquals(35_000L, s.bonusMilli)
        assertEquals(UNMEASURED, s.line(HVAC_OVERLAP).status)
    }

    @Test fun `missing or reversed odometer is unverified`() {
        listOf(metrics(start = null, end = 5), metrics(start = 10, end = 5)).forEach {
            assertFalse(settle(it, h).valid); assertNull(settle(it, h).distanceMm); assertNull(settle(it, h).baseDrivingExpMilli)
        }
    }

    @Test fun `trip over 1000 km is unverified`() {
        assertTrue(settle(metrics(start = 0, end = 1_000_000_000), h).valid) // 정확히 1,000km
        val s = settle(metrics(start = 0, end = 60_703_000_000), h)
        assertFalse(s.valid); assertNull(s.distanceMm); assertNull(s.baseDrivingExpMilli)
    }

    @Test fun `nothing measured gives null score`() {
        val s = settle(metrics(steeringMeasured = false, speedMeasured = false, fuelType = FuelType.ICE, bigTurns = 0), h)
        assertNull(s.safetyScore); assertEquals(UNMEASURED, s.safetyStatus)
        assertEquals(5, s.breakdown!!.size)
        assertEquals(0L, s.bonusMilli)
    }

    @Test fun `max scores sum to 100 and formulas use them`() {
        assertEquals(100, SafetyItem.values().sumOf { it.maxScore })
        val s = settle(metrics(speedMeasured = true, harshAccel = 1, harshDecel = 1, longBrakes = 1, sharpSteers = 1), h)
        assertEquals(36 - 10 - 2, s.line(ACCEL_BRAKE).score); assertEquals(10 - 5, s.line(STEERING).score)
    }

    @Test fun `acceleration and steering scores floor at zero`() {
        val s = settle(metrics(speedMeasured = true, harshAccel = 4, harshDecel = 3, longBrakes = 2, sharpSteers = 5), h)
        assertEquals(0, s.line(ACCEL_BRAKE).score); assertEquals(0, s.line(STEERING).score)
        assertEquals(5.0, s.line(STEERING).value!!, 0.0)
        assertEquals(0, s.safetyScore)
    }

    @Test fun `steering not measured is unmeasured without bonus`() {
        val s = settle(metrics(steeringMeasured = false, speedMeasured = true), h)
        assertNull(s.line(STEERING).score); assertEquals(UNMEASURED, s.line(STEERING).status); assertNull(s.line(STEERING).value)
        assertEquals(0L, s.bonusMilli)
        assertEquals(100, s.safetyScore)
        assertEquals(MEASURED, s.safetyStatus)
    }

    @Test fun `turn signal ratio clamps at zero and bonus needs 80 percent`() {
        val over = settle(metrics(bigTurns = 2, turnsWithoutSignal = 5), h)
        assertEquals(0, over.line(TURN_SIGNAL).score); assertEquals(0.0, over.line(TURN_SIGNAL).value!!, 0.0)
        val eighty = settle(metrics(sharpSteers = 1, bigTurns = 5, turnsWithoutSignal = 1), h)
        assertEquals(80.0, eighty.line(TURN_SIGNAL).value!!, 0.0)
        assertEquals(20_000L, eighty.bonusMilli)
        assertEquals(0L, settle(metrics(sharpSteers = 1, bigTurns = 5, turnsWithoutSignal = 2), h).bonusMilli) // 60%
    }

    @Test fun `efficiency unmeasured for ice hev missing capacity or no energy drop`() {
        val base = metrics(energyStartPct = 64, energyEndPct = 63, batteryKwh = 77.4)
        listOf(
            base.copy(fuelType = FuelType.ICE), base.copy(fuelType = FuelType.HEV),
            base.copy(batteryKwh = null), base.copy(batteryKwh = 0.0), base.copy(batteryKwh = -77.4), base.copy(energyEndPct = 64), base.copy(energyEndPct = 70),
        ).forEach {
            assertEquals(UNMEASURED, settle(it, h).line(EFFICIENCY).status)
            assertNull(settle(it, h).line(EFFICIENCY).score)
        }
    }

    @Test fun `efficiency unmeasured when distance is zero or unverified`() {
        val energy = metrics(energyStartPct = 64, energyEndPct = 63, batteryKwh = 77.4)
        listOf(energy.copy(startMm = 10, endMm = 10), energy.copy(startMm = 10, endMm = 5)).forEach {
            val line = settle(it, h).line(EFFICIENCY)
            assertEquals(UNMEASURED, line.status); assertNull(line.score); assertNull(line.value)
        }
    }

    @Test fun `low efficiency scales score`() {
        // 12km / (10% of 60kWh = 6kWh) = 2 km/kWh -> round(30*2/5) = 12
        val s = settle(metrics(energyStartPct = 70, energyEndPct = 60, batteryKwh = 60.0), h)
        assertEquals(12, s.line(EFFICIENCY).score); assertEquals(2.0, s.line(EFFICIENCY).value!!, 1e-9)
    }

    @Test fun `invalid trip still scores but has no distance exp`() {
        val s = settle(metrics(start = 10, end = 5, sharpSteers = 0, bigTurns = 3), h)
        assertFalse(s.valid); assertTrue(s.bonusMilli > 0); assertNull(s.baseDrivingExpMilli)
        assertEquals(MEASURED, s.safetyStatus)
    }

    @Test fun `health comes straight from argument`() {
        val s = settle(metrics(), HealthResult(88, MEASURED))
        assertEquals(88, s.healthScore); assertEquals(MEASURED, s.healthStatus)
    }
}
