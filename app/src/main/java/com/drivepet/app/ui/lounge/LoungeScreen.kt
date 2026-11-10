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
import com.drivepet.app.ui.components.SpeechBubble
import com.drivepet.app.ui.theme.DpColors

private const val MAX_WARNING_ROWS = 3

/** 07 · 08 메인 라운지. 08(이상 감지)은 같은 화면에서 특이사항·CTA가 채워진 상태다. 프로필이 없으면 아무것도 그리지 않는다. */
@Composable
fun LoungeScreen(vm: LoungeViewModel, speaker: Speaker, onDev: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val bubble by speaker.bubble.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.greetIfFirstToday() }
    val u = ui ?: return

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxSize()) {
            Text(u.nickname, fontSize = 60.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold)
            Text(u.vehicleLine, fontSize = 26.sp, color = DpColors.TextTertiary)
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
            ExpCard(u)
        }
        Column(Modifier.width(592.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("펫 컨디션", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("배터리", u.satietyPct, "%", Modifier.weight(1f))
                StatCard("건강점수", u.healthPct, "/ 100점", Modifier.weight(1f))
            }
            WarningCard(u, vm::careTapped, Modifier.weight(1f))
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
private fun ExpCard(u: LoungeUi) {
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Today's mission", fontSize = 24.sp, color = DpColors.TextTertiary)
                Text("2부에서 열려요", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
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
private fun WarningCard(u: LoungeUi, onCare: () -> Unit, modifier: Modifier) {
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
                    onCare, Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DpColors.Filled, contentColor = Color.White),
                ) { Text(it.label, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
