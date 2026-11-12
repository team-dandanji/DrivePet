package com.drivepet.app.ui.exit

import com.drivepet.app.drive.ExitCheck
import com.drivepet.vehicle.model.ExitItem
import com.drivepet.vehicle.model.Signal
import com.drivepet.vehicle.model.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitCheckScreenTest {
    private val w = ExitItem.WINDOW_OPEN
    private val c = ExitItem.CABIN_LIGHT_ON
    private val h = ExitItem.HEADLIGHT_ON
    private val d = ExitItem.DOORS_UNLOCKED
    private fun check(vararg p: Pair<ExitItem, Boolean>) = ExitCheck(mapOf(*p), lockUnknown = false)

    @Test fun `shows only figma items in figma order`() =
        assertEquals(listOf(c, w), shownExitItems(check(d to false, h to false, w to false, c to false)))

    @Test fun `shows only the items that were requested`() = assertEquals(listOf(w), shownExitItems(check(w to false, h to false)))

    @Test fun `unresolved window needs the popup`() = assertTrue(exitPopupNeeded(check(w to false)))

    @Test fun `unresolved cabin light alone needs the popup`() = assertTrue(exitPopupNeeded(check(c to false, w to true)))

    @Test fun `hidden unresolved headlight does not need the popup`() = assertFalse(exitPopupNeeded(check(w to true, h to false)))

    @Test fun `hidden unresolved lock does not need the popup`() = assertFalse(exitPopupNeeded(check(w to true, d to false)))

    /** 추적기는 창문을 한 번 닫으면 true로 붙잡아 두지만, 다시 열리면 최신 신호로 미조치로 본다. */
    @Test fun `window closed then reopened is not resolved now`() {
        val latched = check(w to true, c to true, h to false)
        val reopened = VehicleState(driverWindow = Signal.Ok(4, 0), cabinLight = Signal.Ok(false, 0))
        assertEquals(mapOf(c to true, w to false), exitItemsResolvedNow(latched, reopened))
        val closed = reopened.copy(driverWindow = Signal.Ok(0, 1))
        assertEquals(mapOf(c to true, w to true), exitItemsResolvedNow(latched, closed))
    }

    @Test fun `unreadable signal is not resolved now`() =
        assertEquals(mapOf(c to false, w to false), exitItemsResolvedNow(check(w to true, c to true), VehicleState()))

    @Test fun `everything resolved does not need the popup`() = assertFalse(exitPopupNeeded(check(w to true, c to true, h to true, d to true)))
}
