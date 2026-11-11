package com.drivepet.app.ui.exit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.domain.Mood
import com.drivepet.app.drive.ExitCheck
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.components.scrollWhen
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.vehicle.model.ExitItem
import kotlinx.coroutines.delay

private const val DONE_DELAY_MS = 2_000L
private const val DONE_AUTO_DISMISS_MS = 5_000L

private fun pending(i: ExitItem) = when (i) {
    ExitItem.WINDOW_OPEN -> "창문이 열려 있어요"
    ExitItem.HEADLIGHT_ON -> "전조등이 켜져 있어요"
    ExitItem.DOORS_UNLOCKED -> "문이 잠기지 않았어요"
}

private fun resolved(i: ExitItem) = when (i) {
    ExitItem.WINDOW_OPEN -> "창문 닫힘"
    ExitItem.HEADLIGHT_ON -> "전조등 꺼짐"
    ExitItem.DOORS_UNLOCKED -> "문 잠김"
}

/** 잠금 상태를 읽을 수 없으면 DOORS_UNLOCKED는 사용자가 직접 확인할 일이라 16으로 넘어가는 것을 막지 않는다. */
fun canFinish(check: ExitCheck): Boolean =
    check.items.all { (item, done) -> done || (item == ExitItem.DOORS_UNLOCKED && check.lockUnknown) }

private fun lockUnverified(check: ExitCheck, item: ExitItem, done: Boolean) = item == ExitItem.DOORS_UNLOCKED && check.lockUnknown && !done

/** 15 하차 점검 → 끝낼 수 있으면 2초 뒤 16(5초 뒤 자동 닫힘). 둘 다 정차 화면. */
@Composable
fun ExitCheckScreen(check: ExitCheck, starter: String, stage: Int, onDismiss: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    val finish = canFinish(check)
    LaunchedEffect(finish) {
        if (finish) { delay(DONE_DELAY_MS); done = true } else done = false
    }
    if (done) {
        LaunchedEffect(Unit) { delay(DONE_AUTO_DISMISS_MS); onDismiss() }
        ExitDone(check, starter, stage, onDismiss)
    } else {
        ExitWarning(check, onDismiss)
    }
}

@Composable
private fun ExitWarning(check: ExitCheck, onDismiss: () -> Unit) {
    val narrow = isNarrowWindow()
    Column(Modifier.fillMaxSize().scrollWhen(narrow).padding(horizontal = if (narrow) 40.dp else 80.dp, vertical = 48.dp), Arrangement.spacedBy(20.dp)) {
        Text("하차 전에 확인해주세요", fontSize = 60.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold)
        Text("차량에서 조치하면 확인된 상태가 자동으로 갱신돼요.", fontSize = 30.sp, color = DpColors.TextTertiary)
        check.items.forEach { (item, done) ->
            if (lockUnverified(check, item, done)) CheckRow(null, false) else CheckRow(item, done)
        }
        // DOORS_UNLOCKED가 점검 항목이 아닐 때만 따로 알린다.
        if (check.lockUnknown && ExitItem.DOORS_UNLOCKED !in check.items) CheckRow(null, false)
        Text("앱이 확인한 항목만 표시해요. 확인되지 않은 차량 상태는 직접 점검해주세요.", fontSize = 26.sp, color = DpColors.TextTertiary)
        if (!narrow) Column(Modifier.weight(1f)) {}
        FilledButton("확인했어요", onDismiss, Modifier.width(480.dp).align(Alignment.End))
    }
}

/** item이 null이면 "잠금 확인 불가" 행(중립, 오류 아님). 해결된 항목은 해결 문구에 "✓ 완료". */
@Composable
private fun CheckRow(item: ExitItem?, done: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp)).padding(horizontal = 32.dp, vertical = 28.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Text(
            when { item == null -> "잠금 확인 불가"; done -> resolved(item); else -> pending(item) }, fontSize = 36.sp, fontWeight = FontWeight.Bold,
            color = if (item == null) DpColors.TextTertiary else DpColors.Text,
        )
        Text(
            when { item == null -> "직접 확인"; done -> "✓ 완료"; else -> "미조치" }, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = when { item == null -> DpColors.TextTertiary; done -> DpColors.Positive; else -> DpColors.Danger },
        )
    }
}

@Composable
private fun ExitDone(check: ExitCheck, starter: String, stage: Int, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(20.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Character(starter, stage, Mood.NORMAL)
        Text("점검 완료! 오늘도 수고했어요", Modifier.padding(horizontal = 40.dp), fontSize = 56.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        check.items.forEach { (item, done) ->
            if (!lockUnverified(check, item, done)) Text("✓ ${resolved(item)}", fontSize = 30.sp, color = DpColors.TextTertiary)
        }
        if (check.lockUnknown && !check.items.getOrDefault(ExitItem.DOORS_UNLOCKED, false)) {
            Text("잠금 상태는 직접 확인해주세요", fontSize = 26.sp, color = DpColors.TextTertiary)
        }
        FilledButton("닫기", onDismiss, Modifier.width(400.dp))
    }
}
