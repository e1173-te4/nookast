package ru.e1173.nookast.player

import android.media.AudioDeviceInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audio rework З14 g, h, i: per-podcast storage, the speaker route, shorter pauses and P0. */
class SoundSettingsTest {
    private class Memory : SoundKeyValues {
        val map = HashMap<String, Any>()
        override fun int(key: String, default: Int) = map[key] as? Int ?: default
        override fun float(key: String) = map[key] as? Float
        override fun bool(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun edit(block: SoundKeyValueEditor.() -> Unit) {
            object : SoundKeyValueEditor {
                override fun put(key: String, value: Int) { map[key] = value }
                override fun put(key: String, value: Float) { map[key] = value }
                override fun put(key: String, value: Boolean) { map[key] = value }
                override fun remove(key: String) { map.remove(key) }
            }.block()
        }
    }

    private val a = SoundSlot("account", 1)
    private val b = SoundSlot("account", 2)
    private val none = SoundSlot("account", null)

    // g)

    @Test
    fun `values follow the podcast across a queue transition`() {
        val store = SoundSettingsStore(Memory())
        store.setPodcast(a, PodcastSound(volumeDb = 18, mono = true))
        store.setGlobal(GlobalSound(clarity = 2))
        val first = soundParameters(store.global(), store.podcast(a), speakerRoute = false, compare = false, episodeKey = "1#1")
        val second = soundParameters(store.global(), store.podcast(b), speakerRoute = false, compare = false, episodeKey = "2#2")
        assertEquals(18, first.volumeDb)
        assertTrue(first.mono)
        assertEquals(0, second.volumeDb)
        assertFalse(second.mono)
        assertEquals(2, second.clarity)
    }

    @Test
    fun `values survive a process restart and stay per account`() {
        val memory = Memory()
        SoundSettingsStore(memory).setPodcast(a, PodcastSound(volumeDb = 7))
        val restarted = SoundSettingsStore(memory)
        assertEquals(7, restarted.podcast(a).volumeDb)
        assertEquals(0, restarted.podcast(SoundSlot("other", 1)).volumeDb)
    }

    @Test
    fun `episodes without a podcast share one slot`() {
        val store = SoundSettingsStore(Memory())
        store.setPodcast(none, PodcastSound(volumeDb = 5))
        assertEquals(5, store.podcast(SoundSlot("account", null)).volumeDb)
        assertEquals(0, store.podcast(a).volumeDb)
    }

    @Test
    fun `remembered gain is taken first, then blended 0_7 old and 0_3 new`() {
        val store = SoundSettingsStore(Memory())
        assertNull(store.podcast(a).rememberedGainDb)
        store.rememberGain(a, before = null, episodeAverageDb = 10f)
        assertEquals(10f, store.podcast(a).rememberedGainDb!!, 0.001f)
        store.rememberGain(a, before = 10f, episodeAverageDb = 0f)
        assertEquals(7f, store.podcast(a).rememberedGainDb!!, 0.001f)
        // The next episode starts from it.
        assertEquals(7f, soundParameters(store.global(), store.podcast(a), false, false, "x").startGainDb, 0.001f)
    }

    @Test
    fun `values are clamped to their ranges`() {
        val store = SoundSettingsStore(Memory())
        store.setPodcast(a, PodcastSound(volumeDb = 99))
        store.setGlobal(GlobalSound(clarity = 9, depth = -2, speakerTrimDb = 40))
        assertEquals(24, store.podcast(a).volumeDb)
        assertEquals(3, store.global().clarity)
        assertEquals(0, store.global().depth)
        assertEquals(12, store.global().speakerTrimDb)
    }

    @Test
    fun `untouched settings leave the processor off on every route`() {
        // A user who never opens the popover hears exactly the original (test a covers the bits).
        val store = SoundSettingsStore(Memory())
        for (route in listOf(false, true)) {
            assertFalse(soundParameters(store.global(), store.podcast(a), route, false, "e").active)
            assertFalse(soundParameters(store.global(), store.podcast(none), route, false, null).active)
        }
    }

    // h)

    @Test
    fun `the speaker profile follows the route`() {
        val speaker = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val earpiece = AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        assertTrue(speakerProfileActive(listOf(earpiece, speaker), wideVehicleProfile = false))
        assertFalse(speakerProfileActive(listOf(speaker, AudioDeviceInfo.TYPE_WIRED_HEADPHONES), false))
        assertFalse(speakerProfileActive(listOf(speaker, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP), false))
        assertFalse(speakerProfileActive(listOf(speaker, AudioDeviceInfo.TYPE_BLE_HEADSET), false))
        assertFalse(speakerProfileActive(listOf(speaker, AudioDeviceInfo.TYPE_USB_HEADSET), false))
        assertFalse("never in the wide in-vehicle profile", speakerProfileActive(listOf(speaker), wideVehicleProfile = true))
        assertFalse(speakerProfileActive(emptyList(), false))
    }

    @Test
    fun `the speaker trim and high-pass only apply on that route`() {
        val global = GlobalSound(speakerTrimDb = 6)
        assertTrue(soundParameters(global, PodcastSound(), true, false, null).active)
        assertFalse(soundParameters(global, PodcastSound(), false, false, null).active)
    }

    // i)

    @Test
    fun `shorter pauses keep the saved and synced position on the media timeline`() {
        val service = source("PlaybackService.kt")
        val controller = source("SoundController.kt")
        // Media3 skips silence inside the sink and reports positions in media time; progress is
        // read from the player's position only, never computed from elapsed wall-clock time.
        assertTrue(controller.contains("skipSilenceEnabled = global.shortenPauses"))
        assertTrue(service.contains("val positionMs = (explicitPositionMs ?: player.currentPosition)"))
        assertFalse(Regex("positionMs\\s*[+=]+.*elapsedRealtime").containsMatchIn(service))
        assertFalse(Regex("positionMs\\s*[+=]+.*currentTimeMillis").containsMatchIn(service))
    }

    private fun source(name: String): String {
        val relative = "src/main/java/ru/e1173/nookast/player/$name"
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .flatMap { root -> sequenceOf(File(root, relative), File(root, "app/$relative")) }
            .firstOrNull(File::isFile)
            ?.readText()
            ?: error("$name was not found")
    }
}
