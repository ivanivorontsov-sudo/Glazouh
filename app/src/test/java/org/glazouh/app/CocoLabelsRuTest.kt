package org.glazouh.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CocoLabelsRuTest {
    @Test
    fun commonObjectsHaveRussianLabels() {
        assertEquals("человек", CocoLabelsRu.displayName("person"))
        assertEquals("автомобиль", CocoLabelsRu.displayName("car"))
        assertEquals("светофор", CocoLabelsRu.displayName("traffic light"))
    }

    @Test
    fun unknownLabelFallsBackWithoutCrashing() {
        assertEquals("unmapped_label", CocoLabelsRu.displayName("unmapped_label"))
    }
}
