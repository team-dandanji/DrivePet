package com.drivepet.app.ui.lounge

import com.drivepet.app.ui.components.TagStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoungeTagsTest {
    @Test fun `배터리 태그 경계`() {
        assertEquals("넉넉해요" to TagStyle.Positive, batteryTag(60))
        assertEquals("보통" to TagStyle.Caution, batteryTag(59))
        assertEquals("보통" to TagStyle.Caution, batteryTag(20))
        assertEquals("부족해요" to TagStyle.Negative, batteryTag(19))
        assertNull(batteryTag(null))
    }

    @Test fun `건강 태그 경계`() {
        assertEquals("건강해요" to TagStyle.Positive, healthTag(90))
        assertEquals("주의" to TagStyle.Caution, healthTag(89))
        assertEquals("주의" to TagStyle.Caution, healthTag(60))
        assertEquals("아파요" to TagStyle.Negative, healthTag(59))
        assertNull(healthTag(null))
    }

    @Test fun `경고 카드 문구`() {
        assertEquals(WarningCard("타이어 공기압 확인 필요", "TPMS 경고등이 켜져 있어요"), warningCard("TPMS 경고"))
        assertEquals(WarningCard("엔진 확인 필요", "차량 경고등이 켜져 있어요"), warningCard("엔진"))
    }

    @Test fun `보이는 경고는 TPMS 먼저 최대 2개`() {
        assertEquals(listOf("TPMS", "A"), visibleWarnings(listOf("A", "TPMS", "B")))
        assertEquals(listOf("A", "B"), visibleWarnings(listOf("A", "B", "C")))
        assertEquals(emptyList<String>(), visibleWarnings(emptyList()))
    }
}
