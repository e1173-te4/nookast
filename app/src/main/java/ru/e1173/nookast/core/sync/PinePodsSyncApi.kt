package ru.e1173.nookast.core.sync

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress
import ru.e1173.nookast.core.network.HttpClients
import ru.e1173.nookast.core.network.optText

/** Error taxonomy of docs/SYNC.md §8. */
enum class SyncFailure {
    /** 401/403: stop, ask for a new sign-in, keep data. */
    AUTH,

    /** 500 "no rows returned": a revoked key on endpoints that ignore the key check (S-21). */
    AUTH_SUSPECT,

    /** HTML or a redirect while the network is not validated: captive portal (S54). */
    NETWORK_LOGIN,
    TRANSIENT,
    PERMANENT,
    PROTOCOL,
}

class SyncCallException(
    val failure: SyncFailure,
    val httpCode: Int? = null,
    /** Short, sanitized server text for the log; never contains the API key or query strings. */
    val excerpt: String? = null,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : Exception("$failure${httpCode?.let { " HTTP $it" } ?: ""}", cause)

/** Progress of one episode as the server reports it. */
data class ServerEpisode(
    val episodeId: Int,
    val listenSeconds: Long,
    val completed: Boolean,
    val durationSeconds: Long?,
    val queued: Boolean,
)

/** A queue row before canonicalisation (§6.2). */
data class ServerQueueRow(
    val episodeId: Int,
    val position: Int,
    val queueDate: String,
    val listenSeconds: Long?,
    val completed: Boolean,
    /** Display data for the queue cache; null in tests that only care about order. */
    val metadata: QueueEpisodeMetadata? = null,
)

data class QueueEpisodeMetadata(
    val title: String,
    val podcastName: String,
    val artworkUrl: String,
    val mediaUrl: String,
    val durationSeconds: Int,
    val description: String,
    val publishedAt: String,
    val downloaded: Boolean,
)

/**
 * The PinePods calls the synchronization engine makes, with the classification of every answer.
 * Semantics: docs/API.md (verified against PinePods 0.9.0). Blocking; call on an IO dispatcher.
 */
class PinePodsSyncApi(
    private val client: OkHttpClient = HttpClients.pinePods,
    /** Whether the current network passed Android's validation; false suggests a captive portal. */
    private val networkValidated: () -> Boolean = { true },
) {
    fun recordListen(session: AuthSession, episodeId: Int, seconds: Long) {
        require(episodeId > 0) { "RSS-only playback IDs are never sent to the server" }
        post(session, "record_listen_duration", episodeBody(session, episodeId).put("listen_duration", seconds), "detail")
    }

    fun markCompleted(session: AuthSession, episodeId: Int) =
        post(session, "mark_episode_completed", episodeBody(session, episodeId), "detail")

    fun markUncompleted(session: AuthSession, episodeId: Int) =
        post(session, "mark_episode_uncompleted", episodeBody(session, episodeId), "detail")

    /** Control read (I5). Null when the server answers 404 (deleted or not the user's episode). */
    fun episode(session: AuthSession, episodeId: Int): ServerEpisode? {
        val body = episodeBody(session, episodeId).put("person_episode", false)
        val json = try {
            call(session, request(session, "get_episode_metadata").post(body.toJsonBody()))
        } catch (error: SyncCallException) {
            if (error.httpCode == 404) return null
            throw error
        }
        val episode = json.optJSONObject("episode") ?: throw protocol("no episode object")
        return ServerEpisode(
            episodeId = episodeId,
            listenSeconds = episode.optLongOrNull("listenduration") ?: 0,
            completed = episode.optBoolean("completed"),
            durationSeconds = episode.optLongOrNull("episodeduration"),
            queued = episode.optBoolean("is_queued"),
        )
    }

    fun queue(session: AuthSession): List<ServerQueueRow> {
        val url = endpoint(session, "get_queued_episodes").newBuilder()
            .addQueryParameter("user_id", session.userId.toString())
            .build()
        val json = call(session, authorized(session, url).get())
        val data = json.optJSONArray("data") ?: throw protocol("no data array")
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("episodeid", 0).takeIf { it > 0 } ?: return@mapNotNull null
            ServerQueueRow(
                episodeId = id,
                position = item.optInt("queueposition", Int.MAX_VALUE),
                queueDate = item.optText("queuedate"),
                listenSeconds = item.optLongOrNull("listenduration"),
                completed = item.optBoolean("completed"),
                metadata = QueueEpisodeMetadata(
                    title = item.optText("episodetitle"),
                    podcastName = item.optText("podcastname"),
                    artworkUrl = item.optText("episodeartwork"),
                    mediaUrl = item.optText("episodeurl"),
                    durationSeconds = item.optInt("episodeduration", 0).coerceAtLeast(0),
                    description = item.optText("episodedescription"),
                    publishedAt = item.optText("episodepubdate"),
                    downloaded = item.optBoolean("downloaded"),
                ),
            )
        }
    }

    fun queuePod(session: AuthSession, episodeId: Int) =
        post(session, "queue_pod", episodeBody(session, episodeId), "data")

    fun removeQueued(session: AuthSession, episodeId: Int) =
        post(session, "remove_queued_pod", episodeBody(session, episodeId), "data")

    /** Always the complete desired list: partial lists duplicate positions on the server. */
    fun reorder(session: AuthSession, episodeIds: List<Int>) {
        require(episodeIds.all { it > 0 } && episodeIds.distinct().size == episodeIds.size)
        val url = endpoint(session, "reorder_queue").newBuilder()
            .addQueryParameter("user_id", session.userId.toString())
            .build()
        val json = call(
            session,
            authorized(session, url).post(JSONObject().put("episode_ids", JSONArray(episodeIds)).toJsonBody()),
        )
        acknowledge(json, "message")
    }

    fun saveEpisode(session: AuthSession, episodeId: Int) =
        post(session, "save_episode", episodeBody(session, episodeId), "detail")

    fun removeSavedEpisode(session: AuthSession, episodeId: Int) =
        post(session, "remove_saved_episode", episodeBody(session, episodeId), "detail")

    fun removePodcast(session: AuthSession, podcastId: Int) = post(
        session,
        "remove_podcast_id",
        JSONObject().put("user_id", session.userId).put("podcast_id", podcastId).put("is_youtube", false),
        "success",
    )

    fun autoCompleteSeconds(session: AuthSession): Int {
        val json = call(session, authorized(session, endpoint(session, "get_auto_complete_seconds/${session.userId}")).get())
        if (!json.has("auto_complete_seconds")) throw protocol("no auto_complete_seconds")
        return json.optInt("auto_complete_seconds", 0)
    }

    /** Server version, or null when the answer is not the expected JSON (used by PROTOCOL, §8). */
    fun version(session: AuthSession): String? = runCatching {
        call(session, Request.Builder().url(endpoint(session, "get_pinepods_version")).get())
            .optText("data").takeIf(String::isNotBlank)
    }.getOrNull()

    /**
     * The real length of an episode whose feed gave none or 0 (PinePods 0.9.1+ corrects it from what
     * clients decode): progress percentages and statistics then add up. Best effort.
     */
    fun reportDuration(session: AuthSession, episodeId: Int, seconds: Int) {
        val body = JSONObject().put("episode_id", episodeId).put("new_duration", seconds).put("is_youtube", false)
        call(session, request(session, "update_episode_duration").post(body.toJsonBody()))
    }

    // --- HTTP ---

    private fun post(session: AuthSession, path: String, body: JSONObject, ackField: String) {
        acknowledge(call(session, request(session, path).post(body.toJsonBody())), ackField)
    }

    private fun acknowledge(json: JSONObject, field: String) {
        if (!json.has(field) || json.isNull(field)) throw protocol("missing $field")
        if (field == "success" && !json.optBoolean(field)) throw protocol("success=false")
    }

    private fun episodeBody(session: AuthSession, episodeId: Int) = JSONObject()
        .put("episode_id", episodeId)
        .put("user_id", session.userId)
        .put("is_youtube", false)

    private fun endpoint(session: AuthSession, path: String): HttpUrl {
        val base = ServerAddress.normalize(session.serverUrl).getOrElse {
            throw SyncCallException(SyncFailure.PERMANENT, excerpt = "invalid server address", cause = it)
        }
        return requireNotNull(base.resolve("/api/data/$path"))
    }

    private fun request(session: AuthSession, path: String) = authorized(session, endpoint(session, path))

    private fun authorized(session: AuthSession, url: HttpUrl) = Request.Builder()
        .url(url)
        .header(HttpClients.API_KEY_HEADER, session.apiKey)
        .header("Accept", "application/json")

    private fun call(session: AuthSession, builder: Request.Builder): JSONObject {
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (error: IOException) {
            throw SyncCallException(SyncFailure.TRANSIENT, excerpt = error.javaClass.simpleName, cause = error)
        }
        return response.use { classify(it) }
    }

    private fun classify(response: Response): JSONObject {
        val code = response.code
        val contentType = response.header("Content-Type").orEmpty()
        val text = runCatching { response.body.string() }.getOrElse {
            throw SyncCallException(SyncFailure.TRANSIENT, code, "body read failed", cause = it)
        }
        val json = runCatching { JSONObject(text) }.getOrNull()
        val message = json?.optText("message").orEmpty()
        val excerpt = sanitize(message.ifBlank { text })
        when {
            code in 200..299 -> {
                if (json != null) return json
                val looksLikePortal = contentType.contains("html", ignoreCase = true) ||
                    text.trimStart().startsWith("<")
                throw SyncCallException(
                    if (looksLikePortal && !networkValidated()) SyncFailure.NETWORK_LOGIN else SyncFailure.PROTOCOL,
                    code,
                    excerpt,
                )
            }
            code in 300..399 -> throw SyncCallException(
                if (!networkValidated()) SyncFailure.NETWORK_LOGIN else SyncFailure.PROTOCOL,
                code,
                excerpt,
            )
            code == 401 || code == 403 -> throw SyncCallException(SyncFailure.AUTH, code, excerpt)
            code == 404 -> throw SyncCallException(SyncFailure.PERMANENT, code, excerpt)
            code == 400 && message.contains("Invalid podcast ID") ->
                throw SyncCallException(SyncFailure.PERMANENT, code, excerpt)
            code == 400 || code == 415 || code == 422 -> throw SyncCallException(SyncFailure.PROTOCOL, code, excerpt)
            code == 500 && message.contains("no rows returned", ignoreCase = true) ->
                throw SyncCallException(SyncFailure.AUTH_SUSPECT, code, excerpt)
            code == 500 && message.contains("foreign key", ignoreCase = true) ->
                throw SyncCallException(SyncFailure.PERMANENT, code, excerpt)
            else -> throw SyncCallException(
                SyncFailure.TRANSIENT,
                code,
                excerpt,
                retryAfterSeconds = response.header("Retry-After")?.trim()?.toLongOrNull(),
            )
        }
    }

    private fun protocol(detail: String) = SyncCallException(SyncFailure.PROTOCOL, excerpt = detail)

    private fun JSONObject.toJsonBody() = toString().toRequestBody(JSON)

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeUnless(Double::isNaN)?.toLong()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        /** Keeps a short excerpt for the sync log without keys or query strings. */
        fun sanitize(text: String): String = text
            .replace(Regex("(?i)(api[_-]?key[=: \"]+)[^&\\s\"]+"), "$1<redacted>")
            .replace(Regex("\\?[^\\s\"]*"), "?…")
            .take(160)
    }
}
