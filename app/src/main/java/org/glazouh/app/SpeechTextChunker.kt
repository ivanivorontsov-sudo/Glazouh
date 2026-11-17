package org.glazouh.app

/** Splits long OCR text into chunks accepted by Android TextToSpeech. */
internal object SpeechTextChunker {
    private val BREAK_CHARACTERS = charArrayOf('.', '!', '?', ';', ':', '\n', ' ', '\t')

    fun split(text: String, maxLength: Int): List<String> {
        require(maxLength > 0) { "maxLength must be positive" }

        val source = text.trim()
        if (source.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < source.length) {
            val hardEnd = minOf(start + maxLength, source.length)
            var end = hardEnd

            if (hardEnd < source.length) {
                val boundary = source.lastIndexOfAny(BREAK_CHARACTERS, hardEnd - 1)
                val earliestUsefulBoundary = start + maxLength / 2
                if (boundary >= earliestUsefulBoundary) {
                    end = boundary + 1
                }
            }

            if (end <= start) end = hardEnd
            chunks += source.substring(start, end)
            start = end
        }

        return chunks
    }
}
