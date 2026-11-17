package ru.e1173.nookast.ui

import android.content.res.Resources
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ru.e1173.nookast.R
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.connection.CardHistory
import ru.e1173.nookast.designsystem.connection.CardSlot
import ru.e1173.nookast.designsystem.connection.ConnectionPalette
import ru.e1173.nookast.designsystem.connection.ConnectionStory
import ru.e1173.nookast.designsystem.connection.GlyphKind
import ru.e1173.nookast.designsystem.connection.LinkState
import ru.e1173.nookast.designsystem.connection.PillModel
import ru.e1173.nookast.designsystem.connection.PillTone
import ru.e1173.nookast.designsystem.connection.RunwayLevel
import ru.e1173.nookast.designsystem.connection.SceneFocus
import ru.e1173.nookast.designsystem.connection.SceneLabels
import ru.e1173.nookast.designsystem.connection.SceneModel
import ru.e1173.nookast.designsystem.connection.SourceCardModel
import ru.e1173.nookast.designsystem.connection.connectionPalette
import ru.e1173.nookast.designsystem.connection.formatOneDecimal
import ru.e1173.nookast.designsystem.connection.qualityTier
import ru.e1173.nookast.offline.ServerDownloadProgress
import ru.e1173.nookast.player.NodeProbe
import ru.e1173.nookast.player.PlaybackUiState
import ru.e1173.nookast.player.ProbeStatus
import ru.e1173.nookast.player.QuickCheck
import ru.e1173.nookast.player.StreamReachability
import kotlin.math.roundToInt

/** The nine states of the source card and pill (DEC-069). */
enum class SourceState { OK, CHECKING, BLOCKED, DOWNLOADING, SERVER_COPY, RESCUE, SERVER_DOWN, OFFLINE, LOCAL }

/** The player's actions the pill and the card need, from the root where the player lives. */
class SourceActions(
    val runChecks: () -> Unit,
    val quickCheck: () -> Unit,
    val downloadToServer: () -> Unit,
    val setAlwaysViaServer: (Boolean) -> Unit,
)

val LocalSourceActions = compositionLocalOf<SourceActions?> { null }

/** Which of the nine states the player is in; [reach] is the settled reachability the pill shows. */
internal fun sourceState(s: PlaybackUiState, reach: StreamReachability): SourceState {
    val p = s.probes
    val fromServer = s.isServerCopySource || s.isUsingPinePodsFallback
    return when {
        s.isLocalSource -> SourceState.LOCAL
        p.internet.status == ProbeStatus.FAIL -> SourceState.OFFLINE
        s.serverDownload is ServerDownloadProgress.Running -> SourceState.DOWNLOADING
        s.isServerCopySource -> SourceState.SERVER_COPY
        s.isUsingPinePodsFallback -> SourceState.RESCUE
        !fromServer && (p.origin.status == ProbeStatus.FAIL || s.originStalled || reach == StreamReachability.UNAVAILABLE) -> SourceState.BLOCKED
        reach == StreamReachability.CHECKING || reach == StreamReachability.IDLE -> SourceState.CHECKING
        p.server.status == ProbeStatus.FAIL -> SourceState.SERVER_DOWN
        else -> SourceState.OK
    }
}

@Composable
internal fun rememberConnectionPalette(artworkUrl: String): ConnectionPalette {
    val colors = LocalNookastColors.current
    val glow = rememberArtworkGlow(rememberArtworkBitmap(artworkUrl, maxPx = 128))
    val cover = glow?.firstOrNull()
    return remember(colors, cover) { connectionPalette(colors, cover) }
}

private fun link(node: NodeProbe, fallback: LinkState): LinkState = when (node.status) {
    ProbeStatus.OK -> LinkState.OK
    ProbeStatus.FAIL -> LinkState.FAIL
    ProbeStatus.RUNNING -> LinkState.RUNNING
    ProbeStatus.UNKNOWN -> fallback
}

internal fun sceneModel(s: PlaybackUiState, state: SourceState, focus: SceneFocus?): SceneModel {
    val p = s.probes
    val local = state == SourceState.LOCAL
    val fromServer = state == SourceState.SERVER_COPY || state == SourceState.RESCUE
    val net = if (local) LinkState.NA else link(p.internet, if (state == SourceState.OFFLINE) LinkState.FAIL else if (state == SourceState.CHECKING) LinkState.RUNNING else LinkState.OK)
    val server = if (local) LinkState.NA else link(p.server, if (state == SourceState.SERVER_DOWN) LinkState.FAIL else if (state == SourceState.CHECKING) LinkState.RUNNING else LinkState.OK)
    val host = when {
        local -> LinkState.NA
        state == SourceState.BLOCKED || state == SourceState.DOWNLOADING || state == SourceState.RESCUE -> if (p.origin.status == ProbeStatus.RUNNING) LinkState.RUNNING else LinkState.FAIL
        else -> link(p.origin, if (state == SourceState.CHECKING) LinkState.RUNNING else LinkState.OK)
    }
    val mbps = s.bandwidthBitsPerSecond / 1e6f
    val flows = s.isPlaying && !s.isBuffering && state !in setOf(SourceState.BLOCKED, SourceState.DOWNLOADING, SourceState.OFFLINE, SourceState.LOCAL)
    return SceneModel(
        net = net,
        netMs = p.internet.latencyMs,
        server = server,
        serverMs = p.server.latencyMs,
        host = host,
        hostMs = p.origin.latencyMs,
        local = local,
        fromServer = fromServer,
        runwaySeconds = s.bufferedAheadMs / 1000f,
        filling = state == SourceState.CHECKING,
        level = when {
            local -> RunwayLevel.FULL
            s.runway.shown && s.runway.critical -> RunwayLevel.CRIT
            s.runway.shown -> RunwayLevel.WARN
            else -> RunwayLevel.NORMAL
        },
        bandwidthMbps = mbps,
        flow = if (flows) (if (mbps > 0f) (mbps / 5f).coerceIn(.3f, 1f) else .4f) else 0f,
        fetchPercent = (s.serverDownload as? ServerDownloadProgress.Running)?.percent,
        offer = state == SourceState.BLOCKED && server == LinkState.OK && !s.isOnServer,
        shareLeft = if (s.durationMs > 0) ((s.durationMs - s.positionMs).toFloat() / s.durationMs).coerceIn(0f, 1f) else .9f,
        running = p.running || state == SourceState.CHECKING,
        focus = focus,
    )
}

private fun Resources.duration(seconds: Float): String = when {
    seconds < 60f -> (seconds.roundToInt().coerceAtLeast(1)).let { getQuantityString(R.plurals.src_seconds, it, it) }
    seconds < 3600f -> (seconds / 60f).roundToInt().let { getQuantityString(R.plurals.src_minutes, it, it) }
    else -> getString(R.string.src_hours, formatOneDecimal(this, seconds / 3600f))
}

private fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** The vessel's number: minutes above a minute, m:ss below. */
private fun Resources.runwayText(seconds: Long): String = if (seconds >= 60) getString(R.string.src_runway_minutes, (seconds / 60f).roundToInt()) else clock(seconds)

/** The size of the file: the feed's or the server's figure, else bitrate × duration with «≈». */
private fun sizeMb(s: PlaybackUiState): Pair<Float, Boolean>? {
    s.mediaSizeBytes?.takeIf { it > 0 }?.let { return it / 1e6f to true }
    val kbps = s.bitrateKbps?.takeIf { it > 0 } ?: return null
    if (s.durationMs <= 0) return null
    return kbps * (s.durationMs / 1000f) / 8f / 1000f to false
}

internal fun Resources.sizeText(s: PlaybackUiState): String? = sizeMb(s)?.let { (mb, exact) ->
    val text = getString(R.string.src_mb, formatOneDecimal(this, mb))
    if (exact) text else getString(R.string.src_about, text)
}

/** The bottom line's comparisons, every 3.2 s: the stream, how much faster than it plays, and so on. */
private fun Resources.speedLines(s: PlaybackUiState, story: ConnectionStory): List<String> {
    val mbps = s.bandwidthBitsPerSecond / 1e6f
    if (mbps <= 0f) return emptyList()
    val lines = mutableListOf(getString(R.string.src_speed_flow, getString(story.strings.flowWord), formatOneDecimal(this, mbps)))
    val kbps = s.bitrateKbps?.takeIf { it > 0 }
    if (kbps != null) {
        val x = (mbps * 1000f / kbps).roundToInt()
        lines += getQuantityString(R.plurals.src_speed_times, x, x)
        sizeMb(s)?.let { (mb, _) -> lines += getString(R.string.src_speed_whole, duration(mb * 8f / mbps)) }
        lines += getString(R.string.src_speed_hour, duration(3600f * kbps / 1000f / mbps))
    }
    lines += getString(
        when {
            mbps < .5f -> R.string.src_speed_3g
            mbps < 5f -> R.string.src_speed_4g
            mbps < 50f -> R.string.src_speed_wifi
            else -> R.string.src_speed_fibre
        },
    )
    lines += story.speedNote(this, mbps)
    return lines
}

internal fun sceneLabels(r: Resources, s: PlaybackUiState, state: SourceState, scene: SceneModel, story: ConnectionStory, nowMs: Long): SceneLabels {
    val local = state == SourceState.LOCAL
    val offline = scene.net == LinkState.FAIL
    val topLeft = when {
        local -> r.getString(R.string.src_scene_no_network)
        offline -> r.getString(R.string.src_scene_offline, r.getString(story.strings.offlineWord))
        scene.net == LinkState.RUNNING || scene.netMs == null -> r.getString(R.string.src_scene_internet_checking)
        else -> r.getString(R.string.src_scene_internet, scene.netMs.toInt())
    }
    val checkedAt = s.probes.checkedAtElapsedMs
    val topRight = when {
        local || offline -> null
        scene.running -> r.getString(R.string.src_scene_checking_count, s.probes.done.coerceIn(0, 3))
        checkedAt == 0L -> null
        nowMs - checkedAt < 10_000L -> r.getString(R.string.src_scene_checked_now)
        nowMs - checkedAt < 60_000L -> r.getString(R.string.src_scene_checked, r.getString(R.string.src_ago_now))
        else -> r.getString(R.string.src_scene_checked, r.getString(R.string.src_ago_minutes, ((nowMs - checkedAt) / 60_000L).toInt()))
    }
    val bottom = when {
        local -> listOf(r.getString(R.string.src_scene_local))
        scene.fetchPercent != null -> listOf(r.getString(R.string.src_scene_fetching, scene.fetchPercent))
        scene.offer -> listOf(r.getString(R.string.src_scene_can_fetch))
        scene.flow > 0f && scene.net == LinkState.OK -> r.speedLines(s, story)
        else -> emptyList()
    }
    val runway = (s.bufferedAheadMs / 1000).coerceAtLeast(0)
    return SceneLabels(
        topLeft = topLeft,
        topLeftBad = offline,
        topLeftWide = offline || local,
        topRight = topRight,
        bottom = bottom,
        bottomMuted = local,
        vesselValue = when {
            local -> clock(s.durationMs / 1000)
            scene.filling -> "…"
            else -> r.runwayText(runway)
        },
        vesselCaption = r.getString(if (local) R.string.src_vessel_all else R.string.src_vessel_buffer),
    )
}

private val TITLES = mapOf(
    SourceState.OK to R.string.src_title_ok,
    SourceState.CHECKING to R.string.src_title_checking,
    SourceState.BLOCKED to R.string.src_title_blocked,
    SourceState.DOWNLOADING to R.string.src_title_downloading,
    SourceState.SERVER_COPY to R.string.src_title_server_copy,
    SourceState.RESCUE to R.string.src_title_rescue,
    SourceState.SERVER_DOWN to R.string.src_title_server_down,
    SourceState.OFFLINE to R.string.src_title_offline,
    SourceState.LOCAL to R.string.src_title_local,
)

private val WHYS = mapOf(
    SourceState.OK to R.string.src_why_ok,
    SourceState.CHECKING to R.string.src_why_checking,
    SourceState.BLOCKED to R.string.src_why_blocked,
    SourceState.DOWNLOADING to R.string.src_why_downloading,
    SourceState.SERVER_COPY to R.string.src_why_server_copy,
    SourceState.RESCUE to R.string.src_why_rescue,
    SourceState.SERVER_DOWN to R.string.src_why_server_down,
    SourceState.OFFLINE to R.string.src_why_offline,
    SourceState.LOCAL to R.string.src_why_local,
)

/** Every explanation of the current language, for the line count that suits them all. */
internal fun Resources.allWhys(): List<Pair<String, Boolean>> =
    WHYS.map { (state, id) -> getString(id) to (state == SourceState.SERVER_COPY) } + (getString(R.string.src_why_always) to true)

internal fun Resources.cardModel(
    s: PlaybackUiState,
    state: SourceState,
    story: ConnectionStory,
    focus: SceneFocus?,
    introSeen: Boolean,
    nowMs: Long,
): SourceCardModel {
    val scene = sceneModel(s, state, focus)
    val labels = sceneLabels(this, s, state, scene, story, nowMs)
    // The second failure of this podcast's host offers «always via the server».
    val repeat = state == SourceState.SERVER_COPY && (s.hostFailedBefore || s.alwaysViaServer) && s.currentPodcastId != null
    val why = when {
        repeat && s.alwaysViaServer -> getString(R.string.src_why_always)
        state == SourceState.SERVER_COPY && !repeat -> getString(R.string.src_why_rescue)
        else -> getString(WHYS.getValue(state))
    }
    val subtitle = when {
        state == SourceState.LOCAL -> getString(R.string.src_sub_local)
        scene.fromServer -> getString(R.string.src_sub_copy, s.serverHost.ifBlank { "PinePods" })
        else -> getString(R.string.src_sub_rss, s.originHost.ifBlank { s.streamHost })
    }
    val ms = { link: LinkState, value: Long?, none: Int ->
        when (link) {
            LinkState.OK -> value?.let { getString(R.string.src_ms, it.toInt()) } ?: "—"
            LinkState.FAIL -> getString(none)
            else -> "—"
        }
    }
    val description = getString(
        R.string.src_scene_description,
        ms(scene.net, scene.netMs, R.string.src_desc_none),
        ms(scene.server, scene.serverMs, R.string.src_desc_no_answer),
        ms(scene.host, scene.hostMs, R.string.src_desc_no_answer),
        if (scene.local) getString(R.string.src_desc_whole) else runwayText((s.bufferedAheadMs / 1000).coerceAtLeast(0)),
    )
    val history = focus?.let {
        val node = when (it) {
            SceneFocus.INTERNET -> s.probes.internet
            SceneFocus.SERVER -> s.probes.server
            SceneFocus.HOST -> s.probes.origin
        }
        if (node.history.isEmpty()) return@let null
        CardHistory(
            name = when (it) {
                SceneFocus.INTERNET -> getString(R.string.src_hist_internet)
                SceneFocus.SERVER -> getString(R.string.src_hist_server, s.serverHost)
                SceneFocus.HOST -> getString(R.string.src_hist_host, s.originHost.ifBlank { s.streamHost })
            },
            unit = getString(R.string.src_hist_unit),
            values = node.history.map { v -> v?.toString() ?: getString(R.string.src_hist_fail) },
            failed = node.history.map { v -> v == null },
        )
    }
    val kbps = s.bitrateKbps?.takeIf { it > 0 }
    val size = sizeText(s)
    val slot = when {
        scene.fetchPercent != null -> CardSlot.Progress(scene.fetchPercent, getString(R.string.src_button_progress, scene.fetchPercent))
        scene.offer -> CardSlot.Download(getString(R.string.src_button_download), getString(R.string.src_button_download_short))
        else -> {
            val kbpsText = kbps?.let { getString(R.string.src_kbps, it) } ?: "—"
            val mb = sizeMb(s)?.first
            CardSlot.Tiles(
                qualityLabel = getString(R.string.src_tile_quality),
                tier = qualityTier(kbps),
                kbps = kbpsText,
                sizeLabel = getString(R.string.src_tile_size, size ?: "—"),
                sizeNote = mb?.let { story.sizeNote(this, it) } ?: "—",
                merged = listOfNotNull(kbpsText.takeIf { kbps != null }, size).joinToString(" · ").ifBlank { "—" },
            )
        }
    }
    return SourceCardModel(
        title = getString(TITLES.getValue(state)),
        subtitle = subtitle,
        why = why,
        whyAll = allWhys(),
        always = if (repeat) s.alwaysViaServer else null,
        showCheck = state != SourceState.LOCAL,
        checking = scene.running,
        checkLabel = getString(R.string.src_check),
        scene = scene,
        labels = labels,
        sceneDescription = description,
        history = history,
        slot = slot,
        intro = if (introSeen) null else getString(story.strings.intro),
        introClose = getString(R.string.src_intro_close),
    )
}

internal fun Resources.pillModel(s: PlaybackUiState, state: SourceState, quick: QuickCheck?): PillModel {
    var label: String
    var short: String
    var tone: PillTone
    when (state) {
        SourceState.OK -> { label = getString(R.string.src_pill_direct); short = label; tone = PillTone.NORMAL }
        SourceState.CHECKING -> { label = getString(R.string.src_pill_checking); short = label; tone = PillTone.RUN }
        SourceState.BLOCKED -> { label = getString(R.string.src_pill_host_down); short = getString(R.string.src_pill_host_down_short); tone = PillTone.BAD }
        SourceState.DOWNLOADING -> { label = getString(R.string.src_pill_fetching); short = getString(R.string.src_pill_fetching_short); tone = PillTone.ACCENT }
        SourceState.SERVER_COPY -> { label = getString(R.string.src_pill_from_server); short = label; tone = PillTone.NORMAL }
        SourceState.RESCUE -> { label = getString(R.string.src_pill_via_server); short = label; tone = PillTone.NORMAL }
        SourceState.SERVER_DOWN -> { label = getString(R.string.src_pill_direct); short = label; tone = PillTone.NORMAL }
        SourceState.OFFLINE -> { label = getString(R.string.src_pill_offline); short = label; tone = PillTone.BAD }
        SourceState.LOCAL -> { label = getString(R.string.src_pill_local); short = label; tone = PillTone.NORMAL }
    }
    var tail: String? = null
    var bare: String? = null
    val fetch = (s.serverDownload as? ServerDownloadProgress.Running)?.percent
    val runway = s.runway
    when {
        quick != null -> {
            when {
                quick.running -> { label = getString(R.string.src_pill_checking); short = label; tone = PillTone.RUN }
                quick.internet == ProbeStatus.FAIL -> { label = getString(R.string.src_quick_no_internet); short = label; tone = PillTone.BAD }
                quick.origin.status == ProbeStatus.FAIL -> {
                    label = getString(R.string.src_quick_host_silent, (quick.server.latencyMs ?: 0L).toInt())
                    short = getString(R.string.src_quick_host_silent_short)
                    tone = PillTone.BAD
                }
                quick.server.status == ProbeStatus.FAIL -> {
                    label = getString(R.string.src_quick_server_silent, (quick.origin.latencyMs ?: 0L).toInt())
                    short = getString(R.string.src_quick_server_silent_short)
                    tone = PillTone.WARN
                }
                else -> {
                    label = getString(R.string.src_quick_all, (s.probes.internet.latencyMs ?: quick.server.latencyMs ?: 0L).toInt())
                    short = getString(R.string.src_quick_all_short)
                    tone = PillTone.NORMAL
                }
            }
        }
        fetch != null -> {
            tail = getString(R.string.src_pill_tail_percent, fetch)
            bare = getString(R.string.src_pill_tail_percent_bare, fetch)
        }
        runway.shown -> {
            bare = clock(runway.seconds.toLong())
            tail = "· $bare"
            tone = if (runway.critical) PillTone.BAD else PillTone.WARN
        }
    }
    val checking = quick?.running == true || (quick == null && state == SourceState.CHECKING)
    val said = when {
        quick == null && runway.shown -> getString(R.string.src_pill_said_runway, clock(runway.seconds.toLong()))
        quick == null && fetch != null -> getQuantityString(R.plurals.src_pill_said_percent, fetch, fetch)
        else -> ""
    }
    val glyph = when {
        state == SourceState.LOCAL -> GlyphKind.LOCAL
        fetch != null -> GlyphKind.FETCH
        state == SourceState.SERVER_COPY || state == SourceState.RESCUE -> GlyphKind.SERVER
        state == SourceState.OFFLINE -> GlyphKind.OFFLINE
        state == SourceState.BLOCKED -> GlyphKind.HOST_DOWN
        else -> GlyphKind.DIRECT
    }
    return PillModel(
        label = label,
        short = short,
        tone = tone,
        tail = tail,
        tailBare = bare,
        tier = if (quick == null) qualityTier(if (state == SourceState.LOCAL) s.bitrateKbps else s.bitrateKbps) else null,
        glyph = glyph,
        fetch = (fetch ?: 0) / 100f,
        checking = checking,
        runway = if (quick == null && runway.shown) (runway.seconds / 60f) else null,
        runwayCritical = runway.critical,
        serverDot = quick == null && state == SourceState.SERVER_DOWN,
        description = "$label$said. ${getString(R.string.src_pill_hint)}",
    )
}

/** Now, for the «checked … ago» corner. */
internal fun elapsedNow(): Long = SystemClock.elapsedRealtime()

@Composable
internal fun resources(): Resources = LocalContext.current.resources

/**
 * The dock's download label (DEC-069): «Скачать · 34 МБ»; when the device is short of room (less
 * than the file and 100 MB to spare where downloads go), «34 МБ, свободно 120 МБ» instead.
 */
internal fun downloadLabelWithSize(context: android.content.Context, r: Resources, s: PlaybackUiState): String {
    val mb = sizeMb(s) ?: return r.getString(R.string.download_episode)
    // Whole megabytes on the button, as the mock-up writes it: «Скачать · 34 МБ».
    val size = r.getString(R.string.src_mb, mb.first.roundToInt().coerceAtLeast(1).toString()).let { if (mb.second) it else r.getString(R.string.src_about, it) }
    val bytes = s.mediaSizeBytes?.takeIf { it > 0 }
        ?: s.bitrateKbps?.takeIf { it > 0 }?.let { it * 1000L / 8L * (s.durationMs / 1000L) }
        ?: 0L
    val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS) ?: context.filesDir
    val free = runCatching { android.os.StatFs(directory.path).availableBytes }.getOrNull()
    if (free != null && bytes > 0 && free < bytes + SPACE_MARGIN_BYTES) {
        return r.getString(R.string.dock_download_space, size, r.getString(R.string.src_mb, (free / 1_000_000L).toString()))
    }
    return r.getString(R.string.dock_download_size, size)
}

private const val SPACE_MARGIN_BYTES = 100L * 1000 * 1000
