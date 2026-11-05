package ru.e1173.nookast.player

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceThreadingContractTest {
    @Test
    fun `player polling stays on main and persistence stays on IO`() {
        val source = locatePlaybackService().readText()

        assertTrue(source.contains("Dispatchers.Main.immediate"))
        assertTrue(source.contains("periodicProgressJob = playerScope.launch"))
        assertTrue(source.contains("private fun persistMutation"))
        assertTrue(source.contains("override fun onTaskRemoved"))
        assertTrue(source.contains("persistFinalProgressSynchronously()"))
        assertTrue(source.contains("recordCompletionAndRemoveFromQueue"))
        assertTrue(source.contains("ioScope.launch"))
        assertFalse(source.contains("periodicProgressJob = ioScope.launch"))
    }

    private fun locatePlaybackService(): File {
        val relative = "src/main/java/ru/e1173/nookast/player/PlaybackService.kt"
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .flatMap { root -> sequenceOf(File(root, relative), File(root, "app/$relative")) }
            .firstOrNull(File::isFile)
            ?: error("PlaybackService.kt was not found from the test working directory")
    }
}
