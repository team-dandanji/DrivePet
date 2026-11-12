package com.drivepet.app.ui.settlement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.drivepet.app.domain.Mood
import com.drivepet.app.ui.components.Character
import com.drivepet.app.ui.components.DpCta
import com.drivepet.app.ui.components.DpTag
import com.drivepet.app.ui.components.TagStyle
import com.drivepet.app.ui.components.isNarrowWindow
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

private const val EVOLVE_MS = 1500

private val Bottom = TransformOrigin(0.5f, 1f)

private fun sizeOf(stage: Int) = when (stage) { 1 -> 240f; 2 -> 300f; else -> 360f }

/** 14 레벨 업·진화: 이전 단계 캐릭터가 새 단계로 1.5초 동안 크기·투명도가 바뀌며 넘어간다(정차 화면이라 허용). [visible]이 false인 동안(덮개가 가리는 중)에는 연출을 시작하지 않는다. */
@Composable
fun LevelUpScreen(starter: String, before: Int, after: Int, visible: Boolean, onDone: () -> Unit) {
    val p = remember(before, after) { Animatable(0f) }
    LaunchedEffect(p, visible) { if (visible) p.animateTo(1f, tween(EVOLVE_MS)) }
    val ratio = sizeOf(after) / sizeOf(before)
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(20.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Text("레벨 업!", style = DpType.headlineL, color = DpColors.Text)
        DpTag("Lv.$before → Lv.$after", TagStyle.RewardSoft)
        Box(Modifier.width(400.dp), contentAlignment = Alignment.BottomCenter) {
            Character(
                starter, before, Mood.HAPPY,
                Modifier.graphicsLayer { alpha = 1f - p.value; val k = 1f + (ratio - 1f) * p.value; scaleX = k; scaleY = k; transformOrigin = Bottom },
            )
            Character(
                starter, after, Mood.HAPPY,
                Modifier.graphicsLayer { alpha = p.value; val k = 1f / ratio + (1f - 1f / ratio) * p.value; scaleX = k; scaleY = k; transformOrigin = Bottom },
            )
        }
        DpCta("좋아요", onDone, if (isNarrowWindow()) Modifier.fillMaxWidth().padding(horizontal = 40.dp) else Modifier.width(616.dp))
    }
}
