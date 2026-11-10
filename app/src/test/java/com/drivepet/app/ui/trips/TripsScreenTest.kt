package com.drivepet.app.ui.trips

import com.drivepet.app.data.SettlementJson
import com.drivepet.app.data.TripPayload
import com.drivepet.app.data.db.InterruptedTripEntity
import com.drivepet.app.data.db.RecordEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TripsScreenTest {
    // 2026-11-10 06:05:00Z = 서울 15:05
    private val at = 1_794_290_700_000L

    private fun trip(
        id: String, occurredAt: Long, status: String, startMm: Long?, endMm: Long?, base: String?, bonus: String, score: Int?,
    ): RecordEntity {
        val distance = if (startMm != null && endMm != null) endMm - startMm else null
        val p = TripPayload(
            "s", "e", startMm, endMm, status,
            SettlementJson(distance, base, bonus, score, "MEASURED", null, null, "UNMEASURED"),
        )
        return RecordEntity(
            id, "p", null, "TRIP", occurredAt, payloadJson = Json.encodeToString(p),
            startMm = startMm, endMm = endMm, valid = status == "VALID", bonusMilli = (bonus.toDouble() * 1000).toLong(),
        )
    }

    @Test fun `거리 안전점수 EXP를 서울 시각과 함께 보여 준다`() {
        val rows = tripRows(listOf(trip("a", at, "VALID", 0, 12_000_000, "24.000", "35.000", 94)))
        assertEquals(listOf(TripRow("11월 10일 15:05", "12.0 km", "94점", "+59.0")), rows)
    }

    @Test fun `거리 미확인 트립은 미측정이고 EXP를 주지 않는다`() {
        val rows = tripRows(listOf(trip("a", at, "UNVERIFIED", null, null, null, "0.000", null)))
        assertEquals(listOf(TripRow("11월 10일 15:05", "미측정", "미측정", "+0.0")), rows)
    }

    @Test fun `최신순이고 앞 트립과 겹친 구간은 EXP를 주지 않는다`() {
        val first = trip("a", at, "VALID", 0, 12_000_000, "24.000", "35.000", 94)
        val overlap = trip("b", at + 60_000, "VALID", 0, 3_000_000, "6.000", "15.000", 100)
        val rows = tripRows(listOf(overlap, first))
        assertEquals(listOf("11월 10일 15:06" to "+0.0", "11월 10일 15:05" to "+59.0"), rows.map { it.date to it.exp })
    }

    @Test fun `읽을 수 없는 payload는 미측정 줄이 된다`() {
        val bad = RecordEntity("r", "p", null, "TRIP", at, payloadJson = "{", startMm = 0, endMm = 1_000_000, valid = true, bonusMilli = 0)
        assertEquals(listOf(TripRow("11월 10일 15:05", "미측정", "미측정", "+2.0")), tripRows(listOf(bad)))
    }

    @Test fun `중단된 트립은 시간순으로 섞여 중단됨으로 보이고 EXP가 없다`() {
        val done = trip("a", at, "VALID", 0, 12_000_000, "24.000", "35.000", 94)
        val cutBefore = InterruptedTripEntity("p", at - 3_600_000, "{}", 4.25, 0) // 14:05 시작
        val cutAfter = InterruptedTripEntity("p", at + 3_600_000, "{}", null, 0) // 16:05 시작, 거리 미상
        val rows = tripRows(listOf(done), listOf(cutBefore, cutAfter))
        assertEquals(
            listOf(
                TripRow("11월 10일 16:05", "", "중단됨", null, interrupted = true),
                TripRow("11월 10일 15:05", "12.0 km", "94점", "+59.0"),
                TripRow("11월 10일 14:05", "4.3 km", "중단됨", null, interrupted = true),
            ),
            rows,
        )
    }

    @Test fun `중단된 트립만 있어도 줄이 있다`() {
        assertEquals(1, tripRows(emptyList(), listOf(InterruptedTripEntity("p", at, "{}", null, 0))).size)
    }
}
