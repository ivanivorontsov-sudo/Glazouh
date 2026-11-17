package org.glazouh.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundEventLabelsRuTest {
    @Test
    fun translatesCommonAlertSounds() {
        assertEquals("Сирена", SoundEventLabelsRu.translate("Siren"))
        assertEquals("Звонок в дверь", SoundEventLabelsRu.translate("Doorbell"))
        assertEquals("Датчик дыма", SoundEventLabelsRu.translate("Smoke detector, smoke alarm"))
    }

    @Test
    fun keepsOriginalNameForUnmappedClasses() {
        assertEquals("Unmapped AudioSet class", SoundEventLabelsRu.translate("Unmapped AudioSet class"))
    }
}
