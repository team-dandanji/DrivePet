package com.drivepet.app.ui.care

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.R
import com.drivepet.app.domain.Growth
import com.drivepet.app.domain.Mood
import com.drivepet.app.domain.formatExp
import com.drivepet.app.domain.gaugeExpText
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.PetMotion
import com.drivepet.app.ui.components.PetMotionView
import com.drivepet.app.ui.components.hasChickMotion
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.DpTag
import com.drivepet.app.ui.components.TagStyle
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.components.scrollWhen
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import com.drivepet.app.ui.theme.skyBackground
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val RISE_MS = 400
private const val GAUGE_MS = 800
private const val GAUGE_DELAY_MS = 200

/**
 * 정비 기록 완료(D6 419:53265). 넓은 창은 왼쪽 캐릭터·EXP 막대 / 오른쪽 결과·상세 카드, 좁은 창은 1단(CTA 폭 꽉).
 * [onGarage] = 차 정비소에서 기록 보기, [onLounge] = 확인했어요와 ✕.
 */
@Composable
fun CareDoneScreen(vm: GarageViewModel, recordId: String, starter: String, stage: Int, onGarage: () -> Unit, onLounge: () -> Unit) {
    val flow = remember(recordId) { vm.done(recordId) }
    val d by flow.collectAsStateWithLifecycle(null)
    val u = d ?: return
    val narrow = isNarrowWindow()
    val paidMilli = u.afterMilli - u.beforeMilli

    val pet: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RisingExpTag("+ ${formatExp(paidMilli)} EXP")
            Box(Modifier.then(if (narrow) Modifier.height(500.dp) else Modifier.weight(1f)).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 보상 모션 798×618(반복). 모션이 없는 캐릭터는 정지 그림
                if (hasChickMotion(starter, stage)) PetMotionView(PetMotion.REWARD_CHICK, Modifier.fillMaxSize())
                else Character(starter, stage, Mood.HAPPY, size = 440.dp)
            }
            ExpGauge(u.beforeMilli, u.afterMilli)
        }
    }
    val result: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("${u.title}${objectParticle(u.title)}\n기록했어요", style = DpType.headlineS, color = DpColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                Text(
                    "${u.date.monthValue}월 ${u.date.dayOfMonth}일", style = DpType.titleM, color = DpColors.TextTertiary, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(36.dp)).background(DpColors.ButtonBasic).padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Column {
                    Text("다음 점검까지", Modifier.padding(vertical = 4.dp), style = DpType.titleL.copy(fontWeight = FontWeight.Normal), color = DpColors.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(u.nextDueKm?.let { String.format(Locale.US, "%,d", it) } ?: "미산출", style = DpType.displayL.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.Text)
                        if (u.nextDueKm != null) Text("km", style = DpType.displayL, color = DpColors.Text)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(CARD).background(DpColors.Card).border(1.dp, DpColors.Divider, CARD).padding(44.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Text("Service Details", style = DpType.titleS.copy(fontWeight = FontWeight.Normal), color = DpColors.TextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoRow("관리일", DateTimeFormatter.ofPattern("yyyy.MM.dd").format(u.date))
                    InfoRow("관리 항목", u.title)
                    InfoRow("기준 누적거리", km(u.baseOdometerKm))
                    InfoRow("관리 후 누적거리", km(u.sinceKm))
                    InfoRow("건강점수", "항목별 배점으로 재계산")
                }
            }
            // 정비 기록만으로 차량 경고가 해제되지는 않는다는 안내(피그마에는 없는 안전 문구).
            if (u.tpmsOn) Text("TPMS 경고 유지 · 정비 기록만으로 차량 경고가 해제되지는 않아요", style = DpType.bodyS, color = DpColors.TextSecondary)
        }
    }

    Column(Modifier.fillMaxSize().skyBackground().scrollWhen(narrow)) {
        Row(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.End) {
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onLounge), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_close), "닫기", Modifier.size(56.dp), tint = DpColors.Text)
            }
        }
        if (narrow) {
            Column(Modifier.padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
                pet(Modifier.fillMaxWidth())
                result(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(104.dp)) {
                pet(Modifier.weight(1f).fillMaxSize())
                result(Modifier.weight(1f))
            }
        }
        if (narrow) {
            Column(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 68.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                DpCta("차 정비소에서 기록 보기", onGarage, Modifier.fillMaxWidth(), primary = false)
                DpCta("확인했어요 (라운지로)", onLounge, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 68.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            ) {
                DpCta("차 정비소에서 기록 보기", onGarage, Modifier.width(616.dp), primary = false)
                DpCta("확인했어요 (라운지로)", onLounge, Modifier.width(616.dp))
            }
        }
    }
}

private val CARD = RoundedCornerShape(16.dp)

private fun km(v: Int?) = v?.let { String.format(Locale.US, "%,d km", it) } ?: "미측정"

/** +EXP 칩: 아래에서 떠오르며 나타난다(400ms, ease-out, 한 번). */
@Composable
private fun RisingExpTag(text: String) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(RISE_MS, easing = EaseOut)) }
    DpTag(text, TagStyle.Glass, Modifier.alpha(rise.value).offset(y = (24 * (1f - rise.value)).dp))
}

/** 정비 전 → 후 총 EXP 막대. 칩이 다 뜬 뒤(400ms) 200ms 있다가(정산 화면과 같다) 800ms ease-out으로 채운다. 레벨 경계를 넘으면 새 레벨 기준으로 다시 시작한다. */
@Composable
private fun ExpGauge(beforeMilli: Long, afterMilli: Long) {
    val anim = remember(beforeMilli, afterMilli) { Animatable(beforeMilli.toFloat()) }
    LaunchedEffect(anim) { anim.animateTo(afterMilli.toFloat(), tween(GAUGE_MS, RISE_MS + GAUGE_DELAY_MS, EaseOut)) }
    val g = Growth.gauge(anim.value.toLong())
    Column(Modifier.fillMaxWidth().padding(horizontal = 160.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { g.percent / 100f }, color = DpColors.Filled, trackColor = DpColors.TrackInactive,
            drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
            g.needMilli?.let { gaugeExpText(g.inLevelMilli, it) } ?: "MAX",
            style = DpType.labelM, color = DpColors.TextSecondary,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(240.dp), style = DpType.bodyM, color = DpColors.TextTertiary)
        Text(value, style = DpType.bodyS, color = DpColors.Text)
    }
}
