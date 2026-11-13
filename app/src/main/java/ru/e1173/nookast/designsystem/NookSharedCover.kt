package ru.e1173.nookast.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp

/**
 * The cover shared by the mini-player and the player (design audit 4.1): the root's shared
 * transition scope and the visibility scope of the surface the cover sits on.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class NookCoverTransition(val shared: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

val LocalCoverTransition = compositionLocalOf<NookCoverTransition?> { null }

/**
 * Marks the playing episode's cover: between the mini-player and the player it flies on the
 * spatial spring, its corner radius going from [otherRadius] (the cover it replaces) to [radius].
 * Without a transition in scope, or with «remove animations», it only clips to [radius].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(radius: Dp, otherRadius: Dp): Modifier {
    val transition = LocalCoverTransition.current
    if (transition == null || rememberReducedMotion()) return clip(RoundedCornerShape(radius))
    val animated by transition.visibility.transition.animateDp(
        transitionSpec = { NookMotion.spatial() },
        label = "coverRadius",
    ) { state -> if (state == EnterExitState.Visible) radius else otherRadius }
    return with(transition.shared) {
        this@sharedCover
            .sharedElement(
                rememberSharedContentState(COVER_KEY),
                transition.visibility,
                boundsTransform = CoverBounds,
            )
            .clip(RoundedCornerShape(animated))
    }
}

private const val COVER_KEY = "nookast-playing-cover"

@OptIn(ExperimentalSharedTransitionApi::class)
private val CoverBounds = BoundsTransform { _, _ -> NookMotion.spatial() }
