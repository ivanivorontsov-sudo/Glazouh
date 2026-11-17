internal object TOneAudioResampler {
    /** Converts mono PCM16 at 16 kHz to normalized mono float samples at 8 kHz. */
    fun pcm16kTo8k(input: ShortArray, validSamples: Int = input.size): FloatArray {
        require(validSamples in 0..input.size) { "validSamples is outside the input buffer" }
        val outputCount = validSamples / 2
        return FloatArray(outputCount) { index ->
            val first = input[index * 2].toInt()
            val second = input[index * 2 + 1].toInt()
            ((first + second) / 2f / 32768f).coerceIn(-1f, 1f)
        }
    }
}
