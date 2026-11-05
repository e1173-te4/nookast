package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTechnicalInfoTest {
    @Test
    fun `content range total wins over partial response length`() {
        assertEquals(12_345L, parseTotalBytes("bytes 0-0/12345", 1L))
    }

    @Test
    fun `content length is used when range is absent`() {
        assertEquals(8_192L, parseTotalBytes(null, 8_192L))
        assertNull(parseTotalBytes("bytes 0-0/*", -1L))
    }

    @Test
    fun `bitrate is derived from size and duration`() {
        assertEquals(128, bitrateKbps(57_600_000L, 3_600_000L))
        assertNull(bitrateKbps(0, 3_600_000L))
    }
}
