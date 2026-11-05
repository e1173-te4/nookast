package ru.e1173.nookast.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.e1173.nookast.diagnostics.ControlJournal

/**
 * Experiment (EXECUTION_PLAN 2b.3, off by default): announces the current episode with the
 * de-facto `com.android.music.*` broadcasts that some older OEM head-unit shells read instead of
 * media sessions to fill their Now Playing panel.
 *
 * The broadcasts are implicit and readable by any app, so they carry episode and podcast titles
 * only while the user keeps the switch on. The control journal records what was sent without the
 * titles themselves.
 */
class LegacyMetadataBroadcast internal constructor(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val journal: ControlJournal,
) {
    constructor(context: Context, journal: ControlJournal) : this(
        context = context.applicationContext,
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        journal = journal,
    )

    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        journal.record(ControlJournal.Category.BROADCAST, "legacy metadata broadcast enabled=$enabled")
    }

    /** Call on the player thread after a play-state, item or metadata change. */
    fun publish(player: Player, reason: String) {
        if (!_enabled.value) return
        val snapshot = Snapshot.of(player) ?: return
        val extras = snapshot.toExtras(context.packageName)
        listOf(ACTION_META_CHANGED, ACTION_PLAY_STATE_CHANGED).forEach { action ->
            context.sendBroadcast(Intent(action).putExtras(extras))
        }
        journal.record(ControlJournal.Category.BROADCAST) {
            "sent metachanged+playstatechanged reason=$reason id=${snapshot.id} " +
                "playing=${snapshot.playing} durationMs=${snapshot.durationMs} " +
                "positionMs=${snapshot.positionMs} trackLength=${snapshot.track.length} " +
                "artistLength=${snapshot.artist.length}"
        }
    }

    internal data class Snapshot(
        val id: Long,
        val track: String,
        val artist: String,
        val album: String,
        val playing: Boolean,
        val durationMs: Long,
        val positionMs: Long,
    ) {
        fun toExtras(packageName: String): Bundle = Bundle().apply {
            putLong("id", id)
            putString("track", track)
            putString("artist", artist)
            putString("album", album)
            putBoolean("playing", playing)
            putLong("duration", durationMs)
            putLong("position", positionMs)
            putString("package", packageName)
        }

        companion object {
            fun of(player: Player): Snapshot? {
                val item = player.currentMediaItem ?: return null
                val metadata = item.mediaMetadata
                val podcast = metadata.artist?.toString().orEmpty()
                return Snapshot(
                    id = item.mediaId.toLongOrNull() ?: 0L,
                    track = metadata.title?.toString().orEmpty(),
                    artist = podcast,
                    album = podcast,
                    playing = player.isPlaying,
                    durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                )
            }
        }
    }

    companion object {
        const val ACTION_META_CHANGED = "com.android.music.metachanged"
        const val ACTION_PLAY_STATE_CHANGED = "com.android.music.playstatechanged"
        private const val PREFERENCES_NAME = "legacy_metadata_broadcast"
        private const val KEY_ENABLED = "enabled"
    }
}
