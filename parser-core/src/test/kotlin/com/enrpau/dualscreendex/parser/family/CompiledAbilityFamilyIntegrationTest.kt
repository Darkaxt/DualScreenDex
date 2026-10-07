package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.AbilityDescriptionMaterializer
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicsMaterializer
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.putAbilityNames
import com.enrpau.dualscreendex.parser.dataset.abilities.putGbaPointer
import com.enrpau.dualscreendex.parser.dataset.abilities.putGbaText
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionRowOutcome
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityTextFixture
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test

class CompiledAbilityFamilyIntegrationTest {
    @Test fun productionSemanticPhaseSelectsOwnedTextBeforeCoherentShapeAlternatives() {
        for (relocation in listOf(0, 0x400)) {
            val fixture = CompiledAbilityTextFixture(relocation)
            val result = semantic(fixture)
            assertTrue(result.abilities.compatible)
            val names = requireNotNull(result.resolvedAbilityNames)
            assertEquals(fixture.names.offset, names.table.offset)
            assertEquals(20, names.baseRowCount)
            assertEquals(17, names.table.nameWidth)
            assertTrue(result.coreDatasets.baseStats.compatible)
        }
    }

    @Test fun catalogReadsProvedSlotsRatherThanTheUnsupportedRecordSizeOverload() {
        val fixture = CompiledAbilityTextFixture()
        val result = semantic(fixture)
        val records = RecordMaterializers.species(RomImage(fixture.bytes), layout(fixture, result))
        assertEquals(listOf(1, 2), records.getValue(1).abilityIds.value)
        assertEquals(listOf(4), records.getValue(2).abilityIds.value)
        assertEquals(listOf(7, 12), records.getValue(3).abilityIds.value)
    }

    @Test fun malformedPointerDoesNotDiscardLaterOwnedDescriptions() {
        val fixture = CompiledAbilityTextFixture()
        putGbaPointer(fixture.bytes, fixture.descriptions.offset.toInt() + 2 * 4, -0x08000000)
        val result = semantic(fixture)
        val descriptions = requireNotNull(AbilityDescriptionMaterializer.materialize(
            RomImage(fixture.bytes), layout(fixture, result)))
        assertEquals(fixture.descriptions.offset.toInt(), descriptions.sourceOffset)
        assertFalse(2 in descriptions.descriptions)
        assertEquals("Binds a native modifier.", descriptions.descriptions[19])
    }

    @Test fun unterminatedAndUnnaturalProseAndNativeMissingTokensAreIndependentRows() {
        val fixture = CompiledAbilityTextFixture()
        putGbaPointer(fixture.bytes, fixture.descriptions.offset.toInt() + 5 * 4, 0x14000)
        fixture.bytes.fill(0xBB.toByte(), 0x14000, 0x14000 + 192)
        putGbaPointer(fixture.bytes, fixture.descriptions.offset.toInt() + 6 * 4, 0x14200)
        putGbaText(fixture.bytes, 0x14200, "-")
        putGbaPointer(fixture.bytes, fixture.descriptions.offset.toInt() + 7 * 4, 0x14300)
        putGbaText(fixture.bytes, 0x14300, "Token.")
        val result = semantic(fixture)
        val typed = requireNotNull(result.resolvedAbilityNames!!.compiledDescriptions)
        assertTrue(typed.rows[5] is AbilityDescriptionRowOutcome.Malformed)
        assertEquals("-", (typed.rows[6] as AbilityDescriptionRowOutcome.MissingProse).placeholder)
        assertTrue(typed.rows[7] is AbilityDescriptionRowOutcome.Malformed)
        val prose = requireNotNull(AbilityDescriptionMaterializer.materialize(
            RomImage(fixture.bytes), layout(fixture, result)))
        assertFalse(prose.descriptions.keys.any { it in setOf(5, 6, 7) })
        assertEquals("Binds a native modifier.", prose.descriptions[19])
        assertTrue(result.abilities.compatible)
    }

    @Test fun malformedActiveNameRetainsItsNativeIdWithoutInventingALabel() {
        val fixture = CompiledAbilityTextFixture()
        val start = fixture.names.offset.toInt() + 12 * fixture.names.stride
        fixture.bytes.fill(0xBB.toByte(), start, start + fixture.names.nameWidth)
        val result = semantic(fixture)
        val selected = layout(fixture, result)
        val rom = RomImage(fixture.bytes)
        assertTrue(result.abilities.compatible)
        assertEquals(CapabilityStatus.NOT_FOUND, RecordMaterializers.abilities(rom, selected).getValue(12).name.status)
        assertEquals(listOf(7, 12), RecordMaterializers.species(rom, selected).getValue(3).abilityIds.value)
    }

    @Test fun rejectedOwnedProseCannotFallBackAndCannotEraseTheNamesOrSpeciesJoin() {
        val fixture = CompiledAbilityTextFixture()
        fixture.bytes.fill(0, fixture.descriptions.offset.toInt(),
            fixture.descriptions.offset.toInt() + fixture.descriptions.count.toInt() * 4)
        val result = semantic(fixture)
        val names = requireNotNull(result.resolvedAbilityNames)
        assertTrue(result.abilities.compatible)
        assertNull(names.compiledDescriptions)
        assertFalse(names.compiledDescriptionFailure.isNullOrBlank())
        val selected = layout(fixture, result)
        assertNull(AbilityDescriptionMaterializer.materialize(RomImage(fixture.bytes), selected))
        assertEquals(listOf(1, 2), RecordMaterializers.species(RomImage(fixture.bytes), selected).getValue(1).abilityIds.value)
    }

    @Test fun changedCoreCannotUseOrFallBackFromTheStoredNativeBinding() {
        val fixture = CompiledAbilityTextFixture()
        val selected = layout(fixture, semantic(fixture))
        val replacements = listOf(fixture.core.copy(offset = fixture.core.offset + 2),
            fixture.core.copy(recordSize = 30, stride = fixture.core.recordSize))
        val rom = RomImage(fixture.bytes)
        replacements.forEach { core ->
            val changed = selected.copy(tables = selected.tables.copy(baseStats = core))
            assertNull(RecordMaterializers.species(rom, changed).getValue(1).abilityIds.value)
            assertNull(AbilityDescriptionMaterializer.materialize(rom, changed))
        }
    }

    @Test fun compiledTextDoesNotAutomaticallyBecomeMechanicsAuthority() {
        val fixture = CompiledAbilityTextFixture()
        val selected = layout(fixture, semantic(fixture))
        val rom = RomImage(fixture.bytes)
        val abilities = RecordMaterializers.abilities(rom, selected)
        assertEquals(19, abilities.size)
        val prose = requireNotNull(AbilityDescriptionMaterializer.materialize(rom, selected))
        assertEquals(19, prose.descriptions.size)
        assertNull(AbilityMechanicsMaterializer.materialize(rom, selected, abilities, abilityDescriptions = prose))
    }

    @Test fun completeMaterializerKeepsNativeEdgesAndPublishesOnlyOwnedLocalizedText() {
        val fixture = CompiledAbilityTextFixture()
        putGbaPointer(fixture.bytes, fixture.descriptions.offset.toInt() + 2 * 4, -0x08000000)
        val result = semantic(fixture)
        val selected = layout(fixture, result)
        val analysis = ParseResult(
            header = RomHeader(Platform.GBA, "SYNTHETIC"), sha256 = "0".repeat(64), crc32 = "00000000",
            size = fixture.bytes.size, status = SelectionStatus.SELECTED, selectedFamily = EngineFamily.EMERALD,
            selectedProfile = null, runnerUpMargin = null, probes = emptyList(), capabilities = emptyList(),
        )
        val catalog = CatalogMaterializer.materialize(RomImage(fixture.bytes), analysis, selected)
        val overlay = catalog.localization.overlays.getValue(PokemonTextCodec.gbaEnglish.language)
        assertEquals(19, catalog.abilitiesById.size)
        assertNull(catalog.abilitiesById.getValue(19).name.value)
        assertNull(catalog.abilitiesById.getValue(19).description.value)
        assertEquals("ABILITY 19", overlay.abilityNames.getValue(19).value)
        assertFalse(2 in overlay.abilityDescriptions)
        assertEquals("Binds a native modifier.", overlay.abilityDescriptions.getValue(19).value)
        assertEquals(18, overlay.localizedCapabilities.getValue(LocalizedTextCapability.ABILITY_DESCRIPTIONS).coveredRecords)
        assertEquals(19, overlay.localizedCapabilities.getValue(LocalizedTextCapability.ABILITY_DESCRIPTIONS).expectedRecords)
        assertEquals(listOf(7, 12), catalog.speciesById.getValue(3).abilityIds.value)
        assertTrue(catalog.abilitiesById.values.all { it.mechanics.value == null })
    }

    @Test fun selectedNativeRowsAndDescriptionEvidenceRemainImmutable() {
        val fixture = CompiledAbilityTextFixture()
        val names = requireNotNull(semantic(fixture).resolvedAbilityNames)
        val descriptions = requireNotNull(names.compiledDescriptions)
        assertThrows(UnsupportedOperationException::class.java) { (names.rows as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (descriptions.rows as MutableList<*>).clear() }
        assertEquals(20, names.rows.size)
        assertEquals(20, descriptions.rows.size)
        assertNotEquals(fixture.names, names.table)
        assertTrue(names.table.terminatedInlineArray)
    }

    @Test fun lostGetterAuthorityAndBudgetCannotPromoteTheCoherentDecoy() {
        for (budget in listOf(false, true)) {
            val fixture = CompiledAbilityTextFixture()
            if (!budget) fixture.half(fixture.getterLoad, 0x7808)
            val result = semantic(fixture,
                if (budget) ResolutionLimits(maxProbeWorkPerDataset = 1) else ResolutionLimits())
            assertFalse(result.abilities.compatible)
            assertNull(result.resolvedAbilityNames)
            assertTrue(result.coreDatasets.speciesNames.compatible)
            assertTrue(result.coreDatasets.baseStats.compatible)
        }
    }

    private fun speciesNames(fixture: CompiledAbilityTextFixture) = TableLayout(0x7000 + fixture.relocation, 4, 12)

    private fun semantic(
        fixture: CompiledAbilityTextFixture,
        limits: ResolutionLimits = ResolutionLimits(),
    ): SemanticDomainPhaseResult.Resolved {
        val codec = PokemonTextCodec.gbaEnglish
        val names = speciesNames(fixture)
        putAbilityNames(fixture.bytes, AbilityNameTableLayout(names.offset, names.count, names.recordSize),
            listOf("-", "Mossling", "Emberling", "Brookling"))
        val manifest = RomLanguageManifest(codec.language, listOf(RomLanguageProjection(
            codec.language, codec.id, codec.version, LocalizedTableLayout(speciesNames = names),
            emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val evidence = ValidationEvidence(true, 4, 4, 1.0, emptyList())
        val abilityTable = TableLayout(fixture.names.offset.toInt(), 20, 17)
        val core = CoreDatasetsPhaseResult.Resolved(
            ProfileTables(speciesNames = names, baseStats = fixture.core, abilities = abilityTable),
            4, 1, 1, evidence, evidence, evidence, evidence,
            speciesNamesLayout = names, baseStatsLayout = fixture.core, moveNamesLayout = null, moveDataLayout = null,
            languageManifest = manifest)
        val identity = IdentityRootsPhaseResult.Resolved(
            exactProfile = null, baseProfile = null, identityMatched = true, scoreEvidence = emptyList(),
            expansion = null, compiledGbaReferences = null,
            tableResolution = ProfileTableResolution(ProfileTables()), probeCodec = codec)
        val state = FamilyProbeState.empty().withIdentityRoots(identity).withCoreDatasets(core)
        return SemanticDomainStrategy().execute(
            RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "SYNTHETIC"), limits = limits),
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD), state,
        ).semanticDomain as SemanticDomainPhaseResult.Resolved
    }

    private fun layout(fixture: CompiledAbilityTextFixture, result: SemanticDomainPhaseResult.Resolved) =
        ResolvedRomLayout(
            family = EngineFamily.EMERALD, generation = 3, platform = Platform.GBA, speciesCount = 4, moveCount = 1,
            tables = ProfileTables(speciesNames = speciesNames(fixture), baseStats = fixture.core,
                abilities = result.abilitiesLayout),
            resolvedDatasets = ResolvedDatasetLayouts(abilityNames = result.resolvedAbilityNames),
            languageManifest = result.coreDatasets.languageManifest,
        )
}
