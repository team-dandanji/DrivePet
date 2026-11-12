package com.drivepet.app.data

import com.drivepet.app.data.db.RecordEntity
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.domain.CareItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CareMappingTest {
    private fun km(k: Int) = k * 1_000_000L

    private fun rep(id: String, itemId: String, at: Long, mm: Long?) = RecordEntity(
        id, "p", null, "REPLACEMENT", at, payloadJson = Json.encodeToString(ReplacementPayload(itemId, "2026-11-04", mm)),
        localDate = "2026-11-04", itemId = itemId,
    )

    @Test fun replacementRecordsFeedCareLife() {
        val tire = careItemEntity("p", CareItem.TIRE, CareBaseline(km(15_000), null, null))
        val wiper = careItemEntity("p", CareItem.WIPER, CareBaseline(km(15_000), null, null))
        val rows = careRows(listOf(wiper, tire), listOf(rep("r1", tire.itemId, 10L, km(20_000))), km(24_800))
        assertEquals(listOf(CareItem.TIRE, CareItem.WIPER), rows.map { it.care }) // CareItem 순서
        assertEquals(52, rows[0].life.lifePct)
        assertEquals(2, rows[1].life.lifePct) // 와이퍼는 기준 15,000 그대로 → 9,800km 지남
        assertEquals(listOf(52, 2), rows.lifePcts())
    }

    @Test fun itemIdIsStablePerProfileAndItem() {
        assertEquals(careItemId("p", CareItem.TIRE), defaultCareItems("p", com.drivepet.app.domain.FuelType.EV).first { it.kind == "TIRE" }.itemId)
    }
}
