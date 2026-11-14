package com.dayo.wavremote

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
    val signal: Color,      // online / healthy
    val activity: Color,    // warnings, updates, upload traffic
    val fault: Color,       // offline / blocked
    val ring: Color,
    val subtle: Color,      // row dividers
    val outlined: Boolean,  // hairline borders on panels
    val down: Color,        // download traffic
    val up: Color,          // upload traffic
)

/* "Pro dark": cool slate panels, 1 px borders, blue accent, green/amber/red status. Always dark. */
private val ProWav = WavColors(
    signal = Color(0xFF2FBF71),
    activity = Color(0xFFF2A93B),
    fault = Color(0xFFF2555A),
    ring = Color(0xFF3D8BFF),
    subtle = Color(0xFF232A33),
    outlined = true,
    down = Color(0xFF3D8BFF),
    up = Color(0xFF9B7BFF),
)

val LocalWav = staticCompositionLocalOf { ProWav }

private val ProScheme = darkColorScheme(
    primary = Color(0xFF3D8BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16305A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF8EA0B5),
    onSecondary = Color(0xFF0E141B),
    secondaryContainer = Color(0xFF1E2731),
    onSecondaryContainer = Color(0xFFD2DCE7),
    tertiary = Color(0xFFF2A93B),
    tertiaryContainer = Color(0xFF3A2A0D),
    onTertiaryContainer = Color(0xFFFFDDA6),
    error = Color(0xFFF2555A),
    errorContainer = Color(0xFF3B1517),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1217),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF0E1217),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1B2129),
    onSurfaceVariant = Color(0xFF8A94A3),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF12171D),
    surfaceContainer = Color(0xFF151A21),
    surfaceContainerHigh = Color(0xFF1B2129),
    surfaceContainerHighest = Color(0xFF242B35),
    outline = Color(0xFF4A5462),
    outlineVariant = Color(0xFF262D37),
    inverseSurface = Color(0xFFE6EAF0),
    inverseOnSurface = Color(0xFF151A21),
    inversePrimary = Color(0xFF1F5FCC),
    scrim = Color.Black,
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Schibsted, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    letterSpacing = tracking.sp,
)

/** Dense, data-first scale. */
private val WavType = Typography(
    displayLarge = style(40, 44, FontWeight.ExtraBold, -1.0),
    displayMedium = style(32, 36, FontWeight.ExtraBold, -0.8),
    displaySmall = style(26, 30, FontWeight.SemiBold, -0.5),
    headlineLarge = style(24, 28, FontWeight.SemiBold, -0.4),
    headlineMedium = style(20, 26, FontWeight.SemiBold, -0.2),
    headlineSmall = style(18, 24, FontWeight.SemiBold, -0.1),
    titleLarge = style(17, 22, FontWeight.SemiBold),
    titleMedium = style(15, 20, FontWeight.SemiBold),
    titleSmall = style(13, 18, FontWeight.SemiBold),
    bodyLarge = style(15, 21, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(13, 18, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium, 0.2),
)

/** Small, square-ish radii throughout. */
private val WavShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun WavTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalWav provides ProWav) {
        MaterialTheme(colorScheme = ProScheme, typography = WavType, shapes = WavShapes, content = content)
    }
}

object Wav {
    val colors: WavColors @Composable get() = LocalWav.current
}
