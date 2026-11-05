package ru.e1173.nookast.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerAddress {
    fun normalize(raw: String): Result<HttpUrl> = runCatching {
        val parsed = raw.trim().toHttpUrlOrNull() ?: throw LoginFailure(LoginError.BAD_ADDRESS)
        if (parsed.scheme != "https" && parsed.scheme != "http") throw LoginFailure(LoginError.ONLY_HTTP_HTTPS)
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw LoginFailure(LoginError.CREDENTIALS_IN_ADDRESS)
        }

        parsed.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
    }
}
