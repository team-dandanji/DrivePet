package com.drivepet.probe

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 검증 값과 로그를 한곳에 모은다. 화면, logcat, 파일(probe-log.txt)에 같이 남는다. */
object Probe {
    const val TAG = "DrivePetProbe"

    private val values = LinkedHashMap<String, String>()
    private val rawValues = HashMap<String, String>()
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    private var file: File? = null

    /** 서비스가 P 전환을 감지하고 화면 띄우기를 시도한 시각 (V-06). */
    @Volatile
    var parkRequestedAt: Long = 0L

    @Synchronized
    fun init(context: Context) {
        if (file == null) file = File(context.filesDir, "probe-log.txt")
    }

    /** 값이 바뀔 때만 로그에 한 줄 남긴다. */
    @Synchronized
    fun set(key: String, value: String, source: String) {
        if (rawValues[key] != value) log("$key = $value  ($source)")
        rawValues[key] = value
        values[key] = "$value   [$source ${fmt.format(Date())}]"
    }

    @Synchronized
    fun value(key: String): String? = rawValues[key]

    @Synchronized
    fun log(message: String) {
        val line = "${fmt.format(Date())}  $message"
        Log.i(TAG, line)
        lines.addLast(line)
        while (lines.size > 400) lines.removeFirst()
        runCatching { file?.appendText(line + "\n") }
    }

    @Synchronized
    fun valuesSnapshot(): List<Pair<String, String>> = values.entries.map { it.key to it.value }

    @Synchronized
    fun recentLines(count: Int): List<String> = lines.toList().takeLast(count).reversed()

    @Synchronized
    fun clearLog() {
        lines.clear()
        runCatching { file?.writeText("") }
    }
}
