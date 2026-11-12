package com.drivepet.app.ui.onboarding

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.components.AppHeader
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.components.scrollWhen
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType
import com.drivepet.app.ui.theme.skyBackground

private const val TAGLINE = "내 차와 함께 자라는\n반려 차 메이트"

/**
 * 인트로(408:44708, 배경 Sky). 로고 자리(300×100)는 글자 「Carpet」, 그래픽 자리(1040×900)는 지금 캐릭터(병아리 알).
 * [계정 만들기] → 약관, [이미 계정이 있어요] → QR 로그인.
 */
@Composable
fun IntroScreen(onCreate: () -> Unit, onLogin: () -> Unit) {
    val narrow = isNarrowWindow()
    Column(Modifier.fillMaxSize().skyBackground()) {
        AppHeader()
        if (narrow) {
            Column(Modifier.weight(1f).fillMaxWidth().scrollWhen(true).padding(40.dp), verticalArrangement = Arrangement.spacedBy(44.dp)) {
                Logo()
                Text(TAGLINE, style = DpType.displayL.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.Text)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Character("STARTER_1", 1, Mood.NORMAL, size = 320.dp) }
                Buttons(onCreate, onLogin, Modifier.fillMaxWidth())
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.padding(start = 40.dp, top = 71.dp), verticalArrangement = Arrangement.spacedBy(44.dp)) {
                    Logo()
                    Text(TAGLINE, style = DpType.displayL.copy(fontWeight = FontWeight.ExtraBold), color = DpColors.Text)
                }
                // 그래픽 자리: 오른쪽 위 1040×900. 버튼이 아래쪽에 겹친다(피그마 그대로).
                Box(Modifier.align(Alignment.TopEnd).padding(top = 71.dp).size(1040.dp, 900.dp), contentAlignment = Alignment.Center) {
                    Character("STARTER_1", 1, Mood.NORMAL, Modifier.padding(bottom = 200.dp), size = 400.dp)
                }
                Buttons(onCreate, onLogin, Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 68.dp).width(1003.dp))
            }
        }
    }
}

@Composable
private fun Logo() {
    Box(Modifier.size(300.dp, 100.dp), contentAlignment = Alignment.CenterStart) {
        Text("Carpet", style = DpType.headlineL, color = DpColors.Text)
    }
}

@Composable
private fun Buttons(onCreate: () -> Unit, onLogin: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(32.dp)) {
        DpCta("계정 만들기", onCreate, Modifier.fillMaxWidth())
        DpCta("이미 계정이 있어요", onLogin, Modifier.fillMaxWidth(), primary = false)
    }
}
