package com.drivepet.app.ui.lounge

import org.junit.Assert.assertEquals
import org.junit.Test

class LoungeLayoutTest {
    @Test fun breakpoints() {
        assertEquals(LoungeLayout.DEFAULT, loungeLayout(1682, 1210))
        assertEquals(LoungeLayout.NARROW, loungeLayout(842, 1210))
        assertEquals(LoungeLayout.NARROW, loungeLayout(760, 1210))
        assertEquals(LoungeLayout.WIDE, loungeLayout(1640, 810))
    }
}
