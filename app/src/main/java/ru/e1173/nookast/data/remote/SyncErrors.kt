package ru.e1173.nookast.data.remote

import org.json.JSONObject
import ru.e1173.nookast.core.network.optText

enum class SyncErrorCategory {
    AUTHENTICATION,
    NETWORK,
    SERVER,
}

class SyncRequestException(
    val category: SyncErrorCategory,
    cause: Throwable? = null,
) : Exception(category.name, cause)

/**
 * PinePods mutation endpoints return HTTP 200 only after the database operation, but a proxy,
 * incompatible server version, or future regression can still return an unexpected success page.
 * Outbox records are confirmed only after the endpoint-specific acknowledgement is present.
 */
internal fun validateSuccessBody(body: String, responseField: String) {
    val payload = body.trim()
    if (payload.equals("true", ignoreCase = true)) return
    if (payload.isEmpty() || payload.equals("false", ignoreCase = true)) {
        throw SyncRequestException(SyncErrorCategory.SERVER)
    }

    val json = runCatching { JSONObject(payload) }
        .getOrElse { throw SyncRequestException(SyncErrorCategory.SERVER, it) }
    if (json.optBoolean("success", true).not()) {
        throw SyncRequestException(SyncErrorCategory.SERVER)
    }
    if (json.optText(responseField).isBlank()) {
        throw SyncRequestException(SyncErrorCategory.SERVER)
    }
}
