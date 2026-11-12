package com.drivepet.app.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpTest {
    private val t0 = Instant.parse("2026-11-10T00:00:00Z")
    private val t1 = Instant.parse("2026-11-10T01:00:00Z")
    private val t2 = Instant.parse("2026-11-10T02:00:00Z")

    @Test fun `12km trip from 370 exp levels up to 444`() {
        val seed = MissionLedger("seed", t0, "seed", "ONCE", 370_000L)
        val trip = TripLedger("t1", t1, 12_034_000_000L, 12_046_000_000L, valid = true, bonusMilli = 50_000L)
        val total = totalExpMilli(listOf(seed, trip))
        assertEquals(444_000L, total)
        assertEquals(Gauge(2, 44_000L, 500_000L, 8), Growth.gauge(total))
    }

    @Test fun `gauge levels`() {
        assertEquals(Gauge(1, 370_000L, 400_000L, 92), Growth.gauge(370_000L))
        assertEquals(3, Growth.level(900_000L))
        assertEquals(Gauge(3, 0L, null, 100), Growth.gauge(900_000L))
    }

    @Test fun `overlapping trips pay distance once and bonus once`() {
        val a = TripLedger("a", t1, 0L, 10_000_000L, true, 50_000L)
        val b = TripLedger("b", t2, 5_000_000L, 15_000_000L, true, 50_000L)
        assertEquals(30_000L + 50_000L, totalExpMilli(listOf(a, b)))
    }

    @Test fun `rounding half up once on union`() {
        assertEquals(1L, drivingExpMilli(listOf(0L..250L)))
        assertEquals(0L, drivingExpMilli(listOf(0L..249L)))
    }

    @Test fun `short or invalid trips get no bonus`() {
        val short = TripLedger("s", t1, 0L, 999_999L, true, 50_000L)
        assertEquals(2_000L, totalExpMilli(listOf(short))) // 999_999mm/500 = 1999.998 -> 2000
        val exact = TripLedger("e", t1, 0L, 1_000_000L, true, 50_000L)
        assertEquals(2_000L + 50_000L, totalExpMilli(listOf(exact)))
        val invalid = TripLedger("i", t1, 0L, 10_000_000L, false, 50_000L)
        assertEquals(0L, totalExpMilli(listOf(invalid)))
        val noDistance = TripLedger("n", t1, null, 10_000_000L, true, 50_000L)
        assertEquals(0L, totalExpMilli(listOf(noDistance)))
        val backwards = TripLedger("b", t1, 10_000_000L, 0L, true, 50_000L)
        assertEquals(0L, totalExpMilli(listOf(backwards)))
    }

    @Test fun `attendance once per seoul date`() {
        val before = Instant.parse("2026-11-10T14:59:00Z")
        val after = Instant.parse("2026-11-10T15:01:00Z")
        assertEquals(LocalDate.of(2026, 11, 10), seoulDate(before))
        assertEquals(LocalDate.of(2026, 11, 11), seoulDate(after))
        val records = listOf(
            AttendanceLedger("a1", before, seoulDate(before)),
            AttendanceLedger("a2", after, seoulDate(after)),
            AttendanceLedger("a3", after.plusSeconds(60), seoulDate(after.plusSeconds(60))),
        )
        assertEquals(20_000L, totalExpMilli(records))
    }

    @Test fun `replacement and mission dedupe`() {
        val d = LocalDate.of(2026, 11, 10)
        val rep = listOf(
            ReplacementLedger("r1", t0, "oil", d), ReplacementLedger("r2", t1, "oil", d),
            ReplacementLedger("r3", t1, "tire", d), ReplacementLedger("r4", t2, "oil", d.plusDays(1)),
        )
        assertEquals(3 * 50_000L, totalExpMilli(rep))
        val mis = listOf(
            MissionLedger("m1", t0, "x", "2026-W46", 30_000L), MissionLedger("m2", t1, "x", "2026-W46", 30_000L),
            MissionLedger("m3", t1, "x", "2026-W47", 30_000L),
        )
        assertEquals(2 * 30_000L, totalExpMilli(mis))
    }

    @Test fun `format`() {
        assertEquals("370.0", formatExp(370_000L))
        assertEquals("0.1", formatExp(50L))
        assertEquals("370.000", apiExp(370_000L))
    }

    @Test fun `gauge text drops trailing zero only`() {
        assertEquals("200 / 500 EXP", gaugeExpText(200_000L, 500_000L))
        assertEquals("200.5 / 500 EXP", gaugeExpText(200_500L, 500_000L))
    }

    @Test fun `tripPaidMilli gives each trip its share and matches the prefix-difference of totalExpMilli`() {
        val m = 1_000_000L
        val trips = listOf(
            TripLedger("c", t2, 3 * m, 8 * m, true, 20_000L),          // 앞 구간과 겹침: 거리 증가분만, 보너스 없음
            TripLedger("a", t0, 0L, 10 * m, true, 50_000L),
            TripLedger("x", Instant.parse("2026-11-10T00:30:00Z"), null, null, false, 15_000L), // 거리 미확인: 0
            TripLedger("b", t1, 9 * m, 14 * m, true, 35_000L),          // a와 겹침: 거리 증가분만
            TripLedger("d", Instant.parse("2026-11-10T03:00:00Z"), 20 * m, 20 * m + 400_000L, true, 40_000L), // 1km 미만: 보너스 없음
            TripLedger("e", Instant.parse("2026-11-10T04:00:00Z"), 30 * m, 32 * m, true, 10_000L),
        )
        val paid = tripPaidMilli(trips)

        // 이전 구현: 오래된 순 접두 합의 차이
        val ordered = trips.sortedWith(compareBy({ it.occurredAt }, { it.recordId }))
        var before = 0L
        val expected = ordered.mapIndexed { i, t ->
            val total = totalExpMilli(ordered.take(i + 1))
            (t.recordId to total - before).also { before = total }
        }.toMap()
        assertEquals(trips.map { expected[it.recordId] }, paid)
        assertEquals(mapOf("a" to 70_000L, "x" to 0L, "b" to 8_000L, "c" to 0L, "d" to 800L, "e" to 14_000L), trips.map { it.recordId }.zip(paid).toMap())
        assertEquals(totalExpMilli(trips), paid.sum())
    }
}
