package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkGlowColorsTest {
    private fun hue(color: Int) = rgbToHsv(color)[0]

    @Test
    fun `a purple cover with yellow text glows purple`() {
        val purple = 0xFF5B2A86.toInt()
        val yellow = 0xFFF2C94C.toInt()
        val pixels = IntArray(576) { if (it % 10 == 0) yellow else purple }
        val first = artworkGlowColors(pixels).first()
        assertTrue("hue ${hue(first)}", hue(first) in 260f..300f)
    }

    @Test
    fun `a grey cover stays neutral instead of turning brown`() {
        val pixels = IntArray(576) { if (it % 2 == 0) 0xFF404040.toInt() else 0xFFB0B0B0.toInt() }
        val colors = artworkGlowColors(pixels)
        assertEquals(0f, rgbToHsv(colors[0])[1], 0.01f)
    }

    @Test
    fun `a second distinct hue joins the dominant one`() {
        val blue = 0xFF2F6FDB.toInt()
        val orange = 0xFFE8742A.toInt()
        val colors = artworkGlowColors(IntArray(576) { if (it % 3 == 0) orange else blue })
        assertTrue(hue(colors[0]) in 200f..230f)
        assertTrue(hue(colors[1]) in 10f..40f)
    }
}
