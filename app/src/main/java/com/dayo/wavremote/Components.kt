package com.dayo.wavremote

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ================================================================ motion

/** Panels fade in and rise a few dp the first time they are shown. */
@Composable
fun Modifier.appear(): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(280)) }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 12.dp.toPx()
    }
}

// ================================================================ grouped lists

/** Sentence-case label above a group. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** One rounded container holding related rows — rows are separated by inset hairlines, not split into cards. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (Wav.colors.outlined) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier.appear().fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
fun GroupDivider(inset: Dp = 72.dp) {
    HorizontalDivider(Modifier.padding(start = inset), color = Wav.colors.subtle)
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> { leading(); Spacer(Modifier.width(16.dp)) }
            icon != null -> { IconBadge(icon, iconTint); Spacer(Modifier.width(16.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A small rounded status tag, e.g. "Blocked", "Limited". */
@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Four rising bars for a 0–100% signal. */
@Composable
fun SignalBars(percent: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val lit = when {
        percent >= 75 -> 4
        percent >= 50 -> 3
        percent >= 25 -> 2
        percent > 0 -> 1
        else -> 0
    }
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(width = 18.dp, height = 14.dp)) {
        val gap = size.width * 0.1f
        val w = (size.width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = size.height * (0.35f + 0.65f * (i + 1) / 4f)
            drawRoundRect(
                color = if (i < lit) color else off,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 3, w / 3),
            )
        }
    }
}

// ================================================================ signal map (home hero)

private fun animationsOff(ctx: android.content.Context): Boolean =
    runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        .getOrDefault(false)

/**
 * The router at the centre, mesh satellites on the inner ring, devices on the outer ring.
 * While online, a single ring slowly radiates out — the app's one ambient motion.
 */
@Composable
fun SignalMap(
    online: Boolean,
    satellites: Int,
    devices: Int,
    modifier: Modifier = Modifier,
) {
    val wav = Wav.colors
    val main = if (online) wav.signal else wav.fault
    val ctx = LocalContext.current
    val still = remember { animationsOff(ctx) }
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val ringBase = MaterialTheme.colorScheme.outlineVariant
    val deviceDot = MaterialTheme.colorScheme.onSurfaceVariant
    val satFill = MaterialTheme.colorScheme.surfaceContainer

    Box(modifier.fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val r = min(size.width / 1.35f, size.height) / 2f * 0.92f
            val rings = listOf(0.30f, 0.62f, 0.94f)
            rings.forEach { f -> drawCircle(ringBase, radius = r * f, center = c, style = Stroke(1.2.dp.toPx())) }

            if (online && !still) {
                val pr = r * (0.22f + 0.78f * pulse)
                drawCircle(main.copy(alpha = (1f - pulse) * 0.45f), radius = pr, center = c, style = Stroke(2.dp.toPx()))
            }

            // Devices: small dots around the outer ring (spread evenly, capped for legibility).
            val nDev = devices.coerceAtMost(36)
            for (i in 0 until nDev) {
                val a = (2 * PI * i / nDev.coerceAtLeast(1)) - PI / 2 + 0.3
                val p = Offset(c.x + (r * rings[2] * cos(a)).toFloat(), c.y + (r * rings[2] * sin(a)).toFloat())
                drawCircle(deviceDot.copy(alpha = 0.55f), radius = 3.2.dp.toPx(), center = p)
            }
            // Satellites: hollow nodes on the middle ring, with a link line to the router.
            for (i in 0 until satellites) {
                val a = (2 * PI * i / satellites) - PI / 2 - 0.6
                val p = Offset(c.x + (r * rings[1] * cos(a)).toFloat(), c.y + (r * rings[1] * sin(a)).toFloat())
                drawLine(main.copy(alpha = 0.35f), c, p, strokeWidth = 1.5.dp.toPx())
                drawCircle(satFill, radius = 9.dp.toPx(), center = p)
                drawCircle(main, radius = 9.dp.toPx(), center = p, style = Stroke(2.5.dp.toPx()))
            }
            // Router.
            drawCircle(main, radius = r * 0.17f, center = c)
        }
        Icon(
            Icons.Rounded.Router,
            contentDescription = null,
            tint = if (online) MaterialTheme.colorScheme.onPrimary else Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

// ================================================================ small layout helpers

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
