package com.enrpau.dualscreendex.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TypePresentationMaterializerTest {
    @Test
    fun appliesFamilyTypeColorAndReadableForeground() {
        val fire = TypeRecord(10, CatalogField.available("FIRE"))

        val presented = TypePresentationMaterializer.apply(mapOf(10 to fire)).getValue(10).presentation.value!!

        assertEquals(PresentationSource.FAMILY_FALLBACK, presented.source)
        assertNotEquals(presented.foregroundArgb, presented.backgroundArgb)
        assertNotEquals(presented.borderArgb, presented.backgroundArgb)
    }

    @Test
    fun customTypeUsesExplicitAccessibleFallback() {
        val custom = TypeRecord(42, CatalogField.available("COSMIC"))

        val presented = TypePresentationMaterializer.apply(mapOf(42 to custom)).getValue(42).presentation.value!!

        assertEquals(PresentationSource.ACCESSIBLE_FALLBACK, presented.source)
    }
}
