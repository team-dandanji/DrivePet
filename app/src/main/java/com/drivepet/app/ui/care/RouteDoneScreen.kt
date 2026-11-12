package com.drivepet.app.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.PetMotion
import com.drivepet.app.ui.components.PetMotionView
import com.drivepet.app.ui.components.hasChickMotion
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.components.scrollWhen
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/** 목적어 조사: 마지막 글자(괄호·기호 앞)에 받침이 있으면 을, 없으면 를. 한글이 아니면 을(를). */
internal fun objectParticle(word: String): String {
    val c = word.trimEnd { !it.isLetterOrDigit() }.lastOrNull() ?: return "을(를)"
    if (c !in '가'..'힣') return "을(를)"
    return if ((c - '가') % 28 == 0) "를" else "을"
}

/**
 * 경로 등록 완료(D3 419:51484). [출발하기]는 [onStart]가 내비 안내를 시작하고 true면 화면을 떠난다.
 * false면 「내비를 열지 못했어요」를 보여 준다.
 */
@Composable
fun RouteDoneScreen(placeName: String, starter: String, stage: Int, onBack: () -> Unit, onLounge: () -> Unit, onStart: () -> Boolean) {
    val narrow = isNarrowWindow()
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().scrollWhen(narrow)) {
        CareBackHeader("펫 돌보기", onBack, divider = false)
        Column(
            (if (narrow) Modifier else Modifier.weight(1f)).fillMaxWidth().padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(52.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 목적지 등록 모션 970×512(반복). 모션이 없는 캐릭터는 정지 그림
            if (hasChickMotion(starter, stage)) PetMotionView(PetMotion.CAR_CHICK, Modifier.fillMaxWidth().height(if (narrow) 360.dp else 512.dp))
            else Character(starter, stage, Mood.HAPPY, size = if (narrow) 360.dp else 440.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$placeName${objectParticle(placeName)}\n네비게이션 목적지로 등록했어요",
                    style = DpType.headlineL, color = DpColors.Text, textAlign = TextAlign.Center,
                )
                Text(
                    "경로를 확인하고 정비소로 이동해 주세요. 정비소 방문 후 관리 내역을 기록할 수 있어요",
                    style = DpType.titleS.copy(fontWeight = FontWeight.Normal), color = DpColors.Text, textAlign = TextAlign.Center,
                )
                if (failed) Text("내비를 열지 못했어요", style = DpType.titleS.copy(fontWeight = FontWeight.Bold), color = DpColors.Danger)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 68.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        ) {
            val cell = if (narrow) Modifier.weight(1f) else Modifier.width(616.dp)
            DpCta("라운지로", onLounge, cell, primary = false)
            DpCta("출발하기", { failed = !onStart() }, cell)
        }
    }
}
