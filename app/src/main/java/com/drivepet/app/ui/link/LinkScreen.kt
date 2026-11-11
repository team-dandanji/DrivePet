package com.drivepet.app.ui.link

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.BuildConfig
import com.drivepet.app.ui.components.FilledButton
import com.drivepet.app.ui.onboarding.OnboardCard
import com.drivepet.app.ui.theme.DpColors

private val CONSENTS = listOf(
    "[동의] 서비스 이용약관 · 개인정보 처리",
    "[동의] 차량 데이터 조회 · 정비 관리",
    "[선택] 위치 정보 · 날씨 및 주변 장소",
)
private val QR_SIZE = 200.dp

/**
 * 03 · 권한 동의 · 계정 연결. 안내 글(동의 항목은 읽기만)과 카카오 QR 로그인.
 * [onBack]이 null이면 "뒤로"를, [onSkip]이 null이면 "나중에 연결하기"를 숨긴다. 연결되면 [onLinked]가 불린다.
 */
@Composable
fun LinkScreen(vm: LinkViewModel, onBack: (() -> Unit)?, onSkip: (() -> Unit)?, onLinked: (LinkUi.Linked) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(ui) { (ui as? LinkUi.Linked)?.let(onLinked) }

    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DrivePet", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("01  권한 동의 · 계정 연결", Modifier.padding(start = 48.dp), fontSize = 24.sp, color = DpColors.TextTertiary)
            Spacer(Modifier.weight(1f))
            Text("P · 주차 중", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Text("스마트폰으로 간편하게 연결해요", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OnboardCard(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("01  필요한 정보 확인", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    for (line in CONSENTS) Text(line, fontSize = 26.sp)
                    Text(
                        "차량별로 지원되는 정보만 조회해요.\n위치 동의 없이도 기본 차량 관리를 이용할 수 있어요.",
                        Modifier.padding(top = 8.dp), fontSize = 22.sp, color = DpColors.TextTertiary,
                    )
                }
            }
            OnboardCard(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("02  카카오 QR 로그인", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QrBox(ui)
                            // 시험용: 호스트 브라우저로 열어 가짜 로그인을 해 볼 수 있게 주소를 보여 준다. 릴리스에는 없다.
                            if (BuildConfig.DEMO_TOOLS) (ui as? LinkUi.Showing)?.let {
                                Text(it.qrUrl, Modifier.width(QR_SIZE), fontSize = 14.sp, color = DpColors.TextTertiary)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("스마트폰 카메라로 스캔 후\n카카오 계정으로 로그인하세요.", fontSize = 24.sp)
                            Text(statusLine(ui), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            OutlinedButton(
                                vm::retry, Modifier.fillMaxWidth().height(80.dp),
                                enabled = ui !is LinkUi.Restoring && ui !is LinkUi.Linked, // 복원 중에 새 세션으로 끊지 않는다
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(2.dp, DpColors.TrackInactive),
                            ) { Text("QR 다시 받기", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DpColors.Text) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (onBack != null) {
                OutlinedButton(
                    onBack, Modifier.width(220.dp).height(96.dp), shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, DpColors.TrackInactive),
                ) { Text("뒤로", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DpColors.Text) }
            } else Spacer(Modifier.width(1.dp))
            if (onSkip != null) FilledButton("나중에 연결하기", { vm.stop(); onSkip() }, Modifier.width(420.dp))
        }
    }
}

/** 연결 대기 중에만 QR을 그리고, 그 밖에는 같은 크기의 회색 칸을 둔다. */
@Composable
private fun QrBox(ui: LinkUi) {
    Box(
        Modifier.size(QR_SIZE).background(if (ui is LinkUi.Showing) Color.White else Color(0xFFF1F2F4), RoundedCornerShape(12.dp)),
        Alignment.Center,
    ) {
        if (ui is LinkUi.Showing) QrImage(ui.qrUrl, QR_SIZE)
        else Text("QR", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = DpColors.TrackInactive)
    }
}

private fun statusLine(ui: LinkUi): String = when (ui) {
    LinkUi.Loading -> "QR을 만드는 중…"
    is LinkUi.Showing -> "인증 대기 중 · 남은 시간 %d:%02d".format(ui.remainingSec / 60, ui.remainingSec % 60)
    is LinkUi.Restoring -> if (ui.nickname.isBlank()) "기록을 불러오는 중…" else "${ui.nickname}님의 기록을 불러오는 중…"
    is LinkUi.Linked -> if (ui.nickname.isBlank()) "연결됐어요" else "${ui.nickname}님과 연결됐어요"
    LinkUi.Expired -> "QR이 만료됐어요"
    is LinkUi.Failed -> ui.message
}
