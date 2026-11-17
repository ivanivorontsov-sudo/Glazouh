package org.glazouh.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionAnnouncementGateTest {
    @Test
    fun categoryMustBeSeenInThreeConsecutiveFrames() {
        val gate = DetectionAnnouncementGate()

        assertTrue(gate.update(listOf("person" to 0.9f), 1_000).isEmpty())
        assertTrue(gate.update(listOf("person" to 0.91f), 2_000).isEmpty())
        assertEquals(listOf("person"), gate.update(listOf("person" to 0.92f), 3_000))
    }

    @Test
    fun announcedCategoryIsNotRepeatedOnEveryFrame() {
        val gate = DetectionAnnouncementGate()
        repeat(3) { gate.update(listOf("car" to 0.9f), 1_000L + it * 1_000L) }

        assertTrue(gate.update(listOf("car" to 0.95f), 10_000).isEmpty())
        assertTrue(gate.update(listOf("car" to 0.95f), 20_000).isEmpty())
    }

    @Test
    fun lowConfidenceDetectionsDoNotBecomeAnnouncements() {
        val gate = DetectionAnnouncementGate()

        repeat(10) {
            assertTrue(gate.update(listOf("person" to 0.3f), 1_000L + it * 1_000L).isEmpty())
        }
    }

    @Test
    fun categoryCanBeAnnouncedAgainAfterItDisappears() {
        val gate = DetectionAnnouncementGate()

        repeat(3) { gate.update(listOf("bottle" to 0.9f), 1_000L + it * 1_000L) }
        repeat(5) { gate.update(emptyList(), 10_000L + it * 1_000L) }
        assertTrue(gate.update(listOf("bottle" to 0.9f), 20_000).isEmpty())
        assertTrue(gate.update(listOf("bottle" to 0.9f), 21_000).isEmpty())
        assertEquals(listOf("bottle"), gate.update(listOf("bottle" to 0.9f), 22_000))
    }

    @Test
    fun globalRateLimitPreventsRapidSpeechForDifferentObjects() {
        val gate = DetectionAnnouncementGate()

        assertTrue(gate.update(listOf("person" to 0.9f), 1_000).isEmpty())
        assertTrue(gate.update(listOf("person" to 0.9f), 2_000).isEmpty())
        assertEquals(listOf("person"), gate.update(listOf("person" to 0.9f), 3_000))

        repeat(3) { gate.update(listOf("chair" to 0.9f), 4_000L + it * 100L) }
        assertTrue(gate.update(listOf("chair" to 0.9f), 5_000).isEmpty())
        assertEquals(listOf("chair"), gate.update(listOf("chair" to 0.9f), 5_500))
    }
}
