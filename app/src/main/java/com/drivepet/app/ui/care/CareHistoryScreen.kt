package com.drivepet.app.ui.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.ServicedPeriod
import com.drivepet.app.domain.careItemsFor
import com.drivepet.app.domain.careScore
import com.drivepet.app.domain.seoulDate
import com.drivepet.app.ui.components.DpCard
import com.drivepet.app.ui.components.DpChoice
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.DpPopup
import com.drivepet.app.ui.components.DpTag
import com.drivepet.app.ui.components.PopupButton
import com.drivepet.app.ui.components.TagStyle
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import com.drivepet.app.ui.theme.softBackground
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
    // 입력·설정 화면은 Soft 배경. 직접 입력 팝업은 루트 Box의 마지막 자식이라 맨 위에 그려진다.
    Box(Modifier.fillMaxSize().softBackground()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("마지막으로 언제 관리했나요?", style = DpType.headlineS)
            Text("교체·관리 시기를 등록해요. 정확히 기억나지 않아도 나중에 수정할 수 있어요.", style = DpType.bodyS.copy(fontWeight = FontWeight.Normal), color = DpColors.TextTertiary)
        }
        val narrow = isNarrowWindow()
        val tags: @Composable () -> Unit = {
            FuelTag("ICE / HEV · 4종 · 각 7.5점", fuel != FuelType.EV)
            FuelTag("EV · 3종 · 각 10점", fuel == FuelType.EV)
        }
        val total: @Composable () -> Unit = { Text("관리 배점 합계 30점", style = DpType.bodyS) }
        if (narrow) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { tags() }
            total()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { tags(); total() }
        }
        // EV 3열, ICE·HEV 2×2
        items.chunked(if (narrow) 1 else if (items.size == 3) 3 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { item ->
                    ItemCard(item, score, baselines[item], { onPeriod(item, it) }, { editing = item }, Modifier.weight(1f))
                }
            }
        }
        Text("관리 당시 주행거리 미입력 시 잔여거리는 미산출로 표시해요.", style = DpType.labelM, color = DpColors.TextTertiary)
        if (onInspection != null) InspectionField(inspection, onInspection, narrow)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DpCta("이전", onBack, Modifier.width(206.dp), primary = false)
            DpCta(primaryLabel, onPrimary, Modifier.width(520.dp))
        }
    }
    editing?.let { item ->
        DirectInputDialog(item, baselines[item], onDismiss = { editing = null }) { date, mm -> onDirect(item, date, mm); editing = null }
    }
    }
}

@Composable
private fun FuelTag(text: String, on: Boolean) {
    DpTag(text, if (on) TagStyle.Selected else TagStyle.Done)
}

@Composable
private fun ItemCard(item: CareItem, score: Double, b: CareBaseline?, onPeriod: (ServicedPeriod) -> Unit, onDirect: () -> Unit, modifier: Modifier) {
    DpCard(modifier) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(item.title, style = DpType.bodyL.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
            Text("권장 ${km(item.intervalKm)} · 항목 배점 ${scoreText(score)}점", style = DpType.labelM, color = DpColors.TextTertiary)
            ServicedPeriod.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { p -> DpChoice(p.label, b?.servicedOn == null && b?.period == p, { onPeriod(p) }, Modifier.weight(1f)) }
                }
            }
            val direct = b?.servicedOn?.let { d -> "$d" + (b.odometerMm?.let { " · ${km((it / 1_000_000).toInt())}" } ?: "") }
            Text(
                direct ?: "관리일·교환 당시 주행거리 직접 입력  >",
                Modifier.fillMaxWidth().clickable(onClick = onDirect).padding(vertical = 8.dp),
                style = DpType.labelM.copy(fontWeight = if (direct != null) FontWeight.Bold else FontWeight.Normal),
                color = if (direct != null) DpColors.Text else DpColors.TextTertiary,
            )
        }
    }
}

private val FIELD = DpType.bodyM.copy(color = DpColors.Text)

/** 날짜는 yyyy-MM-dd(미래 불가), 누적거리는 비워도 된다. */
@Composable
private fun DirectInputDialog(item: CareItem, b: CareBaseline?, onDismiss: () -> Unit, onSave: (LocalDate, Long?) -> Unit) {
    var dateText by remember { mutableStateOf(b?.servicedOn?.toString() ?: "") }
    var kmText by remember { mutableStateOf(b?.odometerMm?.let { (it / 1_000_000).toString() } ?: "") }
    val date = parseDate(dateText)
    val dateOk = date != null && !date.isAfter(seoulDate(Instant.now()))
    DpPopup(
        title = item.title, body = null, onDismissRequest = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    dateText, { dateText = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = FIELD,
                    label = { Text("관리일 (예: 2026-08-04)", style = DpType.labelS) }, isError = dateText.isNotEmpty() && !dateOk,
                )
                OutlinedTextField(
                    kmText, { kmText = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = FIELD,
                    label = { Text("교환 당시 누적 주행거리 (선택)", style = DpType.labelS) }, suffix = { Text("km", style = DpType.labelM) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
    ) {
        PopupButton("취소", onDismiss, primary = false)
        PopupButton("저장", { onSave(date!!, kmText.toLongOrNull()?.let { it * 1_000_000 }) }, primary = true, enabled = dateOk)
    }
}

@Composable
private fun InspectionField(due: LocalDate?, onChange: (LocalDate?) -> Unit, narrow: Boolean) {
    var text by remember { mutableStateOf(due?.toString() ?: "") }
    val parsed = parseDate(text)
    val label: @Composable () -> Unit = { Text("정기검사 예정일", style = DpType.bodyM.copy(fontWeight = FontWeight.Bold)) }
    val field: @Composable (Modifier) -> Unit = { m ->
        OutlinedTextField(
            text, { text = it; if (it.isBlank()) onChange(null) else parseDate(it)?.let(onChange) },
            m, singleLine = true, textStyle = FIELD,
            placeholder = { Text("2026-12-02", style = DpType.labelM) }, isError = text.isNotBlank() && parsed == null,
        )
    }
    val note: @Composable () -> Unit = { Text("비워 두면 미입력", style = DpType.labelM, color = DpColors.TextTertiary) }
    DpCard(Modifier.fillMaxWidth()) {
        if (narrow) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { label(); field(Modifier.fillMaxWidth()); note() }
        } else {
            Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                label()
                field(Modifier.width(360.dp))
                Box(Modifier.weight(1f))
                note()
            }
        }
    }
}

private fun parseDate(t: String): LocalDate? = runCatching { LocalDate.parse(t.trim()) }.getOrNull()
private fun km(n: Int) = String.format(Locale.US, "%,d km", n)
private fun scoreText(s: Double) = if (s % 1.0 == 0.0) s.toInt().toString() else s.toString()
