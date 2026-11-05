package ru.e1173.nookast.testing

import java.io.Closeable
import kotlin.math.floor
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.e1173.nookast.auth.AuthSession

/**
 * In-process PinePods 0.9.0 for synchronization tests (EXECUTION_PLAN 3.4).
 *
 * Every behaviour here is taken from docs/API.md, which was verified against the server source
 * (tag 0.9.0, commit 5dec6cf) and by writes on the test account. Deliberately reproduced quirks:
 * max-only progress, immediate auto-complete inside `auto_complete_seconds`, invisible queue
 * orphans for deleted episodes, reorder numbering by array index, unstable order on equal
 * positions, 500 "no rows returned" for a revoked key on two endpoints, and per-endpoint answers
 * for deleted episodes.
 *
 * Faults are injected per path with [failNext]. The state is guarded by the instance monitor;
 * tests read it through [snapshot] helpers.
 */
class FakePinePodsServer : Closeable {
    private val server = MockWebServer()
    private val lock = Any()

    private val episodes = linkedMapOf<Int, Episode>()
    private val queue = mutableListOf<QueueRow>()
    private val saved = mutableListOf<Int>()
    private val keys = mutableMapOf<String, Int>()
    private val revokedKeys = mutableSetOf<String>()
    private val faults = mutableMapOf<String, ArrayDeque<Fault>>()
    private val calls = mutableListOf<Call>()
    private var nextQueueId = 1
    private var queueClock = 0L

    var autoCompleteSeconds: Int = 0
    var version: String = "0.9.0"

    /** Simulates Postgres returning equal positions in a different order on each read. */
    var unstableTieOrder: Boolean = false
    private var tieFlip = false

    data class Episode(
        val id: Int,
        val podcastId: Int,
        val durationSeconds: Int?,
        var listenSeconds: Int? = null,
        var completed: Boolean = false,
        val title: String = "Episode $id",
    )

    data class QueueRow(val queueId: Int, val episodeId: Int, var position: Int, val queueDate: Long)

    /** One HTTP call as the server saw it, for asserting what a client sent. */
    data class Call(val path: String, val body: JSONObject?, val committed: Boolean)

    sealed interface Fault {
        /** The request never reaches the database; the client sees a dropped connection. */
        data object LoseRequest : Fault

        /** The database commits, then the connection drops before the response (S07, S08). */
        data object LoseResponse : Fault

        /** A plain status with an optional body, without touching the state. */
        data class Status(
            val code: Int,
            val body: String = "",
            val contentType: String = "text/plain",
            val headers: Map<String, String> = emptyMap(),
        ) : Fault

        /** A captive portal or proxy page: HTTP 200 with HTML (S16, S54). */
        data object Html200 : Fault

        data class Delay(val millis: Long) : Fault
    }

    fun start(): FakePinePodsServer = apply {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
        server.start()
    }

    override fun close() = server.close()

    val baseUrl: String get() = server.url("/").toString()

    fun session(userId: Int = USER_ID, apiKey: String = "key-$userId"): AuthSession {
        synchronized(lock) { keys[apiKey] = userId }
        return AuthSession(serverUrl = baseUrl, userId = userId, apiKey = apiKey)
    }

    fun revoke(apiKey: String) = synchronized(lock) {
        keys.remove(apiKey)
        revokedKeys += apiKey
    }

    fun addEpisode(id: Int, podcastId: Int = 1, durationSeconds: Int? = 3_600) = synchronized(lock) {
        episodes[id] = Episode(id, podcastId, durationSeconds)
    }

    fun deleteEpisode(id: Int) = synchronized(lock) { episodes.remove(id) }

    fun failNext(path: String, vararg fault: Fault) = synchronized(lock) {
        faults.getOrPut(path) { ArrayDeque() }.addAll(fault)
    }

    // --- Server-side edits by "another client" (web UI or another device) ---

    fun webQueue(episodeId: Int) = synchronized(lock) { queuePod(episodeId) }

    fun webReorder(ids: List<Int>) = synchronized(lock) { reorder(ids) }

    fun webSetProgress(episodeId: Int, seconds: Int) = synchronized(lock) {
        recordListen(episodeId, seconds.toDouble())
    }

    fun webMarkCompleted(episodeId: Int) = synchronized(lock) { markCompleted(episodeId) }

    fun webMarkUncompleted(episodeId: Int) = synchronized(lock) { markUncompleted(episodeId) }

    /** Ticks only the nightly task: episodes with progress inside the auto-complete window. */
    fun runNightlyAutoComplete() = synchronized(lock) {
        if (autoCompleteSeconds <= 0) return@synchronized
        episodes.values.filter { episode ->
            val listen = episode.listenSeconds ?: 0
            val duration = episode.durationSeconds ?: return@filter false
            listen > 0 && duration - listen <= autoCompleteSeconds
        }.forEach { markCompleted(it.id) }
    }

    // --- Inspection ---

    fun queueIds(): List<Int> = synchronized(lock) { visibleQueue().map { it.episodeId } }

    fun rawQueue(): List<QueueRow> = synchronized(lock) { queue.map { it.copy() } }

    fun episode(id: Int): Episode? = synchronized(lock) { episodes[id]?.copy() }

    fun savedIds(): List<Int> = synchronized(lock) { saved.toList() }

    fun calls(): List<Call> = synchronized(lock) { calls.toList() }

    fun callsTo(path: String): List<Call> = calls().filter { it.path == path }

    // --- HTTP ---

    private fun handle(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val fault = synchronized(lock) { faults[path]?.removeFirstOrNull() }
        when (fault) {
            Fault.LoseRequest -> {
                record(path, request, committed = false)
                return dropped()
            }
            is Fault.Status -> {
                record(path, request, committed = false)
                return MockResponse.Builder().code(fault.code)
                    .addHeader("Content-Type", fault.contentType)
                    .apply { fault.headers.forEach { (name, value) -> addHeader(name, value) } }
                    .body(fault.body)
                    .build()
            }
            Fault.Html200 -> {
                record(path, request, committed = false)
                return MockResponse.Builder().code(200)
                    .addHeader("Content-Type", "text/html")
                    .body("<html><body>Sign in to Wi-Fi</body></html>")
                    .build()
            }
            is Fault.Delay -> Thread.sleep(fault.millis)
            Fault.LoseResponse, null -> Unit
        }
        val response = synchronized(lock) { route(path, request) }
        record(path, request, committed = true)
        return if (fault == Fault.LoseResponse) dropped() else response
    }

    private fun record(path: String, request: RecordedRequest, committed: Boolean) {
        val body = request.body?.utf8()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        synchronized(lock) { calls += Call(path, body, committed) }
    }

    private fun route(path: String, request: RecordedRequest): MockResponse {
        if (path == "/api/data/get_pinepods_version") return json(200, JSONObject().put("data", version))

        val apiKey = request.headers["Api-Key"] ?: return error(401, "Unauthorized", "Missing API key")
        val keyOwner = keys[apiKey]
        val revoked = apiKey in revokedKeys
        if (keyOwner == null && !revoked) return error(401, "Unauthorized", "Invalid API key")

        val body = request.body?.utf8().orEmpty()
        val json = if (request.method == "POST") {
            if (request.headers["Content-Type"]?.startsWith("application/json") != true) {
                return text(415, "Expected request with `Content-Type: application/json`")
            }
            try {
                JSONObject(body)
            } catch (_: JSONException) {
                return text(400, "Failed to parse the request body as JSON")
            }
        } else {
            null
        }

        // record_listen_duration and mark_episode_uncompleted ignore the key check result and then
        // fail on fetch_one: a revoked key surfaces as 500, not 401 (docs/API.md §5).
        if (revoked) {
            return when (path) {
                "/api/data/record_listen_duration", "/api/data/mark_episode_uncompleted" ->
                    error(500, "Database error", "no rows returned by a query that expected to return at least one row")
                else -> error(401, "Unauthorized", "Invalid API key")
            }
        }
        val owner = keyOwner!!

        return when (path) {
            "/api/data/get_queued_episodes" -> {
                val userId = request.url.queryParameter("user_id")?.toIntOrNull()
                    ?: return text(400, "Failed to deserialize query string: missing field `user_id`")
                if (userId != owner) return error(403, "Forbidden", "You can only view your own queue")
                json(200, JSONObject().put("data", JSONArray(visibleQueue().map(::queueItemJson))))
            }

            "/api/data/get_auto_complete_seconds/$owner" ->
                json(200, JSONObject().put("auto_complete_seconds", autoCompleteSeconds))

            "/api/data/get_episode_metadata" -> {
                val id = json!!.requireInt("episode_id") ?: return missingField("episode_id")
                if (json.optInt("user_id") != owner) return forbidden()
                val episode = episodes[id] ?: return error(404, "Resource not found", "Episode not found")
                json(200, JSONObject().put("episode", episodeJson(episode)))
            }

            "/api/data/record_listen_duration" -> {
                val id = json!!.requireInt("episode_id") ?: return missingField("episode_id")
                if (json.optInt("user_id") != owner) return forbidden()
                if (!json.has("listen_duration")) return missingField("listen_duration")
                if (id == 0) return detail("Listen duration for episode ID 0 is ignored.")
                if (episodes[id] == null) return foreignKeyViolation()
                recordListen(id, json.getDouble("listen_duration"))
                detail("Listen duration recorded.")
            }

            "/api/data/mark_episode_completed" -> {
                val id = json!!.requireInt("episode_id") ?: return missingField("episode_id")
                if (json.optInt("user_id") != owner) return forbidden()
                markCompleted(id)
                detail("Episode marked as completed.")
            }

            "/api/data/mark_episode_uncompleted" -> {
                val id = json!!.requireInt("episode_id") ?: return missingField("episode_id")
                if (json.optInt("user_id") != owner) return forbidden()
                markUncompleted(id)
                detail("Episode marked as uncompleted.")
            }

            "/api/data/queue_pod" -> {
                val id = json!!.requireInt("episode_id") ?: return missingField("episode_id")
                if (!json.has("is_youtube")) return missingField("is_youtube")
                if (json.optInt("user_id") != owner) return forbidden()
                queuePod(id)
                json(200, JSONObject().put("data", "Episode queued successfully"))
            }

            "/api/data/remove_queued_pod" -> {
                val id = json!!.requireInt("episode_id") ?: return missingField("episode_id")
                if (!json.has("is_youtube")) return missingField("is_youtube")
                if (json.optInt("user_id") != owner) return forbidden()
                removeQueued(id)
                json(200, JSONObject().put("data", "Successfully Removed Episode From Queue"))
            }

            "/api/data/clear_queue" -> {
                if (json!!.optInt("user_id") != owner) return forbidden()
                queue.clear()
                json(200, JSONObject().put("data", "Queue cleared successfully"))
            }

            "/api/data/reorder_queue" -> {
                val userId = request.url.queryParameter("user_id")?.toIntOrNull()
                    ?: return text(400, "Failed to deserialize query string: missing field `user_id`")
                if (userId != owner) return forbidden()
                val ids = json!!.optJSONArray("episode_ids") ?: return missingField("episode_ids")
                reorder((0 until ids.length()).map(ids::getInt))
                json(200, JSONObject().put("message", "Queue reordered successfully"))
            }

            "/api/data/save_episode" -> {
                val id = json!!.requireInt("episode_id") ?: return missingField("episode_id")
                if (!json.has("is_youtube")) return missingField("is_youtube")
                if (json.optInt("user_id") != owner) return forbidden()
                if (episodes[id] == null) return foreignKeyViolation()
                if (id !in saved) saved += id
                detail("Episode saved!")
            }

            "/api/data/remove_saved_episode" -> {
                val id = json!!.requireInt("episode_id") ?: return missingField("episode_id")
                if (!json.has("is_youtube")) return missingField("is_youtube")
                if (json.optInt("user_id") != owner) return forbidden()
                saved.remove(id)
                detail("Saved episode removed.")
            }

            "/api/data/remove_podcast_id" -> {
                if (json!!.optInt("user_id") != owner) return forbidden()
                val podcastId = json.optInt("podcast_id")
                if (podcastId == 0) return error(400, "Bad request", "Invalid podcast ID")
                val removed = episodes.values.filter { it.podcastId == podcastId }.map { it.id }.toSet()
                removed.forEach(episodes::remove)
                queue.removeAll { it.episodeId in removed } // positions are not compacted
                saved.removeAll { it in removed }
                json(200, JSONObject().put("success", true))
            }

            else -> error(404, "Resource not found", "No route for $path")
        }
    }

    // --- Semantics (callers hold the lock) ---

    private fun recordListen(id: Int, sent: Double) {
        val episode = episodes[id] ?: return
        val value = floor(sent).toInt()
        if (value >= 0 && value > (episode.listenSeconds ?: -1)) episode.listenSeconds = value
        // Immediate auto-complete compares the SENT value, not the stored maximum.
        val duration = episode.durationSeconds ?: 0
        if (autoCompleteSeconds > 0 && duration > 0 &&
            (duration - sent <= autoCompleteSeconds || sent >= duration)
        ) {
            markCompleted(id)
        }
    }

    private fun markCompleted(id: Int) {
        val episode = episodes[id] ?: return
        val duration = episode.durationSeconds ?: return // NULL duration: silent no-op
        episode.completed = true
        episode.listenSeconds = duration
    }

    private fun markUncompleted(id: Int) {
        val episode = episodes[id] ?: return
        episode.completed = false
        episode.listenSeconds = 0
    }

    private fun queuePod(id: Int) {
        // No foreign key on EpisodeQueue: a deleted episode becomes an invisible orphan row.
        if (queue.any { it.episodeId == id }) return
        val position = (queue.maxOfOrNull { it.position } ?: 0) + 1
        queue += QueueRow(nextQueueId++, id, position, ++queueClock)
    }

    private fun removeQueued(id: Int) {
        val row = queue.firstOrNull { it.episodeId == id } ?: return
        queue.remove(row)
        queue.filter { it.position > row.position }.forEach { it.position -= 1 }
    }

    private fun reorder(ids: List<Int>) {
        // Positions follow the array index; unknown IDs are skipped but consume their number;
        // a duplicated ID ends with its last index.
        ids.forEachIndexed { index, id ->
            queue.filter { it.episodeId == id }.forEach { it.position = index + 1 }
        }
    }

    private fun visibleQueue(): List<QueueRow> {
        val visible = queue.filter { episodes.containsKey(it.episodeId) } // INNER JOIN Episodes
        val byPosition = visible.groupBy { it.position }.toSortedMap()
        tieFlip = !tieFlip
        return byPosition.values.flatMap { rows ->
            if (unstableTieOrder && tieFlip) rows.reversed() else rows
        }
    }

    private fun queueItemJson(row: QueueRow): JSONObject {
        val episode = episodes.getValue(row.episodeId)
        return episodeJson(episode)
            .put("queueposition", row.position)
            .put("queuedate", "2026-10-24T00:00:%02d".format(row.queueDate % 60))
    }

    private fun episodeJson(episode: Episode): JSONObject = JSONObject()
        .put("episodeid", episode.id)
        .put("podcastid", episode.podcastId)
        .put("episodetitle", episode.title)
        .put("podcastname", "Podcast ${episode.podcastId}")
        .put("episodepubdate", "2026-10-01T00:00:00")
        .put("episodedescription", "")
        .put("episodeartwork", "")
        .put("episodeurl", "https://media.invalid/${episode.id}.mp3")
        .put("episodeduration", episode.durationSeconds ?: JSONObject.NULL)
        .put("listenduration", episode.listenSeconds ?: JSONObject.NULL)
        .put("completed", episode.completed)
        .put("is_queued", queue.any { it.episodeId == episode.id })
        .put("saved", episode.id in saved)
        .put("downloaded", false)

    // --- Responses ---

    private fun detail(message: String) = json(200, JSONObject().put("detail", message))

    private fun forbidden() = error(403, "Forbidden", "You can only access your own data")

    private fun foreignKeyViolation() = error(
        500,
        "Database error",
        "insert or update on table \"UserEpisodeHistory\" violates foreign key constraint " +
            "\"UserEpisodeHistory_episodeid_fkey\"",
    )

    private fun missingField(name: String) = text(
        422,
        "Failed to deserialize the JSON body into the target type: missing field `$name`",
    )

    private fun error(code: Int, error: String, message: String) = json(
        code,
        JSONObject().put("error", error).put("message", message).put("status_code", code),
    )

    private fun json(code: Int, body: JSONObject) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body.toString())
        .build()

    private fun text(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "text/plain; charset=utf-8")
        .body(body)
        .build()

    private fun dropped() = MockResponse.Builder()
        .onResponseStart(SocketEffect.CloseSocket())
        .build()

    private fun JSONObject.requireInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    companion object {
        const val USER_ID = 2
    }
}
