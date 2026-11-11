package com.drivepet.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.theme.DpColors

private val EggWhite = Color(0xFFF2F2F2)

private fun faceOf(mood: Mood) = when (mood) {
    Mood.NORMAL -> "·ω·"
    Mood.HAPPY -> "^ω^"
    Mood.HUNGRY -> "´ω`"
    Mood.WORRIED -> "•﹏•"
    Mood.SICK_1, Mood.SICK_2, Mood.SICK_3 -> "×﹏×"
}

/** 그림이 나오기 전까지의 자리 표시: 계열별 색 원 + 표정 문자, stage마다 크기(1: 240, 2: 300, 3: 360dp). */
@Composable
fun Character(starter: String, stage: Int, mood: Mood, modifier: Modifier = Modifier) {
    val size = when (stage) { 1 -> 240.dp; 2 -> 300.dp; else -> 360.dp }
    val color = when (starter) {
        "STARTER_2" -> Color(0xFF7CC46B)
        "STARTER_3" -> EggWhite
        else -> Color(0xFFF6C343)
    }
    Box(modifier.width(size).height(size * 1.1f), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.width(size * 0.95f).height(size * 0.2f).clip(CircleShape).background(DpColors.Ground))
        Box(
            Modifier.align(Alignment.TopCenter).size(size).clip(CircleShape).background(color)
                .then(if (starter == "STARTER_3") Modifier.border(BorderStroke(2.dp, DpColors.TrackInactive), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(faceOf(mood), fontSize = (size.value / 4).sp, color = DpColors.Text)
        }
    }
}
