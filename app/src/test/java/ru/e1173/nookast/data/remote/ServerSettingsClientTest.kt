package ru.e1173.nookast.data.remote

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ServerSettingsClientTest {
    @Test
    fun parsesTheThreeServerAnswers() {
        val settings = parseServerSettings(
            JSONObject("""{"playback_speed":1.3}"""),
            JSONObject("""{"auto_complete_seconds":60}"""),
            JSONObject("""{"timezone":"Europe/Moscow","hour_pref":12,"date_format":"DMY"}"""),
        )
        assertEquals(ServerSettings(1.3, 60, "Europe/Moscow", 12, "DMY"), settings)
    }

    @Test
    fun missingOrBrokenValuesFallBackToPinePodsDefaults() {
        val settings = parseServerSettings(
            JSONObject("""{"playback_speed":0}"""),
            JSONObject("""{"auto_complete_seconds":-5}"""),
            JSONObject("{}"),
        )
        assertEquals(ServerSettings(1.0, 0, "UTC", 24, "ISO"), settings)
    }
}
