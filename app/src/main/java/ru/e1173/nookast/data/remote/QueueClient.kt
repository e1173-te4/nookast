package ru.e1173.nookast.data.remote

import ru.e1173.nookast.core.network.optText
import ru.e1173.nookast.core.network.HttpClients
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress

data class RemoteQueuedEpisode(
    val episodeId: Int,
    val queuePosition: Int?,
    val episodeTitle: String,
    val podcastName: String,
    val artworkUrl: String,
    val mediaUrl: String,
    val durationSeconds: Int,
    val listenDurationSeconds: Double?,
    val completed: Boolean,
    val description: String,
    val publishedAt: String,
    val downloaded: Boolean,
)

enum class QueueLoadError {
    AUTHENTICATION,
    NETWORK,
    SERVER,
    INVALID_RESPONSE,
}

class QueueLoadException(
    val reason: QueueLoadError,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

class QueueClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    fun fetch(session: AuthSession): List<RemoteQueuedEpisode> {
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val endpoint = requireNotNull(baseUrl.resolve("/api/data/get_queued_episodes"))
            .newBuilder()
            .addQueryParameter("user_id", session.userId.toString())
            .build()
        val request = Request.Builder()
            .url(endpoint)
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
                parseResponse(response.body.string())
            }
        } catch (error: QueueLoadException) {
            throw error
        } catch (error: IOException) {
            throw QueueLoadException(QueueLoadError.NETWORK, error)
        } catch (error: JSONException) {
            throw QueueLoadException(QueueLoadError.INVALID_RESPONSE, error)
        }
    }

    internal fun parseResponse(body: String): List<RemoteQueuedEpisode> {
        val data = JSONObject(body).getJSONArray("data")
        return buildList(data.length()) {
            for (index in 0 until data.length()) {
                val item = data.getJSONObject(index)
                val episodeId = item.getInt("episodeid")
                if (episodeId <= 0) throw JSONException("Invalid episode id")
                add(
                    index to
                    RemoteQueuedEpisode(
                        episodeId = episodeId,
                        queuePosition = item.optNullableInt("queueposition"),
                        episodeTitle = item.optText("episodetitle"),
                        podcastName = item.optText("podcastname"),
                        artworkUrl = item.optText("episodeartwork"),
                        mediaUrl = item.optText("episodeurl"),
                        durationSeconds = item.optInt("episodeduration").coerceAtLeast(0),
                        listenDurationSeconds = item.optNullableDouble("listenduration"),
                        completed = item.optBoolean("completed"),
                        description = item.optText("episodedescription"),
                        publishedAt = item.optText("episodepubdate"),
                        downloaded = item.optBoolean("downloaded"),
                    ),
                )
            }
        }.sortedBy { (serverIndex, episode) -> episode.queuePosition ?: serverIndex }
            .map { it.second }
    }
}

private fun JSONObject.optNullableInt(name: String): Int? =
    if (isNull(name) || !has(name)) null else getInt(name)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name) || !has(name)) null else getDouble(name)
