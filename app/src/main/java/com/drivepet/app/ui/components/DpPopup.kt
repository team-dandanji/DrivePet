package com.drivepet.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/**
 * 같은 창 안의 팝업(별도 Window 없음): dim 20% + 가운데 흰 카드(419:53613).
 * 화면 루트 Box의 마지막 자식으로 둬야 맨 위에 그려진다.
 * dim 탭과 뒤로 가기는 [onDismissRequest]로 간다. 버튼이 둘이면 오른쪽이 주 동작이다.
 */
@Composable
fun DpPopup(
    title: String,
    body: String?,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
    icon: (@Composable () -> Unit)? = null,
    buttons: @Composable RowScope.() -> Unit,
) {
    BackHandler(onBack = onDismissRequest)
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(DpColors.Dim).clickable(none, indication = null, onClick = onDismissRequest),
        contentAlignment = Alignment.Center,
    ) {
        // 카드 안쪽 탭이 dim까지 올라가 닫히지 않게 막는다. 폭 최대 1000, 좁은 창에서는 양옆 40을 남긴다.
        Surface(
            Modifier.padding(horizontal = 40.dp).widthIn(max = 1000.dp).fillMaxWidth().clickable(none, indication = null) {},
            shape = RoundedCornerShape(40.dp), color = Color.White, border = BorderStroke(2.dp, DpColors.DividerPrimary),
        ) {
            Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(33.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 아이콘이 있으면 머리말이 왼쪽 정렬이다(하차 점검 419:55335).
                val align = if (icon != null) Alignment.Start else Alignment.CenterHorizontally
                val textAlign = if (icon != null) TextAlign.Start else TextAlign.Center
                Column(
                    Modifier.fillMaxWidth().padding(start = 27.dp, end = 27.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = align,
                ) {
                    icon?.invoke()
                    Text(title, style = DpType.titleS.copy(lineHeight = 43.sp), color = DpColors.Text, textAlign = textAlign)
                    if (body != null) Text(body, style = DpType.labelM.copy(lineHeight = 32.sp), color = DpColors.TextSecondary, textAlign = textAlign)
                }
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) { buttons() }
            }
        }
    }
}

/** 팝업 버튼(높이 84, radius 28, 글자 28 Bold). 버튼 줄 안에서 같은 폭을 나눠 가진다. primary는 차콜. */
@Composable
fun RowScope.PopupButton(text: String, onClick: () -> Unit, primary: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick, Modifier.weight(1f).height(84.dp).then(modifier), enabled = enabled, shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) DpColors.Filled else DpColors.ButtonBasic,
            contentColor = if (primary) Color.White else DpColors.Text,
            disabledContainerColor = DpColors.ButtonBasicPressed,
            disabledContentColor = DpColors.TextLightTertiary,
        ),
    ) { Text(text, style = DpType.bodyM.copy(fontWeight = FontWeight.Bold, lineHeight = 33.sp), maxLines = 1) }
}
