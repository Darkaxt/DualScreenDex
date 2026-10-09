package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Test

class Gen3BlankMapDispositionTest {
    @Test
    fun positiveBlankAuthoritySurvivesLocalDescriptorMaterialization() {
        val map = materialize(7, setOf(7), mapOf(7 to "unrelated readable text"))
        assertEquals("NATIVE_BLANK", map.nameDisposition.name)
        assertEquals(null, map.displayName)
        assertEquals(0x300, map.baseAreaId)
        assertEquals(24, map.gridWidth)
        assertEquals(20, map.gridHeight)
    }

    @Test
    fun absentTextDoesNotDeclareAnOrdinarySectionBlank() {
        val map = materialize(7, emptySet(), emptyMap())
        assertEquals("STATIC_NAME_REQUIRED", map.nameDisposition.name)
        assertEquals(null, map.displayName)
    }

    @Test
    fun unrelatedBlankSectionsDoNotChangeStaticOrContextualNames() {
        val ordinary = materialize(7, setOf(8), mapOf(7 to "ordinary"))
        assertEquals("STATIC_NAME_REQUIRED", ordinary.nameDisposition.name)
        assertEquals("ordinary", ordinary.displayName)
        val contextual = materialize(0x57, setOf(0x57), mapOf(0x57 to "not static"))
        assertEquals("CONTEXT_DEPENDENT", contextual.nameDisposition.name)
        assertEquals(null, contextual.displayName)
    }

    private fun materialize(section: Int, blanks: Set<Int>, names: Map<Int, String>): LocalMap {
        val bytes = ByteArray(0x1000)
        fun word(at: Int, value: Int) {
            repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() }
        }
        word(0x100, 0x08000200)
        bytes[0x114] = section.toByte()
        word(0x200, 24); word(0x204, 20)
        word(0x20C, 0x08000400); word(0x210, 0x08000800); word(0x214, 0x08000900)
        val method = Gen3LocalMapResolver::class.java.declaredMethods
            .single { it.name == "readDescriptors" }.apply { isAccessible = true }
        val batch = method.invoke(
            Gen3LocalMapResolver, RomImage(bytes), mapOf(0x300 to 0x100), names, ParserCancellationToken.NONE, blanks,
        )
        val field = batch.javaClass.getDeclaredField("descriptors").apply { isAccessible = true }
        val descriptor = (field.get(batch) as List<*>).single()!!
        val toLocalMap = descriptor.javaClass.declaredMethods.single { it.name == "toLocalMap" }
            .apply { isAccessible = true }
        return toLocalMap.invoke(descriptor, "asset") as LocalMap
    }
}
