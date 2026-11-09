package ru.e1173.nookast.data.remote

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress
import ru.e1173.nookast.core.network.HttpClients

/** Why the server could not read a feed (PinePods 0.9.1+ `FeedErrorKind`). */
enum class ServerFeedError { GONE, AUTH, NOT_FEED, UNREACHABLE, SERVER, THROTTLED, OTHER }

/** A subscription whose feed the server keeps failing to read. */
data class ServerFeedProblem(
    val podcastId: Int,
    val kind: ServerFeedError,
    val failingSinceEpochMs: Long?,
    /** Past the server's own reporting threshold (a day, or three tries for a feed that is gone). */
    val reported: Boolean,
    /** The server refused the address itself: it resolves to a home-network address (0.9.1+ guard). */
    val privateAddress: Boolean = false,
)

/**
 * A podcast's feed on the server (user, 6.11.2026: change the RSS address of a subscription from
 * its card). Online calls: the address is the server's, and its answer — can it read the new feed —
 * is what the user waits for, so there is nothing to keep in the outbox.
 */
class PodcastFeedClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    /** Points [podcastId] at [feedUrl]; throws with the server's message when it refuses. */
    fun changeFeedUrl(session: AuthSession, podcastId: Int, feedUrl: String) {
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("podcast_id", podcastId)
            .put("feed_url", feedUrl)
        val response = call(session, "/api/data/update_podcast_info", "PUT", body.toString())
        val json = runCatching { JSONObject(response) }.getOrNull()
        if (json != null && json.has("success") && !json.optBoolean("success")) {
            throw FeedChangeRefused(json.optString("message"))
        }
    }

    /**
     * Has the server read [podcastId] at once and returns its problem afterwards, or null when the
     * read worked. Null too on a server older than 0.9.1, which has no such call.
     */
    fun refreshNow(session: AuthSession, podcastId: Int): ServerFeedProblem? {
        val body = try {
            call(session, "/api/data/feed_problems/$podcastId/refresh", "POST", "{}")
        } catch (error: QueueLoadException) {
            if (error.reason == QueueLoadError.SERVER) return null else throw error
        } catch (_: FeedChangeRefused) {
            return null
        }
        return parseFeedProblems(body).firstOrNull { it.podcastId == podcastId }
    }

    /** The subscriptions the server keeps failing to read (0.9.1+); empty on older servers. */
    fun problems(session: AuthSession): List<ServerFeedProblem> = try {
        parseFeedProblems(call(session, "/api/data/feed_problems", "GET", null))
    } catch (error: QueueLoadException) {
        if (error.reason == QueueLoadError.SERVER) emptyList() else throw error
    }

    private fun call(session: AuthSession, path: String, method: String, json: String?): String {
        val baseUrl: HttpUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val request = Request.Builder()
            .url(requireNotNull(baseUrl.resolve(path)))
            .header("Api-Key", session.apiKey)
            .header("Accept", "application/json")
            .method(method, json?.toRequestBody(JSON))
            .build()
        try {
            return httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> throw QueueLoadException(QueueLoadError.AUTHENTICATION)
                }
                val text = response.body.string()
                if (!response.isSuccessful) {
                    // A refusal with a reason (a duplicate feed, an address the server will not fetch).
                    val message = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                    if (response.code in 400..499 && !message.isNullOrBlank()) throw FeedChangeRefused(message)
                    throw QueueLoadException(QueueLoadError.SERVER)
                }
                text
            }
        } catch (error: QueueLoadException) {
            throw error
        } catch (error: FeedChangeRefused) {
            throw error
        } catch (error: IOException) {
            throw QueueLoadException(QueueLoadError.NETWORK, error)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/** The server refused the change, with its own words. */
class FeedChangeRefused(val serverMessage: String) : Exception(serverMessage)

internal fun parseFeedProblems(body: String): List<ServerFeedProblem> {
    val feeds = runCatching { JSONObject(body).optJSONArray("feeds") }.getOrNull() ?: return emptyList()
    return (0 until feeds.length()).mapNotNull { index ->
        val item = feeds.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optInt("podcast_id").takeIf { it > 0 } ?: return@mapNotNull null
        ServerFeedProblem(
            podcastId = id,
            kind = when (item.optString("kind")) {
                "gone" -> ServerFeedError.GONE
                "auth" -> ServerFeedError.AUTH
                "not_feed" -> ServerFeedError.NOT_FEED
                "unreachable" -> ServerFeedError.UNREACHABLE
                "server" -> ServerFeedError.SERVER
                "throttled" -> ServerFeedError.THROTTLED
                else -> ServerFeedError.OTHER
            },
            failingSinceEpochMs = item.optLong("failing_since", 0L).takeIf { it > 0 }?.times(1_000),
            reported = item.optBoolean("reported"),
            privateAddress = item.optString("error").contains("private/reserved address", ignoreCase = true),
        )
    }
}
