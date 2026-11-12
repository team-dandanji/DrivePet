package com.drivepet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StepDotsTest {
    @Test
    fun `dots before current are done, current is current, rest todo`() {
        assertEquals(listOf(DotState.Done, DotState.Current, DotState.Todo, DotState.Todo), stepDots(4, 1))
        assertEquals(listOf(DotState.Done, DotState.Done, DotState.Current), stepDots(3, 2))
    }
}
