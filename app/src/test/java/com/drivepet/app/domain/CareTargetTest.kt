package com.drivepet.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CareTargetTest {
    @Test fun tpmsWarningIsTire() {
        assertEquals(CareItem.TIRE, careTarget(listOf("TPMS"), mapOf(CareItem.WIPER to 5, CareItem.TIRE to 90)))
    }

    @Test fun lowestLifeWithoutWarnings() {
        assertEquals(CareItem.WIPER, careTarget(emptyList(), mapOf(CareItem.WIPER to 10, CareItem.TIRE to 80, CareItem.CABIN_FILTER to null)))
    }

    @Test fun noInfoIsTire() {
        assertEquals(CareItem.TIRE, careTarget(emptyList(), mapOf(CareItem.WIPER to null)))
        assertEquals(CareItem.TIRE, careTarget(emptyList(), emptyMap()))
    }
}
