package com.drivepet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleLineTest {
    @Test fun firstSentenceOnly() {
        assertEquals("안녕, 나 모모!", bubbleLine("안녕, 나 모모! 오늘 기분 최고!"))
        assertEquals("벨트를 매 주세요", bubbleLine("벨트를 매 주세요"))
        assertEquals("정말?!", bubbleLine("정말?! 대단해요"))
    }

    @Test fun decimalPointIsNotSentenceEnd() {
        assertEquals("기온 3.5도예요.", bubbleLine("기온 3.5도예요. 따뜻하게 입어요"))
    }

    @Test fun longSentenceCutsAtWordBoundaryWithin15() {
        val line = bubbleLine("오늘은 날씨가 맑아서 드라이브하기 좋은 날이에요!")
        assertEquals("오늘은 날씨가 맑아서", line)
        assert(line.length <= 15)
    }

    @Test fun lastWordEndingExactlyAt15IsKept() {
        assertEquals("가나다라마 바사아자차 카타파", bubbleLine("가나다라마 바사아자차 카타파 하"))
    }

    @Test fun noSpaceCutsAt15WithoutBreakingSurrogates() {
        assertEquals("가".repeat(15), bubbleLine("가".repeat(20)))
        val emoji = "😀".repeat(20)
        val line = bubbleLine(emoji)
        assertEquals(15, line.codePointCount(0, line.length))
        assertEquals(30, line.length)
    }
}
