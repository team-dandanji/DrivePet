package com.drivepet.app.ui.care

import com.drivepet.app.data.CareRow
import com.drivepet.app.data.PendingCare
import com.drivepet.app.data.db.MaintenanceItemEntity
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.CareLife
import com.drivepet.app.drive.shouldArm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldAskCareRecordTest {
    private val armed = PendingCare("TIRE", "한국타이어 강남점", armed = true)
    private val unarmed = armed.copy(armed = false)

    @Test fun asksWhenArmedParkedOnLounge() = assertTrue(shouldAskCareRecord(armed, parked = true, onLounge = true))
    @Test fun notBeforeDriving() = assertFalse(shouldAskCareRecord(unarmed, parked = true, onLounge = true))
    @Test fun notWhileDriving() = assertFalse(shouldAskCareRecord(armed, parked = false, onLounge = true))
    @Test fun notWithoutPending() = assertFalse(shouldAskCareRecord(null, parked = true, onLounge = true))
    @Test fun notOffLounge() = assertFalse(shouldAskCareRecord(armed, parked = true, onLounge = false))

    @Test fun armsOnlyWhenDrivingWithUnarmedPending() {
        assertTrue(shouldArm(unarmed, driving = true))
        assertFalse(shouldArm(unarmed, driving = false))
        assertFalse(shouldArm(armed, driving = true))
        assertFalse(shouldArm(null, driving = true))
    }

    @Test fun careRowIdMatchesByCareItem() {
        val tire = CareRow(MaintenanceItemEntity("row-tire", "p1", "TIRE", 10_000, null, null, null), CareItem.TIRE, CareLife(null, null, null, null, null))
        assertEquals("row-tire", careRowIdFor(CareItem.TIRE, listOf(tire)))
        assertNull(careRowIdFor(CareItem.ENGINE_OIL, listOf(tire)))
    }
}
