package com.drivepet.app.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.ServicedPeriod
import com.drivepet.app.domain.careItemsFor
import com.drivepet.app.domain.careScore
import com.drivepet.app.domain.seoulDate
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.onboarding.Choice
import com.drivepet.app.ui.onboarding.OnboardCard
import com.drivepet.app.ui.theme.DpColors
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * 05(EV 3종) · 20(ICE·HEV 4종) 소모품 이력. 온보딩과 10의 "관리 이력 수정"이 같이 쓴다.
 * [onInspection]이 있으면(수정 모드) 정기검사 예정일 칸을 보인다. 위의 연료 탭은 지금 차 쪽만 켜진 표시다(연료는 04에서 정함).
 */
@Composable
fun CareHistoryScreen(
    fuel: FuelType,
    baselines: Map<CareItem, CareBaseline>,
    onPeriod: (CareItem, ServicedPeriod) -> Unit,
    onDirect: (CareItem, LocalDate, Long?) -> Unit,
    primaryLabel: String,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    inspection: LocalDate? = null,
    onInspection: ((LocalDate?) -> Unit)? = null,
) {
    var editing by remember { mutableStateOf<CareItem?>(null) }
    val items = careItemsFor(fuel)
    val score = careScore(fuel)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("마지막으로 언제 관리했나요?", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Text("교체·관리 시기를 등록해요. 정확히 기억나지 않아도 나중에 수정할 수 있어요.", fontSize = 26.sp, color = DpColors.TextTertiary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FuelTag("ICE / HEV · 4종 · 각 7.5점", fuel != FuelType.EV)
            FuelTag("EV · 3종 · 각 10점", fuel == FuelType.EV)
            Text("관리 배점 합계 30점", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        // EV 3열, ICE·HEV 2×2
        items.chunked(if (LocalConfiguration.current.screenWidthDp < 1200) 1 else if (items.size == 3) 3 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { item ->
                    ItemCard(item, score, baselines[item], { onPeriod(item, it) }, { editing = item }, Modifier.weight(1f))
                }
            }
        }
        Text("관리 당시 주행거리 미입력 시 잔여거리는 미산출로 표시해요.", fontSize = 24.sp, color = DpColors.TextTertiary)
        if (onInspection != null) InspectionField(inspection, onInspection)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onBack, Modifier.width(220.dp).height(100.dp).background(DpColors.Secondary, RoundedCornerShape(16.dp))) {
                Text("이전", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DpColors.Text)
            }
            FilledButton(primaryLabel, onPrimary, Modifier.width(520.dp))
        }
    }
    editing?.let { item ->
        DirectInputDialog(item, baselines[item], onDismiss = { editing = null }) { date, mm -> onDirect(item, date, mm); editing = null }
    }
}

@Composable
private fun FuelTag(text: String, on: Boolean) {
    Text(
        text,
        Modifier.background(if (on) DpColors.Filled else Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else DpColors.TextTertiary,
    )
}

@Composable
private fun ItemCard(item: CareItem, score: Double, b: CareBaseline?, onPeriod: (ServicedPeriod) -> Unit, onDirect: () -> Unit, modifier: Modifier) {
    OnboardCard(modifier) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(item.title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("권장 ${km(item.intervalKm)} · 항목 배점 ${scoreText(score)}점", fontSize = 22.sp, color = DpColors.TextTertiary)
            ServicedPeriod.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { p -> Choice(p.label, b?.servicedOn == null && b?.period == p, { onPeriod(p) }, Modifier.weight(1f)) }
                }
            }
            val direct = b?.servicedOn?.let { d -> "$d" + (b.odometerMm?.let { " · ${km((it / 1_000_000).toInt())}" } ?: "") }
            Text(
                direct ?: "관리일·교환 당시 주행거리 직접 입력  >",
                Modifier.fillMaxWidth().clickable(onClick = onDirect).padding(vertical = 8.dp),
                fontSize = 22.sp, fontWeight = if (direct != null) FontWeight.Bold else FontWeight.Normal,
                color = if (direct != null) DpColors.Text else DpColors.TextTertiary,
            )
        }
    }
}

private val FIELD = TextStyle(fontSize = 28.sp, color = DpColors.Text)

/** 날짜는 yyyy-MM-dd(미래 불가), 누적거리는 비워도 된다. */
@Composable
private fun DirectInputDialog(item: CareItem, b: CareBaseline?, onDismiss: () -> Unit, onSave: (LocalDate, Long?) -> Unit) {
    var dateText by remember { mutableStateOf(b?.servicedOn?.toString() ?: "") }
    var kmText by remember { mutableStateOf(b?.odometerMm?.let { (it / 1_000_000).toString() } ?: "") }
    val date = parseDate(dateText)
    val dateOk = date != null && !date.isAfter(seoulDate(Instant.now()))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title, fontSize = 32.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    dateText, { dateText = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = FIELD,
                    label = { Text("관리일 (예: 2026-08-04)", fontSize = 20.sp) }, isError = dateText.isNotEmpty() && !dateOk,
                )
                OutlinedTextField(
                    kmText, { kmText = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = FIELD,
                    label = { Text("교환 당시 누적 주행거리 (선택)", fontSize = 20.sp) }, suffix = { Text("km", fontSize = 24.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton({ onSave(date!!, kmText.toLongOrNull()?.let { it * 1_000_000 }) }, enabled = dateOk) { Text("저장", fontSize = 26.sp) }
        },
        dismissButton = { TextButton(onDismiss) { Text("취소", fontSize = 26.sp) } },
    )
}

@Composable
private fun InspectionField(due: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var text by remember { mutableStateOf(due?.toString() ?: "") }
    val parsed = parseDate(text)
    OnboardCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("정기검사 예정일", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                text, { text = it; if (it.isBlank()) onChange(null) else parseDate(it)?.let(onChange) },
                Modifier.width(360.dp), singleLine = true, textStyle = FIELD,
                placeholder = { Text("2026-12-02", fontSize = 24.sp) }, isError = text.isNotBlank() && parsed == null,
            )
            Box(Modifier.weight(1f))
            Text("비워 두면 미입력", fontSize = 22.sp, color = DpColors.TextTertiary)
        }
    }
}

private fun parseDate(t: String): LocalDate? = runCatching { LocalDate.parse(t.trim()) }.getOrNull()
private fun km(n: Int) = String.format(Locale.US, "%,d km", n)
private fun scoreText(s: Double) = if (s % 1.0 == 0.0) s.toInt().toString() else s.toString()
