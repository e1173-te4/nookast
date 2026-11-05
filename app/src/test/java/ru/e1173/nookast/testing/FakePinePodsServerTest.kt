package ru.e1173.nookast.testing

import android.app.Application
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.data.remote.QueueClient

/**
 * The fake must behave like the real server. Each test mirrors a verified fact from docs/API.md
 * (§2–§5 from the server source, §4a from writes on the test account on 24.10.2026).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FakePinePodsServerTest {
    private lateinit var server: FakePinePodsServer
    private val http = OkHttpClient()
    private val user = FakePinePodsServer.USER_ID

    @Before
    fun setUp() {
        server = FakePinePodsServer().start()
        server.session()
        listOf(10, 20, 30, 40).forEach { server.addEpisode(it, durationSeconds = 1_279) }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun progressOnlyGrowsAndTruncatesFractions() {
        record(10, 110.0)
        record(10, 5.0)
        assertEquals(110, server.episode(10)!!.listenSeconds)

        post("mark_episode_uncompleted", episode(10))
        assertEquals(0, server.episode(10)!!.listenSeconds)
        record(10, 12.9)
        assertEquals(12, server.episode(10)!!.listenSeconds)
    }

    @Test
    fun recordInsideAutoCompleteWindowCompletesImmediately() {
        server.autoCompleteSeconds = 180

        record(10, 1_100.0)

        val episode = server.episode(10)!!
        assertTrue(episode.completed)
        assertEquals(1_279, episode.listenSeconds)
    }

    @Test
    fun completingDoesNotLeaveTheQueue() {
        post("queue_pod", episode(10))
        post("mark_episode_completed", episode(10))

        assertTrue(server.episode(10)!!.completed)
        assertEquals(listOf(10), server.queueIds())
    }

    @Test
    fun queuePodIsIdempotentAndAppends() {
        post("queue_pod", episode(10))
        post("queue_pod", episode(20))
        post("queue_pod", episode(10))

        assertEquals(listOf(10, 20), server.queueIds())
    }

    @Test
    fun partialReorderDuplicatesPositionsAndUnknownIdsConsumeANumber() {
        listOf(10, 20, 30).forEach { post("queue_pod", episode(it)) }

        post("reorder_queue?user_id=$user", JSONObject().put("episode_ids", JSONArray(listOf(30))))
        assertEquals(listOf(1, 1, 2), server.rawQueue().map { it.position }.sorted())

        post("reorder_queue?user_id=$user", JSONObject().put("episode_ids", JSONArray(listOf(999, 30))))
        assertEquals(2, server.rawQueue().single { it.episodeId == 30 }.position)
    }

    @Test
    fun removingAnAbsentEpisodeSucceeds() {
        assertEquals(200, post("remove_queued_pod", episode(10)).first)
    }

    @Test
    fun deletedEpisodeAnswersDifferPerEndpoint() {
        server.deleteEpisode(40)

        val record = post("record_listen_duration", episode(40).put("listen_duration", 10.0))
        assertEquals(500, record.first)
        assertTrue(record.second.contains("foreign key"))
        assertEquals(200, post("mark_episode_completed", episode(40)).first)
        assertEquals(200, post("mark_episode_uncompleted", episode(40)).first)
        assertEquals(404, post("get_episode_metadata", episode(40).put("person_episode", false)).first)

        // queue_pod succeeds and leaves an orphan that the queue read never shows.
        assertEquals(200, post("queue_pod", episode(40)).first)
        assertEquals(1, server.rawQueue().size)
        assertTrue(server.queueIds().isEmpty())
    }

    @Test
    fun revokedKeyGivesServerErrorOnlyOnTwoEndpoints() {
        server.revoke("key-$user")

        assertEquals(500, post("record_listen_duration", episode(10).put("listen_duration", 1.0)).first)
        assertEquals(500, post("mark_episode_uncompleted", episode(10)).first)
        assertEquals(401, post("queue_pod", episode(10)).first)
    }

    @Test
    fun malformedBodiesAreRejectedLikeAxum() {
        assertEquals(422, post("queue_pod", JSONObject().put("episode_id", 10).put("user_id", user)).first)
        assertEquals(400, postRaw("queue_pod", "{not json").first)
    }

    @Test
    fun existingQueueClientParsesTheFake() {
        listOf(30, 10).forEach { post("queue_pod", episode(it)) }
        record(10, 42.0)

        val queue = QueueClient().fetch(server.session())

        assertEquals(listOf(30, 10), queue.map { it.episodeId })
        assertEquals(42.0, queue.last().listenDurationSeconds!!, 0.0)
        assertFalse(queue.first().completed)
    }

    private fun episode(id: Int) = JSONObject()
        .put("episode_id", id)
        .put("user_id", user)
        .put("is_youtube", false)

    private fun record(id: Int, seconds: Double) =
        post("record_listen_duration", episode(id).put("listen_duration", seconds))

    private fun post(endpoint: String, body: JSONObject): Pair<Int, String> =
        postRaw(endpoint, body.toString())

    private fun postRaw(endpoint: String, body: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(server.baseUrl + "api/data/" + endpoint)
            .header("Api-Key", "key-$user")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(request).execute().use { it.code to it.body.string() }
    }
}
