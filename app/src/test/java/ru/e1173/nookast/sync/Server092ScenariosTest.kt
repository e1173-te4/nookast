package ru.e1173.nookast.sync

import android.app.Application
import kotlinx.coroutines.runBlocking
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
import ru.e1173.nookast.testing.FakePinePodsServer
import ru.e1173.nookast.testing.SyncDevice

/**
 * Server changes of PinePods 0.9.1/0.9.2 (tag 0.9.2, caed396) that touch synchronization:
 * where `queue_pod` puts an episode, completion leaving the queue, and merged episode ids
 * (FINDINGS V-01, V-02).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Server092ScenariosTest {
    private lateinit var server: FakePinePodsServer
    private val devices = mutableListOf<SyncDevice>()

    @Before
    fun setUp() {
        server = FakePinePodsServer().start()
        server.version = "0.9.2"
        server.queue091 = true
        (1..9).forEach { server.addEpisode(it) }
        server.addEpisode(40)
    }

    @After
    fun tearDown() {
        devices.forEach(SyncDevice::close)
        server.close()
    }

    private fun device(name: String) =
        SyncDevice(RuntimeEnvironment.getApplication(), server, name).also { devices += it }

    /** Web adds on 0.9.1+ go to the top, so the queue below reads 3, 2, 1. */
    private fun webQueue(vararg ids: Int) = ids.forEach(server::webQueue)

    // --- Queue position (item 1) ---

    @Test
    fun addAtTheEndStaysAtTheEndAlthoughTheServerPutsItOnTop() = runBlocking {
        val phone = device("phone")
        webQueue(1, 2, 3)
        phone.sync()
        assertEquals(listOf(3, 2, 1), phone.localQueue())

        phone.add(9)
        phone.sync()

        assertEquals(listOf(3, 2, 1, 9), server.queueIds())
        assertEquals(listOf(3, 2, 1, 9), phone.localQueue())
    }

    @Test
    fun addUnderAnotherDevicesEpisodeKeepsNookastsPlace() = runBlocking {
        val phone = device("phone")
        webQueue(1, 2, 3)
        server.nowPlayingElsewhere = 2
        phone.sync()

        phone.add(9, index = 3)
        phone.sync()

        assertEquals(listOf(3, 2, 1, 9), server.queueIds())
    }

    @Test
    fun episodeFinishedElsewhereIsNotBroughtBackByTheFullOrder() = runBlocking {
        val phone = device("phone")
        webQueue(1, 2, 3)
        phone.sync()
        phone.move(1, 0) // offline drag: 1, 3, 2
        server.webMarkCompleted(1) // another device finishes it: 0.9.1+ removes it from the queue
        phone.add(5)

        phone.sync()

        assertEquals(listOf(3, 2, 5), server.queueIds())
        assertFalse(1 in phone.localQueue())
        val reorder = server.callsTo(REORDER).last().body!!
        val sent = reorder.getJSONArray("episode_ids").let { ids -> (0 until ids.length()).map(ids::getInt) }
        assertFalse(1 in sent)
        assertTrue(reorder.has("episodes"))
    }

    @Test
    fun episodeStartedOnTopElsewhereKeepsItsPlace() = runBlocking {
        val phone = device("phone")
        webQueue(1, 2, 3) // 3, 2, 1
        phone.sync()
        server.webReorder(listOf(1, 3, 2)) // web/mobile `queue_bump`: the started episode goes first
        phone.add(9)

        phone.sync()

        assertEquals(listOf(1, 3, 2, 9), server.queueIds())
    }

    @Test
    fun ownCompletionLeavesTheQueueOnce() = runBlocking {
        val phone = device("phone")
        webQueue(4)
        phone.sync()

        phone.complete(4)
        phone.syncRepeatedly(2)

        assertEquals(emptyList<Int>(), server.queueIds())
        assertEquals(0, phone.pendingCount())
    }

    // --- Merged episode ids (item 2) ---

    @Test
    fun removalWithAnOldIdReachesTheMergedEpisode() = runBlocking {
        val phone = device("phone")
        webQueue(40)
        phone.sync()
        server.mergeEpisode(40, into = 4)

        phone.remove(40)
        phone.sync()

        assertEquals(emptyList<Int>(), server.queueIds())
        assertEquals(emptyList<Int>(), phone.localQueue())
        assertEquals(0, phone.pendingCount())
        assertEquals(listOf(mapOf(40 to 4)), phone.merges)
    }

    @Test
    fun addWithAnOldIdCreatesNoDuplicateAndSettles() = runBlocking {
        val phone = device("phone")
        webQueue(4, 1) // 1, 4
        phone.sync()
        server.mergeEpisode(40, into = 4)

        phone.add(40) // a stale page still shows the old id
        val report = phone.sync()

        assertTrue(report.failures.isEmpty())
        assertEquals(listOf(1, 4), server.queueIds())
        assertEquals(2, server.rawQueue().size)
        assertEquals(listOf(1, 4), phone.localQueue())
        assertEquals(0, phone.pendingCount())
    }

    @Test
    fun progressWithAnOldIdLandsOnTheMergedEpisodeWithoutATwin() = runBlocking {
        val phone = device("phone")
        phone.listen(40, fromSeconds = 100, toSeconds = 200)
        server.mergeEpisode(40, into = 4)

        phone.sync()

        assertEquals(200, server.episode(4)!!.listenSeconds)
        assertNull(server.episode(40))
        assertEquals(emptyList<Int>(), server.queueIds())
        assertNull(phone.repository.progress(40))
        assertEquals(200L, phone.repository.progress(4)!!.positionMs / 1_000)
        assertEquals(0, phone.pendingCount())
    }

    @Test
    fun twoPendingRowsForOneMergedEpisodeKeepTheNewerIntent() = runBlocking {
        val phone = device("phone")
        phone.listen(40, fromSeconds = 100, toSeconds = 600) // older intent, old id
        phone.listen(4, fromSeconds = 2_000, toSeconds = 2_400) // newer intent, merged id
        server.mergeEpisode(40, into = 4)
        phone.repository.remapEpisodeIds(mapOf(40 to 4))

        assertNull(phone.repository.progress(40))
        assertEquals(2_400L, phone.repository.progress(4)!!.positionMs / 1_000)
        phone.sync()
        assertEquals(2_400, server.episode(4)!!.listenSeconds)
        assertEquals(0, phone.pendingCount())
    }

    private companion object {
        const val REORDER = "/api/data/reorder_queue"
        const val RECORD = "/api/data/record_listen_duration"
    }
}
