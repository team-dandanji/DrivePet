package com.drivepet.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.domain.FuelType
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.vehicle.model.valueOrNull
import java.util.Locale

/** 04 · 차량 정보 확인. 누적거리는 읽히면 표시만, 못 읽으면 직접 입력. */
@Composable
fun ScanScreen(vm: OnboardingViewModel, onNext: () -> Unit) {
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val odometer = state.odometerKm.valueOrNull()

    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("차량 정보를 확인해 주세요", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Text("차에서 읽은 정보예요. 읽지 못한 항목은 직접 적어 주세요", fontSize = 26.sp, color = DpColors.TextTertiary)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OnboardCard(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("차량 프로필", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Field("차량 이름") {
                        OutlinedTextField(
                            vm.vehicleName, { vm.vehicleName = it }, Modifier.fillMaxWidth(), singleLine = true,
                            textStyle = FIELD_TEXT, supportingText = { Text("1~20자", fontSize = 20.sp) },
                            isError = vm.vehicleName.trim().length !in 1..20,
                        )
                    }
                    Field("연료 종류") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for ((type, label) in FUELS) {
                                Choice(label, vm.fuelType == type, { vm.fuelType = type }, Modifier.weight(1f))
                            }
                        }
                    }
                    if (vm.fuelType == FuelType.EV) {
                        Field("배터리 용량 (kWh, 선택)") {
                            val error = vm.batteryError()
                            OutlinedTextField(
                                vm.batteryText, { vm.batteryText = it }, Modifier.fillMaxWidth(), singleLine = true,
                                textStyle = FIELD_TEXT, isError = error != null,
                                supportingText = error?.let { { Text(it, fontSize = 20.sp) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                    Field("누적 주행거리") {
                        if (odometer != null) {
                            Text(String.format(Locale.US, "%,.0f km", odometer), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        } else {
                            OutlinedTextField(
                                vm.odometerText, { vm.odometerText = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
                                singleLine = true, textStyle = FIELD_TEXT, suffix = { Text("km", fontSize = 26.sp) },
                                supportingText = { Text("차에서 읽지 못했어요", fontSize = 20.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                }
            }
            OnboardCard(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.padding(32.dp)) {
                    Text("차량 기능 확인", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Column(Modifier.weight(1f).padding(top = 12.dp).verticalScroll(rememberScrollState())) {
                        caps.forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(color = DpColors.Divider)
                            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), Arrangement.SpaceBetween) {
                                Text(c.name, fontSize = 26.sp, color = DpColors.TextTertiary)
                                Text(c.status, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PrimaryButton("다음", vm.canNext, onNext, Modifier.width(420.dp))
        }
    }
}

private val FIELD_TEXT = TextStyle(fontSize = 28.sp, color = DpColors.Text)
private val FUELS = listOf(FuelType.EV to "EV", FuelType.ICE to "내연기관", FuelType.HEV to "하이브리드")

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 24.sp, color = DpColors.TextTertiary)
        content()
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(72.dp).background(if (selected) DpColors.Filled else Color.White, shape)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(2.dp, DpColors.TrackInactive), shape))
            .selectable(selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else DpColors.Text)
    }
}

@Composable
internal fun OnboardCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(DpColors.Card, RoundedCornerShape(20.dp))) { content() }
}

@Composable
internal fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick, modifier.height(96.dp), enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DpColors.Filled, contentColor = Color.White,
            disabledContainerColor = DpColors.TrackInactive, disabledContentColor = Color.White,
        ),
    ) { Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
}
