package com.drivepet.vehicledemo

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drivepet.vehicle.VehicleModule
import com.drivepet.vehicle.model.DriveEvent
import com.drivepet.vehicle.model.Gear
import com.drivepet.vehicle.model.TripSummary
import com.drivepet.vehicle.model.TurnSide
import com.drivepet.vehicle.model.VehicleState
import com.drivepet.vehicle.model.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 차량 데이터 모듈 데모·디버그 화면.
 * - 가짜 데이터: 버튼으로 차 상태를 바꿔 이벤트가 나오는지 확인
 * - 시나리오 재생: 발표용 '출근 12km'를 4배속으로 재생
 * - 실제 차: Pleos 에뮬레이터의 Virtual Console 값을 그대로 받음
 */
class DemoActivity : ComponentActivity() {

    private lateinit var sourceView: TextView
    private lateinit var stateView: TextView
    private lateinit var tripView: TextView
    private lateinit var eventView: TextView
    private lateinit var fakePanel: LinearLayout
    private val eventLines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        requestPermissions(arrayOf("android.car.permission.CAR_SPEED", "android.permission.POST_NOTIFICATIONS"), 1)

        val repo = VehicleModule.repository
        // RESUMED가 아닌 STARTED 기준: Pleos 화면에서 다른 영역을 눌러 일시정지돼도 계속 갱신
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { repo.state.collect { stateView.text = describe(it) } }
                launch { repo.trip.collect { tripView.text = describeTrip(it, ended = false) } }
                launch { repo.sourceName.collect { sourceView.text = "데이터 출처: $it" } }
                launch {
                    repo.events.collect { e ->
                        eventLines.addFirst("${fmt.format(Date(e.at))}  ${describe(e)}")
                        while (eventLines.size > 40) eventLines.removeLast()
                        eventView.text = eventLines.joinToString("\n")
                        if (e is DriveEvent.TripEnded) tripView.text = describeTrip(e.trip, ended = true)
                    }
                }
            }
        }
    }

    // ---------------- 화면 구성 ----------------

    private fun buildUi(): View {
        val root = column().apply { setPadding(40, 32, 40, 32) }
        root.addView(text("DrivePet 차량 데이터 데모", 26f, "#FFD36B", bold = true))
        sourceView = text("", 16f, "#A6AEC4").also { root.addView(it) }

        root.addView(row(
            button("가짜 데이터") { start(VehicleModule.Mode.FAKE) },
            button("시나리오 재생 (출근 12km)") { start(VehicleModule.Mode.REPLAY) },
            button("실제 차") { start(VehicleModule.Mode.REAL) },
            button("중지") { VehicleModule.stop(this); fakePanel.visibility = View.GONE },
            button("기록 지우기") { eventLines.clear(); eventView.text = "" },
        ))

        fakePanel = column().apply { visibility = View.GONE }
        val f = VehicleModule.fake
        fakePanel.addView(row(label("기어"), *Gear.values().map { g -> button(g.name) { f.setGear(g) } }.toTypedArray()))
        fakePanel.addView(row(
            label("브레이크"), button("밟기") { f.setBrake(true) }, button("떼기") { f.setBrake(false) },
            label("  거리"), button("+1km") { f.addKm(1.0) }, button("+12km") { f.addKm(12.0) },
        ))
        fakePanel.addView(row(label("조향"), *listOf(-120.0, -50.0, 0.0, 50.0, 120.0).map { d ->
            button("${d.toInt()}°") { f.setSteering(d) }
        }.toTypedArray(), button("⚡ 급조향") { sharpSteer() }))
        fakePanel.addView(row(
            label("방향지시등"), *TurnSide.values().map { s -> button(s.name) { f.setTurnSignal(s) } }.toTypedArray(),
        ))
        fakePanel.addView(row(
            label("운전석 문"), button("열기") { f.setDriverDoor(true) }, button("닫기") { f.setDriverDoor(false) },
            label("  잠금"), button("잠금") { f.setLocked(true) }, button("해제") { f.setLocked(false) },
        ))
        fakePanel.addView(row(
            label("창문"), button("0") { f.setWindow(0) }, button("4") { f.setWindow(4) }, button("10") { f.setWindow(10) },
            label("  전조등"), button("하향") { f.setLowBeam(true) }, button("상향") { f.setHighBeam(true) },
            button("끄기") { f.setLowBeam(false); f.setHighBeam(false) },
        ))
        fakePanel.addView(row(
            label("벨트"), button("착용") { f.setBelt(true) }, button("해제") { f.setBelt(false) },
            label("  잔량"), button("80%") { f.setEnergy(80) }, button("15%") { f.setEnergy(15) },
            label("  예외"), button("조향 미지원") { f.markUnsupported("steeringDeg") },
        ))
        root.addView(fakePanel)

        val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = column().apply {
            addView(text("차 상태", 18f, "#A6AEC4", bold = true))
            stateView = text("", 15f, "#F3F1EA", mono = true).also { addView(it) }
            addView(text("주행", 18f, "#A6AEC4", bold = true))
            tripView = text("", 15f, "#F3F1EA", mono = true).also { addView(it) }
        }
        val right = column().apply {
            addView(text("이벤트 (최신이 위)", 18f, "#A6AEC4", bold = true))
            eventView = text("", 15f, "#7FD49B", mono = true).also { addView(it) }
        }
        columns.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        columns.addView(right, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(columns)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#11141E"))
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        }
    }

    private fun start(mode: VehicleModule.Mode) {
        eventLines.clear()
        eventView.text = ""
        VehicleModule.start(this, mode)
        fakePanel.visibility = if (mode == VehicleModule.Mode.FAKE) View.VISIBLE else View.GONE
    }

    /** 손으로 1초 안에 두 버튼을 누르기 어려워서, 급조향을 한 번에 재현하는 버튼. */
    private fun sharpSteer() {
        val f = VehicleModule.fake
        lifecycleScope.launch {
            f.setSteering(-60.0)
            delay(300)
            f.setSteering(60.0)
            delay(700)
            f.setSteering(0.0)
        }
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun row(vararg views: View): View {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 6) }
        views.forEach { r.addView(it) }
        return HorizontalScrollView(this).apply { addView(r) }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = 8 }
    }

    private fun label(t: String) = text(t, 16f, "#A6AEC4").apply { setPadding(0, 0, 12, 0) }

    private fun text(t: String, size: Float, color: String, bold: Boolean = false, mono: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (mono) typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        }

    // ---------------- 표시 문구 ----------------

    private fun describe(s: VehicleState) = """
        기어          ${s.gear.label()}
        주행 제한     ${s.uxRestricted.label()}
        누적거리 km   ${s.odometerKm.label()}
        차속 km/h     ${s.speedKmh.label()}
        브레이크      ${s.brakePressed.label()}
        조향각        ${s.steeringDeg.label()}
        방향지시등    ${s.turnSignal.label()}
        운전석 문     ${s.driverDoorOpen.label()}
        문 잠금       ${s.doorsLocked.label()}
        운전석 창문   ${s.driverWindow.label()}
        하향/상향등   ${s.lowBeam.label()} / ${s.highBeam.label()}
        안전벨트      ${s.driverBeltBuckled.label()}
        잔량 %        ${s.energyPct.label()}
        경고등        ${s.warnings.label()}
    """.trimIndent()

    private fun describeTrip(t: TripSummary?, ended: Boolean): String {
        if (t == null) return "주행 중 아님"
        val km = t.distanceKm?.let { "%.1f km (%d EXP)".format(it, (it * 2).toInt()) } ?: "미측정"
        return """
            ${if (ended) "✅ 주행 종료" else "🚗 주행 중"}
            거리          $km
            급조향        ${t.sharpSteerCount}회
            브레이크      ${t.brakeCount}회 / ${t.brakeHoldTotalMs / 1000}초
            방향지시등    ${t.turnSignalCount}회 (크게 꺾음 ${t.bigTurnCount}회 중 미점등 ${t.turnWithoutSignalCount}회)
            미측정        ${t.missingData.ifEmpty { setOf("없음") }.joinToString()}
        """.trimIndent()
    }

    private fun describe(e: DriveEvent): String = when (e) {
        is DriveEvent.TripStarted -> "출발 (누적 ${e.odometerKm ?: "미측정"} km)"
        is DriveEvent.TripEnded -> "도착 · ${e.trip.distanceKm?.let { "%.1f km".format(it) } ?: "거리 미측정"}"
        is DriveEvent.SharpSteer -> "급조향 ${e.deltaDeg.toInt()}°"
        is DriveEvent.BrakeReleased -> "브레이크 ${e.holdMs}ms"
        is DriveEvent.TurnSignalOn -> "방향지시등 ${e.side}"
        is DriveEvent.TurnWithoutSignal -> "방향지시등 없이 꺾음 ${e.steeringDeg.toInt()}°"
        is DriveEvent.WindowOpened -> "창문 열림 ${e.level}"
        is DriveEvent.HeadlightOn -> "전조등 켜짐"
        is DriveEvent.DriverDoorOpened -> "운전석 문 열림" + if (e.parked) " (하차)" else ""
        is DriveEvent.PreExitCheck -> "하차 점검: " + e.items.ifEmpty { listOf("모두 정상") }.joinToString()
        is DriveEvent.BeltReminder -> "벨트 미착용 출발"
        is DriveEvent.DrivingModeChanged -> if (e.restricted) "주행 화면 모드" else "정차 화면 모드"
        is DriveEvent.LowEnergy -> "잔량 부족 ${e.pct}%"
        is DriveEvent.VehicleWarning -> "경고등 ${e.names.joinToString()}"
    }
}