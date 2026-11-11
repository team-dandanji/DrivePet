package com.drivepet.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
}

fun Modifier.skyBackground(): Modifier = background(Brush.verticalGradient(listOf(DpColors.SkyTop, DpColors.SoftTop)))

@Composable
fun DrivePetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = DpColors.Filled, onSurface = DpColors.Text)) {
        // 창 전체 배경. Surface가 있어야 어두운 창 배경 위에서도 글자색이 DpColors.Text로 잡힌다.
        Box(Modifier.fillMaxSize().skyBackground()) {
            Surface(color = Color.Transparent, contentColor = DpColors.Text, modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}
