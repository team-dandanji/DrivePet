package com.drivepet.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.StepDots
import com.drivepet.app.ui.components.StepHeader
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.components.scrollWhen
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import com.drivepet.app.ui.theme.softBackground

/** 하단 주 CTA(616, 비활성은 회색). */
data class StepCta(val text: String, val enabled: Boolean, val onClick: () -> Unit)

/**
 * 온보딩 단계 화면 틀(403:36018 등, 배경 Soft): ‹ 제목 ⋯ + 4칸 막대, 큰 제목·설명 + 오른쪽 소단계 번호, 내용, 하단 [이전](보조 206) · 주 CTA(616).
 * 헤더 ‹ 와 [이전]은 모두 [onBack](한 칸 뒤로). [showPrev]가 false면 [이전]을 숨긴다(약관 403:36018).
 * 세로 분할 창(폭 840)은 내용을 위아래 1단으로 두고 스크롤하며, 주 CTA가 남은 폭을 채운다.
 * [content]는 넓은 창에서 남은 높이를 받는다(narrow 인자로 1단 여부를 알려 준다).
 */
@Composable
fun OnboardStep(
    title: String,
    step: Int,
    onBack: () -> Unit,
    headline: String,
    sub: String,
    dots: Pair<Int, Int>? = null,
    showPrev: Boolean = true,
    cta: StepCta? = null,
    content: @Composable ColumnScope.(narrow: Boolean) -> Unit,
) {
    val narrow = isNarrowWindow()
    Column(Modifier.fillMaxSize().softBackground()) {
        StepHeader(title, step, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().scrollWhen(narrow).padding(start = 40.dp, end = 40.dp, top = 48.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(headline, style = DpType.headlineM, color = DpColors.Text)
                    Text(sub, style = DpType.titleS.copy(fontWeight = FontWeight.Normal), color = DpColors.Text)
                }
                dots?.let { (total, current) -> StepDots(total, current) }
            }
            Column(if (narrow) Modifier.padding(top = 48.dp) else Modifier.weight(1f).padding(top = 48.dp)) { content(narrow) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 68.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showPrev) DpCta("이전", onBack, Modifier.width(206.dp), primary = false)
            if (cta != null) {
                if (narrow) { if (showPrev) Spacer(Modifier.width(24.dp)) } else Spacer(Modifier.weight(1f))
                DpCta(cta.text, cta.onClick, if (narrow) Modifier.weight(1f) else Modifier.width(616.dp), enabled = cta.enabled)
            }
        }
    }
}

/** 단계 화면 안 흰 유리 카드(403:36102): glass, 테두리 2, r24. */
@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.background(DpColors.Card, RoundedCornerShape(24.dp)).border(2.dp, DpColors.Divider, RoundedCornerShape(24.dp)),
    ) { content() }
}
