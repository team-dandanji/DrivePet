package com.drivepet.app.ui.lounge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.BuildConfig
import com.drivepet.app.domain.formatExp
import com.drivepet.app.drive.Speaker
import com.drivepet.app.ui.components.Character
import com.drivepet.app.domain.MissionStatus
import com.drivepet.app.domain.MissionKind
import androidx.compose.foundation.layout.PaddingValues
import com.drivepet.app.ui.components.faceOf
import com.drivepet.app.ui.components.SpeechBubble
import com.drivepet.app.ui.theme.DpColors

private const val MAX_WARNING_ROWS = 3

/** 07 · 08 메인 라운지. 08(이상 감지)은 같은 화면에서 특이사항·CTA가 채워진 상태다. 프로필이 없으면 아무것도 그리지 않는다. */
@Composable
fun LoungeScreen(vm: LoungeViewModel, speaker: Speaker, onCare: (CareAction) -> Unit, onDev: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val bubble by speaker.bubble.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.greetIfFirstToday() }
    val u = ui ?: return

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxSize()) {
            Text("${u.nickname} ${faceOf(u.mood)}", fontSize = 60.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold)
            VehicleLine(u.vehicleLine, weather)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Character(
                    u.starter, u.stage, u.mood,
                    Modifier.then(if (BuildConfig.DEMO_TOOLS) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onDev() }) } else Modifier),
                )
            }
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { SpeechBubble(bubble) }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (u.parked) Chip("주차중")
                Box(Modifier.weight(1f))
                if (u.showCheckInButton) {
                    Button(
                        vm::checkInNow, Modifier.height(72.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DpColors.Filled, contentColor = Color.White),
                    ) { Text("출석하기", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                }
            }
            ExpCard(u, vm::claim)
        }
        Column(Modifier.width(592.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("펫 컨디션", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("배터리", u.satietyPct, "%", Modifier.weight(1f))
                StatCard("건강점수", u.healthPct, "/ 100점", Modifier.weight(1f))
            }
            WarningCard(u, onCare, Modifier.weight(1f))
        }
    }
}

/** 07: 펫 이름 아래 차량 줄 뒤에 "맑음 · 18°C"를 잇고, 미세먼지·경고·이전 정보는 작은 글씨로 뒤따른다. 날씨가 없으면 차량 이름만. */
@Composable
private fun VehicleLine(vehicle: String, w: WeatherUi?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (w == null) vehicle else "$vehicle  /  ${w.label} · ${w.temp}", fontSize = 26.sp, color = DpColors.TextTertiary)
        if (w != null) {
            w.air?.let { Text(it, fontSize = 20.sp, color = DpColors.TextTertiary) }
            w.warning?.let { Text(it, fontSize = 20.sp, color = DpColors.Danger, fontWeight = FontWeight.Bold) }
            if (w.stale) Text("이전 정보", fontSize = 20.sp, color = DpColors.TextTertiary)
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, Modifier.background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 8.dp),
        fontSize = 24.sp, color = DpColors.TextTertiary,
    )
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp)).padding(24.dp)) { content() }
}

@Composable
private fun ExpCard(u: LoungeUi, onClaim: (MissionKind) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("LV. ${u.gauge.level}", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    u.gauge.needMilli?.let { "${formatExp(u.gauge.inLevelMilli)} / ${formatExp(it)} EXP" } ?: "MAX",
                    fontSize = 26.sp, color = DpColors.TextTertiary,
                )
                LinearProgressIndicator(
                    progress = { u.gauge.percent / 100f }, color = DpColors.Filled, trackColor = DpColors.TrackInactive,
                    drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(12.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Today's mission", fontSize = 24.sp, color = DpColors.TextTertiary)
                u.missions.forEach { m -> MissionRow(m) { onClaim(m.kind) } }
            }
        }
    }
}

/** 미션 한 줄: 제목(+진행), 오른쪽에 보상 칩 / 받기 / 받음. */
@Composable
private fun MissionRow(m: MissionStatus, onClaim: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(m.kind.title, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (m.progress.isNotEmpty()) Text(m.progress, Modifier.padding(end = 12.dp), fontSize = 20.sp, color = DpColors.TextTertiary)
        when {
            m.claimed -> Text("받음", fontSize = 22.sp, color = DpColors.TextTertiary)
            m.done -> Button(
                onClaim, Modifier.height(48.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DpColors.Filled, contentColor = Color.White),
            ) { Text("받기 +30", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            else -> Text(
                "+30EXP", Modifier.background(DpColors.TrackInactive, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                fontSize = 20.sp, color = DpColors.Text,
            )
        }
    }
}

/** value가 null이면 "미측정"(태그 없음). */
@Composable
private fun StatCard(label: String, value: Int?, unit: String, modifier: Modifier) {
    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (value != null) {
                val low = value <= LOW_PCT
                Text(
                    if (low) "부족해요" else "건강해요",
                    Modifier.background(if (low) DpColors.Danger else DpColors.Positive, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 4.dp),
                    fontSize = 22.sp, color = Color.White,
                )
            } else {
                Box(Modifier.height(36.dp))
            }
            Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (value == null) {
                Text("미측정", fontSize = 48.sp, lineHeight = 88.sp, fontWeight = FontWeight.Bold, color = DpColors.TextTertiary)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$value", fontSize = 72.sp, lineHeight = 88.sp, fontWeight = FontWeight.Bold)
                    Text(unit, Modifier.padding(start = 6.dp, bottom = 12.dp), fontSize = 24.sp, color = DpColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun WarningCard(u: LoungeUi, onCare: (CareAction) -> Unit, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("특이사항", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("${u.warnings.size}", fontSize = 32.sp, color = DpColors.TextTertiary)
            }
            if (u.warnings.isEmpty()) {
                Text("특이사항 없음", fontSize = 28.sp, color = DpColors.TextTertiary)
            } else {
                u.warnings.take(MAX_WARNING_ROWS).forEach { name ->
                    Text(
                        name, Modifier.fillMaxWidth().border(BorderStroke(1.dp, DpColors.Divider), RoundedCornerShape(12.dp)).padding(16.dp),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    )
                }
                if (u.warnings.size > MAX_WARNING_ROWS) {
                    Text("외 ${u.warnings.size - MAX_WARNING_ROWS}건", fontSize = 24.sp, color = DpColors.TextTertiary)
                }
            }
            Box(Modifier.weight(1f))
            u.careAction?.let {
                Button(
                    { onCare(it) }, Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DpColors.Filled, contentColor = Color.White),
                ) { Text(it.label, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
