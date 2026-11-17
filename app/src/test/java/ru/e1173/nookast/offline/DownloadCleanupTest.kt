package ru.e1173.nookast.offline

import android.app.Application
import android.content.Context
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.e1173.nookast.core.sync.PinePodsSyncApi
import ru.e1173.nookast.testing.FakePinePodsServer

/** On-device download cleanup (DEC-064) and its hard rule: files on the phone only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DownloadCleanupTest {
    private val hour = 60L * 60 * 1_000
    private val day = 24 * hour
    private var now = 100 * day

    private class MemoryState : CleanupState {
        override var lastRunMs = 0L
        var knownIds = mapOf<Int, Boolean>()
        var playedIds = mapOf<Int, Long>()
        var cleanedIds = listOf<Int>()
        override fun known() = knownIds
        override fun setKnown(known: Map<Int, Boolean>) { knownIds = known }
        override fun playedSince() = playedIds
        override fun setPlayedSince(values: Map<Int, Long>) { playedIds = values }
        override fun cleaned() = cleanedIds
        override fun setCleaned(ids: List<Int>) { cleanedIds = ids }
    }

    private val files = mutableMapOf<Int, CleanupCandidate>()
    private val state = MemoryState()
    private val localPlayedIds = mutableSetOf<Int>()
    private val pendingIds = mutableSetOf<Int>()
    private val remembered = mutableMapOf<Int, Int>()
    private var defaults = CleanupRules(deletePlayed = true, deleteAfterDays = 0)
    private var overrides = mapOf<Int, CleanupOverride>()
    private val open = mutableSetOf<Int>()
    private var server: FakePinePodsServer? = null

    private fun runner(serverPlayed: ServerPlayed? = null, deleteLocal: (Int) -> Boolean = { id -> files.remove(id) != null }) =
        DownloadCleanupRunner(
            downloads = { files.values.map { it.copy(podcastId = it.podcastId ?: remembered[it.episodeId]) } },
            deleteLocal = deleteLocal,
            defaults = { defaults },
            overrides = { overrides },
            state = state,
            serverPlayed = serverPlayed,
            localPlayed = { LocalPlayed(it in localPlayedIds, pending = it in pendingIds) },
            openEpisodeIds = { open.toSet() },
            rememberPodcastId = { episode, podcast -> remembered[episode] = podcast },
            clock = { now },
        )

    private fun download(id: Int, podcastId: Int? = 1, at: Long = now) {
        files[id] = CleanupCandidate(id, podcastId, at)
    }

    private fun answers(vararg played: Pair<Int, Boolean>, podcastId: Int? = 1) = ServerPlayed { id ->
        played.toMap()[id]?.let { ServerAnswer(it, podcastId) }
    }

    @Before
    fun setUp() {
        // Not the first run: downloads seen from now on are new.
        state.lastRunMs = now - day
    }

    @After
    fun tearDown() {
        server?.close()
    }

    @Test
    fun `played download goes an hour after it was first seen played, not before`() = runBlocking<Unit> {
        download(1)
        runner().run(force = true)
        localPlayedIds += 1
        runner().run(force = true)
        now += hour - 1
        assertEquals(emptyList<Int>(), runner().run(force = true))
        now += 1
        assertEquals(listOf(1), runner().run(force = true))
        assertEquals(listOf(1), state.cleaned())
    }

    @Test
    fun `marked unplayed within the hour the download stays`() = runBlocking<Unit> {
        download(1)
        runner().run(force = true)
        localPlayedIds += 1
        runner().run(force = true)
        localPlayedIds -= 1
        now += 2 * hour
        assertEquals(emptyList<Int>(), runner().run(force = true))
        assertTrue(1 in files)
    }

    @Test
    fun `unplayed marked here and not yet sent beats the server's played`() = runBlocking<Unit> {
        download(2)
        runner().run(force = true)
        pendingIds += 2 // UNCOMPLETE still in the outbox
        val server = answers(2 to true)
        runner(server).run(force = true)
        now += 2 * hour
        assertEquals(emptyList<Int>(), runner(server).run(force = true))
        assertTrue(2 in files)
    }

    @Test
    fun `an episode downloaded already played is a re-listen and stays`() = runBlocking<Unit> {
        localPlayedIds += 2
        download(2)
        runner().run(force = true)
        now += 5 * hour
        assertEquals(emptyList<Int>(), runner().run(force = true))
        assertTrue(2 in files)
    }

    @Test
    fun `the open episode and the next one are rechecked right before each deletion`() = runBlocking<Unit> {
        download(3)
        download(4)
        runner().run(force = true)
        localPlayedIds += setOf(3, 4)
        runner().run(force = true)
        now += 2 * hour
        // The player opens 4 while the pass is asking the server about 3.
        val opening = ServerPlayed { id ->
            if (id == 3) open += 4
            null
        }
        defaults = CleanupRules(deletePlayed = true, deleteAfterDays = 0)
        localPlayedIds -= 3
        pendingIds += 4
        assertEquals(emptyList<Int>(), runner(opening).run(force = true))
        assertTrue(4 in files)
        open.clear()
        assertEquals(listOf(4), runner().run(force = true))
    }

    @Test
    fun `delete after days counts from the download and per-podcast overrides win`() = runBlocking<Unit> {
        defaults = CleanupRules(deletePlayed = false, deleteAfterDays = 7)
        overrides = mapOf(2 to CleanupOverride(deleteAfterDays = 0), 3 to CleanupOverride(deleteAfterDays = 1))
        download(10, podcastId = 1, at = now - 7 * day)
        download(11, podcastId = 2, at = now - 30 * day)
        download(12, podcastId = 3, at = now - day)
        download(13, podcastId = 1, at = now - 6 * day)

        val removed = runner().run(force = true)

        assertEquals(setOf(10, 12), removed.toSet())
        assertEquals(setOf(11, 13), files.keys)
        assertEquals(emptyList<Int>(), state.cleaned()) // expiry is not «played»
    }

    @Test
    fun `unknown podcast is kept while overrides exist, and learns its podcast from the server`() = runBlocking<Unit> {
        defaults = CleanupRules(deletePlayed = false, deleteAfterDays = 7)
        overrides = mapOf(2 to CleanupOverride(deleteAfterDays = 0)) // «never» for podcast 2
        download(20, podcastId = null, at = now - 30 * day)
        download(21, podcastId = null, at = now - 30 * day)

        // Offline: nothing tells which podcast they belong to, so neither goes.
        assertEquals(emptyList<Int>(), runner(ServerPlayed { throw java.io.IOException("offline") }).run(force = true))
        assertEquals(setOf(20, 21), files.keys)

        // The server names podcast 2 («never») for both: they stay, and the record remembers it.
        assertEquals(emptyList<Int>(), runner(answers(20 to false, 21 to false, podcastId = 2)).run(force = true))
        assertEquals(mapOf(20 to 2, 21 to 2), remembered)

        // Without any override the defaults apply to an unknown podcast.
        overrides = emptyMap()
        remembered.clear()
        assertEquals(setOf(20, 21), runner().run(force = true).toSet())
    }

    @Test
    fun `server is not asked for rss ids or clean played rows, and one failure does not stop it`() = runBlocking<Unit> {
        download(-5)
        download(30)
        download(31)
        download(32)
        localPlayedIds += 30
        val asked = mutableListOf<Int>()
        val flaky = ServerPlayed { id ->
            asked += id
            if (id == 31) throw java.io.IOException("one bad answer")
            ServerAnswer(played = true, podcastId = 1)
        }
        runner(flaky).run(force = true)
        assertEquals(listOf(31, 32), asked)
        assertTrue(32 in state.playedSince())
    }

    @Test
    fun `runs at most every ten minutes unless forced, and never twice at once`() = runBlocking<Unit> {
        defaults = CleanupRules(deletePlayed = false, deleteAfterDays = 1)
        runner().run(force = true)
        download(20, at = now - 2 * day)
        now += CLEANUP_MIN_INTERVAL_MS - 1
        assertEquals(emptyList<Int>(), runner().run())
        now += 1
        assertEquals(listOf(20), runner().run())

        // Concurrent passes of one runner: the second is a no-op while the first runs.
        defaults = CleanupRules(deletePlayed = true, deleteAfterDays = 0)
        download(21)
        val gate = CompletableDeferred<Unit>()
        val shared = runner(ServerPlayed { gate.await(); null })
        val first = async { shared.run(force = true) }
        yield()
        assertEquals(emptyList<Int>(), shared.run(force = true))
        gate.complete(Unit)
        first.await()
    }

    @Test
    fun `nothing enabled deletes nothing`() = runBlocking<Unit> {
        defaults = CleanupRules(deletePlayed = false, deleteAfterDays = 0)
        download(30, at = now - 365 * day)
        localPlayedIds += 30
        runner().run(force = true)
        now += 2 * hour
        assertEquals(emptyList<Int>(), runner().run(force = true))
    }

    @Test
    fun `played comes from the server when the phone does not know it`() = runBlocking<Unit> {
        val fake = FakePinePodsServer().start().also { server = it }
        fake.addEpisode(40)
        fake.addEpisode(41)
        fake.webMarkCompleted(40) // played on the web; the phone does not know yet
        val api = PinePodsSyncApi()
        val session = fake.session()
        download(40)
        download(41)
        runner().run(force = true)
        val fromServer = ServerPlayed { id -> api.episode(session, id)?.let { ServerAnswer(it.completed, it.podcastId) } }

        runner(fromServer).run(force = true)
        now += hour

        assertEquals(listOf(40), runner(fromServer).run(force = true))
        assertNull(files[40])
    }

    /**
     * HARD RULE (user, 13.11.2026): the phone's cleanup deletes the phone's file only. An episode
     * also downloaded on the server by the user (not a Nookast temporary copy) stays there. Real
     * [EpisodeDownloadManager] and [ServerDownloads] against the fake server.
     */
    @Test
    fun `phone cleanup never deletes the server download of an episode`() = runBlocking<Unit> {
        val context: Context = RuntimeEnvironment.getApplication()
        state.lastRunMs = 0L // the first pass ever: this download predates the feature, not a re-listen
        val fake = FakePinePodsServer().start().also { server = it }
        fake.addEpisode(50)
        fake.webMarkCompleted(50)
        fake.serverDownload(50) // downloaded on the server by the user
        val session = fake.session()
        val serverDownloads = ServerDownloads(context, session = { session })
        serverDownloads.refresh()
        assertTrue(serverDownloads.isOnServer(50))
        assertFalse(serverDownloads.isTemporary(50))

        val file = File(context.filesDir, "50.mp3").apply { writeBytes(ByteArray(64)) }
        context.getSharedPreferences("episode_downloads", Context.MODE_PRIVATE).edit()
            .putString("path_50", file.absolutePath)
            .putBoolean("completed_50", true)
            .putLong("completed_at_50", now)
            .commit()
        val manager = EpisodeDownloadManager(context)
        assertEquals(listOf(50), manager.completedEpisodes().map { it.episodeId })

        val api = PinePodsSyncApi()
        val fromServer = ServerPlayed { id -> api.episode(session, id)?.let { ServerAnswer(it.completed, it.podcastId) } }
        val real = DownloadCleanupRunner(
            downloads = { manager.completedEpisodes().map { CleanupCandidate(it.episodeId, 1, it.completedAtEpochMs) } },
            deleteLocal = manager::remove,
            defaults = { defaults },
            overrides = { emptyMap() },
            state = state,
            serverPlayed = fromServer,
            localPlayed = { LocalPlayed(played = false, pending = false) },
            openEpisodeIds = { emptySet() },
            clock = { now },
        )
        real.run(force = true)
        now += 2 * hour
        assertEquals(listOf(50), real.run(force = true))
        // The app's own after-sync step for temporary copies: it must leave the user's copy alone.
        serverDownloads.cleanupPlayed { _, _ -> true }

        assertFalse(file.exists())
        assertEquals(emptyList<Int>(), manager.completedEpisodes().map { it.episodeId })
        assertEquals(setOf(50), fake.serverDownloadIds())
        assertTrue(fake.calls().none { it.path.contains("delete") || it.path.contains("remove") })
        serverDownloads.refresh()
        assertTrue(serverDownloads.isOnServer(50))
    }

    @Test
    fun `merged ids carry downloads, server-copy marks and the cleanup memory`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "60.mp3").apply { writeBytes(ByteArray(64)) }
        context.getSharedPreferences("episode_downloads", Context.MODE_PRIVATE).edit()
            .putString("path_60", file.absolutePath)
            .putBoolean("completed_60", true)
            .putLong("completed_at_60", now)
            .putInt("podcast_id_60", 7)
            .commit()
        val manager = EpisodeDownloadManager(context)
        assertTrue(manager.remapEpisodeId(60, 6))
        assertEquals(listOf(6), manager.completedEpisodes().map { it.episodeId })
        assertEquals(7, manager.podcastIdOf(6))

        // A temporary mark moves; but never onto the user's own server download.
        context.getSharedPreferences("server_downloads", Context.MODE_PRIVATE).edit()
            .putStringSet("temporary_episode_ids", setOf("70:5", "80:9")).commit()
        val fake = FakePinePodsServer().start().also { server = it }
        fake.serverDownload(8) // the merged twin of 80, downloaded by the user
        val session = fake.session()
        val serverDownloads = ServerDownloads(context, session = { session })
        serverDownloads.refresh()
        serverDownloads.remapEpisodeId(70, 7)
        serverDownloads.remapEpisodeId(80, 8)
        assertTrue(serverDownloads.isTemporary(7))
        assertFalse(serverDownloads.isTemporary(70))
        assertFalse(serverDownloads.isTemporary(8))
        assertFalse(serverDownloads.isTemporary(80))

        val memory = CleanupPreferences(context)
        memory.setKnown(mapOf(90 to true))
        memory.setPlayedSince(mapOf(90 to 123L))
        memory.setCleaned(listOf(91, 90))
        memory.remapEpisodeId(90, 9)
        assertEquals(mapOf(9 to true), memory.known())
        assertEquals(mapOf(9 to 123L), memory.playedSince())
        assertEquals(listOf(91, 9), memory.cleaned())
    }
}
