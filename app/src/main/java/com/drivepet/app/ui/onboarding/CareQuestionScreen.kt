package com.drivepet.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.ServicedPeriod
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import kotlinx.coroutines.delay

/** 고른 뒤 다음 질문으로 넘어가기까지(419:59881). */
private const val AUTO_NEXT_MS = 400L

/** 질문 제목·설명(스펙 3절 O5~O7, 5절 엔진오일). */
private fun question(item: CareItem): Pair<String, String> = when (item) {
    CareItem.ENGINE_OIL -> "엔진오일은 언제 바꿨나요?" to "보통 10,000km마다 바꿔요. 기억나는 만큼만 골라주세요."
    CareItem.TIRE -> "타이어 위치 교환은 언제 했나요?" to "보통 10,000km마다 해요. 기억나는 만큼만 골라주세요."
    CareItem.CABIN_FILTER -> "에어컨 필터는 언제 바꿨나요?" to "보통 15,000km마다 바꿔요. 나중에 고칠 수 있어요."
    CareItem.WIPER -> "와이퍼는 언제 바꿨나요?" to "보통 10,000km마다 바꿔요. 나중에 고칠 수 있어요."
}

/**
 * 소모품 질문 한 화면(403:36148 미선택 / 419:56899 선택). 필수라 CTA가 없고, 고르면 차콜로 채운 뒤 0.4초 뒤 [onNext]로 넘어간다.
 * 0.4초 안에 다시 고르면 마지막 것만 남고 한 번만 넘어간다.
 */
@Composable
fun CareQuestionScreen(vm: OnboardingViewModel, index: Int, onBack: () -> Unit, onNext: () -> Unit) {
    val items = vm.careQuestions
    val item = items.getOrNull(index) ?: return
    val (headline, sub) = question(item)
    val selected = vm.careBaselines[item]?.period
    var picked by remember { mutableStateOf<ServicedPeriod?>(null) }
    LaunchedEffect(picked) { if (picked != null) { delay(AUTO_NEXT_MS); onNext() } }

    OnboardStep("소모품 이력 확인하기", 2, onBack, headline, sub, dots = items.size to index) { narrow ->
        Box(Modifier.fillMaxWidth().then(if (narrow) Modifier else Modifier.weight(1f)), contentAlignment = if (narrow) Alignment.TopCenter else Alignment.Center) {
            Column(Modifier.widthIn(max = 1420.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ServicedPeriod.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { p ->
                            Option(periodLabel(p), p == selected, narrow, Modifier.weight(1f).height(if (narrow) 160.dp else 220.dp)) {
                                vm.setPeriod(item, p)
                                picked = p
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 보기 카드: 흰 바탕·테두리 2·r20, 고르면 차콜 채움에 흰 글자(56 Bold, 세로 분할 창은 48). */
@Composable
private fun Option(label: String, selected: Boolean, narrow: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(shape).background(if (selected) DpColors.Filled else Color.White).border(2.dp, DpColors.Divider, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = (if (narrow) DpType.headlineS else DpType.headlineM).copy(fontWeight = FontWeight.Bold), color = if (selected) Color.White else DpColors.TextSecondary, maxLines = 1)
    }
}
