package com.drivepet.app.ui.components

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.drivepet.app.R
import com.drivepet.app.domain.Mood

/**
 * 디자이너가 넘긴 Lottie 모션(res/raw). 모두 규격 크기에 맞춰 만든 것이라 [w]×[h] 비율 그대로 칸에 맞춰 그린다.
 * 지금은 병아리(STARTER_1, 2단계)와 알만 있다. 다른 캐릭터·단계는 [Character]의 정지 그림을 그대로 쓴다.
 */
enum class PetMotion(@RawRes val res: Int, val w: Int, val h: Int) {
    EGG_CHICK(R.raw.egg_chick, 400, 400),
    EGG_DINOSAUR(R.raw.egg_dinosaur, 400, 400),
    EGG_SNOWMAN(R.raw.egg_snowman, 400, 400),
    DEFAULT_CHICK(R.raw.default_chick, 1004, 480),
    SICK_CHICK(R.raw.sick_chick, 1004, 480),
    CAR_CHICK(R.raw.car_chick, 970, 512),
    REWARD_CHICK(R.raw.reward_chick, 798, 618),
}

/** 모션이 있는 캐릭터인가(병아리 2단계). */
fun hasChickMotion(starter: String, stage: Int): Boolean = starter == "STARTER_1" && stage == 2

/** 라운지 모션: 아픈 상태(SICK_*)면 sick, 아니면 기본. 모션이 없는 캐릭터는 null. */
fun homeMotion(starter: String, stage: Int, mood: Mood): PetMotion? = when {
    !hasChickMotion(starter, stage) -> null
    mood == Mood.SICK_1 || mood == Mood.SICK_2 || mood == Mood.SICK_3 -> PetMotion.SICK_CHICK
    else -> PetMotion.DEFAULT_CHICK
}

/** 알 선택 미리보기 모션. */
fun eggMotion(starter: String): PetMotion = when (starter) {
    "STARTER_2" -> PetMotion.EGG_DINOSAUR
    "STARTER_3" -> PetMotion.EGG_SNOWMAN
    else -> PetMotion.EGG_CHICK
}

/** [loop]이면 반복 재생, 아니면 [motion]이 바뀔 때마다 한 번만 재생하고 마지막 장면에서 멈춘다. */
@Composable
fun PetMotionView(motion: PetMotion, modifier: Modifier = Modifier, loop: Boolean = true) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(motion.res))
    val progress by animateLottieCompositionAsState(composition, iterations = if (loop) LottieConstants.IterateForever else 1)
    LottieAnimation(composition, { progress }, modifier, contentScale = ContentScale.Fit)
}
