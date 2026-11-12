package com.drivepet.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

enum class TagStyle { Positive, Negative, Caution, Neutral, Glass, Done, Selected, Sky, Reward, RewardSoft, Date }

/**
 * 피그마 DpTag. 스타일마다 모양이 정해져 있다(노드: 440:64177 · 440:64130 · 404:41673 · 419:51516 · 419:50656).
 * M(24/28, 패딩 16×8, r12): Positive·Negative·Caution / Selected: 같은 글자, 패딩 12×8 / S(20/24, 패딩 8×6, r12): Neutral·Done /
 * Sky: 26/34 Bold, 패딩 12×8, 테두리 1 / L(36/44, 패딩 20×8, r36): Glass.
 * 정산(419:51078): Reward = sky_300 바탕 20/24 Regular, 패딩 8×6, r12, 글자 Text(폭은 호출 쪽에서 104) /
 * RewardSoft = sky_50 바탕 80% 불투명, 40/52 Bold sky_500, 패딩 20×12, r28 / Date = 연한 회색 바탕, 36/44 Bold TextTertiary, 패딩 20×8, r36.
 */
@Composable
fun DpTag(text: String, style: TagStyle, modifier: Modifier = Modifier) {
    val (bg, fg) = when (style) {
        TagStyle.Positive -> DpColors.Positive to Color.White
        TagStyle.Negative -> DpColors.Danger to Color.White
        TagStyle.Caution -> DpColors.Warning to Color.White
        TagStyle.Neutral -> DpColors.TagNeutral to Color.White
        TagStyle.Glass -> DpColors.GlassStrong to DpColors.TextTertiary
        TagStyle.Done -> Color.White to DpColors.Text
        TagStyle.Selected -> DpColors.Filled to Color.White
        TagStyle.Sky -> DpColors.SoftTop to DpColors.TextTertiary
        TagStyle.Reward -> DpColors.Chip to DpColors.Text
        TagStyle.RewardSoft -> DpColors.Sky50 to DpColors.Sky500
        TagStyle.Date -> DpColors.ButtonBasic to DpColors.TextTertiary
    }
    val type = when (style) {
        TagStyle.Neutral, TagStyle.Done, TagStyle.Reward -> DpType.labelS
        TagStyle.Glass, TagStyle.Date -> DpType.titleM
        TagStyle.RewardSoft -> DpType.titleL
        TagStyle.Sky -> DpType.bodyS
        else -> DpType.labelM
    }
    val (padH, padV, radius) = when (style) {
        TagStyle.Neutral, TagStyle.Done, TagStyle.Reward -> Triple(8, 6, 12)
        TagStyle.Glass -> Triple(20, 8, 36)
        TagStyle.Date -> Triple(20, 8, 36)
        TagStyle.RewardSoft -> Triple(20, 12, 28)
        TagStyle.Selected, TagStyle.Sky -> Triple(12, 8, 12)
        else -> Triple(16, 8, 12)
    }
    val shape = RoundedCornerShape(radius.dp)
    // 흰 태그와 하늘색 태그는 1 테두리(divider_primary)가 있다.
    val outlined = style == TagStyle.Done || style == TagStyle.Sky
    Text(
        text, style = type, color = fg, maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = (if (style == TagStyle.RewardSoft) modifier.alpha(0.8f) else modifier).clip(shape).background(bg).then(if (outlined) Modifier.border(1.dp, DpColors.DividerPrimary, shape) else Modifier)
            .padding(horizontal = padH.dp, vertical = padV.dp),
    )
}

/**
 * 하단 CTA: 높이 100, radius 16, 글자 32 Bold. primary는 차콜 바탕, 아니면 연회색(보조).
 * 비활성은 회색 바탕에 흰 글자다(403:36018).
 */
@Composable
fun DpCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = true) {
    Button(
        onClick, modifier.height(100.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) DpColors.Filled else DpColors.ButtonBasic,
            contentColor = if (primary) Color.White else DpColors.Text,
            disabledContainerColor = DpColors.ButtonBasicPressed,
            disabledContentColor = DpColors.TextLightTertiary,
        ),
        border = if (enabled) null else BorderStroke(1.dp, DpColors.DividerPrimary),
    ) { Text(text, style = DpType.titleS.copy(fontWeight = FontWeight.Bold)) }
}

enum class DotState { Done, Current, Todo }

/** 소단계 번호 상태: current 앞은 Done, current는 Current, 뒤는 Todo. */
fun stepDots(total: Int, current: Int): List<DotState> =
    List(total) { if (it < current) DotState.Done else if (it == current) DotState.Current else DotState.Todo }

/** 오른쪽 위 소단계 번호(지름 60): 끝남 ✓ / 지금 채움 / 남음 빈 원. */
@Composable
fun StepDots(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
        stepDots(total, current).forEachIndexed { i, state ->
            val base = Modifier.size(60.dp)
            val outline = base.background(DpColors.SurfaceLow, CircleShape)
            // 끝남(403:36047): 차콜 채움 원에 흰 ✓(Asta Sans에 ✓ 글리프가 없어 Canvas). 지금: 차콜 채움 + 흰 숫자. 남음(403:36018): 연한 테두리 3 + 회색 숫자.
            when (state) {
                DotState.Done -> Canvas(base.background(DpColors.Filled, CircleShape)) {
                    val w = size.width
                    val check = Path().apply { moveTo(w * 0.30f, w * 0.52f); lineTo(w * 0.43f, w * 0.64f); lineTo(w * 0.70f, w * 0.37f) }
                    drawPath(check, Color.White, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                DotState.Current -> Box(base.background(DpColors.Filled, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = DpType.titleS.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
                DotState.Todo -> Box(outline.border(3.dp, DpColors.TrackInactive, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = DpType.titleS.copy(fontWeight = FontWeight.Bold), color = DpColors.TrackInactive)
                }
            }
        }
    }
}

/** 탭·입력 화면 안 흰 유리 카드: Card 바탕, radius 20. */
@Composable
fun DpCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(DpColors.Card, RoundedCornerShape(20.dp))) { content() }
}

/** 둘 중 하나·여러 개 중 하나 고르는 칸(높이 72, radius 16). 고른 칸은 차콜 바탕에 흰 글자, 아니면 흰 바탕에 연한 테두리. */
@Composable
fun DpChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(72.dp).background(if (selected) DpColors.Filled else Color.White, shape)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(2.dp, DpColors.TrackInactive), shape))
            .selectable(selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DpType.bodyS, color = if (selected) Color.White else DpColors.Text, maxLines = 1, softWrap = false)
    }
}
