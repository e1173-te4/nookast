package ru.e1173.nookast.designcatalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.LocalPlayLight
import ru.e1173.nookast.designsystem.MiniPlayerLabels
import ru.e1173.nookast.designsystem.NookMiniPlayer
import ru.e1173.nookast.designsystem.NookShapes
import ru.e1173.nookast.designsystem.NookSwitch
import ru.e1173.nookast.designsystem.NookTransport
import ru.e1173.nookast.designsystem.NookType
import ru.e1173.nookast.designsystem.TransportLabels
import ru.e1173.nookast.designsystem.rememberPlayLight

/**
 * Buffering without a network (design audit 4.11 and 5.3): the open player's transport and the
 * mini-player with buffering forced on, under the real Play light, so the breathing (after 400 ms)
 * and the 270° arc can be looked at. View it in the dark theme: the light is a night effect.
 */
internal val BufferingScene = CatalogScene(
    id = "buffering-r1",
    title = "Buffering · breathing and arc",
    state = "buffering",
    revision = "play-light/r1",
    behaviorContract = "docs/design/catalog/components/icon-controls/r1/BEHAVIOR.md",
    content = { context, modifier -> BufferingSample(context, modifier) },
)

@Composable
private fun BufferingSample(context: CatalogSceneContext, modifier: Modifier) {
    val russian = context.locale == CatalogLocale.RU
    val colors = LocalNookastColors.current
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    val light = rememberPlayLight(playing = playing, buffering = buffering)
    CompositionLocalProvider(LocalPlayLight provides light) {
        Column(
            modifier.background(colors.background).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (russian) "Буферизация" else "Buffering", style = NookType.rowTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                NookSwitch(buffering, { buffering = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (russian) "Играет" else "Playing", style = NookType.rowTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                NookSwitch(playing, { playing = it })
            }
            NookTransport(
                playing = playing,
                buffering = buffering,
                enabled = true,
                seekBackSeconds = 10,
                seekForwardSeconds = 30,
                labels = TransportLabels("Back 10", "Forward 30", "Play", "Pause"),
                onSeekBack = {},
                onPlayPause = { playing = !playing },
                onSeekForward = {},
                modifier = Modifier.fillMaxWidth(),
            )
            NookMiniPlayer(
                title = if (russian) "Выпуск, который грузится" else "An episode still loading",
                podcastName = "Nookast",
                progress = 0.3f,
                playing = playing,
                buffering = buffering,
                enabled = true,
                labels = MiniPlayerLabels("Open", "Play", "Pause", "Up next"),
                onOpen = {},
                onPlayPause = { playing = !playing },
                onQueue = {},
                artwork = { m -> Box(m.size(48.dp).background(colors.tintContainer, NookShapes.s)) },
            )
        }
    }
}
