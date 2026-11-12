package com.drivepet.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.ServicedPeriod
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import java.util.Locale

private const val MAX_KM_DIGITS = 7

/** 줄 아래 안내: 고른 시기 + 권장 주기. 「기억 안남」·미선택이면 주기만. */
private fun hint(item: CareItem, period: ServicedPeriod?): String {
    val interval = "보통 ${String.format(Locale.US, "%,d", item.intervalKm)}km마다 바꿔요."
    return if (period == null || period == ServicedPeriod.UNKNOWN) interval else "${periodLabel(period)} 점검했어요. $interval"
}

/** 관리 항목별 당시 주행거리(403:36242 빈칸 / 404:41326 입력). 선택 입력이라 [다음]은 늘 켜져 있다. 숫자만, 비우면 미산출. */
@Composable
fun MileageScreen(vm: OnboardingViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    OnboardStep(
        "소모품 이력 확인하기", 2, onBack, "관리 항목별 당시 주행거리를 입력해주세요 (선택)", "관리 당시 주행거리를 입력하지 않으면 잔여 거리는 미산출로 표시해요",
        cta = StepCta("다음", true, onNext),
    ) { narrow ->
        // ICE·HEV는 4줄이라 넓은 창에서도 넘칠 수 있어 이 칸만 스크롤한다.
        Column(
            (if (narrow) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState())).padding(top = 72.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 40.dp else 60.dp),
        ) {
            for (item in vm.careQuestions) {
                val b = vm.careBaselines[item]
                val text = b?.odometerMm?.let { (it / 1_000_000).toString() }.orEmpty()
                val onChange: (String) -> Unit = { t -> vm.setOdometerKm(item, t.filter(Char::isDigit).take(MAX_KM_DIGITS).toIntOrNull()) }
                val label: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, style = DpType.titleL, color = DpColors.Text, maxLines = 1)
                        Text(hint(item, b?.period), style = DpType.bodyS.copy(fontWeight = FontWeight.Normal), color = DpColors.TextTertiary)
                    }
                }
                if (narrow) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { label(); KmField(text, onChange, Modifier.fillMaxWidth()) }
                else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { label() }
                    KmField(text, onChange, Modifier.width(822.dp))
                }
            }
        }
    }
}

/** 입력칸(ivi_text_field): 흰 바탕, 테두리 2, r24, 높이 100, 글자 36 + 오른쪽 「km」. */
@Composable
private fun KmField(text: String, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BasicTextField(
            text, onChange, Modifier.weight(1f), singleLine = true,
            textStyle = DpType.titleM.copy(fontWeight = FontWeight.Normal, color = DpColors.Text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(100.dp).background(Color.White, shape).border(2.dp, DpColors.Divider, shape).padding(horizontal = 32.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text("00,000", style = DpType.titleM.copy(fontWeight = FontWeight.Normal), color = DpColors.TextQuaternary)
                    inner()
                }
            },
        )
        Text("km", style = DpType.titleM, color = DpColors.Text)
    }
}
