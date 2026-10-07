package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.parse.expandedDexFixture
import com.enrpau.dualscreendex.parser.dataset.descriptions.putU16
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test

class ExpandedDescriptionFamilyIntegrationTest {
    @Test fun productionSemanticPhaseSelectsTheCompleteNativeFallback() {
        val fixture = expandedDexFixture()
        val result = semantic(fixture.bytes)
        assertTrue(result.descriptions.compatible)
        val table = requireNotNull(result.descriptionsLayout)
        assertEquals(0x2000, table.offset)
        assertEquals(listOf(20), table.pointerOffsets)
        assertEquals(mapOf(1 to 1, 2 to 2, 3 to 2, 4 to 1), result.resolvedDescriptions!!.compiledRowBinding!!.rows)
        assertEquals(3, result.descriptions.totalRecords)
        assertEquals(4, result.descriptions.expectedRecords)
        assertEquals(4, result.descriptions.coveredRecords)
        assertTrue(result.coreDatasets.speciesNames.compatible)
        assertTrue(result.coreDatasets.baseStats.compatible)
    }

    @Test fun lostOptionalProofAndWorkBudgetKeepIndependentCoreIntact() {
        for (budget in listOf(false, true)) {
            val fixture = expandedDexFixture()
            if (!budget) putU16(fixture.bytes, 0x18c, 0x8804)
            val result = semantic(fixture.bytes,
                if (budget) ResolutionLimits(maxProbeWorkPerDataset = 1) else ResolutionLimits())
            assertFalse(result.descriptions.compatible)
            assertNull(result.resolvedDescriptions)
            assertTrue(result.coreDatasets.speciesNames.compatible)
            assertTrue(result.coreDatasets.baseStats.compatible)
            assertEquals(5, result.coreDatasets.speciesCount)
        }
    }

    private fun semantic(bytes: ByteArray, limits: ResolutionLimits = ResolutionLimits()): SemanticDomainPhaseResult.Resolved {
        val codec = PokemonTextCodec.gbaEnglish
        val names = TableLayout(0x4800, 5, 6)
        val manifest = RomLanguageManifest(codec.language, listOf(RomLanguageProjection(
            codec.language, codec.id, codec.version, LocalizedTableLayout(speciesNames = names),
            emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val evidence = ValidationEvidence(true, 5, 5, 1.0, emptyList())
        val core = CoreDatasetsPhaseResult.Resolved(ProfileTables(speciesNames = names),
            5, 1, 1, evidence, evidence, evidence, evidence,
            speciesNamesLayout = names, baseStatsLayout = null, moveNamesLayout = null, moveDataLayout = null,
            languageManifest = manifest)
        val identity = IdentityRootsPhaseResult.Resolved(
            exactProfile = null, baseProfile = null, identityMatched = true, scoreEvidence = emptyList(),
            expansion = null, compiledGbaReferences = null,
            tableResolution = ProfileTableResolution(ProfileTables()), probeCodec = codec)
        val state = FamilyProbeState.empty().withIdentityRoots(identity).withCoreDatasets(core)
        return SemanticDomainStrategy().execute(
            RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC"), limits = limits),
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD), state,
        ).semanticDomain as SemanticDomainPhaseResult.Resolved
    }
}
