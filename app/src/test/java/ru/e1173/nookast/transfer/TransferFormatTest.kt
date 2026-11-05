package ru.e1173.nookast.transfer

import android.app.Application
import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.data.local.DeferredEpisodeIntentEntity
import ru.e1173.nookast.data.local.DeferredEpisodeIntentKind
import ru.e1173.nookast.sync.ProgressCause

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TransferFormatTest {
    private val bundle = TransferBundle(
        formatVersion = TransferFormat.VERSION,
        createdAtMs = 1_000L,
        sourcePackage = "ru.e1173.nookast.debug",
        sourceVersion = "0.8.0-dev (55)",
        serverUrl = "https://pods.example",
        userId = 7,
        preferences = mapOf(
            "display_preferences" to mapOf(
                "theme_palette" to "WINTER",
                "sky_morning" to false,
                "feed_episode_count" to 120,
                "hide_played_playlist_ids" to setOf("3", "1"),
            ),
            "playback_preferences" to mapOf("ahead_cache_mb" to 64, "last_tick" to 5_000_000_000L, "gain" to 1.5f),
        ),
        deferredIntents = listOf(
            DeferredEpisodeIntentEntity(
                episodeCacheKey = "rss:feed:guid",
                kind = DeferredEpisodeIntentKind.PROGRESS,
                positionMs = 61_000L,
                durationMs = 3_600_000L,
                progressCause = ProgressCause.entries.first(),
                sequence = 42L,
                createdAtEpochMs = 900L,
            ),
            DeferredEpisodeIntentEntity(
                episodeCacheKey = "rss:feed:guid",
                kind = DeferredEpisodeIntentKind.QUEUE,
                desiredState = true,
                destinationIndex = 2,
                sequence = 43L,
                createdAtEpochMs = 950L,
            ),
        ),
    )

    @Test
    fun aBundleSurvivesTheRoundTrip() {
        assertEquals(bundle, TransferFormat.decode(TransferFormat.encode(bundle)))
    }

    @Test
    fun anAccountlessBundleKeepsNulls() {
        val decoded = TransferFormat.decode(TransferFormat.encode(bundle.copy(serverUrl = null, userId = null, deferredIntents = emptyList())))
        assertNull(decoded.serverUrl)
        assertNull(decoded.userId)
    }

    @Test
    fun theFileNeverCarriesTheAccountKey() {
        val text = TransferFormat.encode(bundle)
        assertFalse(text.contains("api_key"))
        assertFalse(text.contains("encrypted_session"))
    }

    @Test
    fun otherFilesAndUnknownSettingsFilesAreRejected() {
        expect(TransferError.NOT_A_TRANSFER) { TransferFormat.decode("""{"hello":1}""") }
        expect(TransferError.NOT_A_TRANSFER) { TransferFormat.decode("not json") }
        expect(TransferError.NEWER_FORMAT) {
            TransferFormat.decode(TransferFormat.encode(bundle).replace("\"format\": 1", "\"format\": 99"))
        }
        val foreign = TransferFormat.encode(bundle).replace("\"playback_preferences\"", "\"encrypted_session\"")
        assertEquals(setOf("display_preferences"), TransferFormat.decode(foreign).preferences.keys)
    }

    @Test
    fun onlyTheTravellingSettingsAreReadAndWritten() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("encrypted_session", Context.MODE_PRIVATE).edit().putString("session_ciphertext", "x").commit()
        TransferFormat.writePreferences(context, bundle.preferences + ("encrypted_session" to mapOf("session_ciphertext" to "y")))
        val read = TransferFormat.readPreferences(context)
        assertEquals(bundle.preferences, read)
        assertEquals("x", context.getSharedPreferences("encrypted_session", Context.MODE_PRIVATE).getString("session_ciphertext", null))
        assertTrue(read.keys.all { it in TransferFormat.PREFERENCE_FILES })
    }

    private fun expect(error: TransferError, block: () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (actual: TransferException) {
            assertEquals(error, actual.reason)
        }
    }
}
