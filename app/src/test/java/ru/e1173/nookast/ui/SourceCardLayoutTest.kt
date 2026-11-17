package ru.e1173.nookast.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.e1173.nookast.designsystem.LocalNookastColors
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
import java.util.Locale

/**
 * DEC-069, without screenshots: the source card in every state, RU and EN, font 1.0 / 1.15 / 1.3 and
 * screens of 346 and 360 dp. No text of the card overflows its block, and the card has one height
 * in every state of a configuration. The pill fits between the times of a short and an hour-long
 * episode.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h2000dp-440dpi", application = android.app.Application::class)
class SourceCardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun ok(ms: Long) = NodeProbe(ProbeStatus.OK, ms, listOf(ms, ms - 2, ms + 3, ms, ms + 1))

    private fun fail() = NodeProbe(ProbeStatus.FAIL, null, listOf(null, null, null, 118, 121))

    private val base = PlaybackUiState(
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

    private val states: List<Pair<String, PlaybackUiState>> = listOf(
        "ok" to base,
        "checking" to base.copy(streamReachability = StreamReachability.CHECKING, probes = ConnectionProbes(running = true)),
        "blocked" to base.copy(streamReachability = StreamReachability.UNAVAILABLE, bufferedAheadMs = 40_000, runway = RunwayStatus(true, 40, false), probes = base.probes.copy(origin = fail())),
        "downloading" to base.copy(serverDownload = ServerDownloadProgress.Running(46), probes = base.probes.copy(origin = fail())),
        "server copy" to base.copy(isServerCopySource = true, hostFailedBefore = true),
        "server copy always" to base.copy(isServerCopySource = true, hostFailedBefore = true, alwaysViaServer = true),
        "rescue" to base.copy(isUsingPinePodsFallback = true),
        "server down" to base.copy(probes = base.probes.copy(server = fail())),
        "offline" to base.copy(bufferedAheadMs = 12_000, runway = RunwayStatus(true, 12, true), probes = base.probes.copy(internet = fail())),
        "local" to base.copy(isLocalSource = true, streamReachability = StreamReachability.LOCAL),
        "long host" to base.copy(originHost = "episodes.long-podcast-hosting-network.example.com", streamHost = "episodes.long-podcast-hosting-network.example.com"),
    )

    private fun resources(language: String) = ApplicationProvider.getApplicationContext<android.content.Context>().let { context ->
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        context.createConfigurationContext(config).resources
    }

    @Test
    fun `the card keeps one height and no text overflows`() {
        val report = StringBuilder()
        var config by mutableStateOf(Triple("ru", 1f, 360))
        var stateIndex by mutableStateOf(0)
        var focus by mutableStateOf<SceneFocus?>(null)
        var height = -1
        val colors = nookastColors(ThemePalette.SIGNATURE, dark = true)
        val palette = connectionPalette(colors, null)
        compose.setContent {
            val (language, scale, screen) = config
            val res = resources(language)
            val s = states[stateIndex].second
            val model = res.cardModel(s, sourceState(s, s.streamReachability), ConnectionStories.default, focus, introSeen = true, nowMs = 5_000)
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale), LocalNookastColors provides colors) {
                NookSourceCard(
                    model = model,
                    story = ConnectionStories.default,
                    palette = palette,
                    onCheck = {},
                    onSceneTap = {},
                    onAlways = {},
                    onDownload = {},
                    onCloseIntro = {},
                    // The card is min(316, screen − 44) wide with 16 dp of padding on each side.
                    modifier = Modifier.width(minOf(316, screen - 44).dp - 32.dp).onSizeChanged { height = it.height },
                    live = false,
                )
            }
        }
        for (language in listOf("ru", "en")) for (scale in listOf(1f, 1.15f, 1.3f)) for (screen in listOf(346, 360)) {
            val heights = mutableMapOf<String, Int>()
            states.indices.forEach { i ->
                listOf<SceneFocus?>(null, SceneFocus.HOST).forEach { f ->
                    config = Triple(language, scale, screen)
                    stateIndex = i
                    focus = f
                    compose.waitForIdle()
                    val tag = "$language ×$scale ${screen}dp ${states[i].first}${if (f != null) " +history" else ""}"
                    heights[tag] = height
                    overflowing().forEach { report.appendLine("$tag: «$it» overflows") }
                }
            }
            val distinct = heights.values.toSet()
            if (distinct.size > 1) report.appendLine("heights differ: $heights")
        }
        assertTrue(report.toString(), report.isEmpty())
    }

    @Test
    fun `the pill fits between the times`() {
        val report = StringBuilder()
        val colors = nookastColors(ThemePalette.LAVENDER, dark = false)
        val palette = connectionPalette(colors, null)
        var config by mutableStateOf(Triple("ru", 1f, 0))
        var width = 0
        compose.setContent {
            val (language, scale, i) = config
            val s = states[i].second
            val model = resources(language).pillModel(s, sourceState(s, s.streamReachability), null)
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale), LocalNookastColors provides colors) {
                NookSourcePill(model, ConnectionStories.default, palette, maxWidth = ROOM.dp, onClick = {}, onLongPress = {}, modifier = Modifier.onSizeChanged { width = it.width })
            }
        }
        for (language in listOf("ru", "en")) for (scale in listOf(1f, 1.15f, 1.3f)) states.indices.forEach { i ->
            config = Triple(language, scale, i)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(2_000)
            // The pill's own steps measure its words; here its width must stay within the room it was given.
            if (width / 2.75f > ROOM + .5f) report.appendLine("$language ×$scale ${states[i].first}: ${width / 2.75f} dp > $ROOM")
        }
        assertTrue(report.toString(), report.isEmpty())
    }

    /** Texts whose layout reports a visual overflow (cut or ellipsized). */
    private fun overflowing(): List<String> = compose.onAllNodesWithText("", substring = true).fetchSemanticsNodes().mapNotNull { node ->
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        results.firstOrNull()?.takeIf(::cut)?.let { r -> "${r.layoutInput.text.text} size=${r.size}" }
    }

    /** Cut or ellipsized: more lines than allowed, an ellipsis, a line wider than its box, or taller than it. */
    private fun cut(r: TextLayoutResult): Boolean =
        r.multiParagraph.didExceedMaxLines ||
            r.didOverflowHeight ||
            (0 until r.lineCount).any { r.isLineEllipsized(it) || r.getLineRight(it) - r.getLineLeft(it) > r.size.width + .5f }

    private companion object {
        /** The room between «1:12:34» and «−1:23:10» on a 346 dp screen at font 1.3, the narrowest case. */
        const val ROOM = 150
    }
}
