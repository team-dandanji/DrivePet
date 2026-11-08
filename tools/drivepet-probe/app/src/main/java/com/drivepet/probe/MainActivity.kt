package com.drivepet.probe

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 값과 로그를 보여 주는 단순한 검증 화면. 레이아웃 파일 없이 코드로 만든다. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var valuesView: TextView
    private lateinit var logView: TextView

    private val refresh = object : Runnable {
        override fun run() {
            valuesView.text = Probe.valuesSnapshot().joinToString("\n") { (k, v) -> "• $k\n    $v" }
                .ifEmpty { "아직 값이 없어요. '검증 시작'을 눌러 주세요." }
            logView.text = Probe.recentLines(60).joinToString("\n")
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Probe.init(this)
        setContentView(buildUi())
        Probe.log("앱 화면 생성")
    }

    override fun onResume() {
        super.onResume()
        val since = System.currentTimeMillis() - Probe.parkRequestedAt
        if (Probe.parkRequestedAt > 0 && since < 5_000) {
            Probe.log("V-06 결과: P 전환 후 앱 화면이 자동으로 떴어요 (${since}ms)")
            Probe.parkRequestedAt = 0
        } else {
            Probe.log("앱 화면 보임")
        }
        handler.post(refresh)
    }

    override fun onPause() {
        Probe.log("앱 화면 가려짐 (다른 앱으로 이동 또는 주행 제한)")
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 40)
            setBackgroundColor(Color.parseColor("#11141E"))
        }
        root.addView(TextView(this).apply {
            text = "DrivePet P0 검증"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFD36B"))
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 24, 0, 24) }
        buttons.addView(button("검증 시작") {
            startForegroundService(Intent(this, ProbeService::class.java))
        })
        buttons.addView(button("중지") { stopService(Intent(this, ProbeService::class.java)) })
        buttons.addView(button("권한 요청") {
            requestPermissions(
                arrayOf("android.car.permission.CAR_SPEED", "android.permission.POST_NOTIFICATIONS"),
                1,
            )
        })
        buttons.addView(button("로그 지우기") { Probe.clearLog() })
        root.addView(buttons)

        root.addView(section("현재 값"))
        valuesView = body().also { root.addView(it) }
        root.addView(section("기록 (최신이 위)"))
        logView = body().apply { textSize = 15f; typeface = Typeface.MONOSPACE }.also { root.addView(it) }

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#11141E"))
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissions.forEachIndexed { i, p ->
            Probe.log("권한 $p = ${if (grantResults.getOrNull(i) == 0) "허용" else "거부"}")
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 18f
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = 16 }
    }

    private fun section(label: String) = TextView(this).apply {
        text = label
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#A6AEC4"))
        setPadding(0, 24, 0, 8)
    }

    private fun body() = TextView(this).apply {
        textSize = 17f
        setTextColor(Color.parseColor("#F3F1EA"))
        setLineSpacing(0f, 1.2f)
    }
}
