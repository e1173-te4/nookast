package ru.e1173.nookast.core.network

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.data.remote.SyncRequestException
import ru.e1173.nookast.data.remote.validateSuccessBody

// Robolectric provides Android's org.json, whose optString() differs from the JVM artifact.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class JsonTextTest {
    @Test
    fun jsonNullAndMissingKeysUseTheFallback() {
        val json = JSONObject("""{"a":null,"b":"value","c":""}""")

        assertEquals("", json.optText("a"))
        assertEquals("fallback", json.optText("a", "fallback"))
        assertEquals("fallback", json.optText("missing", "fallback"))
        assertEquals("value", json.optText("b"))
        assertNull(json.optTextOrNull("a"))
        assertNull(json.optTextOrNull("c"))
        assertEquals("", JSONArray("""[null, "x"]""").optText(0))
    }

    @Test
    fun acknowledgementWithNullFieldIsNotTreatedAsSuccess() {
        try {
            validateSuccessBody("""{"detail":null}""", responseField = "detail")
            fail("a null acknowledgement must not confirm an outbox row")
        } catch (_: SyncRequestException) {
            // expected
        }
    }
}
