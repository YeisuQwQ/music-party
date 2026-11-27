@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.yiqidi.mobile

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val light = lightColorScheme(
    primary = Color(0xFF006A60), onPrimary = Color.White,
    primaryContainer = Color(0xFF9FF2E0), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DF), onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF73567F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5D9FF), onTertiaryContainer = Color(0xFF2B1238),
    background = Color(0xFFF6FBF8), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF6FBF8), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E0), onSurfaceVariant = Color(0xFF3E4945),
    surfaceDim = Color(0xFFD6DBD8), surfaceBright = Color(0xFFF6FBF8),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F6F2),
    surfaceContainer = Color(0xFFEAF0EC), surfaceContainerHigh = Color(0xFFE4EAE6),
    surfaceContainerHighest = Color(0xFFDEE4E0), outline = Color(0xFF6E7974),
    outlineVariant = Color(0xFFBEC9C3), inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFEDF3EF), inversePrimary = Color(0xFF82D5C4),
)

private val dark = darkColorScheme(
    primary = Color(0xFF82D5C4), onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047), onPrimaryContainer = Color(0xFF9FF2E0),
    secondary = Color(0xFFB1CCC3), onSecondary = Color(0xFF1C352F),
    secondaryContainer = Color(0xFF334B45), onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFE0BDEB), onTertiary = Color(0xFF41294C),
    tertiaryContainer = Color(0xFF5A3F65), onTertiaryContainer = Color(0xFFF5D9FF),
    background = Color(0xFF101513), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF101513), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3E4945), onSurfaceVariant = Color(0xFFBEC9C3),
    surfaceDim = Color(0xFF101513), surfaceBright = Color(0xFF353B38),
    surfaceContainerLowest = Color(0xFF0B0F0D), surfaceContainerLow = Color(0xFF181E1B),
    surfaceContainer = Color(0xFF1C2220), surfaceContainerHigh = Color(0xFF272D2B),
    surfaceContainerHighest = Color(0xFF323835), outline = Color(0xFF89948F),
    outlineVariant = Color(0xFF3E4945), inverseSurface = Color(0xFFDEE4E0),
    inverseOnSurface = Color(0xFF2B3230), inversePrimary = Color(0xFF006A60),
)

private val defaults = Typography()
private val musicTypography = defaults.copy(
    displaySmall = defaults.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = defaults.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleSmall = defaults.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    labelLarge = defaults.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
)

private val expressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
internal fun YiqidiTheme(mode: Int, content: @Composable (Boolean) -> Unit) {
    val darkTheme = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = remember(context, configuration, darkTheme) {
        when {
            Build.VERSION.SDK_INT >= 31 && darkTheme -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
            darkTheme -> dark
            else -> light
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = musicTypography,
        shapes = expressiveShapes,
    ) {
        content(darkTheme)
    }
}
