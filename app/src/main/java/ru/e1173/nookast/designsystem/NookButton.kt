package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.designsystem.NookType.scaled

/** What a [NookButton] means (audit 3.1). */
enum class NookButtonVariant { Primary, Tonal, Outline, Destructive, Text }

/** M: 40 dp, beside other controls. L: 52 dp, the screen's main action (usually full width). */
enum class NookButtonSize { M, L }

/**
 * The app's one button (audit 3.1): always a capsule, its touch target at least 48 dp, scaled with
 * the interface (the car profile). Primary is solid [NookastColors.accentFill] with a coloured shadow
 * by day and a soft accent glow at night (the gradient is only for Play discs, which carry a glyph). The colours come from [LocalNookastColors],
 * so a card that provides the cover's colours (the episode card) gets a cover-coloured button.
 *
 * Buttons inside [ScaledAlertDialog] keep the alert's own text-button style.
 */
@Composable
fun NookButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NookButtonVariant = NookButtonVariant.Primary,
    size: NookButtonSize = NookButtonSize.M,
    enabled: Boolean = true,
    scale: Float = 1f,
    contentPadding: PaddingValues? = null,
    /** False for a finished state that is not a control (e.g. «Subscribed»): full colour, not clickable. */
    dimWhenDisabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalNookastColors.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val pressed by animateFloatAsState(if (isPressed && enabled && !reduced) PRESSED_SCALE else 1f, NookMotion.press(), label = "nookButtonPress")
    val height = (if (size == NookButtonSize.L) 52.dp else 40.dp) * scale
    val error = MaterialTheme.colorScheme.error
    val fill: Brush? = when (variant) {
        NookButtonVariant.Primary -> SolidColor(colors.accentFill)
        NookButtonVariant.Tonal -> SolidColor(colors.tintContainer)
        NookButtonVariant.Destructive -> SolidColor(error.copy(alpha = 0.10f))
        NookButtonVariant.Outline, NookButtonVariant.Text -> null
    }
    val contentColor = when (variant) {
        NookButtonVariant.Primary -> colors.onAccent
        NookButtonVariant.Destructive -> error
        NookButtonVariant.Tonal, NookButtonVariant.Outline, NookButtonVariant.Text -> colors.accentText
    }
    val horizontal = (if (variant == NookButtonVariant.Text) 12.dp else if (size == NookButtonSize.L) 24.dp else 18.dp) * scale
    val glows = enabled && variant == NookButtonVariant.Primary
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = height)
            .scale(pressed)
            .alpha(if (enabled || !dimWhenDisabled) 1f else DISABLED_ALPHA)
            .drawBehind {
                // Night: the accent's glow, .35 blur 16 (audit 3.1); dark themes have no drop shadow.
                if (glows && colors.isDark) drawCssShadow(colors.accent.copy(alpha = 0.35f), 16.dp, this.size.height / 2f)
            }
            .shadow(
                if (glows && !colors.isDark) 8.dp * scale else 0.dp,
                NookShapes.full,
                ambientColor = colors.accentFill.copy(alpha = 0.40f),
                spotColor = colors.accentFill.copy(alpha = 0.40f),
            )
            .clip(NookShapes.full)
            .focusRing(NookShapes.full, if (variant == NookButtonVariant.Primary) colors.onAccent else colors.accent)
            .then(if (fill != null) Modifier.drawBehind { drawRect(fill) } else Modifier)
            .then(
                when (variant) {
                    NookButtonVariant.Outline -> Modifier.border(1.dp, colors.cardOutline, NookShapes.full)
                    NookButtonVariant.Destructive -> Modifier.border(1.dp, error.copy(alpha = 0.45f), NookShapes.full)
                    else -> Modifier
                },
            )
            .clickable(interaction, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding ?: PaddingValues(horizontal = horizontal, vertical = 8.dp * scale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            androidx.compose.material3.LocalTextStyle provides (if (size == NookButtonSize.L) NookType.buttonLarge else NookType.button).scaled(scale).copy(color = contentColor),
        ) {
            content()
        }
    }
}

/** The common case: a label, optionally with a leading [icon] or a spinner while [loading]. */
@Composable
fun NookButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NookButtonVariant = NookButtonVariant.Primary,
    size: NookButtonSize = NookButtonSize.M,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
    scale: Float = 1f,
) {
    NookButton(onClick, modifier, variant, size, enabled && !loading, scale) {
        val glyph = (if (size == NookButtonSize.L) 18.dp else 16.dp) * scale
        when {
            loading -> CircularProgressIndicator(Modifier.size(glyph), strokeWidth = 2.dp, color = LocalContentColor.current)
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(glyph))
        }
        if (loading || icon != null) Spacer(Modifier.width(8.dp * scale))
        Text(
            text,
            style = (if (size == NookButtonSize.L) NookType.buttonLarge else NookType.button).scaled(scale),
            color = LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val PRESSED_SCALE = 0.96f
private const val DISABLED_ALPHA = 0.38f

/** The contrast colour of text over a filled button, for callers that draw their own glyphs. */
@Composable
fun nookButtonContentColor(variant: NookButtonVariant): Color {
    val colors = LocalNookastColors.current
    return when (variant) {
        NookButtonVariant.Primary -> colors.onAccent
        NookButtonVariant.Destructive -> MaterialTheme.colorScheme.error
        else -> colors.accentText
    }
}
