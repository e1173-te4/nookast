package ru.e1173.nookast.transfer

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import ru.e1173.nookast.data.local.DeferredEpisodeIntentEntity
import ru.e1173.nookast.data.local.DeferredEpisodeIntentKind
import ru.e1173.nookast.sync.ProgressCause

/**
 * Moving from the `.debug` package to the release one (plan 8.2, Q-03): the two are different
 * apps, so what lives only on the device travels in a file the user saves and opens again.
 *
 * What travels: the display and playback settings, and the RSS intents still waiting for the
 * server to know their episode (they cannot be sent yet, so they would otherwise be lost).
 * What does not: the account key (the user signs in again), caches the server refills, downloads
 * (fetched again) and diagnostics. Everything else is on the server, so a transfer is offered only
 * with nothing pending (§ P0: no local intent is lost or overwritten).
 */
internal data class TransferBundle(
    val formatVersion: Int,
    val createdAtMs: Long,
    val sourcePackage: String,
    val sourceVersion: String,
    /** The account the deferred intents belong to; not secret, only for matching. */
    val serverUrl: String?,
    val userId: Int?,
    val preferences: Map<String, Map<String, Any>>,
    val deferredIntents: List<DeferredEpisodeIntentEntity>,
)

internal class TransferException(val reason: TransferError) : Exception(reason.name)

internal enum class TransferError { NOT_A_TRANSFER, NEWER_FORMAT, BROKEN }

internal object TransferFormat {
    const val VERSION = 1
    private const val MAGIC = "nookast-transfer"

    /** The settings files that travel; the rest are caches, journals or per-install state. */
    val PREFERENCE_FILES = listOf("display_preferences", "playback_preferences")

    fun encode(bundle: TransferBundle): String = JSONObject()
        .put("magic", MAGIC)
        .put("format", bundle.formatVersion)
        .put("created_at_ms", bundle.createdAtMs)
        .put("source_package", bundle.sourcePackage)
        .put("source_version", bundle.sourceVersion)
        .put("server_url", bundle.serverUrl ?: JSONObject.NULL)
        .put("user_id", bundle.userId ?: JSONObject.NULL)
        .put(
            "preferences",
            JSONObject().apply {
                bundle.preferences.toSortedMap().forEach { (file, values) ->
                    put(file, JSONObject().apply { values.toSortedMap().forEach { (key, value) -> put(key, encodeValue(value)) } })
                }
            },
        )
        .put("deferred_intents", JSONArray().apply { bundle.deferredIntents.forEach { put(encodeIntent(it)) } })
        .toString(2)

    fun decode(text: String): TransferBundle {
        val root = runCatching { JSONObject(text) }.getOrElse { throw TransferException(TransferError.NOT_A_TRANSFER) }
        if (root.optString("magic") != MAGIC) throw TransferException(TransferError.NOT_A_TRANSFER)
        val format = root.optInt("format", -1)
        if (format > VERSION) throw TransferException(TransferError.NEWER_FORMAT)
        return runCatching {
            val preferences = root.getJSONObject("preferences").let { files ->
                files.keys().asSequence().filter { it in PREFERENCE_FILES }.associateWith { file ->
                    val values = files.getJSONObject(file)
                    values.keys().asSequence().associateWith { key -> decodeValue(values.getJSONObject(key)) }
                }
            }
            val intents = root.getJSONArray("deferred_intents").let { array ->
                List(array.length()) { decodeIntent(array.getJSONObject(it)) }
            }
            TransferBundle(
                formatVersion = format,
                createdAtMs = root.getLong("created_at_ms"),
                sourcePackage = root.getString("source_package"),
                sourceVersion = root.getString("source_version"),
                serverUrl = root.optString("server_url").takeUnless { root.isNull("server_url") || it.isBlank() },
                userId = if (root.isNull("user_id")) null else root.getInt("user_id"),
                preferences = preferences,
                deferredIntents = intents,
            )
        }.getOrElse { throw TransferException(TransferError.BROKEN) }
    }

    private fun encodeValue(value: Any): JSONObject = when (value) {
        is Boolean -> JSONObject().put("type", "boolean").put("value", value)
        is Int -> JSONObject().put("type", "int").put("value", value)
        is Long -> JSONObject().put("type", "long").put("value", value)
        is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
        is String -> JSONObject().put("type", "string").put("value", value)
        is Set<*> -> JSONObject().put("type", "string_set").put("value", JSONArray(value.filterIsInstance<String>().sorted()))
        else -> throw IllegalArgumentException("Unsupported preference value")
    }

    private fun decodeValue(json: JSONObject): Any = when (json.getString("type")) {
        "boolean" -> json.getBoolean("value")
        "int" -> json.getInt("value")
        "long" -> json.getLong("value")
        "float" -> json.getDouble("value").toFloat()
        "string" -> json.getString("value")
        "string_set" -> json.getJSONArray("value").let { array -> List(array.length()) { array.getString(it) }.toSet() }
        else -> throw IllegalArgumentException("Unknown preference type")
    }

    private fun encodeIntent(intent: DeferredEpisodeIntentEntity): JSONObject = JSONObject()
        .put("episode_cache_key", intent.episodeCacheKey)
        .put("kind", intent.kind.name)
        .put("desired_state", intent.desiredState ?: JSONObject.NULL)
        .put("position_ms", intent.positionMs ?: JSONObject.NULL)
        .put("duration_ms", intent.durationMs ?: JSONObject.NULL)
        .put("progress_cause", intent.progressCause?.name ?: JSONObject.NULL)
        .put("destination_index", intent.destinationIndex ?: JSONObject.NULL)
        .put("sequence", intent.sequence)
        .put("created_at_epoch_ms", intent.createdAtEpochMs)

    private fun decodeIntent(json: JSONObject) = DeferredEpisodeIntentEntity(
        episodeCacheKey = json.getString("episode_cache_key"),
        kind = DeferredEpisodeIntentKind.valueOf(json.getString("kind")),
        desiredState = if (json.isNull("desired_state")) null else json.getBoolean("desired_state"),
        positionMs = if (json.isNull("position_ms")) null else json.getLong("position_ms"),
        durationMs = if (json.isNull("duration_ms")) null else json.getLong("duration_ms"),
        progressCause = if (json.isNull("progress_cause")) null else ProgressCause.valueOf(json.getString("progress_cause")),
        destinationIndex = if (json.isNull("destination_index")) null else json.getInt("destination_index"),
        sequence = json.getLong("sequence"),
        createdAtEpochMs = json.getLong("created_at_epoch_ms"),
    )

    /** The settings of [context] that travel, as typed values. */
    fun readPreferences(context: Context): Map<String, Map<String, Any>> = PREFERENCE_FILES.associateWith { file ->
        context.getSharedPreferences(file, Context.MODE_PRIVATE).all
            .filterValues { it is Boolean || it is Int || it is Long || it is Float || it is String || it is Set<*> }
            .mapValues { (_, value) -> value!! }
    }.filterValues { it.isNotEmpty() }

    /** Writes [preferences] over the settings of [context]; keys not in the file stay as they are. */
    fun writePreferences(context: Context, preferences: Map<String, Map<String, Any>>) {
        preferences.filterKeys { it in PREFERENCE_FILES }.forEach { (file, values) ->
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit(commit = true) {
                values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }
        }
    }
}
