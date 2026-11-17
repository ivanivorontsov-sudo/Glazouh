package org.glazouh.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextChunkerTest {
    @Test
    fun shortTextStaysInOneChunk() {
        assertEquals(listOf("Привет, мир!"), SpeechTextChunker.split("  Привет, мир!  ", 100))
    }

    @Test
    fun longTextIsSplitWithoutDroppingCharacters() {
        val source = ("Привет, мир! Это проверка длинной распознанной страницы. ").repeat(80).trim()
        val chunks = SpeechTextChunker.split(source, 48)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 48 })
        assertEquals(source, chunks.joinToString(separator = ""))
    }

    @Test
    fun veryLongWordIsSplitSafely() {
        val source = "я".repeat(137)
        val chunks = SpeechTextChunker.split(source, 16)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 16 })
        assertEquals(source, chunks.joinToString(separator = ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroMaximumLengthIsRejected() {
        SpeechTextChunker.split("текст", 0)
    }
}
