package com.drivepet.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MaintenanceTest {
    private fun km(k: Int) = k * 1_000_000L
    private val none = CareBaseline(null, null, null)

    @Test fun latestReplacementWithOdometerWins() {
        val reps = listOf(
            Replacement("r1", Instant.parse("2026-08-04T01:00:00Z"), km(20_000)),
            Replacement("r2", Instant.parse("2026-10-01T01:00:00Z"), null),
        )
        val life = careLife(CareItem.TIRE, CareBaseline(km(15_000), null, null), reps, km(24_800))
        assertEquals(km(20_000), life.baseOdometerMm)
        assertEquals(4_800, life.sinceKm)
        assertEquals(5_200, life.remainingKm)
        assertEquals(52, life.lifePct)
        assertEquals("2026.10.01", life.lastServiced) // 마지막 관리일은 가장 늦은 교체(누적거리 없어도)
    }

    @Test fun sameTimeTieBreaksByRecordId() {
        val at = Instant.parse("2026-08-04T01:00:00Z")
        val reps = listOf(Replacement("b", at, km(21_000)), Replacement("a", at, km(20_000)))
        assertEquals(km(21_000), careLife(CareItem.TIRE, none, reps, km(24_800)).baseOdometerMm)
    }

    @Test fun fallsBackToBaselineThenUnknown() {
        assertEquals(52, careLife(CareItem.TIRE, CareBaseline(km(20_000), null, null), emptyList(), km(24_800)).lifePct)
        val period = careLife(CareItem.CABIN_FILTER, CareBaseline(null, null, ServicedPeriod.ABOUT_6_MONTHS), emptyList(), km(24_800))
        assertNull(period.lifePct)
        assertEquals("6개월 전쯤", period.lastServiced)
        val unregistered = careLife(CareItem.WIPER, none, emptyList(), km(24_800))
        assertNull(unregistered.lastServiced)
        assertNull(unregistered.lifePct)
        assertEquals("2026.08.04", careLife(CareItem.WIPER, CareBaseline(null, LocalDate.of(2026, 8, 4), null), emptyList(), null).lastServiced)
    }

    @Test fun unreadableOrReversedOdometerIsNotComputed() {
        val base = CareBaseline(km(20_000), null, null)
        assertNull(careLife(CareItem.TIRE, base, emptyList(), null).lifePct)
        assertNull(careLife(CareItem.TIRE, base, emptyList(), km(19_000)).lifePct)
        val over = careLife(CareItem.TIRE, base, emptyList(), km(35_000))
        assertEquals(0, over.remainingKm)
        assertEquals(0, over.lifePct)
    }

    @Test fun freshReplacementIsFull() {
        val reps = listOf(Replacement("r", Instant.parse("2026-11-04T01:00:00Z"), km(24_800)))
        assertEquals(100, careLife(CareItem.CABIN_FILTER, none, reps, km(24_800)).lifePct)
    }

    @Test fun inspectionDdayUsesSeoulDate() {
        assertEquals(28, inspectionDaysLeft(LocalDate.of(2026, 12, 2), seoulDate(Instant.parse("2026-11-03T15:30:00Z"))))
        assertNull(inspectionDaysLeft(null, LocalDate.of(2026, 11, 4)))
    }

    @Test fun itemsByFuel() {
        assertEquals(listOf(CareItem.TIRE, CareItem.CABIN_FILTER, CareItem.WIPER), careItemsFor(FuelType.EV))
        val hev = careItemsFor(FuelType.HEV)
        assertEquals(4, hev.size)
        assertEquals(CareItem.ENGINE_OIL, hev.first())
        assertEquals(10.0, careScore(FuelType.EV), 0.0)
        assertEquals(7.5, careScore(FuelType.ICE), 0.0)
    }
}
