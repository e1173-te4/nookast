package ru.e1173.nookast.data.remote

import ru.e1173.nookast.core.network.HttpClients
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress

data class PodcastCatalogItem(
    val podcastId: Int,
    val name: String,
    val artworkUrl: String,
    val description: String,
    val episodeCount: Int?,
    val episodesPlayed: Int,
    val feedUrl: String,
    val author: String,
)

class PodcastCatalogClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    fun fetch(session: AuthSession): List<PodcastCatalogItem> {
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val extendedUrl = requireNotNull(
            baseUrl.resolve("/api/data/return_pods_extra/${session.userId}"),
        )
        val basicUrl = requireNotNull(
            baseUrl.resolve("/api/data/return_pods/${session.userId}"),
        )
        return try {
            parse(get(session, extendedUrl.toString()))
        } catch (error: QueueLoadException) {
            if (!shouldFallbackToBaseCatalog(error.reason)) throw error
            // Some otherwise healthy PinePods installations fail the aggregate statistics query
            // behind return_pods_extra, or time out there while the cheaper base query still
            // works. Authentication errors are never hidden by a second request.
            parse(get(session, basicUrl.toString()))
        }
    }

    internal fun shouldFallbackToBaseCatalog(reason: QueueLoadError): Boolean =
        reason != QueueLoadError.AUTHENTICATION

    private fun get(session: AuthSession, url: String): String {
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
        } catch (error: QueueLoadException) {
            throw error
        } catch (error: IOException) {
            throw QueueLoadException(QueueLoadError.NETWORK, error)
        }
        return body
    }

    internal fun parse(body: String): List<PodcastCatalogItem> {
        try {
            val root = JSONTokener(body).nextValue()
            val items = when (root) {
                is JSONArray -> root
                is JSONObject -> when {
                    root.has("pods") -> root.getJSONArray("pods")
                    root.has("data") -> root.getJSONArray("data")
                    else -> throw JSONException("Podcast array is missing")
                }
                else -> throw JSONException("Unexpected podcast response")
            }
            return buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val podcastId = item.optInt("podcastid", item.optInt("podcast_id", 0))
                    if (podcastId <= 0) continue
                    add(
                        PodcastCatalogItem(
                            podcastId = podcastId,
                            name = item.optNonNullString("podcastname", "name"),
                            artworkUrl = item.optNonNullString("artworkurl", "artwork_url"),
                            description = item.optNonNullString("description"),
                            episodeCount = item.optNullableInt("episodecount")
                                ?.takeIf { it >= 0 },
                            episodesPlayed = item.optInt("episodes_played", 0).coerceAtLeast(0),
                            feedUrl = item.optNonNullString("feedurl", "feed_url"),
                            author = item.optNonNullString("author"),
                        ),
                    )
                }
            }
        } catch (error: JSONException) {
            throw QueueLoadException(QueueLoadError.INVALID_RESPONSE, error)
        }
    }

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)

    private fun JSONObject.optNonNullString(vararg names: String): String =
        names.firstNotNullOfOrNull { name ->
            if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)
        }.orEmpty()
}
