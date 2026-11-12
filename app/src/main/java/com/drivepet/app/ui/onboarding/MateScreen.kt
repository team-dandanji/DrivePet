package com.drivepet.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepet.app.R
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.LocalDriving
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.DpPopup
import com.drivepet.app.ui.components.DpTag
import com.drivepet.app.ui.components.PopupButton
import com.drivepet.app.ui.components.PopupSpec
import com.drivepet.app.ui.components.TagStyle
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 메이트 선택(404:41673)의 알 이름과 소개. */
private data class Starter(val type: String, val egg: String, val about: String)

private val STARTERS = listOf(
    Starter("STARTER_1", "노란색 알", "짧고 밝게 응원하는 병아리로 자라나요"),
    Starter("STARTER_2", "초록색 알", "힘차게 응원하는 공룡으로 자라나요"),
    Starter("STARTER_3", "흰색 알", "차분하게 응원하는 눈사람으로 자라나요"),
)

/** 이름 팝업(419:61024): 1200 폭, r48, 패딩 48, 제목 40 ExtraBold, 본문 30, 버튼 100 / r32 / 32 Bold. */
private val NAME_POPUP = PopupSpec(
    radius = 48.dp, padding = 48.dp, sectionGap = 40.dp,
    headerPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 16.dp),
    titleStyle = DpType.titleL.copy(fontWeight = FontWeight.ExtraBold), bodyStyle = DpType.bodyL,
    buttonHeight = 100.dp, buttonRadius = 32.dp, buttonStyle = DpType.titleS.copy(fontWeight = FontWeight.Bold), maxWidth = 1200.dp,
)

/**
 * 메이트 선택(404:41673 · 419:58793 · 419:59143 · 419:59493): 왼쪽 ‹ 알 › + 말풍선, 오른쪽 알 3개(기본 노란 알), [메이트 선택 완료] → 이름 팝업.
 * 팝업에서 2~8자면 [메이트 설정 완료]가 켜지고, 아니면 빨간 테두리 + 안내. 저장·활성화가 끝나면 [onDone].
 */
@Composable
fun MateScreen(vm: OnboardingViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    var naming by remember { mutableStateOf(false) }
    // 대화상자는 별도 창이라 주행 팝업 위에 그려진다. 주행이 시작되면 닫는다.
    val driving = LocalDriving.current
    LaunchedEffect(driving) { if (driving) naming = false }
    val index = STARTERS.indexOfFirst { it.type == vm.starter }.coerceAtLeast(0)
    val shift = { d: Int -> vm.starter = STARTERS[(index + d + STARTERS.size) % STARTERS.size].type }

    Box(Modifier.fillMaxSize()) {
        OnboardStep(
            "메이트 선택하기", 3, onBack, "함께 달릴 메이트를 선택해주세요", "메이트를 성장시키며 함께 달려보세요",
            cta = StepCta("메이트 선택 완료", true) { naming = true },
        ) { narrow ->
            val egg: @Composable (Modifier) -> Unit = { m ->
                Row(m, verticalAlignment = Alignment.CenterVertically) {
                    Slider(left = true) { shift(-1) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(25.dp)) {
                        Character(vm.starter, 1, Mood.NORMAL, size = if (narrow) 260.dp else 320.dp)
                        Text(
                            "나랑 같이 달려볼래요?", Modifier.clip(RoundedCornerShape(20.dp)).background(DpColors.Sky500).padding(horizontal = 28.dp, vertical = 20.dp),
                            style = DpType.bodyL.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1,
                        )
                    }
                    Slider(left = false) { shift(1) }
                }
            }
            val list: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (s in STARTERS) EggCard(s, s.type == vm.starter) { vm.starter = s.type }
                }
            }
            if (narrow) Column(verticalArrangement = Arrangement.spacedBy(40.dp)) { egg(Modifier.fillMaxWidth()); list(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(82.dp)) { egg(Modifier.width(922.dp)); list(Modifier.weight(1f)) }
        }
        if (naming && !driving) NamePopup(vm, STARTERS[index].egg, onDismiss = { naming = false }, onDone = onDone)
    }
}

/** ‹ / › (dp/slider_button 100, r28, dim 10%). */
@Composable
private fun Slider(left: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(100.dp).clip(RoundedCornerShape(28.dp)).background(DpColors.ButtonBasicPressed).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_back), if (left) "이전 알" else "다음 알",
            Modifier.size(70.dp).then(if (left) Modifier else Modifier.rotate(180f)), colorFilter = ColorFilter.tint(DpColors.Text),
        )
    }
}

/** 알 카드(319:35058): 흰 바탕 r20, 고르면 테두리 3 + 그림자 + 「선택 중」. */
@Composable
private fun EggCard(s: Starter, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.shadow(12.dp, shape, ambientColor = DpColors.Divider, spotColor = DpColors.Divider) else Modifier)
            .clip(shape).background(Color.White)
            .then(if (selected) Modifier.border(3.dp, DpColors.DividerTertiary, shape) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 40.dp, vertical = 36.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.egg, style = DpType.titleS, color = DpColors.Text)
            Text(s.about, style = DpType.labelM, color = DpColors.TextSecondary, maxLines = 1)
        }
        if (selected) DpTag("선택 중", TagStyle.Selected)
    }
}

/** 이름 팝업(419:57931 빈칸 · 419:57992 입력 · 419:61024 에러). 같은 창 안 DpPopup. */
@Composable
private fun NamePopup(vm: OnboardingViewModel, egg: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val error = vm.nickname.isNotEmpty() && vm.nicknameError() != null
    DpPopup(
        "메이트를 어떻게 부를까요?", "부화시킬 ${egg}의 이름을 지어주세요", onDismissRequest = { if (!saving) onDismiss() }, spec = NAME_POPUP,
        content = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val shape = RoundedCornerShape(24.dp)
                BasicTextField(
                    vm.nickname, { vm.nickname = it; saveFailed = false }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = DpType.titleM.copy(fontWeight = FontWeight.Normal, color = DpColors.Text),
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxWidth().height(100.dp).background(Color.White, shape)
                                .border(if (error) 3.dp else 2.dp, if (error) DpColors.RegulationRed else DpColors.Divider, shape).padding(horizontal = 32.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (vm.nickname.isEmpty()) Text("이름 입력하기 (2~8자)", style = DpType.titleM.copy(fontWeight = FontWeight.Normal), color = DpColors.TextQuaternary)
                            inner()
                        }
                    },
                )
                val help = if (saveFailed) "저장하지 못했어요. 다시 눌러 주세요" else if (error) vm.nicknameError() else null
                if (help != null) Text(help, Modifier.padding(horizontal = 16.dp), style = DpType.bodyS.copy(fontWeight = FontWeight.Normal), color = DpColors.RegulationRed)
            }
        },
    ) {
        PopupButton("메이트 설정 완료", {
            saving = true
            saveFailed = false
            scope.launch {
                try {
                    vm.finish()
                    onDone()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    saveFailed = true
                } finally {
                    saving = false
                }
            }
        }, primary = true, enabled = vm.canFinish && !saving)
    }
}
