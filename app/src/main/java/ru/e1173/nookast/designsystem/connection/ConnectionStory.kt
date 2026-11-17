package ru.e1173.nookast.designsystem.connection

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/*
 * The connection scheme is built like the colour themes (DEC-069): one engine and stories that only
 * draw their own world. A new story is one Kotlin file, an entry in [ConnectionStories] and its RU/EN
 * strings.
 */

/** How a node of the scheme answered its last check. */
enum class LinkState { OK, RUNNING, FAIL, NA }

/** The two sources on the scheme: the episode's host and the user's server. */
enum class SourceKind { HOST, SERVER }

/** What a tap on the scheme chose: a source, or the internet (the background). */
enum class SceneFocus { INTERNET, SERVER, HOST }

/** How full the buffer is against the line where the pill starts to show it. */
enum class RunwayLevel { NORMAL, WARN, CRIT, FULL }

/** How a route looks: sound runs on it, it is cut off, or it waits for a transfer. */
enum class LaneMode { LIVE, BLOCKED, IDLE }

/** The pill's glyph of each state; every story draws its own. */
enum class GlyphKind { DIRECT, SERVER, FETCH, HOST_DOWN, OFFLINE, LOCAL }

/** What the engine knows of one moment of the connection; built by the player's UI layer. */
@Immutable
data class SceneModel(
    val net: LinkState,
    val netMs: Long?,
    val server: LinkState,
    val serverMs: Long?,
    val host: LinkState,
    val hostMs: Long?,
    val local: Boolean,
    /** Sound comes from the server's copy (or its rescue stream). */
    val fromServer: Boolean,
    val runwaySeconds: Float,
    /** The start: the buffer fills, the vessel counts to 50 s. */
    val filling: Boolean,
    val level: RunwayLevel,
    val bandwidthMbps: Float,
    /** 0…1: how lively the stream is (how many couriers). */
    val flow: Float,
    /** The server's download of the episode, in percent, while it runs. */
    val fetchPercent: Int?,
    /** The server could fetch the episode: the link between the sources waits. */
    val offer: Boolean,
    /** How much of the episode is left to play (for a source's share). */
    val shareLeft: Float,
    val running: Boolean,
    val focus: SceneFocus?,
)

/** The scheme's words: two corners, the bottom line (rotating every 3.2 s) and the vessel's number. */
@Immutable
data class SceneLabels(
    val topLeft: String,
    val topLeftBad: Boolean,
    val topLeftWide: Boolean,
    val topRight: String?,
    val bottom: List<String>,
    val bottomMuted: Boolean,
    val vesselValue: String,
    val vesselCaption: String,
)

/** What a story draws with: the moment, its time in seconds and the scheme's colours. */
class SceneEnv(
    val model: SceneModel,
    val t: Float,
    val colors: SceneColors,
    /** The font scale the corner words grow with, at most 1.3: the quiet zones grow with it. */
    val fontScale: Float = 1f,
) {
    val dark: Boolean get() = colors.dark
    val local: Boolean get() = model.local
    val offline: Boolean get() = model.net == LinkState.FAIL

    /** The ink the story draws its world with: a little lighter than the text at night. */
    val ink: Color get() = if (colors.dark) Color(0xFFF3F0FF) else colors.ink

    /** How hard a courier has just touched the vessel, 0…1. */
    var splash: Float = 0f
        internal set

    val level: RunwayLevel get() = model.level
}

/** Where a story stands its host, server and vessel, and its routes between them. */
class StoryLayout(
    val host: Offset,
    val server: Offset,
    val vessel: VesselShape,
    val toVessel: (SourceKind) -> SceneRoute,
    val link: () -> SceneRoute,
)

/** What a source shows: its state and how much of the episode it holds. */
class SourceInfo(
    val state: LinkState,
    val isSource: Boolean,
    val share: Float?,
    val blockedForPhone: Boolean,
    val copy: Float,
)

/** A story's words: [name], the story itself, who it suits, the first-open hint and the stream's words. */
class StoryStrings(
    @StringRes val name: Int,
    @StringRes val text: Int,
    @StringRes val suits: Int,
    @StringRes val intro: Int,
    @StringRes val flowWord: Int,
    @StringRes val offlineWord: Int,
)

interface ConnectionStory {
    val id: String
    val strings: StoryStrings

    fun layout(env: SceneEnv): StoryLayout

    fun DrawScope.background(env: SceneEnv)

    fun DrawScope.lane(route: SceneRoute, mode: LaneMode, env: SceneEnv) {}

    fun DrawScope.courier(route: SceneRoute, u: Float, size: Float, env: SceneEnv)

    /** The host is out of reach from this device only: something stands on its way. */
    fun DrawScope.obstruction(route: SceneRoute, env: SceneEnv)

    fun DrawScope.offline(env: SceneEnv)

    fun DrawScope.source(kind: SourceKind, at: Offset, info: SourceInfo, env: SceneEnv)

    fun DrawScope.vesselDecor(vessel: VesselShape, env: SceneEnv) {}

    /** What stands in front of the vessel: the near slope, the seabed (DEC-070). */
    fun DrawScope.foreground(env: SceneEnv) {}

    /** The colours the picture dissolves into under the words: its sky at the top, its ground at the bottom. */
    fun veil(env: SceneEnv): Pair<Color, Color>

    /** How far to toss the vessel when a courier touches it. */
    fun vesselLift(env: SceneEnv): Float = 0f

    /** A check is running. */
    fun DrawScope.checking(env: SceneEnv)

    /** The moment sound moves to the server's copy; [p] runs 0…1. */
    fun DrawScope.flash(p: Float, layout: StoryLayout, env: SceneEnv)

    /** The pill's glyph in a 20 × 20 box; [fetch] is the server's share while it downloads. */
    fun DrawScope.glyph(kind: GlyphKind, fetch: Float, color: Color)

    /** «бандероль», «172 семени»: what the episode's size is in this story. */
    fun sizeNote(resources: Resources, mb: Float): String

    /** «в 11 тыс. раз быстрее „Вояджера“», «ветер свежий». */
    fun speedNote(resources: Resources, mbps: Float): String
}

/** The stories in the order of the settings list; the first is the default (DEC-069). */
object ConnectionStories {
    val all: List<ConnectionStory> by lazy { listOf(CosmosStory, MeadowStory, SeaStory, PacketStory) }

    val default: ConnectionStory get() = all.first()

    fun byId(id: String?): ConnectionStory = all.firstOrNull { it.id == id } ?: default
}
