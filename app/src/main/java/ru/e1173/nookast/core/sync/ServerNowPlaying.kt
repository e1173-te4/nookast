package ru.e1173.nookast.core.sync

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** One row of the server's listening history: what was listened last, where and when. */
data class ListenedRow(
    val episodeId: Int,
    /** When it was last listened, UTC epoch milliseconds. */
    val listenedAtEpochMs: Long,
    val positionSeconds: Long,
    val completed: Boolean,
    val title: String,
    val podcastName: String,
    val artworkUrl: String,
    val mediaUrl: String,
    val durationSeconds: Long?,
)

/**
 * Now Playing follows the listener across devices (user, 9.11.2026: an episode finished in the car
 * and another one started there; the phone still showed the finished one at its morning mark).
 * When this device is idle and something was listened elsewhere after this device last listened,
 * the newest unfinished episode of the server's history becomes the loaded one.
 */
object ServerNowPlaying {
    /** Clock difference between the server and this device that still counts as «the same time». */
    const val SLACK_MS = 60_000L

    /**
     * The episode to load, or null to keep the local one. [localDirty]: this device holds a listening
     * position the server has not received yet; that is the newest intent, nothing replaces it.
     */
    fun choose(rows: List<ListenedRow>, localListenedAtEpochMs: Long, localDirty: Boolean): ListenedRow? {
        if (localDirty) return null
        val newer = rows.filter { it.listenedAtEpochMs > localListenedAtEpochMs + SLACK_MS }
        if (newer.isEmpty()) return null
        return newer.filter { !it.completed && it.mediaUrl.isNotBlank() }.maxByOrNull { it.listenedAtEpochMs }
    }

    /** Parses `user_history` (newest first, one row per episode; dates are UTC). */
    fun parse(body: String, limit: Int = 40): List<ListenedRow> {
        val root = JSONTokener(body).nextValue()
        val items = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("data") ?: root.optJSONArray("episodes") ?: return emptyList()
            else -> return emptyList()
        }
        return (0 until items.length()).asSequence().mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("episodeid", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val at = utcMillis(item.optString("listendate", item.optString("listen_date", ""))) ?: return@mapNotNull null
            ListenedRow(
                episodeId = id,
                listenedAtEpochMs = at,
                positionSeconds = item.optLong("listenduration", 0L).coerceAtLeast(0L),
                completed = item.optBoolean("completed"),
                title = item.optString("episodetitle", ""),
                podcastName = item.optString("podcastname", ""),
                artworkUrl = item.optString("episodeartwork", ""),
                mediaUrl = item.optString("episodeurl", ""),
                durationSeconds = item.optLong("episodeduration", 0L).takeIf { it > 0 },
            )
        }.distinctBy { it.episodeId }.take(limit).toList()
    }

    internal fun utcMillis(raw: String): Long? {
        if (raw.isBlank()) return null
        runCatching { return java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        return runCatching {
            java.time.LocalDateTime.parse(raw.replace(' ', 'T').take(19)).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }
}
