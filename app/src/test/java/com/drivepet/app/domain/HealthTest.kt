package com.drivepet.app.domain

import com.drivepet.app.domain.MeasurementStatus.MEASURED
import com.drivepet.app.domain.MeasurementStatus.PERMISSION_DENIED
import com.drivepet.app.domain.MeasurementStatus.UNMEASURED
import com.drivepet.app.domain.Mood.HAPPY
import com.drivepet.app.domain.Mood.HUNGRY
import com.drivepet.app.domain.Mood.NORMAL
import com.drivepet.app.domain.Mood.SICK_1
import com.drivepet.app.domain.Mood.SICK_2
import com.drivepet.app.domain.Mood.SICK_3
import com.drivepet.app.domain.Mood.WORRIED
import com.drivepet.app.domain.WarningInput.Known
import com.drivepet.app.domain.WarningInput.Unavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthTest {
    @Test fun `tpms and good habits`() = assertEquals(HealthResult(65, MEASURED), health(Known(listOf("TPMS")), emptyList(), listOf(80, 90)))
    @Test fun `no warnings full score`() = assertEquals(100, health(Known(emptyList()), emptyList(), emptyList()).score)
    @Test fun `all excluded is unmeasured`() = assertEquals(HealthResult(null, UNMEASURED), health(Unavailable(PERMISSION_DENIED), emptyList(), emptyList()))

    @Test fun `recent uses last five`() {
        // 최근 5개 = 마지막 5개(전부 100) → 습관 30/30
        assertEquals(HealthResult(100, MEASURED), health(Unavailable(PERMISSION_DENIED), emptyList(), listOf(0, 100, 100, 100, 100, 100)))
    }

    @Test fun `tpms counted once and others each ten`() {
        assertEquals(50, health(Known(listOf("TPMS_FL", "TPMS_FR")), emptyList(), emptyList()).score)
        assertEquals(25, health(Known(listOf("TPMS", "ENGINE")), emptyList(), emptyList()).score)
    }

    @Test fun `warning deduction floors at zero`() =
        assertEquals(0, health(Known(List(6) { "W$it" }), emptyList(), emptyList()).score)

    @Test fun `consumables clamped then averaged`() =
        assertEquals(86, health(Known(emptyList()), listOf(150, -50, 100), emptyList()).score) // 40 + 20 = 60 / 70

    @Test fun `satiety clamps and passes null`() {
        assertEquals(100, satiety(120)); assertEquals(0, satiety(-5)); assertEquals(55, satiety(55)); assertNull(satiety(null))
    }

    @Test fun `mood priority`() {
        assertEquals(SICK_3, mood(MoodInput(3, 10, 5, 1, true))); assertEquals(SICK_1, mood(MoodInput(0, null, null, null, false)))
        assertEquals(HUNGRY, mood(MoodInput(null, 20, 5, null, true))); assertEquals(WORRIED, mood(MoodInput(null, 80, null, 30, true)))
        assertEquals(HAPPY, mood(MoodInput(null, 80, 50, 100, true))); assertEquals(NORMAL, mood(MoodInput(null, null, null, null, false)))
    }

    @Test fun `mood boundaries`() {
        assertEquals(SICK_2, mood(MoodInput(1, null, null, null, false))); assertEquals(SICK_2, mood(MoodInput(2, null, null, null, false)))
        assertEquals(SICK_1, mood(MoodInput(-1, null, null, null, false)))
        assertEquals(WORRIED, mood(MoodInput(null, 21, 10, null, false))); assertEquals(NORMAL, mood(MoodInput(null, 21, 11, 31, false)))
    }
}
