package ru.e1173.nookast.data.remote

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress
import ru.e1173.nookast.core.network.HttpClients
import ru.e1173.nookast.data.local.QueueListEntry

/** How a PinePods smart playlist picks its episodes (`playlist_info`), shown as its description. */
data class PlaylistRules(
    val name: String,
    val description: String,
    val includeUnplayed: Boolean,
    val includePartiallyPlayed: Boolean,
    val includePlayed: Boolean,
    val minDurationMinutes: Int?,
    val maxDurationMinutes: Int?,
    val timeFilterHours: Int?,
    val sortOrder: String,
    val podcastCount: Int,
    val maxEpisodes: Int?,
)

data class PlaylistEpisodes(
    val rules: PlaylistRules?,
    val episodes: List<QueueListEntry>,
    val total: Int,
)

/**
 * Episodes of a smart playlist (`/api/data/get_playlist_episodes`, user 27.10.2026: playlists open
 * as a list screen). Read-only; play and marks go through the usual queue and outbox paths.
 */
class PlaylistClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    fun fetch(session: AuthSession, playlistId: Int, limit: Int = PLAYLIST_PAGE): PlaylistEpisodes {
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val url = requireNotNull(baseUrl.resolve("/api/data/get_playlist_episodes"))
            .newBuilder()
            .addQueryParameter("user_id", session.userId.toString())
            .addQueryParameter("playlist_id", playlistId.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", "0")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Api-Key", session.apiKey)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = try {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> throw QueueLoadException(QueueLoadError.AUTHENTICATION)
                    in 300..399 -> throw QueueLoadException(QueueLoadError.SERVER)
                }
                if (!response.isSuccessful) throw QueueLoadException(QueueLoadError.SERVER)
                response.body.string()
            }
        } catch (error: IOException) {
            throw QueueLoadException(QueueLoadError.NETWORK, error)
        }
        return parsePlaylistEpisodes(body)
    }

    companion object {
        const val PLAYLIST_PAGE = 100
    }
}

/** The playlist answer: its rules and its episodes in the server's order. */
fun parsePlaylistEpisodes(json: String): PlaylistEpisodes {
    val root = try {
        JSONObject(json)
    } catch (_: JSONException) {
        return PlaylistEpisodes(rules = null, episodes = emptyList(), total = 0)
    }
    val episodes = parseFeedEpisodes(json, newestFirst = false)
    val info = root.optJSONObject("playlist_info")
    val rules = info?.let {
        // playlist_info gives durations in minutes; the playlists list gives seconds.
        fun minutes(key: String): Int? = it.optInt(key, -1).takeIf { value -> value > 0 }
            ?.let { value -> if (value > 600) value / 60 else value }
        PlaylistRules(
            name = it.optString("name"),
            description = it.optString("description"),
            includeUnplayed = it.optBoolean("include_unplayed", true),
            includePartiallyPlayed = it.optBoolean("include_partially_played", true),
            includePlayed = it.optBoolean("include_played", false),
            minDurationMinutes = minutes("min_duration"),
            maxDurationMinutes = minutes("max_duration"),
            timeFilterHours = it.optInt("time_filter_hours", -1).takeIf { value -> value > 0 },
            sortOrder = it.optString("sort_order"),
            podcastCount = it.optJSONArray("podcast_ids")?.length() ?: 0,
            maxEpisodes = it.optInt("max_episodes", -1).takeIf { value -> value > 0 },
        )
    }
    return PlaylistEpisodes(rules = rules, episodes = episodes, total = root.optInt("total", episodes.size))
}
