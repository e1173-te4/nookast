package ru.e1173.nookast.player

import android.app.Application
import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.e1173.nookast.diagnostics.ControlJournal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LegacyMetadataBroadcastTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun offByDefaultAndPersisted() {
        val preferences = context.getSharedPreferences("legacy_broadcast_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        val journal = journal()
        val broadcast = LegacyMetadataBroadcast(context, preferences, journal)

        assertFalse(broadcast.enabled.value)
        broadcast.setEnabled(true)

        assertTrue(LegacyMetadataBroadcast(context, preferences, journal).enabled.value)
    }

    @Test
    fun extrasUseTheMusicBroadcastKeys() {
        val extras = LegacyMetadataBroadcast.Snapshot(
            id = 42,
            track = "Episode",
            artist = "Podcast",
            album = "Podcast",
            playing = true,
            durationMs = 60_000,
            positionMs = 1_500,
        ).toExtras("ru.e1173.nookast")

        assertEquals(42L, extras.getLong("id"))
        assertEquals("Episode", extras.getString("track"))
        assertEquals("Podcast", extras.getString("artist"))
        assertEquals("Podcast", extras.getString("album"))
        assertTrue(extras.getBoolean("playing"))
        assertEquals(60_000L, extras.getLong("duration"))
        assertEquals(1_500L, extras.getLong("position"))
    }

    private fun journal() = ControlJournal(
        directory = File(context.cacheDir, "journal-test"),
        preferences = context.getSharedPreferences("journal_test", Context.MODE_PRIVATE),
        writer = { it.run() },
    )
}
