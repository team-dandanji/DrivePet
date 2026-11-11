package com.drivepet.app.ui.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.onboarding.OnboardCard
import com.drivepet.app.ui.theme.DpColors

/**
 * 01 · 탑승 후 앱 실행. 저장된 프로필을 불러왔다고 알리고 라운지로 보낸다.
 * onSetup은 새로 시작하거나 프로필을 다시 설정할 때(기존 프로필은 지우지 않는다).
 */
@Composable
fun WelcomeScreen(vm: WelcomeViewModel, onLounge: () -> Unit, onSetup: () -> Unit) {
    val current by vm.current.collectAsStateWithLifecycle()
    val others by vm.others.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DrivePet", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("01  탑승 후 앱 실행", Modifier.padding(start = 48.dp), fontSize = 24.sp, color = DpColors.TextTertiary)
            Spacer(Modifier.weight(1f))
            Text("P · 주차 중", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1.4f).fillMaxSize(), Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("내 차와 함께 자라는\n반려차 메이트", fontSize = 56.sp, lineHeight = 76.sp, fontWeight = FontWeight.ExtraBold)
                    Text("차량 상태를 살피고, 안전한 주행을 함께 쌓아요.", fontSize = 28.sp, color = DpColors.TextTertiary)
                    OnboardCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("차량 상태   →   펫의 건강·포만감·성장", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                            for (line in listOf("주차 중에는 차량 케어", "주행 중에는 조용히 기록", "주행 후에는 점수와 성장 보상")) {
                                Text(line, fontSize = 26.sp)
                            }
                        }
                    }
                }
                FilledButton("처음이에요 · 시작하기", onSetup, Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                current?.let { c ->
                    OnboardCard(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("다시 만나서 반가워요", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                            Text("저장된 차량·펫을 불러왔어요.", fontSize = 26.sp, color = DpColors.TextTertiary)
                            Box(Modifier.fillMaxWidth().background(Color(0xFFF7F8FA), RoundedCornerShape(16.dp)).padding(24.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${c.vehicleName} · ${c.fuelLabel}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    Text("${c.petNickname}  /  Lv.${c.level}  /  로컬 프로필 복원 완료", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            // 카드는 내용만큼. 다른 프로필이 있을 때만 목록이 자리를 차지하고, 많으면 목록만 스크롤한다.
                            if (others.isNotEmpty()) {
                                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                                    Text("다른 프로필", fontSize = 24.sp, color = DpColors.TextTertiary)
                                    for (o in others) {
                                        Row(
                                            Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { vm.choose(o.profileId, onLounge) },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("${o.petNickname} · ${o.vehicleName} · Lv.${o.level}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            FilledButton("라운지로 이동", { vm.enter(onLounge) }, Modifier.fillMaxWidth())
                            OutlinedButton(
                                onSetup, Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(2.dp, DpColors.TrackInactive),
                            ) { Text("프로필 다시 설정", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DpColors.Text) }
                        }
                    }
                    Text("시동 후 앱 실행 · 차량 상태 기록 준비", fontSize = 22.sp, color = DpColors.TextTertiary)
                }
            }
        }
    }
}
