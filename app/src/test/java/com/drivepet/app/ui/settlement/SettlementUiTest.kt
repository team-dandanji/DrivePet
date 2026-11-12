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

    @Test fun `reward rows follow the figma four lines`() {
        val rows = rewardRows(settlement(24_000L, 50_000L), 74_000L)
        assertEquals(4, rows.size)
        assertEquals("거리 12km × 2EXP", rows[0].first)
        assertEquals("+24.0EXP", rows[0].second)
        assertEquals("급조향", rows[1].first)
        assertEquals("미측정", rows[1].second)
        assertEquals("공조 중복", rows[2].first)
        assertEquals("방향지시등 80% 이상", rows[3].first)
        assertEquals("미측정", rewardRows(settlement(null, 0L), 0L)[0].second)
    }

    @Test fun `measured steering row shows count and bonus`() {
        val s = settlement(24_000L, 15_000L).copy(breakdown = listOf(SafetyLine(SafetyItem.STEERING, 10, MeasurementStatus.MEASURED, 0.0)))
        val row = rewardRows(s, 39_000L)[1]
        assertEquals("급조향 0회", row.first)
        assertEquals("+15EXP", row.second)
    }
}
