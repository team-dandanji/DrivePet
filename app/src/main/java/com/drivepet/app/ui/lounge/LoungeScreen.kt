package com.drivepet.app.ui.lounge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.BuildConfig
import com.drivepet.app.R
import com.drivepet.app.domain.MissionStatus
import com.drivepet.app.domain.gaugeExpText
import com.drivepet.app.drive.Speaker
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.characterSize
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.DpTag
import com.drivepet.app.ui.components.SpeechBubble
import com.drivepet.app.ui.components.TagStyle
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/** TabHeader 높이(위 여백 40 + 80 + 아래 여백 24 + 구분선 1). */
private const val HEADER_DP = 145

/** 07 · 08 메인 라운지(HIFI 419:50656). 08(이상 감지)은 같은 화면에서 특이사항 카드·CTA가 채워진 상태다. 프로필이 없으면 아무것도 그리지 않는다. */
@Composable
fun LoungeScreen(vm: LoungeViewModel, speaker: Speaker, onCare: () -> Unit, onDev: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val bubble by speaker.bubble.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.onEnter() }
    val u = ui ?: return

    val devPress = if (BuildConfig.DEMO_TOOLS) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onDev() }) } else Modifier
    val header: @Composable () -> Unit = {
        Text(u.nickname, style = DpType.headlineL, color = DpColors.Text)
        VehicleLine(u.vehicleLine, weather)
    }
    // 말풍선 높이 80(dp/speech: 위아래 20 + 줄 40). 말풍선이 없어도 자리를 지켜 아래 줄이 움직이지 않는다.
    val bubbleRow: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { SpeechBubble(bubble) } }
    val statusRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            DpTag(if (u.parked) "주차중" else "주행중", TagStyle.Glass)
        }
    }
    val stats: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("배터리", u.satietyPct, batteryTag(u.satietyPct), "%", Modifier.weight(1f))
            StatCard("건강점수", u.healthPct, healthTag(u.healthPct), "/ 100점", Modifier.weight(1f))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 창 높이로 가른다(LoungeLayoutTest의 기준). 이 영역은 헤더를 뺀 높이라 헤더 높이를 더해 창 높이로 되돌린다.
        when (loungeLayout(maxWidth.value.toInt(), maxHeight.value.toInt() + HEADER_DP)) {
            // 17 Connect-S 분할 세로 창: 위아래로 쌓고 스크롤
            LoungeLayout.NARROW -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                header()
                LoungePet(u, devPress, Modifier)
                bubbleRow()
                stats()
                WarningPanel(u, onCare, Modifier.fillMaxWidth(), fill = false)
                statusRow()
                ExpCard(u, stacked = true)
            }
            // 18 Connect-W 낮고 넓은 창: 캐릭터 왼쪽, 게이지·특이사항·미션 오른쪽
            LoungeLayout.WIDE -> Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    header()
                    LoungePet(u, devPress, Modifier.weight(1f))
                    bubbleRow()
                    statusRow()
                }
                // 낮은 창은 카드 두 장 + CTA가 한 화면에 안 들어가므로 오른쪽 열은 스크롤한다.
                Column(Modifier.width(720.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stats()
                    WarningPanel(u, onCare, Modifier.fillMaxWidth(), fill = false)
                    ExpCard(u)
                }
            }
            LoungeLayout.DEFAULT -> Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    header()
                    LoungePet(u, devPress, Modifier.weight(1f))
                    bubbleRow()
                    statusRow()
                    ExpCard(u)
                }
                // 특이사항 카드는 열 바닥까지 늘리고 CTA를 맨 아래에 둔다(419:50680, 592×574 justify-between). 창이 낮으면 늘리지 않고 스크롤한다.
                BoxWithConstraints(Modifier.width(592.dp).fillMaxHeight()) {
                    // 경고가 없으면 늘리지 않는다(빈 카드가 열을 채우지 않게)
                    val viewport = if (u.warnings.isEmpty()) 0.dp else maxHeight
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        FillRest(viewport, 24.dp, top = {
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                Text("펫 컨디션", style = DpType.titleM.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.TextSecondary)
                                stats()
                            }
                        }) { WarningPanel(u, onCare, Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
}

/** 라운지 펫 크기 배율: 피그마 2단계 병아리 키 382(419:50704) / 기본 300. */
private const val LOUNGE_PET_SCALE = 1.27f

/** 라운지 펫(419:50702). 칸 아래에 붙이고 말풍선과 40 띄운다. 칸 높이가 정해져 있으면(넓은 창) 그 안에 들어가게 줄인다. */
@Composable
private fun LoungePet(u: LoungeUi, devPress: Modifier, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
        val want = characterSize(u.stage) * LOUNGE_PET_SCALE
        // Character 높이 = size × 1.1
        val size = if (constraints.hasBoundedHeight) minOf(want, maxHeight / 1.1f) else want
        Character(u.starter, u.stage, u.mood, devPress, size)
    }
}

/**
 * [top]을 먼저 재고, [rest]를 남은 높이([viewport] − top − [gap])까지 늘린다. 남은 높이가 [rest]의 제 높이보다 작으면 제 높이로 두고
 * 바깥 스크롤에 맡긴다(창이 낮아도 겹치거나 잘리지 않는다). [rest]는 받은 높이를 채워야 한다(WarningPanel fill).
 */
@Composable
private fun FillRest(viewport: Dp, gap: Dp, top: @Composable () -> Unit, rest: @Composable () -> Unit) {
    Layout({ top(); rest() }) { ms, c ->
        val w = c.maxWidth
        val t = ms[0].measure(Constraints(maxWidth = w))
        val g = gap.roundToPx()
        val h = maxOf(ms[1].minIntrinsicHeight(w), viewport.roundToPx() - t.height - g)
        val r = ms[1].measure(Constraints.fixed(w, h))
        layout(w, t.height + g + h) { t.place(0, 0); r.place(0, t.height + g) }
    }
}

/** 펫 이름 아래 차량 줄 뒤에 "맑음 · 18°C"를 잇고, 미세먼지·경고·이전 정보는 작은 글씨로 뒤따른다. 날씨가 없으면 차량 이름만. */
@Composable
private fun VehicleLine(vehicle: String, w: WeatherUi?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (w == null) vehicle else "$vehicle  /  ${w.label} · ${w.temp}", style = DpType.bodyL, color = DpColors.Text)
        if (w != null) {
            w.air?.let { Text(it, style = DpType.labelS, color = DpColors.TextTertiary) }
            w.warning?.let { Text(it, style = DpType.labelS.copy(fontWeight = FontWeight.Bold), color = DpColors.Danger) }
            if (w.stale) Text("이전 정보", style = DpType.labelS, color = DpColors.TextTertiary)
        }
    }
}

/** 유리 카드(drivepet/card/glass, r20). */
@Composable
private fun Glass(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(28.dp), content: @Composable () -> Unit) {
    Box(modifier.background(DpColors.Card, RoundedCornerShape(20.dp)).padding(padding)) { content() }
}

@Composable
private fun ExpCard(u: LoungeUi, stacked: Boolean = false) {
    val level: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("LV. ${u.gauge.level}", style = DpType.headlineS, color = DpColors.Text)
            Text(
                u.gauge.needMilli?.let { gaugeExpText(u.gauge.inLevelMilli, it) } ?: "MAX",
                style = DpType.labelM, color = DpColors.TextSecondary, maxLines = 1, softWrap = false,
            )
            // 진행 막대: 높이 8, r4(drivepet/progress)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(DpColors.TrackInactive)) {
                Box(Modifier.fillMaxWidth(u.gauge.percent / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(DpColors.Filled))
            }
        }
    }
    val missions: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Today\u2019s mission", style = DpType.labelM, color = DpColors.TextSecondary)
            u.missions.forEach { ms -> MissionRow(ms) }
        }
    }
    Glass(Modifier.fillMaxWidth(), PaddingValues(horizontal = 40.dp, vertical = 28.dp)) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { level(Modifier.fillMaxWidth()); missions(Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(60.dp)) { level(Modifier.width(200.dp)); missions(Modifier.weight(1f)) }
        }
    }
}

/** 미션 한 줄: 제목(+진행), 오른쪽에 태그(달성 전 +30EXP, 달성 후 달성 완료). 보상은 달성하면 자동으로 들어간다. */
@Composable
private fun MissionRow(m: MissionStatus) {
    // 칸이 좁으면 미션 이름을 단어 단위로 두 줄까지(글자 중간에서 자르지 않는다)
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(m.kind.title, Modifier.weight(1f), style = DpType.bodyS, color = DpColors.Text, maxLines = 2)
        if (m.progress.isNotEmpty()) Text(m.progress, Modifier.padding(end = 12.dp), style = DpType.labelS, color = DpColors.TextTertiary)
        if (m.done || m.claimed) DpTag("달성 완료", TagStyle.Done) else DpTag("+30EXP", TagStyle.Neutral)
    }
}

/** 펫 컨디션 카드(288 폭 비율, 419:50664). value가 null이면 "미측정"(태그 없음). */
@Composable
private fun StatCard(label: String, value: Int?, tag: Pair<String, TagStyle>?, unit: String, modifier: Modifier) {
    Glass(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            // 태그가 없어도 두 카드의 줄이 맞도록 태그 높이(44)만큼 비운다.
            if (tag != null) DpTag(tag.first, tag.second) else Box(Modifier.height(44.dp))
            Column {
                Text(label, style = DpType.bodyS, color = DpColors.Text)
                if (value == null) {
                    Text("미측정", style = DpType.titleL, color = DpColors.TextTertiary, modifier = Modifier.height(104.dp).wrapContentHeight(Alignment.CenterVertically))
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$value", style = DpType.displayL, color = DpColors.Text)
                        val small = unit.length == 1
                        Text(
                            unit, Modifier.padding(start = 4.dp, bottom = if (small) 16.dp else 0.dp),
                            style = if (small) DpType.titleL else DpType.bodyS.copy(fontWeight = FontWeight.Normal), color = DpColors.Text,
                        )
                    }
                }
            }
        }
    }
}

/** 특이사항 카드: 경고 카드 최대 2개(TPMS 먼저), 경고가 있을 때만 [돌보러 가기 ›]. [fill]이면 CTA를 카드 아래로 민다(스크롤 열에서는 쓰지 않는다). */
@Composable
private fun WarningPanel(u: LoungeUi, onCare: () -> Unit, modifier: Modifier, fill: Boolean = true) {
    Glass(modifier, PaddingValues(horizontal = 28.dp, vertical = 36.dp)) {
        Column(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("특이사항", style = DpType.titleM.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.TextSecondary)
                Text("${u.warnings.size}", style = DpType.titleM, color = DpColors.TextQuaternary)
            }
            if (u.warnings.isEmpty()) {
                Text("특이사항 없음", style = DpType.bodyL, color = DpColors.TextTertiary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { visibleWarnings(u.warnings).forEach { WarningItem(warningCard(it)) } }
            }
            if (fill) Box(Modifier.weight(1f))
            if (u.warnings.isNotEmpty()) DpCta("돌보러 가기 ›", onCare, Modifier.fillMaxWidth().height(80.dp))
        }
    }
}

/** 경고 카드 한 장(419:50688): 핸들 아이콘 40 + 제목 32 ExtraBold, 아래 설명 24. */
@Composable
private fun WarningItem(w: WarningCard) {
    Surface(
        Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(16.dp), ambientColor = DpColors.Shadow, spotColor = DpColors.Shadow),
        shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(2.dp, DpColors.Divider),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(R.drawable.ic_steering_wheel), null, Modifier.size(40.dp))
                Text(w.title, style = DpType.titleS, color = DpColors.Text)
            }
            Text(w.detail, style = DpType.labelM, color = DpColors.Text)
        }
    }
}
