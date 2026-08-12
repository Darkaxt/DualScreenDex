package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FamilyProbeCoordinatorTest {
    @Test
    fun executesImmutableDependencyPhasesInOrderAndFeedsRecoveredCoreEvidenceToDependents() {
        val observed = mutableListOf<FamilyProbePhase>()
        val strategies = FamilyProbeStrategies(
            identityRoots = phase(FamilyProbePhase.IDENTITY_ROOTS, observed) { state -> state },
            coreDatasets = phase(FamilyProbePhase.CORE_DATASETS, observed) { state ->
                state.withCapability(available(RomCapability.BASE_STATS))
            },
            semanticDomain = phase(FamilyProbePhase.SEMANTIC_DOMAIN, observed) { state ->
                assertEquals(CapabilityStatus.AVAILABLE, state.capabilities.getValue(RomCapability.BASE_STATS).status)
                state
            },
            dependentDatasets = phase(FamilyProbePhase.DEPENDENT_DATASETS, observed) { state ->
                state.withCapability(available(RomCapability.ABILITIES))
            },
            capabilityAggregation = phase(FamilyProbePhase.CAPABILITY_AGGREGATION, observed) { state ->
                state.withProbe(probe(state.capabilities.values.toList()))
            },
        )

        val result = FamilyProbeCoordinator(strategies).probe(session(), definition())

        assertEquals(FamilyProbePhase.entries, observed)
        assertEquals(2, result.capabilities.size)
        assertTrue(result.capabilities.all { it.status == CapabilityStatus.AVAILABLE })
    }

    @Test
    fun dependentDatasetFailureDoesNotClearIndependentCoreCapabilities() {
        val strategies = FamilyProbeStrategies(
            identityRoots = passthrough(),
            coreDatasets = FamilyProbePhaseStrategy { _, _, state ->
                state.withCapability(available(RomCapability.BASE_STATS))
            },
            semanticDomain = passthrough(),
            dependentDatasets = FamilyProbePhaseStrategy { _, _, state ->
                state.withCapability(
                    CapabilityEvidence(
                        capability = RomCapability.ABILITIES,
                        compatible = false,
                        confidence = 0.0,
                        reasons = listOf("ability resolver failed closed"),
                        status = CapabilityStatus.NOT_FOUND,
                    ),
                )
            },
            capabilityAggregation = FamilyProbePhaseStrategy { _, _, state ->
                state.withProbe(probe(state.capabilities.values.toList()))
            },
        )

        val byCapability = FamilyProbeCoordinator(strategies)
            .probe(session(), definition())
            .capabilities
            .associateBy { it.capability }

        assertEquals(CapabilityStatus.AVAILABLE, byCapability.getValue(RomCapability.BASE_STATS).status)
        assertEquals(CapabilityStatus.NOT_FOUND, byCapability.getValue(RomCapability.ABILITIES).status)
    }

    @Test
    fun phaseStateHasTypedSlotsAndNoArbitraryMutableArtifactEscapeHatch() {
        val source = Files.readString(productionSourceRoot().resolve(
            "com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinator.kt",
        ))

        assertFalse(source.contains("Map<String, Any>"))
        assertFalse(source.contains("withArtifact"))
        assertFalse(source.contains("fun <T : Any> artifact"))
        listOf("identityRoots", "coreDatasets", "semanticDomain", "dependentDatasets").forEach { slot ->
            assertTrue("missing typed phase slot $slot", source.contains("val $slot:"))
        }
    }

    @Test
    fun successorAndCallerMutationCannotChangeAPredecessorSnapshot() {
        val mutableReasons = mutableListOf("validated core")
        val predecessor = FamilyProbeState.empty().withCapability(
            available(RomCapability.BASE_STATS).copy(reasons = mutableReasons),
        )

        mutableReasons += "mutated after publication"
        val successor = predecessor.withCapability(available(RomCapability.ABILITIES))

        assertEquals(listOf("validated core"), predecessor.capabilities.getValue(RomCapability.BASE_STATS).reasons)
        assertEquals(setOf(RomCapability.BASE_STATS), predecessor.capabilities.keys)
        assertEquals(setOf(RomCapability.BASE_STATS, RomCapability.ABILITIES), successor.capabilities.keys)
    }

    @Test
    fun typedIdentityCoreAndProbeResultsSnapshotNestedCallerCollections() {
        val identityBanks = mutableListOf(1)
        val identityReasons = mutableListOf("published")
        val referenceCounts = mutableMapOf(0x100 to 2)
        val identity = IdentityRootsPhaseResult.Resolved(
            exactProfile = null,
            baseProfile = FamilyProfileBasis(
                "ancestor",
                3,
                4,
                ProfileTables(speciesNames = TableLayout(0x100, 3, 11, banks = identityBanks)),
            ),
            identityMatched = true,
            scoreEvidence = emptyList(),
            expansion = null,
            compiledGbaReferences = GbaCompiledReferenceIndex(referenceCounts),
            tableResolution = ProfileTableResolution(
                ProfileTables(),
                publishedDataEvidence = evidence(true, 0x100, 3, 11).copy(reasons = identityReasons),
            ),
            codec = PokemonTextCodec.gbaEnglish,
        )
        identityBanks += 2
        identityReasons += "mutated"
        referenceCounts[0x200] = 9

        assertEquals(listOf(1), identity.baseProfile?.tables?.speciesNames?.banks)
        assertEquals(listOf("published"), identity.tableResolution.publishedDataEvidence?.reasons)
        assertEquals(mapOf(0x100 to 2), identity.compiledGbaReferences?.counts)

        val coreBanks = mutableListOf(3)
        val coreReasons = mutableListOf("core")
        val core = CoreDatasetsPhaseResult.Resolved(
            candidateTables = ProfileTables(baseStats = TableLayout(0x240, 3, 28, banks = coreBanks)),
            speciesCount = 3,
            inferredMoveCount = 4,
            moveCount = 4,
            speciesNames = evidence(true, 0x100, 3, 11).copy(reasons = coreReasons),
            baseStats = evidence(true, 0x240, 3, 28),
            moveNames = evidence(true, 0x300, 4, 13),
            moveData = evidence(true, 0x400, 4, 12),
            speciesNamesLayout = TableLayout(0x100, 3, 11),
            baseStatsLayout = TableLayout(0x240, 3, 28),
            moveNamesLayout = TableLayout(0x300, 4, 13),
            moveDataLayout = TableLayout(0x400, 4, 12),
        )
        coreBanks += 4
        coreReasons += "mutated"

        assertEquals(listOf(3), core.candidateTables.baseStats?.banks)
        assertEquals(listOf("core"), core.speciesNames.reasons)

        val probeBanks = mutableListOf(5)
        val probeReasons = mutableListOf("probe")
        val sourceProbe = probe(
            listOf(available(RomCapability.BASE_STATS).copy(reasons = probeReasons)),
        ).copy(
            resolvedLayout = ResolvedRomLayout(
                family = EngineFamily.EMERALD,
                generation = 3,
                platform = Platform.GBA,
                speciesCount = 3,
                moveCount = 4,
                tables = ProfileTables(baseStats = TableLayout(0x240, 3, 28, banks = probeBanks)),
            ),
        )
        val probeState = FamilyProbeState.empty().withProbe(sourceProbe)
        probeBanks += 6
        probeReasons += "mutated"

        assertEquals(listOf(5), probeState.probe?.resolvedLayout?.tables?.baseStats?.banks)
        assertEquals(listOf("probe"), probeState.probe?.capabilities?.single()?.reasons)
    }

    @Test
    fun coreResultMakesRecoveredStatsVisibleAndKeepsIndependentFailuresIsolated() {
        val identity = resolvedIdentity()
        val recoveredStats = TableLayout(offset = 0x240, count = 3, recordSize = 28)
        val core = CoreDatasetsPhaseResult.Resolved(
            candidateTables = ProfileTables(baseStats = recoveredStats),
            speciesCount = 3,
            inferredMoveCount = 4,
            moveCount = 4,
            speciesNames = evidence(compatible = true, offset = 0x100, count = 3, recordSize = 11),
            baseStats = evidence(compatible = false, offset = 0x240, count = 3, recordSize = 28),
            moveNames = evidence(compatible = true, offset = 0x300, count = 4, recordSize = 13),
            moveData = evidence(compatible = true, offset = 0x400, count = 4, recordSize = 12),
            speciesNamesLayout = TableLayout(0x100, 3, 11),
            baseStatsLayout = recoveredStats,
            moveNamesLayout = TableLayout(0x300, 4, 13),
            moveDataLayout = TableLayout(0x400, 4, 12),
        )
        var semanticObservedCore = false
        val strategies = FamilyProbeStrategies(
            identityRoots = FamilyProbePhaseStrategy { _, _, state -> state.withIdentityRoots(identity) },
            coreDatasets = FamilyProbePhaseStrategy { _, _, state ->
                assertTrue(state.identityRoots === identity)
                state.withCoreDatasets(core)
            },
            semanticDomain = FamilyProbePhaseStrategy { _, _, state ->
                val resolved = state.coreDatasets as CoreDatasetsPhaseResult.Resolved
                semanticObservedCore = true
                assertEquals(recoveredStats, resolved.baseStatsLayout)
                assertTrue(resolved.speciesNames.compatible)
                assertFalse(resolved.baseStats.compatible)
                assertTrue(resolved.moveNames.compatible)
                assertTrue(resolved.moveData.compatible)
                state
            },
            dependentDatasets = passthrough(),
            capabilityAggregation = FamilyProbePhaseStrategy { _, _, state -> state.withProbe(probe(emptyList())) },
        )

        FamilyProbeCoordinator(strategies).probe(session(), definition())

        assertTrue(semanticObservedCore)
    }

    @Test
    fun exactProfileCountsAndCoreRootsRemainAuthoritative() {
        val bytes = ByteArray(4096)
        val rom = RomImage(bytes)
        val names = TableLayout(0x100, 3, 11)
        val stats = TableLayout(0x200, 3, 28)
        val moveNames = TableLayout(0x300, 4, 13)
        val moveData = TableLayout(0x400, 4, 12)
        val profile = RomProfile(
            name = "synthetic exact Emerald",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "POKEMON EMER",
            gameCode = "BPEE",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = 2,
            internalSpeciesCount = 3,
            moveCount = 3,
            tables = ProfileTables(
                speciesNames = names,
                baseStats = stats,
                moveNames = moveNames,
                moveData = moveData,
            ),
        )
        val session = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"),
            exactProfile = profile,
        )
        var state = IdentityRootsStrategy().execute(session, definition(), FamilyProbeState.empty())
        state = CoreDatasetsStrategy().execute(session, definition(), state)
        val core = state.coreDatasets as CoreDatasetsPhaseResult.Resolved

        assertEquals(3, core.speciesCount)
        assertEquals(4, core.inferredMoveCount)
        assertEquals(names.offset, core.candidateTables.speciesNames?.offset)
        assertEquals(stats.offset, core.candidateTables.baseStats?.offset)
        assertEquals(moveNames.offset, core.candidateTables.moveNames?.offset)
        assertEquals(moveData.offset, core.candidateTables.moveData?.offset)
        assertEquals(names, core.speciesNamesLayout)
        assertEquals(stats, core.baseStatsLayout)
        assertEquals(moveNames, core.moveNamesLayout)
        assertEquals(moveData, core.moveDataLayout)
    }

    @Test
    fun semanticPartialPromotionPreservesValidatedCloudWhiteTwoDescriptionAuthority() {
        val configured = System.getenv("DUALDEX_CLOUD_WHITE_2_ROM")
        assumeTrue("set DUALDEX_CLOUD_WHITE_2_ROM to run this live regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("Cloud White 2 ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("6d9075a559c289eee4f336c925b46fdba55f34c6baa0576626d4a3b71513d879", rom.sha256)
        val session = RomAnalysisSession(rom, RomHeaderReader.read(rom))
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.FIRERED_LEAFGREEN)
        var state = IdentityRootsStrategy().execute(session, definition, FamilyProbeState.empty())
        state = CoreDatasetsStrategy().execute(session, definition, state)
        val structural = state.coreDatasets as CoreDatasetsPhaseResult.Resolved

        assertNotNull(structural.publishedPartialBaseStatsCandidate)
        assertFalse(structural.baseStats.compatible)

        state = SemanticDomainStrategy().execute(session, definition, state)
        val semantic = state.semanticDomain as SemanticDomainPhaseResult.Resolved

        assertEquals(944, semantic.descriptionsLayout?.count)
        assertTrue(semantic.coreDatasets.baseStats.compatible)
        assertEquals(861, semantic.coreDatasets.baseStats.coveredRecords)
        assertEquals(943, semantic.coreDatasets.baseStats.expectedRecords)
    }

    @Test
    fun rejectedIdentityRootsAreTerminalBeforeAnyLaterPhaseCanOverrideTheProbe() {
        var laterPhases = 0
        val rejected = probe(emptyList()).copy(hardGatePassed = false, score = 0)
        val replacement = probe(listOf(available(RomCapability.BASE_STATS)))
        val later = FamilyProbePhaseStrategy { _, _, state ->
            laterPhases++
            state
        }
        val strategies = FamilyProbeStrategies(
            identityRoots = FamilyProbePhaseStrategy { _, _, state ->
                state.withIdentityRoots(IdentityRootsPhaseResult.Rejected(rejected))
            },
            coreDatasets = later,
            semanticDomain = later,
            dependentDatasets = later,
            capabilityAggregation = FamilyProbePhaseStrategy { _, _, state ->
                laterPhases++
                state.withProbe(replacement)
            },
        )

        val result = FamilyProbeCoordinator(strategies).probe(session(), definition())

        assertEquals(rejected, result)
        assertEquals(0, laterPhases)
    }

    @Test
    fun terminalRejectedIdentitySnapshotsNestedProbeAliases() {
        val reasons = mutableListOf("wrong platform")
        val banks = mutableListOf(1)
        val rejectedSource = probe(
            listOf(available(RomCapability.BASE_STATS).copy(reasons = reasons)),
        ).copy(
            hardGatePassed = false,
            resolvedLayout = ResolvedRomLayout(
                family = EngineFamily.EMERALD,
                generation = 3,
                platform = Platform.GBA,
                speciesCount = 3,
                moveCount = 4,
                tables = ProfileTables(baseStats = TableLayout(0x240, 3, 28, banks = banks)),
            ),
        )
        val strategies = FamilyProbeStrategies(
            identityRoots = FamilyProbePhaseStrategy { _, _, state ->
                state.withIdentityRoots(IdentityRootsPhaseResult.Rejected(rejectedSource))
            },
            coreDatasets = passthrough(),
            semanticDomain = passthrough(),
            dependentDatasets = passthrough(),
            capabilityAggregation = passthrough(),
        )

        val rejected = FamilyProbeCoordinator(strategies).probe(session(), definition())
        reasons += "mutated"
        banks += 2

        assertEquals(listOf("wrong platform"), rejected.capabilities.single().reasons)
        assertEquals(listOf(1), rejected.resolvedLayout?.tables?.baseStats?.banks)
    }

    @Test
    fun familyApplicabilityAndLineageCompositionAreIndependentFromNumericGeneration() {
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD)

        assertEquals(3, definition.formatGeneration)
        assertTrue(definition.applicableCapabilities.contains(RomCapability.ABILITIES))
        assertTrue(definition.lineages.contains(EngineLineage.GEN3_RETAIL))
        assertTrue(definition.lineages.contains(EngineLineage.CFRU))
        assertTrue(definition.lineages.contains(EngineLineage.POKEEMERALD_EXPANSION))
        assertFalse(
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.RED_BLUE)
                .applicableCapabilities.contains(RomCapability.ABILITIES),
        )
    }

    @Test
    fun aggregationUsesDeclaredApplicabilityInsteadOfNumericGenerationOrGlobalDefaults() {
        val abilityApplicable = EngineFamilyDefinition(
            family = EngineFamily.RED_BLUE,
            formatGeneration = 1,
            expectedPlatform = Platform.GB,
            compatiblePlatforms = setOf(Platform.GB),
            lineages = setOf(EngineLineage.GEN1_RETAIL),
            applicableCapabilities = setOf(RomCapability.ABILITIES, RomCapability.MOVE_DESCRIPTIONS),
            identityRule = { true },
        )
        val spriteApplicable = EngineFamilyDefinition(
            family = EngineFamily.YELLOW,
            formatGeneration = 1,
            expectedPlatform = Platform.GB,
            compatiblePlatforms = setOf(Platform.GB),
            lineages = setOf(EngineLineage.GEN1_RETAIL),
            applicableCapabilities = setOf(RomCapability.SPRITES, RomCapability.MOVE_DESCRIPTIONS),
            identityRule = { true },
        )
        val abilities = available(RomCapability.ABILITIES)
        val sprites = available(RomCapability.SPRITES)

        val abilityResolved = applyCapabilityApplicability(abilityApplicable, listOf(abilities, sprites))
            .associateBy(CapabilityEvidence::capability)
        val spriteResolved = applyCapabilityApplicability(spriteApplicable, listOf(abilities, sprites))
            .associateBy(CapabilityEvidence::capability)

        assertEquals(RomCapability.entries.size, abilityResolved.size)
        assertEquals(RomCapability.entries.size, spriteResolved.size)
        assertEquals(CapabilityStatus.AVAILABLE, abilityResolved.getValue(RomCapability.ABILITIES).status)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, spriteResolved.getValue(RomCapability.ABILITIES).status)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, abilityResolved.getValue(RomCapability.SPRITES).status)
        assertEquals(CapabilityStatus.AVAILABLE, spriteResolved.getValue(RomCapability.SPRITES).status)
        assertEquals(CapabilityStatus.NOT_FOUND, abilityResolved.getValue(RomCapability.MOVE_DESCRIPTIONS).status)
        assertEquals(CapabilityStatus.NOT_FOUND, spriteResolved.getValue(RomCapability.MOVE_DESCRIPTIONS).status)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, abilityResolved.getValue(RomCapability.BASE_STATS).status)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, spriteResolved.getValue(RomCapability.BASE_STATS).status)
    }

    @Test
    fun exactProfilesAreAuthoritativeOnlyForTheirMatchingFamilyDefinition() {
        val rom = RomImage(ByteArray(512))
        val profile = RomProfile(
            name = "synthetic exact Emerald",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "POKEMON EMER",
            gameCode = "BPEE",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = 1,
            internalSpeciesCount = 1,
            moveCount = 1,
            tables = ProfileTables(),
        )
        val exactSession = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"),
            exactProfile = profile,
        )
        val exact = exactSession.exactProfileSnapshot

        assertEquals(
            exact,
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD).authoritativeExactProfile(exactSession),
        )
        assertNull(
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.FIRERED_LEAFGREEN)
                .authoritativeExactProfile(exactSession),
        )
    }

    @Test
    fun compatibilityFacadeContainsOnlyDelegationForTheMigratedProbeEntrypoint() {
        val source = Files.readString(productionSourceRoot().resolve(
            "com/enrpau/dualscreendex/parser/parse/FamilyParsers.kt",
        ))
        val forbidden = listOf(
            "TableValidators",
            "DatasetResolvers",
            "PokemonDatasetValidators",
            "SpriteValidators",
            "GbaPublishedHeaderResolver",
            "PokeemeraldExpansionResolver",
            "while (",
            ".findAll(",
        )

        assertTrue(
            "FamilyParsers must be a delegation-only compatibility facade; found " +
                forbidden.filter(source::contains),
            forbidden.none(source::contains),
        )
        assertTrue(source.contains("FamilyProbeCoordinator"))
        assertTrue(source.contains("EngineFamilyDefinitions"))
    }

    @Test
    fun productionCoordinatorOwnsIdentityRootResolutionInsteadOfTheLegacyProbe() {
        val root = productionSourceRoot()
        val coordinator = Files.readString(root.resolve(
            "com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinator.kt",
        ))
        val identityPhase = root.resolve(
            "com/enrpau/dualscreendex/parser/family/IdentityRootsStrategy.kt",
        )
        val legacyPath = root.resolve("com/enrpau/dualscreendex/parser/parse/LegacyFamilyProbe.kt")
        val legacy = if (Files.isRegularFile(legacyPath)) Files.readString(legacyPath) else ""

        assertTrue(Files.isRegularFile(identityPhase))
        val identitySource = Files.readString(identityPhase)
        listOf(
            "class IdentityRootsStrategy",
            "sealed interface IdentityRootsPhaseResult",
            "GbaPublishedHeaderResolver",
            "PokeemeraldExpansionResolver",
            "KnownProfiles.forFamily",
        ).forEach { required -> assertTrue("identity phase missing $required", identitySource.contains(required)) }
        assertTrue(coordinator.contains("IdentityRootsStrategy()"))
        listOf(
            "closestProfile(",
            "identityMatches(",
            "resolveTables(",
            "GbaPublishedHeaderResolver",
            "locateRubySapphireNames(",
        ).forEach { forbidden ->
            assertFalse("legacy probe still owns identity/root logic: $forbidden", legacy.contains(forbidden))
        }
    }

    @Test
    fun productionCoordinatorOwnsCoreDatasetResolutionInsteadOfTheLegacyProbe() {
        val root = productionSourceRoot()
        val coordinator = Files.readString(root.resolve(
            "com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinator.kt",
        ))
        val corePhase = root.resolve(
            "com/enrpau/dualscreendex/parser/family/CoreDatasetsStrategy.kt",
        )
        val legacyPath = root.resolve("com/enrpau/dualscreendex/parser/parse/LegacyFamilyProbe.kt")
        val legacy = if (Files.isRegularFile(legacyPath)) Files.readString(legacyPath) else ""

        assertTrue(Files.isRegularFile(corePhase))
        val coreSource = Files.readString(corePhase)
        listOf(
            "class CoreDatasetsStrategy",
            "sealed interface CoreDatasetsPhaseResult",
            "Gen3DynamicTableResolver",
            "Gen3PublishedPartialBaseStatsResolver",
            "reconciledMoveCount",
        ).forEach { required -> assertTrue("core phase missing $required", coreSource.contains(required)) }
        assertTrue(coordinator.contains("CoreDatasetsStrategy()"))
        listOf(
            "inferSpeciesCount(",
            "inferMoveCount(",
            "Gen3DynamicTableResolver",
            "Gen3PublishedPartialBaseStatsResolver",
            "locateFixedNameTable(",
            "locateBaseStatTable(",
            "locateVariableNameSequenceNear(",
            "reconciledMoveCount(",
            "pokeemeraldExpansionMoveData(",
            "cfruMoveData(",
            "battleEngineMoveData(",
        ).forEach { forbidden ->
            assertFalse("legacy probe still owns core dataset discovery: $forbidden", legacy.contains(forbidden))
        }
    }

    @Test
    fun productionStrategiesOwnRemainingDatasetsAndAggregationWithoutALegacyMonolith() {
        val root = productionSourceRoot()
        val coordinator = Files.readString(root.resolve(
            "com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinator.kt",
        ))
        val semantic = Files.readString(root.resolve(
            "com/enrpau/dualscreendex/parser/family/SemanticDomainStrategy.kt",
        ))
        val dependentPath = root.resolve(
            "com/enrpau/dualscreendex/parser/family/DependentDatasetsStrategy.kt",
        )
        val aggregationPath = root.resolve(
            "com/enrpau/dualscreendex/parser/family/CapabilityAggregationStrategy.kt",
        )
        val legacyPath = root.resolve(
            "com/enrpau/dualscreendex/parser/parse/LegacyFamilyProbe.kt",
        )

        assertTrue(Files.isRegularFile(dependentPath))
        assertTrue(Files.isRegularFile(aggregationPath))
        val dependent = Files.readString(dependentPath)
        val aggregation = Files.readString(aggregationPath)
        listOf("validateDescriptions", "resolveGen3TypeChart", "resolveAbilities").forEach { required ->
            assertTrue("semantic phase missing $required", semantic.contains(required))
        }
        listOf("SpriteValidators", "gen3Evolutions", "gen3LearnsetResolution").forEach { required ->
            assertTrue("dependent phase missing $required", dependent.contains(required))
        }
        listOf("buildCapabilities", "ResolvedRomLayout", "ScoreEvidence").forEach { required ->
            assertTrue("aggregation phase missing $required", aggregation.contains(required))
        }
        assertTrue(coordinator.contains("SemanticDomainStrategy()"))
        assertTrue(coordinator.contains("DependentDatasetsStrategy()"))
        assertTrue(coordinator.contains("CapabilityAggregationStrategy()"))
        if (Files.isRegularFile(legacyPath)) {
            val legacy = Files.readString(legacyPath)
            val forbidden = listOf(
                "TableValidators",
                "DatasetResolvers",
                "PokemonDatasetValidators",
                "SpriteValidators",
                "buildCapabilities(",
                "ResolvedRomLayout(",
                "ScoreEvidence(",
            )
            assertTrue(
                "legacy compatibility adapter still owns parsing/aggregation: ${forbidden.filter(legacy::contains)}",
                forbidden.none(legacy::contains),
            )
            assertTrue("legacy adapter must stay tiny", legacy.lineSequence().count() <= 40)
        }
    }

    private fun phase(
        expected: FamilyProbePhase,
        observed: MutableList<FamilyProbePhase>,
        transform: (FamilyProbeState) -> FamilyProbeState,
    ) = FamilyProbePhaseStrategy { _, _, state ->
        observed += expected
        transform(state)
    }

    private fun passthrough() = FamilyProbePhaseStrategy { _, _, state -> state }

    private fun session() = RomAnalysisSession(
        rom = RomImage(ByteArray(512)),
        header = RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"),
    )

    private fun definition() = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD)

    private fun available(capability: RomCapability) = CapabilityEvidence(
        capability = capability,
        compatible = true,
        confidence = 1.0,
        reasons = listOf("synthetic phase evidence"),
    )

    private fun probe(capabilities: List<CapabilityEvidence>) = ParserProbe(
        family = EngineFamily.EMERALD,
        score = 100,
        hardGatePassed = true,
        anchors = 3,
        scoreEvidence = emptyList(),
        capabilities = capabilities,
    )

    private fun resolvedIdentity() = IdentityRootsPhaseResult.Resolved(
        exactProfile = null,
        baseProfile = null,
        identityMatched = true,
        scoreEvidence = emptyList(),
        expansion = null,
        compiledGbaReferences = null,
        tableResolution = ProfileTableResolution(ProfileTables()),
        codec = PokemonTextCodec.gbaEnglish,
    )

    private fun evidence(
        compatible: Boolean,
        offset: Int,
        count: Int,
        recordSize: Int,
    ) = ValidationEvidence(
        compatible = compatible,
        validRecords = if (compatible) count else count - 1,
        totalRecords = count,
        confidence = if (compatible) 1.0 else 0.5,
        reasons = listOf("synthetic core evidence"),
        offset = offset,
        recordSize = recordSize,
    )

    private fun productionSourceRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath()
        return listOf(
            workingDirectory.resolve("parser-core/src/main/kotlin"),
            workingDirectory.resolve("src/main/kotlin"),
        ).firstOrNull(Files::isDirectory)
            ?: error("parser-core production source root not found from $workingDirectory")
    }
}
