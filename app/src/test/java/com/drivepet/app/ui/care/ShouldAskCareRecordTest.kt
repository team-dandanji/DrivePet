package com.drivepet.app.ui.care

import com.drivepet.app.data.PendingCare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldAskCareRecordTest {
    private val pending = PendingCare("TIRE", "한국타이어 강남점")

    @Test fun asksWhenPendingParkedOnLounge() = assertTrue(shouldAskCareRecord(pending, parked = true, onLounge = true))
    @Test fun notWhileDriving() = assertFalse(shouldAskCareRecord(pending, parked = false, onLounge = true))
    @Test fun notWithoutPending() = assertFalse(shouldAskCareRecord(null, parked = true, onLounge = true))
    @Test fun notOffLounge() = assertFalse(shouldAskCareRecord(pending, parked = true, onLounge = false))
}
