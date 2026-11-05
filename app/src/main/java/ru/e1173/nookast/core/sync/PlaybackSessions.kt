package ru.e1173.nookast.core.sync

import java.util.UUID

/**
 * The playback session the user is engaged in right now (docs/SYNC.md §5.2). PlaybackService opens
 * a session when an episode really starts playing and closes it when playback stops for good.
 * Passive progress carries the session ID; the engine treats intents from other sessions as stale
 * and checks them against the server before sending (§5.5).
 */
object PlaybackSessions {
    @Volatile
    var current: String? = null
        private set

    @Volatile
    private var episodeId: Int? = null

    /** Starts a new session for [episode] unless one is already open for it. */
    fun engage(episode: Int): String {
        val open = current
        if (open != null && episodeId == episode) return open
        return UUID.randomUUID().toString().also {
            current = it
            episodeId = episode
        }
    }

    /** The open session if it is for [episode]; another episode's session never vouches (review 3.6). */
    fun currentFor(episode: Int): String? = current.takeIf { episodeId == episode }

    fun close() {
        current = null
        episodeId = null
    }
}
