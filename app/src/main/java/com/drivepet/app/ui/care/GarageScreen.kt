package com.drivepet.app.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.drivepet.app.domain.CareBaseline
import com.drivepet.app.data.baseline
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.data.CareRow
import com.drivepet.app.domain.FuelType
import com.drivepet.app.domain.careScore
import com.drivepet.app.ui.onboarding.OnboardCard
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.theme.DpColors
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DOTTED = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/** 10(EV 3종) · 21(ICE·HEV 4종) 내 차 정비소. 교체하면 [onReplaced]로 11 관리 기록에 간다. [account]는 맨 아래 카카오 계정 카드. */
@Composable
fun GarageScreen(vm: GarageViewModel, onEdit: () -> Unit, onReplaced: (String) -> Unit, account: @Composable () -> Unit = {}) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val u = ui ?: return
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val narrow = isNarrowWindow()
        val title: @Composable (Modifier) -> Unit = { m -> Text("소모품을 간편하게 관리해요", m, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold) }
        val odo: @Composable () -> Unit = {
            Text(u.odometerKm?.let { "현재 누적거리 ${km(it)}" } ?: "누적거리 미측정", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DpColors.TextTertiary)
        }
        if (narrow) {
            title(Modifier)
            odo()
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { title(Modifier.weight(1f)); odo() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (u.fuel == FuelType.EV) "EV · 3종 · 각 10점" else "ICE / HEV · 4종 · 각 7.5점",
                Modifier.background(DpColors.Filled, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Text("배점 합계 30점", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f))
            Button(
                onEdit, Modifier.height(72.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DpColors.Text),
            ) { Text("관리 이력 수정", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        }
        // EV 3열, ICE·HEV 2×2(와이어프레임 10·21)
        u.rows.chunked(if (narrow) 1 else if (u.rows.size == 3) 3 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { r -> CareCard(r, careScore(u.fuel), { scope.launch { vm.replace(r.item.itemId)?.let(onReplaced) } }, Modifier.weight(1f)) }
            }
        }
        OnboardCard(Modifier.fillMaxWidth()) {
            val inspection: @Composable (Modifier) -> Unit = { m ->
                Text(
                    u.inspectionDate?.let { "정기검사 ${dday(u.inspectionDays!!)} · ${DOTTED.format(it)} / 직접 입력한 일정" } ?: "정기검사 일정 미입력",
                    m, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                )
            }
            val note: @Composable () -> Unit = { Text("관리 주기는 실제 부품 수명이 아니에요.", fontSize = 22.sp, color = DpColors.TextTertiary) }
            if (narrow) {
                Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { inspection(Modifier); note() }
            } else {
                Row(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) { inspection(Modifier.weight(1f)); note() }
            }
        }
        account()
    }
}

@Composable
private fun CareCard(r: CareRow, score: Double, onDone: () -> Unit, modifier: Modifier) {
    val life = r.life
    OnboardCard(modifier) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.care.title, Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    "배점 ${if (score % 1.0 == 0.0) score.toInt() else score}",
                    Modifier.background(DpColors.Secondary, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 20.sp,
                )
            }
            Text("권장 ${km(r.care.intervalKm)} · " + (life.lastServiced?.let { "마지막 관리 $it" } ?: "관리 이력 미등록"), fontSize = 22.sp, color = DpColors.TextTertiary)
            Text(
                when {
                    life.baseOdometerMm == null -> "기준 거리 미입력 · 잔여거리 미산출"
                    life.sinceKm == null -> "기준 ${km((life.baseOdometerMm / 1_000_000).toInt())} · 현재 누적거리 미측정"
                    else -> "기준 ${km((life.baseOdometerMm / 1_000_000).toInt())} · 관리 후 ${km(life.sinceKm)}"
                },
                fontSize = 22.sp,
            )
            if (life.lifePct != null) {
                LinearProgressIndicator(
                    progress = { life.lifePct / 100f }, color = DpColors.Filled, trackColor = DpColors.TrackInactive,
                    drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(10.dp),
                )
                Text("잔여 ${km(life.remainingKm!!)} · 주기 ${life.lifePct}%", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    when {
                        life.lastServiced == null -> "이력을 등록해주세요"
                        else -> "잔여거리 미산출 · 날짜 기준 점검 안내"
                    },
                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onDone, Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DpColors.Filled, contentColor = Color.White),
            ) { Text(r.care.doneLabel, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun km(n: Int) = String.format(Locale.US, "%,d km", n)
private fun dday(days: Int) = when {
    days > 0 -> "D-$days"
    days == 0 -> "D-Day"
    else -> "D+${-days}"
}

/** 10의 "관리 이력 수정": 05 화면을 수정 모드로(검사일 칸 포함). 저장하면 [onDone]. */
@Composable
fun GarageEditScreen(vm: GarageViewModel, onDone: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val u = ui ?: return
    var baselines by remember { mutableStateOf(u.rows.associate { it.care to it.item.baseline() }) }
    var inspection by remember { mutableStateOf(u.inspectionDate) }
    CareHistoryScreen(
        u.fuel, baselines,
        onPeriod = { item, p -> baselines = baselines + (item to CareBaseline(null, null, p)) },
        onDirect = { item, date, mm -> baselines = baselines + (item to CareBaseline(mm, date, null)) },
        primaryLabel = "저장", onBack = onDone,
        onPrimary = { vm.saveEdit(baselines, inspection); onDone() },
        inspection = inspection, onInspection = { inspection = it },
    )
}
