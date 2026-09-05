package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.model.GbDescriptionSegment
import com.enrpau.dualscreendex.parser.model.GbInlineDescriptionLayout
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationSource
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanic
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicCondition
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CaptureBallRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMetadata
import com.enrpau.dualscreendex.parser.catalog.CatalogTheme
import com.enrpau.dualscreendex.parser.catalog.CatalogThemeAssetClass
import com.enrpau.dualscreendex.parser.catalog.CatalogThemeMethod
import com.enrpau.dualscreendex.parser.catalog.CatalogThemeTokens
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.catalog.CatalogGameClockSchedule
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocketAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BattleUiAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BitFlag
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3EventFlagAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3PartyAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3SaveRuntimeAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3TextEncoding
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3TrainerCardAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.RuntimeMemoryEvidence
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.CatalogPoiText
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.IndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.LevelUpRulesetSelector
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapLightingPolicy
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiItem
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.LocalMapRasterCodec
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.LocalMapScene
import com.enrpau.dualscreendex.parser.catalog.LocalMapScenePlacement
import com.enrpau.dualscreendex.parser.catalog.MapLightingPalettes
import com.enrpau.dualscreendex.parser.catalog.MapTimeBlend
import com.enrpau.dualscreendex.parser.catalog.MapTimePaletteModel
import com.enrpau.dualscreendex.parser.catalog.TimedIndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisition
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisitionMethod
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PresentationSource
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.catalog.TypePresentation
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldLocationKey
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.language.LanguageEvidence
import com.enrpau.dualscreendex.parser.language.LanguageEvidenceKind
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.listDirectoryEntries
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CatalogStoreTest {
    @Test
    fun `applicable missing descriptions survive extraction close reopen and overlay validation`() {
        val rom = RomImage(ByteArray(1024) { 0xff.toByte() })
        val layout = com.enrpau.dualscreendex.parser.model.ResolvedRomLayout(
            EngineFamily.GOLD_SILVER, 2, Platform.GBC, 2, 0,
            com.enrpau.dualscreendex.parser.model.ProfileTables(speciesNames = TableLayout(0, 2, 6)),
            languageManifest = completeCatalog("7".repeat(64)).languageManifest,
        )
        val analysis = com.enrpau.dualscreendex.parser.model.ParseResult(
            com.enrpau.dualscreendex.parser.model.RomHeader(Platform.GBC, "SYNTHETIC"), rom.sha256, rom.crc32, rom.size,
            com.enrpau.dualscreendex.parser.model.SelectionStatus.SELECTED, EngineFamily.GOLD_SILVER, null, 20, emptyList(), emptyList(),
        )
        var extracted: ParsedCatalog? = null
        val stop = IllegalStateException("media captured")
        try {
            com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer.materialize(rom, analysis, layout, onProgress = {
                if (it.phase == com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationPhase.SPECIES_MEDIA) {
                    extracted = it.catalog
                    throw stop
                }
            })
        } catch (failure: IllegalStateException) { if (failure !== stop) throw failure }
        val catalog = requireNotNull(extracted)
        assertTrue(catalog.speciesById.values.all { it.description.status == CapabilityStatus.NOT_FOUND && it.description.value == null })
        val cache = CatalogCache(newRoot().toFile(), JdbcCatalogDatabaseFactory)
        cache.write(catalog, CatalogSourceMetadata.direct("Synthetic.gbc", rom.size, "SYNTHETIC"), CatalogWriteProgress.complete())
        val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
        assertEquals(catalog, reopened)
        val state = requireNotNull(reopened.defaultLocalizedText()).localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS)
        assertEquals(0, state.coveredRecords)
        assertEquals(2, state.expectedRecords)
        assertEquals(CapabilityStatus.NOT_FOUND, state.status)
    }

    @Test
    fun `revision 51 caches without declared sign semantics must be reparsed`() {
        val cache = CatalogCache(newRoot().toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("6".repeat(64))
        val source = CatalogSourceMetadata.direct("Synthetic.gbc", 32768, "SYNTHETIC")
        cache.write(catalog, source, CatalogWriteProgress.complete())
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("UPDATE catalog_metadata SET parser_schema_version = 51 WHERE id = 1")
        }
        assertNull(cache.readComplete(catalog.romSha256))
        cache.write(catalog, source, CatalogWriteProgress.complete())
        assertEquals(catalog, cache.readComplete(catalog.romSha256)?.catalog)
        assertEquals(2, CatalogSchema.version)
    }

    @Test
    fun `revision 50 description placeholders must be reparsed`() {
        val cache = CatalogCache(newRoot().toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("8".repeat(64))
        cache.write(catalog, CatalogSourceMetadata.direct("Synthetic.gba", 32768, "SYNTHETIC"), CatalogWriteProgress.complete())
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("UPDATE catalog_metadata SET parser_schema_version = 50 WHERE id = 1")
        }
        assertNull(cache.readComplete(catalog.romSha256))
        assertEquals(2, CatalogSchema.version)
    }

    @Test
    fun `revision 49 caches containing unproven native move prose must be reparsed`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("9".repeat(64))
        val source = CatalogSourceMetadata.direct("Synthetic native control.gba", 32768, "SYNTHETIC")
        cache.write(catalog, source, CatalogWriteProgress.complete())
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("UPDATE catalog_metadata SET parser_schema_version = 49 WHERE id = 1")
        }
        assertNull(cache.readComplete(catalog.romSha256))
        cache.write(catalog, source, CatalogWriteProgress.complete())
        assertEquals(catalog, cache.readComplete(catalog.romSha256)?.catalog)
        assertEquals(2, CatalogSchema.version)
    }

    @Test
    fun `catalog chunk stream pulls ordered chunks lazily and rejects gaps or duplicates`() {
        val chunks = ArrayDeque(
            listOf(
                CatalogChunk(0, byteArrayOf(1, 2)),
                CatalogChunk(1, byteArrayOf(3, 4)),
            ),
        )
        var pulls = 0
        val input = CatalogChunkInputStream("control") {
            pulls++
            chunks.removeFirstOrNull()
        }

        assertEquals(0, pulls)
        assertEquals(1, input.read())
        assertEquals(1, pulls)
        assertArrayEquals(byteArrayOf(2, 3, 4), input.readBytes())
        assertEquals(3, pulls)

        listOf(
            listOf(CatalogChunk(0, byteArrayOf(1)), CatalogChunk(2, byteArrayOf(2))),
            listOf(CatalogChunk(0, byteArrayOf(1)), CatalogChunk(0, byteArrayOf(2))),
        ).forEach { invalid ->
            val pending = ArrayDeque(invalid)
            assertThrows(IllegalArgumentException::class.java) {
                CatalogChunkInputStream("invalid") { pending.removeFirstOrNull() }.readBytes()
            }
        }
    }

    @Test
    fun `catalog chunk stream enforces chunk and encoded byte limits`() {
        val tooMany = ArrayDeque(
            listOf(
                CatalogChunk(0, byteArrayOf(1)),
                CatalogChunk(1, byteArrayOf(2)),
                CatalogChunk(2, byteArrayOf(3)),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            CatalogChunkInputStream(
                sectionName = "chunk-limit",
                maximumChunks = 2,
                maximumEncodedBytes = 8,
            ) { tooMany.removeFirstOrNull() }.readBytes()
        }

        val tooLarge = ArrayDeque(
            listOf(
                CatalogChunk(0, byteArrayOf(1, 2)),
                CatalogChunk(1, byteArrayOf(3, 4)),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            CatalogChunkInputStream(
                sectionName = "byte-limit",
                maximumChunks = 4,
                maximumEncodedBytes = 3,
            ) { tooLarge.removeFirstOrNull() }.readBytes()
        }
    }

    @Test
    fun `catalog chunk output enforces write-side chunk and encoded byte limits`() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogChunkOutputStream(
                maximumBytes = 2,
                maximumChunks = 4,
                maximumEncodedBytes = 3,
            ) { _, _ -> }.use { output -> output.write(byteArrayOf(1, 2, 3, 4)) }
        }

        assertThrows(IllegalArgumentException::class.java) {
            CatalogChunkOutputStream(
                maximumBytes = 2,
                maximumChunks = 1,
                maximumEncodedBytes = 8,
            ) { _, _ -> }.use { output -> output.write(byteArrayOf(1, 2, 3)) }
        }
    }

    @Test
    fun `catalog read and write budgets cap aggregate language overlays independently`() {
        val writeBudget = CatalogWriteBudget()
        writeBudget.claim("language_overlay:en", CatalogSchema.maximumLanguageOverlaysEncodedBytes)
        assertThrows(IllegalArgumentException::class.java) {
            writeBudget.claim("language_overlay:fr", 1)
        }
        writeBudget.claimInflated(
            "language_overlay:en",
            CatalogSchema.maximumLanguageOverlaysInflatedBytes,
        )
        assertThrows(IllegalArgumentException::class.java) {
            writeBudget.claimInflated("language_overlay:fr", 1)
        }

        val readBudget = CatalogReadBudget()
        readBudget.claimInflated("language_overlay:en", CatalogSchema.maximumLanguageOverlaysInflatedBytes)
        assertThrows(IllegalArgumentException::class.java) {
            readBudget.claimInflated("language_overlay:fr", 1)
        }
    }

    @Test
    fun `catalog section codec rejects gzip beyond its inflate limit`() {
        val payload = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write(("\"ok\"" + " ".repeat(256)).toByteArray(Charsets.UTF_8))
            }
        }.toByteArray()
        val stringType = object : TypeToken<String>() {}.type

        assertThrows(IllegalArgumentException::class.java) {
            CatalogSectionCodec().decodeSection(
                ByteArrayInputStream(payload),
                stringType,
                sectionName = "inflate-limit",
                maximumInflatedBytes = 64,
            )
        }
    }

    @Test
    fun `catalog section codec rejects JSON beyond its write inflate limit`() {
        val catalog = completeCatalog("a".repeat(64)).copy(diagnostics = listOf("x".repeat(256)))

        assertThrows(IllegalArgumentException::class.java) {
            CatalogSectionCodec().writeSection(
                catalog = catalog,
                name = "diagnostics",
                output = ByteArrayOutputStream(),
                maximumInflatedBytes = 64,
            )
        }
    }

    @Test
    fun `catalog section codec reconstructs and validates persisted language manifests`() {
        val catalog = completeCatalog("a".repeat(64))
        val codec = CatalogSectionCodec()
        val sections = codec.encode(catalog, CatalogSectionPlan.from(catalog.localization).sections).toMutableMap()
        sections["language_manifest"] = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write(
                    """{"defaultLanguage":"en","projections":[],"status":"RESOLVED","diagnostics":[]}"""
                        .toByteArray(Charsets.UTF_8),
                )
            }
        }.toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(catalog.romSha256, catalog.romCrc32, catalog.family, catalog.platform, sections)
        }
    }

    @Test
    fun `catalog section codec restores immutable language manifest snapshots`() {
        val table = TableLayout(
            offset = 0x100,
            count = 3,
            recordSize = 11,
            banks = listOf(1, 2),
            pointerOffsets = listOf(0x20, 0x24),
            bankRemap = mapOf(1 to 2),
        )
        val descriptions = TableLayout(0x4800, 251, 2,
            gbDescriptions = GbInlineDescriptionLayout(
                GbDescriptionSegment(0x4800, 99, 1),
                GbDescriptionSegment(0x6000, 152, 1),
            ))
        val typeNames = TableLayout(
            offset = 0x200,
            count = 18,
            recordSize = 7,
        )
        val manifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = listOf(
                RomLanguageProjection(
                    language = LanguageTag.ENGLISH,
                    codecId = PokemonTextCodec.gbaEnglish.id,
                    codecVersion = PokemonTextCodec.gbaEnglish.version,
                    localizedTables = LocalizedTableLayout(
                        speciesNames = table,
                        descriptions = descriptions,
                        typeNames = typeNames,
                    ),
                    evidence = listOf(
                        LanguageEvidence(LanguageEvidenceKind.TABLE_RELATIONSHIP, "fixture", 100),
                    ),
                    status = LanguageResolutionStatus.RESOLVED,
                ),
            ),
            status = LanguageResolutionStatus.RESOLVED,
            diagnostics = listOf("validated fixture"),
        )
        val fixture = completeCatalog("b".repeat(64))
        val catalog = fixture.copy(
            localization = CatalogLocalization(
                manifest,
                mapOf(LanguageTag.ENGLISH to requireNotNull(fixture.defaultLocalizedText())),
            ),
        )
        val codec = CatalogSectionCodec()
        val sections = codec.encode(catalog, CatalogSectionPlan.from(catalog.localization).sections)

        val reopened = codec.decode(
            catalog.romSha256,
            catalog.romCrc32,
            catalog.family,
            catalog.platform,
            sections,
        ).languageManifest

        assertEquals(manifest, reopened)
        assertThrows(UnsupportedOperationException::class.java) {
            (reopened.projections as MutableList<RomLanguageProjection>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (reopened.diagnostics as MutableList<String>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (reopened.projections.single().evidence as MutableList<LanguageEvidence>).clear()
        }
        val reopenedTable = requireNotNull(reopened.defaultProjection()?.localizedTables?.speciesNames)
        assertEquals(typeNames, reopened.defaultProjection()?.localizedTables?.typeNames)
        assertEquals(descriptions, reopened.defaultProjection()?.localizedTables?.descriptions)
        assertEquals(descriptions.gbDescriptions, reopened.defaultProjection()?.localizedTables?.descriptions?.gbDescriptions)
        assertThrows(UnsupportedOperationException::class.java) {
            (reopenedTable.banks as MutableList<Int>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (reopenedTable.pointerOffsets as MutableList<Int>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (reopenedTable.bankRemap as MutableMap<Int, Int>).clear()
        }
    }

    @Test
    fun `language overlays use canonical dynamic sections and reopen exactly`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val localMap = LocalMap("local/0001", "Test Route", 1, 16, 16, 1, 1, "local/0001/map")
        val mapAsset = PngMapAsset(
            PngEncoder.encode(RgbaSprite(16, 16, IntArray(16 * 16) { 0xff204060.toInt() })),
        )
        val base = localizedFixtureCatalog(
            completeCatalog("c".repeat(64)),
            localMaps = LocalMapCatalog(
                maps = listOf(localMap),
                assets = mapOf(localMap.imageAssetKey to mapAsset),
            ),
        )
        val english = requireNotNull(base.defaultLocalizedText())
        val french = overlayForLanguage(english, LanguageTag.FRENCH, "Dracaufeu", "Feu")
        val manifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = listOf(
                base.languageManifest.projections.single(),
                RomLanguageProjection(
                    language = LanguageTag.FRENCH,
                    codecId = "gba-french",
                    codecVersion = 1,
                    localizedTables = LocalizedTableLayout(),
                    evidence = listOf(
                        LanguageEvidence(LanguageEvidenceKind.TABLE_RELATIONSHIP, "fixture", 100),
                    ),
                    status = LanguageResolutionStatus.RESOLVED,
                ),
            ),
            status = LanguageResolutionStatus.RESOLVED,
        )
        val catalog = base.copy(
            localization = CatalogLocalization(
                manifest,
                linkedMapOf(LanguageTag.ENGLISH to english, LanguageTag.FRENCH to french),
            ),
        )

        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Localized.gba", 16_777_216, "LOCALIZED"),
            CatalogWriteProgress.complete(),
        )
        val reopened = requireNotNull(cache.readComplete(catalog.romSha256))

        assertEquals(catalog, reopened.catalog)
        assertEquals(
            CatalogSchema.requiredSections + setOf("language_overlay:en", "language_overlay:fr"),
            reopened.committedSections,
        )
        assertEquals(
            "Dracaufeu",
            reopened.catalog.localizedText(LanguageTag.FRENCH)?.speciesNames?.get(6)?.value,
        )
        assertEquals("Fire", reopened.catalog.localizedText(LanguageTag.ENGLISH)?.typeNames?.get(10)?.value)
        assertEquals("Feu", reopened.catalog.localizedText(LanguageTag.FRENCH)?.typeNames?.get(10)?.value)
        assertEquals(TypeSemanticRole.FIRE, reopened.catalog.typesById.getValue(10).semanticRole.value)
        assertNull(reopened.catalog.typesById.getValue(10).name.value)
        assertNull(reopened.catalog.localMaps.maps.single().displayName)
        assertEquals(
            "Test Route",
            reopened.catalog.localizedText(LanguageTag.FRENCH)?.localMapNames?.get("local/0001")?.value,
        )
    }

    @Test
    fun `native type overlays and unresolved semantics survive database close and reopen without leakage`() {
        val root = newRoot()
        val base = completeCatalog("a".repeat(64))
        val prior = requireNotNull(base.defaultLocalizedText())
        val codecs = listOf(JapanesePokemonTextCodecs.gen2, KoreanGen2PokemonTextCodec.codec)
        // Deliberately swapped IDs: neither persistence nor text projection may infer a numeric type order.
        val names = mapOf(
            LanguageTag.JAPANESE to mapOf(21 to "ほのお", 20 to "みず", 19 to "？？？", 31 to "ほのお"),
            LanguageTag.KOREAN to mapOf(21 to "화염", 20 to "물", 19 to "???"),
        )
        val manifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.JAPANESE,
            projections = codecs.map { codec ->
                RomLanguageProjection(
                    language = codec.language,
                    codecId = codec.id,
                    codecVersion = codec.version,
                    localizedTables = LocalizedTableLayout(),
                    evidence = listOf(
                        LanguageEvidence(LanguageEvidenceKind.TABLE_RELATIONSHIP, "synthetic native overlay", 100),
                    ),
                    status = LanguageResolutionStatus.RESOLVED,
                )
            },
            status = LanguageResolutionStatus.RESOLVED,
        )
        val overlays = names.mapValues { (language, typeNames) ->
            CatalogLanguageOverlay(
                language = language,
                overlayVersion = 1,
                localizedCapabilities = prior.localizedCapabilities.mapValues { (capability, state) ->
                    if (capability == LocalizedTextCapability.TYPE_NAMES) {
                        LocalizedCapabilityState(
                            status = if (typeNames.size == 4) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL,
                            confidence = 1.0,
                            coveredRecords = typeNames.size,
                            expectedRecords = 4,
                        )
                    } else {
                        LocalizedCapabilityState.notFound("type-only synthetic overlay", state.expectedRecords)
                    }
                },
                typeNames = typeNames.mapValues { CatalogField.available(it.value) },
            )
        }
        val localizedPlaceholder = CatalogField.notApplicable<String>("stored in language overlay")
        val catalog = base.copy(
            family = EngineFamily.GOLD_SILVER,
            platform = Platform.GBC,
            speciesById = base.speciesById.mapValues { (_, species) ->
                species.copy(typeIds = CatalogField.available(listOf(21, 20)))
            },
            movesById = base.movesById.mapValues { (_, move) -> move.copy(typeId = CatalogField.available(21)) },
            typesById = mapOf(
                21 to TypeRecord(21, localizedPlaceholder, semanticRole = CatalogField.available(TypeSemanticRole.FIRE)),
                20 to TypeRecord(20, localizedPlaceholder, semanticRole = CatalogField.available(TypeSemanticRole.WATER)),
                19 to TypeRecord(19, localizedPlaceholder, semanticRole = CatalogField.available(TypeSemanticRole.MYSTERY)),
                // A display label alone is not semantic evidence, even when another row has the same name.
                31 to TypeRecord(31, localizedPlaceholder, semanticRole = CatalogField.notFound("unresolved fixture role")),
            ),
            typeChart = listOf(TypeMatchup(21, 20, 50), TypeMatchup(19, 31, 100)),
            runtimeMetadata = CatalogRuntimeMetadata(areaBaseIds = base.runtimeMetadata.areaBaseIds),
            localization = CatalogLocalization(manifest, overlays),
        )
        val file = root.resolve("native-types.sqlite").toFile()
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            CatalogWriter(database).write(
                catalog,
                CatalogSourceMetadata.direct("Synthetic native types.gbc", 49_152, "SYNTHETIC TYPES"),
                CatalogWriteProgress.complete(),
            )
        }

        val stored = JdbcCatalogDatabaseFactory.open(file).use { database ->
            requireNotNull(CatalogReader(database).readComplete())
        }
        val reopened = stored.catalog

        assertEquals(catalog, reopened)
        assertEquals(CatalogSchema.requiredSections + setOf("language_overlay:ja", "language_overlay:ko"), stored.committedSections)
        assertEquals(manifest, reopened.languageManifest)
        names.forEach { (language, expected) ->
            assertEquals(expected, reopened.localizedText(language)?.typeNames?.mapValues { it.value.value })
        }
        assertEquals(names.getValue(LanguageTag.JAPANESE), reopened.defaultLocalizedText()?.typeNames?.mapValues { it.value.value })
        assertNull(reopened.localizedText(LanguageTag.ENGLISH))
        assertNull(reopened.localizedText(LanguageTag.KOREAN)?.typeNames?.get(31))
        assertEquals("ほのお", reopened.localizedText(LanguageTag.JAPANESE)?.typeNames?.get(31)?.value)
        assertTrue(reopened.typesById.values.all { it.name.value == null })
        assertEquals(TypeSemanticRole.FIRE, reopened.typesById.getValue(21).semanticRole.value)
        assertEquals(TypeSemanticRole.WATER, reopened.typesById.getValue(20).semanticRole.value)
        assertEquals(TypeSemanticRole.MYSTERY, reopened.typesById.getValue(19).semanticRole.value)
        assertEquals(CapabilityStatus.NOT_FOUND, reopened.typesById.getValue(31).semanticRole.status)
        assertNull(reopened.typesById.getValue(31).semanticRole.value)
        assertEquals(listOf(21, 20), reopened.speciesById.getValue(6).typeIds.value)
        assertEquals(21, reopened.movesById.getValue(53).typeId.value)
        assertEquals(catalog.typeChart, reopened.typeChart)
    }

    @Test
    fun `reader validates the manifest inventory before shared payloads`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("d".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Control.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("DELETE FROM catalog_section_chunks WHERE section_name = 'language_overlay:en'")
            database.execute("DELETE FROM catalog_sections WHERE name = 'language_overlay:en'")
            val speciesChunkBytes = database.query(
                "SELECT length(payload) AS payload_bytes FROM catalog_section_chunks " +
                    "WHERE section_name = 'species' AND chunk_index = 0",
            ) { row -> requireNotNull(row.long("payload_bytes")).toInt() }.single()
            database.execute(
                "UPDATE catalog_section_chunks SET payload = ? " +
                    "WHERE section_name = 'species' AND chunk_index = 0",
                listOf(ByteArray(speciesChunkBytes) { 1 }),
            )

            val failure = assertThrows(IllegalArgumentException::class.java) {
                CatalogReader(database).readComplete()
            }
            assertEquals("completed catalog sections do not match the language manifest", failure.message)
        }
    }

    @Test
    fun `reader rejects noncanonical overlay section names`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("e".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Control.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_section_chunks SET section_name = 'language_overlay:EN' " +
                    "WHERE section_name = 'language_overlay:en'",
            )
            database.execute(
                "UPDATE catalog_sections SET name = 'language_overlay:EN' WHERE name = 'language_overlay:en'",
            )
            assertThrows(IllegalArgumentException::class.java) {
                CatalogReader(database).readComplete()
            }
        }
    }

    @Test
    fun `bounded blob adapter rejects before materializing beyond its limit`() {
        val root = newRoot()
        val file = root.resolve("bounded-blob.sqlite").toFile()
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            database.execute("CREATE TABLE blobs(id INTEGER PRIMARY KEY, payload BLOB NOT NULL)")
            database.execute("INSERT INTO blobs(id, payload) VALUES (1, zeroblob(1024))")

            assertThrows(IllegalArgumentException::class.java) {
                database.readBlob(
                    "SELECT payload AS payload FROM blobs WHERE id = 1",
                    maximumBytes = 32,
                )
            }
            database.execute("UPDATE blobs SET payload = zeroblob(32) WHERE id = 1")
            assertEquals(
                32,
                database.readBlob(
                    "SELECT payload AS payload FROM blobs WHERE id = 1",
                    maximumBytes = 32,
                )?.size,
            )
        }
    }

    @Test
    fun `catalog reader keeps blobs out of cursor-backed row queries`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("5".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Cursor contract.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { delegate ->
            val guarded = PayloadProjectionRejectingDatabase(delegate)

            assertEquals(catalog, CatalogReader(guarded).readComplete()?.catalog)
            assertEquals(0, guarded.cursorPayloadProjections)
        }
    }

    @Test
    fun `catalog reader rejects an oversized digest before retrieving its blob`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("d".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Digest control.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("UPDATE catalog_sections SET payload = zeroblob(33) WHERE name = 'species'")
        }

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { delegate ->
            val guarded = GuardedBlobCatalogDatabase(delegate) { sql, row, column ->
                column == "payload" &&
                    sql.contains("FROM catalog_sections") &&
                    row.string("name") == "species"
            }

            assertThrows(IllegalArgumentException::class.java) {
                CatalogReader(guarded).readComplete()
            }
            assertEquals(0, guarded.forbiddenBlobReads)
        }
    }

    @Test
    fun `catalog reader rejects an oversized chunk before retrieving its blob`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("e".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Chunk control.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_section_chunks SET payload = zeroblob(?) " +
                    "WHERE section_name = 'species' AND chunk_index = 0",
                listOf(CatalogSchema.sectionChunkBytes + 1),
            )
        }

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { delegate ->
            val guarded = GuardedBlobCatalogDatabase(delegate) { sql, _, column ->
                column == "payload" && sql.contains("FROM catalog_section_chunks")
            }

            assertThrows(IllegalArgumentException::class.java) {
                CatalogReader(guarded).readComplete()
            }
            assertEquals(0, guarded.forbiddenBlobReads)
        }
    }

    @Test
    fun `catalog reader rejects aggregate chunk bytes before streaming rows`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("f".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Aggregate control.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { delegate ->
            val guarded = OversizedAggregateCatalogDatabase(delegate)

            val failure = assertThrows(IllegalArgumentException::class.java) {
                CatalogReader(guarded).readComplete()
            }
            assertTrue(failure.message.orEmpty().contains("encoded-byte limit"))
            assertEquals(0, guarded.streamQueries)
        }
    }

    @Test
    fun `blocked SHA A does not prevent SHA B persistence and reopen`() {
        val root = newRoot()
        val shaA = "1".repeat(64)
        val shaB = "2".repeat(64)
        val aEntered = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val bStarted = CountDownLatch(1)
        val bFinished = CountDownLatch(1)
        val blockAOnce = AtomicBoolean(true)
        val factory = CatalogDatabaseFactory { file ->
            if (file.name == "$shaA.sqlite" && blockAOnce.compareAndSet(true, false)) {
                aEntered.countDown()
                check(releaseA.await(5, TimeUnit.SECONDS)) { "timed out releasing SHA A" }
            }
            JdbcCatalogDatabaseFactory.open(file)
        }
        val cache = CatalogCache(root.toFile(), factory)
        val source = CatalogSourceMetadata.direct("Coordination control.gba", 16_777_216, "CONTROL")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val a = executor.submit {
                cache.write(completeCatalog(shaA), source, CatalogWriteProgress.complete())
            }
            assertTrue(aEntered.await(2, TimeUnit.SECONDS))
            val b = executor.submit {
                bStarted.countDown()
                cache.write(completeCatalog(shaB), source, CatalogWriteProgress.complete())
                requireNotNull(cache.readComplete(shaB))
                bFinished.countDown()
            }
            assertTrue(bStarted.await(2, TimeUnit.SECONDS))

            assertTrue("SHA B remained blocked behind SHA A", bFinished.await(500, TimeUnit.MILLISECONDS))
            b.get(2, TimeUnit.SECONDS)
            releaseA.countDown()
            a.get(2, TimeUnit.SECONDS)
        } finally {
            releaseA.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `same SHA writers serialize across canonical directory aliases`() {
        val root = newRoot()
        Files.createDirectory(root.resolve("nested"))
        val canonicalCache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val aliasCache = CatalogCache(root.resolve("nested/..").toFile(), JdbcCatalogDatabaseFactory)
        val sha = "3".repeat(64)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val opens = AtomicInteger()
        val factory = CatalogDatabaseFactory { file ->
            if (file.name == "$sha.sqlite") {
                when (opens.incrementAndGet()) {
                    1 -> {
                        firstEntered.countDown()
                        check(releaseFirst.await(5, TimeUnit.SECONDS)) { "timed out releasing first writer" }
                    }
                    2 -> secondEntered.countDown()
                }
            }
            JdbcCatalogDatabaseFactory.open(file)
        }
        val firstCache = CatalogCache(canonicalCache.fileFor(sha).parentFile, factory)
        val secondCache = CatalogCache(aliasCache.fileFor(sha).parentFile, factory)
        val source = CatalogSourceMetadata.direct("Alias control.gba", 16_777_216, "CONTROL")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit {
                firstCache.write(completeCatalog(sha), source, CatalogWriteProgress.complete())
            }
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            val second = executor.submit {
                secondCache.write(completeCatalog(sha), source, CatalogWriteProgress.complete())
            }

            assertFalse(secondEntered.await(150, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            first.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
            assertTrue(secondEntered.await(0, TimeUnit.MILLISECONDS))
        } finally {
            releaseFirst.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `cancellation transition fenced before JDBC commit rolls back publication`() {
        val root = newRoot()
        val file = root.resolve("publication-fence.sqlite").toFile()
        val token = PausingPublicationToken()
        val executor = Executors.newSingleThreadExecutor()
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            database.execute("CREATE TABLE publication(value INTEGER NOT NULL)")
            try {
                val write = executor.submit {
                    database.transaction(token) {
                        database.execute("INSERT INTO publication(value) VALUES (1)")
                        token.throwIfCancellationRequested()
                    }
                }
                assertTrue(token.publicationEntered.await(2, TimeUnit.SECONDS))
                token.cancel()
                token.releasePublication.countDown()

                val failure = assertThrows(java.util.concurrent.ExecutionException::class.java) {
                    write.get(2, TimeUnit.SECONDS)
                }
                assertTrue(failure.cause is ParserCancellationException)
                assertEquals(
                    listOf(0L),
                    database.query("SELECT COUNT(*) AS count FROM publication") { row -> row.long("count") },
                )
            } finally {
                token.releasePublication.countDown()
                executor.shutdown()
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `cancelling catalog encoding emits no later chunks or publication`() {
        val root = newRoot()
        val sha = "4".repeat(64)
        val firstChunkEntered = CountDownLatch(1)
        val releaseFirstChunk = CountDownLatch(1)
        lateinit var recordingDatabase: BlockingFirstChunkCatalogDatabase
        val factory = CatalogDatabaseFactory { file ->
            BlockingFirstChunkCatalogDatabase(
                JdbcCatalogDatabaseFactory.open(file),
                firstChunkEntered,
                releaseFirstChunk,
            ).also { recordingDatabase = it }
        }
        val cache = CatalogCache(root.toFile(), factory)
        val cancellation = ParserCancellationSource()
        val catalog = completeCatalog(sha).copy(diagnostics = listOf("x".repeat(CatalogSchema.sectionChunkBytes * 2)))
        val source = CatalogSourceMetadata.direct("Cancellation control.gba", 16_777_216, "CONTROL")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val write = executor.submit {
                cache.write(catalog, source, CatalogWriteProgress.complete(), cancellation.token)
            }
            assertTrue(firstChunkEntered.await(2, TimeUnit.SECONDS))
            cancellation.cancel()
            releaseFirstChunk.countDown()

            val failure = assertThrows(java.util.concurrent.ExecutionException::class.java) {
                write.get(2, TimeUnit.SECONDS)
            }
            assertTrue(failure.cause is java.util.concurrent.CancellationException)
            assertEquals(1, recordingDatabase.attemptedChunks)
        } finally {
            releaseFirstChunk.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }

        JdbcCatalogDatabaseFactory.open(cache.fileFor(sha)).use { database ->
            assertEquals(
                listOf(0L),
                database.query("SELECT COUNT(*) AS count FROM catalog_metadata") { row -> row.long("count") },
            )
            assertEquals(
                listOf(0L),
                database.query("SELECT COUNT(*) AS count FROM catalog_section_chunks") { row -> row.long("count") },
            )
        }
    }

    @Test
    fun `unchanged checkpoint writes zero catalog chunk bytes`() {
        val root = newRoot()
        val file = root.resolve("unchanged.sqlite").toFile()
        JdbcCatalogDatabaseFactory.open(file).use { delegate ->
            val recording = RecordingCatalogDatabase(delegate)
            val writer = CatalogWriter(recording, clock = { 100L })
            val catalog = completeCatalog("9".repeat(64))
            val source = CatalogSourceMetadata.direct("Emerald.gba", 16_777_216, "POKEMON EMER")

            writer.write(catalog, source, CatalogWriteProgress.complete())
            assertTrue(recording.writtenChunkBytes > 0)
            val firstCheckpointBytes = recording.writtenChunkBytes
            recording.writtenChunkBytes = 0
            writer.write(catalog, source, CatalogWriteProgress.complete())

            assertEquals(0L, recording.writtenChunkBytes)
            println(
                "CATALOG_CHECKPOINT_EVIDENCE firstBytes=$firstCheckpointBytes unchangedBytes=${recording.writtenChunkBytes}",
            )
        }
    }

    @Test
    fun `Unbound completed move descriptions survive the production incremental cache round trip`() {
        val configured = System.getenv("DUALDEX_UNBOUND_ROM")
        assumeTrue("set DUALDEX_UNBOUND_ROM to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val firstRom = RomSourceLoader.load(path).rom
        val secondRom = RomSourceLoader.load(path).rom
        assertEquals("7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7", firstRom.sha256)
        assertEquals(firstRom.sha256, secondRom.sha256)
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val source = CatalogSourceMetadata.direct(path.fileName.toString(), secondRom.size, "POKEMON FIRE")

        val first = requireNotNull(CatalogParser.parse(firstRom).catalog)
        val second = requireNotNull(
            CatalogParser.parse(secondRom) { progress ->
                cache.write(progress.catalog, source, catalogWriteProgress(progress))
            }.catalog,
        )
        val stored = requireNotNull(cache.readComplete(second.romSha256))
        val reopened = stored.catalog
        val firstDescriptions = first.movesById.values
            .filter { it.id > 0 }
            .associate { it.id to it.effectText }
        val secondDescriptions = second.movesById.values
            .filter { it.id > 0 }
            .associate { it.id to it.effectText }

        assertEquals(922, firstDescriptions.size)
        assertEquals(firstDescriptions, secondDescriptions)
        assertEquals(secondDescriptions, reopened.movesById.values.filter { it.id > 0 }.associate { it.id to it.effectText })
        assertEquals("-", reopened.movesById.getValue(769).effectText.value)
        assertEquals(
            "Normal-Type Dynamax move. It lowers the target's Speed stat.",
            reopened.movesById.getValue(821).effectText.value,
        )
        assertEquals(
            mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
            reopened.trainerAssets.overworldAssetKeys,
        )
        reopened.trainerAssets.overworldAssetKeys.values.forEach { key ->
            assertEquals(32, reopened.trainerAssets.assets.getValue(key).width)
            assertEquals(32, reopened.trainerAssets.assets.getValue(key).height)
        }
        assertCatalogReferencesClose(reopened)
        assertEquals(second.romSha256, reopened.romSha256)
        assertEquals(expectedSections(stored.catalog), stored.committedSections)
        assertEquals(expectedSections(stored.catalog).size, stored.committedSections.size)
        assertDatabaseIntegrity(cache.fileFor(second.romSha256))
    }

    @Test
    fun `Odyssey completed Pokedex descriptions and battle-only species survive cache round trip`() {
        val configured = System.getenv("DUALDEX_ODYSSEY_ROM")
        assumeTrue("set DUALDEX_ODYSSEY_ROM to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val firstRom = RomSourceLoader.load(path).rom
        val secondRom = RomSourceLoader.load(path).rom
        assertEquals("44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0", firstRom.sha256)
        assertEquals(firstRom.sha256, secondRom.sha256)
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val source = CatalogSourceMetadata.direct(path.fileName.toString(), secondRom.size, "POKEMON FIRE")

        val first = requireNotNull(CatalogParser.parse(firstRom).catalog)
        val second = requireNotNull(
            CatalogParser.parse(secondRom) { progress ->
                cache.write(progress.catalog, source, catalogWriteProgress(progress))
            }.catalog,
        )
        val stored = requireNotNull(cache.readComplete(second.romSha256))
        val reopened = stored.catalog
        val firstDescriptions = first.navigableSpecies().associate { it.id to it.description }
        val secondDescriptions = second.navigableSpecies().associate { it.id to it.description }

        assertEquals(409, firstDescriptions.size)
        assertEquals(firstDescriptions, secondDescriptions)
        assertEquals(secondDescriptions, reopened.navigableSpecies().associate { it.id to it.description })
        listOf(275, 276).forEach { speciesId ->
            val species = reopened.speciesById.getValue(speciesId)
            assertEquals(CapabilityStatus.NOT_APPLICABLE, species.dexNumber.status)
            assertEquals(CapabilityStatus.NOT_APPLICABLE, species.description.status)
            assertEquals(CapabilityStatus.AVAILABLE, species.name.status)
            assertEquals(CapabilityStatus.AVAILABLE, species.baseStats.status)
            assertEquals(CapabilityStatus.AVAILABLE, species.sprite.status)
        }
        assertEquals(
            mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
            reopened.trainerAssets.overworldAssetKeys,
        )
        reopened.trainerAssets.overworldAssetKeys.values.forEach { key ->
            assertEquals(16, reopened.trainerAssets.assets.getValue(key).width)
            assertEquals(32, reopened.trainerAssets.assets.getValue(key).height)
        }
        assertCatalogReferencesClose(reopened)
        assertEquals(expectedSections(stored.catalog), stored.committedSections)
        assertEquals(expectedSections(stored.catalog).size, stored.committedSections.size)
        assertDatabaseIntegrity(cache.fileFor(second.romSha256))
    }

    @Test
    fun `official Gen3 trainer world sprites survive complete catalog cache round trips`() {
        val controls = listOf(
            Triple(
                "DUALDEX_OFFICIAL_EMERALD_ROM",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                EngineFamily.EMERALD,
            ),
            Triple(
                "DUALDEX_FIRERED_ROM",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                EngineFamily.FIRERED_LEAFGREEN,
            ),
            Triple(
                "DUALDEX_LEAFGREEN_ROM",
                "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
                EngineFamily.FIRERED_LEAFGREEN,
            ),
        )

        controls.forEach { (environmentVariable, expectedSha256, expectedFamily) ->
            val configured = System.getenv(environmentVariable)
            assumeTrue("set $environmentVariable to run this real-ROM control", !configured.isNullOrBlank())
            val path = Path.of(requireNotNull(configured))
            assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
            val rom = RomSourceLoader.load(path).rom
            assertEquals(expectedSha256, rom.sha256)
            val root = newRoot()
            val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
            val source = CatalogSourceMetadata.direct(path.fileName.toString(), rom.size, "POKEMON")

            val parsed = requireNotNull(
                CatalogParser.parse(rom) { progress ->
                    cache.write(progress.catalog, source, catalogWriteProgress(progress))
                }.catalog,
            )
            val stored = requireNotNull(cache.readComplete(parsed.romSha256))
            val reopened = stored.catalog

            assertEquals(expectedFamily, reopened.family)
            assertEquals(
                mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
                reopened.trainerAssets.overworldAssetKeys,
            )
            reopened.trainerAssets.overworldAssetKeys.values.forEach { key ->
                assertEquals(16, reopened.trainerAssets.assets.getValue(key).width)
                assertEquals(32, reopened.trainerAssets.assets.getValue(key).height)
            }
            assertEquals(expectedSections(stored.catalog), stored.committedSections)
            assertEquals(expectedSections(stored.catalog).size, stored.committedSections.size)
            assertCatalogReferencesClose(reopened)
            assertDatabaseIntegrity(cache.fileFor(parsed.romSha256))
        }
    }

    @Test
    fun `large binary catalog section is chunked below the Android cursor window budget`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val bytes = ByteArray(4 * 1024 * 1024).also { png ->
            Random(0xD0A1DE5L).nextBytes(png)
            byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10).copyInto(png)
        }
        val map = LocalMap("local/0001", "Large", 1, 16, 16, 1, 1, "local/0001/map")
        val catalog = localizedFixtureCatalog(
            completeCatalog("6".repeat(64)),
            localMaps = LocalMapCatalog(listOf(map), mapOf(map.imageAssetKey to PngMapAsset(bytes))),
        )

        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Large.gba", 32 * 1024 * 1024, "LARGE"),
            CatalogWriteProgress.complete(),
        )
        val reopened = requireNotNull(cache.readComplete(catalog.romSha256))

        assertTrue(reopened.catalog.localMaps.assets.getValue(map.imageAssetKey).bytes.contentEquals(bytes))
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            assertEquals(
                "gzip+json+chunks-v1",
                database.query(
                    "SELECT encoding FROM catalog_sections WHERE name = 'local_maps'",
                ) { row -> row.string("encoding") }.single(),
            )
            val chunks = database.query(
                """
                SELECT chunk_index, length(payload) AS payload_bytes
                FROM catalog_section_chunks
                WHERE section_name = 'local_maps'
                ORDER BY chunk_index
                """.trimIndent(),
            ) { row ->
                requireNotNull(row.long("chunk_index")).toInt() to
                    requireNotNull(row.long("payload_bytes")).toInt()
            }
            assertTrue(chunks.size > 1)
            assertEquals(chunks.indices.toList(), chunks.map(Pair<Int, Int>::first))
            assertTrue(chunks.all { (_, size) -> size in 1..CatalogSchema.sectionChunkBytes })
            println(
                "CATALOG_SECTION_EVIDENCE rawAssetBytes=${bytes.size} compressedBytes=${chunks.sumOf { it.second }} chunks=${chunks.size}",
            )
            assertEquals(
                listOf(32L),
                database.query(
                    "SELECT length(payload) AS payload_bytes FROM catalog_sections WHERE name = 'local_maps'",
                ) { row -> row.long("payload_bytes") },
            )
        }
    }

    @Test
    fun `normalized world and local maps survive a complete catalog round trip`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val raster = RgbaSprite(2, 2, intArrayOf(0xff102030.toInt(), 0xff405060.toInt(), 0xff708090.toInt(), 0xffa0b0c0.toInt()))
        val worldMaps = WorldMapCatalog(
            regions = listOf(
                WorldMapRegion(
                    key = "region-0",
                    displayName = null,
                    pixelWidth = 2,
                    pixelHeight = 2,
                    gridWidth = 22,
                    gridHeight = 15,
                    imageAssetKey = "world/region-0",
                    locations = listOf(
                        WorldMapLocation(
                            key = "section-0",
                            displayName = null,
                            baseAreaIds = setOf(0x0102),
                            geometry = listOf(WorldMapCell(4, 5, 2, 1)),
                        ),
                    ),
                ),
            ),
            assets = mapOf("world/region-0" to raster),
        )
        val localPng = PngMapAsset(PngEncoder.encode(RgbaSprite(32, 32, IntArray(32 * 32) { 0xff102030.toInt() })))
        val indexedPalettes = MapLightingPalettes(
            morning = IntArray(32) { 0xff100000.toInt() + it },
            day = IntArray(32) { 0xff200000.toInt() + it },
            night = IntArray(32) { 0xff300000.toInt() + it },
            dark = IntArray(32) { 0xff400000.toInt() + it },
        )
        val indexedAsset = IndexedMapAsset(
            pixelWidth = 32,
            pixelHeight = 32,
            compressedIndices = LocalMapRasterCodec.compress(ByteArray(32 * 32) { (it % 32).toByte() }),
            lightingPolicy = LocalMapLightingPolicy.AUTO,
            palettes = indexedPalettes,
        )
        val timedAsset = TimedIndexedMapAsset(
            pixelWidth = 32,
            pixelHeight = 32,
            compressedIndices = LocalMapRasterCodec.compress(ByteArray(32 * 32) { (it % 256).toByte() }),
            baseColors = IntArray(256) { it },
            alternateColors = IntArray(256) { 0x7FFF - it },
            alternatePaletteMask = 0x124,
            paletteModel = MapTimePaletteModel(
                night = MapTimeBlend(0x9D7474, tint = true, coefficient = 10),
                twilight = MapTimeBlend(0xA8B0E0, tint = true, coefficient = 4),
                day = MapTimeBlend(0, tint = false, coefficient = 0),
            ),
        )
        val localMaps = LocalMapCatalog(
            maps = listOf(
                LocalMap("local/0102", "Route Test", 0x0102, 32, 32, 2, 2, "local/0102/map"),
                LocalMap("local/0103", "Indexed Test", 0x0103, 32, 32, 2, 2, "local/0103/map"),
                LocalMap("local/0104", "Timed Test", 0x0104, 32, 32, 2, 2, "local/0104/map"),
            ),
            assets = mapOf("local/0102/map" to localPng),
            indexedAssets = mapOf("local/0103/map" to indexedAsset),
            timedAssets = mapOf("local/0104/map" to timedAsset),
            scenes = listOf(
                LocalMapScene(
                    key = "scene/0102",
                    gridWidth = 4,
                    gridHeight = 2,
                    placements = listOf(
                        LocalMapScenePlacement("local/0102", 0x0102, 0, 0),
                        LocalMapScenePlacement("local/0103", 0x0103, 2, 0),
                    ),
                ),
            ),
            pois = listOf(
                LocalMapPoi(
                    key = "local/0102/bg/0",
                    localMapKey = "local/0102",
                    baseAreaId = 0x0102,
                    tileX = 1,
                    tileY = 1,
                    kind = LocalMapPoiKind.HIDDEN_ITEM,
                    organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                    item = LocalMapPoiItem(13, "Potion", 0x52),
                ),
            ),
        )
        val catalog = localizedFixtureCatalog(
            completeCatalog("7".repeat(64)),
            runtimeMetadata = CatalogRuntimeMetadata(gen2TimeOfDayWramOffset = 0x1841),
            worldMaps = worldMaps,
            localMaps = localMaps,
        )

        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Control.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )
        val reopened = cache.readComplete(catalog.romSha256)

        assertEquals(52, CatalogSchema.parserSchemaVersion)
        assertEquals(catalog.worldMaps, reopened?.catalog?.worldMaps)
        assertEquals(catalog.localMaps.maps, reopened?.catalog?.localMaps?.maps)
        assertEquals(catalog.localMaps.scenes, reopened?.catalog?.localMaps?.scenes)
        assertEquals(catalog.localMaps.pois, reopened?.catalog?.localMaps?.pois)
        assertEquals(
            "Route Test",
            reopened?.catalog?.defaultLocalizedText()?.localMapNames?.get("local/0102")?.value,
        )
        assertEquals(
            "Potion",
            reopened?.catalog?.defaultLocalizedText()?.itemNames?.get(13)?.value,
        )
        assertEquals(localPng.bytes.toList(), reopened?.catalog?.localMaps?.assets?.get("local/0102/map")?.bytes?.toList())
        val reopenedIndexed = reopened?.catalog?.localMaps?.indexedAssets?.get("local/0103/map")
        assertEquals(indexedAsset.pixelWidth, reopenedIndexed?.pixelWidth)
        assertEquals(indexedAsset.pixelHeight, reopenedIndexed?.pixelHeight)
        assertEquals(indexedAsset.compressedIndices.toList(), reopenedIndexed?.compressedIndices?.toList())
        assertEquals(indexedAsset.lightingPolicy, reopenedIndexed?.lightingPolicy)
        assertEquals(indexedAsset.palettes.morning.toList(), reopenedIndexed?.palettes?.morning?.toList())
        assertEquals(indexedAsset.palettes.day.toList(), reopenedIndexed?.palettes?.day?.toList())
        assertEquals(indexedAsset.palettes.night.toList(), reopenedIndexed?.palettes?.night?.toList())
        assertEquals(indexedAsset.palettes.dark.toList(), reopenedIndexed?.palettes?.dark?.toList())
        val reopenedTimed = reopened?.catalog?.localMaps?.timedAssets?.get("local/0104/map")
        assertEquals(timedAsset.compressedIndices.toList(), reopenedTimed?.compressedIndices?.toList())
        assertEquals(timedAsset.baseColors.toList(), reopenedTimed?.baseColors?.toList())
        assertEquals(timedAsset.alternateColors.toList(), reopenedTimed?.alternateColors?.toList())
        assertEquals(timedAsset.alternatePaletteMask, reopenedTimed?.alternatePaletteMask)
        assertEquals(timedAsset.paletteModel, reopenedTimed?.paletteModel)
        assertEquals(0x1841, reopened?.catalog?.runtimeMetadata?.gen2TimeOfDayWramOffset)
        assertEquals(raster.argb.toList(), reopened?.catalog?.worldMaps?.assets?.get("world/region-0")?.argb?.toList())
        assertEquals(
            expectedSections(requireNotNull(reopened).catalog),
            reopened.committedSections,
        )
    }

    @Test
    fun `Modern Emerald world and local maps survive the production incremental cache round trip`() {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895", rom.sha256)
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val source = CatalogSourceMetadata.direct(path.fileName.toString(), rom.size, "POKEMON EMER")
        val catalog = requireNotNull(
            CatalogParser.parse(rom) { progress ->
                cache.write(progress.catalog, source, catalogWriteProgress(progress))
            }.catalog,
        )

        val stored = requireNotNull(cache.readComplete(catalog.romSha256))
        val reopened = stored.catalog

        assertEquals(expectedSections(stored.catalog), stored.committedSections)
        assertEquals(1, reopened.worldMaps.regions.size)
        assertEquals(557, reopened.localMaps.maps.size)
        assertEquals(
            mapOf(0 to "trainer/avatar/male", 1 to "trainer/avatar/female"),
            reopened.trainerAssets.avatarAssetKeys,
        )
        assertEquals(
            mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
            reopened.trainerAssets.overworldAssetKeys,
        )
        assertEquals(
            reopened.trainerAssets.avatarAssetKeys.values.toSet() + reopened.trainerAssets.overworldAssetKeys.values,
            reopened.trainerAssets.assets.keys,
        )
        reopened.trainerAssets.avatarAssetKeys.values.forEach { key ->
            assertEquals(64, reopened.trainerAssets.assets.getValue(key).width)
            assertEquals(64, reopened.trainerAssets.assets.getValue(key).height)
        }
        reopened.trainerAssets.overworldAssetKeys.values.forEach { key ->
            assertEquals(16, reopened.trainerAssets.assets.getValue(key).width)
            assertEquals(32, reopened.trainerAssets.assets.getValue(key).height)
        }
        assertEquals(catalog.localMaps.maps, reopened.localMaps.maps)
        assertEquals(catalog.localMaps.pois, reopened.localMaps.pois)
        assertEquals(
            mapOf(0 to "{PLAYER}'s House", 1 to "Prof. Birch's House"),
            reopened.localMaps.pois.single {
                it.baseAreaId == 0x0009 && it.tileX == 7 && it.tileY == 8
            }.displayNamesByTrainerGender,
        )
        assertEquals(catalog.localMaps.assets.keys, reopened.localMaps.assets.keys)
        assertEquals(catalog.localMaps.timedAssets.keys, reopened.localMaps.timedAssets.keys)
        assertTrue(reopened.localMaps.timedAssets.isNotEmpty())
        assertEquals(
            "Littleroot Town",
            reopened.localMaps.maps.single { it.baseAreaId == 0x0009 }.displayName,
        )
        assertEquals(
            "Littleroot Town",
            reopened.worldMaps.regions.flatMap { it.locations }
                .single { 0x0009 in it.baseAreaIds }
                .displayName,
        )
        val route102Asset = catalog.localMaps.maps.single { it.baseAreaId == 0x0011 }.imageAssetKey
        val expectedTimed = catalog.localMaps.timedAssets.getValue(route102Asset)
        val reopenedTimed = reopened.localMaps.timedAssets.getValue(route102Asset)
        assertEquals(expectedTimed.compressedIndices.toList(), reopenedTimed.compressedIndices.toList())
        assertEquals(expectedTimed.baseColors.toList(), reopenedTimed.baseColors.toList())
        assertEquals(expectedTimed.alternateColors.toList(), reopenedTimed.alternateColors.toList())
        assertEquals(expectedTimed.alternatePaletteMask, reopenedTimed.alternatePaletteMask)
        assertEquals(expectedTimed.paletteModel, reopenedTimed.paletteModel)

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("UPDATE catalog_metadata SET parser_schema_version = 16 WHERE id = 1")
        }
        assertNull(cache.readComplete(catalog.romSha256))
    }

    @Test
    fun `official Gen I local map assets survive a complete cache round trip`() {
        val configured = System.getenv("DUALDEX_POKERED_ROM")
        assumeTrue("set DUALDEX_POKERED_ROM to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b", rom.sha256)
        val catalog = requireNotNull(CatalogParser.parseCatching(rom).catalog).getOrThrow()
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)

        cache.write(
            catalog,
            CatalogSourceMetadata.direct(path.fileName.toString(), rom.size, "POKEMON RED"),
            CatalogWriteProgress.complete(),
        )
        val reopened = requireNotNull(cache.readComplete(catalog.romSha256)).catalog

        assertEquals(226, reopened.localMaps.maps.size)
        assertEquals(catalog.localMaps.maps, reopened.localMaps.maps)
        assertEquals(catalog.localMaps.scenes, reopened.localMaps.scenes)
        assertEquals(catalog.localMaps.assets.keys, reopened.localMaps.assets.keys)
        val palletTownAsset = catalog.localMaps.maps.single { it.baseAreaId == 0x00 }.imageAssetKey
        assertTrue(
            catalog.localMaps.assets.getValue(palletTownAsset).bytes.contentEquals(
                reopened.localMaps.assets.getValue(palletTownAsset).bytes,
            ),
        )
        assertEquals(catalog.trainerAssets.overworldAssetKeys, reopened.trainerAssets.overworldAssetKeys)
        assertEquals(catalog.trainerAssets.assets, reopened.trainerAssets.assets)
    }

    @Test
    fun `official Gen II local map assets survive a complete cache round trip`() {
        val configured = System.getenv("DUALDEX_POKECRYSTAL_ROM")
        assumeTrue("set DUALDEX_POKECRYSTAL_ROM to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2", rom.sha256)
        val catalog = requireNotNull(CatalogParser.parseCatching(rom).catalog).getOrThrow()
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)

        cache.write(
            catalog,
            CatalogSourceMetadata.direct(path.fileName.toString(), rom.size, "PM_CRYSTAL"),
            CatalogWriteProgress.complete(),
        )
        val reopened = requireNotNull(cache.readComplete(catalog.romSha256)).catalog

        assertEquals(388, reopened.localMaps.maps.size)
        assertEquals(catalog.localMaps.maps, reopened.localMaps.maps)
        assertEquals(catalog.localMaps.scenes, reopened.localMaps.scenes)
        assertTrue(reopened.localMaps.assets.isEmpty())
        assertEquals(catalog.localMaps.indexedAssets.keys, reopened.localMaps.indexedAssets.keys)
        val battleTowerAsset = catalog.localMaps.maps.single { it.baseAreaId == 0x1610 }.imageAssetKey
        val expectedAsset = catalog.localMaps.indexedAssets.getValue(battleTowerAsset)
        val reopenedAsset = reopened.localMaps.indexedAssets.getValue(battleTowerAsset)
        assertEquals(expectedAsset.compressedIndices.toList(), reopenedAsset.compressedIndices.toList())
        assertEquals(expectedAsset.lightingPolicy, reopenedAsset.lightingPolicy)
        assertEquals(expectedAsset.palettes.morning.toList(), reopenedAsset.palettes.morning.toList())
        assertEquals(expectedAsset.palettes.day.toList(), reopenedAsset.palettes.day.toList())
        assertEquals(expectedAsset.palettes.night.toList(), reopenedAsset.palettes.night.toList())
        assertEquals(expectedAsset.palettes.dark.toList(), reopenedAsset.palettes.dark.toList())
        assertEquals(0x1841, reopened.runtimeMetadata.gen2TimeOfDayWramOffset)
        assertEquals(catalog.trainerAssets.overworldAssetKeys, reopened.trainerAssets.overworldAssetKeys)
        assertEquals(catalog.trainerAssets.assets, reopened.trainerAssets.assets)
    }

    @Test
    fun `legacy encounter sections without windows reopen as unrestricted`() {
        val catalog = completeCatalog("f".repeat(64))
        val codec = CatalogSectionCodec()
        val sections = codec.encode(catalog, CatalogSectionPlan.from(catalog.localization).sections).toMutableMap()
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
        val sections = codec.encode(catalog, CatalogSectionPlan.from(catalog.localization).sections).toMutableMap()
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
                """
                SELECT chunk_index, payload FROM catalog_section_chunks
                WHERE section_name = 'capabilities' ORDER BY chunk_index
                """.trimIndent(),
            ) { row ->
                requireNotNull(row.long("chunk_index")).toInt() to requireNotNull(row.bytes("payload"))
            }.also { chunks ->
                assertEquals(chunks.indices.toList(), chunks.map(Pair<Int, ByteArray>::first))
            }.let { chunks ->
                ByteArrayOutputStream().also { output -> chunks.forEach { output.write(it.second) } }.toByteArray()
            }
            val legacyJson = GZIPInputStream(ByteArrayInputStream(payload)).use {
            it.readBytes().toString(Charsets.UTF_8)
            }.replace(Regex(",\"validatorReviewRecommended\":true"), "")
            val legacyPayload = ByteArrayOutputStream().also { output ->
                GZIPOutputStream(output).use { it.write(legacyJson.toByteArray(Charsets.UTF_8)) }
            }.toByteArray()
            database.execute("DELETE FROM catalog_section_chunks WHERE section_name = 'capabilities'")
            legacyPayload.asList().chunked(CatalogSchema.sectionChunkBytes).forEachIndexed { index, bytes ->
                database.execute(
                    "INSERT INTO catalog_section_chunks(section_name, chunk_index, payload) VALUES ('capabilities', ?, ?)",
                    listOf(index, bytes.toByteArray()),
                )
            }
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
    fun `catalog schema migration retains unrelated database state`() {
        val databaseFile = newRoot().resolve("migration.sqlite").toFile()
        JdbcCatalogDatabaseFactory.open(databaseFile).use { database ->
            CatalogMigration.prepare(database)
            database.execute("CREATE TABLE retained_settings (value TEXT NOT NULL)")
            database.execute("INSERT INTO retained_settings (value) VALUES ('keep')")
            database.execute(
                """
                INSERT INTO catalog_metadata (
                    id, schema_version, parser_schema_version, sha256, crc32, rom_size, rom_title,
                    source_name, source_kind, source_entry, family, platform, phase,
                    completed_units, total_units, is_complete, written_at_epoch_ms
                ) VALUES (1, 1, 47, ?, '00000000', 1, 'OLD', 'Old.gba', 'DIRECT', NULL,
                    'OFFICIAL_GEN_III', 'GBA', 'COMPLETE', 1, 1, 1, 0)
                """.trimIndent(),
                listOf("0".repeat(64)),
            )
            database.execute("PRAGMA user_version = 1")

            CatalogMigration.prepare(database)

            assertEquals(
                CatalogSchema.version,
                database.query("PRAGMA user_version") { row -> row.long("user_version")?.toInt() }.single(),
            )
            assertEquals(
                listOf("keep"),
                database.query("SELECT value FROM retained_settings") { row -> row.string("value") },
            )
            assertEquals(
                0L,
                database.query("SELECT COUNT(*) AS count FROM catalog_metadata") { row -> row.long("count") }.single(),
            )
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

        assertEquals(52, CatalogSchema.parserSchemaVersion)
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
        assertEquals(0x030036F0L, reopened?.catalog?.runtimeMetadata?.gen3SaveBlock1PointerAddress)
        assertEquals(
            catalog.runtimeMetadata.gen3RuntimeMemoryLayout,
            reopened?.catalog?.runtimeMetadata?.gen3RuntimeMemoryLayout,
        )
        assertEquals(setOf(0x0010), reopened?.catalog?.runtimeMetadata?.areaBaseIds)
        assertEquals(
            "Route 101",
            reopened?.catalog?.defaultLocalizedText()?.areaNames?.get(0x0010)?.value,
        )
        assertEquals(
            expectedSections(requireNotNull(reopened).catalog),
            reopened.committedSections,
        )
        assertEquals(catalog.theme, reopened.catalog.theme)
    }

    @Test
    fun `revision 19 caches without the required theme section are invalidated`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("2".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Legacy.gba", 16_777_216, "POKEMON EMER"),
            CatalogWriteProgress.complete(),
        )
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("DELETE FROM catalog_sections WHERE name = 'theme'")
            database.execute("UPDATE catalog_metadata SET parser_schema_version = 19 WHERE id = 1")
        }

        assertNull(cache.readComplete(catalog.romSha256))
    }

    @Test
    fun `revision 27 caches are invalidated so partial trainer portraits are rebuilt`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("3".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Legacy trainer assets.gba", 16_777_216, "POKEMON EMER"),
            CatalogWriteProgress.complete(),
        )
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("UPDATE catalog_metadata SET parser_schema_version = 27 WHERE id = 1")
        }

        assertNull(cache.readComplete(catalog.romSha256))
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
    fun `revision 34 catalogs are invalidated so GB map scenes and trainer assets are rebuilt`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("d".repeat(64))
        val source = CatalogSourceMetadata.direct("Yellow.gb", 1_048_576, "POKEMON YELLOW")
        cache.write(catalog, source, CatalogWriteProgress.complete())

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_metadata SET parser_schema_version = ? WHERE id = 1",
                listOf(34),
            )
        }

        assertNull(cache.readComplete(catalog.romSha256))
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            assertEquals(listOf(0L), database.query("SELECT COUNT(*) AS count FROM catalog_metadata") { it.long("count") })
            assertEquals(listOf(0L), database.query("SELECT COUNT(*) AS count FROM catalog_sections") { it.long("count") })
            assertEquals(listOf(0L), database.query("SELECT COUNT(*) AS count FROM catalog_section_chunks") { it.long("count") })
        }

        val reparsed = catalog.copy(diagnostics = listOf("reparsed with the current schema"))
        cache.write(reparsed, source, CatalogWriteProgress.complete())

        assertEquals(reparsed, cache.readComplete(catalog.romSha256)?.catalog)
    }

    @Test
    fun `revision 42 caches are invalidated so hybrid move details are rebuilt`() {
        assertEquals(52, CatalogSchema.parserSchemaVersion)
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("4".repeat(64)).copy(diagnostics = listOf("pre-hybrid move output"))
        val source = CatalogSourceMetadata.direct("Inclement Emerald.gba", 32 * 1024 * 1024, "POKEMON EMER")
        cache.write(catalog, source, CatalogWriteProgress.complete())
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_metadata SET parser_schema_version = ? WHERE id = 1",
                listOf(42),
            )
        }

        assertNull(cache.readComplete(catalog.romSha256))

        val reparsed = catalog.copy(diagnostics = listOf("hybrid move output rebuilt"))
        cache.write(reparsed, source, CatalogWriteProgress.complete())
        assertEquals(reparsed, cache.readComplete(catalog.romSha256)?.catalog)
    }

    @Test
    fun `revision 43 caches are invalidated so optional relationship evidence is rebuilt`() {
        assertEquals(52, CatalogSchema.parserSchemaVersion)
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("5".repeat(64)).copy(diagnostics = listOf("pre-isolation relationship output"))
        val source = CatalogSourceMetadata.direct("Relationship Control.gba", 32 * 1024 * 1024, "POKEMON EMER")
        cache.write(catalog, source, CatalogWriteProgress.complete())
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_metadata SET parser_schema_version = ? WHERE id = 1",
                listOf(43),
            )
        }

        assertNull(cache.readComplete(catalog.romSha256))

        val reparsed = catalog.copy(diagnostics = listOf("relationship evidence rebuilt"))
        cache.write(reparsed, source, CatalogWriteProgress.complete())
        assertEquals(reparsed, cache.readComplete(catalog.romSha256)?.catalog)
    }

    @Test
    fun `revision 44 caches are invalidated so bounded detached Gen I evidence is rebuilt`() {
        assertEquals(52, CatalogSchema.parserSchemaVersion)
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("6".repeat(64)).copy(diagnostics = listOf("pre-bounded detached Gen I output"))
        val source = CatalogSourceMetadata.direct("Gen I Control.gb", 1 * 1024 * 1024, "POKEMON RED")
        cache.write(catalog, source, CatalogWriteProgress.complete())
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_metadata SET parser_schema_version = ? WHERE id = 1",
                listOf(44),
            )
        }

        assertNull(cache.readComplete(catalog.romSha256))

        val reparsed = catalog.copy(diagnostics = listOf("bounded detached Gen I output rebuilt"))
        cache.write(reparsed, source, CatalogWriteProgress.complete())
        assertEquals(reparsed, cache.readComplete(catalog.romSha256)?.catalog)
    }

    @Test
    fun `revision 45 caches are invalidated so Gen I applicability and bounded fallbacks are rebuilt`() {
        assertEquals(52, CatalogSchema.parserSchemaVersion)
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("7".repeat(64)).copy(
            diagnostics = listOf("pre-Gen I applicability and fallback bounds output"),
        )
        val source = CatalogSourceMetadata.direct("Gen I Applicability Control.gb", 1 * 1024 * 1024, "POKEMON RED")
        cache.write(catalog, source, CatalogWriteProgress.complete())
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_metadata SET parser_schema_version = ? WHERE id = 1",
                listOf(45),
            )
        }

        assertNull(cache.readComplete(catalog.romSha256))

        val reparsed = catalog.copy(diagnostics = listOf("Gen I applicability and bounded fallbacks rebuilt"))
        cache.write(reparsed, source, CatalogWriteProgress.complete())
        assertEquals(reparsed, cache.readComplete(catalog.romSha256)?.catalog)
    }

    @Test
    fun `cache rejects a valid catalog stored under another ROM identity`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val requestedSha = "a".repeat(64)
        val storedCatalog = completeCatalog("b".repeat(64))
        cache.write(
            storedCatalog,
            CatalogSourceMetadata.direct("Control-B.gba", 16_777_216, "CONTROL B"),
            CatalogWriteProgress.complete(),
        )
        Files.copy(
            cache.fileFor(storedCatalog.romSha256).toPath(),
            cache.fileFor(requestedSha).toPath(),
        )

        val lookup = cache.lookupComplete(requestedSha)

        assertNull(lookup.stored)
        assertEquals(CatalogCacheDecision.REJECTED_EXCEPTION, lookup.decision)
        assertFalse(cache.fileFor(requestedSha).exists())
        assertEquals(storedCatalog, cache.readComplete(storedCatalog.romSha256)?.catalog)
    }

    @Test
    fun `cache rejects valid gzip substituted without its matching digest`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("8".repeat(64))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Control.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )
        val replacement = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write("[\"substituted\"]".toByteArray(Charsets.UTF_8))
            }
        }.toByteArray()
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute("DELETE FROM catalog_section_chunks WHERE section_name = 'diagnostics'")
            database.execute(
                """
                INSERT INTO catalog_section_chunks(section_name, chunk_index, payload)
                VALUES ('diagnostics', 0, ?)
                """.trimIndent(),
                listOf(replacement),
            )
        }

        val lookup = cache.lookupComplete(catalog.romSha256)

        assertNull(lookup.stored)
        assertEquals(CatalogCacheDecision.REJECTED_EXCEPTION, lookup.decision)
        assertFalse(cache.fileFor(catalog.romSha256).exists())
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

    @Test
    fun `cache decisions distinguish absence hit incompatibility and read rejection`() {
        val root = newRoot()
        val events = mutableListOf<CatalogCacheEvent>()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory, events::add)
        val catalog = completeCatalog("f".repeat(64))

        assertNull(cache.readComplete(catalog.romSha256))
        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Control.gba", 16_777_216, "CONTROL"),
            CatalogWriteProgress.complete(),
        )
        requireNotNull(cache.readComplete(catalog.romSha256))
        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_metadata SET parser_schema_version = ? WHERE id = 1",
                listOf(CatalogSchema.parserSchemaVersion - 1),
            )
        }
        assertNull(cache.readComplete(catalog.romSha256))

        val corruptHash = "0".repeat(64)
        Files.write(cache.fileFor(corruptHash).toPath(), byteArrayOf(1, 2, 3, 4))
        assertNull(cache.readComplete(corruptHash))

        assertEquals(
            listOf(
                CatalogCacheDecision.MISS_FILE_ABSENT,
                CatalogCacheDecision.HIT,
                CatalogCacheDecision.MISS_INCOMPLETE_OR_INCOMPATIBLE,
                CatalogCacheDecision.REJECTED_EXCEPTION,
            ),
            events.map(CatalogCacheEvent::decision),
        )
        assertEquals(corruptHash, events.last().sha256)
        assertTrue(events.last().failure is Exception)
        assertFalse(cache.fileFor(corruptHash).exists())
    }

    private class PayloadProjectionRejectingDatabase(
        private val delegate: CatalogDatabase,
    ) : CatalogDatabase by delegate {
        var cursorPayloadProjections = 0
            private set

        override fun <T> query(
            sql: String,
            arguments: List<Any?>,
            map: (CatalogRow) -> T,
        ): List<T> {
            rejectCursorPayload(sql)
            return delegate.query(sql, arguments, map)
        }

        override fun <T> streamQuery(
            sql: String,
            arguments: List<Any?>,
            consume: (CatalogRows) -> T,
        ): T {
            rejectCursorPayload(sql)
            return delegate.streamQuery(sql, arguments, consume)
        }

        private fun rejectCursorPayload(sql: String) {
            val projection = sql.substringBefore("FROM", missingDelimiterValue = sql)
                .replace(Regex("length\\s*\\(\\s*payload\\s*\\)", RegexOption.IGNORE_CASE), "")
            if (Regex("\\bpayload\\b", RegexOption.IGNORE_CASE).containsMatchIn(projection)) {
                cursorPayloadProjections++
                throw AssertionError("cursor-backed query projected a catalog blob")
            }
        }
    }

    private class GuardedBlobCatalogDatabase(
        private val delegate: CatalogDatabase,
        private val forbidden: (String, CatalogRow, String) -> Boolean,
    ) : CatalogDatabase by delegate {
        var forbiddenBlobReads = 0
            private set

        override fun <T> query(
            sql: String,
            arguments: List<Any?>,
            map: (CatalogRow) -> T,
        ): List<T> = delegate.query(sql, arguments) { row -> map(guardedRow(sql, row)) }

        override fun <T> streamQuery(
            sql: String,
            arguments: List<Any?>,
            consume: (CatalogRows) -> T,
        ): T = delegate.streamQuery(sql, arguments) { rows ->
            consume(CatalogRows { rows.next()?.let { guardedRow(sql, it) } })
        }

        private fun guardedRow(sql: String, row: CatalogRow): CatalogRow = object : CatalogRow by row {
            override fun bytes(column: String): ByteArray? {
                if (forbidden(sql, row, column)) {
                    forbiddenBlobReads++
                    throw AssertionError("oversized blob was retrieved before its projected length was validated")
                }
                return row.bytes(column)
            }
        }
    }

    private class OversizedAggregateCatalogDatabase(
        private val delegate: CatalogDatabase,
    ) : CatalogDatabase by delegate {
        var streamQueries = 0
            private set

        override fun <T> query(
            sql: String,
            arguments: List<Any?>,
            map: (CatalogRow) -> T,
        ): List<T> {
            if (sql.contains("GROUP BY section_name")) {
                return listOf(
                    map(
                        object : CatalogRow {
                            override fun string(column: String): String? = when (column) {
                                "section_name" -> "species"
                                else -> null
                            }

                            override fun long(column: String): Long? = when (column) {
                                "chunk_count" -> 1L
                                "payload_bytes" -> CatalogSchema.maximumSectionEncodedBytes.toLong() + 1L
                                "maximum_payload_bytes" -> 1L
                                else -> null
                            }

                            override fun bytes(column: String): ByteArray? = null
                        },
                    ),
                )
            }
            return delegate.query(sql, arguments, map)
        }

        override fun <T> streamQuery(
            sql: String,
            arguments: List<Any?>,
            consume: (CatalogRows) -> T,
        ): T {
            streamQueries++
            return delegate.streamQuery(sql, arguments, consume)
        }
    }

    private class PausingPublicationToken : ParserCancellationToken {
        private val cancelled = AtomicBoolean()
        val publicationEntered = CountDownLatch(1)
        val releasePublication = CountDownLatch(1)

        override fun throwIfCancellationRequested() {
            if (cancelled.get()) throw ParserCancellationException()
        }

        override fun <T> publish(block: () -> T): T {
            publicationEntered.countDown()
            check(releasePublication.await(5, TimeUnit.SECONDS)) { "timed out releasing publication fence" }
            throwIfCancellationRequested()
            return block()
        }

        fun cancel() {
            cancelled.set(true)
        }
    }

    private class BlockingFirstChunkCatalogDatabase(
        private val delegate: CatalogDatabase,
        private val firstChunkEntered: CountDownLatch,
        private val releaseFirstChunk: CountDownLatch,
    ) : CatalogDatabase by delegate {
        var attemptedChunks = 0
            private set

        override fun execute(sql: String, arguments: List<Any?>) {
            if (sql.contains("INSERT INTO catalog_section_chunks")) {
                attemptedChunks++
                if (attemptedChunks == 1) {
                    firstChunkEntered.countDown()
                    check(releaseFirstChunk.await(5, TimeUnit.SECONDS)) { "timed out releasing first catalog chunk" }
                }
            }
            delegate.execute(sql, arguments)
        }
    }

    private class RecordingCatalogDatabase(
        private val delegate: CatalogDatabase,
    ) : CatalogDatabase by delegate {
        var writtenChunkBytes = 0L

        override fun execute(sql: String, arguments: List<Any?>) {
            if (sql.contains("INSERT INTO catalog_section_chunks")) {
                writtenChunkBytes += arguments.filterIsInstance<ByteArray>().sumOf(ByteArray::size)
            }
            delegate.execute(sql, arguments)
        }
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

    private fun assertDatabaseIntegrity(file: java.io.File) {
        assertTrue(file.length() > 0)
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            assertEquals(listOf("ok"), database.query("PRAGMA quick_check") { row -> row.string("quick_check") })
            assertTrue(database.query("PRAGMA foreign_key_check") { row -> row.string("table") }.isEmpty())
            assertEquals(
                listOf(CatalogSchema.requiredSections.size.toLong()),
                database.query("SELECT COUNT(*) AS count FROM catalog_sections") { row -> row.long("count") },
            )
        }
    }

    private fun assertCatalogReferencesClose(catalog: ParsedCatalog) {
        catalog.navigableSpecies().forEach { species ->
            species.typeIds.value.orEmpty().forEach { assertTrue("missing type $it", it in catalog.typesById) }
            species.abilityIds.value.orEmpty().filter { it > 0 }.forEach {
                assertTrue("missing ability $it", it in catalog.abilitiesById)
            }
            species.evolutionEdges.value.orEmpty().forEach {
                assertTrue("missing evolution target ${it.targetSpeciesId}", it.targetSpeciesId in catalog.speciesById)
            }
            species.learnset.value.orEmpty().forEach {
                assertTrue("missing learned move ${it.moveId}", it.moveId in catalog.movesById)
            }
            species.moveAcquisitions.value.orEmpty().filter { it.moveId > 0 }.forEach {
                assertTrue("missing acquired move ${it.moveId}", it.moveId in catalog.movesById)
            }
        }
        catalog.encounterAreas.flatMap { it.slots }.forEach {
            assertTrue("missing encounter species ${it.speciesId}", it.speciesId in catalog.speciesById)
        }
    }

    private fun overlayForLanguage(
        source: CatalogLanguageOverlay,
        language: LanguageTag,
        speciesName: String,
        typeName: String,
    ) = CatalogLanguageOverlay(
        language = language,
        overlayVersion = source.overlayVersion,
        localizedCapabilities = source.localizedCapabilities,
        speciesNames = source.speciesNames.mapValues { CatalogField.available(speciesName) },
        speciesDescriptions = source.speciesDescriptions,
        moveNames = source.moveNames,
        moveDescriptions = source.moveDescriptions,
        abilityNames = source.abilityNames,
        abilityDescriptions = source.abilityDescriptions,
        typeNames = source.typeNames.mapValues { CatalogField.available(typeName) },
        natureNames = source.natureNames,
        itemNames = source.itemNames,
        areaNames = source.areaNames,
        localMapNames = source.localMapNames,
        worldRegionNames = source.worldRegionNames,
        worldLocationNames = source.worldLocationNames,
        encounterAreaNames = source.encounterAreaNames,
        poiTexts = source.poiTexts,
    )

    private fun localizedFixtureCatalog(
        base: ParsedCatalog,
        runtimeMetadata: CatalogRuntimeMetadata = base.runtimeMetadata,
        worldMaps: WorldMapCatalog = base.worldMaps,
        localMaps: LocalMapCatalog = base.localMaps,
    ): ParsedCatalog {
        val prior = requireNotNull(base.defaultLocalizedText())
        val itemIds = base.captureBallsById.keys + localMaps.pois.mapNotNull { it.item?.itemId }
        val areaIds = runtimeMetadata.areaBaseIds + runtimeMetadata.areaNamesByBaseId.keys
        val localMapKeys = localMaps.maps.mapTo(linkedSetOf(), LocalMap::key)
        val regionKeys = worldMaps.regions.mapTo(linkedSetOf(), WorldMapRegion::key)
        val locationKeys = worldMaps.regions.flatMapTo(linkedSetOf()) { region ->
            region.locations.map { WorldLocationKey(region.key, it.key) }
        }
        val poiKeys = localMaps.pois.mapTo(linkedSetOf(), LocalMapPoi::key)
        val itemNames = buildMap {
            putAll(prior.itemNames.filterKeys(itemIds::contains))
            localMaps.pois.forEach { poi ->
                val item = poi.item ?: return@forEach
                val itemId = item.itemId ?: return@forEach
                val displayName = item.displayName ?: return@forEach
                val existing = put(itemId, CatalogField.available(displayName))
                require(existing == null || existing.value == displayName) {
                    "fixture item names must not conflict"
                }
            }
        }
        val areaNames = buildMap {
            putAll(prior.areaNames.filterKeys(areaIds::contains))
            runtimeMetadata.areaNamesByBaseId.forEach { (id, name) -> put(id, CatalogField.available(name)) }
        }
        val localMapNames = localMaps.maps.mapNotNull { map ->
            map.displayName?.let { map.key to CatalogField.available(it) }
        }.toMap()
        val worldRegionNames = worldMaps.regions.mapNotNull { region ->
            region.displayName?.let { region.key to CatalogField.available(it) }
        }.toMap()
        val worldLocationNames = worldMaps.regions.flatMap { region ->
            region.locations.mapNotNull { location ->
                location.displayName?.let {
                    WorldLocationKey(region.key, location.key) to CatalogField.available(it)
                }
            }
        }.toMap()
        val poiTexts = localMaps.pois.mapNotNull { poi ->
            val displayName = poi.displayName?.let(CatalogField.Companion::available)
            val genderNames = poi.displayNamesByTrainerGender.mapValues { (_, text) -> CatalogField.available(text) }
            val itemName = poi.item?.takeIf { it.itemId == null }?.displayName
                ?.let(CatalogField.Companion::available)
            if (displayName == null && genderNames.isEmpty() && itemName == null) {
                null
            } else {
                poi.key to CatalogPoiText(displayName, genderNames, itemName)
            }
        }.toMap()
        val localizedMaps = mapOf(
            LocalizedTextCapability.SPECIES_NAMES to prior.speciesNames,
            LocalizedTextCapability.SPECIES_DESCRIPTIONS to prior.speciesDescriptions,
            LocalizedTextCapability.MOVE_NAMES to prior.moveNames,
            LocalizedTextCapability.MOVE_DESCRIPTIONS to prior.moveDescriptions,
            LocalizedTextCapability.ABILITY_NAMES to prior.abilityNames,
            LocalizedTextCapability.ABILITY_DESCRIPTIONS to prior.abilityDescriptions,
            LocalizedTextCapability.TYPE_NAMES to prior.typeNames,
            LocalizedTextCapability.NATURE_NAMES to prior.natureNames,
            LocalizedTextCapability.ITEM_NAMES to itemNames,
            LocalizedTextCapability.AREA_NAMES to areaNames,
            LocalizedTextCapability.LOCAL_MAP_NAMES to localMapNames,
            LocalizedTextCapability.WORLD_REGION_NAMES to worldRegionNames,
            LocalizedTextCapability.WORLD_LOCATION_NAMES to worldLocationNames,
            LocalizedTextCapability.ENCOUNTER_AREA_NAMES to prior.encounterAreaNames,
            LocalizedTextCapability.POI_TEXT to poiTexts,
        )
        val expected = mapOf(
            LocalizedTextCapability.SPECIES_NAMES to base.speciesById.size,
            LocalizedTextCapability.SPECIES_DESCRIPTIONS to base.speciesById.count { (id, record) ->
                id > 0 && record.dexNumber.status != CapabilityStatus.NOT_APPLICABLE
            },
            LocalizedTextCapability.MOVE_NAMES to base.movesById.size,
            LocalizedTextCapability.MOVE_DESCRIPTIONS to base.movesById.keys.count { it > 0 },
            LocalizedTextCapability.ABILITY_NAMES to base.abilitiesById.size,
            LocalizedTextCapability.ABILITY_DESCRIPTIONS to base.abilitiesById.keys.count { it > 0 },
            LocalizedTextCapability.TYPE_NAMES to base.typesById.size,
            LocalizedTextCapability.NATURE_NAMES to base.naturesById.size,
            LocalizedTextCapability.ITEM_NAMES to itemIds.size,
            LocalizedTextCapability.AREA_NAMES to areaIds.size,
            LocalizedTextCapability.LOCAL_MAP_NAMES to localMapKeys.size,
            LocalizedTextCapability.WORLD_REGION_NAMES to regionKeys.size,
            LocalizedTextCapability.WORLD_LOCATION_NAMES to locationKeys.size,
            LocalizedTextCapability.ENCOUNTER_AREA_NAMES to base.encounterAreas.size,
            LocalizedTextCapability.POI_TEXT to poiKeys.size,
        )
        val capabilities = LocalizedTextCapability.entries.associateWith { capability ->
            val expectedRecords = expected.getValue(capability)
            val coveredRecords = localizedMaps.getValue(capability).size
            when {
                expectedRecords == 0 -> LocalizedCapabilityState.notApplicable("empty fixture domain")
                coveredRecords == expectedRecords -> LocalizedCapabilityState.available(expectedRecords)
                coveredRecords > 0 -> LocalizedCapabilityState(
                    status = CapabilityStatus.PARTIAL,
                    confidence = 1.0,
                    coveredRecords = coveredRecords,
                    expectedRecords = expectedRecords,
                )
                else -> LocalizedCapabilityState.notFound("fixture text unavailable", expectedRecords)
            }
        }
        val overlay = CatalogLanguageOverlay(
            language = prior.language,
            overlayVersion = prior.overlayVersion,
            localizedCapabilities = capabilities,
            speciesNames = prior.speciesNames,
            speciesDescriptions = prior.speciesDescriptions,
            moveNames = prior.moveNames,
            moveDescriptions = prior.moveDescriptions,
            abilityNames = prior.abilityNames,
            abilityDescriptions = prior.abilityDescriptions,
            typeNames = prior.typeNames,
            natureNames = prior.natureNames,
            itemNames = itemNames,
            areaNames = areaNames,
            localMapNames = localMapNames,
            worldRegionNames = worldRegionNames,
            worldLocationNames = worldLocationNames,
            encounterAreaNames = prior.encounterAreaNames,
            poiTexts = poiTexts,
        )
        return base.copy(
            runtimeMetadata = runtimeMetadata.copy(
                areaBaseIds = areaIds,
                areaNamesByBaseId = emptyMap(),
            ),
            worldMaps = worldMaps.copy(
                regions = worldMaps.regions.map { region ->
                    region.copy(
                        displayName = null,
                        locations = region.locations.map { it.copy(displayName = null) },
                    )
                },
            ),
            localMaps = localMaps.copy(
                maps = localMaps.maps.map { it.copy(displayName = null) },
                pois = localMaps.pois.map { poi ->
                    poi.copy(
                        displayName = null,
                        displayNamesByTrainerGender = emptyMap(),
                        item = poi.item?.copy(displayName = null),
                    )
                },
            ),
            localization = CatalogLocalization(base.languageManifest, mapOf(prior.language to overlay)),
        )
    }

    private fun expectedSections(catalog: ParsedCatalog): Set<String> =
        CatalogSectionPlan.from(catalog.localization).sections

    private fun completeCatalog(hash: String): ParsedCatalog {
        val sprite = RgbaSprite(2, 2, intArrayOf(0x00000000, 0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt()))
        val avatar = RgbaSprite(64, 64, IntArray(64 * 64) { 0xff406080.toInt() })
        val overworld = RgbaSprite(16, 32, IntArray(16 * 32) { 0xff406080.toInt() })
        val badge = RgbaSprite(16, 16, IntArray(16 * 16) { 0xffc0a020.toInt() })
        val badgeKeys = (1..8).map { "trainer/badge/$it" }
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
                listOf(AbilityMechanic(
                    AbilityMechanicKind.MULTIPLIER,
                    "Fire power",
                    "1.5x",
                    3,
                    2,
                    listOf(AbilityMechanicCondition(
                        AbilityMechanicConditionKind.MOVE_SPLIT,
                        1,
                        "Special moves",
                    )),
                )),
            ),
        )
        val manifest = englishLanguageManifest()
        val localizedExpectedRecords = mapOf(
            LocalizedTextCapability.SPECIES_NAMES to 1,
            LocalizedTextCapability.SPECIES_DESCRIPTIONS to 1,
            LocalizedTextCapability.MOVE_NAMES to 1,
            LocalizedTextCapability.MOVE_DESCRIPTIONS to 1,
            LocalizedTextCapability.ABILITY_NAMES to 1,
            LocalizedTextCapability.ABILITY_DESCRIPTIONS to 1,
            LocalizedTextCapability.TYPE_NAMES to 1,
            LocalizedTextCapability.NATURE_NAMES to 1,
            LocalizedTextCapability.ITEM_NAMES to 1,
            LocalizedTextCapability.AREA_NAMES to 1,
            LocalizedTextCapability.ENCOUNTER_AREA_NAMES to 1,
        )
        val overlay = CatalogLanguageOverlay(
            language = LanguageTag.ENGLISH,
            overlayVersion = 1,
            localizedCapabilities = LocalizedTextCapability.entries.associateWith { capability ->
                val expected = localizedExpectedRecords[capability] ?: 0
                if (expected == 0) {
                    LocalizedCapabilityState.notApplicable("empty fixture domain")
                } else {
                    LocalizedCapabilityState.available(expected)
                }
            },
            speciesNames = mapOf(6 to species.name),
            speciesDescriptions = mapOf(6 to species.description),
            moveNames = mapOf(53 to move.name),
            moveDescriptions = mapOf(53 to move.effectText),
            abilityNames = mapOf(66 to ability.name),
            abilityDescriptions = mapOf(66 to ability.description),
            typeNames = mapOf(10 to CatalogField.available("Fire")),
            natureNames = mapOf(0 to CatalogField.available("Resolute")),
            itemNames = mapOf(4 to CatalogField.available("Poké Ball")),
            areaNames = mapOf(0x0010 to CatalogField.available("Route 101")),
            encounterAreaNames = mapOf(1 to CatalogField.available("Route 1")),
        )
        val localized = CatalogLocalization(manifest, mapOf(LanguageTag.ENGLISH to overlay))
        val localizedPlaceholder = CatalogField.notApplicable<String>("stored in language overlay")
        return ParsedCatalog(
            romSha256 = hash,
            romCrc32 = "8C7DBECA",
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(
                6 to species.copy(name = localizedPlaceholder, description = CatalogField.notFound("stored in language overlay")),
            ),
            movesById = mapOf(
                53 to move.copy(name = localizedPlaceholder, effectText = localizedPlaceholder),
            ),
            typesById = mapOf(
                10 to TypeRecord(
                    id = 10,
                    name = localizedPlaceholder,
                    presentation = CatalogField.available(typePresentation),
                    semanticRole = CatalogField.available(TypeSemanticRole.FIRE),
                ),
            ),
            abilitiesById = mapOf(
                66 to ability.copy(name = localizedPlaceholder, description = localizedPlaceholder),
            ),
            naturesById = mapOf(
                0 to NatureRecord(
                    id = 0,
                    name = null,
                    statModifiers = listOf(1, 0, 0, -1, 0),
                    positivePercent = 112,
                    negativePercent = 88,
                    flavorModifiers = listOf(1, -1, 0, 0, 0),
                ),
            ),
            typeChart = listOf(TypeMatchup(10, 12, 200)),
            encounterAreas = listOf(
                EncounterArea(
                    1,
                    localizedPlaceholder,
                    0,
                    listOf(EncounterSlot(6, 34, 36, 10)),
                    setOf(EncounterWindow.NIGHT),
                ),
            ),
            captureBallsById = mapOf(
                4 to CaptureBallRecord(4, localizedPlaceholder, CatalogField.available(sprite)),
            ),
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
            trainerAssets = TrainerAssetCatalog(
                avatarAssetKeys = mapOf(
                    0 to "trainer/avatar/male",
                    1 to "trainer/avatar/female",
                ),
                overworldAssetKeys = mapOf(
                    0 to "trainer/overworld/male",
                    1 to "trainer/overworld/female",
                ),
                badgeAssetKeys = badgeKeys,
                assets = buildMap {
                    put("trainer/avatar/male", avatar)
                    put("trainer/avatar/female", avatar)
                    put("trainer/overworld/male", overworld)
                    put("trainer/overworld/female", overworld)
                    badgeKeys.forEach { put(it, badge) }
                },
            ),
            theme = CatalogTheme(
                method = CatalogThemeMethod.MULTI_ASSET_QUANTIZATION,
                assetClasses = setOf(CatalogThemeAssetClass.TRAINER, CatalogThemeAssetClass.SPECIES),
                contrastCorrected = true,
                tokens = CatalogThemeTokens(
                    field = 0x244936,
                    fieldPattern = 0x315943,
                    header = 0x183A5C,
                    headerShadow = 0x0B1B2B,
                    menu = 0xE4D6A8,
                    menuShadow = 0x75694B,
                    panel = 0xFFF7DB,
                    border = 0x4D4032,
                    text = 0x1C201D,
                    textShadow = 0xFFFFFF,
                    accent = 0x9D302A,
                    accentText = 0xFFFFFF,
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
                    liveClockAddress = 0x030039E8,
                    liveClockSchedule = CatalogGameClockSchedule(6, 21),
                    multiUsePlayerCursorAddress = 0x03002378,
                    multiUsePlayerCursorEvidence = RuntimeMemoryEvidence.SOURCE_PROVEN_UNTESTED,
                    playerPartyCountAddress = 0x02001001,
                    playerPartyAddress = 0x02001004,
                    battleMonsAddress = 0x0200143C,
                    battleTypeFlagsAddress = 0x020003A0,
                    trainerBattleMask = 1 shl 3,
                    nonWildBattleMask = 0x8FFF8B72.toInt(),
                    saveBlock1PointerAddress = 0x030036F0L,
                    saveBlock2PointerAddress = 0x030036F4L,
                    pokemonStoragePointerAddress = 0x030036F8L,
                    pokemonStorageBoxCount = 14,
                    pokemonStorageBoxCapacity = 30,
                    pokemonStorageRecordSize = 80,
                    pokemonStorageRecordsOffset = 4,
                    saveRuntimeAbi = CatalogGen3SaveRuntimeAbi(
                        saveBlock1Size = 0x3D88,
                        saveBlock2Size = 0x0F2C,
                        textEncoding = CatalogGen3TextEncoding.ENGLISH,
                        trainer = CatalogGen3TrainerCardAbi(
                            playerNameOffset = 0,
                            playerNameLength = 8,
                            genderOffset = 8,
                            trainerIdOffset = 0x0A,
                            playTimeHoursOffset = 0x0E,
                            playTimeMinutesOffset = 0x10,
                            encryptionKeyOffset = 0xAC,
                            moneyOffset = 0x490,
                            maximumMoney = 999_999,
                            badgeFlags = (0 until 8).map { CatalogGen3BitFlag(0x1270, 1 shl it) },
                        ),
                        bag = CatalogGen3BagAbi(
                            listOf(CatalogGen3BagPocketAbi(CatalogGen3BagPocket.ITEMS, 0x560, 30)),
                        ),
                        eventFlags = CatalogGen3EventFlagAbi(0x1270, 0x12C),
                    ),
                    partyAbi = CatalogGen3PartyAbi(0x02001001, 0x02001004, 6, 100),
                    battleUiAbi = CatalogGen3BattleUiAbi(
                        0x02001000,
                        0x02001002,
                        0x0200143C + 0x438,
                        0x0200143C + 0x43C,
                    ),
                ),
                areaBaseIds = setOf(0x0010),
                areaNamesByBaseId = emptyMap(),
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
            localization = localized,
        )
    }

    private fun englishLanguageManifest(): RomLanguageManifest = RomLanguageManifest(
        defaultLanguage = LanguageTag.ENGLISH,
        projections = listOf(
            RomLanguageProjection(
                language = LanguageTag.ENGLISH,
                codecId = PokemonTextCodec.gbaEnglish.id,
                codecVersion = PokemonTextCodec.gbaEnglish.version,
                localizedTables = LocalizedTableLayout(),
                evidence = listOf(LanguageEvidence(LanguageEvidenceKind.TABLE_RELATIONSHIP, "fixture", 100)),
                status = LanguageResolutionStatus.RESOLVED,
            ),
        ),
        status = LanguageResolutionStatus.RESOLVED,
    )
}
