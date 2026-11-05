package ru.e1173.nookast.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Response shapes of PinePods 0.9.0 (sources and the test server, 25.10.2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ServerDownloadsClientTest {
    @Test
    fun parsesTheDownloadedEpisodeList() {
        val body = """{"downloaded_episodes":[{"podcastid":35,"episodeid":2699},{"episodeid":5812}]}"""
        assertEquals(setOf(2699, 5812), ServerDownloadsClient.parseDownloadedEpisodeIds(body))
    }

    @Test
    fun recognisesQueuedAndAlreadyDownloaded() {
        assertEquals(
            ServerDownloadStart.Queued("abc"),
            ServerDownloadsClient.parseDownloadStart("""{"detail":"Podcast episode download has been queued","task_id":"abc"}"""),
        )
        assertEquals(
            ServerDownloadStart.AlreadyDownloaded,
            ServerDownloadsClient.parseDownloadStart("""{"detail":"Content already downloaded."}"""),
        )
    }

    @Test
    fun mapsTaskStates() {
        val running = ServerDownloadsClient.parseTaskStatus("""{"status":"DOWNLOADING","progress":47.5,"message":"Downloading"}""")
        assertEquals(ServerTaskStatus.State.RUNNING, running.state)
        assertEquals(47.5, running.progress, 0.0)
        assertEquals(ServerTaskStatus.State.SUCCESS, ServerDownloadsClient.parseTaskStatus("""{"status":"SUCCESS","progress":100}""").state)
        assertEquals(ServerTaskStatus.State.FAILED, ServerDownloadsClient.parseTaskStatus("""{"status":"FAILED","progress":20}""").state)
    }
}
