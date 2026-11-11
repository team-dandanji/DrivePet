package com.drivepet.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.R
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.theme.DpColors

/**
 * 캐릭터 그림. 메인 캐릭터(STARTER_1)만 피그마 APP Graphic(`134:15508`) 그림이 있고 나머지는 "이미지 없음".
 * 크기는 stage마다 1: 240, 2: 300, 3: 360dp(14 레벨업 연출이 이 비율을 쓴다). 표정 그림이 없어 [mood]는 그림에 쓰지 않는다(말풍선·태그로 보인다).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Character(starter: String, stage: Int, mood: Mood, modifier: Modifier = Modifier) {
    val size = when (stage) { 1 -> 240.dp; 2 -> 300.dp; else -> 360.dp }
    Box(modifier.width(size).height(size * 1.1f), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.width(size * 0.95f).height(size * 0.2f).clip(CircleShape).background(DpColors.Ground))
        val art = if (starter == "STARTER_1") when (stage) {
            1 -> R.drawable.pet_starter1_stage1
            2 -> R.drawable.pet_starter1_stage2
            else -> R.drawable.pet_starter1_stage3
        } else null
        if (art != null) {
            // 발이 바닥 그림자 가운데쯤 오도록 아래를 조금 띄운다.
            Image(painterResource(art), null, Modifier.align(Alignment.TopCenter).size(size).padding(bottom = size * 0.02f), contentScale = ContentScale.Fit)
        } else {
            NoImage(size, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** 그림이 없는 캐릭터 자리표시. */
@Composable
fun NoImage(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size * 0.8f).clip(RoundedCornerShape(24.dp)).background(DpColors.TrackInactive), contentAlignment = Alignment.Center) {
        Text("이미지 없음", fontSize = (size.value / 10).sp, color = DpColors.TextTertiary)
    }
}
