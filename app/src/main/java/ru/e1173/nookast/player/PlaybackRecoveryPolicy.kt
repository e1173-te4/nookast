package ru.e1173.nookast.player

internal fun nextRecoverySource(
    currentUrl: String?,
    primaryUrl: String?,
    fallbackUrl: String?,
    attemptedUrls: Set<String>,
): String? = listOfNotNull(primaryUrl, fallbackUrl)
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .firstOrNull { it != currentUrl && it !in attemptedUrls }

internal fun recoveryRetryDelayMs(attempt: Int): Long {
    val exponent = attempt.coerceIn(0, 5)
    return (2_000L shl exponent).coerceAtMost(60_000L)
}

/**
 * What a playback error means for recovery (plan 4.6). Only a transient one is retried with
 * back-off; a missing file, a refused request or an unplayable format would fail the same way
 * every time, so the player stops at once and says why.
 */
internal enum class PlaybackErrorKind { TRANSIENT, MISSING, REFUSED, UNSUPPORTED }

internal fun playbackErrorKind(errorCode: Int, httpStatus: Int? = null): PlaybackErrorKind = when {
    httpStatus == 404 || httpStatus == 410 -> PlaybackErrorKind.MISSING
    httpStatus == 401 || httpStatus == 403 -> PlaybackErrorKind.REFUSED
    httpStatus != null && httpStatus in 400..499 && httpStatus != 408 && httpStatus != 429 -> PlaybackErrorKind.REFUSED
    httpStatus != null -> PlaybackErrorKind.TRANSIENT
    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackErrorKind.MISSING
    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
        errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackErrorKind.REFUSED
    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
        errorCode in PARSING_ERRORS || errorCode in DECODING_ERRORS -> PlaybackErrorKind.UNSUPPORTED
    else -> PlaybackErrorKind.TRANSIENT
}

// Media3 groups its codes by thousands: 3xxx parsing, 4xxx decoding.
private val PARSING_ERRORS = 3000..3999
private val DECODING_ERRORS = 4000..4999
