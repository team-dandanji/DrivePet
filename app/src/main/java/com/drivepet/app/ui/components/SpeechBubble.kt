package com.drivepet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivepet.app.drive.Bubble
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/** 피그마 dp/speech 한 줄 최대 글자 수(공백 포함). */
private const val BUBBLE_MAX = 15

/** 문장 끝: . ! ? … 묶음 뒤에 공백이나 글 끝이 올 때(3.5km의 점은 끊지 않는다). */
private val SENTENCE_END = Regex("""[.!?…]+(?=\s|$)""")

/**
 * 말풍선에 보일 짧은 한 줄. 목소리는 전체 문장을 읽고 말풍선은 첫 문장만 보인다.
 * 첫 문장이 15자(공백 포함)를 넘으면 15자 안의 마지막 띄어쓰기에서, 없으면 15자에서 자른다(서로게이트 쌍은 쪼개지 않는다).
 */
fun bubbleLine(text: String): String {
    val t = text.trim()
    val first = SENTENCE_END.find(t)?.let { t.substring(0, it.range.last + 1) } ?: t
    if (first.codePointCount(0, first.length) <= BUBBLE_MAX) return first
    val cut = first.substring(0, first.offsetByCodePoints(0, BUBBLE_MAX))
    if (first[cut.length] == ' ') return cut.trimEnd() // 바로 뒤가 띄어쓰기면 마지막 단어가 꼭 맞게 들어간다
    val space = cut.lastIndexOf(' ')
    return (if (space > 0) cut.substring(0, space) else cut).trimEnd()
}

/** bubble이 null이면 아무것도 그리지 않는다(사라짐은 Speaker가 정한다). 모양은 DpSpeech: sky_500, r20, 32 ExtraBold 흰 글자. */
@Composable
fun SpeechBubble(bubble: Bubble?, modifier: Modifier = Modifier) {
    if (bubble == null) return
    Text(
        bubbleLine(bubble.text),
        style = DpType.titleS,
        color = Color.White,
        // dp/speech는 한 줄(최대 15자)이고 넘치면 말줄임
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(DpColors.Sky500).padding(horizontal = 28.dp, vertical = 20.dp),
    )
}
