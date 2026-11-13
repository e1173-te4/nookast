package ru.e1173.nookast.designsystem

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A visible ring while the element has keyboard, D-pad or rotary focus (plan 5.5, U-12): the car's
 * knob moves focus, and many controls draw no ripple, so without it the driver could not see where
 * focus is. Place it before the clickable so the ring follows the element's own focus. A filled
 * control passes a [color] that differs from its fill.
 */
@Composable
fun Modifier.focusRing(shape: Shape, color: Color = LocalNookastColors.current.accent): Modifier {
    var focused by remember { mutableStateOf(false) }
    val accent = color
    return onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(if (focused) Modifier.border(2.5.dp, accent, shape) else Modifier)
}
