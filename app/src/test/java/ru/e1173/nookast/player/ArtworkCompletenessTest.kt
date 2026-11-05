package ru.e1173.nookast.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkCompletenessTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `jpeg needs its end marker`() {
        assertTrue(ArtworkBitmapRepository.isCompleteImage(bytes(0xFF, 0xD8, 0x00, 0x11, 0xFF, 0xD9)))
        assertTrue(ArtworkBitmapRepository.isCompleteImage(bytes(0xFF, 0xD8, 0x00, 0xFF, 0xD9, 0x00, 0x00)))
        assertFalse(ArtworkBitmapRepository.isCompleteImage(bytes(0xFF, 0xD8, 0x00, 0x11, 0x22, 0x33)))
    }

    @Test
    fun `png needs IEND and other formats pass`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        assertFalse(ArtworkBitmapRepository.isCompleteImage(png + ByteArray(10)))
        assertTrue(ArtworkBitmapRepository.isCompleteImage(png + "....IEND".toByteArray()))
        assertTrue(ArtworkBitmapRepository.isCompleteImage("RIFF....WEBP".toByteArray()))
    }
}
