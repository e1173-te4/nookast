package ru.e1173.nookast.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** The app's switch (audit 3.5): the labelled-fill accent when on, the track colour when off. */
@Composable
fun NookSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalNookastColors.current
    val haptics = rememberNookHaptics()
    Switch(
        checked = checked,
        // A tick on every change (design audit 4.4).
        onCheckedChange = onCheckedChange?.let { change -> { on: Boolean -> haptics.tick(); change(on) } },
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accentFill,
            checkedBorderColor = colors.accentFill,
            // An off switch keeps a 3:1 outline on a white card (independent review of audit 3).
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.track,
            uncheckedBorderColor = colors.textSecondary,
        ),
    )
}

/**
 * The app's slider (audit 3.5): a 4 dp track in the theme's track colour with the accent fill and a
 * 14 dp accent thumb (the settings' approved slider, formerly ApprovedSliderTrack).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NookSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = LocalNookastColors.current
    val haptics = rememberNookHaptics()
    // A tick per twentieth of the range (design audit 4.4: the slider's step).
    val stepOf = { v: Float -> (((v - valueRange.start) / (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)) * SLIDER_TICKS).toInt() }
    Slider(
        value = value,
        onValueChange = { next ->
            if (stepOf(next) != stepOf(value)) haptics.tick()
            onValueChange(next)
        },
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        thumb = { Box(Modifier.size(14.dp).background(colors.accent, CircleShape)) },
        track = { NookSliderTrack(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NookSliderTrack(state: SliderState) {
    val colors = LocalNookastColors.current
    val span = state.valueRange.endInclusive - state.valueRange.start
    val fraction = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(4.dp).background(colors.track, CircleShape)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxSize().background(colors.accent, CircleShape))
    }
}

/** The app's text field (audit 3.5): outlined, radius m, the tokens' colours. */
@Composable
fun NookTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = LocalNookastColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = NookShapes.m,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.6f),
            focusedLabelColor = colors.accentText,
            cursorColor = colors.accent,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            unfocusedLabelColor = colors.textSecondary,
            focusedContainerColor = colors.surface.copy(alpha = 0f),
            unfocusedContainerColor = colors.surface.copy(alpha = 0f),
        ),
    )
}

private const val SLIDER_TICKS = 20f
