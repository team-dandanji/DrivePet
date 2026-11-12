package com.drivepet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 분할 창(앱 2개, 폭 약 840dp)처럼 좁은 창. 이때는 나란한 칸을 위아래로 쌓고 화면 전체를 스크롤한다. */
@Composable
fun isNarrowWindow(): Boolean = LocalConfiguration.current.screenWidthDp < 1200

/** 좁은 창에서만 세로 스크롤. 넓은 창은 지금처럼 한 화면에 맞춘다. */
@Composable
fun Modifier.scrollWhen(on: Boolean): Modifier {
    val state = rememberScrollState()
    return if (on) verticalScroll(state) else this
}

/**
 * 넓은 창에서는 [content]의 칸들을 나란히, 좁은 창에서는 위아래로 쌓는다.
 * [content]는 칸마다 받은 Modifier를 쓴다(넓은 창: [weighted]면 같은 폭 weight, 좁은 창: 폭 채우기).
 */
@Composable
fun AdaptiveRow(
    narrow: Boolean,
    modifier: Modifier = Modifier,
    gap: Dp = 24.dp,
    weighted: Boolean = true,
    content: @Composable (cell: Modifier) -> Unit,
) {
    if (narrow) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) { content(Modifier.fillMaxWidth()) }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) { content(if (weighted) Modifier.weight(1f) else Modifier) }
    }
}
