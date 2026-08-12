package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.catalog.AbilityDescriptionMaterializer
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Test

class AbilityLegacyParityTest {
    @Test
    fun focusedCodecsMatchLegacyMaterializersForAValidatedRetailLayout() {
        val names = AbilityNameTableLayout(0x100, 4, 13)
        val descriptions = AbilityDescriptionTableLayout(0x300, 4)
        val bytes = ByteArray(0x2000)
        val expectedNames = listOf("-------", "STENCH", "DRIZZLE", "SPEED BOOST")
        putAbilityNames(bytes, names, expectedNames)
        putAbilityDescriptions(
            bytes,
            descriptions,
            listOf(
                "NO SPECIAL ABILITY",
                "FIRST ABILITY EFFECT",
                "SECOND ABILITY EFFECT",
                "THIRD ABILITY EFFECT",
            ),
            textBase = 0x800,
        )
        val legacyLayout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 4,
            moveCount = 4,
            tables = ProfileTables(abilities = TableLayout(0x100, 4, 13)),
            compiledGbaReferences = GbaCompiledReferenceIndex(mapOf(0x300 to 2)),
        )
        val legacyNames = expectedNames.drop(1).mapIndexed { index, name -> index + 1 to name }.toMap()
        val legacyDescriptions = AbilityDescriptionMaterializer.materialize(RomImage(bytes), legacyLayout)
            ?.descriptions

        val resolvedNames = AbilityNameResolver().resolve(
            session = abilitySession(bytes),
            semanticDomain = AbilitySemanticDomain(setOf(1, 2, 3)),
            inheritedLayouts = listOf(names),
        ) as DatasetResolution.Resolved<ResolvedAbilityNameLayout>
        val resolvedDescriptions = AbilityDescriptionResolver().resolve(
            session = abilitySession(bytes, references = mapOf(0x300 to 2)),
            abilityNames = resolvedNames.candidate.layout,
            compiledLayouts = listOf(descriptions),
        ) as DatasetResolution.Resolved<ResolvedAbilityDescriptionLayout>

        assertEquals(
            legacyNames,
            resolvedNames.candidate.layout.baseRows
                .filterIsInstance<AbilityNameRowOutcome.Decoded>()
                .filter { it.rowIndex > 0 }
                .associate { it.rowIndex to it.name },
        )
        assertEquals(
            legacyDescriptions,
            resolvedDescriptions.candidate.layout.rows
                .filterIsInstance<AbilityDescriptionRowOutcome.Decoded>()
                .filter { it.rowIndex > 0 }
                .associate { it.rowIndex to it.description },
        )
    }
}
