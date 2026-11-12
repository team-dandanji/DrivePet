package com.drivepet.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepet.app.ui.theme.DpColors
import com.drivepet.app.ui.theme.DpType

/**
 * 팝업 규격. 기본값은 일반 팝업(419:53613). 화면별 피그마 노드 값이 다르면 그 값으로 만든 스펙을 넘긴다(예: 하차 점검 [ExitPopupSpec]).
 */
data class PopupSpec(
    val radius: Dp = 40.dp,
    val padding: Dp = 40.dp,
    val sectionGap: Dp = 33.dp,
    val headerPadding: PaddingValues = PaddingValues(start = 27.dp, end = 27.dp, bottom = 12.dp),
    val headerGap: Dp = 24.dp,
    val titleStyle: TextStyle = DpType.titleS.copy(lineHeight = 43.sp),
    val bodyStyle: TextStyle = DpType.labelM.copy(lineHeight = 32.sp),
    val titleColor: Color = DpColors.Text,
    val bodyColor: Color = DpColors.TextSecondary,
    val buttonHeight: Dp = 84.dp,
    val buttonRadius: Dp = 28.dp,
    val buttonStyle: TextStyle = DpType.bodyM.copy(fontWeight = FontWeight.Bold, lineHeight = 33.sp),
    val maxWidth: Dp = 1000.dp,
)

/**
 * 화면 안쪽 팝업을 앱 창 전체(탭 헤더 포함) 위에 그리는 자리. AppNav 루트가 [LocalPopupHost]로 주고, 헤더·NavHost 위에 [Content]를 그린다.
 * 화면은 NavHost 안(헤더 아래 칸)에 있어 그 자리에서 그리면 dim이 헤더를 못 덮기 때문이다.
 * 팝업 내용은 루트에서 그려지므로 그 안에서 viewModel()·rememberSaveable처럼 화면 항목에 묶인 것을 새로 부르지 않는다(바깥에서 만들어 넘긴다).
 */
class PopupHost {
    internal val slots = mutableStateListOf<State<@Composable () -> Unit>>()
    val isEmpty: Boolean get() = slots.isEmpty()

    @Composable
    fun Content() { slots.forEach { key(it) { it.value() } } }
}

/** null이면(호스트 밖: 주행·하차 점검 팝업처럼 이미 루트에 있는 것) 그 자리에서 바로 그린다. */
val LocalPopupHost = staticCompositionLocalOf<PopupHost?> { null }

/** [DpPopup]가 안쪽 [PopupButton]에 규격을 알려 준다. */
private val LocalPopupSpec = compositionLocalOf { PopupSpec() }

/**
 * 같은 창 안의 팝업(별도 Window 없음): dim 20% + 가운데 흰 카드(419:53613).
 * [LocalPopupHost]가 있으면 거기로 올려 앱 창 전체를 덮고, 없으면 그 자리(화면 루트 Box의 마지막 자식)에 그린다.
 * dim 탭과 뒤로 가기는 [onDismissRequest]로 간다. 버튼이 둘이면 오른쪽이 주 동작이다.
 */
@Composable
fun DpPopup(
    title: String,
    body: String?,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
    icon: (@Composable () -> Unit)? = null,
    spec: PopupSpec = PopupSpec(),
    buttons: @Composable RowScope.() -> Unit,
) {
    val host = LocalPopupHost.current
    if (host == null) {
        PopupLayer(title, body, onDismissRequest, content, icon, spec, buttons)
        return
    }
    // 호출 쪽이 다시 그려질 때마다 최신 내용으로 바꿔 둔다. 화면을 떠나면 호스트에서 뺀다.
    val latest = rememberUpdatedState<@Composable () -> Unit> { PopupLayer(title, body, onDismissRequest, content, icon, spec, buttons) }
    DisposableEffect(host) {
        host.slots.add(latest)
        onDispose { host.slots.remove(latest) }
    }
}

@Composable
private fun PopupLayer(
    title: String,
    body: String?,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    icon: (@Composable () -> Unit)?,
    spec: PopupSpec,
    buttons: @Composable RowScope.() -> Unit,
) {
    BackHandler(onBack = onDismissRequest)
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(DpColors.Dim).clickable(none, indication = null, onClick = onDismissRequest),
        contentAlignment = Alignment.Center,
    ) {
        // 카드 안쪽 탭이 dim까지 올라가 닫히지 않게 막는다. 좁은 창에서는 양옆 40을 남긴다.
        Surface(
            Modifier.padding(horizontal = 40.dp).widthIn(max = spec.maxWidth).fillMaxWidth().clickable(none, indication = null) {},
            shape = RoundedCornerShape(spec.radius), color = Color.White, border = BorderStroke(2.dp, DpColors.DividerPrimary),
        ) {
            Column(Modifier.padding(spec.padding), verticalArrangement = Arrangement.spacedBy(spec.sectionGap), horizontalAlignment = Alignment.CenterHorizontally) {
                // 아이콘이 있으면 머리말이 왼쪽 정렬이다(하차 점검 419:55335).
                val align = if (icon != null) Alignment.Start else Alignment.CenterHorizontally
                val textAlign = if (icon != null) TextAlign.Start else TextAlign.Center
                Column(Modifier.fillMaxWidth().padding(spec.headerPadding), verticalArrangement = Arrangement.spacedBy(spec.headerGap), horizontalAlignment = align) {
                    if (icon != null) Box(Modifier.padding(bottom = 12.dp)) { icon() }
                    Text(title, style = spec.titleStyle, color = spec.titleColor, textAlign = textAlign)
                    if (body != null) Text(body, style = spec.bodyStyle, color = spec.bodyColor, textAlign = textAlign)
                }
                content()
                CompositionLocalProvider(LocalPopupSpec provides spec) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) { buttons() }
                }
            }
        }
    }
}

/** 팝업 버튼(기본 높이 84, radius 28, 글자 28 Bold). 버튼 줄 안에서 같은 폭을 나눠 가진다. primary는 차콜. 크기는 팝업 규격을 따른다. */
@Composable
fun RowScope.PopupButton(text: String, onClick: () -> Unit, primary: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val spec = LocalPopupSpec.current
    Button(
        onClick, Modifier.weight(1f).height(spec.buttonHeight).then(modifier), enabled = enabled, shape = RoundedCornerShape(spec.buttonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) DpColors.Filled else DpColors.ButtonBasic,
            contentColor = if (primary) Color.White else DpColors.Text,
            disabledContainerColor = DpColors.ButtonBasicPressed,
            disabledContentColor = DpColors.TextLightTertiary,
        ),
    ) { Text(text, style = spec.buttonStyle, maxLines = 1) }
}
