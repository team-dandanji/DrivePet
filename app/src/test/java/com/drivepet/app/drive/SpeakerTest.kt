package com.drivepet.app.drive

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakerTest {
    @Test
    fun `말풍선은 5초 뒤 사라지고 새 말풍선은 타이머를 다시 시작한다`() = runTest {
        val speaker = BubbleSpeaker(this) { 0L }
        speaker.say("a")
        advanceTimeBy(4_000)
        speaker.say("b")
        advanceTimeBy(4_000)
        assertEquals("b", speaker.bubble.value?.text)
        advanceTimeBy(1_001)
        assertNull(speaker.bubble.value)
    }
}
