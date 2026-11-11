package com.drivepet.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit

/** Pleos_UI Hi-fi(Figma LVJcQ8Cjio1BUABnSWzC6h)에서 읽은 값. Figma px = dp/sp. */
object DpColors {
    val SkyTop = Color(0xFF89DEFF)
    val SoftTop = Color(0xFFD0F2FF)
    val Ground = Color(0xFFA8C98A)
    val Card = Color(0xD6FFFFFF)
    val Text = Color(0xFF131417)
    val TextTertiary = Color(0xA3131417)
    val Filled = Color(0xFF44464E)
    val TrackInactive = Color(0xFFC7C9CE)
    val Bubble = Color(0xFF609BB2)
    val Divider = Color(0x1A131417)
    val Danger = Color(0xFFE73612)
    val Positive = Color(0xFF32B957)
    /** 보상 칩 바탕(drivepet/palette/sky_300). */
    val Chip = Color(0xFF89DEFF)
    /** 보조 버튼 바탕(drivepet/button/secondary/bg). */
    val Secondary = Color(0x0D131417)
}

fun Modifier.skyBackground(): Modifier = background(Brush.verticalGradient(listOf(DpColors.SkyTop, DpColors.SoftTop)))

/**
 * 화면마다 글자 크기만 키워 쓰므로 줄 간격은 글자 크기를 따르게 하고(기본 24sp로 두면 두 줄일 때 겹친다),
 * 한국어는 글자 단위가 아니라 띄어쓰기 단위로 줄을 바꾼다(Android 13+ 구 단위 줄바꿈).
 */
private val KOREAN_LINE_BREAK = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase)
private fun TextStyle.fit() = copy(lineHeight = TextUnit.Unspecified, lineBreak = KOREAN_LINE_BREAK)
private val TYPOGRAPHY = Typography().run {
    Typography(
        displayLarge.fit(), displayMedium.fit(), displaySmall.fit(), headlineLarge.fit(), headlineMedium.fit(), headlineSmall.fit(),
        titleLarge.fit(), titleMedium.fit(), titleSmall.fit(), bodyLarge.fit(), bodyMedium.fit(), bodySmall.fit(),
        labelLarge.fit(), labelMedium.fit(), labelSmall.fit(),
    )
}

@Composable
fun DrivePetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = DpColors.Filled, onSurface = DpColors.Text), typography = TYPOGRAPHY) {
        // 창 전체 배경. Surface가 있어야 어두운 창 배경 위에서도 글자색이 DpColors.Text로 잡힌다.
        Box(Modifier.fillMaxSize().skyBackground()) {
            Surface(color = Color.Transparent, contentColor = DpColors.Text, modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}
