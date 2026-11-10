package com.drivepet.app.ui.exit

import com.drivepet.app.drive.ExitCheck
import com.drivepet.vehicle.model.ExitItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitCheckScreenTest {
    private val w = ExitItem.WINDOW_OPEN
    private val d = ExitItem.DOORS_UNLOCKED

    @Test fun `all resolved can finish`() = assertTrue(canFinish(ExitCheck(mapOf(w to true), false)))

    @Test fun `unresolved window blocks`() = assertFalse(canFinish(ExitCheck(mapOf(w to false, d to true), false)))

    @Test fun `unknown lock does not block when everything else is resolved`() =
        assertTrue(canFinish(ExitCheck(mapOf(w to true, d to false), lockUnknown = true)))

    @Test fun `unknown lock still blocks on other unresolved items`() =
        assertFalse(canFinish(ExitCheck(mapOf(w to false, d to false), lockUnknown = true)))

    @Test fun `known lock unresolved blocks`() = assertFalse(canFinish(ExitCheck(mapOf(d to false), lockUnknown = false)))
}
