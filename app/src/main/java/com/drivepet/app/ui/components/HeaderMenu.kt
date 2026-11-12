package com.drivepet.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/**
 * 헤더 메뉴(피그마에 없음): 헤더 아래 모서리에 붙는 흰 카드 드롭다운. 팝업 토큰을 쓴다(흰 카드, r20, 테두리 2).
 * 화면 루트 Box의 자식으로 두고, 빈 곳을 누르거나 뒤로 가기를 하면 [onDismiss]. [alignment]는 TopStart(앱 이름) 또는 TopEnd(⋯).
 */
@Composable
fun HeaderMenu(alignment: Alignment, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    val none = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().clickable(none, indication = null, onClick = onDismiss)) {
        Surface(
            // 헤더(위 40 + 높이 80) 바로 아래. 카드 안쪽 탭이 닫힘으로 올라가지 않게 막는다.
            Modifier.align(alignment).padding(top = 128.dp, start = 40.dp, end = 24.dp).widthIn(min = 360.dp, max = 560.dp).clickable(none, indication = null) {},
            shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 8.dp, border = BorderStroke(2.dp, DpColors.DividerPrimary),
        ) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

/** 메뉴 한 줄(글자 28, 높이 최소 84). */
@Composable
fun MenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = DpColors.Text, enabled: Boolean = true) {
    Box(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 32.dp, vertical = 24.dp).then(modifier)) {
        Text(text, style = DpType.bodyM, color = if (enabled) color else DpColors.TextQuaternary)
    }
}
