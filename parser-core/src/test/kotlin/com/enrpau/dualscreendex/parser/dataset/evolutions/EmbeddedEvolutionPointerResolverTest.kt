package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.model.HeaderlessUnifiedSpeciesMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedEvolutionPointerResolverTest {
    @Test fun resolvesOneUniqueTerminatedEvolutionPointerField() {
        val bytes = ByteArray(0x1000)
        val root = 0x100
        val stride = 0x40
        val field = 0x20
        repeat(4) { species -> bytes[root + species * stride] = if (species == 0) 0 else 1 }
        putU32(bytes, root + stride + field, 0x08000800)
        putEvolution(bytes, 0x800, method = 4, parameter = 16, target = 2, condition = 0)
        putEvolution(bytes, 0x808, method = 0xFFFF, parameter = 0, target = 0, condition = 0)

        val result = EmbeddedEvolutionPointerResolver.resolve(
            evolutionSession(bytes), metadata(root, stride), speciesCount = 4,
        )

        val resolved = requireNotNull(result)
        assertEquals(field, resolved.pointerFieldOffset)
        assertEquals(8, resolved.resolved.table.recordSize)
        val row = resolved.resolved.rows[1] as EvolutionRowOutcome.Decoded
        assertEquals(listOf(2), row.edges.map(EvolutionEdgeValue::targetSpeciesId))
        assertTrue(resolved.resolved.rows[2] is EvolutionRowOutcome.StructuralEmpty)
    }

    @Test fun rejectsTwoEquallyValidPointerFieldsAsAmbiguous() {
        val bytes = ByteArray(0x1000)
        val root = 0x100
        val stride = 0x40
        repeat(3) { species -> bytes[root + species * stride] = if (species == 0) 0 else 1 }
        listOf(0x20 to 0x800, 0x24 to 0x900).forEach { (field, list) ->
            putU32(bytes, root + stride + field, 0x08000000 + list)
            putEvolution(bytes, list, method = 4, parameter = 16, target = 2, condition = 0)
            putEvolution(bytes, list + 8, method = 0xFFFF, parameter = 0, target = 0, condition = 0)
        }

        assertNull(
            EmbeddedEvolutionPointerResolver.resolve(
                evolutionSession(bytes), metadata(root, stride), speciesCount = 3,
            ),
        )
    }

    @Test fun rejectsUnterminatedOrInactiveTargetsWithoutPublishingPartialEdges() {
        val bytes = ByteArray(0x1000)
        val root = 0x100
        val stride = 0x40
        bytes[root + stride] = 1
        putU32(bytes, root + stride + 0x20, 0x08000800)
        putEvolution(bytes, 0x800, method = 4, parameter = 16, target = 3, condition = 0)

        assertNull(
            EmbeddedEvolutionPointerResolver.resolve(
                evolutionSession(bytes), metadata(root, stride), speciesCount = 3,
            ),
        )
    }

    private fun metadata(root: Int, stride: Int) = HeaderlessUnifiedSpeciesMetadata(
        speciesTableOffset = root,
        speciesRecordSize = stride,
        activePredicateOffset = 0,
        speciesNameOffset = 8,
        speciesNameWidth = 8,
        nationalDexOffset = 0x10,
    )
}
