package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pulling the open player down (design audit 4.2): the player follows the finger and shrinks to
 * 0.92; let go past 0.35 of the way (half the screen height) or faster than 800 dp/s and it closes
 * with its usual exit from where it is, otherwise it springs back. Predictive back drives the same
 * [progress].
 */
@Stable
class PlayerDismissState(private val scope: CoroutineScope, private val onDismiss: () -> Unit) {
    val progress = Animatable(0f)

    /** Half the player's height in pixels: the distance of a full pull. */
    var travelPx by mutableFloatStateOf(1f)

    fun dragBy(deltaPx: Float) {
        scope.launch { progress.snapTo((progress.value + deltaPx / travelPx).coerceIn(0f, 1f)) }
    }

    fun release(velocityPxPerSecond: Float, density: Density) {
        val dpPerSecond = velocityPxPerSecond / density.density
        scope.launch {
            if (progress.value > DISMISS_PROGRESS || dpPerSecond > DISMISS_VELOCITY_DP) {
                onDismiss()
            } else {
                progress.animateTo(0f, NookMotion.settle())
            }
        }
    }

    /** Predictive back: the gesture's own progress, then close or return. */
    suspend fun followBack(fraction: Float) = progress.snapTo(fraction.coerceIn(0f, 1f))
    suspend fun cancelBack() = progress.animateTo(0f, NookMotion.settle())

    /** The player is open again: start from rest. */
    suspend fun reset() = progress.snapTo(0f)

    private companion object {
        const val DISMISS_PROGRESS = 0.35f
        const val DISMISS_VELOCITY_DP = 800f
    }
}

val LocalPlayerDismiss = staticCompositionLocalOf<PlayerDismissState?> { null }

/** The player follows the pull: it moves down and shrinks to 0.92 (on the player's root). */
fun Modifier.playerDismissLayer(state: PlayerDismissState): Modifier = graphicsLayer {
    state.travelPx = size.height / 2f
    val p = state.progress.value
    translationY = p * size.height / 2f
    val s = 1f - DISMISS_SHRINK * p
    scaleX = s
    scaleY = s
}

/** Where the pull may start: the header, the cover and the title (not the timeline or lists). */
@Composable
fun Modifier.playerDismissDrag(): Modifier {
    val state = LocalPlayerDismiss.current ?: return this
    val density = LocalDensity.current
    return draggable(
        state = rememberDraggableState { delta -> state.dragBy(delta) },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> state.release(velocity, density) },
    )
}

private const val DISMISS_SHRINK = 0.08f

/**
 * The pull inside the player's scrolling pane (independent review of audit 4): the pane scrolls as
 * before; only what is left of a downward drag at its top pulls the player, and an upward drag
 * first gives back the pull. A fling ends the pull like letting go.
 */
@Composable
fun rememberPlayerDismissScroll(): androidx.compose.ui.input.nestedscroll.NestedScrollConnection? {
    val state = LocalPlayerDismiss.current ?: return null
    val density = LocalDensity.current
    return androidx.compose.runtime.remember(state, density) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return androidx.compose.ui.geometry.Offset.Zero
                if (available.y < 0f && state.progress.value > 0f) {
                    state.dragBy(available.y)
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || available.y <= 0f) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                state.dragBy(available.y)
                return androidx.compose.ui.geometry.Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (state.progress.value <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                state.release(available.y, density)
                return available
            }
        }
    }
}
