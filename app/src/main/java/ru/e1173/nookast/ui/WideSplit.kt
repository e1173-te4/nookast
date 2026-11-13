package ru.e1173.nookast.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Master-detail on a wide screen (user, 27.10.2026) for Podcasts and Settings: the left pane is the
 * phone screen exactly as on a phone, the right pane shows what a tap would open on the phone, over
 * all the remaining width, without rescaling the left one. Other screens stay single and only
 * widen their layout; Now Playing has its own split.
 */
@Composable
internal fun WideSplit(
    largeControls: Boolean,
    phone: @Composable (Modifier) -> Unit,
    detail: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    phoneWidth: Dp = if (largeControls) 480.dp else 360.dp,
    /** The detail pane's own inset; a pane whose list pads itself passes less, so both sides of
     *  the divider and both screen edges get the same gap (user, 27.10.2026). */
    detailPadding: Dp = 16.dp,
) {
    Row(modifier.fillMaxSize()) {
        phone(Modifier.width(phoneWidth).fillMaxHeight().padding(horizontal = 12.dp))
        // No line between the panes (user, 11.11.2026): the gap alone separates them.
        androidx.compose.foundation.layout.Spacer(Modifier.width(1.dp))
        detail(Modifier.weight(1f).fillMaxHeight().padding(horizontal = detailPadding))
    }
}
