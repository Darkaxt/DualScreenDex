package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.putAbilityNames
import com.enrpau.dualscreendex.parser.dataset.abilities.putGbaPointer
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.parse.CompiledCachedAbilityFixture
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityTextResolution
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityTextResolver
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test

class CompiledCachedAbilityIntegrationTest {
    @Test fun completeCatalogJoinsDisjointCoreGroupsThroughOwnedCachedText() {
        for (relocation in listOf(0, 0x400)) {
            val fixture = CompiledCachedAbilityFixture(relocation)
            for (id in listOf(381, 386)) {
                val start = fixture.names.offset.toInt() + id * fixture.names.stride
                fixture.bytes.fill(0, start, start + fixture.names.nameWidth)
                putGbaPointer(fixture.bytes, fixture.descriptions.offset.toInt() + id * 4, -0x08000000)
            }
            val result = semantic(fixture)
            assertTrue("the compiled cache bridge must establish native ability ownership", result.abilities.compatible)
            val names = requireNotNull(result.resolvedAbilityNames)
            assertEquals(617, names.baseRowCount)
            val speciesNames = speciesNames(fixture)
            val selected = ResolvedRomLayout(
                family = EngineFamily.EMERALD, generation = 3, platform = Platform.GBA, speciesCount = 4, moveCount = 1,
                tables = ProfileTables(speciesNames = speciesNames, baseStats = fixture.core, abilities = result.abilitiesLayout),
                resolvedDatasets = ResolvedDatasetLayouts(abilityNames = names), languageManifest = result.coreDatasets.languageManifest,
            )
            val analysis = ParseResult(
                header = RomHeader(Platform.GBA, "SYNTHETIC"), sha256 = "0".repeat(64), crc32 = "00000000",
                size = fixture.bytes.size, status = SelectionStatus.SELECTED, selectedFamily = EngineFamily.EMERALD,
                selectedProfile = null, runnerUpMargin = null, probes = emptyList(), capabilities = emptyList(),
            )
            val catalog = CatalogMaterializer.materialize(RomImage(fixture.bytes), analysis, selected)
            assertEquals(listOf(1, 2, 513, 615), catalog.speciesById.getValue(1).abilityIds.value)
            assertEquals(listOf(4, 610), catalog.speciesById.getValue(2).abilityIds.value)
            assertEquals(listOf(7, 12, 14, 611), catalog.speciesById.getValue(3).abilityIds.value)
            assertTrue(catalog.speciesById.values.none { 35072 in it.abilityIds.value.orEmpty() || 72 in it.abilityIds.value.orEmpty() })
            // Unreferenced malformed cells do not invent shared ability records.
            assertEquals(614, catalog.abilitiesById.size)
            val overlay = catalog.localization.overlays.getValue(PokemonTextCodec.gbaEnglish.language)
            assertEquals("ABILITY 615", overlay.abilityNames.getValue(615).value)
            assertFalse(381 in overlay.abilityNames)
            assertFalse(386 in overlay.abilityDescriptions)
            assertEquals("Binds a native modifier.", overlay.abilityDescriptions.getValue(615).value)
            assertEquals(614, overlay.localizedCapabilities.getValue(LocalizedTextCapability.ABILITY_DESCRIPTIONS).coveredRecords)
            assertTrue(catalog.abilitiesById.values.all { it.mechanics.value == null })
        }
    }

    @Test fun runtimeTransformationsAreExcludedRatherThanExecutedOrInferred() {
        val fixture = CompiledCachedAbilityFixture(runtimeBranches = true)
        val result = semantic(fixture)
        assertTrue(result.abilities.compatible)
        assertTrue(result.abilities.reasons.any { "static default" in it && "not modeled" in it })
        assertEquals(fixture.fields, requireNotNull(result.resolvedAbilityNames).compiledTextBinding?.speciesSlots?.offsets)
    }

    @Test fun cacheOwnershipRejectsBrokenJoinsAndOverlappingGroups() {
        val mutations: List<Pair<String, (CompiledCachedAbilityFixture) -> Unit>> = listOf(
            "different setter field" to { it.half(it.objectSetter + 6, 0x304C) },
            "byte getter" to { it.half(it.objectGetter + 10, 0x7800) },
            "overlapping core groups" to { it.half(it.innateLoad - 4, 0x3218) },
            "all channels select zero" to { it.half(it.sourceProducer + 18, 0x2200) },
            "different description ID" to { it.half(it.descriptionRootSites.first() + 2, 0x1830) },
            "unsupported getter" to { it.half(it.baseGetter, 0xDE00) },
            "cyclic channel wrapper" to { it.half(it.channelGetter, 0xE7FE) },
        )
        for ((label, mutate) in mutations) {
            val fixture = CompiledCachedAbilityFixture()
            mutate(fixture)
            val proof = CompiledAbilityTextResolver.resolve(
                RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "SYNTHETIC")), fixture.core)
            assertFalse(label, proof is CompiledAbilityTextResolution.Resolved)
        }
    }

    @Test fun unrelatedCounterLoopCannotExhaustCachedOwnershipProbe() {
        val fixture = CompiledCachedAbilityFixture()
        fixture.addUnrelatedProducerLoop()
        val proof = CompiledAbilityTextResolver.resolve(
            RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "SYNTHETIC")), fixture.core)
        assertTrue(proof.toString(), proof is CompiledAbilityTextResolution.Resolved)
    }

    @Test fun onlyProvenPostReturnLiteralDataIsExcludedFromCodeConsumers() {
        for (validReturn in listOf(true, false)) {
            val fixture = CompiledCachedAbilityFixture()
            fixture.addLiteralPoolDecoy(validReturn)
            val proof = CompiledAbilityTextResolver.resolve(
                RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "SYNTHETIC")), fixture.core)
            assertEquals(validReturn, proof is CompiledAbilityTextResolution.Resolved)
        }
    }

    @Test fun selectedCoreAndWorkBudgetRemainMandatory() {
        val fixture = CompiledCachedAbilityFixture()
        val image = RomImage(fixture.bytes)
        val header = RomHeader(Platform.GBA, "SYNTHETIC")
        assertTrue(CompiledAbilityTextResolver.resolve(RomAnalysisSession(image, header), fixture.core.copy(offset = fixture.core.offset + 40))
            is CompiledAbilityTextResolution.Unavailable)
        assertTrue(CompiledAbilityTextResolver.resolve(RomAnalysisSession(image, header,
            limits = ResolutionLimits(maxProbeWorkPerDataset = 16)), fixture.core) is CompiledAbilityTextResolution.BudgetExceeded)
    }

    private fun speciesNames(fixture: CompiledCachedAbilityFixture) = TableLayout(0x7000 + fixture.relocation, 4, 12)

    private fun semantic(fixture: CompiledCachedAbilityFixture): SemanticDomainPhaseResult.Resolved {
        val codec = PokemonTextCodec.gbaEnglish
        val names = speciesNames(fixture)
        putAbilityNames(fixture.bytes, AbilityNameTableLayout(names.offset, names.count, names.recordSize),
            listOf("-", "Mossling", "Emberling", "Brookling"))
        val manifest = RomLanguageManifest(codec.language, listOf(RomLanguageProjection(
            codec.language, codec.id, codec.version, LocalizedTableLayout(speciesNames = names),
            emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val evidence = ValidationEvidence(true, 4, 4, 1.0, emptyList())
        val core = CoreDatasetsPhaseResult.Resolved(
            ProfileTables(speciesNames = names, baseStats = fixture.core,
                abilities = TableLayout(fixture.names.offset.toInt(), 617, 19)),
            4, 1, 1, evidence, evidence, evidence, evidence,
            speciesNamesLayout = names, baseStatsLayout = fixture.core, moveNamesLayout = null, moveDataLayout = null,
            languageManifest = manifest,
        )
        val identity = IdentityRootsPhaseResult.Resolved(
            exactProfile = null, baseProfile = null, identityMatched = true, scoreEvidence = emptyList(),
            expansion = null, compiledGbaReferences = null, tableResolution = ProfileTableResolution(ProfileTables()), probeCodec = codec,
        )
        return SemanticDomainStrategy().execute(
            RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "SYNTHETIC")),
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD),
            FamilyProbeState.empty().withIdentityRoots(identity).withCoreDatasets(core),
        ).semanticDomain as SemanticDomainPhaseResult.Resolved
    }
}
