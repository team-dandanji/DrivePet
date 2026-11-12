package com.drivepet.app.ui.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.R
import com.drivepet.app.domain.CareItem
import com.drivepet.app.domain.REPLACEMENT_MILLI
import com.drivepet.app.domain.formatExp
import com.drivepet.app.domain.shortName
import com.drivepet.app.ui.LocalDriving
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.DpPopup
import com.drivepet.app.ui.components.DpTag
import com.drivepet.app.ui.components.PopupButton
import com.drivepet.app.ui.components.TagStyle
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.components.scrollWhen
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import java.util.Locale

private val CARD_SHAPE = RoundedCornerShape(20.dp)

/** 거리 표기: 1km 미만은 m, 이상은 소수 한 자리 km(피그마 「2.1km」). */
internal fun distanceLabel(m: Int): String = if (m < 1000) "${m}m" else String.format(Locale.ROOT, "%.1fkm", m / 1000.0)

/**
 * 펫 돌보기(D2 419:51516): 왼쪽 목록 패널 + 오른쪽 경로선·정보 카드. 목적지 카드를 누르면 정비소 고르기 시트(D2-s 419:56128),
 * 휴지통은 경로 삭제 팝업(D2-p 419:56479). 좁은 창은 1단(목록 → 경로 → 정보 → CTA).
 */
@Composable
fun CareCourseScreen(vm: CareCourseViewModel, onBack: () -> Unit, onRegister: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val narrow = isNarrowWindow()
    var sheet by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    // 주행이 시작되면 시트·팝업을 닫는다.
    val driving = LocalDriving.current
    LaunchedEffect(driving) { if (driving) { sheet = false; confirm = false } }

    Box(Modifier.fillMaxSize()) {
        when (val u = ui) {
            CourseUi.Loading -> Column(Modifier.fillMaxSize()) {
                CareBackHeader("펫 돌보기", onBack)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DpColors.Filled) }
            }
            CourseUi.Empty, CourseUi.Failed -> Column(Modifier.fillMaxSize()) {
                CareBackHeader("펫 돌보기", onBack)
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(40.dp),
                    verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("주변 정비소를 찾지 못했어요", style = DpType.titleL.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.Text, textAlign = TextAlign.Center)
                    DpCta("다시 찾기", vm::retry, if (narrow) Modifier.fillMaxWidth() else Modifier.width(616.dp))
                }
            }
            is CourseUi.Ready -> {
                val stop = u.stops[u.selected]
                if (narrow) {
                    Column(Modifier.fillMaxSize().scrollWhen(true)) {
                        CareBackHeader("펫 돌보기", onBack)
                        Column(Modifier.padding(top = 44.dp).padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(44.dp)) {
                            CourseList(stop, u.target, { sheet = true }, { confirm = true })
                            RouteLine(stop.place.name, Modifier.fillMaxWidth())
                            InfoCard(stop.minutes, Modifier.fillMaxWidth())
                            DpCta("등록할게요", onRegister, Modifier.fillMaxWidth().padding(bottom = 40.dp))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(718.dp).fillMaxHeight().shadow(12.dp).background(DpColors.Card)) {
                            CareBackHeader("펫 돌보기", onBack)
                            Column(Modifier.padding(top = 44.dp).padding(horizontal = 40.dp)) {
                                CourseList(stop, u.target, { sheet = true }, { confirm = true })
                            }
                            Spacer(Modifier.weight(1f))
                            DpCta("등록할게요", onRegister, Modifier.fillMaxWidth().padding(start = 40.dp, end = 24.dp, bottom = 68.dp))
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            RouteLine(stop.place.name, Modifier.align(Alignment.Center).padding(horizontal = 80.dp))
                            InfoCard(stop.minutes, Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 68.dp).width(448.dp))
                        }
                    }
                }
                if (sheet) StopSheet(u, narrow, onPick = { vm.select(it); sheet = false }, onDismiss = { sheet = false })
                if (confirm) {
                    DpPopup("경로를 삭제하시겠습니까?", stop.place.name, onDismissRequest = { confirm = false }) {
                        PopupButton("괜찮아요", { confirm = false }, primary = false)
                        PopupButton("삭제할게요", { vm.remove(u.selected); confirm = false }, primary = true)
                    }
                }
            }
        }
    }
}

/** ‹ 제목 헤더(단계 막대 없음). [divider]면 아래 1 구분선(D2), D3은 없음. */
@Composable
internal fun CareBackHeader(title: String, onBack: () -> Unit, divider: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp).height(80.dp).padding(start = 40.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_back), "뒤로", Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onBack),
                tint = DpColors.Text,
            )
            Text(title, style = DpType.titleL, color = DpColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(DpColors.DividerTertiary))
    }
}

/** 안내 문구 + 출발지 줄 + 고른 정비소 카드. */
@Composable
private fun CourseList(stop: Stop, target: CareItem, onOpen: () -> Unit, onTrash: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(60.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("차량 상태 확인을 위해\n네비게이션 목적지를 등록해요", style = DpType.titleL.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.Text)
            Text("목적지를 탭하면 경로를 바꿀 수 있어요", style = DpType.bodyS.copy(fontWeight = FontWeight.Normal), color = DpColors.Text)
        }
        Column {
            Row(Modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(40.dp)) {
                        drawCircle(DpColors.ControlOn)
                        drawCircle(Color.White, 8.dp.toPx())
                    }
                    Canvas(Modifier.width(2.dp).height(80.dp)) {
                        val dash = 4.dp.toPx()
                        drawLine(DpColors.ControlOn, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), size.width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("출발지", style = DpType.titleS.copy(fontWeight = FontWeight.Bold), color = DpColors.Text)
                    Text("현재 위치", style = DpType.bodyS.copy(fontWeight = FontWeight.Normal), color = DpColors.Text)
                }
            }
            StopCard(stop, target, onOpen, onTrash)
        }
    }
}

@Composable
private fun StopCard(stop: Stop, target: CareItem, onOpen: () -> Unit, onTrash: () -> Unit) {
    val body = DpType.bodyS.copy(fontWeight = FontWeight.Normal)
    Row(
        Modifier.fillMaxWidth().clip(CARD_SHAPE).border(2.dp, DpColors.DividerTertiary, CARD_SHAPE).clickable(onClick = onOpen)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(40.dp).background(DpColors.ControlOn, CircleShape), contentAlignment = Alignment.Center) {
                Text("1", style = DpType.bodyM.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stop.place.name, style = DpType.titleS.copy(fontWeight = FontWeight.Bold), color = DpColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stop.place.address, style = body, color = DpColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${distanceLabel(stop.place.distanceM)} | 약 ${stop.minutes}분 소요", style = body, color = DpColors.Text, maxLines = 1)
                }
                DpTag("${target.shortName} 점검 필요", TagStyle.Sky)
            }
        }
        Icon(
            painterResource(R.drawable.ic_care_trash), "경로 삭제", Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onTrash),
            tint = DpColors.TextQuaternary,
        )
    }
}

/** 출발지 → 정비소 경로선. 목적지가 바뀔 때마다 600ms ease-out으로 출발지 쪽에서 자란다. */
@Composable
private fun RouteLine(dest: String, modifier: Modifier) {
    val grow = remember(dest) { Animatable(0f) }
    LaunchedEffect(dest) { grow.animateTo(1f, tween(600, easing = EaseOut)) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RouteEnd(R.drawable.ic_care_car, "출발지")
        Box(Modifier.weight(1f).height(20.dp)) {
            Box(
                Modifier.fillMaxWidth(grow.value).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(DpColors.ControlOn.copy(alpha = 0f), DpColors.ControlOn)), CircleShape),
            )
        }
        RouteEnd(R.drawable.ic_care_wrench, dest)
    }
}

@Composable
private fun RouteEnd(icon: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.background(DpColors.ControlOn, RoundedCornerShape(28.dp)).padding(10.dp)) {
            Icon(painterResource(icon), null, Modifier.size(80.dp), tint = Color.White)
        }
        Text(label, style = DpType.labelS, color = DpColors.Text, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
    }
}

/** 추가시간(고른 정비소 분)·충전 후(고정)·보상(정비 기록 EXP). */
@Composable
private fun InfoCard(minutes: Int, modifier: Modifier) {
    Column(
        modifier.clip(CARD_SHAPE).background(DpColors.Card).border(2.dp, DpColors.DividerTertiary, CARD_SHAPE).padding(horizontal = 52.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        listOf("추가시간" to "+${minutes}분", "충전 후" to "충전 불필요", "보상" to "+${formatExp(REPLACEMENT_MILLI)}EXP").forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = DpType.titleS.copy(fontWeight = FontWeight.Normal), color = DpColors.TextSecondary, maxLines = 1, modifier = Modifier.width(120.dp))
                Text(value, style = DpType.titleS.copy(fontWeight = FontWeight.Bold), color = DpColors.Text, maxLines = 1)
            }
        }
    }
}

/** 정비소 고르기 바텀시트(같은 창, dim 20%). 카드를 눌러 고르고 [선택할게요]로 확정한다. */
@Composable
private fun StopSheet(r: CourseUi.Ready, narrow: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    var pick by remember { mutableIntStateOf(r.selected) }
    val none = remember { MutableInteractionSource() }
    val side = if (narrow) 40.dp else 80.dp
    Box(Modifier.fillMaxSize().background(DpColors.Dim).clickable(none, indication = null, onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(DpColors.SurfaceLow)
                .clickable(none, indication = null) {},
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 52.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(280.dp).height(12.dp).background(DpColors.ButtonBasicPressed, RoundedCornerShape(16.dp)))
            }
            Column(Modifier.padding(horizontal = side), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                r.stops.forEachIndexed { i, s -> SheetCard(s, i == pick) { pick = i } }
            }
            DpCta("선택할게요", { onPick(pick) }, Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 90.dp, bottom = 80.dp))
        }
    }
}

@Composable
private fun SheetCard(stop: Stop, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CARD_SHAPE).background(Color.White)
            .then(if (selected) Modifier.border(3.dp, DpColors.DividerTertiary, CARD_SHAPE) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 40.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stop.place.name, style = DpType.titleS, color = DpColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${distanceLabel(stop.place.distanceM)},  약 ${stop.minutes}분 소요", style = DpType.labelM, color = DpColors.TextSecondary, maxLines = 1)
        }
        if (selected) DpTag("선택 중", TagStyle.Selected)
    }
}
