package com.enrpau.dualscreendex.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TypePresentationMaterializerTest {
    @Test
    fun `semantic role determines family color regardless of localized name`() {
        val fire = TypeRecord(
            id = 10,
            name = CatalogField.available("FEU"),
            semanticRole = CatalogField.available(TypeSemanticRole.FIRE),
        )

        val presented = TypePresentationMaterializer.apply(mapOf(10 to fire)).getValue(10).presentation.value!!

        assertEquals(PresentationSource.FAMILY_FALLBACK, presented.source)
        assertNotEquals(presented.foregroundArgb, presented.backgroundArgb)
        assertNotEquals(presented.borderArgb, presented.backgroundArgb)
    }

    @Test
    fun `presentation fallback does not grant semantic authority`() {
        val fire = TypeRecord(
            id = 10,
            name = CatalogField.available("FEU"),
        )

        val record = TypePresentationMaterializer.apply(mapOf(10 to fire)) {
            TypeSemanticRole.FIRE
        }.getValue(10)

        assertEquals(PresentationSource.FAMILY_FALLBACK, record.presentation.value?.source)
        assertEquals(null, record.semanticRole.value)
    }

    @Test
    fun customTypeUsesExplicitAccessibleFallback() {
        val custom = TypeRecord(42, CatalogField.available("COSMIC"))

        val presented = TypePresentationMaterializer.apply(mapOf(42 to custom)).getValue(42).presentation.value!!

        assertEquals(PresentationSource.ACCESSIBLE_FALLBACK, presented.source)
    }
}
