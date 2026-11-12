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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.drivepet.app.R

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

    // HIFI 토큰(Task 1에서 추가). 값은 라운지 앱 창 419:50656의 변수.
    val SurfaceLow = Color(0xFFF7F8FA)
    val TextSecondary = Color(0xD6131417)
    val TextQuaternary = Color(0x52131417)
    /** 비활성 CTA의 흰 글자(text_light_tertiary). */
    val TextLightTertiary = Color(0xA3FFFFFF)
    val Informative = Color(0xFF0064FF)
    /** 피그마 「보통/주의」 태그 주황. */
    val Warning = Color(0xFFFF8A00)
    /** 팝업 뒤 어둡게(dim_dark_tertiary, 약 20%). */
    val Dim = Color(0x33131417)
    val DividerPrimary = Color(0x0D131417)
    val DividerTertiary = Color(0x33131417)
    val ButtonBasic = Color(0x0D131417)
    /** 비활성 CTA 바탕(button_basic_pressed). */
    val ButtonBasicPressed = Color(0x1A131417)
    val TagNeutral = Color(0xFF686A72)
    val GlassStrong = Color(0x80FFFFFF)
    val Sky500 = Color(0xFF609BB2)
    /** 경로·번호 원 파랑(drivepet/control/on). */
    val ControlOn = Color(0xFF4781FF)
    /** 선택된 카드 바탕(drivepet/palette/sky_50). */
    val Sky50 = Color(0xFFE7F8FF)
}

/** Sky: sky_top → surface_low(70% 지점), 이후 단색. 180° 세로. */
fun Modifier.skyBackground(): Modifier =
    background(Brush.verticalGradient(0f to DpColors.SkyTop, 0.7f to DpColors.SurfaceLow, 1f to DpColors.SurfaceLow))

/** Soft: soft_top → surface_low(50% 지점), 이후 단색. 180° 세로. */
fun Modifier.softBackground(): Modifier =
    background(Brush.verticalGradient(0f to DpColors.SoftTop, 0.5f to DpColors.SurfaceLow, 1f to DpColors.SurfaceLow))

// 글꼴 Asta Sans(SIL OFL 1.1, https://github.com/google/fonts/tree/main/ofl/astasans)는 가변 글꼴(wght 300~800) 한 파일이다.
// 기본 축 값이 300이라 400/700/800을 variationSettings로 직접 지정한다(minSdk 28에서 지원).
@OptIn(ExperimentalTextApi::class)
private fun astaSans(w: Int) =
    Font(R.font.asta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val AstaSans = FontFamily(astaSans(400), astaSans(700), astaSans(800))

/**
 * 화면마다 글자 크기만 키워 쓰므로 줄 간격은 글자 크기를 따르게 하고(기본 24sp로 두면 두 줄일 때 겹친다),
 * 한국어는 글자 단위가 아니라 띄어쓰기 단위로 줄을 바꾼다(Android 13+ 구 단위 줄바꿈).
 */
private val KOREAN_LINE_BREAK = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase)
private fun TextStyle.fit() = copy(fontFamily = AstaSans, lineHeight = TextUnit.Unspecified, lineBreak = KOREAN_LINE_BREAK)
private val TYPOGRAPHY = Typography().run {
    Typography(
        displayLarge.fit(), displayMedium.fit(), displaySmall.fit(), headlineLarge.fit(), headlineMedium.fit(), headlineSmall.fit(),
        titleLarge.fit(), titleMedium.fit(), titleSmall.fit(), bodyLarge.fit(), bodyMedium.fit(), bodySmall.fit(),
        labelLarge.fit(), labelMedium.fit(), labelSmall.fit(),
    )
}

/** 크기/줄 간격은 피그마 토큰(spec 2절). 굵기는 가장 흔한 쓰임이고, 다르면 화면에서 `.copy(fontWeight = ...)`. */
object DpType {
    private fun t(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, lineBreak = KOREAN_LINE_BREAK)

    val displayL = t(80, 104, FontWeight.Bold)
    val headlineL = t(60, 80, FontWeight.ExtraBold)
    val headlineM = t(56, 72, FontWeight.ExtraBold)
    val headlineS = t(48, 64, FontWeight.ExtraBold)
    val titleL = t(40, 52, FontWeight.Bold)
    val titleM = t(36, 44, FontWeight.Bold)
    val titleS = t(32, 40, FontWeight.ExtraBold)
    val bodyL = t(30, 38, FontWeight.Normal)
    val bodyM = t(28, 36, FontWeight.Normal)
    val bodyS = t(26, 34, FontWeight.Bold)
    val labelM = t(24, 28, FontWeight.Normal)
    val labelS = t(20, 24, FontWeight.Normal)
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
