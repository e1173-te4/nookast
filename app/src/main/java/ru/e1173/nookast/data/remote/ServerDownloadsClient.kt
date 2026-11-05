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

/** State of a PinePods background download task (`/api/tasks/{id}`), progress 0..100. */
data class ServerTaskStatus(val state: State, val progress: Double, val message: String?) {
    enum class State { PENDING, RUNNING, SUCCESS, FAILED, UNKNOWN }
}

sealed interface ServerDownloadStart {
    data class Queued(val taskId: String?) : ServerDownloadStart
    data object AlreadyDownloaded : ServerDownloadStart

    /** The administrator disabled server downloads (403). */
    data object Disabled : ServerDownloadStart
}

class ServerDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Episodes downloaded to the PinePods server (DEC-023). Verified against PinePods 0.9.0 sources
 * and the test server on 25.10.2026:
 * - `GET /api/data/download_episode_list` lists the user's server downloads;
 * - `POST /api/data/download_podcast` queues a background task and returns its id;
 * - `GET /api/tasks/{task_id}` reports progress (25..90 % while bytes arrive);
 * - `POST /api/data/delete_episode` removes the file;
 * - `GET /api/data/stream/{id}` serves the stored file in ~0.2 s, but only with the key in the
 *   query, so the stream uses the user's separate RSS key (`/api/data/get_rss_key`). It ignores
 *   `Range` (always 200 with the whole file); the player skips to the position itself.
 */
class ServerDownloadsClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    fun downloadedEpisodeIds(session: AuthSession): Set<Int> {
        val body = execute(
            session,
            Request.Builder().url(
                base(session).resolve("/api/data/download_episode_list")!!.newBuilder()
                    .addQueryParameter("user_id", session.userId.toString())
                    .build(),
            ).get(),
        )
        return parseDownloadedEpisodeIds(body)
    }

    fun requestDownload(session: AuthSession, episodeId: Int): ServerDownloadStart {
        val payload = JSONObject()
            .put("episode_id", episodeId)
            .put("user_id", session.userId)
            .put("is_youtube", false)
            .toString()
        val request = Request.Builder()
            .url(base(session).resolve("/api/data/download_podcast")!!)
            .post(payload.toRequestBody(JSON))
        return try {
            parseDownloadStart(execute(session, request))
        } catch (error: ServerDownloadHttpException) {
            if (error.code == 403) ServerDownloadStart.Disabled else throw error
        }
    }

    fun taskStatus(session: AuthSession, taskId: String): ServerTaskStatus? = try {
        parseTaskStatus(
            execute(
                session,
                Request.Builder().url(base(session).resolve("/api/tasks/$taskId")!!).get(),
            ),
        )
    } catch (error: ServerDownloadHttpException) {
        // Finished tasks may be dropped from the server's task table; the caller re-checks the list.
        if (error.code == 404) null else throw error
    }

    fun deleteDownload(session: AuthSession, episodeId: Int) {
        val payload = JSONObject()
            .put("episode_id", episodeId)
            .put("user_id", session.userId)
            .put("is_youtube", false)
            .toString()
        execute(
            session,
            Request.Builder()
                .url(base(session).resolve("/api/data/delete_episode")!!)
                .post(payload.toRequestBody(JSON)),
        )
    }

    fun rssKey(session: AuthSession): String? {
        val body = execute(
            session,
            Request.Builder().url(
                base(session).resolve("/api/data/get_rss_key")!!.newBuilder()
                    .addQueryParameter("user_id", session.userId.toString())
                    .build(),
            ).get(),
        )
        return runCatching { JSONObject(body).optString("rss_key") }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun base(session: AuthSession): HttpUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()

    private fun execute(session: AuthSession, builder: Request.Builder): String {
        val request = builder
            .header(HttpClients.API_KEY_HEADER, session.apiKey)
            .header("Accept", "application/json")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ServerDownloadHttpException(response.code)
                response.body.string()
            }
        } catch (error: ServerDownloadHttpException) {
            throw error
        } catch (error: IOException) {
            throw ServerDownloadException("network", error)
        }
    }

    internal class ServerDownloadHttpException(val code: Int) : IOException("HTTP $code")

    companion object {
        private val JSON = "application/json".toMediaType()

        internal fun parseDownloadedEpisodeIds(body: String): Set<Int> {
            val episodes = JSONObject(body).optJSONArray("downloaded_episodes") ?: return emptySet()
            return (0 until episodes.length()).mapNotNull { index ->
                episodes.optJSONObject(index)?.optInt("episodeid", 0)?.takeIf { it > 0 }
            }.toSet()
        }

        internal fun parseDownloadStart(body: String): ServerDownloadStart {
            val json = JSONObject(body)
            val taskId = json.optString("task_id").takeIf(String::isNotBlank)
            if (taskId == null && json.optString("detail").contains("already downloaded", ignoreCase = true)) {
                return ServerDownloadStart.AlreadyDownloaded
            }
            return ServerDownloadStart.Queued(taskId)
        }

        internal fun parseTaskStatus(body: String): ServerTaskStatus {
            val json = JSONObject(body)
            val state = when (json.optString("status")) {
                "PENDING" -> ServerTaskStatus.State.PENDING
                "DOWNLOADING" -> ServerTaskStatus.State.RUNNING
                "SUCCESS" -> ServerTaskStatus.State.SUCCESS
                "FAILED" -> ServerTaskStatus.State.FAILED
                else -> ServerTaskStatus.State.UNKNOWN
            }
            return ServerTaskStatus(
                state = state,
                progress = json.optDouble("progress", 0.0).coerceIn(0.0, 100.0),
                message = json.optString("message").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}
