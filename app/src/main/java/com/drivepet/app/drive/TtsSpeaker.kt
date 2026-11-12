package com.drivepet.app.drive

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import kotlinx.coroutines.flow.StateFlow
import android.os.SystemClock
import java.util.Locale

/**
 * 말풍선 + 깜빡이 목소리(Pleos TextToSpeech SDK 3.3.1).
 *
 * - 말풍선은 항상 [bubbleSpeaker]가 띄운다. 목소리는 voice = true일 때만 낸다.
 * - ON_DEVICE 모드: 차 안 엔진으로 합성 → 인터넷·앱 등록(My Project 키) 불필요.
 * - 초기화 실패·권한 없음·엔진 오류면 목소리를 끄고 말풍선만 남긴다(앱은 그대로 동작).
 * - SDK 호출은 모두 메인 스레드에서 한다(DriveCoordinator는 Default 디스패처에서 부름).
 * - 대사는 순서대로 말한다(대기열). 앞 대사를 끊지 않고, 끝나면 다음 대사를 말한다.
 *   너무 오래 기다린 대사([MAX_WAIT_MS] 초과)는 상황이 지났으므로 버린다.
 */
class TtsSpeaker(
    context: Context,
    private val bubbleSpeaker: Speaker,
) : Speaker {

    override val bubble: StateFlow<Bubble?> = bubbleSpeaker.bubble

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    @Volatile private var ready = false
    @Volatile private var disabled = false

    // 아래 세 값은 메인 스레드에서만 읽고 쓴다.
    private data class Line(val text: String, val queuedAt: Long)
    private val queue = ArrayDeque<Line>()
    private var speaking = false
    private val watchdog = Runnable { Log.w(TAG, "말하기 끝 신호 없음 → 다음 대사로"); onFinished() }

    private val eventListener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() { Log.d(TAG, "말하기 시작") }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { Log.d(TAG, "말하기 끝"); main.post { onFinished() } }
        override fun onStop() { main.post { onFinished() } }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "TTS 오류: ${errorType.name}")
            when (errorType) {
                // 다시 해도 안 되는 오류 → 목소리 끄고 말풍선만
                ErrorType.PERMISSION_DENIED,
                ErrorType.NOT_SUPPORTED_API,
                ErrorType.UNAVAILABLE_LANGUAGE,
                ErrorType.ON_DEVICE_ENGINE_ERROR -> disabled = true
                else -> Unit // AUDIO_ERROR 등 일시적 오류는 다음 대사에서 다시 시도
            }
            main.post { onFinished() }
        }
    }

    init {
        main.post {
            runCatching {
                tts.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        tts.addEventListener(eventListener)
                        tts.setLocale(Locale.KOREA)
                        ready = true
                        Log.i(TAG, "TTS 준비 완료 (ON_DEVICE, ko-KR)")
                    }

                    override fun onFailure(errorType: ErrorType) {
                        disabled = true
                        Log.w(TAG, "TTS 초기화 실패: ${errorType.name} → 말풍선만 사용")
                    }
                })
            }.onFailure {
                disabled = true
                Log.w(TAG, "TTS 생성 실패 → 말풍선만 사용", it)
            }
        }
    }

    override fun say(text: String, voice: Boolean) {
        bubbleSpeaker.say(text, voice)
        if (!voice || disabled) return
        val line = spokenText(text)
        if (line.isBlank()) return
        main.post {
            if (!ready || disabled) return@post
            if (queue.lastOrNull()?.text == line) return@post   // 같은 대사 연속 중복은 한 번만
            queue.addLast(Line(line, SystemClock.elapsedRealtime()))
            while (queue.size > MAX_QUEUE) queue.removeFirst()   // 밀린 대사가 많으면 오래된 것부터 버림
            if (!speaking) playNext()
        }
    }

    /** 메인 스레드. 대기열에서 아직 유효한 다음 대사를 꺼내 말한다. */
    private fun playNext() {
        val now = SystemClock.elapsedRealtime()
        while (queue.isNotEmpty() && now - queue.first().queuedAt > MAX_WAIT_MS) {
            Log.d(TAG, "오래 기다린 대사 버림: ${queue.first().text}")
            queue.removeFirst()
        }
        val next = queue.removeFirstOrNull() ?: return
        if (!ready || disabled) { queue.clear(); return }
        speaking = true
        runCatching { tts.speak(next.text) }
            .onSuccess { main.postDelayed(watchdog, WATCHDOG_MS) }
            .onFailure { Log.w(TAG, "speak 실패", it); speaking = false; playNext() }
    }

    /** 메인 스레드. 한 대사가 끝났거나(onDone·onStop·onError) 응답이 없을 때. */
    private fun onFinished() {
        main.removeCallbacks(watchdog)
        if (!speaking) return
        speaking = false
        playNext()
    }

    /** 앱이 끝날 때 호출(지금은 AppContainer가 앱 수명 동안 살아 있어서 호출 안 해도 됨). */
    fun release() {
        main.post {
            main.removeCallbacks(watchdog)
            queue.clear()
            speaking = false
            runCatching {
                tts.removeEventListener(eventListener)
                tts.release()
            }
            ready = false
            disabled = true
        }
    }

    private companion object {
        const val TAG = "DrivePetTts"
        const val MAX_QUEUE = 3            // 대기열 최대 대사 수
        const val MAX_WAIT_MS = 8_000L     // 이보다 오래 기다린 대사는 버림
        const val WATCHDOG_MS = 15_000L    // 끝 신호가 안 오면 이 시간 뒤 다음 대사로

        /** 이모지·기호는 읽지 않게 빼고, 한글·영문·숫자·문장부호만 남긴다. */
        fun spokenText(text: String): String =
            text.replace(Regex("[^\\p{L}\\p{N}\\s.,!?~%·'\"-]"), "").trim()
    }
}
