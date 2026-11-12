package com.drivepet.app.ui.settlement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.drivepet.app.R
import com.drivepet.app.domain.Growth
import com.drivepet.app.domain.MeasurementStatus
import com.drivepet.app.domain.SafetyItem
import com.drivepet.app.domain.Mood
import com.drivepet.app.domain.SafetyLine
import com.drivepet.app.domain.Settlement
import com.drivepet.app.domain.formatExp
import com.drivepet.app.drive.RecorderOutput
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.DpTag
import com.drivepet.app.ui.components.TagStyle
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SCORE_MS = 600
private const val EXP_MS = 400
private const val GAUGE_MS = 800
private const val GAUGE_DELAY_MS = 200
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

private fun kmText(mm: Long) = String.format(Locale.US, "%.1f", mm / 1_000_000.0).removeSuffix(".0")

/** 보상 칩 글자: 「+24.0」→「+24.0EXP」, 「0」→「+0EXP」, 「-」·「미측정」은 그대로(칩 없이 글자만). */
private fun expValue(t: String) = when {
    t.startsWith("+") -> "${t}EXP"
    t == "0" -> "+0EXP"
    else -> t
}

/** 피그마 보상 4줄(419:51078): 거리 / 급조향 / 공조 중복 / 방향지시등. 오른쪽 값은 「+{exp}EXP」, 측정 안 된 줄은 「미측정」. */
fun rewardRows(s: Settlement, paidMilli: Long): List<Pair<String, String>> {
    val withheld = bonusWithheld(paidMilli, s)
    val steer = s.breakdown?.firstOrNull { it.item == SafetyItem.STEERING }
    val signal = s.breakdown?.firstOrNull { it.item == SafetyItem.TURN_SIGNAL }
    return listOf(
        (s.distanceMm?.let { "거리 ${kmText(it)}km × 2EXP" } ?: "거리 × 2EXP") to expValue(drivingExpText(s, paidMilli)),
        (if (steer.measured() && steer!!.value != null) "급조향 ${steer.value!!.toInt()}회" else "급조향") to expValue(steeringBonusText(steer, withheld)),
        "공조 중복" to "미측정", // 공조 중복은 아직 측정하지 않는다
        "방향지시등 ${SIGNAL_BONUS_PERCENT.toInt()}% 이상" to expValue(signalBonusText(signal, withheld)),
    )
}

/**
 * 13 주차 후 트립 정산(D10, 419:51078). 점수 카운트업 600ms → 「+EXP」 400ms(떠오르며 나타남) → 200ms 뒤 EXP 막대 800ms.
 * 오른쪽 위 ✕는 [확인했어요 (라운지로)]와 같다. [기록 리플레이]는 아직 아무 동작이 없다.
 */
@Composable
fun SettlementScreen(out: RecorderOutput.Settled, beforeMilli: Long, afterMilli: Long, starter: String, stage: Int, onConfirm: () -> Unit) {
    val s = out.settlement
    val paidMilli = afterMilli - beforeMilli
    val narrow = isNarrowWindow()
    val date = remember { LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("M월 d일")) }
    val measured = s.safetyScore != null && s.safetyStatus == MeasurementStatus.MEASURED

    val left: @Composable (Modifier) -> Unit = { m ->
        Column(m, Arrangement.spacedBy(16.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
            ExpChip(paidMilli)
            Character(starter, stage, Mood.HAPPY)
            CumulativeGauge(beforeMilli, afterMilli, narrow)
        }
    }
    val title: @Composable () -> Unit = {
        Text(
            if (measured) "오늘도 수고했어요!\n안정적인 주행이었어요" else "오늘도 수고했어요!",
            style = DpType.headlineS, color = DpColors.Text,
        )
    }
    val info: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(48.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                DpTag(date, TagStyle.Date)
                Column {
                    Text("안전운전 점수", style = DpType.titleL.copy(fontWeight = FontWeight.Normal), color = DpColors.Text, modifier = Modifier.padding(vertical = 4.dp))
                    SafetyScore(if (measured) s.safetyScore else null)
                }
            }
            RewardCard(rewardRows(s, paidMilli), bonusWithheld(paidMilli, s))
        }
    }
    val replay: @Composable (Modifier) -> Unit = { DpCta("기록 리플레이", {}, it, primary = false) }
    val confirm: @Composable (Modifier) -> Unit = { DpCta("확인했어요 (라운지로)", onConfirm, it) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.End) {
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onConfirm), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_close), "닫기", Modifier.size(56.dp), tint = DpColors.Text)
            }
        }
        if (narrow) {
            // 분할 창: 1단으로 쌓고 스크롤. CTA는 폭을 꽉 채운다.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp).padding(bottom = 40.dp), Arrangement.spacedBy(32.dp)) {
                left(Modifier.fillMaxWidth())
                title()
                info()
                replay(Modifier.fillMaxWidth())
                confirm(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                left(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), Arrangement.SpaceBetween) { title(); info() }
            }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 40.dp), Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)) {
                replay(Modifier.width(616.dp))
                confirm(Modifier.width(616.dp))
            }
        }
    }
}

/** 펫 위 「+ 00 EXP」(RewardSoft): 400ms 동안 아래에서 떠오르며 나타난다(한 번). */
@Composable
private fun ExpChip(paidMilli: Long) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(p) { p.animateTo(1f, tween(EXP_MS, easing = EaseOut)) }
    DpTag("+ ${formatExp(paidMilli)} EXP", TagStyle.RewardSoft, Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 24.dp.toPx() })
}

/** 안전운전 점수 큰 숫자(112/134 Bold). 0에서 600ms 동안 센다. 미측정이면 「미측정」(0점으로 바꾸지 않는다). */
@Composable
private fun SafetyScore(score: Int?) {
    val shown = remember(score) { Animatable(0f) }
    LaunchedEffect(shown, score) { if (score != null) shown.animateTo(score.toFloat(), tween(SCORE_MS, easing = EaseOut)) }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (score != null) {
            Text(
                "${shown.value.toInt()}", color = DpColors.TextSecondary,
                style = DpType.displayL.copy(fontSize = 112.sp, lineHeight = 134.sp, letterSpacing = (-3.36).sp),
            )
            Text("/ 100점", Modifier.padding(bottom = 20.dp), style = DpType.titleM.copy(fontWeight = FontWeight.Normal), color = DpColors.TextSecondary)
        } else {
            Text("미측정", style = DpType.displayL.copy(lineHeight = 134.sp), color = DpColors.TextTertiary)
        }
    }
}

/** Today's Driving Rewards 카드(glass, r16, 패딩 44, 테두리 1). */
@Composable
private fun RewardCard(rows: List<Pair<String, String>>, withheld: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(DpColors.Card).border(1.dp, DpColors.Divider, shape).padding(44.dp),
        Arrangement.spacedBy(32.dp),
    ) {
        Text("Today's Driving Rewards", style = DpType.titleS.copy(fontWeight = FontWeight.Normal), color = DpColors.TextSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = DpType.bodyS, color = DpColors.Text)
                    if (value.endsWith("EXP")) DpTag(value, TagStyle.Reward, Modifier.widthIn(min = 104.dp))
                    else Text(value, style = DpType.labelS, color = DpColors.TextTertiary)
                }
            }
        }
        if (withheld) Text("중복 구간·1km 미만은 보너스 없음", style = DpType.labelS, color = DpColors.TextTertiary)
    }
}

/** 정산 전 → 후 총 EXP 막대(8, 가운데 폭 제한). 「+EXP」가 나타난 200ms 뒤 800ms 동안 채우며, 레벨 경계를 넘으면 새 레벨 기준으로 다시 시작한다. */
@Composable
private fun CumulativeGauge(beforeMilli: Long, afterMilli: Long, narrow: Boolean) {
    val anim = remember(beforeMilli, afterMilli) { Animatable(beforeMilli.toFloat()) }
    LaunchedEffect(anim) {
        delay((EXP_MS + GAUGE_DELAY_MS).toLong())
        anim.animateTo(afterMilli.toFloat(), tween(GAUGE_MS, easing = EaseOut))
    }
    val g = Growth.gauge(anim.value.toLong())
    Column(Modifier.fillMaxWidth().padding(horizontal = if (narrow) 80.dp else 160.dp), Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(DpColors.TrackInactive)) {
            Box(Modifier.fillMaxWidth(g.percent / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(DpColors.Filled))
        }
        Text(g.needMilli?.let { "${formatExp(g.inLevelMilli)} / ${formatExp(it)} EXP" } ?: "MAX", style = DpType.labelM, color = DpColors.TextSecondary)
    }
}
