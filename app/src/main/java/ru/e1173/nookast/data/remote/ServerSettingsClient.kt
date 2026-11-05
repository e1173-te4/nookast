package ru.e1173.nookast.data.remote

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress
import ru.e1173.nookast.core.network.HttpClients

/**
 * Account settings PinePods keeps on the server and shares with its web app (user, 27.10.2026:
 * a Settings section for them). Appearance and navigation are deliberately not here: they stay
 * on the device.
 */
data class ServerSettings(
    val playbackSpeed: Double,
    val autoCompleteSeconds: Int,
    val timezone: String,
    val hourPref: Int,
    val dateFormat: String,
)

/**
 * Reads and writes [ServerSettings]. These are account preferences, not listening state, so they
 * go straight to the server while online instead of through the outbox (DEC-036).
 */
class ServerSettingsClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    fun fetch(session: AuthSession): ServerSettings {
        val speed = JSONObject(
            call(session, "POST", "/api/data/get_playback_speed", JSONObject().put("user_id", session.userId).put("podcast_id", JSONObject.NULL)),
        )
        val complete = JSONObject(call(session, "GET", "/api/data/get_auto_complete_seconds/${session.userId}"))
        val time = JSONObject(call(session, "GET", "/api/data/get_time_info?user_id=${session.userId}"))
        return try {
            parseServerSettings(speed, complete, time)
        } catch (error: JSONException) {
            throw QueueLoadException(QueueLoadError.SERVER, error)
        }
    }

    fun setPlaybackSpeed(session: AuthSession, speed: Double) {
        call(session, "POST", "/api/data/user/set_playback_speed", body(session).put("playback_speed", speed))
    }

    fun setAutoCompleteSeconds(session: AuthSession, seconds: Int) {
        call(session, "PUT", "/api/data/update_auto_complete_seconds", body(session).put("seconds", seconds))
    }

    fun setTimezone(session: AuthSession, timezone: String) {
        call(session, "PUT", "/api/data/update_timezone", body(session).put("timezone", timezone))
    }

    fun setHourPref(session: AuthSession, hourPref: Int) {
        call(session, "PUT", "/api/data/update_time_format", body(session).put("hour_pref", hourPref))
    }

    fun setDateFormat(session: AuthSession, dateFormat: String) {
        call(session, "PUT", "/api/data/update_date_format", body(session).put("date_format", dateFormat))
    }

    private fun body(session: AuthSession) = JSONObject().put("user_id", session.userId)

    private fun call(session: AuthSession, method: String, path: String, json: JSONObject? = null): String {
        val baseUrl = ServerAddress.normalize(session.serverUrl).getOrThrow()
        val url = requireNotNull(baseUrl.resolve(path))
        val request = Request.Builder()
            .url(url)
            .header("Api-Key", session.apiKey)
            .header("Accept", "application/json")
            .method(method, json?.toString()?.toRequestBody(JSON))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> throw QueueLoadException(QueueLoadError.AUTHENTICATION)
                }
                if (!response.isSuccessful) throw QueueLoadException(QueueLoadError.SERVER)
                response.body.string()
            }
        } catch (error: IOException) {
            throw QueueLoadException(QueueLoadError.NETWORK, error)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/** The three answers that make up [ServerSettings]; missing fields fall back to PinePods defaults. */
fun parseServerSettings(speed: JSONObject, complete: JSONObject, time: JSONObject): ServerSettings =
    ServerSettings(
        playbackSpeed = speed.optDouble("playback_speed", 1.0).takeUnless { it.isNaN() || it <= 0.0 } ?: 1.0,
        autoCompleteSeconds = complete.optInt("auto_complete_seconds", 0).coerceAtLeast(0),
        timezone = time.optString("timezone").ifBlank { "UTC" },
        hourPref = if (time.optInt("hour_pref", 24) == 12) 12 else 24,
        dateFormat = time.optString("date_format").ifBlank { "ISO" },
    )
