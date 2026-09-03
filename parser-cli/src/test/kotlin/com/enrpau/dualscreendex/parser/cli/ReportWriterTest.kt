package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanic
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicCondition
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisition
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisitionMethod
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.LevelUpRulesetSelector
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.google.gson.JsonParser
import java.io.StringWriter
import java.io.Writer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportWriterTest {
    @Test
    fun streamingWritersPreserveSmallReportBytes() {
        val report = CorpusReport(
            roots = listOf("D:/private/roms"),
            results = listOf(CorpusResult("sample.gba", "sample.gba", durationMillis = 1, result = sampleResult())),
        )
        val json = StringWriter()
        val markdown = StringWriter()

        ReportWriter.json(report, json)
        ReportWriter.markdown(report, markdown)

        assertEquals(ReportWriter.json(report), json.toString())
        assertEquals(ReportWriter.markdown(report), markdown.toString())
    }

    @Test
    fun largeReportStreamsWithoutOneAggregateWrite() {
        val results = List(5_000) { index ->
            CorpusResult("sample-$index.gba", "sample-$index.gba", durationMillis = index.toLong())
        }
        val jsonSink = BoundedChunkWriter(maxChunkChars = 4_096)
        val markdownSink = BoundedChunkWriter(maxChunkChars = 4_096)

        val report = CorpusReport(roots = listOf("test"), results = results)
        ReportWriter.json(report, jsonSink)
        ReportWriter.markdown(report, markdownSink)

        assertTrue(jsonSink.totalChars > 4_096)
        assertTrue(jsonSink.maximumChunkChars <= 4_096)
        assertTrue(markdownSink.totalChars > 4_096)
        assertTrue(markdownSink.maximumChunkChars <= 4_096)
    }

    @Test
    fun noFamilyMatchWithConflictingValidatedLocatorsRequiresManualReview() {
        val probes = listOf(
            ParserProbe(
                family = EngineFamily.RED_BLUE,
                score = 70,
                hardGatePassed = true,
                anchors = 2,
                scoreEvidence = emptyList(),
                capabilities = listOf(
                    CapabilityEvidence(
                        RomCapability.SPECIES_NAMES, true, 0.95, offset = 0x1000, count = 151,
                    ),
                ),
            ),
            ParserProbe(
                family = EngineFamily.YELLOW,
                score = 69,
                hardGatePassed = true,
                anchors = 2,
                scoreEvidence = emptyList(),
                capabilities = listOf(
                    CapabilityEvidence(
                        RomCapability.SPECIES_NAMES, true, 0.96, offset = 0x2000, count = 151,
                    ),
                ),
            ),
        )
        val selection = ParserOrchestrator.select(probes)
        val capabilities = ParserOrchestrator.resolveCapabilities(selection, probes)
        val score = calculateCompatibility(
            sampleResult().copy(
                status = selection.status,
                selectedFamily = null,
                selectedProfile = null,
                capabilities = capabilities,
            ),
        )

        assertEquals(SelectionStatus.NO_FAMILY_MATCH, selection.status)
        assertTrue(score.manualReviewRequired)
    }

    @Test
    fun dataStructureCompatibilityDoesNotDependOnFamilySelection() {
        val available = RomCapability.entries.map {
            CapabilityEvidence(it, true, 1.0, offset = 0x100, count = 1)
        }
        val noFamily = sampleResult().copy(
            status = SelectionStatus.NO_FAMILY_MATCH,
            selectedFamily = null,
            selectedProfile = null,
            capabilities = available,
        )
        val partial = CorpusResult(
            "Derivative.gba", "Derivative.gba", durationMillis = 1, result = noFamily,
        )

        assertEquals(DataStructureCompatibility.PARTIAL, partial.dataCompatibility)
    }

    @Test
    fun completeDataStructureCompatibilityRequiresDecodedSamplesAndCleanReferences() {
        val available = RomCapability.entries.map {
            CapabilityEvidence(it, true, 1.0, offset = 0x100, count = 1)
        }
        val catalog = CatalogMetrics(
            species = 1, namedSpecies = 1, speciesWithStats = 1, speciesWithSprites = 1,
            speciesWithDescriptions = 1, evolutionEdges = 1, learnsetEntries = 1,
            learnsetRulesets = 1, moves = 1, movesWithDetails = 1, movesWithDescriptions = 1,
            eggMoveLinks = 1, machineMoveLinks = 1, tutorMoveLinks = 1, types = 1,
            typeMatchups = 1, abilities = 1, abilitiesWithDescriptions = 1,
            abilitiesWithMechanics = 1, captureBalls = 1,
        )
        val samples = CatalogSamples(
            species = listOf("id=1"), moves = listOf("id=1"), types = listOf("id=0"),
            typeChart = emptyList(), evolutions = emptyList(), learnsets = emptyList(),
            abilities = emptyList(), encounters = emptyList(), balls = emptyList(), referenceErrors = emptyList(),
        )
        val entry = CorpusResult(
            "Derivative.gba", "Derivative.gba", durationMillis = 1,
            result = sampleResult().copy(capabilities = available), catalog = catalog, samples = samples,
        )

        assertEquals(DataStructureCompatibility.COMPLETE, entry.dataCompatibility)
    }

    @Test
    fun catalogSamplesExposeLeadingDecodedValuesAndValidateReferences() {
        val species = SpeciesRecord(
            id = 1,
            dexNumber = CatalogField.available(1),
            name = CatalogField.available("Bulbasaur"),
            typeIds = CatalogField.available(listOf(12, 4)),
            baseStats = CatalogField.available(BaseStats(45, 49, 49, 45, 65, 65)),
            sprite = CatalogField.notFound("fixture"),
        )
        val move = MoveRecord(
            id = 1,
            name = CatalogField.available("Pound"),
            typeId = CatalogField.available(0),
            category = CatalogField.available(MoveCategory.PHYSICAL),
            power = CatalogField.available(40),
            accuracy = CatalogField.available(100),
            pp = CatalogField.available(35),
        )
        val catalog = ParsedCatalog(
            romSha256 = "0".repeat(64),
            family = EngineFamily.FIRERED_LEAFGREEN,
            platform = Platform.GBA,
            speciesById = mapOf(1 to species),
            movesById = mapOf(1 to move),
            typesById = mapOf(
                10 to TypeRecord(
                    id = 10,
                    name = CatalogField.available("FEU"),
                    semanticRole = CatalogField.available(TypeSemanticRole.FIRE),
                ),
            ),
        )

        val samples = CatalogSamples.from(catalog)

        assertTrue(samples.species.single().contains("name=Bulbasaur"))
        assertTrue(samples.species.single().contains("stats=45/49/49/45/65/65"))
        assertTrue(samples.moves.single().contains("name=Pound"))
        assertTrue(samples.types.single().contains("name=FEU"))
        assertTrue(samples.types.single().contains("role=FIRE"))
        assertTrue(samples.referenceErrors.any { it.contains("species 1 references missing type 12") })
    }

    @Test
    fun catalogMetricsMeasureCategoriesAndTypedAbilityModifiersIndependently() {
        fun move(id: Int, category: CatalogField<MoveCategory>) = MoveRecord(
            id = id,
            name = CatalogField.available("Move $id"),
            typeId = CatalogField.available(10),
            category = category,
            power = CatalogField.available(40),
            accuracy = CatalogField.available(100),
            pp = CatalogField.available(20),
        )
        val typedModifier = AbilityMechanic(
            kind = AbilityMechanicKind.MULTIPLIER,
            label = "Fire power",
            value = "1.5x",
            numerator = 3,
            denominator = 2,
            conditions = listOf(
                AbilityMechanicCondition(
                    kind = AbilityMechanicConditionKind.ATTACKING_MOVE_TYPE,
                    value = 10,
                    label = "Fire",
                ),
            ),
        )
        val untypedModifier = typedModifier.copy(conditions = emptyList())
        val catalog = ParsedCatalog(
            romSha256 = "0".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            movesById = mapOf(
                1 to move(1, CatalogField.available(MoveCategory.SPECIAL)),
                2 to move(2, CatalogField.available(MoveCategory.UNKNOWN)),
                3 to move(3, CatalogField.notFound("category missing")),
            ),
            typesById = mapOf(
                10 to TypeRecord(
                    id = 10,
                    name = CatalogField.available("FIRE"),
                    semanticRole = CatalogField.available(TypeSemanticRole.FIRE),
                ),
                18 to TypeRecord(
                    id = 18,
                    name = CatalogField.notFound("custom type name missing"),
                ),
            ),
            abilitiesById = mapOf(
                1 to AbilityRecord(
                    id = 1,
                    name = CatalogField.available("Blaze"),
                    mechanics = CatalogField.available(listOf(typedModifier, untypedModifier)),
                ),
                2 to AbilityRecord(
                    id = 2,
                    name = CatalogField.available("Untyped"),
                    mechanics = CatalogField.available(listOf(untypedModifier)),
                ),
            ),
        )

        val metrics = CatalogMetrics.from(catalog)

        assertEquals(3, metrics.movesWithDetails)
        assertEquals(1, metrics.movesWithCategories)
        assertEquals(2, metrics.types)
        assertEquals(1, metrics.namedTypes)
        assertEquals(1, metrics.typesWithSemanticRoles)
        assertEquals(2, metrics.abilitiesWithMechanics)
        assertEquals(1, metrics.abilitiesWithProvenTypedModifiers)
        assertEquals(1, metrics.provenTypedAbilityModifiers)
    }

    @Test
    fun catalogMetricsExposeSanitizedLocalizedCapabilityEvidence() {
        val capabilities = LocalizedTextCapability.entries.associateWith { capability ->
            when (capability) {
                LocalizedTextCapability.TYPE_NAMES -> LocalizedCapabilityState.available(1)
                LocalizedTextCapability.SPECIES_DESCRIPTIONS -> LocalizedCapabilityState.unavailable(
                    status = CapabilityStatus.NOT_FOUND,
                    expectedRecords = 0,
                    reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
                    validatorReviewRecommended = true,
                    reasons = listOf("private diagnostic must not enter metrics"),
                )
                else -> LocalizedCapabilityState.notApplicable("fixture")
            }
        }
        val projection = RomLanguageProjection(
            language = LanguageTag.FRENCH,
            codecId = "gba-gen3-fr",
            codecVersion = 1,
            localizedTables = LocalizedTableLayout(),
            evidence = emptyList(),
            status = LanguageResolutionStatus.RESOLVED,
        )
        val manifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.FRENCH,
            projections = listOf(projection),
            status = LanguageResolutionStatus.RESOLVED,
        )
        val overlay = CatalogLanguageOverlay(
            language = LanguageTag.FRENCH,
            overlayVersion = 1,
            localizedCapabilities = capabilities,
            typeNames = mapOf(10 to CatalogField.available("FEU")),
        )
        val catalog = ParsedCatalog(
            romSha256 = "0".repeat(64),
            family = EngineFamily.FIRERED_LEAFGREEN,
            platform = Platform.GBA,
            typesById = mapOf(
                10 to TypeRecord(
                    id = 10,
                    name = CatalogField.notFound("localized overlay owns the name"),
                    semanticRole = CatalogField.available(TypeSemanticRole.FIRE),
                ),
            ),
            localization = CatalogLocalization(manifest, mapOf(LanguageTag.FRENCH to overlay)),
        )

        val metrics = CatalogMetrics.from(catalog)
        val descriptions = metrics.localizedCapabilities.getValue("SPECIES_DESCRIPTIONS")

        assertEquals(LocalizedTextCapability.entries.size, metrics.localizedCapabilities.size)
        assertEquals(CapabilityStatus.NOT_FOUND, descriptions.status)
        assertEquals(0, descriptions.coveredRecords)
        assertEquals(0, descriptions.expectedRecords)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, descriptions.reviewStatus)
        assertTrue(descriptions.validatorReviewRecommended)
        val json = ReportWriter.json(
            CorpusReport(
                roots = emptyList(),
                results = listOf(
                    CorpusResult(
                        displayName = "fixture.gba",
                        source = "fixture.gba",
                        durationMillis = 1,
                        catalog = metrics,
                    ),
                ),
            ),
        )
        assertTrue(json.contains("\"localizedCapabilities\""))
        assertFalse(json.contains("private diagnostic"))
    }

    @Test
    fun catalogSamplesValidateEveryMoveReferenceSource() {
        val species = SpeciesRecord(
            id = 1,
            dexNumber = CatalogField.available(1),
            name = CatalogField.available("Bulbasaur"),
            typeIds = CatalogField.available(emptyList()),
            baseStats = CatalogField.available(BaseStats(45, 49, 49, 45, 65, 65)),
            sprite = CatalogField.notFound("fixture"),
            learnset = CatalogField.available(listOf(LearnsetEntry(5, 10))),
            moveAcquisitions = CatalogField.available(
                listOf(MoveAcquisition(11, MoveAcquisitionMethod.MACHINE, 1)),
            ),
        )
        val ruleset = LearnsetRuleset(
            id = "alternate",
            label = "Alternate",
            sourceOffset = 0x100,
            confidence = 1.0,
            entriesBySpecies = mapOf(1 to listOf(LearnsetEntry(6, 12))),
        )
        val catalog = ParsedCatalog(
            romSha256 = "0".repeat(64),
            family = EngineFamily.FIRERED_LEAFGREEN,
            platform = Platform.GBA,
            speciesById = mapOf(1 to species),
            learnsetRulesets = listOf(ruleset),
        )

        val errors = CatalogSamples.from(catalog).referenceErrors

        assertTrue(errors.any { it == "species 1 learns missing move 10" })
        assertTrue(errors.any { it == "species 1 acquires missing move 11 by MACHINE" })
        assertTrue(errors.any { it == "ruleset alternate species 1 learns missing move 12" })
    }

    @Test
    fun catalogSamplesExposePhysicalAndDexOrderedLeadingSpeciesRegisters() {
        fun species(id: Int, dex: Int, name: String, acquisitions: List<MoveAcquisition> = emptyList()) = SpeciesRecord(
            id = id,
            dexNumber = CatalogField.available(dex),
            name = CatalogField.available(name),
            typeIds = CatalogField.available(emptyList()),
            baseStats = CatalogField.available(BaseStats(1, 1, 1, 1, 1, 1)),
            sprite = CatalogField.notFound("fixture"),
            moveAcquisitions = CatalogField.available(acquisitions),
        )
        val catalog = ParsedCatalog(
            romSha256 = "0".repeat(64),
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(
                1 to species(1, 287, "A"),
                277 to species(277, 1, "Nimbleaf", listOf(MoveAcquisition(152, MoveAcquisitionMethod.TUTOR, 1))),
            ),
        )

        val samples = CatalogSamples.from(catalog)

        assertTrue(samples.species.first().contains("id=1; dex=287; name=A"))
        assertTrue(samples.speciesByDex.first().contains("id=277; dex=1; name=Nimbleaf"))
        assertEquals(listOf("species=277; move=152; source=1"), samples.tutorMoves)
    }

    @Test
    fun markdownIncludesCandidatesAndCapabilitiesWithoutExtractedText() {
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(
                CorpusResult(
                    displayName = "Pokemon Test.gba",
                    source = "Pokemon Test.gba",
                    durationMillis = 3,
                    result = sampleResult(),
                ),
            ),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("FIRERED_LEAFGREEN"))
        assertTrue(markdown.contains("SPECIES_NAMES"))
        assertFalse(markdown.contains("BULBASAUR"))
    }

    @Test
    fun jsonIsDeterministicForSameReport() {
        val report = CorpusReport(roots = emptyList(), results = emptyList())
        assertEquals(ReportWriter.json(report), ReportWriter.json(report))
        assertTrue(ReportWriter.json(report).contains("\"schemaVersion\": 14"))
        assertFalse(ReportWriter.markdown(report).contains("No mainline-family match"))
    }

    @Test
    fun jsonPublishesOnlyStructuralRulesetDetailsAtSchemaEleven() {
        val catalog = ParsedCatalog(
            romSha256 = "0".repeat(64),
            family = EngineFamily.FIRERED_LEAFGREEN,
            platform = Platform.GBA,
            learnsetRulesets = listOf(
                LearnsetRuleset(
                    id = "ruleset-00001234",
                    label = "Expanded 1",
                    sourceOffset = 0x1234,
                    confidence = 0.875,
                    entriesBySpecies = mapOf(1 to listOf(LearnsetEntry(level = 5, moveId = 10))),
                    primary = false,
                    levelUpSelector = LevelUpRulesetSelector(
                        saveBlock1ByteOffset = 0x20,
                        mask = 0x04,
                        expectedValue = 0x04,
                    ),
                ),
            ),
        )
        val report = CorpusReport(
            roots = emptyList(),
            results = listOf(
                CorpusResult(
                    displayName = "ruleset.gba",
                    source = "ruleset.gba",
                    durationMillis = 1,
                    result = sampleResult(),
                    catalog = CatalogMetrics.from(catalog),
                ),
            ),
        )

        val json = ReportWriter.json(report)
        val root = JsonParser.parseString(json).asJsonObject
        val catalogJson = root.getAsJsonArray("results")[0].asJsonObject.getAsJsonObject("catalog")
        val ruleset = catalogJson.getAsJsonArray("rulesetDetails")[0].asJsonObject
        val selector = ruleset.getAsJsonObject("levelUpSelector")

        assertEquals(14, root.get("schemaVersion").asInt)
        assertEquals(1, catalogJson.get("learnsetRulesets").asInt)
        assertEquals(
            setOf("id", "label", "sourceOffset", "confidence", "primary", "levelUpSelector"),
            ruleset.keySet(),
        )
        assertEquals(
            setOf("saveBlock1ByteOffset", "mask", "expectedValue"),
            selector.keySet(),
        )
        assertEquals("ruleset-00001234", ruleset.get("id").asString)
        assertEquals(0x1234, ruleset.get("sourceOffset").asInt)
        assertEquals(0x20, selector.get("saveBlock1ByteOffset").asInt)
        assertFalse(json.contains("entriesBySpecies"))
        assertFalse(json.contains("moveId"))
    }

    @Test
    fun jsonCarriesValidatorReviewProvenanceWithoutChangingTheReportSchema() {
        val result = sampleResult()
        val capability = result.capabilities.single().copy(validatorReviewRecommended = true)
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(
                CorpusResult(
                    displayName = "review.gba",
                    source = "review.gba",
                    durationMillis = 1,
                    result = result.copy(capabilities = listOf(capability)),
                ),
            ),
        )

        val json = ReportWriter.json(report)

        assertTrue(json.contains("\"schemaVersion\": 14"))
        assertTrue(json.contains("\"validatorReviewRecommended\": true"))
    }

    @Test
    fun jsonPublishesRootLabelsWithoutPrivateAbsolutePaths() {
        val report = CorpusReport(
            roots = listOf(
                "H:/Private/Roms/Nintendo - Game Boy",
                "H:\\Private\\Roms\\Nintendo - Game Boy Advance",
            ),
            results = emptyList(),
        )

        val json = ReportWriter.json(report)

        assertTrue(json.contains("\"Nintendo - Game Boy\""))
        assertTrue(json.contains("\"Nintendo - Game Boy Advance\""))
        assertFalse(json.contains("H:/"))
        assertFalse(json.contains("H:\\\\"))
        assertFalse(json.contains("Private"))
    }

    @Test
    fun markdownNamesEveryPersistedSqliteCatalogAndItsReopenEvidence() {
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(
                CorpusResult(
                    displayName = "Pokemon Emerald.gba",
                    source = "Pokemon Emerald.gba",
                    durationMillis = 12,
                    result = sampleResult(),
                    persistence = CatalogPersistenceMetrics(
                        fileName = "${"0".repeat(64)}.sqlite",
                        bytes = 640_000,
                        writeMillis = 80,
                        reopenMillis = 12,
                        sections = 10,
                    ),
                ),
            ),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("Persisted and reopened SQLite catalogs: 1"))
        assertTrue(markdown.contains("## SQLite catalog persistence"))
        assertTrue(markdown.contains("| Pokemon Emerald.gba | 000000000000"))
        assertTrue(markdown.contains("| 640000 | 10 | 80 | 12 |"))
    }

    @Test
    fun markdownNamesRomAndReportsMaterializedCatalogCounts() {
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(
                CorpusResult(
                    displayName = "Pokemon Emerald.gba",
                    source = "Pokemon Emerald.gba",
                    durationMillis = 12,
                    result = sampleResult(),
                    catalog = CatalogMetrics(
                        species = 412,
                        namedSpecies = 412,
                        speciesWithStats = 412,
                        speciesWithSprites = 411,
                        speciesWithDescriptions = 386,
                        evolutionEdges = 219,
                        learnsetEntries = 4211,
                        learnsetRulesets = 2,
                        moves = 355,
                        movesWithDetails = 355,
                        movesWithDescriptions = 354,
                        eggMoveLinks = 900,
                        machineMoveLinks = 5000,
                        tutorMoveLinks = 200,
                        types = 18,
                        typeMatchups = 112,
                        abilities = 78,
                        abilitiesWithDescriptions = 77,
                        abilitiesWithMechanics = 4,
                        captureBalls = 12,
                    ),
                ),
            ),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("## Materialized catalog counts"))
        assertTrue(markdown.contains("Pokemon Emerald.gba"))
        assertTrue(markdown.contains("| Pokemon Emerald.gba | 412 | 412 | 412 | 411 | 386 | 219 | 4211 | 2 | 355 |"))
        assertTrue(markdown.contains("| 355 | 354 | 900 | 5000 | 200 | 18 | 112 | 78 | 77 | 4 | 12 |"))
        assertFalse(markdown.contains("BULBASAUR"))
    }

    @Test
    fun markdownDistinguishesNotFoundFromNotApplicable() {
        val unavailable = CapabilityEvidence(
            RomCapability.TYPE_CHART,
            compatible = false,
            confidence = 0.0,
            status = CapabilityStatus.NOT_FOUND,
        )
        val notApplicable = CapabilityEvidence(
            RomCapability.ABILITIES,
            compatible = false,
            confidence = 0.0,
            status = CapabilityStatus.NOT_APPLICABLE,
        )
        val result = sampleResult().copy(capabilities = sampleResult().capabilities + unavailable + notApplicable)
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(CorpusResult("Pokemon Test.gba", "Pokemon Test.gba", durationMillis = 1, result = result)),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("Routing score"))
        assertTrue(markdown.contains("TYPE_CHART: not found"))
        assertTrue(markdown.contains("ABILITIES: not applicable"))
    }

    @Test
    fun markdownPreservesPartialAndAmbiguousCapabilities() {
        val result = sampleResult().copy(
            capabilities = listOf(
                CapabilityEvidence(
                    RomCapability.LEARNSETS,
                    compatible = true,
                    confidence = 0.70,
                    count = 10,
                    status = CapabilityStatus.PARTIAL,
                    validRecords = 7,
                    totalRecords = 10,
                ),
                CapabilityEvidence(
                    RomCapability.EVOLUTIONS,
                    compatible = false,
                    confidence = 0.65,
                    status = CapabilityStatus.AMBIGUOUS,
                ),
            ),
        )
        val entry = CorpusResult("Pokemon Partial.gba", "Pokemon Partial.gba", durationMillis = 1, result = result)

        val markdown = ReportWriter.markdown(CorpusReport(roots = listOf("test"), results = listOf(entry)))

        assertEquals(DataStructureCompatibility.PARTIAL, entry.dataCompatibility)
        assertTrue(markdown.contains("Below 100% or manual review"))
        assertTrue(markdown.contains("manual review required"))
        assertTrue(markdown.contains("LEARNSETS: partial"))
        assertTrue(markdown.contains("EVOLUTIONS: ambiguous"))
    }

    @Test
    fun jsonPublishesNumericCompatibilityFromApplicableFeatureCoverage() {
        val applicable = mapOf(
            RomCapability.SPECIES_NAMES to CapabilityEvidence(
                RomCapability.SPECIES_NAMES,
                compatible = true,
                confidence = 1.0,
                status = CapabilityStatus.AVAILABLE,
            ),
            RomCapability.LEARNSETS to CapabilityEvidence(
                RomCapability.LEARNSETS,
                compatible = true,
                confidence = 0.9,
                status = CapabilityStatus.PARTIAL,
                validRecords = 9,
                totalRecords = 10,
            ),
            RomCapability.EVOLUTIONS to CapabilityEvidence(
                RomCapability.EVOLUTIONS,
                compatible = true,
                confidence = 0.7,
                status = CapabilityStatus.PARTIAL,
                validRecords = 7,
                totalRecords = 10,
            ),
            RomCapability.TYPE_CHART to CapabilityEvidence(
                RomCapability.TYPE_CHART,
                compatible = false,
                confidence = 0.0,
                status = CapabilityStatus.NOT_FOUND,
            ),
        )
        val capabilities = RomCapability.entries.map { capability ->
            applicable[capability] ?: CapabilityEvidence(
                capability,
                compatible = false,
                confidence = 0.0,
                status = CapabilityStatus.NOT_APPLICABLE,
            )
        }
        val entry = CorpusResult(
            "Pokemon Numeric.gba",
            "Pokemon Numeric.gba",
            durationMillis = 1,
            result = sampleResult().copy(capabilities = capabilities),
        )

        val json = ReportWriter.json(CorpusReport(roots = listOf("test"), results = listOf(entry)))

        assertEquals(65.0, entry.compatibilityPercent, 0.001)
        assertEquals(3, entry.resolvedFeatureCount)
        assertEquals(4, entry.expectedFeatureCount)
        assertTrue(json.contains("\"compatibilityPercent\": 65.0"))
        assertTrue(json.contains("\"resolvedFeatureCount\": 3"))
        assertTrue(json.contains("\"expectedFeatureCount\": 4"))
    }

    @Test
    fun numericCompatibilityPrefersSemanticCoverageOverRawTableValidity() {
        val capabilities = RomCapability.entries.map { capability ->
            if (capability == RomCapability.EVOLUTIONS) {
                CapabilityEvidence(
                    capability,
                    compatible = true,
                    confidence = 0.99,
                    status = CapabilityStatus.PARTIAL,
                    validRecords = 999,
                    totalRecords = 1_000,
                    coveredRecords = 7,
                    expectedRecords = 10,
                )
            } else {
                CapabilityEvidence(
                    capability,
                    compatible = false,
                    confidence = 0.0,
                    status = CapabilityStatus.NOT_APPLICABLE,
                )
            }
        }

        val score = calculateCompatibility(sampleResult().copy(capabilities = capabilities))

        assertEquals(70.0, score.compatibilityPercent, 0.001)
        assertEquals(1, score.resolvedFeatureCount)
        assertEquals(1, score.expectedFeatureCount)
    }

    @Test
    fun markdownUsesPercentageAsThePrimaryRomStatus() {
        val capabilities = RomCapability.entries.map { capability ->
            when (capability) {
                RomCapability.SPECIES_NAMES -> CapabilityEvidence(
                    capability,
                    compatible = true,
                    confidence = 1.0,
                    status = CapabilityStatus.AVAILABLE,
                )
                RomCapability.LEARNSETS -> CapabilityEvidence(
                    capability,
                    compatible = true,
                    confidence = 0.75,
                    status = CapabilityStatus.PARTIAL,
                    validRecords = 3,
                    totalRecords = 4,
                )
                else -> CapabilityEvidence(
                    capability,
                    compatible = false,
                    confidence = 0.0,
                    status = CapabilityStatus.NOT_APPLICABLE,
                )
            }
        }
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(
                CorpusResult(
                    "Pokemon Numeric.gba",
                    "Pokemon Numeric.gba",
                    durationMillis = 1,
                    result = sampleResult().copy(capabilities = capabilities),
                ),
            ),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("| ROM | Compatibility | Resolved features | Expected features |"))
        assertTrue(markdown.contains("| Pokemon Numeric.gba | 87.50% | 2 | 2 |"))
        assertFalse(markdown.contains("| ROM | Data parser |"))
        assertTrue(markdown.contains("LEARNSETS: partial"))
    }

    @Test
    fun markdownReportsEveryStaticCapabilityAndFullCompleteness() {
        val capabilities = RomCapability.entries.map { capability ->
            if (capability == RomCapability.ABILITIES) {
                CapabilityEvidence(capability, false, 0.0, status = CapabilityStatus.NOT_APPLICABLE)
            } else {
                CapabilityEvidence(capability, true, 1.0, offset = 0x100, count = 10)
            }
        }
        val result = sampleResult().copy(capabilities = capabilities)
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(CorpusResult("Pokemon Test.gba", "Pokemon Test.gba", durationMillis = 1, result = result)),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("Complete core catalogs: 0"))
        assertTrue(markdown.contains("Complete for every applicable extended dataset: 0"))
        RomCapability.entries.forEach { capability ->
            assertTrue("missing detailed evidence for $capability", markdown.contains("$capability:"))
        }
    }

    @Test
    fun markdownSeparatesCoreCatalogCompletenessFromMissingExtendedMetadata() {
        val extended = setOf(
            RomCapability.MOVE_DESCRIPTIONS,
            RomCapability.EGG_MOVES,
            RomCapability.MACHINE_MOVES,
            RomCapability.TUTOR_MOVES,
            RomCapability.ABILITY_DESCRIPTIONS,
            RomCapability.ABILITY_MECHANICS,
        )
        val capabilities = RomCapability.entries.map { capability ->
            if (capability in extended) CapabilityEvidence(capability, false, 0.0, status = CapabilityStatus.NOT_FOUND)
            else CapabilityEvidence(capability, true, 1.0, offset = 0x100, count = 10)
        }
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(
                CorpusResult(
                    "Pokemon Test.gba",
                    "Pokemon Test.gba",
                    durationMillis = 1,
                    result = sampleResult().copy(capabilities = capabilities),
                ),
            ),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("Complete core catalogs: 0"))
        assertTrue(markdown.contains("Complete for every applicable extended dataset: 0"))
    }

    @Test
    fun markdownNamesEveryNoFamilyMatchInput() {
        val result = sampleResult().copy(
            status = SelectionStatus.NO_FAMILY_MATCH,
            selectedFamily = null,
            selectedProfile = null,
            capabilities = RomCapability.entries.map { CapabilityEvidence(it, false, 0.0) },
        )
        val report = CorpusReport(
            roots = listOf("test"),
            results = listOf(CorpusResult("Pokemon Pinball.gbc", "Pokemon Pinball.gbc", durationMillis = 1, result = result)),
        )

        val markdown = ReportWriter.markdown(report)

        assertTrue(markdown.contains("Below 100% or manual review (1)"))
        assertTrue(markdown.contains("0.00%; 0/${RomCapability.entries.size} features resolved"))
        assertTrue(markdown.contains("Pokemon Pinball.gbc"))
        assertFalse(markdown.contains("Unsupported"))
    }

    private fun sampleResult(): ParseResult {
        val capability = CapabilityEvidence(RomCapability.SPECIES_NAMES, true, 1.0, 0x100, 411, 11, listOf("validated"))
        val probe = ParserProbe(
            family = EngineFamily.FIRERED_LEAFGREEN,
            score = 92,
            hardGatePassed = true,
            anchors = 4,
            scoreEvidence = emptyList(),
            capabilities = listOf(capability),
            profileName = "test profile",
        )
        return ParseResult(
            header = RomHeader(Platform.GBA, "POKEMON FIRE", "BPRE", 1),
            sha256 = "0".repeat(64),
            crc32 = "00000000",
            size = 1024,
            status = SelectionStatus.SELECTED,
            selectedFamily = EngineFamily.FIRERED_LEAFGREEN,
            selectedProfile = "test profile",
            runnerUpMargin = 20,
            probes = listOf(probe),
            capabilities = listOf(capability),
        )
    }

    private class BoundedChunkWriter(private val maxChunkChars: Int) : Writer() {
        var totalChars: Long = 0
            private set
        var maximumChunkChars: Int = 0
            private set

        override fun write(buffer: CharArray, offset: Int, length: Int) {
            require(length <= maxChunkChars) { "aggregate write of $length characters exceeded diagnostic bound" }
            totalChars += length
            maximumChunkChars = maxOf(maximumChunkChars, length)
        }

        override fun flush() = Unit

        override fun close() = Unit
    }
}
