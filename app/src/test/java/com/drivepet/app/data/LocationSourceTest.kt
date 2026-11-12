package com.drivepet.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationSourceTest {
    private val seoul = SEOUL_CITY_HALL_LAT to SEOUL_CITY_HALL_LNG

    @Test fun outsideKoreaFallsBackToSeoul() {
        assertEquals(seoul, pickOrSeoul(37.42, -122.08))
    }

    @Test fun pickInKoreaIsNullOutsideKorea() {
        assertNull(pickInKorea(37.42, -122.08))
        assertEquals(37.5665 to 126.978, pickInKorea(37.5665, 126.978))
    }

    @Test fun insideKoreaIsKept() {
        listOf(37.5665 to 126.978, 35.18 to 129.08, 33.5 to 126.5).forEach { assertEquals(it, pickOrSeoul(it.first, it.second)) }
    }
}
