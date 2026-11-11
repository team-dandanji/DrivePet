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
import androidx.compose.ui.unit.sp
import com.drivepet.app.drive.Bubble
import com.drivepet.app.ui.theme.DpColors

/** bubble이 null이면 아무것도 그리지 않는다(사라짐은 Speaker가 정한다). */
@Composable
fun SpeechBubble(bubble: Bubble?, modifier: Modifier = Modifier) {
    if (bubble == null) return
    Text(
        bubble.text,
        color = Color.White,
        fontSize = 26.sp,
        modifier = modifier.clip(RoundedCornerShape(50)).background(DpColors.Bubble).padding(horizontal = 32.dp, vertical = 16.dp),
    )
}
