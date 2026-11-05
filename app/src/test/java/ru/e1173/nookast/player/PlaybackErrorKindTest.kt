package ru.e1173.nookast.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorKindTest {
    @Test
    fun `http answers decide by status`() {
        val bad = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        assertEquals(PlaybackErrorKind.MISSING, playbackErrorKind(bad, 404))
        assertEquals(PlaybackErrorKind.MISSING, playbackErrorKind(bad, 410))
        assertEquals(PlaybackErrorKind.REFUSED, playbackErrorKind(bad, 403))
        assertEquals(PlaybackErrorKind.REFUSED, playbackErrorKind(bad, 401))
        // A timeout, rate limit or server failure may pass: retried.
        assertEquals(PlaybackErrorKind.TRANSIENT, playbackErrorKind(bad, 408))
        assertEquals(PlaybackErrorKind.TRANSIENT, playbackErrorKind(bad, 429))
        assertEquals(PlaybackErrorKind.TRANSIENT, playbackErrorKind(bad, 503))
    }

    @Test
    fun `network trouble is retried and broken files are not`() {
        assertEquals(PlaybackErrorKind.TRANSIENT, playbackErrorKind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(PlaybackErrorKind.TRANSIENT, playbackErrorKind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(PlaybackErrorKind.MISSING, playbackErrorKind(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(PlaybackErrorKind.UNSUPPORTED, playbackErrorKind(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertEquals(PlaybackErrorKind.UNSUPPORTED, playbackErrorKind(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
    }
}
