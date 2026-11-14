package com.dayo.wavremote

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * WavRemote visual identity — "status light".
 * Colours come from the router itself: the teal-green "all good" LED, the amber "activity" LED,
 * on a cool grey-green base. One typeface (Schibsted Grotesk) with a clear weight ladder.
 */

val Schibsted = FontFamily(
    Font(R.font.schibsted_regular, FontWeight.Normal),
    Font(R.font.schibsted_medium, FontWeight.Medium),
    Font(R.font.schibsted_semibold, FontWeight.SemiBold),
    Font(R.font.schibsted_extrabold, FontWeight.ExtraBold),
)

/** Colours outside Material's roles. */
@Immutable
data class WavColors(
    val signal: Color,      // healthy / online
    val activity: Color,    // amber: traffic, warnings, updates
    val fault: Color,       // offline / blocked
    val ring: Color,        // hero rings
    val subtle: Color,      // grouped-list row dividers
    val outlined: Boolean,  // dark mode: hairline borders on cards instead of contrast
)

private val LightWav = WavColors(
    signal = Color(0xFF13785F),
    activity = Color(0xFFC77F00),
    fault = Color(0xFFB83A32),
    ring = Color(0xFF13785F),
    subtle = Color(0xFFDCE5E1),
    outlined = false,
)
private val DarkWav = WavColors(
    signal = Color(0xFF55CFAF),
    activity = Color(0xFFF0B23C),
    fault = Color(0xFFFF8A80),
    ring = Color(0xFF55CFAF),
    subtle = Color(0xFF24332E),
    outlined = true,
)

val LocalWav = staticCompositionLocalOf { LightWav }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF13785F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE1),
    onPrimaryContainer = Color(0xFF00261C),
    secondary = Color(0xFF4A5F58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7E3),
    onSecondaryContainer = Color(0xFF132320),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFE2B3),
    onTertiaryContainer = Color(0xFF2B1800),
    error = Color(0xFFB83A32),
    errorContainer = Color(0xFFFCDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEEF3F1),
    onBackground = Color(0xFF13201C),
    surface = Color(0xFFEEF3F1),
    onSurface = Color(0xFF13201C),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF52625C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAF9),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE6EDEA),
    surfaceContainerHighest = Color(0xFFDDE6E2),
    outline = Color(0xFF7D8D87),
    outlineVariant = Color(0xFFC9D5D0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF55CFAF),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0E4F3F),
    onPrimaryContainer = Color(0xFFBDF0DF),
    secondary = Color(0xFFB2CBC3),
    onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF26372F),
    onSecondaryContainer = Color(0xFFCDE6DD),
    tertiary = Color(0xFFF0B23C),
    tertiaryContainer = Color(0xFF4A3300),
    onTertiaryContainer = Color(0xFFFFDDA6),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1712),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0C1412),
    onBackground = Color(0xFFE2ECE8),
    surface = Color(0xFF0C1412),
    onSurface = Color(0xFFE2ECE8),
    surfaceVariant = Color(0xFF24332E),
    onSurfaceVariant = Color(0xFFA3B5AE),
    surfaceContainerLowest = Color(0xFF09100E),
    surfaceContainerLow = Color(0xFF111B18),
    surfaceContainer = Color(0xFF15211D),
    surfaceContainerHigh = Color(0xFF1C2925),
    surfaceContainerHighest = Color(0xFF24332E),
    outline = Color(0xFF6E827A),
    outlineVariant = Color(0xFF2E3F39),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Schibsted, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    letterSpacing = tracking.sp,
)

/** A modular scale (~1.25) with tight display tracking and relaxed body leading. */
private val WavType = Typography(
    displayLarge = style(52, 56, FontWeight.ExtraBold, -1.5),
    displayMedium = style(42, 46, FontWeight.ExtraBold, -1.0),
    displaySmall = style(34, 38, FontWeight.ExtraBold, -0.8),
    headlineLarge = style(30, 36, FontWeight.ExtraBold, -0.6),
    headlineMedium = style(26, 32, FontWeight.ExtraBold, -0.4),
    headlineSmall = style(22, 28, FontWeight.SemiBold, -0.2),
    titleLarge = style(21, 28, FontWeight.SemiBold, -0.2),
    titleMedium = style(17, 24, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(15, 22, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.SemiBold),
    labelMedium = style(13, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium, 0.2),
)

/** Radius follows hierarchy: hero 32, groups 22, controls 14, chips full. */
private val WavShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WavTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalWav provides if (dark) DarkWav else LightWav) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = WavType,
            shapes = WavShapes,
            content = content,
        )
    }
}

object Wav {
    val colors: WavColors @Composable get() = LocalWav.current
}
