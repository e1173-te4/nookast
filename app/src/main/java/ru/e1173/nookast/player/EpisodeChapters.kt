package ru.e1173.nookast.player

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress
import ru.e1173.nookast.core.network.HttpClients

/**
 * Podcasting 2.0 chapters of an episode through PinePods (`fetch_podcasting_2_data` reads the
 * feed's `podcast:chapters` JSON): titles, start times and the chapter pictures that Now Playing
 * shows instead of the cover (user, 29.10.2026). Chapters marked `toc: false` stay hidden.
 */
internal object EpisodeChapters {
    fun fetch(session: AuthSession, episodeId: Int): List<PlaybackChapter> {
        val url = requireNotNull(
            ServerAddress.normalize(session.serverUrl).getOrThrow().resolve("/api/data/fetch_podcasting_2_data"),
        ).newBuilder()
            .addQueryParameter("episode_id", episodeId.toString())
            .addQueryParameter("user_id", session.userId.toString())
            .build()
        val request = Request.Builder().url(url).header("Api-Key", session.apiKey).header("Accept", "application/json").get().build()
        return HttpClients.pinePods.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            parse(response.body.string())
        }
    }

    internal fun parse(body: String): List<PlaybackChapter> {
        val root = runCatching { JSONTokener(body).nextValue() }.getOrNull()
        val items: JSONArray = when (root) {
            is JSONObject -> root.optJSONArray("chapters") ?: root.optJSONObject("data")?.optJSONArray("chapters")
            is JSONArray -> root
            else -> null
        } ?: return emptyList()
        val chapters = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                if (item.has("toc") && !item.optBoolean("toc", true)) continue
                val start = item.optDouble("startTime", -1.0)
                if (start < 0) continue
                val end = item.optDouble("endTime", -1.0)
                add(
                    PlaybackChapter(
                        startTimeMs = (start * 1_000).toLong(),
                        endTimeMs = (end * 1_000).toLong().takeIf { end > start },
                        title = item.optString("title").trim(),
                        imageUrl = item.optString("img").trim().takeIf { it.startsWith("http") },
                    ),
                )
            }
        }
        return chapters.sortedBy(PlaybackChapter::startTimeMs).distinctBy(PlaybackChapter::startTimeMs)
    }
}
