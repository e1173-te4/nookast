package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioProcessingPolicyTest {
    @Test
    fun `settings are clamped to safe ranges`() {
        assertEquals(
            AudioProcessingSettings(overallGainDb = 12, bassDb = 0, speechClarity = 3),
            AudioProcessingSettings(overallGainDb = 99, bassDb = -4, speechClarity = 8)
                .normalized(),
        )
    }

    @Test
    fun `bass strength maps the supported range`() {
        assertEquals(0, bassStrengthForDb(0).toInt())
        assertEquals(500, bassStrengthForDb(6).toInt())
        assertEquals(1_000, bassStrengthForDb(12).toInt())
    }

    @Test
    fun `clarity level respects equalizer limits`() {
        assertEquals(0, clarityBandLevelFor(0, -1_500, 1_500).toInt())
        assertEquals(900, clarityBandLevelFor(3, -1_500, 1_500).toInt())
        assertEquals(1_200, bassBandLevelFor(12, -1_500, 1_500).toInt())
        assertEquals(200, clarityBandLevelFor(3, -200, 200).toInt())
    }
}
