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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.BuildConfig
import com.drivepet.app.R
import com.drivepet.app.domain.MissionStatus
import com.drivepet.app.domain.formatExp
import com.drivepet.app.drive.Speaker
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.DpTag
import com.drivepet.app.ui.components.SpeechBubble
import com.drivepet.app.ui.components.TagStyle
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

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
    val bubbleRow: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { SpeechBubble(bubble) } }
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
        when (loungeLayout(maxWidth.value.toInt(), maxHeight.value.toInt())) {
            // 17 Connect-S 분할 세로 창: 위아래로 쌓고 스크롤
            LoungeLayout.NARROW -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                header()
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Character(u.starter, u.stage, u.mood, devPress) }
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
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Character(u.starter, u.stage, u.mood, devPress) }
                    bubbleRow()
                    statusRow()
                }
                Column(Modifier.width(720.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stats()
                    WarningPanel(u, onCare, Modifier.weight(1f))
                    ExpCard(u)
                }
            }
            LoungeLayout.DEFAULT -> Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    header()
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Character(u.starter, u.stage, u.mood, devPress) }
                    bubbleRow()
                    statusRow()
                    ExpCard(u)
                }
                Column(Modifier.width(592.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("펫 컨디션", style = DpType.titleM.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.TextSecondary)
                    stats()
                    WarningPanel(u, onCare, Modifier.weight(1f))
                }
            }
        }
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
                u.gauge.needMilli?.let { "${formatExp(u.gauge.inLevelMilli)} / ${formatExp(it)} EXP" } ?: "MAX",
                style = DpType.labelM, color = DpColors.TextSecondary,
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

/** 특이사항 카드: 경고 카드 최대 2개(TPMS 먼저), 경고가 있을 때만 [돌보러 가기 ›]. [fill]이면 CTA를 카드 아래로 민다. */
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
        Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x14000000), spotColor = Color(0x14000000)),
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
