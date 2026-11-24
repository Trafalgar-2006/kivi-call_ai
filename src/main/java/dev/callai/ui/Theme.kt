package dev.callai.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.callai.R
import dev.callai.calls.Mode

/** Truck-paint palette. The tailgate keeps these in both themes: it's paint, not chrome. */
object Paint {
    val Night = Color(0xFF0B1C26)
    val Marigold = Color(0xFFF2B21B)
    val Vermilion = Color(0xFFC7372B)
    val Leaf = Color(0xFF23774A)
    val Cream = Color(0xFFF4ECDC)
    val Resting = Color(0xFF33434B)
}

fun modeColor(mode: Mode?): Color = when (mode) {
    Mode.SLEEP -> Color(0xFF4B3CA7)
    Mode.BUSY -> Color(0xFF9A6400)
    Mode.DRIVE -> Paint.Leaf
    Mode.AWAY -> Color(0xFFB0352B)
    Mode.STRANGERS -> Color(0xFF14627A)
    null -> Paint.Resting
}

/** Hand-painted sign lettering. Used at exactly two sizes, only on the tailgate. */
val Sign = FontFamily(Font(R.font.yatra_one))

@Composable fun signLarge(): TextStyle {
    val shade = with(LocalDensity.current) { 2.5.dp.toPx() }
    return TextStyle(fontFamily = Sign, fontSize = 42.sp, lineHeight = 44.sp, color = Paint.Cream,
        shadow = Shadow(Paint.Vermilion, Offset(shade, shade), 0f)) // painted drop-shade, a sign-painter's hard edge
}
val SignSmall = TextStyle(fontFamily = Sign, fontSize = 19.sp, lineHeight = 24.sp, color = Paint.Marigold, letterSpacing = 0.6.sp)

/** One motion grammar everywhere: a single duration with a small overshoot. */
const val MOTION_MS = 350
val KiviEase = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)
fun <T> kiviMotion(): FiniteAnimationSpec<T> = tween(MOTION_MS, easing = KiviEase)

private val Dark = darkColorScheme(
    primary = Paint.Marigold, onPrimary = Paint.Night,
    primaryContainer = Color(0xFF3B300E), onPrimaryContainer = Color(0xFFFFE2A0),
    secondary = Color(0xFFFF8A7A), onSecondary = Color(0xFF3A0A05),
    secondaryContainer = Color(0xFF3B300E), onSecondaryContainer = Paint.Marigold,
    tertiary = Color(0xFF7CC3D6), onTertiary = Paint.Night,
    tertiaryContainer = Color(0xFF14627A), onTertiaryContainer = Color(0xFFE3F5FA),
    background = Paint.Night, onBackground = Paint.Cream,
    surface = Paint.Night, onSurface = Paint.Cream,
    surfaceVariant = Color(0xFF1A3542), onSurfaceVariant = Color(0xFFA9C1CC),
    surfaceContainerLowest = Color(0xFF071219), surfaceContainerLow = Color(0xFF0F2430),
    surfaceContainer = Color(0xFF132B38), surfaceContainerHigh = Color(0xFF183442), surfaceContainerHighest = Color(0xFF1E3D4C),
    outline = Color(0xFF55707D), outlineVariant = Color(0xFF2A4552),
    error = Color(0xFFFF8A7A), onError = Color(0xFF3A0A05),
    errorContainer = Color(0xFF5C1A14), onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Paint.Cream, inverseOnSurface = Paint.Night,
)

private val Light = lightColorScheme(
    primary = Color(0xFF7A5200), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3A3), onPrimaryContainer = Color(0xFF2A1E00),
    secondary = Paint.Vermilion, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3A3), onSecondaryContainer = Color(0xFF2A1E00),
    tertiary = Color(0xFF14627A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEBF3), onTertiaryContainer = Color(0xFF06303C),
    background = Color(0xFFEEF3F5), onBackground = Paint.Night,
    surface = Color(0xFFEEF3F5), onSurface = Paint.Night,
    surfaceVariant = Color(0xFFDCE6EA), onSurfaceVariant = Color(0xFF3F5560),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F8F9),
    surfaceContainer = Color(0xFFE8EFF2), surfaceContainerHigh = Color(0xFFE1EAEE), surfaceContainerHighest = Color(0xFFD9E4E9),
    outline = Color(0xFF6F8791), outlineVariant = Color(0xFFC3D1D7),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

@Composable fun KiviTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
