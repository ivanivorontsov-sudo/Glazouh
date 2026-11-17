package org.glazouh.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TOneAudioResamplerTest {
    @Test
    fun averagesAdjacentSamplesWhenDownsampling() {
        val input = shortArrayOf(10_000, 20_000, -10_000, -20_000)
        val output = TOneAudioResampler.pcm16kTo8k(input)

        assertEquals(2, output.size)
        assertEquals(15_000f / 32768f, output[0], 0.00001f)
        assertEquals(-15_000f / 32768f, output[1], 0.00001f)
    }

    @Test
    fun ignoresOneIncompleteTrailingSample() {
        val input = shortArrayOf(10_000, 20_000, 30_000)
        val output = TOneAudioResampler.pcm16kTo8k(input)

        assertEquals(1, output.size)
        assertEquals(15_000f / 32768f, output[0], 0.00001f)
    }

    @Test
    fun normalizesFullScaleSamplesIntoFloatRange() {
        val input = shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE)
        val output = TOneAudioResampler.pcm16kTo8k(input)

        assertTrue(output[0] > 0.99f && output[0] <= 1f)
        assertEquals(-1f, output[1], 0.00001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSampleCount() {
        TOneAudioResampler.pcm16kTo8k(shortArrayOf(1, 2), 3)
    }
}
