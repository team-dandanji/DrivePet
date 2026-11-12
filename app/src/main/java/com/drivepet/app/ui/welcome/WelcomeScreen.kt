package com.drivepet.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.components.scrollWhen
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.components.DpCard
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/**
 * 01 · 탑승 후 앱 실행. 저장된 프로필을 불러왔다고 알리고 라운지로 보낸다.
 * onSetup은 새로 시작하거나 프로필을 다시 설정할 때(기존 프로필은 지우지 않는다). onAccount는 카카오 계정 연결·바꾸기.
 */
@Composable
fun WelcomeScreen(vm: WelcomeViewModel, onLounge: () -> Unit, onSetup: () -> Unit, onAccount: () -> Unit) {
    val current by vm.current.collectAsStateWithLifecycle()
    val others by vm.others.collectAsStateWithLifecycle()
    val kakao by vm.kakao.collectAsStateWithLifecycle()

    val narrow = isNarrowWindow()
    val hero: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("내 차와 함께 자라는\n반려차 메이트", style = DpType.headlineM)
            Text("차량 상태를 살피고, 안전한 주행을 함께 쌓아요.", style = DpType.bodyM, color = DpColors.TextTertiary)
            DpCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("차량 상태   →   펫의 건강·포만감·성장", style = DpType.titleS)
                    for (line in listOf("주차 중에는 차량 케어", "주행 중에는 조용히 기록", "주행 후에는 점수와 성장 보상")) {
                        Text(line, style = DpType.bodyS.copy(fontWeight = FontWeight.Normal))
                    }
                }
            }
        }
    }
    // 좁은 창에서는 화면 전체가 스크롤하므로 카드 안 목록은 스크롤·가중치 없이 펼친다.
    val profile: @Composable ColumnScope.() -> Unit = {
        current?.let { c ->
            DpCard((if (narrow) Modifier else Modifier.weight(1f, fill = false)).fillMaxWidth()) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("다시 만나서 반가워요", style = DpType.titleL.copy(fontWeight = FontWeight.ExtraBold))
                    Text("저장된 차량·펫을 불러왔어요.", style = DpType.bodyS.copy(fontWeight = FontWeight.Normal), color = DpColors.TextTertiary)
                    Box(Modifier.fillMaxWidth().background(DpColors.SurfaceLow, RoundedCornerShape(16.dp)).padding(24.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${c.vehicleName} · ${c.fuelLabel}", style = DpType.bodyM.copy(fontWeight = FontWeight.Bold))
                            Text("${c.petNickname}  /  Lv.${c.level}  /  로컬 프로필 복원 완료", style = DpType.bodyM.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    // 카드는 내용만큼. 다른 프로필이 있을 때만 목록이 자리를 차지하고, 많으면 목록만 스크롤한다.
                    if (others.isNotEmpty()) {
                        Column(if (narrow) Modifier else Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            Text("다른 프로필", style = DpType.labelM, color = DpColors.TextTertiary)
                            for (o in others) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { vm.choose(o.profileId, onLounge) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${o.petNickname} · ${o.vehicleName} · Lv.${o.level}", style = DpType.bodyS)
                                }
                            }
                        }
                    }
                    FilledButton("라운지로 이동", { vm.enter(onLounge) }, Modifier.fillMaxWidth())
                    DpCta("프로필 다시 설정", onSetup, Modifier.fillMaxWidth(), primary = false)
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onAccount), verticalAlignment = Alignment.CenterVertically) {
                        Text(kakaoLabel(kakao), style = DpType.bodyS)
                    }
                }
            }
            Text("시동 후 앱 실행 · 차량 상태 기록 준비", style = DpType.labelM, color = DpColors.TextTertiary)
        }
    }

    Column(Modifier.fillMaxSize().scrollWhen(narrow).padding(40.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Carpet", style = DpType.titleM.copy(fontWeight = FontWeight.ExtraBold))
            Text("01  탑승 후 앱 실행", Modifier.padding(start = 48.dp), style = DpType.labelM, color = DpColors.TextTertiary)
            Spacer(Modifier.weight(1f))
            Text("P · 주차 중", style = DpType.bodyS)
        }
        if (narrow) {
            hero()
            profile()
            FilledButton("처음이에요 · 시작하기", onSetup, Modifier.fillMaxWidth())
        } else {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(Modifier.weight(1.4f).fillMaxSize(), Arrangement.SpaceBetween) {
                    hero()
                    FilledButton("처음이에요 · 시작하기", onSetup, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) { profile() }
            }
        }
    }
}
