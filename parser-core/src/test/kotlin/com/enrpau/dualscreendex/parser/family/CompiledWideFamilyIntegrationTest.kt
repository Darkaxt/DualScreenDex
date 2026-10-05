package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.catalog.RelationshipMaterializers
import com.enrpau.dualscreendex.parser.catalog.LearnsetRulesetMaterializer
import com.enrpau.dualscreendex.parser.parse.SpeciesSemanticDomainResolver
import com.enrpau.dualscreendex.parser.parse.SpeciesSemanticDomainResolution
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideMoveFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompiledWideFamilyIntegrationTest {
    @Test
    fun productionPhasesPreservePhysicalExtentsCanonicalIndicesAndTypedRecords() {
        for (width in listOf(20, 56)) {
            val f = Gen3CompiledWideMoveFixture(width, 0x800)
            val probe = probe(f)
            val layout = requireNotNull(probe.resolvedLayout)
            assertNotNull(layout.compiledCanonicalSpecies)
            assertEquals(7, layout.tables.speciesNames!!.count)
            assertEquals(6, layout.tables.baseStats!!.count)
            assertEquals(TableRecordFormat.WIDE_STATS_64, layout.tables.baseStats!!.format)
            assertEquals(64, layout.tables.baseStats!!.recordSize)
            assertEquals(mapOf(1 to 44, 2 to 7, 4 to 9), layout.compiledCanonicalSpecies!!.nativeToDex)
            assertEquals(8, layout.moveCount)
            assertEquals(width, layout.resolvedDatasets.moveDetails!!.table.abi.recordSize)
            assertNotNull(layout.resolvedDatasets.learnsets)
            val image = RomImage(f.bytes)
            val entries = RelationshipMaterializers.learnsets(image, layout)
            assertEquals(setOf(1, 2, 4), entries.keys)
            assertEquals(setOf(1, 2, 4), LearnsetRulesetMaterializer.materialize(image, layout, entries).single().entriesBySpecies.keys)
            val domain = SpeciesSemanticDomainResolver.resolveWithEvidence(image, layout) as SpeciesSemanticDomainResolution.Resolved
            assertEquals(setOf(1, 2, 4), domain.domain.expectedSpeciesIds)
            assertEquals(3, domain.domain.coveredStatRecords)
            val rows = RecordMaterializers.species(image, layout)
            assertEquals(setOf(1, 2, 4), rows.keys)
            assertEquals(345, rows.getValue(1).baseStats.value?.hp)
            assertEquals(listOf(260, 65, 34), rows.getValue(1).abilityIds.value)
            assertEquals(CapabilityStatus.AVAILABLE, probe.capabilities.single { it.capability == RomCapability.BASE_STATS }.status)
            assertEquals(3, probe.capabilities.single { it.capability == RomCapability.BASE_STATS }.coveredRecords)
        }
    }

    @Test
    fun recognizedPartialWideCoreCannotReturnToInheritedRetailTables() {
        val f = Gen3CompiledWideMoveFixture()
        f.bytes.fill(0, f.core.typeConsumer, f.core.typeConsumer + 96)
        val probe = probe(f)
        assertFalse(probe.hardGatePassed)
        assertNull(probe.resolvedLayout)
        assertTrue(probe.diagnostics.any { it.contains("wide", ignoreCase = true) })
    }

    @Test
    fun incompleteMoveProofKeepsNumericCoreButDoesNotPublishDanglingAcquisitionLinks() {
        val f = Gen3CompiledWideMoveFixture()
        f.core.putU16(f.fieldConsumers[7] + 12, 0x5cc3)
        val layout = requireNotNull(probe(f).resolvedLayout)
        assertNotNull(layout.resolvedDatasets.baseStats)
        assertNotNull(layout.compiledCanonicalSpecies)
        assertNull(layout.resolvedDatasets.moveDetails)
        assertNull(layout.resolvedDatasets.learnsets)
        assertNull(layout.tables.moveData)
        assertNull(layout.tables.learnsets)
        assertEquals(setOf(1, 2, 4), RecordMaterializers.species(RomImage(f.bytes), layout).keys)
    }

    @Test
    fun lostOrMismatchedAcquisitionPrerequisitesCannotPublishRelationships() {
        val f = Gen3CompiledWideMoveFixture()
        val layout = requireNotNull(probe(f).resolvedLayout)
        val image = RomImage(f.bytes)
        val primary = RelationshipMaterializers.learnsets(image, layout)
        val variants = listOf(
            layout.copy(resolvedDatasets = com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts(
                baseStats = layout.resolvedDatasets.baseStats, learnsets = layout.resolvedDatasets.learnsets,
            )),
            layout.copy(tables = layout.tables.copy(moveData = null)),
            layout.copy(tables = layout.tables.copy(learnsets = layout.tables.learnsets!!.copy(offset = 0))),
            layout.copy(moveCount = 2),
        )
        for (variant in variants) {
            assertTrue(RelationshipMaterializers.learnsets(image, variant).isEmpty())
            assertTrue(LearnsetRulesetMaterializer.materialize(image, variant, primary).isEmpty())
        }
    }

    private fun probe(f: Gen3CompiledWideMoveFixture) = FamilyProbeCoordinator().probe(
        RomAnalysisSession(RomImage(f.bytes), RomHeader(Platform.GBA, "UNRELATED", "BPEE")),
        EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD),
    )
}
