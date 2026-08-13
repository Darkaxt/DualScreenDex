package com.enrpau.dualscreendex.parser.dataset.encounters

import com.enrpau.dualscreendex.parser.catalog.EncounterMethods
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterMaterializationProjectionTest {
    @Test
    fun projectionConsumesOnlyTypedRowsAndMatchesClassicHiddenCatalogSemantics() {
        val bytes = ByteArray(0x4000)
        putClassicEncounterTable(bytes, 0x100, hiddenEnvironment = 0)
        val decoded = Gen3EncounterCodec().decode(
            encounterSession(bytes),
            Gen3EncounterTableLayout(0x100, Gen3EncounterAbi.CLASSIC_24, 100),
        ) as EncounterTableOutcome.Decoded
        val layout = ResolvedEncounterLayout(decoded.layout, decoded.rows)

        val areas = EncounterMaterializationProjection.materialize(layout)

        val hidden = areas.single { it.methodId == EncounterMethods.HIDDEN }
        assertTrue(hidden.name.value!!.contains("land"))
        assertEquals(setOf(EncounterWindow.ANY), hidden.windows)
        assertEquals(listOf(60, 30, 10), hidden.slots.map { it.weight })
    }

    @Test
    fun projectionPreservesMixedTimeWindowsAndSuppressesEmptyAndMalformedRows() {
        val methods = listOf(
            DecodedEncounterMethod(
                methodId = EncounterMethods.GRASS_MORNING,
                label = "morning grass",
                encounterRate = 20,
                environment = null,
                windows = setOf(EncounterTimeWindow.MORNING),
                slots = listOf(DecodedEncounterSlot(10, 5, 5, 100)),
            ),
            DecodedEncounterMethod(
                methodId = EncounterMethods.GRASS_DAY,
                label = "day grass",
                encounterRate = 20,
                environment = null,
                windows = setOf(EncounterTimeWindow.DAY),
                slots = listOf(DecodedEncounterSlot(11, 5, 5, 100)),
            ),
            DecodedEncounterMethod(
                methodId = EncounterMethods.GRASS_NIGHT,
                label = "night grass",
                encounterRate = 20,
                environment = null,
                windows = setOf(EncounterTimeWindow.NIGHT),
                slots = listOf(DecodedEncounterSlot(12, 5, 5, 100)),
            ),
        )
        val table = Gen3EncounterTableLayout(0, Gen3EncounterAbi.STANDARD_20, 100)
        val layout = ResolvedEncounterLayout(
            table,
            listOf(
                EncounterHeaderOutcome.Decoded(0, 1, 2, methods),
                EncounterHeaderOutcome.StructuralEmpty(1, 1, 3),
                EncounterHeaderOutcome.Malformed(2, 1, 4, listOf("bad pointer")),
            ),
        )

        val areas = EncounterMaterializationProjection.materialize(layout)

        assertEquals(3, areas.size)
        assertEquals(
            setOf(EncounterWindow.MORNING, EncounterWindow.DAY, EncounterWindow.NIGHT),
            areas.flatMap { it.windows }.toSet(),
        )
        assertTrue(areas.none { it.id / 10 in setOf(0x103, 0x104) })
    }
}
