package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.moves.CatalogMoveDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferencedMoveCatalogClosureTest {
    @Test
    fun closesEveryPositiveReferencedMoveWithoutReplacingDecodedRecords() {
        val existing = move(1, "Pound")
        val species = species(
            learnset = listOf(LearnsetEntry(level = 5, moveId = 2)),
            acquisitions = listOf(
                MoveAcquisition(moveId = 3, method = MoveAcquisitionMethod.MACHINE, sourceId = 1),
                MoveAcquisition(moveId = 4, method = MoveAcquisitionMethod.TUTOR, sourceId = 2),
                MoveAcquisition(moveId = 0, method = MoveAcquisitionMethod.EGG),
                MoveAcquisition(moveId = -1, method = MoveAcquisitionMethod.EGG),
            ),
        )
        val ruleset = LearnsetRuleset(
            id = "alternate",
            label = "Alternate",
            sourceOffset = 0x100,
            confidence = 1.0,
            entriesBySpecies = mapOf(1 to listOf(LearnsetEntry(level = 6, moveId = 5))),
        )
        val typedDetails = mapOf(
            1 to details(power = 1),
            3 to details(power = 70),
        )

        val closed = ReferencedMoveCatalogClosure.close(
            moves = mapOf(existing.id to existing),
            species = mapOf(species.id to species),
            rulesets = listOf(ruleset),
            typedDetails = typedDetails,
        )

        assertEquals(listOf(1, 2, 3, 4, 5), closed.keys.toList())
        assertSame(existing, closed.getValue(1))
        assertEquals(CatalogField.notFound<String>("referenced move has no decoded ROM name"), closed.getValue(3).name)
        assertEquals(70, closed.getValue(3).power.value)
        assertEquals(MoveCategory.PHYSICAL, closed.getValue(3).category.value)
        listOf(closed.getValue(4), closed.getValue(5)).forEach { placeholder ->
            assertEquals(null, placeholder.name.value)
            assertTrue(placeholder.name.reasons.single().contains("referenced move"))
            listOf(
                placeholder.typeId,
                placeholder.category,
                placeholder.power,
                placeholder.accuracy,
                placeholder.pp,
                placeholder.priority,
                placeholder.effectId,
            ).forEach { field -> assertEquals(null, field.value) }
        }
        assertFalse(0 in closed)
        assertFalse(-1 in closed)
    }

    private fun species(
        learnset: List<LearnsetEntry>,
        acquisitions: List<MoveAcquisition>,
    ) = SpeciesRecord(
        id = 1,
        dexNumber = CatalogField.available(1),
        name = CatalogField.available("Bulbasaur"),
        typeIds = CatalogField.available(listOf(12, 3)),
        baseStats = CatalogField.available(BaseStats(45, 49, 49, 45, 65, 65)),
        sprite = CatalogField.notFound("fixture"),
        learnset = CatalogField.available(learnset),
        moveAcquisitions = CatalogField.available(acquisitions),
    )

    private fun move(id: Int, name: String) = MoveRecord(
        id = id,
        name = CatalogField.available(name),
        typeId = CatalogField.available(0),
        category = CatalogField.available(MoveCategory.PHYSICAL),
        power = CatalogField.available(40),
        accuracy = CatalogField.available(100),
        pp = CatalogField.available(35),
    )

    private fun details(power: Int) = CatalogMoveDetails(
        typeId = 1,
        category = MoveCategory.PHYSICAL,
        power = power,
        accuracy = 100,
        pp = 15,
        priority = 0,
        effectId = 0,
    )
}
