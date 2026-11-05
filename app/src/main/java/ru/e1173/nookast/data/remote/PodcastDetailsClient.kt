package ru.e1173.nookast.data.remote

import ru.e1173.nookast.core.network.optText
import ru.e1173.nookast.core.network.HttpClients
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress

data class PodcastEpisodeProgress(
    val episodeId: Int,
    val title: String,
    val publishedAt: String,
    val durationSeconds: Int,
    val listenedSeconds: Double,
    val completed: Boolean,
    val podcastName: String = "",
    val description: String = "",
    val artworkUrl: String = "",
    val mediaUrl: String = "",
    val downloaded: Boolean = false,
    /** Stable Room identity; non-null for cached PinePods and public-RSS rows. */
    val cacheKey: String? = null,
    /** Null when this episode is known only from public RSS. */
    val pinePodsEpisodeId: Int? = episodeId.takeIf { it > 0 },
) {
    val fraction: Float
        get() = when {
            completed -> 1f
            durationSeconds <= 0 -> 0f
            else -> (listenedSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
        }
}

data class PodcastDetails(
    val podcastId: Int,
    val name: String,
    val artworkUrl: String,
    val description: String,
    val author: String,
    val categories: List<String>,
    val episodeCount: Int?,
    val episodesPlayed: Int?,
    val recentEpisodes: List<PodcastEpisodeProgress>,
)

class PodcastDetailsClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    fun fetch(session: AuthSession, episodeId: Int): PodcastDetails {
        require(episodeId > 0)
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val podcastId = resolvePodcastId(session, baseUrl, episodeId)
        return fetchByPodcastId(session, podcastId)
    }

    fun fetchByPodcastId(session: AuthSession, podcastId: Int): PodcastDetails {
        require(podcastId > 0)
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val detailsBody = get(
            session,
            baseUrl.resolve("/api/data/get_podcast_details")!!.newBuilder()
                .addQueryParameter("user_id", session.userId.toString())
                .addQueryParameter("podcast_id", podcastId.toString())
                .build(),
        )
        val episodesBody = fetchEpisodesBody(
            session = session,
            baseUrl = baseUrl,
            podcastId = podcastId,
            limit = RECENT_EPISODE_LIMIT,
            offset = 0,
        )
        return parseDetails(podcastId, detailsBody, episodesBody)
    }

    fun fetchLatestEpisode(session: AuthSession, podcastId: Int): PodcastEpisodeProgress? {
        require(podcastId > 0)
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        return parseEpisodes(
            fetchEpisodesBody(session, baseUrl, podcastId, limit = 1, offset = 0),
        ).firstOrNull()
    }

    fun fetchAllEpisodes(session: AuthSession, podcastId: Int): List<PodcastEpisodeProgress> {
        require(podcastId > 0)
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val result = mutableListOf<PodcastEpisodeProgress>()
        var offset = 0
        while (offset < MAX_EPISODES_PER_PODCAST) {
            val page = parseEpisodes(
                fetchEpisodesBody(
                    session = session,
                    baseUrl = baseUrl,
                    podcastId = podcastId,
                    limit = EPISODE_PAGE_SIZE,
                    offset = offset,
                ),
            )
            result += page
            if (page.size < EPISODE_PAGE_SIZE) break
            offset += EPISODE_PAGE_SIZE
        }
        return result.distinctBy(PodcastEpisodeProgress::episodeId)
    }

    private fun fetchEpisodesBody(
        session: AuthSession,
        baseUrl: HttpUrl,
        podcastId: Int,
        limit: Int,
        offset: Int,
    ): String = get(
        session,
        baseUrl.resolve("/api/data/podcast_episodes")!!.newBuilder()
            .addQueryParameter("user_id", session.userId.toString())
            .addQueryParameter("podcast_id", podcastId.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sort_by", "episodepubdate")
            .addQueryParameter("sort_order", "desc")
            .build(),
    )

    private fun resolvePodcastId(
        session: AuthSession,
        baseUrl: HttpUrl,
        episodeId: Int,
    ): Int {
        val body = get(
            session,
            baseUrl.resolve("/api/data/get_podcast_id_from_ep_id")!!.newBuilder()
                .addQueryParameter("episode_id", episodeId.toString())
                .addQueryParameter("user_id", session.userId.toString())
                .addQueryParameter("is_youtube", "false")
                .build(),
        )
        return parsePodcastId(body)
            ?: throw QueueLoadException(QueueLoadError.INVALID_RESPONSE)
    }

    private fun get(session: AuthSession, url: HttpUrl): String {
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
        } catch (error: QueueLoadException) {
            throw error
        } catch (error: IOException) {
            throw QueueLoadException(QueueLoadError.NETWORK, error)
        }
    }

    internal fun parsePodcastId(body: String): Int? {
        body.trim().toIntOrNull()?.let { return it.takeIf { value -> value > 0 } }
        return runCatching { findPodcastId(JSONObject(body)) }.getOrNull()
    }

    private fun findPodcastId(value: Any?): Int? = when (value) {
        is Number -> value.toInt().takeIf { it > 0 }
        is String -> value.toIntOrNull()?.takeIf { it > 0 }
        is JSONObject -> {
            listOf("podcast_id", "podcastid", "id")
                .firstNotNullOfOrNull { key ->
                    if (value.has(key) && !value.isNull(key)) findPodcastId(value.get(key)) else null
                }
                ?: listOf("data", "podcast", "details")
                    .firstNotNullOfOrNull { key ->
                        if (value.has(key) && !value.isNull(key)) findPodcastId(value.get(key)) else null
                    }
        }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { findPodcastId(value.get(it)) }
        else -> null
    }

    internal fun parseDetails(
        podcastId: Int,
        detailsBody: String,
        episodesBody: String,
    ): PodcastDetails {
        try {
            val details = unwrapObject(JSONObject(detailsBody))
            val categories = details.optJSONObject("categories")?.let { objectValue ->
                buildList {
                    val keys = objectValue.keys()
                    while (keys.hasNext()) {
                        objectValue.optText(keys.next())
                            .takeIf(String::isNotBlank)
                            ?.let(::add)
                    }
                }.distinct()
            }.orEmpty()
            val recent = parseEpisodes(episodesBody)
            return PodcastDetails(
                podcastId = podcastId,
                name = details.optText("podcastname", details.optText("name")),
                artworkUrl = details.optText("artworkurl", details.optText("artwork_url")),
                description = details.optText("description"),
                author = details.optText("author"),
                categories = categories,
                episodeCount = details.optNullableInt("episodecount"),
                episodesPlayed = details.optNullableInt("episodes_played"),
                recentEpisodes = recent,
            )
        } catch (error: JSONException) {
            throw QueueLoadException(QueueLoadError.INVALID_RESPONSE, error)
        }
    }

    internal fun parseEpisodes(body: String): List<PodcastEpisodeProgress> {
        val episodesRoot = JSONObject(body)
        val episodes = when (val value = episodesRoot.opt("episodes")) {
            is JSONArray -> value
            else -> episodesRoot.optJSONArray("data") ?: JSONArray()
        }
        return buildList {
            for (index in 0 until episodes.length()) {
                val item = episodes.optJSONObject(index) ?: continue
                val id = item.optInt("Episodeid", item.optInt("episodeid", 0))
                if (id <= 0) continue
                add(
                    PodcastEpisodeProgress(
                        episodeId = id,
                        title = item.optText("Episodetitle", item.optText("episodetitle")),
                        publishedAt = item.optText(
                            "Episodepubdate",
                            item.optText("episodepubdate"),
                        ),
                        durationSeconds = item.optInt(
                            "Episodeduration",
                            item.optInt("episodeduration", 0),
                        ).coerceAtLeast(0),
                        listenedSeconds = optDouble(
                            item,
                            "Listenduration",
                            "listenduration",
                        ).coerceAtLeast(0.0),
                        completed = item.optBoolean(
                            "Completed",
                            item.optBoolean("completed", false),
                        ),
                        podcastName = item.optText("podcastname"),
                        description = item.optText(
                            "Episodedescription",
                            item.optText("episodedescription"),
                        ),
                        artworkUrl = item.optText(
                            "Episodeartwork",
                            item.optText("episodeartwork"),
                        ),
                        mediaUrl = item.optText(
                            "Episodeurl",
                            item.optText("episodeurl"),
                        ),
                        downloaded = item.optBoolean(
                            "downloaded",
                            item.optBoolean("Downloaded", false),
                        ),
                    ),
                )
            }
        }
    }

    private fun unwrapObject(root: JSONObject): JSONObject {
        listOf("data", "podcast", "details").forEach { key ->
            when (val nested = root.opt(key)) {
                is JSONObject -> return nested
                is JSONArray -> nested.optJSONObject(0)?.let { return it }
            }
        }
        return root
    }

    private fun optDouble(value: JSONObject, vararg keys: String): Double =
        keys.firstNotNullOfOrNull { key ->
            if (!value.has(key) || value.isNull(key)) null else value.optDouble(key)
        } ?: 0.0

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)

    private companion object {
        const val RECENT_EPISODE_LIMIT = 100
        const val EPISODE_PAGE_SIZE = 100
        const val MAX_EPISODES_PER_PODCAST = 10_000
    }
}
