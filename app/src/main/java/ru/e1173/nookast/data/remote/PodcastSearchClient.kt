package ru.e1173.nookast.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress
import ru.e1173.nookast.core.network.HttpClients

/** A podcast found by a search, ready to be added by its feed. */
data class FoundPodcast(
    val title: String,
    val author: String,
    val artworkUrl: String,
    val feedUrl: String,
    val description: String = "",
    /** The Podcast Index id when the result came from there; the server stores it. */
    val podcastIndexId: Long? = null,
)

/** Where the results came from: the server's own search, or Apple's catalogue when it is down. */
enum class SearchSource { SERVER, APPLE }

data class PodcastSearchResult(val podcasts: List<FoundPodcast>, val source: SearchSource)

/**
 * Finding and adding podcasts (user, 1.11.2026). The search goes through PinePods
 * (`/api/data/proxy_search`, the server's search service, Podcast Index); when the server cannot
 * reach its search service, Apple's public podcast catalogue answers instead, from the device.
 * Adding always goes through the server (`/api/data/add_podcast`), which reads the feed itself.
 */
class PodcastSearchClient(
    private val serverClient: OkHttpClient = HttpClients.pinePods,
    private val publicClient: OkHttpClient = HttpClients.public,
    private val appleSearchUrl: String = APPLE_SEARCH_URL,
) {
    fun search(session: AuthSession, query: String): PodcastSearchResult {
        val fromServer = runCatching { serverSearch(session, query) }.getOrNull()
        if (fromServer != null) return PodcastSearchResult(fromServer, SearchSource.SERVER)
        return PodcastSearchResult(appleSearch(query), SearchSource.APPLE)
    }

    private fun serverSearch(session: AuthSession, query: String): List<FoundPodcast> {
        val url = requireNotNull(ServerAddress.normalize(session.serverUrl).getOrThrow().resolve("/api/data/proxy_search"))
            .newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("index", "podcastindex")
            .build()
        val request = Request.Builder().url(url).header("Api-Key", session.apiKey).header("Accept", "application/json").get().build()
        return serverClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "search service: ${response.code}" }
            parse(response.body.string())
        }
    }

    private fun appleSearch(query: String): List<FoundPodcast> {
        val url = appleSearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
            .addQueryParameter("term", query)
            // The store of the device's country: Russian podcasts are found in the Russian one.
            .addQueryParameter("country", java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "US")
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        return publicClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "catalogue: ${response.code}" }
            parse(response.body.string())
        }
    }

    /** Adds [podcast] for the session's user; the server parses the feed and fetches the episodes. */
    fun add(session: AuthSession, podcast: FoundPodcast) {
        val body = JSONObject()
            .put(
                "podcast_values",
                JSONObject()
                    .put("pod_title", podcast.title)
                    .put("pod_artwork", podcast.artworkUrl)
                    .put("pod_author", podcast.author)
                    .put("categories", JSONObject())
                    .put("pod_description", podcast.description)
                    .put("pod_episode_count", 0)
                    .put("pod_feed_url", podcast.feedUrl)
                    .put("pod_website", "")
                    .put("pod_explicit", false)
                    .put("user_id", session.userId),
            )
            .put("podcast_index_id", podcast.podcastIndexId ?: JSONObject.NULL)
        val url = requireNotNull(ServerAddress.normalize(session.serverUrl).getOrThrow().resolve("/api/data/add_podcast"))
        val request = Request.Builder()
            .url(url)
            .header("Api-Key", session.apiKey)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        serverClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "add_podcast: ${response.code}" }
        }
    }

    companion object {
        const val APPLE_SEARCH_URL = "https://itunes.apple.com/search"
        private const val SEARCH_LIMIT = 30

        /** Apple's `results` or Podcast Index's `feeds`; results without a feed are left out. */
        internal fun parse(body: String): List<FoundPodcast> {
            val root = JSONTokener(body).nextValue() as? JSONObject ?: return emptyList()
            root.optJSONArray("results")?.let { return parseApple(it) }
            root.optJSONArray("feeds")?.let { return parsePodcastIndex(it) }
            return emptyList()
        }

        private fun parseApple(items: JSONArray): List<FoundPodcast> = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val feed = item.optString("feedUrl").takeIf(String::isNotBlank) ?: continue
                add(
                    FoundPodcast(
                        title = item.optString("collectionName").ifBlank { item.optString("trackName") },
                        author = item.optString("artistName"),
                        artworkUrl = item.optString("artworkUrl600").ifBlank { item.optString("artworkUrl100") },
                        feedUrl = feed,
                    ),
                )
            }
        }.distinctBy(FoundPodcast::feedUrl)

        private fun parsePodcastIndex(items: JSONArray): List<FoundPodcast> = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val feed = item.optString("url").takeIf(String::isNotBlank) ?: continue
                add(
                    FoundPodcast(
                        title = item.optString("title"),
                        author = item.optString("author"),
                        artworkUrl = item.optString("artwork").ifBlank { item.optString("image") },
                        feedUrl = feed,
                        description = item.optString("description"),
                        podcastIndexId = item.optLong("id").takeIf { it > 0 },
                    ),
                )
            }
        }.distinctBy(FoundPodcast::feedUrl)
    }
}
