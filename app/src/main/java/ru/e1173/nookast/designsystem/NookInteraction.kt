package ru.e1173.nookast.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

/**
 * The app's press feedback (design audit 4.4): instead of Material's ripple, a quiet wash of the
 * text colour, 6 % by day and 8 % at night, fading in and out over Fast. Installed as
 * LocalIndication by the theme, so every clickable without its own indication gets it.
 */
@Immutable
class NookIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressWashNode(interactionSource, color)
    override fun equals(other: Any?) = other is NookIndication && other.color == color
    override fun hashCode() = color.hashCode()

    private class PressWashNode(private val source: InteractionSource, private val color: Color) :
        Modifier.Node(),
        DrawModifierNode {
        private val strength = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                var presses = 0
                var focuses = 0
                var hovers = 0
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> presses++
                        is PressInteraction.Release, is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                        // Keyboard, D-pad and pointer hover keep a steady, lighter wash (review of audit 4).
                        is androidx.compose.foundation.interaction.FocusInteraction.Focus -> focuses++
                        is androidx.compose.foundation.interaction.FocusInteraction.Unfocus -> focuses = (focuses - 1).coerceAtLeast(0)
                        is androidx.compose.foundation.interaction.HoverInteraction.Enter -> hovers++
                        is androidx.compose.foundation.interaction.HoverInteraction.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
                    }
                    val target = when {
                        presses > 0 -> 1f
                        focuses > 0 -> FOCUS_WASH
                        hovers > 0 -> HOVER_WASH
                        else -> 0f
                    }
                    if (strength.targetValue != target) {
                        coroutineScope.launch {
                            strength.animateTo(target, NookMotion.fast())
                        }
                    }
                }
            }
            coroutineScope.launch {
                androidx.compose.runtime.snapshotFlow { strength.value }.collect { invalidateDraw() }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val k = strength.value
            if (k > 0f) drawRect(color.copy(alpha = color.alpha * k))
        }
    }
}

private const val FOCUS_WASH = 0.75f
private const val HOVER_WASH = 0.5f

/** The indication of the current theme. */
@Composable
fun rememberNookIndication(): NookIndication {
    val colors = LocalNookastColors.current
    val wash = colors.textPrimary.copy(alpha = if (colors.isDark) 0.08f else 0.06f)
    return remember(wash) { NookIndication(wash) }
}

/**
 * Shrinks while pressed (design audit 4.4): discs 0.91, capsules 0.96, cards, rows and tiles 0.98.
 * Pass the same [interactionSource] as the clickable. Off with «remove animations».
 */
@Composable
fun Modifier.nookPressable(interactionSource: InteractionSource, scale: Float = NookPress.CARD): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val value by animateFloatAsState(if (pressed && !reduced) scale else 1f, NookMotion.press(), label = "nookPress")
    return graphicsLayer {
        scaleX = value
        scaleY = value
    }
}

object NookPress {
    const val DISC = 0.91f
    const val CAPSULE = 0.96f
    const val CARD = 0.98f
}

/**
 * Haptics of the app (design audit 4.4), only through View.performHapticFeedback so the system
 * setting is respected: tick (switches, slider steps, a week bar, snapping to a chapter, a tab),
 * confirm (subscribed, played, a download finished on screen), reject (adding failed), and the
 * queue swipe threshold.
 */
class NookHaptics(private val view: View) {
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun reject() = perform(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun threshold() = perform(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberNookHaptics(): NookHaptics {
    val view = LocalView.current
    return remember(view) { NookHaptics(view) }
}

/**
 * Confirm when a [pending] action ends in [success], reject when it ends without it (design audit
 * 4.4: subscribed, adding failed). Nothing on the first composition.
 */
@Composable
fun HapticOutcome(pending: Boolean, success: Boolean) {
    val haptics = rememberNookHaptics()
    val wasPending = remember { androidx.compose.runtime.mutableStateOf(pending) }
    androidx.compose.runtime.LaunchedEffect(pending, success) {
        if (wasPending.value && !pending) {
            if (success) haptics.confirm() else haptics.reject()
        }
        wasPending.value = pending
    }
}
