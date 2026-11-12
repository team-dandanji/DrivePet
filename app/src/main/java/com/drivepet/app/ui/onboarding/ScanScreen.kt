package com.drivepet.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.domain.FuelType
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import com.drivepet.vehicle.model.valueOrNull
import java.util.Locale

/** 차 이름 아래 큰 글씨(라운지와 같은 표기). */
private val FUEL_TITLE = mapOf(FuelType.EV to "전기차(EV)", FuelType.ICE to "내연기관", FuelType.HEV to "하이브리드")

/**
 * 차량 정보 확인(403:36100). CTA는 늘 켜져 있다(배터리 용량을 잘못 적었을 때만 꺼진다).
 * 차가 연료 종류를 알려 주지 않아, 피그마 「수정하기」 자리에 연료 고르기를 둔다(소모품 질문 수가 이것으로 정해진다).
 * EV면 배터리 용량(선택, 효율 점수용)을 오른쪽 카드 맨 아래에서 받는다.
 */
@Composable
fun ScanScreen(vm: OnboardingViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val odometer = state.odometerKm.valueOrNull()
    val energy = state.energyPct.valueOrNull()
    val warnings = state.warnings.valueOrNull().orEmpty()
    val unreadable = caps.filter { it.status != "읽힘" }.map { it.name }

    OnboardStep(
        "차량 정보 확인하기", 1, onBack, "차량 정보를 불러왔어요", "지원 가능한 차량 정보를 조회했어요. 잘못된 항목은 수정할 수 있어요.",
        cta = StepCta("다음", vm.canNext, onNext),
    ) { narrow ->
        val cardHeight = if (narrow) Modifier else Modifier.heightIn(min = 505.dp)
        val left: @Composable (Modifier) -> Unit = { m ->
            GlassCard(m.then(cardHeight)) {
                Column(Modifier.padding(40.dp).then(if (narrow) Modifier else Modifier.fillMaxHeight())) {
                    Text("차량 프로필", style = DpType.titleS, color = DpColors.TextTertiary)
                    Text("차량 정보는 자동 매핑됐어요", Modifier.padding(top = 4.dp), style = DpType.labelM, color = DpColors.TextTertiary)
                    Text(OnboardingViewModel.VEHICLE_NAME, Modifier.padding(top = 83.dp), style = DpType.headlineL, color = DpColors.Text, maxLines = 1)
                    Text(FUEL_TITLE.getValue(vm.fuelType), Modifier.padding(top = 4.dp), style = DpType.titleL, color = DpColors.Text)
                    if (narrow) Spacer(Modifier.height(40.dp)) else Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for ((type, label) in FUELS) Choice(label, vm.fuelType == type, { vm.fuelType = type }, Modifier.weight(1f))
                    }
                }
            }
        }
        val right: @Composable (Modifier) -> Unit = { m ->
            GlassCard(m.then(cardHeight)) {
                Column(
                    Modifier.padding(horizontal = 52.dp, vertical = 40.dp).then(if (narrow) Modifier else Modifier.fillMaxHeight()),
                    verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
                ) {
                    InfoRow("누적 주행거리", odometer?.let { String.format(Locale.US, "%,.0f km", it) } ?: "조회 불가")
                    InfoRow(if (vm.fuelType == FuelType.EV) "배터리 잔량" else "연료 잔량", energy?.let { "$it %" } ?: "조회 불가")
                    InfoRow("차량 경고", warnings.joinToString(", ").ifEmpty { "없음" })
                    InfoRow("조회 불가 항목", unreadable.joinToString(", ").ifEmpty { "없음" })
                    if (vm.fuelType == FuelType.EV) {
                        val error = vm.batteryError()
                        InfoRow("배터리 용량") {
                            OutlinedTextField(
                                vm.batteryText, { vm.batteryText = it }, Modifier.fillMaxWidth(), singleLine = true,
                                textStyle = TextStyle(fontSize = 28.sp, color = DpColors.Text), isError = error != null,
                                placeholder = { Text("선택 입력", fontSize = 26.sp, color = DpColors.TextQuaternary) },
                                suffix = { Text("kWh", fontSize = 26.sp) },
                                supportingText = error?.let { { Text(it, fontSize = 20.sp) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }
            }
        }
        if (narrow) Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth()) }
        else Column(Modifier.verticalScroll(rememberScrollState())) { // EV 배터리 칸·긴 목록이면 505보다 길어진다
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                left(Modifier.width(593.dp).fillMaxHeight()); right(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** 오른쪽 카드 한 줄(라벨 260 + 값 40 Bold). */
@Composable
private fun InfoRow(label: String, value: String) = InfoRow(label) {
    Text(value, style = DpType.titleL, color = DpColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(100.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(260.dp), style = DpType.titleM.copy(fontWeight = FontWeight.Normal), color = DpColors.TextTertiary)
        Box(Modifier.weight(1f)) { value() }
    }
}

internal val FUELS = listOf(FuelType.EV to "EV", FuelType.ICE to "내연기관", FuelType.HEV to "하이브리드")

@Composable
internal fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(72.dp).background(if (selected) DpColors.Filled else Color.White, shape)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(2.dp, DpColors.TrackInactive), shape))
            .selectable(selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else DpColors.Text, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun OnboardCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(DpColors.Card, RoundedCornerShape(20.dp))) { content() }
}
