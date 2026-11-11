package com.drivepet.app.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.onboarding.OnboardCard
import com.drivepet.app.ui.theme.DpColors
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 11 관리 기록 · 회복 완료. */
@Composable
fun CareDoneScreen(vm: GarageViewModel, recordId: String, onGarage: () -> Unit, onLounge: () -> Unit) {
    val flow = remember(recordId) { vm.done(recordId) }
    val d by flow.collectAsStateWithLifecycle(null)
    val u = d ?: return
    // 받침이 있으면 "을", 없으면 "를"
    val obj = if ((u.title.last().code - 0xAC00) % 28 != 0) "을" else "를"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 120.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text("${u.title}$obj 기록했어요", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        OnboardCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(
                    Modifier.width(360.dp).background(DpColors.Secondary, RoundedCornerShape(16.dp)).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("관리 주기", fontSize = 26.sp, color = DpColors.TextTertiary)
                    Text(u.lifePct?.let { "$it%" } ?: "미산출", fontSize = 72.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (u.paid) "+50 EXP" else "+0 EXP", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (u.paid) "관리 기록 보상 적립 완료" else "오늘은 이미 보상을 받았어요", fontSize = 22.sp, color = DpColors.TextTertiary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoRow("관리일", DateTimeFormatter.ofPattern("yyyy.MM.dd").format(u.date))
                    InfoRow("기준 누적거리", u.odometerKm?.let { String.format(Locale.US, "%,d km", it) } ?: "미측정")
                    InfoRow("관리 후 누적거리", if (u.odometerKm != null) "0 km" else "미측정")
                    InfoRow("건강점수", "항목별 배점으로 재계산")
                    Text("동력원별 항목 배점으로 건강점수를 갱신해요.\n차량의 실제 경고 상태는 별도로 확인해주세요.", Modifier.padding(top = 16.dp), fontSize = 24.sp, color = DpColors.TextTertiary)
                }
            }
        }
        if (u.tpmsOn) {
            OnboardCard(Modifier.fillMaxWidth()) {
                Text("TPMS 경고 유지 · 정비 기록만으로 차량 경고가 해제되지는 않아요.", Modifier.padding(28.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onGarage, Modifier.weight(1f).height(100.dp).background(DpColors.Secondary, RoundedCornerShape(16.dp))) {
                Text("내 차 정비소로 돌아가기", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DpColors.Text)
            }
            FilledButton("라운지에서 갱신 상태 확인", onLounge, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, Modifier.width(260.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}
