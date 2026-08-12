package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.AbilityMechanic
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CaptureBallRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.catalog.RuntimeMemoryEvidence
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.LevelUpRulesetSelector
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisition
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisitionMethod
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PresentationSource
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.catalog.TypePresentation
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.listDirectoryEntries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogStoreTest {
    @Test
    fun `legacy encounter sections without windows reopen as unrestricted`() {
        val catalog = completeCatalog("f".repeat(64))
        val codec = CatalogSectionCodec()
        val sections = codec.encode(catalog, CatalogSchema.requiredSections).toMutableMap()
        val legacyJson = GZIPInputStream(ByteArrayInputStream(sections.getValue("encounters"))).use {
            it.readBytes().toString(Charsets.UTF_8)
        }.replace(Regex(""","windows":\[[^]]*]"""), "")
        sections["encounters"] = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(legacyJson.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        val reopened = codec.decode(
            catalog.romSha256,
            catalog.romCrc32,
            catalog.family,
            catalog.platform,
            sections,
        )

        assertEquals(setOf(EncounterWindow.ANY), reopened.encounterAreas.single().windows)
    }

    @Test
    fun `legacy learnset rulesets without selectors reopen without inventing save detection`() {
        val catalog = completeCatalog("9".repeat(64))
        val codec = CatalogSectionCodec()
        val sections = codec.encode(catalog, CatalogSchema.requiredSections).toMutableMap()
        val currentJson = GZIPInputStream(ByteArrayInputStream(sections.getValue("learnset_rulesets"))).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        val legacyJson = currentJson.replace(
            Regex(",\"levelUpSelector\":\\{[^}]*}"),
            "",
        )
        assertTrue(legacyJson != currentJson)
        sections["learnset_rulesets"] = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(legacyJson.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        val reopened = codec.decode(
            catalog.romSha256,
            catalog.romCrc32,
            catalog.family,
            catalog.platform,
            sections,
        )

        assertNull(reopened.learnsetRulesets.single().levelUpSelector)
    }

    @Test
    fun `schema two cache without validator review provenance is invalidated`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("e".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Legacy.gba", 16_777_216, "POKEMON EMER"),
            CatalogWriteProgress.complete(),
        )
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            val payload = database.query(
                "SELECT payload FROM catalog_sections WHERE name = 'capabilities'",
            ) { row -> requireNotNull(row.bytes("payload")) }.single()
            val legacyJson = GZIPInputStream(ByteArrayInputStream(payload)).use {
            it.readBytes().toString(Charsets.UTF_8)
            }.replace(Regex(",\"validatorReviewRecommended\":true"), "")
            val legacyPayload = ByteArrayOutputStream().also { output ->
                GZIPOutputStream(output).use { it.write(legacyJson.toByteArray(Charsets.UTF_8)) }
            }.toByteArray()
            database.execute(
                "UPDATE catalog_sections SET payload = ? WHERE name = 'capabilities'",
                listOf(legacyPayload),
            )
            database.execute("UPDATE catalog_metadata SET parser_schema_version = 2 WHERE id = 1")
        }

        assertNull(cache.readComplete(catalog.romSha256))
    }

    @Test
    fun clearingInactiveCatalogsPreservesTheActiveDatabaseAndUnrelatedFiles() {
        val root = Files.createTempDirectory("dualdex-catalog-clear")
        val active = "a".repeat(64)
        val stale = "b".repeat(64)
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        cache.fileFor(active).writeBytes(byteArrayOf(1))
        cache.fileFor(stale).writeBytes(byteArrayOf(2))
        root.resolve("knowledge.json").toFile().writeText("keep")

        assertEquals(1, cache.clearInactive(active))
        assertTrue(cache.fileFor(active).isFile)
        assertFalse(cache.fileFor(stale).exists())
        assertTrue(root.resolve("knowledge.json").toFile().isFile)
    }

    private val roots = mutableListOf<Path>()

    @After
    fun clean() {
        roots.asReversed().forEach { root ->
            if (Files.exists(root)) {
                Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun `complete catalogs round trip every production section`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("a".repeat(64))
        val source = CatalogSourceMetadata.direct("Pokemon Emerald.gba", 16_777_216, "POKEMON EMER")

        cache.write(catalog, source, CatalogWriteProgress.complete())
        val reopened = cache.readComplete(catalog.romSha256)

        assertEquals(7, CatalogSchema.parserSchemaVersion)
        assertEquals(source, reopened?.source)
        assertEquals(catalog, reopened?.catalog)
        assertEquals(
            LevelUpRulesetSelector(0x3DA6, 0x02, 0x02),
            reopened?.catalog?.learnsetRulesets?.single()?.levelUpSelector,
        )
        assertTrue(
            reopened?.catalog?.capabilities?.getValue(RomCapability.SPECIES_CATALOG)
                ?.validatorReviewRecommended == true,
        )
        assertEquals(
            23,
            reopened?.catalog?.speciesById?.get(6)?.evolutionEdges?.value?.single()?.conditionValue,
        )
        assertEquals(setOf(EncounterWindow.NIGHT), reopened?.catalog?.encounterAreas?.single()?.windows)
        assertEquals(0, reopened?.catalog?.encounterAreas?.single()?.baseAreaId)
        assertEquals(0x030036F0L, reopened?.catalog?.runtimeMetadata?.gen3SaveBlock1PointerAddress)
        assertEquals(
            CatalogGen3RuntimeMemoryLayout(
                mainAddress = 0x03001574,
                inBattleAddress = 0x030019AD,
                inBattleMask = 0x02,
                saveBlock1MapGroupOffset = 4,
                saveBlock1MapNumberOffset = 5,
                multiUsePlayerCursorAddress = 0x03002378,
                multiUsePlayerCursorEvidence = RuntimeMemoryEvidence.SOURCE_PROVEN_UNTESTED,
            ),
            reopened?.catalog?.runtimeMetadata?.gen3RuntimeMemoryLayout,
        )
        assertEquals("Route 101", reopened?.catalog?.runtimeMetadata?.areaNamesByBaseId?.get(0x0010))
        assertEquals(CatalogSchema.requiredSections, reopened?.committedSections)
    }

    @Test
    fun `incomplete phases remain unavailable until the complete transaction commits`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("b".repeat(64))
        val source = CatalogSourceMetadata.direct("Crystal.gbc", 2_097_152, "PM_CRYSTAL")

        cache.write(catalog, source, CatalogWriteProgress("RELATIONSHIPS", 3, 5, complete = false))
        assertNull(cache.readComplete(catalog.romSha256))

        cache.write(catalog, source, CatalogWriteProgress.complete())
        assertEquals(catalog, cache.readComplete(catalog.romSha256)?.catalog)
    }

    @Test
    fun `complete marker can commit without serializing unchanged extended sections again`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("1".repeat(64))
        val source = CatalogSourceMetadata.direct("Emerald.gba", 16_777_216, "POKEMON EMER")
        cache.write(catalog, source, CatalogWriteProgress("EXTENDED", 4, 5, complete = false))

        cache.write(
            catalog,
            source,
            CatalogWriteProgress("COMPLETE", 5, 5, complete = true, changedSections = emptySet()),
        )

        assertEquals(catalog, cache.readComplete(catalog.romSha256)?.catalog)
    }

    @Test
    fun `direct and zip sources converge on one SHA authoritative cache`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("c".repeat(64))
        val direct = CatalogSourceMetadata.direct("Emerald.gba", 16_777_216, "POKEMON EMER")
        val archive = CatalogSourceMetadata.archive(
            "Modern Emerald.zip!Pokemon - Modern Emerald.gba",
            16_777_216,
            "POKEMON EMER",
        )

        cache.write(catalog, direct, CatalogWriteProgress.complete())
        cache.write(catalog, archive, CatalogWriteProgress.complete())

        assertEquals(1, root.listDirectoryEntries("*.sqlite").size)
        assertEquals(archive, cache.readComplete(catalog.romSha256)?.source)
        assertEquals(catalog.romSha256, cache.findCompleted(catalog.romCrc32, 16_777_216, "POKEMON EMER").single().catalog.romSha256)
    }

    @Test
    fun `parser schema changes invalidate stale content so a current reparse can replace it`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("d".repeat(64))
        val source = CatalogSourceMetadata.direct("Yellow.gb", 1_048_576, "POKEMON YELLOW")
        cache.write(catalog, source, CatalogWriteProgress.complete())

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("UPDATE catalog_metadata SET parser_schema_version = ? WHERE id = 1", listOf(-1))
        }

        assertNull(cache.readComplete(catalog.romSha256))

        val reparsed = catalog.copy(diagnostics = listOf("reparsed with the current schema"))
        cache.write(reparsed, source, CatalogWriteProgress.complete())

        assertEquals(reparsed, cache.readComplete(catalog.romSha256)?.catalog)
    }

    @Test
    fun `corrupt cache is rejected and removed so the ROM can be parsed again`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val hash = "e".repeat(64)
        Files.write(cache.fileFor(hash).toPath(), byteArrayOf(1, 2, 3, 4))

        assertNull(cache.readComplete(hash))
        assertFalse(cache.fileFor(hash).exists())
    }

    private fun newRoot(): Path {
        val preferred = System.getenv("DUALDEX_TEST_TEMP")?.let(Path::of)
        val root = if (preferred != null && Files.isDirectory(preferred)) {
            Files.createTempDirectory(preferred, "catalog-store-")
        } else {
            Files.createTempDirectory("catalog-store-")
        }
        roots.add(root)
        return root
    }

    private fun completeCatalog(hash: String): ParsedCatalog {
        val sprite = RgbaSprite(2, 2, intArrayOf(0x00000000, 0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt()))
        val typePresentation = TypePresentation(PresentationSource.ROM_EXTRACTED, 0xffffffff.toInt(), 0xffff4422.toInt(), 0xff772211.toInt())
        val move = MoveRecord(
            id = 53,
            name = CatalogField.available("Flamethrower"),
            typeId = CatalogField.available(10),
            category = CatalogField.available(MoveCategory.SPECIAL),
            power = CatalogField.available(90),
            accuracy = CatalogField.available(100),
            pp = CatalogField.available(15),
            priority = CatalogField.available(0),
            effectId = CatalogField.available(4),
            effectText = CatalogField.available("May burn the target."),
        )
        val species = SpeciesRecord(
            id = 6,
            dexNumber = CatalogField.available(6),
            name = CatalogField.available("Charizard"),
            typeIds = CatalogField.available(listOf(10, 2)),
            baseStats = CatalogField.available(BaseStats(78, 84, 78, 100, 109, 85)),
            sprite = CatalogField.available(sprite),
            description = CatalogField.available("Spits fire hot enough to melt boulders."),
            height = CatalogField.available(17),
            weight = CatalogField.available(905),
            evolutionEdges = CatalogField.available(
                listOf(
                    EvolutionEdge(
                        targetSpeciesId = 7,
                        methodId = 4,
                        parameter = 36,
                        raw = byteArrayOf(4, 0, 36, 0, 7, 0, 23, 0),
                        conditionValue = 23,
                    ),
                ),
            ),
            learnset = CatalogField.available(listOf(LearnsetEntry(46, 53))),
            moveAcquisitions = CatalogField.available(listOf(MoveAcquisition(53, MoveAcquisitionMethod.MACHINE, 35))),
            abilityIds = CatalogField.available(listOf(66)),
        )
        val ability = AbilityRecord(
            id = 66,
            name = CatalogField.available("Blaze"),
            description = CatalogField.available("Powers up Fire-type moves in a pinch."),
            mechanics = CatalogField.available(
                listOf(AbilityMechanic(AbilityMechanicKind.MULTIPLIER, "Fire power", "1.5x", 3, 2)),
            ),
        )
        return ParsedCatalog(
            romSha256 = hash,
            romCrc32 = "8C7DBECA",
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(6 to species),
            movesById = mapOf(53 to move),
            typesById = mapOf(10 to TypeRecord(10, CatalogField.available("Fire"), CatalogField.available(typePresentation))),
            abilitiesById = mapOf(66 to ability),
            typeChart = listOf(TypeMatchup(10, 12, 200)),
            encounterAreas = listOf(
                EncounterArea(
                    1,
                    0,
                    CatalogField.available("Route 1"),
                    0,
                    listOf(EncounterSlot(6, 34, 36, 10)),
                    setOf(EncounterWindow.NIGHT),
                ),
            ),
            captureBallsById = mapOf(4 to CaptureBallRecord(4, CatalogField.available("Poké Ball"), CatalogField.available(sprite))),
            learnsetRulesets = listOf(
                LearnsetRuleset(
                    "modern",
                    "Modern",
                    0x1234,
                    0.99,
                    mapOf(6 to listOf(LearnsetEntry(46, 53))),
                    primary = true,
                    levelUpSelector = LevelUpRulesetSelector(0x3DA6, 0x02, 0x02),
                ),
            ),
            runtimeMetadata = CatalogRuntimeMetadata(
                gen3SaveBlock1PointerAddress = 0x030036F0L,
                gen3RuntimeMemoryLayout = CatalogGen3RuntimeMemoryLayout(
                    mainAddress = 0x03001574,
                    inBattleAddress = 0x030019AD,
                    inBattleMask = 0x02,
                    saveBlock1MapGroupOffset = 4,
                    saveBlock1MapNumberOffset = 5,
                    multiUsePlayerCursorAddress = 0x03002378,
                    multiUsePlayerCursorEvidence = RuntimeMemoryEvidence.SOURCE_PROVEN_UNTESTED,
                ),
                areaNamesByBaseId = mapOf(0x0010 to "Route 101"),
            ),
            capabilities = mapOf(
                RomCapability.SPECIES_CATALOG to CapabilityEvidence(
                    RomCapability.SPECIES_CATALOG,
                    compatible = true,
                    confidence = 1.0,
                    offset = 0x100,
                    count = 1,
                    reasons = listOf("test fixture"),
                    status = CapabilityStatus.AVAILABLE,
                    reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
                    validatorReviewRecommended = true,
                ),
            ),
            diagnostics = listOf("fixture diagnostic"),
        )
    }
}
