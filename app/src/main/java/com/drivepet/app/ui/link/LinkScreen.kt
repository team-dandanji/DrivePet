package com.drivepet.app.ui.link

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepet.app.BuildConfig
import com.drivepet.app.R
import com.drivepet.app.ui.onboarding.OnboardStep
import com.drivepet.app.ui.onboarding.StepCta
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import kotlinx.coroutines.delay

private val QR_SIZE = 472.dp
private const val AUTO_NEXT_MS = 1_000L

/**
 * 카카오 QR 연결(403:36047 대기 · 403:40664 완료 · 419:60375 로그인 완료). 남은 시간은 대기 중 빨강, 그 밖에는 회색으로 멈춘다.
 * 연결되면 「인증을 완료했습니다」 + CTA가 켜지고 1초 뒤 스스로 [onLinked]로 넘어간다(CTA를 누르면 바로).
 * [login]이면 CTA 문구가 「로그인 완료」. [onBack]이 null이면 [이전]을 숨긴다(헤더 ‹ 는 아무것도 하지 않는다).
 */
@Composable
fun LinkScreen(vm: LinkViewModel, login: Boolean, onBack: (() -> Unit)?, onLinked: (LinkUi.Linked) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start() }
    var lastSec by remember { mutableIntStateOf(0) } // 대기가 끝나도 마지막 남은 시간을 회색으로 보인다
    val sec = when (val u = ui) { is LinkUi.Showing -> u.remainingSec; LinkUi.Expired -> 0; else -> lastSec }
    SideEffect { lastSec = sec }
    var done by remember { mutableStateOf(false) }
    val linked = ui as? LinkUi.Linked
    val go = { if (linked != null && !done) { done = true; onLinked(linked) } }
    LaunchedEffect(linked) { if (linked != null) { delay(AUTO_NEXT_MS); go() } }

    OnboardStep(
        "회원가입 및 로그인", 0, onBack ?: {}, "QR코드를 스캔하여 카카오톡 계정을 연결해주세요", "휴대폰에서 동의하면 자동으로 연결돼요",
        dots = 2 to 1, showPrev = onBack != null, cta = StepCta(if (login) "로그인 완료" else "다음", linked != null, go),
    ) { narrow ->
        val qr: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(540.dp).background(Color.White, RoundedCornerShape(12.dp)), Alignment.Center) {
                    if (ui is LinkUi.Showing) QrImage((ui as LinkUi.Showing).qrUrl, QR_SIZE)
                }
                // 시험용: 호스트 브라우저로 열어 가짜 로그인을 해 볼 수 있게 주소를 보여 준다. 릴리스에는 없다.
                if (BuildConfig.DEMO_TOOLS) (ui as? LinkUi.Showing)?.let {
                    Text(it.qrUrl, Modifier.width(540.dp), style = DpType.labelS, color = DpColors.TextTertiary)
                }
            }
        }
        val side: @Composable () -> Unit = {
            Column(Modifier.widthIn(max = 509.dp)) {
                Text("카카오톡 앱  > 더보기 > 우측 상단의\n버튼을 눌러 QR코드를 스캔하세요", style = DpType.titleM.copy(fontWeight = FontWeight.Normal), color = DpColors.Text)
                Spacer(Modifier.height(if (narrow) 48.dp else 136.dp))
                Column(Modifier.widthIn(max = 413.dp), verticalArrangement = Arrangement.spacedBy(44.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(statusLine(ui), style = DpType.titleL.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.TextTertiary)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("남은 시간", style = DpType.headlineS.copy(fontWeight = FontWeight.Bold), color = DpColors.Text)
                            Text(
                                "%02d:%02d".format(sec / 60, sec % 60), style = DpType.headlineS,
                                color = if (ui is LinkUi.Showing) DpColors.InformativeNegative else DpColors.TextQuaternary,
                            )
                        }
                    }
                    RetryButton(enabled = ui !is LinkUi.Restoring && ui !is LinkUi.Linked, vm::retry) // 복원 중에 새 세션으로 끊지 않는다
                }
            }
        }
        if (narrow) Column(verticalArrangement = Arrangement.spacedBy(40.dp)) { qr(); side() }
        else Row(horizontalArrangement = Arrangement.spacedBy(52.dp)) { qr(); side() }
    }
}

/** [QR 다시받기 ↻](374:56694): surface_low, 테두리 2, r20. */
@Composable
private fun RetryButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(DpColors.SurfaceLow).border(2.dp, DpColors.DividerTertiary, shape)
            .clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.4f).padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("QR 다시받기", style = DpType.titleS.copy(fontWeight = FontWeight.Normal), color = DpColors.Text, maxLines = 1)
        Image(painterResource(R.drawable.ic_refresh), null, Modifier.size(40.dp))
    }
}

private fun statusLine(ui: LinkUi): String = when (ui) {
    LinkUi.Loading -> "QR을 만드는 중…"
    is LinkUi.Showing -> "인증 대기중"
    is LinkUi.Restoring -> if (ui.nickname.isBlank()) "기록을 불러오는 중…" else "${ui.nickname}님의 기록을 불러오는 중…"
    is LinkUi.Linked -> "인증을 완료했습니다"
    LinkUi.Expired -> "인증 시간이 지났어요"
    is LinkUi.Failed -> ui.message
}
