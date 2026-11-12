package com.drivepet.app.ui.exit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivepet.app.R
import com.drivepet.app.drive.ExitCheck
import com.drivepet.app.ui.components.DpPopup
import com.drivepet.app.ui.components.PopupButton
import com.drivepet.app.ui.components.PopupSpec
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import com.drivepet.vehicle.model.ExitItem
import kotlinx.coroutines.delay

private const val CHECK_ANIM_MS = 300
private const val ALL_DONE_HOLD_MS = 700L // ✓ 애니메이션을 보여 준 뒤 닫는다

/** 피그마(419:55335)가 보이는 항목과 순서. 전조등·문 잠금은 보이지 않는다. */
private val SHOWN = listOf(ExitItem.CABIN_LIGHT_ON, ExitItem.WINDOW_OPEN)

fun shownExitItems(check: ExitCheck): List<ExitItem> = SHOWN.filter { it in check.items }

/** 보이는 항목 중 미조치가 하나라도 있을 때만 팝업이 뜬다. */
fun exitPopupNeeded(check: ExitCheck): Boolean = shownExitItems(check).any { check.items[it] != true }

private fun title(i: ExitItem) = if (i == ExitItem.CABIN_LIGHT_ON) "실내등 켜짐" else "운전석 창문 열림"
private fun hint(i: ExitItem) = if (i == ExitItem.CABIN_LIGHT_ON) "차량 조명 스위치로 꺼주세요" else "차량 창문 스위치로 닫아주세요"
private fun resolved(i: ExitItem) = if (i == ExitItem.CABIN_LIGHT_ON) "꺼짐" else "닫힘"

/** 하차 점검 팝업 규격(419:55335): radius 48, 패딩 48, 간격 40, 제목 40/52 Bold, 본문 30/38, 버튼 100·radius 32·32/40 Bold. */
private val ExitPopupSpec = PopupSpec(
    radius = 48.dp, padding = 48.dp, sectionGap = 40.dp,
    headerPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 30.dp), // 머리말과 카드 사이 70
    headerGap = 4.dp,
    titleStyle = DpType.titleL, bodyStyle = DpType.bodyL, bodyColor = DpColors.Text,
    buttonHeight = 100.dp, buttonRadius = 32.dp, buttonStyle = DpType.titleS.copy(fontWeight = FontWeight.Bold),
    maxWidth = 1234.dp, // 1138 + 패딩 48×2
)

/**
 * 하차 전 점검 팝업(419:55335): 보던 화면 위에 뜬다. 항목이 정상이 되면 ✓가 300ms에 걸쳐 켜지고,
 * 보이는 항목이 모두 정상이면 잠시 뒤 [onAllDone]을 부른다(앱 종료로 이어진다). [onStay]는 [아직 안 내려요].
 */
@Composable
fun ExitCheckPopup(check: ExitCheck, onStay: () -> Unit, onAllDone: () -> Unit) {
    val items = shownExitItems(check)
    val allDone = items.all { check.items[it] == true }
    val finish by rememberUpdatedState(onAllDone)
    LaunchedEffect(allDone) {
        if (allDone) { delay(ALL_DONE_HOLD_MS); finish() }
    }
    // 뒤로 가기·dim 탭은 소비한다. 닫는 것은 [아직 안 내려요]뿐.
    DpPopup(
        "하차 전, 창문과 조명을 확인해주세요", "모두 확인되면 자동으로 닫혀요", onDismissRequest = {},
        content = {
            Column(Modifier.fillMaxWidth().padding(bottom = 48.dp), Arrangement.spacedBy(40.dp)) {
                items.forEach { ExitRow(it, check.items[it] == true) }
            }
        },
        icon = { Image(painterResource(R.drawable.ic_alert_triangle), null, Modifier.size(60.dp)) },
        spec = ExitPopupSpec,
    ) { PopupButton("아직 안 내려요", onStay, primary = false) }
}

@Composable
private fun ExitRow(item: ExitItem, done: Boolean) {
    val boxColor by animateColorAsState(if (done) DpColors.ControlOn else DpColors.ButtonBasicPressed, tween(CHECK_ANIM_MS), label = "box")
    val labelColor by animateColorAsState(if (done) DpColors.ControlOn else DpColors.TextQuaternary, tween(CHECK_ANIM_MS), label = "label")
    val checkAlpha by animateFloatAsState(if (done) 1f else 0.64f, tween(CHECK_ANIM_MS), label = "check")
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(if (done) DpColors.Sky50 else Color.Transparent, shape)
            .border(BorderStroke(2.dp, DpColors.Divider), shape)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        Arrangement.SpaceBetween, Alignment.Top,
    ) {
        Column(Modifier.weight(1f), Arrangement.spacedBy(8.dp)) {
            Text(title(item), style = DpType.headlineS, color = DpColors.Text)
            Text(hint(item), style = DpType.titleS.copy(fontWeight = FontWeight.Normal), color = DpColors.Text)
        }
        Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(80.dp), Alignment.Center) {
                Box(Modifier.size(64.dp).background(boxColor, RoundedCornerShape(16.dp)), Alignment.Center) {
                    Image(painterResource(R.drawable.ic_check), null, Modifier.size(60.dp).alpha(checkAlpha))
                }
            }
            Text(resolved(item), style = DpType.bodyS, color = labelColor, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
