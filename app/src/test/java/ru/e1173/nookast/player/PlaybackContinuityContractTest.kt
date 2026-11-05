package ru.e1173.nookast.player

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackContinuityContractTest {
    @Test
    fun `room queue synchronization edits the playlist without reprepare`() {
        val source = locateMainSource("player/PlaybackViewModel.kt").readText()
        val synchronizeQueue = source
            .substringAfter("fun synchronizeQueue")
            .substringBefore("fun togglePlayPause")

        assertTrue(synchronizeQueue.contains("planPlaylistEdits"))
        assertTrue(synchronizeQueue.contains("moveMediaItem"))
        assertFalse(synchronizeQueue.contains("setMediaItems"))
        assertFalse(synchronizeQueue.contains("prepare()"))
        assertFalse(synchronizeQueue.contains("pause()"))
    }

    @Test
    fun `service delegates focus calls and noisy output handling to exoplayer`() {
        val source = locateMainSource("player/PlaybackService.kt").readText()

        assertTrue(source.contains("setAudioAttributes(audioAttributes, true)"))
        assertTrue(source.contains("setHandleAudioBecomingNoisy(true)"))
        assertTrue(source.contains("registerDefaultNetworkCallback"))
    }

    private fun locateMainSource(relative: String): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .map { root -> File(root, "src/main/java/ru/e1173/nookast/$relative") }
            .plus(
                generateSequence(File(workingDirectory)) { it.parentFile }
                    .map { root -> File(root, "app/src/main/java/ru/e1173/nookast/$relative") },
            )
            .firstOrNull(File::isFile)
            ?: error("$relative was not found from the test working directory")
    }
}
