package com.drivepet.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.R
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.theme.DpColors

/**
 * 캐릭터 그림(피그마 APP Graphic `134:15508`). 시안에 없는 단계는 "이미지 없음".
 * 기본 크기는 [characterSize](14 레벨업 연출이 이 비율을 쓴다). 화면이 크게 그릴 때는 이 크기에 배율을 곱해 넘긴다. 표정 그림이 없어 [mood]는 그림에 쓰지 않는다(기분은 말풍선으로 보인다).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Character(starter: String, stage: Int, mood: Mood, modifier: Modifier = Modifier, size: Dp? = null) {
    val size = size ?: characterSize(stage)
    Box(modifier.width(size).height(size * 1.1f), contentAlignment = Alignment.BottomCenter) {
        // Hi-fi: 캐릭터 아래 납작한 타원 바닥. 높이는 피그마 그래픽 그룹의 타원/펫 키 비율(라운지 128/382, 정산 141/454 ≈ 0.32).
        Box(Modifier.requiredWidth(size * 1.1f).height(size * 0.32f).drawBehind { drawOval(DpColors.Ground) })
        val art = artOf(starter, stage)
        if (art != null) {
            // 발이 바닥 그림자 가운데쯤 오도록 아래를 조금 띄운다.
            Image(painterResource(art), null, Modifier.align(Alignment.TopCenter).size(size).padding(bottom = size * 0.02f), contentScale = ContentScale.Fit)
        } else {
            NoImage(size, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** stage별 기본 크기(펫 그림 높이): 1: 240, 2: 300, 3: 360dp. */
fun characterSize(stage: Int): Dp = when (stage) { 1 -> 240.dp; 2 -> 300.dp; else -> 360.dp }

/** 시안(APP Graphic)에 있는 그림만: 병아리(알·병아리·닭), 눈사람(흰 알·눈사람 — 3단계도 같은 눈사람, 134:15891), 공룡(알·2단계). 없는 단계는 null → "이미지 없음". */
private fun artOf(starter: String, stage: Int): Int? = when (starter) {
    "STARTER_1" -> when (stage) { 1 -> R.drawable.pet_starter1_stage1; 2 -> R.drawable.pet_starter1_stage2; else -> R.drawable.pet_starter1_stage3 }
    "STARTER_2" -> when (stage) { 1 -> R.drawable.pet_starter2_stage1; 2 -> R.drawable.pet_starter2_stage2; else -> null }
    "STARTER_3" -> if (stage == 1) R.drawable.pet_starter3_stage1 else R.drawable.pet_starter3_stage2
    else -> null
}

/** 그림이 없는 캐릭터 자리표시. */
@Composable
fun NoImage(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size * 0.8f).clip(RoundedCornerShape(24.dp)).background(DpColors.TrackInactive), contentAlignment = Alignment.Center) {
        Text("이미지 없음", fontSize = (size.value / 10).sp, color = DpColors.TextTertiary)
    }
}
