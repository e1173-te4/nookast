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

/**
 * The PinePods feed (`/api/data/return_episodes/{user_id}`): recent episodes of every subscribed
 * podcast. The server decides the window (about the last 30 days); the app shows the newest first
 * and cuts the list to the count from Settings (user, 26.10.2026). Read-only: playing or marking an
 * episode goes through the usual queue and outbox paths.
 */
class FeedClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    /** The raw JSON of the feed; [parseFeedEpisodes] turns it into entries. */
    fun fetchJson(session: AuthSession): String {
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val url = requireNotNull(baseUrl.resolve("/api/data/return_episodes/${session.userId}"))
        val request = Request.Builder()
            .url(url)
            .header("Api-Key", session.apiKey)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            return httpClient.newCall(request).execute().use { response ->
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
    }
}

/**
 * Episodes from a feed or playlist JSON ("episodes" array); broken items are skipped. The feed is
 * sorted newest first; a playlist keeps the server's order ([newestFirst] = false).
 */
fun parseFeedEpisodes(json: String, newestFirst: Boolean = true): List<QueueListEntry> {
    val episodes = try {
        JSONObject(json).optJSONArray("episodes")
    } catch (_: JSONException) {
        null
    } ?: return emptyList()
    val result = ArrayList<QueueListEntry>(episodes.length())
    for (index in 0 until episodes.length()) {
        val item = episodes.optJSONObject(index) ?: continue
        val episodeId = item.optInt("episodeid", -1)
        if (episodeId <= 0) continue
        result += QueueListEntry(
            episodeId = episodeId,
            queuePosition = 0,
            episodeTitle = item.optString("episodetitle").takeIf(String::isNotBlank),
            podcastName = item.optString("podcastname").takeIf(String::isNotBlank),
            artworkUrl = item.optString("episodeartwork").takeIf(String::isNotBlank),
            mediaUrl = item.optString("episodeurl").takeIf(String::isNotBlank),
            durationSeconds = item.optInt("episodeduration", 0).takeIf { it > 0 },
            listenDurationSeconds = item.optDouble("listenduration", 0.0).takeIf { it > 0.0 },
            completed = item.optBoolean("completed", false),
            description = item.optString("episodedescription").takeIf(String::isNotBlank),
            publishedAt = item.optString("episodepubdate").takeIf(String::isNotBlank),
            downloaded = item.optBoolean("downloaded", false),
            refreshedAtEpochMs = null,
        )
    }
    // ISO-8601 dates sort as text; episodes without a date go last.
    return (if (newestFirst) result.sortedByDescending { it.publishedAt.orEmpty() } else result)
        .mapIndexed { position, entry -> entry.copy(queuePosition = position) }
}

/** The number of feed episodes Settings allows (user: default 23). */
const val DEFAULT_FEED_EPISODE_COUNT = 23
const val MIN_FEED_EPISODE_COUNT = 5
const val MAX_FEED_EPISODE_COUNT = 100
