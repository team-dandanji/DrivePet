package com.drivepet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drivepet.app.drive.Bubble
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/** bubble이 null이면 아무것도 그리지 않는다(사라짐은 Speaker가 정한다). 모양은 DpSpeech: sky_500, r20, 32 ExtraBold 흰 글자. */
@Composable
fun SpeechBubble(bubble: Bubble?, modifier: Modifier = Modifier) {
    if (bubble == null) return
    Text(
        bubble.text,
        style = DpType.titleS,
        color = Color.White,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(DpColors.Sky500).padding(horizontal = 28.dp, vertical = 20.dp),
    )
}
