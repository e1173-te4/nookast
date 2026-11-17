package ru.e1173.nookast.designcatalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookButton
import ru.e1173.nookast.designsystem.NookButtonVariant
import ru.e1173.nookast.designsystem.NookTimecodes
import ru.e1173.nookast.designsystem.ThemePalette
import ru.e1173.nookast.designsystem.connection.ConnectionStories
import ru.e1173.nookast.designsystem.connection.NookSourceCard
import ru.e1173.nookast.designsystem.connection.NookSourcePill
import ru.e1173.nookast.designsystem.connection.SceneFocus
import ru.e1173.nookast.designsystem.connection.connectionPalette
import ru.e1173.nookast.designsystem.nookastColors
import ru.e1173.nookast.offline.ServerDownloadProgress
import ru.e1173.nookast.player.ConnectionProbes
import ru.e1173.nookast.player.NodeProbe
import ru.e1173.nookast.player.PlaybackUiState
import ru.e1173.nookast.player.ProbeStatus
import ru.e1173.nookast.player.RunwayStatus
import ru.e1173.nookast.player.StreamReachability
import ru.e1173.nookast.ui.cardModel
import ru.e1173.nookast.ui.pillModel
import ru.e1173.nookast.ui.sourceState

/**
 * The source pill, the connection card and the scheme of DEC-069 for manual review: the nine states,
 * the four stories, the history of a node and the pill between the times of a short and an hour-long
 * episode. The same components as on «Сейчас играет».
 */
internal val ConnectionScene = CatalogScene(
    id = "connection-r1",
    title = "Source pill · Connection card · Scheme",
    state = "nine states × four stories",
    revision = "connection/r1",
    behaviorContract = "docs/design/catalog/components/connection/r1/BEHAVIOR.md",
    content = { context, modifier -> ConnectionSample(context, modifier) },
)

private fun ok(ms: Long) = NodeProbe(ProbeStatus.OK, ms, listOf(ms, ms - 2, ms + 3, ms, ms + 1))

private fun fail() = NodeProbe(ProbeStatus.FAIL, null, listOf(null, null, null, 118, 121))

private val sample = PlaybackUiState(
    isConnected = true,
    currentEpisodeId = 214,
    isPlaying = true,
    positionMs = 754_000,
    durationMs = 2_144_000,
    bufferedAheadMs = 840_000,
    streamReachability = StreamReachability.AVAILABLE,
    streamHost = "cdn.podhost.fm",
    originHost = "cdn.podhost.fm",
    serverHost = "pods.example.ru",
    bitrateKbps = 128,
    mediaSizeBytes = 34_300_000,
    bandwidthBitsPerSecond = 1_800_000,
    probes = ConnectionProbes(ok(41), ok(63), ok(118), checkedAtElapsedMs = 1),
    currentPodcastId = 7,
)

private val states = listOf(
    "ok" to sample,
    "checking" to sample.copy(streamReachability = StreamReachability.CHECKING, bufferedAheadMs = 4_000, probes = ConnectionProbes(running = true)),
    "blocked" to sample.copy(streamReachability = StreamReachability.UNAVAILABLE, bufferedAheadMs = 38_000, runway = RunwayStatus(true, 38, false), probes = sample.probes.copy(origin = fail())),
    "downloading" to sample.copy(serverDownload = ServerDownloadProgress.Running(46), bufferedAheadMs = 24_000, probes = sample.probes.copy(origin = fail())),
    "server copy" to sample.copy(isServerCopySource = true, hostFailedBefore = true, bufferedAheadMs = 540_000),
    "rescue" to sample.copy(isUsingPinePodsFallback = true, bufferedAheadMs = 300_000),
    "server down" to sample.copy(probes = sample.probes.copy(server = fail())),
    "offline" to sample.copy(bufferedAheadMs = 12_000, runway = RunwayStatus(true, 12, true), probes = sample.probes.copy(internet = fail())),
    "local" to sample.copy(isLocalSource = true, streamReachability = StreamReachability.LOCAL, bitrateKbps = 192),
)

@Composable
private fun ConnectionSample(context: CatalogSceneContext, modifier: Modifier) {
    val dark = context.theme == CatalogTheme.DARK
    var stateIndex by remember { mutableStateOf(0) }
    var storyIndex by remember { mutableStateOf(0) }
    var focus by remember { mutableStateOf<SceneFocus?>(null) }
    var always by remember { mutableStateOf(false) }
    var long by remember { mutableStateOf(false) }
    val colors = nookastColors(ThemePalette.SIGNATURE, dark)
    // The catalog's own language, not the device's: the words of the card follow the toggle.
    val base = LocalContext.current
    val current = androidx.compose.ui.platform.LocalConfiguration.current
    val res = remember(context.locale, current) {
        val config = android.content.res.Configuration(current)
        config.setLocale(java.util.Locale.forLanguageTag(if (context.locale == CatalogLocale.RU) "ru" else "en"))
        base.createConfigurationContext(config).resources
    }
    val story = ConnectionStories.all[storyIndex]
    val s = states[stateIndex].second.copy(alwaysViaServer = always)
    val source = sourceState(s, s.streamReachability)
    CompositionLocalProvider(LocalNookastColors provides colors) {
        val palette = connectionPalette(colors, null)
        Box(modifier.background(colors.background)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    states.forEachIndexed { i, (name, _) ->
                        NookButton(variant = if (i == stateIndex) NookButtonVariant.Tonal else NookButtonVariant.Text, onClick = { stateIndex = i; focus = null }) { androidx.compose.material3.Text(name) }
                    }
                    ConnectionStories.all.forEachIndexed { i, st ->
                        NookButton(variant = if (i == storyIndex) NookButtonVariant.Tonal else NookButtonVariant.Text, onClick = { storyIndex = i }) { androidx.compose.material3.Text(res.getString(st.strings.name)) }
                    }
                    NookButton(variant = NookButtonVariant.Text, onClick = { long = !long }) { androidx.compose.material3.Text(if (long) "1:12:34" else "12:34") }
                }
                Box(Modifier.width(316.dp).background(palette.card).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                    NookSourceCard(
                        model = res.cardModel(s, source, story, focus, introSeen = stateIndex != 0, nowMs = 60_000),
                        story = story,
                        palette = palette,
                        onCheck = {},
                        onSceneTap = { tapped -> focus = if (focus == tapped) null else tapped },
                        onAlways = { always = it },
                        onDownload = { stateIndex = 3 },
                        onCloseIntro = {},
                    )
                }
                NookTimecodes(
                    elapsed = if (long) "1:12:34" else "12:34",
                    remaining = if (long) "−1:23:10" else "−23:10",
                    centerFitted = { room ->
                        NookSourcePill(res.pillModel(s, source, null), story, palette, room, onClick = {}, onLongPress = {})
                    },
                )
            }
        }
    }
}
