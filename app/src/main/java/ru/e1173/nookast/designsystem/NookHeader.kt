package ru.e1173.nookast.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.designsystem.NookType.scaled

/**
 * The one icon-only button of the app (audit 3.3, DEC-059): a round [discSize] disc, 38–40 dp
 * (× 4/3 with large controls), floating over the glass (level 2) unless [elevated] is off, in a
 * transparent touch zone at least 48 dp; the glyph is [ICON_SHARE] of the diameter in
 * [NookastColors.accentText]; without an icon or content it draws «⋯». A [fill] of
 * [Color.Transparent] gives a bare glyph with the same zone and press. The zone is not clipped, so
 * the disc's shadow has room (independent review of audit 1–2: a clipped parent cut it to a
 * crescent); pressing shrinks the disc (design audit 4.4).
 */
@Composable
fun NookHeaderDisc(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    discSize: Dp = 38.dp,
    fill: Color = LocalNookastColors.current.tintContainer,
    tint: Color = LocalNookastColors.current.accentText,
    iconSize: Dp = discSize * ICON_SHARE,
    elevated: Boolean = true,
    enabled: Boolean = true,
    /**
     * The laid-out size, at least the disc. By default a 48 dp+ zone around it; beside other
     * controls pass their height so the row does not grow — Compose still widens the touch
     * target of a smaller clickable to 48 dp.
     */
    zone: Dp = maxOf(MIN_TOUCH, discSize + 10.dp),
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(maxOf(zone, discSize))
            .focusRing(CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(discSize)
                // Discs shrink to 0.91 when pressed (design audit 4.4).
                .nookPressable(interaction, NookPress.DISC)
                .then(if (elevated && fill != Color.Transparent) Modifier.nookElevation(NookElevation.Level2, discSize) else Modifier)
                .background(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                content != null -> content()
                icon != null -> Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
                // No icon: «⋯», the more-actions disc.
                else -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { Box(Modifier.size(4.dp).background(tint, CircleShape)) }
                }
            }
        }
    }
}

/**
 * A screen's header (audit 3.3): Back on the left, the title centred in the bar title role once
 * [showTitle], actions on the right. Lives in the screen's top glass; the bar itself draws nothing.
 */
@Composable
fun NookHeaderBar(
    title: String,
    showTitle: Boolean,
    onBack: (() -> Unit)?,
    backLabel: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    actions: (@Composable () -> Unit)? = null,
    /**
     * 0..1 as the large title leaves under the bar (design audit 4.6), read while drawing so a scroll
     * does not recompose the bar; [showTitle] when not given.
     */
    titleProgress: (() -> Float)? = null,
) {
    val colors = LocalNookastColors.current
    Box(modifier.fillMaxSize().padding(horizontal = 7.dp)) {
        if (onBack != null) {
            NookHeaderDisc(NookIcons.ChevronLeft, backLabel, onBack, Modifier.align(Alignment.CenterStart))
        }
        if (titleProgress != null || showTitle) {
            Text(
                title,
                style = NookType.barTitle.scaled(scale),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
                    .graphicsLayer {
                        val p = titleProgress?.invoke() ?: 1f
                        alpha = p
                        translationY = TITLE_RISE.toPx() * (1f - p)
                    },
            )
        }
        if (actions != null) {
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

private val TITLE_RISE = 8.dp

/** The glyph of an icon-only disc: just under half its diameter (DEC-059). */
const val ICON_SHARE = 0.48f
private val MIN_TOUCH = 48.dp

/**
 * How far the large title has gone under a bar (design audit 4.6): 0 until it is [spanPx] from
 * leaving at [leavesAtPx] of scroll, 1 once gone.
 */

/** The large title as it leaves under the bar: it shrinks to 0.92 and fades (4.6). */
fun Modifier.leavingTitle(progress: () -> Float): Modifier = graphicsLayer {
    val p = progress()
    alpha = 1f - p
    val s = 1f - LEAVING_SHRINK * p
    scaleX = s
    scaleY = s
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
}

private const val LEAVING_SHRINK = 0.08f

/**
 * Where the bar ends and where the screen's large title ends, both in the root (design audit 4.6):
 * the bar's title appears over the last [TITLE_SPAN] of the large title going under it.
 */
@androidx.compose.runtime.Stable
class HeaderTitleTracker {
    internal var barBottom by androidx.compose.runtime.mutableFloatStateOf(0f)
    internal var titleBottom by androidx.compose.runtime.mutableFloatStateOf(Float.MAX_VALUE)

    /** False until the large title has been measured, e.g. on a list restored already scrolled. */
    val measured: Boolean get() = titleBottom != Float.MAX_VALUE

    fun progress(density: androidx.compose.ui.unit.Density): Float {
        val span = with(density) { TITLE_SPAN.toPx() }
        return ((barBottom + span - titleBottom) / span).coerceIn(0f, 1f)
    }
}

@Composable
fun rememberHeaderTitleTracker(): HeaderTitleTracker = remember { HeaderTitleTracker() }

/** On the bar (or its glass) whose bottom the large title goes under. */
fun Modifier.trackHeaderBar(tracker: HeaderTitleTracker): Modifier =
    then(Modifier.onGloballyPositioned { tracker.barBottom = it.boundsInRoot().bottom })

/** On the screen's large title. */
fun Modifier.trackLargeTitle(tracker: HeaderTitleTracker): Modifier =
    then(Modifier.onGloballyPositioned { tracker.titleBottom = it.boundsInRoot().bottom })

private val TITLE_SPAN = 24.dp
