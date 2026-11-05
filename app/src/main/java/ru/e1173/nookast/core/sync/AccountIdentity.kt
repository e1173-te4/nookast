package ru.e1173.nookast.core.sync

import java.security.MessageDigest
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.auth.ServerAddress

/**
 * Whose data the database holds (docs/SYNC.md §10, I8): the server origin and the user id.
 * The API key is never part of it, so signing in again to the same account keeps the data.
 */
data class AccountIdentity(val fingerprint: String, val origin: String, val userId: Int) {
    companion object {
        fun of(session: AuthSession): AccountIdentity {
            val origin = ServerAddress.normalize(session.serverUrl).getOrNull()
                ?.let { url -> "${url.scheme}://${url.host}:${url.port}" }
                ?: session.serverUrl.trim().trimEnd('/').lowercase()
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$origin\n${session.userId}".toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
            return AccountIdentity(fingerprint = digest.take(32), origin = origin, userId = session.userId)
        }
    }
}
