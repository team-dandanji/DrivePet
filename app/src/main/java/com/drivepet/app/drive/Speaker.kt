package com.drivepet.app.drive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Bubble(val text: String, val at: Long)

interface Speaker {
    val bubble: StateFlow<Bubble?>
    /** voice = true면 말풍선과 함께 목소리도 낸다(TtsSpeaker). BubbleSpeaker는 voice를 무시. */
    fun say(text: String, voice: Boolean = false)
}

private const val BUBBLE_MS = 5_000L

class BubbleSpeaker(private val scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis) : Speaker {
    private val _bubble = MutableStateFlow<Bubble?>(null)
    override val bubble: StateFlow<Bubble?> = _bubble
    private var clearJob: Job? = null

    @Synchronized
    override fun say(text: String, voice: Boolean) {
        clearJob?.cancel()
        _bubble.value = Bubble(text, clock())
        clearJob = scope.launch {
            delay(BUBBLE_MS)
            _bubble.value = null
        }
    }
}
