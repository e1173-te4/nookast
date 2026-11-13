package ru.e1173.nookast.designsystem

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the theme change was asked for (design audit 5.7): a palette sample records the touch, in
 * window pixels, and the theme takes it if it changes right after. A change without a touch (the
 * system's dark mode) has no origin and keeps the plain fade.
 */
object ThemeReveal {
    private var origin: Offset? = null
    private var at = 0L

    internal fun record(point: Offset) {
        origin = point
        at = SystemClock.uptimeMillis()
    }

    /** The touch that asked for this change, if it was within the last moment; consumed once. */
    fun take(): Offset? {
        val point = origin?.takeIf { SystemClock.uptimeMillis() - at < FRESH_MS }
        origin = null
        return point
    }

    private const val FRESH_MS = 1_500L
}

/** On a palette sample: its touches can start the theme's circular reveal. */
fun Modifier.themeRevealSource(): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    onGloballyPositioned { coordinates = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                coordinates?.let { ThemeReveal.record(it.localToWindow(down.position)) }
            }
        }
}
