package com.drivepet.app.ui.settlement

import com.drivepet.app.domain.MeasurementStatus
import com.drivepet.app.domain.SafetyItem
import com.drivepet.app.domain.SafetyLine
import com.drivepet.app.domain.Settlement
import org.junit.Assert.assertEquals
import org.junit.Test

class SettlementUiTest {
    private fun settlement(base: Long?, bonus: Long) = Settlement(
        12_000_000L, true, base, bonus, null, MeasurementStatus.UNMEASURED, null, null, MeasurementStatus.UNMEASURED,
    )

    @Test fun `bonus withheld when ledger paid less than settlement`() {
        assertEquals(false, bonusWithheld(74_000L, settlement(24_000L, 50_000L)))
        assertEquals(true, bonusWithheld(24_000L, settlement(24_000L, 50_000L)))
        assertEquals(false, bonusWithheld(24_000L, settlement(24_000L, 0L)))
        assertEquals(true, bonusWithheld(0L, settlement(24_000L, 0L)))
    }

    @Test fun `driving exp text follows what the ledger paid`() {
        assertEquals("+24.0", drivingExpText(settlement(24_000L, 35_000L), 59_000L))
        assertEquals("+24.0", drivingExpText(settlement(24_000L, 0L), 24_000L))
        assertEquals("+0.0", drivingExpText(settlement(24_000L, 35_000L), 0L)) // 겹친 구간: 지급 0
        assertEquals("+10.0", drivingExpText(settlement(24_000L, 35_000L), 10_000L))
        assertEquals("미측정", drivingExpText(settlement(null, 0L), 0L))
    }

    @Test fun `steering bonus text`() {
        val m = SafetyItem.STEERING.maxScore
        assertEquals("+15", steeringBonusText(SafetyLine(SafetyItem.STEERING, m, MeasurementStatus.MEASURED, 0.0)))
        assertEquals("-", steeringBonusText(SafetyLine(SafetyItem.STEERING, m - 5, MeasurementStatus.MEASURED, 1.0)))
        assertEquals("0", steeringBonusText(SafetyLine(SafetyItem.STEERING, m, MeasurementStatus.MEASURED, 0.0), withheld = true))
        assertEquals("미측정", steeringBonusText(SafetyLine(SafetyItem.STEERING, null, MeasurementStatus.UNMEASURED, null)))
        assertEquals("미측정", steeringBonusText(null))
    }

    @Test fun `signal bonus text`() {
        fun line(v: Double?, st: MeasurementStatus = MeasurementStatus.MEASURED) = SafetyLine(SafetyItem.TURN_SIGNAL, 8, st, v)
        assertEquals("+20", signalBonusText(line(80.0)))
        assertEquals("-", signalBonusText(line(79.9)))
        assertEquals("0", signalBonusText(line(80.0), withheld = true))
        assertEquals("미측정", signalBonusText(line(null, MeasurementStatus.UNMEASURED)))
    }
}
