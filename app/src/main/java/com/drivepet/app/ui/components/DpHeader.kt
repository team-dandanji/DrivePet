package com.drivepet.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/** 앱 이름 표기(피그마 시안은 Drivepet이지만 공식 이름은 Carpet). */
private const val APP_NAME = "Carpet"

/** 헤더 공통: 높이 80 줄, 위 40 / 아래 24 여백, 아래 1 구분선(피그마 page_navigation 토큰). */
@Composable
private fun HeaderFrame(modifier: Modifier = Modifier, start: Dp = 40.dp, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp).height(80.dp).padding(start = start, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DpColors.DividerTertiary))
    }
}

/** 헤더 오른쪽 ⋯(80 터치 영역, 56 아이콘). */
@Composable
private fun MoreButton(onClick: (() -> Unit)?) {
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).then(mod), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val r = 3.5.dp.toPx()
            listOf(0.2f, 0.5f, 0.8f).forEach { drawCircle(DpColors.Text, r, Offset(size.width * it, size.height / 2)) }
        }
    }
}

/** 꺾쇠(‹ 또는 ▾). [down]이면 아래 방향. */
@Composable
private fun Chevron(size: Int, down: Boolean, color: Color = DpColors.Text) {
    Canvas(Modifier.size(size.dp)) {
        val w = size.dp.toPx()
        val path = Path().apply {
            if (down) { moveTo(w * 0.25f, w * 0.38f); lineTo(w * 0.5f, w * 0.63f); lineTo(w * 0.75f, w * 0.38f) }
            else { moveTo(w * 0.62f, w * 0.22f); lineTo(w * 0.34f, w * 0.5f); lineTo(w * 0.62f, w * 0.78f) }
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 앱 아이콘 자리(80, r20, 핸들 모양). 실제 아이콘 에셋이 들어오면 이 Canvas만 바꾼다. */
@Composable
private fun AppIcon(side: Dp = 80.dp) {
    Canvas(Modifier.size(side).clip(RoundedCornerShape(20.dp)).background(DpColors.Filled).border(1.dp, DpColors.Divider, RoundedCornerShape(20.dp))) {
        val c = Offset(size.width / 2, size.height / 2)
        val stroke = Stroke(width = 5.dp.toPx())
        drawCircle(Color.White, size.width * 0.28f, c, style = stroke)
        drawCircle(Color.White, size.width * 0.07f, c)
        drawLine(Color.White, Offset(c.x - size.width * 0.28f, c.y), Offset(c.x + size.width * 0.28f, c.y), 5.dp.toPx())
        drawLine(Color.White, c, Offset(c.x, c.y + size.width * 0.28f), 5.dp.toPx())
    }
}

/**
 * 라운지 계열 상단: 앱 아이콘 80 + 「Carpet ▾」 + 밑줄 탭 + ⋯. 앱 이름 줄을 누르면 [onAppMenu], ⋯는 [onMoreMenu].
 * 탭은 32 Bold, 고른 탭은 글자색 Text + 4 밑줄, 나머지는 TextSecondary(419:50656).
 * 분할 세로 창(폭 840)에서는 간격·아이콘·앱 이름을 줄여 한 줄에 들어가게 한다(넓은 창 합계 약 980, 좁은 창 약 790 추정).
 */
@Composable
fun TabHeader(selected: Tab, onSelect: (Tab) -> Unit, onAppMenu: () -> Unit, onMoreMenu: () -> Unit, modifier: Modifier = Modifier) {
    val narrow = isNarrowWindow()
    HeaderFrame(modifier, start = if (narrow) 24.dp else 40.dp) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 16.dp else 48.dp)) {
            Row(Modifier.clickable(onClick = onAppMenu), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else 16.dp)) {
                AppIcon(if (narrow) 64.dp else 80.dp)
                Row(Modifier.padding(horizontal = if (narrow) 8.dp else 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(APP_NAME, style = if (narrow) DpType.titleS.copy(fontWeight = FontWeight.Bold) else DpType.titleL, color = DpColors.Text, maxLines = 1)
                    Chevron(28, down = true)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else 24.dp)) {
                Tab.entries.forEach { tab ->
                    val on = tab == selected
                    Box(
                        Modifier.height(80.dp).clickable { onSelect(tab) }.drawBehind {
                            if (on) drawRect(DpColors.Text, Offset(0f, size.height - 4.dp.toPx()), Size(size.width, 4.dp.toPx()))
                        }.padding(horizontal = if (narrow) 8.dp else 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tab.label, style = DpType.titleS.copy(fontWeight = FontWeight.Bold), color = if (on) DpColors.Text else DpColors.TextSecondary,
                            maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
        }
        MoreButton(onMoreMenu)
    }
}

/** 인트로 상단(408:44715): 앱 아이콘 + 「Carpet ▾」 + ⋯. 프로필이 없을 때라 메뉴는 열지 않는다. */
@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    HeaderFrame(modifier) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon()
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(APP_NAME, style = DpType.titleL, color = DpColors.Text, maxLines = 1)
                Chevron(28, down = true)
            }
        }
        MoreButton(null)
    }
}

/** 4칸 진행 막대의 칸 이름. */
private val STEP_LABELS = listOf("회원가입/로그인", "차량정보 확인하기", "소모품 이력 확인하기", "메이트 선택하기")

/**
 * 단계 화면 상단(403:36018): ‹ 제목 ⋯ 과 4칸 진행 막대. [step] 칸만 막대 progress/active · 글자 tertiary, 나머지는 inactive · quaternary.
 * 오른쪽 위 소단계 번호는 화면이 [StepDots]로 따로 놓는다.
 */
@Composable
fun StepHeader(title: String, step: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp).height(80.dp).padding(start = 40.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Chevron(56, down = false) }
            Text(title, style = DpType.titleL, color = DpColors.Text, maxLines = 1, modifier = Modifier.weight(1f))
            MoreButton(null)
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp).padding(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            STEP_LABELS.forEachIndexed { i, label ->
                val on = i == step // 지금 칸만 진하다(403:36018 · 403:36100 · 404:41673)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(if (on) DpColors.Filled else DpColors.TrackInactive))
                    Text(label, style = DpType.labelS, color = if (on) DpColors.TextTertiary else DpColors.TextQuaternary, maxLines = 1)
                }
            }
        }
    }
}
