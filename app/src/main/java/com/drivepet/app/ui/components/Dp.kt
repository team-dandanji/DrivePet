package com.drivepet.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

enum class TagStyle { Positive, Negative, Caution, Neutral, Glass, Done, Selected, Sky }

/**
 * 라운지 앱 창(419:50656)의 DpTag. 크기는 스타일이 정한다.
 * M(24/28, 패딩 16×8, r12): Positive·Negative·Caution·Selected·Sky / S(20/24, 8×6, r12): Neutral·Done / L(36/44, 20×8, r36): Glass.
 */
@Composable
fun DpTag(text: String, style: TagStyle, modifier: Modifier = Modifier) {
    val (bg, fg) = when (style) {
        TagStyle.Positive -> DpColors.Positive to Color.White
        TagStyle.Negative -> DpColors.Danger to Color.White
        TagStyle.Caution -> DpColors.Warning to Color.White
        TagStyle.Neutral -> DpColors.TagNeutral to Color.White
        TagStyle.Glass -> DpColors.GlassStrong to DpColors.TextTertiary
        TagStyle.Done -> DpColors.ButtonBasic to DpColors.TextTertiary
        TagStyle.Selected -> DpColors.Filled to Color.White
        TagStyle.Sky -> DpColors.Chip to DpColors.Text
    }
    val type = when (style) {
        TagStyle.Neutral, TagStyle.Done -> DpType.labelS
        TagStyle.Glass -> DpType.titleM
        else -> DpType.labelM
    }
    val (padH, padV, radius) = when (style) {
        TagStyle.Neutral, TagStyle.Done -> Triple(8, 6, 12)
        TagStyle.Glass -> Triple(20, 8, 36)
        else -> Triple(16, 8, 12)
    }
    Text(
        text, style = type, color = fg, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(radius.dp)).background(bg).padding(horizontal = padH.dp, vertical = padV.dp),
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
            val (mod, color, label) = when (state) {
                DotState.Current -> Triple(base.background(DpColors.Filled, CircleShape), Color.White, "${i + 1}")
                DotState.Done -> Triple(outline.border(3.dp, DpColors.Filled, CircleShape), DpColors.Filled, "✓")
                DotState.Todo -> Triple(outline.border(3.dp, DpColors.TrackInactive, CircleShape), DpColors.TrackInactive, "${i + 1}")
            }
            Box(mod, contentAlignment = Alignment.Center) {
                Text(label, style = DpType.titleS.copy(fontWeight = FontWeight.Bold), color = color)
            }
        }
    }
}
