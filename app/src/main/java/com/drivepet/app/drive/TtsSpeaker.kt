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
import java.util.Locale

/**
 * 말풍선 + 깜빡이 목소리(Pleos TextToSpeech SDK 3.3.1).
 *
 * - 말풍선은 항상 [bubbleSpeaker]가 띄운다. 목소리는 voice = true일 때만 낸다.
 * - ON_DEVICE 모드: 차 안 엔진으로 합성 → 인터넷·앱 등록(My Project 키) 불필요.
 * - 초기화 실패·권한 없음·엔진 오류면 목소리를 끄고 말풍선만 남긴다(앱은 그대로 동작).
 * - SDK 호출은 모두 메인 스레드에서 한다(DriveCoordinator는 Default 디스패처에서 부름).
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

    private val eventListener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() { Log.d(TAG, "말하기 시작") }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { Log.d(TAG, "말하기 끝") }
        override fun onStop() = Unit
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
            runCatching {
                tts.stop()          // 앞 대사가 남아 있으면 끊고 새 대사로
                tts.speak(line)
            }.onFailure { Log.w(TAG, "speak 실패", it) }
        }
    }

    /** 앱이 끝날 때 호출(지금은 AppContainer가 앱 수명 동안 살아 있어서 호출 안 해도 됨). */
    fun release() {
        main.post {
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

        /** 이모지·기호는 읽지 않게 빼고, 한글·영문·숫자·문장부호만 남긴다. */
        fun spokenText(text: String): String =
            text.replace(Regex("[^\\p{L}\\p{N}\\s.,!?~%·'\"-]"), "").trim()
    }
}
