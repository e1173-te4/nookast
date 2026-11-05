package ru.e1173.nookast.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import ru.e1173.nookast.designsystem.LocalNookastColors
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.e1173.nookast.R
import ru.e1173.nookast.player.ControlReadEvents

/**
 * "Continued from 15:00 — from another device" with "Go back", or an offer with "Go there"
 * (SYNC.md §5.10). Shown for [MESSAGE_MS] as a banner at the top of the screen in the theme's own
 * card colours, light and dark (user, 5.11.2026: a stock snackbar over the player controls fell
 * out of the design); on wide screens it keeps a readable width.
 */
@Composable
internal fun ControlReadMessage(
    currentEpisodeId: Int?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val event by ControlReadEvents.latest.collectAsStateWithLifecycle()
    val current = event?.takeIf { it.mediaId == currentEpisodeId?.toString() }
    LaunchedEffect(event?.id) {
        val id = event?.id ?: return@LaunchedEffect
        delay(MESSAGE_MS)
        ControlReadEvents.consume(id)
    }
    val colors = LocalNookastColors.current
    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        val shown = current ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 520.dp)
                .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .border(1.dp, colors.surfaceOutline, RoundedCornerShape(22.dp))
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(
                    if (shown.jumped) R.string.control_read_jumped else R.string.control_read_offer,
                    formatListeningPosition(shown.serverMs),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
            )
            TextButton(
                onClick = {
                    onSeek(shown.targetMs)
                    ControlReadEvents.consume(shown.id)
                },
            ) {
                Text(
                    stringResource(if (shown.jumped) R.string.control_read_go_back else R.string.control_read_go_there),
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

internal fun formatListeningPosition(ms: Long): String {
    val total = (ms / 1_000).coerceAtLeast(0)
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private const val MESSAGE_MS = 10_000L
