package ru.e1173.nookast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.e1173.nookast.R
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookButton
import ru.e1173.nookast.designsystem.NookButtonVariant
import ru.e1173.nookast.designsystem.NookShapes
import ru.e1173.nookast.designsystem.NookSlider
import ru.e1173.nookast.designsystem.NookSwitch
import ru.e1173.nookast.designsystem.NookType
import ru.e1173.nookast.designsystem.NookType.scaled
import ru.e1173.nookast.designsystem.floatingShadow
import ru.e1173.nookast.designsystem.nookCard
import ru.e1173.nookast.designsystem.rememberNookHaptics
import ru.e1173.nookast.player.SoundParameters
import ru.e1173.nookast.player.SoundSettingsModel

/**
 * The sound popover on a long press of Play (audio rework З13, DEC-060), built only from the design
 * system: «This podcast» (volume, mono) and «All podcasts» (even loudness, clarity, depth, shorter
 * pauses, the built-in speaker on that route), then «Compare» held for the original sound.
 * One scrolling column on a phone, two columns on a wide screen.
 */
@Composable
internal fun SoundPopover(
    largeControls: Boolean,
    wide: Boolean,
    maxWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val model: SoundSettingsModel = viewModel()
    val ui by model.ui.collectAsStateWithLifecycle()
    val colors = LocalNookastColors.current
    val scale = if (largeControls) 4f / 3f else 1f
    val radius = if (largeControls) NookShapes.xlRadius else NookShapes.lRadius
    Column(
        modifier
            .width(minOf(if (wide) 640.dp * scale else 320.dp * scale, maxWidth))
            .heightIn(max = maxHeight)
            .floatingShadow(radius)
            .nookCard(radius = radius)
            // Taps inside stay inside: the scrim behind closes the popover.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp * scale, vertical = 18.dp * scale),
        verticalArrangement = Arrangement.spacedBy(14.dp * scale),
    ) {
        if (!ui.session.supported) {
            Text(stringResource(R.string.sound_unavailable), style = NookType.bodySmall.scaled(scale), color = colors.textSecondary)
        }
        val thisPodcast: @Composable ColumnScope.() -> Unit = {
            SectionHeader(stringResource(R.string.sound_this_podcast), ui.session.podcastTitle, scale)
            DecibelSlider(stringResource(R.string.sound_volume), ui.podcast.volumeDb, SoundParameters.MAX_VOLUME_DB, scale, model::setVolume)
            SwitchRow(stringResource(R.string.sound_mono), null, ui.podcast.mono, scale, model::setMono)
        }
        val allPodcasts: @Composable ColumnScope.() -> Unit = {
            SectionHeader(stringResource(R.string.sound_all_podcasts), null, scale)
            SwitchRow(stringResource(R.string.sound_even_loudness), stringResource(R.string.sound_even_loudness_hint), ui.global.evenLoudness, scale) { on ->
                model.changeGlobal { it.copy(evenLoudness = on) }
            }
            StepSlider(stringResource(R.string.sound_speech_clarity), ui.global.clarity, scale) { step ->
                model.changeGlobal { it.copy(clarity = step) }
            }
            StepSlider(stringResource(R.string.sound_voice_depth), ui.global.depth, scale) { step ->
                model.changeGlobal { it.copy(depth = step) }
            }
            // The voice light rests while pauses are shortened: said where it happens (audit of З, R7).
            val voiceRests = if (ru.e1173.nookast.preferences.VoiceLightSwitch.on) stringResource(R.string.sound_voice_light_rests) else null
            SwitchRow(stringResource(R.string.sound_shorten_pauses), voiceRests, ui.global.shortenPauses, scale) { on ->
                model.changeGlobal { it.copy(shortenPauses = on) }
            }
            if (ui.session.speakerRoute) {
                DecibelSlider(stringResource(R.string.sound_speaker_trim), ui.global.speakerTrimDb, SoundParameters.MAX_SPEAKER_TRIM_DB, scale) { db ->
                    model.changeGlobal { it.copy(speakerTrimDb = db) }
                }
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp * scale), content = thisPodcast)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp * scale), content = allPodcasts)
            }
        } else {
            thisPodcast()
            Spacer(Modifier.width(1.dp))
            allPodcasts()
        }
        CompareButton(scale, model::compare)
        if (ui.session.limiterHolding) {
            Text(stringResource(R.string.sound_limiter_holding), style = NookType.bodySmall.scaled(scale), color = colors.textSecondary)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?, scale: Float) {
    val colors = LocalNookastColors.current
    Column {
        Text(title, style = NookType.label.scaled(scale), color = colors.textSecondary, maxLines = 1)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = NookType.rowTitle.scaled(scale), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String?, checked: Boolean, scale: Float, onChange: (Boolean) -> Unit) {
    val colors = LocalNookastColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = NookType.rowTitle.scaled(scale), color = colors.textPrimary)
            if (hint != null) Text(hint, style = NookType.bodySmall.scaled(scale), color = colors.textSecondary)
        }
        NookSwitch(checked, onChange, Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun DecibelSlider(label: String, valueDb: Int, maxDb: Int, scale: Float, onChange: (Int) -> Unit) {
    val text = if (valueDb == 0) stringResource(R.string.sound_off) else stringResource(R.string.sound_plus_decibels, valueDb)
    LabelledSlider(label, text, valueDb, maxDb, scale, onChange)
}

@Composable
private fun StepSlider(label: String, step: Int, scale: Float, onChange: (Int) -> Unit) {
    val text = if (step == 0) stringResource(R.string.sound_off) else step.toString()
    LabelledSlider(label, text, step, SoundParameters.MAX_STEP, scale, onChange)
}

@Composable
private fun LabelledSlider(label: String, valueText: String, value: Int, max: Int, scale: Float, onChange: (Int) -> Unit) {
    val colors = LocalNookastColors.current
    // The thumb follows the finger; the stored value changes by whole steps.
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = NookType.rowTitle.scaled(scale), color = colors.textPrimary)
            Text(valueText, style = NookType.bodySmall.scaled(scale), color = colors.textSecondary)
        }
        NookSlider(
            value = if (dragging.isNaN()) value.toFloat() else dragging,
            onValueChange = { next ->
                dragging = next
                val whole = Math.round(next).coerceIn(0, max)
                if (whole != value) onChange(whole)
            },
            valueRange = 0f..max.toFloat(),
            onValueChangeFinished = { dragging = Float.NaN },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = valueText
            },
        )
    }
}

@Composable
private fun CompareButton(scale: Float, onCompare: (Boolean) -> Unit) {
    val colors = LocalNookastColors.current
    val haptics = rememberNookHaptics()
    val description = stringResource(R.string.sound_compare_hint)
    val onLabel = stringResource(R.string.sound_compare_on)
    // TalkBack cannot hold a button: its action switches the original sound on until used again
    // (review of З). Leaving the popover always returns to the processed sound.
    var latched by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (latched) onCompare(false) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        NookButton(
            onClick = {},
            variant = NookButtonVariant.Outline,
            scale = scale,
            modifier = Modifier
                .semantics {
                    if (latched) stateDescription = onLabel
                    customActions = listOf(
                        androidx.compose.ui.semantics.CustomAccessibilityAction(description) {
                            latched = !latched
                            onCompare(latched)
                            true
                        },
                    )
                }
                .pointerInput(Unit) {
                    // Held: the original sound (З11); let go: the processed one again.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        haptics.tick()
                        onCompare(true)
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            onCompare(false)
                        }
                    }
                },
        ) {
            Text(stringResource(R.string.sound_compare))
        }
        Text(description, style = NookType.meta.scaled(scale), color = colors.textSecondary)
    }
}
