package com.dayo.wavremote

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/*
 * Pro-dark control set. These replace Material 3's pill buttons, thumb switches, chips and
 * floating-label fields with flat, square-ish, bordered controls. Names and parameters match the
 * Material components the screens already call, so screens pick these up without changes.
 */

private val CTRL = RoundedCornerShape(6.dp)

@Composable
private fun BaseButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    bg: Color,
    fg: Color,
    border: Color?,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(CTRL)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, CTRL) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = fg)) { content() }
        }
    }
}

/** Primary action: solid accent. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    content: @Composable RowScope.() -> Unit,
) = BaseButton(onClick, modifier, enabled, MaterialTheme.colorScheme.primary, Color.White, null, content)

/** Secondary action: panel with hairline border. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = BaseButton(onClick, modifier, enabled, MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.outlineVariant, content)

/** Accent-tinted secondary action. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = BaseButton(onClick, modifier, enabled, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), content)

/** Text-only action (dialogs, row trailing actions). */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .clip(CTRL)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = MaterialTheme.colorScheme.primary
        CompositionLocalProvider(LocalContentColor provides c) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = c)) { content() }
        }
    }
}

/** Compact square-ish toggle: 36×20 track, sliding 14 dp knob. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "track",
    )
    val x by animateDpAsState(if (checked) 18.dp else 3.dp, label = "knob")
    Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(track)
            .border(1.dp, if (checked) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled && onCheckedChange != null, role = Role.Switch) { onCheckedChange?.invoke(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = x).size(14.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
    }
}

@Composable
fun Checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Box(
        Modifier
            .padding(10.dp)
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .clickable(enabled = onCheckedChange != null, role = Role.Checkbox) { onCheckedChange?.invoke(!checked) }
            .semantics { stateDescription = if (checked) "Ticked" else "Not ticked" },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/** Segment-style filter chip: square-ish, accent outline when selected. */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .clip(CTRL)
            .background(if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, CTRL)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val c = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        CompositionLocalProvider(LocalContentColor provides c) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = c)) { label() }
        }
    }
}

@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .clip(CTRL)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CTRL)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val c = MaterialTheme.colorScheme.onSurface
        CompositionLocalProvider(LocalContentColor provides c) {
            leadingIcon?.invoke()
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = c)) { label() }
        }
    }
}

/** Text field with its label above (no floating label), flat fill, 6 dp corners. */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = if (isError) cs.error else cs.onSurfaceVariant)) { label() }
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = CTRL,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = cs.surfaceContainerLowest,
                unfocusedContainerColor = cs.surfaceContainerLowest,
                disabledContainerColor = cs.surfaceContainerLowest,
                focusedBorderColor = cs.primary,
                unfocusedBorderColor = cs.outlineVariant,
                errorBorderColor = cs.error,
            ),
        )
    }
}

/** Flat dialog: panel colour, small corners, hairline border. */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    )
}
