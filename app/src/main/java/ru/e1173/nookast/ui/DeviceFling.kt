package ru.e1173.nookast.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.abs

/**
 * List inertia by the screen's own density (user, 3.11.2026: in the car the lists' inertia stopped
 * short). The car profile draws the app at a larger density, and Compose decelerates a fling by the
 * density the list is drawn with, so flings ended 1.5 times sooner than in the system's own lists.
 * Where the app density is the device's, this is Compose's own fling.
 */
@Composable
internal fun deviceFling(): FlingBehavior {
    val deviceDensity = LocalResources.current.displayMetrics.density
    val appDensity = LocalDensity.current.density
    if (abs(deviceDensity - appDensity) < 0.01f) return ScrollableDefaults.flingBehavior()
    return remember(deviceDensity) { DeviceFlingBehavior(Density(deviceDensity)) }
}

private class DeviceFlingBehavior(density: Density) : FlingBehavior {
    private val decay = splineBasedDecay<Float>(density)

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var velocityLeft = initialVelocity
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = velocity
            // The list ran into its end: stop, as Compose's own fling does.
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return velocityLeft
    }
}
