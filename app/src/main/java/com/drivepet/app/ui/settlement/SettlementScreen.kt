package com.drivepet.app.ui.settlement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.domain.Growth
import com.drivepet.app.domain.MeasurementStatus
import com.drivepet.app.domain.Mood
import com.drivepet.app.domain.SafetyItem
import com.drivepet.app.domain.SafetyLine
import com.drivepet.app.domain.Settlement
import com.drivepet.app.domain.formatExp
import com.drivepet.app.drive.RecorderOutput
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.theme.DpColors
import java.util.Locale

private const val GAUGE_MS = 1000
private const val SIGNAL_BONUS_PERCENT = 80.0

/** 원장이 이번 트립에 실제로 지급한 EXP(after − before)가 정산의 주행+보너스보다 적으면 보너스(또는 중복 구간)가 인정되지 않은 것이다. */
fun bonusWithheld(paidMilli: Long, s: Settlement): Boolean = paidMilli < (s.baseDrivingExpMilli ?: 0L) + s.bonusMilli

/** "주행 EXP" 줄: 거리 기반 값이 있으면 원장이 실제 지급한 만큼까지만(중복 구간이면 0), 없으면 "미측정". */
fun drivingExpText(s: Settlement, paidMilli: Long): String =
    s.baseDrivingExpMilli?.let { "+${formatExp(minOf(it, paidMilli))}" } ?: "미측정"

private fun SafetyLine?.measured() = this != null && status == MeasurementStatus.MEASURED && score != null

/** 급조향 없음 보너스 칸: 측정 못 했으면 "미측정", 보너스가 인정 안 됐으면 "0", 만점이면 "+15", 아니면 "-". */
fun steeringBonusText(line: SafetyLine?, withheld: Boolean = false): String = when {
    !line.measured() -> "미측정"
    withheld -> "0"
    line!!.score == line.item.maxScore -> "+15"
    else -> "-"
}

/** 방향지시등 80% 이상 보너스 칸. */
fun signalBonusText(line: SafetyLine?, withheld: Boolean = false): String = when {
    !line.measured() || line!!.value == null -> "미측정"
    withheld -> "0"
    line.value!! >= SIGNAL_BONUS_PERCENT -> "+20"
    else -> "-"
}

private fun itemName(i: SafetyItem) = when (i) {
    SafetyItem.ACCEL_BRAKE -> "급가속·급감속"
    SafetyItem.STEERING -> "급조향"
    SafetyItem.EFFICIENCY -> "전비"
    SafetyItem.HVAC_OVERLAP -> "공조 동시 사용"
    SafetyItem.TURN_SIGNAL -> "방향지시등"
}

/** 13 주차 후 트립 정산. 정차 화면이라 게이지 애니메이션을 쓴다. */
@Composable
fun SettlementScreen(out: RecorderOutput.Settled, beforeMilli: Long, afterMilli: Long, starter: String, stage: Int, onConfirm: () -> Unit) {
    val s = out.settlement
    val lines = SafetyItem.entries.map { item -> item to s.breakdown?.firstOrNull { it.item == item } }
    val byItem = lines.toMap()
    val distance = s.distanceMm?.let { String.format(Locale.US, "%.1f km", it / 1_000_000.0) } ?: "미측정"
    val paidMilli = afterMilli - beforeMilli
    val withheld = bonusWithheld(paidMilli, s)

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(600.dp).fillMaxSize(), Arrangement.spacedBy(16.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
            Text(
                "+${formatExp(paidMilli)} EXP", Modifier.background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 28.dp, vertical = 10.dp),
                fontSize = 36.sp, fontWeight = FontWeight.Bold, color = DpColors.Bubble,
            )
            Character(starter, stage, Mood.HAPPY)
            CumulativeGauge(beforeMilli, afterMilli)
        }
        Column(Modifier.weight(1f).fillMaxSize(), Arrangement.spacedBy(16.dp)) {
            Text("오늘도 수고했어요!", fontSize = 60.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("안전운전 점수", Modifier.padding(bottom = 14.dp), fontSize = 30.sp)
                if (s.safetyScore != null && s.safetyStatus == MeasurementStatus.MEASURED) {
                    Text("${s.safetyScore}", fontSize = 96.sp, lineHeight = 104.sp, fontWeight = FontWeight.Bold)
                    Text("/ 100점", Modifier.padding(bottom = 14.dp), fontSize = 28.sp, color = DpColors.TextTertiary)
                } else {
                    Text("미측정", fontSize = 56.sp, lineHeight = 104.sp, fontWeight = FontWeight.Bold, color = DpColors.TextTertiary)
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    lines.forEach { (item, line) -> SafetyBar(itemName(item), line, item.maxScore) }
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Today's Driving Rewards", fontSize = 26.sp, color = DpColors.TextTertiary)
                    RewardRow("거리", distance)
                    RewardRow("주행 EXP", drivingExpText(s, paidMilli))
                    RewardRow("급조향 없음", steeringBonusText(byItem[SafetyItem.STEERING], withheld))
                    RewardRow("방향지시등 80% 이상", signalBonusText(byItem[SafetyItem.TURN_SIGNAL], withheld))
                    RewardRow("공조 중복", "미측정")
                    if (withheld) Text("중복 구간·1km 미만은 보너스 없음", fontSize = 22.sp, color = DpColors.TextTertiary)
                }
            }
            Box(Modifier.weight(1f))
            FilledButton("확인", onConfirm, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp)).padding(horizontal = 28.dp, vertical = 20.dp)) { content() }
}

@Composable
private fun RewardRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 26.sp)
        if (value.startsWith("+")) {
            // Hi-fi: 받은 보상은 하늘색 칩
            Text("$value EXP", Modifier.background(DpColors.Chip, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 2.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (value == "미측정") DpColors.TextTertiary else DpColors.Text)
        }
    }
}

/** 미측정이면 "미측정"과 빈 트랙(0점으로 바꾸지 않는다). */
@Composable
private fun SafetyBar(name: String, line: SafetyLine?, max: Int) {
    val score = if (line.measured()) line!!.score!! else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontSize = 26.sp)
            Text(score?.let { "$it / $max" } ?: "미측정", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (score == null) DpColors.TextTertiary else DpColors.Text)
        }
        LinearProgressIndicator(
            progress = { (score ?: 0) / max.toFloat() }, color = DpColors.Filled, trackColor = DpColors.TrackInactive,
            drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(10.dp),
        )
    }
}

/** 정산 전 → 후 총 EXP를 1초 동안 채운다. 레벨 경계를 넘으면 막대가 새 레벨 기준으로 다시 시작한다. */
@Composable
private fun CumulativeGauge(beforeMilli: Long, afterMilli: Long) {
    val anim = remember(beforeMilli, afterMilli) { Animatable(beforeMilli.toFloat()) }
    LaunchedEffect(anim) { anim.animateTo(afterMilli.toFloat(), tween(GAUGE_MS)) }
    val g = Growth.gauge(anim.value.toLong())
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LV. ${g.level}", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text(g.needMilli?.let { "${formatExp(g.inLevelMilli)} / ${formatExp(it)} EXP" } ?: "MAX", fontSize = 26.sp, color = DpColors.TextTertiary)
        }
        LinearProgressIndicator(
            progress = { g.percent / 100f }, color = DpColors.Filled, trackColor = DpColors.TrackInactive,
            drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(12.dp),
        )
    }
}
