package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisition
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisitionMethod
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportWriterTest {
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
        )

        val samples = CatalogSamples.from(catalog)

        assertTrue(samples.species.single().contains("name=Bulbasaur"))
        assertTrue(samples.species.single().contains("stats=45/49/49/45/65/65"))
        assertTrue(samples.moves.single().contains("name=Pound"))
        assertTrue(samples.referenceErrors.any { it.contains("species 1 references missing type 12") })
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
        assertTrue(ReportWriter.json(report).contains("\"schemaVersion\": 9"))
        assertFalse(ReportWriter.markdown(report).contains("No mainline-family match"))
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
        assertTrue(markdown.contains("`N/F` = applicable but not found or validated"))
        assertTrue(markdown.contains("`N/A` = not applicable to that engine"))
        assertTrue(markdown.contains("| N/F |"))
        assertTrue(markdown.contains("| N/A |"))
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
        assertTrue(markdown.contains("| Catalog | Names | Types | Type chart | Stats | Sprites | Dex text | Evolutions | Moves | Move data | Move text | Learnsets | Rulesets | Egg moves | Machine moves | Tutor moves | Abilities | Ability text | Ability values | Areas | Type colors | Balls |"))
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

        assertTrue(markdown.contains("Unresolved ROM data structures (1)"))
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
}
