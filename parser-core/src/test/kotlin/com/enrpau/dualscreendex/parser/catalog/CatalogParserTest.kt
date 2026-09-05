package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameCodec
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableOutcome
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilitySemanticDomain
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionCodec
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableOutcome
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.dataset.natures.NatureCatalog
import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import com.enrpau.dualscreendex.parser.dataset.natures.NatureResolution
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.resolvedLanguageManifest
import com.enrpau.dualscreendex.parser.language.textUnavailableLanguageManifests
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.sprite.SpriteMaterializer
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.parse.LocalMapResolution
import com.enrpau.dualscreendex.parser.parse.WorldMapResolution
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogParserTest {
    @Test
    fun moveDescriptionResolverNullIsAuthoritative() {
        val (rom, analysis, layout) = moveDescriptionCallerFixture()
        var calls = 0
        val catalog = CatalogMaterializer.materialize(rom, analysis, layout, resolveMoveDescriptions = {
            calls++
            null
        })
        assertEquals(1, calls)
        assertNull(catalog.defaultTextProjection().moveDescription(1))
    }

    @Test
    fun moveDescriptionResolverConflictIsNotRetriedWithoutItsReferences() {
        val (rom, analysis, layout) = moveDescriptionCallerFixture()
        val references = GbaReferenceIndex.countsOnlyForTesting(mapOf(0x100 to 1, 0x200 to 1))
        assertNull(MoveDescriptionMaterializer.materialize(rom, layout, references))
        var calls = 0
        val catalog = CatalogMaterializer.materialize(rom, analysis, layout, resolveMoveDescriptions = {
            calls++
            MoveDescriptionMaterializer.materialize(rom, it, references)
        })
        assertEquals(1, calls)
        assertNull(catalog.defaultTextProjection().moveDescription(1))
    }

    @Test
    fun moveDescriptionResolverBudgetIsNotRetriedWithAFreshBudget() {
        val (rom, analysis, layout) = moveDescriptionCallerFixture()
        val references = GbaReferenceIndex.countsOnlyForTesting(mapOf(0x100 to 1))
        val limits = ResolutionLimits(maxProbeWorkPerDataset = 1)
        assertNull(MoveDescriptionMaterializer.materialize(rom, layout, references, limits = limits))
        var calls = 0
        val catalog = CatalogMaterializer.materialize(rom, analysis, layout, resolveMoveDescriptions = {
            calls++
            MoveDescriptionMaterializer.materialize(rom, it, references, limits = limits)
        })
        assertEquals(1, calls)
        assertNull(catalog.defaultTextProjection().moveDescription(1))
    }

    @Test
    fun moveDescriptionResolverAbsentRetainsStandaloneMaterialization() {
        val (rom, analysis, layout) = moveDescriptionCallerFixture()
        val catalog = CatalogMaterializer.materialize(rom, analysis, layout)
        assertEquals("A small flame attack.", catalog.defaultTextProjection().moveDescription(1))
        assertEquals(3, catalog.defaultLocalizedText()?.moveDescriptions?.size)
    }

    @Test
    fun moveDescriptionResolverCancellationPropagatesWithoutRetry() {
        val (rom, analysis, layout) = moveDescriptionCallerFixture()
        var calls = 0
        assertThrows(ParserCancellationException::class.java) {
            CatalogMaterializer.materialize(rom, analysis, layout, resolveMoveDescriptions = {
                calls++
                throw ParserCancellationException()
            })
        }
        assertEquals(1, calls)
    }

    private fun moveDescriptionCallerFixture(): Triple<RomImage, ParseResult, ResolvedRomLayout> {
        val bytes = ByteArray(0x1000)
        repeat(4) { id ->
            encodeGbaText(bytes, 0x20 + id * 13, "MOVE")
            bytes[0x80 + id * 12 + 1] = 40
            bytes[0x80 + id * 12 + 3] = 100
            bytes[0x80 + id * 12 + 4] = 20
        }
        listOf(0x100, 0x200).forEachIndexed { table, root ->
            repeat(3) { id ->
                val text = 0x400 + table * 0x200 + id * 0x40
                putGbaPointer(bytes, root + id * 4, text)
                encodeGbaText(bytes, text, "A small flame attack.")
            }
        }
        val rom = RomImage(bytes)
        val analysis = ParseResult(RomHeader(Platform.GBA, "MOVE TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList())
        val layout = ResolvedRomLayout(EngineFamily.EMERALD, 3, Platform.GBA, 0, 4,
            ProfileTables(moveNames = TableLayout(0x20, 4, 13), moveData = TableLayout(0x80, 4, 12)),
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish))
        return Triple(rom, analysis, layout)
    }

    @Test
    fun numericOnlyNatureMechanicsAreReportedAsPartialWithoutClaimingDecodedText() {
        val resolution = NatureResolution.Resolved(
            NatureCatalog(
                records = listOf(
                    NatureRecord(
                        id = 0,
                        name = null,
                        statModifiers = listOf(0, 0, 0, 0, 0),
                        positivePercent = 110,
                        negativePercent = 90,
                        flavorModifiers = null,
                    ),
                ),
                nameTableOffset = null,
                statTableOffset = 0x100,
                flavorTableOffset = null,
            ),
        )

        val evidence = natureCapabilityEvidence(resolution, generation = 3)

        assertEquals(CapabilityStatus.PARTIAL, evidence.status)
        assertTrue(evidence.compatible)
        assertEquals(1, evidence.validRecords)
        assertEquals(1, evidence.totalRecords)
        assertTrue(evidence.reasons.any { it.contains("stat effects") })
        assertTrue(evidence.reasons.any { it.contains("names are unavailable") })
        assertTrue(evidence.reasons.any { it.contains("flavor affinities are unavailable") })
        assertFalse(evidence.reasons.any { it.contains("decoded ROM-native Nature names") })
    }

    @Test
    fun numericAbilityMechanicsSurviveWhenRomTextIsUnavailable() {
        val bytes = ByteArray(0x200) { 0xFF.toByte() }
        bytes[0] = 0xAE.toByte()
        bytes[1] = 0xFF.toByte()
        encodeGbaText(bytes, 13, "OVERGROW")
        val rom = RomImage(bytes)
        val typedNames = (AbilityNameCodec().decode(
            RomAnalysisSession(rom, RomHeader(Platform.GBA, "ABILITY TEST")),
            AbilityNameTableLayout(0, 2, 13),
            AbilitySemanticDomain(setOf(1)),
        ) as AbilityNameTableOutcome.Decoded).resolved
        val expectedMechanic = AbilityMechanic(
            AbilityMechanicKind.MULTIPLIER,
            "Power",
            "Power ×1.5",
            3,
            2,
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "ABILITY TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )

        textUnavailableLanguageManifests.forEach { manifest ->
            val layout = ResolvedRomLayout(
                family = EngineFamily.EMERALD,
                generation = 3,
                platform = Platform.GBA,
                speciesCount = 0,
                moveCount = 0,
                tables = ProfileTables(abilities = TableLayout(0, 2, 13)),
                resolvedDatasets = ResolvedDatasetLayouts(abilityNames = typedNames),
                languageManifest = manifest,
            )

            val catalog = CatalogMaterializer.materialize(
                rom = rom,
                analysis = analysis,
                layout = layout,
                resolveAbilityMechanics = { _, abilities, _, _ ->
                    assertEquals(setOf(1), abilities.keys)
                    assertEquals(CapabilityStatus.NOT_FOUND, abilities.getValue(1).name.status)
                    AbilityMechanicsResult(0x100, 1.0, mapOf(1 to listOf(expectedMechanic)))
                },
            )

            assertEquals(CapabilityStatus.NOT_APPLICABLE, catalog.abilitiesById.getValue(1).name.status)
            assertEquals(listOf(expectedMechanic), catalog.abilitiesById.getValue(1).mechanics.value)
            assertEquals(
                CapabilityStatus.AVAILABLE,
                catalog.capabilities.getValue(RomCapability.ABILITY_MECHANICS).status,
            )
        }
    }

    @Test
    fun optionalResolverDoesNotConvertParserCancellationIntoUnavailableEvidence() {
        val rom = RomImage(ByteArray(0x200))
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )

        assertThrows(ParserCancellationException::class.java) {
            CatalogMaterializer.materialize(
                rom = rom,
                analysis = analysis,
                layout = layout,
                resolveWorldMap = { _, _ -> throw ParserCancellationException() },
            )
        }
    }

    @Test
    fun speciesMediaPropagatesCancellationIntoDetachedSpriteScanning() {
        val rom = RomImage(ByteArray(0x8000))
        val layout = ResolvedRomLayout(
            family = EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 1,
            moveCount = 0,
            tables = ProfileTables(sprites = TableLayout(0, 1, 28)),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GB, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.RED_BLUE, null, 20, emptyList(), emptyList(),
        )
        val cancellation = CancelAfterChecks(successfulChecks = 8)
        var mediaPublished = false

        assertThrows(ParserCancellationException::class.java) {
            CatalogMaterializer.materialize(
                rom = rom,
                analysis = analysis,
                layout = layout,
                cancellation = cancellation,
                onProgress = { progress ->
                    if (progress.phase == CatalogMaterializationPhase.SPECIES_MEDIA) {
                        mediaPublished = true
                    }
                },
            )
        }

        assertFalse(mediaPublished)
        assertEquals(9, cancellation.checks)
    }

    @Test
    fun optionalWorldMapResolverFailureKeepsTheBaseCatalogUsable() {
        val rom = RomImage(ByteArray(0x200))
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(
            rom = rom,
            analysis = analysis,
            layout = layout,
            resolveWorldMap = { _, _ -> error("deliberate optional resolver failure") },
        )

        assertTrue(catalog.worldMaps.regions.isEmpty())
        assertTrue(catalog.worldMaps.assets.isEmpty())
        val evidence = catalog.capabilities.getValue(RomCapability.WORLD_MAP)
        assertEquals(CapabilityStatus.NOT_FOUND, evidence.status)
        assertTrue(evidence.reasons.any { it.contains("world-map stage: resolver-exception") })
    }

    @Test
    fun optionalLocalMapResolverFailureKeepsTheAtlasUsable() {
        val rom = RomImage(ByteArray(0x200))
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )
        val region = WorldMapRegion(
            key = "world/test",
            displayName = "Test",
            pixelWidth = 8,
            pixelHeight = 8,
            gridWidth = 1,
            gridHeight = 1,
            imageAssetKey = "world/test/map",
            locations = listOf(
                WorldMapLocation("test", "Test", setOf(1), listOf(WorldMapCell(0, 0, 1, 1))),
            ),
        )

        val catalog = CatalogMaterializer.materialize(
            rom = rom,
            analysis = analysis,
            layout = layout,
            resolveWorldMap = { _, _ ->
                WorldMapResolution.Resolved(
                    WorldMapCatalog(
                        listOf(region),
                        mapOf(region.imageAssetKey to RgbaSprite(8, 8, IntArray(64))),
                    ),
                    listOf("test atlas"),
                )
            },
            resolveLocalMaps = { _, _ -> error("deliberate optional resolver failure") },
        )

        assertEquals(1, catalog.worldMaps.regions.size)
        assertTrue(catalog.localMaps.maps.isEmpty())
        assertTrue(catalog.localMaps.assets.isEmpty())
        assertTrue(catalog.localMaps.indexedAssets.isEmpty())
        assertEquals(null, catalog.runtimeMetadata.gen2TimeOfDayWramOffset)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.WORLD_MAP).status)
        val evidence = catalog.capabilities.getValue(RomCapability.LOCAL_MAP)
        assertEquals(CapabilityStatus.NOT_FOUND, evidence.status)
        assertTrue(evidence.reasons.any { it.contains("local-map stage: resolver-exception") })
    }

    @Test
    fun partialLocalMapEvidencePublishesExactCoverageCounts() {
        val rom = RomImage(ByteArray(0x200))
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )
        val assetKey = "local/test/map"
        val localMap = LocalMap(
            key = "test-map",
            displayName = "Test Map",
            baseAreaId = 1,
            pixelWidth = 16,
            pixelHeight = 16,
            gridWidth = 1,
            gridHeight = 1,
            imageAssetKey = assetKey,
        )
        val png = PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

        val catalog = CatalogMaterializer.materialize(
            rom = rom,
            analysis = analysis,
            layout = layout,
            resolveLocalMaps = { _, _ ->
                LocalMapResolution.Resolved(
                    catalog = LocalMapCatalog(
                        maps = listOf(localMap),
                        assets = mapOf(assetKey to png),
                    ),
                    reasons = listOf("one map retained"),
                    skippedMaps = 1,
                )
            },
        )

        val evidence = catalog.capabilities.getValue(RomCapability.LOCAL_MAP)
        assertEquals(CapabilityStatus.PARTIAL, evidence.status)
        assertEquals(1, evidence.coveredRecords)
        assertEquals(2, evidence.expectedRecords)
        assertEquals(1, evidence.incompleteRecords)
        assertEquals(0.5, evidence.confidence, 0.0)
    }

    @Test
    fun partialLocalMapSubsystemFailureDoesNotInventMissingMaps() {
        val rom = RomImage(ByteArray(0x200))
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )
        val assetKey = "local/test/map"
        val localMap = LocalMap(
            key = "test-map",
            displayName = "Test Map",
            baseAreaId = 1,
            pixelWidth = 16,
            pixelHeight = 16,
            gridWidth = 1,
            gridHeight = 1,
            imageAssetKey = assetKey,
        )
        val png = PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

        val catalog = CatalogMaterializer.materialize(
            rom = rom,
            analysis = analysis,
            layout = layout,
            resolveLocalMaps = { _, _ ->
                LocalMapResolution.Resolved(
                    catalog = LocalMapCatalog(
                        maps = listOf(localMap),
                        assets = mapOf(assetKey to png),
                    ),
                    reasons = listOf("map 0x0001 POIs: malformed optional records"),
                    partialSubsystemFailures = 1,
                )
            },
        )

        val evidence = catalog.capabilities.getValue(RomCapability.LOCAL_MAP)
        assertEquals(CapabilityStatus.PARTIAL, evidence.status)
        assertEquals(1, evidence.coveredRecords)
        assertEquals(1, evidence.expectedRecords)
        assertEquals(0, evidence.incompleteRecords)
        assertEquals(1.0, evidence.confidence, 0.0)
    }

    @Test
    fun themeMaterializerRunsAfterNormalizedWorldMapAssetsResolve() {
        val rom = RomImage(ByteArray(0x200))
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )
        val raster = RgbaSprite(2, 2, intArrayOf(
            0xff16324f.toInt(), 0xfff6c453.toInt(), 0xff2f7d50.toInt(), 0xfff5ead7.toInt(),
        ))
        val expected = RomThemeMaterializer.materialize(
            mapOf(
                CatalogThemeAssetClass.WORLD_MAP to listOf(raster),
                CatalogThemeAssetClass.SPECIES to listOf(raster.copy(argb = raster.argb.reversedArray())),
            ),
        )

        val catalog = CatalogMaterializer.materialize(
            rom = rom,
            analysis = analysis,
            layout = layout,
            resolveWorldMap = { _, _ ->
                WorldMapResolution.Resolved(
                    WorldMapCatalog(
                        regions = listOf(
                            WorldMapRegion(
                                "world/test", "Test", 2, 2, 1, 1, "world/test/map",
                                listOf(WorldMapLocation("test", "Test", setOf(1), listOf(WorldMapCell(0, 0, 1, 1)))),
                            ),
                        ),
                        assets = mapOf("world/test/map" to raster),
                    ),
                    listOf("test atlas"),
                )
            },
            materializeTheme = { assets, _ ->
                assertEquals(listOf(raster), assets.getValue(CatalogThemeAssetClass.WORLD_MAP))
                expected
            },
        )

        assertEquals(expected, catalog.theme)
        assertEquals(1, catalog.worldMaps.regions.size)
    }

    @Test
    fun optionalThemeMaterializerFailureKeepsTheCompletedCatalogUsable() {
        val rom = RomImage(ByteArray(0x200))
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(
            rom = rom,
            analysis = analysis,
            layout = layout,
            materializeTheme = { _, _ -> error("deliberate optional theme failure") },
        )

        assertEquals(CatalogTheme.neutral(), catalog.theme)
        assertEquals(rom.sha256, catalog.romSha256)
    }

    @Test
    fun catalogThemeEvidenceIncludesEveryNormalizedAssetClassAndBoundsSpecies() {
        fun pixel(width: Int, height: Int, rgb: Int) =
            RgbaSprite(width, height, IntArray(width * height) { 0xff000000.toInt() or rgb })
        val species = (1..20).associateWith { id ->
            SpeciesRecord(
                id = id,
                dexNumber = CatalogField.available(id),
                name = CatalogField.available("Species $id"),
                typeIds = CatalogField.available(listOf(1)),
                baseStats = CatalogField.available(BaseStats(1, 1, 1, 1, 1, 1)),
                sprite = CatalogField.available(pixel(1, 1, id)),
            )
        }
        val badgeKeys = (1..8).map { "trainer/badge/$it" }
        val trainer = TrainerAssetCatalog(
            avatarAssetKeys = mapOf(0 to "trainer/male", 1 to "trainer/female"),
            badgeAssetKeys = badgeKeys,
            assets = buildMap {
                put("trainer/male", pixel(64, 64, 0x123456))
                put("trainer/female", pixel(64, 64, 0x654321))
                badgeKeys.forEachIndexed { index, key -> put(key, pixel(16, 16, 0x220000 + index)) }
            },
        )
        val worldRaster = pixel(2, 2, 0xabcdef)
        val world = WorldMapCatalog(
            regions = listOf(WorldMapRegion(
                "world", "World", 2, 2, 1, 1, "world/map",
                listOf(WorldMapLocation("location", "Location", setOf(1), listOf(WorldMapCell(0, 0, 1, 1)))),
            )),
            assets = mapOf("world/map" to worldRaster),
        )
        val localRaster = pixel(16, 16, 0xfedcba)
        val local = LocalMapCatalog(
            maps = listOf(LocalMap("local/1", "Local", 1, 16, 16, 1, 1, "local/1/map")),
            assets = mapOf("local/1/map" to PngMapAsset(PngEncoder.encode(localRaster))),
        )

        val evidence = CatalogMaterializer.catalogThemeAssets(species, trainer, world, local)

        assertEquals(
            setOf(
                CatalogThemeAssetClass.SPECIES,
                CatalogThemeAssetClass.TRAINER,
                CatalogThemeAssetClass.WORLD_MAP,
                CatalogThemeAssetClass.LOCAL_MAP,
            ),
            evidence.keys,
        )
        assertEquals(16, evidence.getValue(CatalogThemeAssetClass.SPECIES).size)
        assertEquals((1..16).toList(), evidence.getValue(CatalogThemeAssetClass.SPECIES).map { it.argb.single() and 0xffffff })
        assertEquals(worldRaster, evidence.getValue(CatalogThemeAssetClass.WORLD_MAP).single())
        assertEquals(localRaster, evidence.getValue(CatalogThemeAssetClass.LOCAL_MAP).single())
    }

    @Test
    fun reportsStructurallyResolvedPartialAbilityDescriptionsForManualReview() {
        val count = 21
        val bytes = ByteArray(0x8000) { 0xFF.toByte() }
        val namesOffset = 0x400
        val descriptionsOffset = 0x1000
        bytes.fill(0, namesOffset, namesOffset + 13)
        bytes[namesOffset] = 0xFF.toByte()
        repeat(count - 1) { index -> encodeGbaText(bytes, namesOffset + (index + 1) * 13, "ABILITY") }
        putGbaPointer(bytes, descriptionsOffset, 0x3000)
        encodeGbaText(bytes, 0x3000, "NO SPECIAL ABILITY")
        repeat(15) { index ->
            val id = index + 1
            val textOffset = 0x3100 + index * 0x30
            putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
            encodeGbaText(bytes, textOffset, "ABILITY EFFECT DESCRIPTION")
        }
        listOf(0x2200, 0x2300, descriptionsOffset, 0x2400, 0x2500, 0x2600, 0x2700)
            .forEachIndexed { index, root -> putGbaPointer(bytes, 0x1BC + index * 4, root) }
        val rom = RomImage(bytes)
        val typedNames = (AbilityNameCodec().decode(
            RomAnalysisSession(rom, RomHeader(Platform.GBA, "TEST", "TEST")),
            AbilityNameTableLayout(namesOffset, count, 13),
            AbilitySemanticDomain((1 until count).toSet()),
        ) as AbilityNameTableOutcome.Decoded).resolved
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0,
            ProfileTables(abilities = TableLayout(namesOffset, count, 13)),
            resolvedDatasets = ResolvedDatasetLayouts(abilityNames = typedNames),
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )

        val evidence = CatalogMaterializer.materialize(rom, analysis, layout)
            .defaultLocalizedText()!!
            .localizedCapabilities.getValue(LocalizedTextCapability.ABILITY_DESCRIPTIONS)

        assertEquals(CapabilityStatus.PARTIAL, evidence.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, evidence.reviewStatus)
        assertEquals(15, evidence.coveredRecords)
        assertEquals(20, evidence.expectedRecords)
    }

    @Test
    fun reportsClassicEmptyFirstProbeBudgetOverflowAsAmbiguousManualReview() {
        val bytes = ByteArray(0x50000)
        repeat(257) { index ->
            val root = 0x10000 + index * 0x100
            putGbaPointer(bytes, 0x1000 + index * 4, root)
            bytes[root] = 1
            bytes[root + 1] = 1
            repeat(2) { headerIndex ->
                val header = root + (headerIndex + 1) * 24
                bytes[header] = 1
                bytes[header + 1] = (headerIndex + 2).toByte()
            }
            bytes[root + 72] = 0xFF.toByte()
            bytes[root + 73] = 0xFF.toByte()
        }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 100, 0, ProfileTables(),
        )
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )

        val evidence = CatalogMaterializer.materialize(rom, analysis, layout)
            .capabilities.getValue(RomCapability.AREA_ENCOUNTERS)

        assertEquals(CapabilityStatus.AMBIGUOUS, evidence.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, evidence.reviewStatus)
        assertTrue(evidence.reasons.single().contains("empty-first Classic24 candidate budget exceeded (256)"))
    }

    @Test
    fun infersOnlyMissingSpritesFromAnExactNameAndDexAlias() {
        val bytes = ByteArray(0x2000)
        val stride = 128
        val names = listOf("NONE", "ALIAS", "ALIAS", "ALIAS", "OTHER", "OWN", "AMBIG", "AMBIG", "AMBIG")
        val dexNumbers = listOf(0, 25, 25, 26, 25, 25, 30, 30, 30)
        names.indices.forEach { id ->
            val record = id * stride
            encodeGbaText(bytes, record + 44, names[id])
            putU16(bytes, record + 60, dexNumbers[id])
            if (id > 0) {
                repeat(6) { stat -> bytes[record + stat] = (40 + stat).toByte() }
                bytes[record + 6] = 12
                bytes[record + 7] = 3
            }
        }
        val sprite = Base64.getDecoder().decode("ASAIAAAAKAABAAAAAAH+BwEAAAA=")
        sprite.copyInto(bytes, 0x1000)
        sprite.copyInto(bytes, 0x1100)
        putGbaPointer(bytes, stride + 88, 0x1000)
        putGbaPointer(bytes, stride + 96, 0x1200)
        putGbaPointer(bytes, stride * 5 + 88, 0x1100)
        putGbaPointer(bytes, stride * 5 + 96, 0x1240)
        putGbaPointer(bytes, stride * 6 + 88, 0x1000)
        putGbaPointer(bytes, stride * 6 + 96, 0x1200)
        putGbaPointer(bytes, stride * 7 + 88, 0x1100)
        putGbaPointer(bytes, stride * 7 + 96, 0x1240)
        bytes[0x1202] = 0x1F
        bytes[0x1242] = 0xE0.toByte()
        bytes[0x1243] = 0x03

        val metadata = PokeemeraldExpansionMetadata(
            0x204, 1, 15, 3, stride, 44, 13, 31, 60, 62, 64, 76, 88, 96,
            24, 21, 100, 104, 108, 112, 64, 28, 20, 20,
        )
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, names.size, 0,
            ProfileTables(
                speciesNames = TableLayout(44, names.size, 13, stride = stride),
                baseStats = TableLayout(0, names.size, stride, stride = stride),
                sprites = TableLayout(88, names.size, 4, stride = stride),
            ),
            pokeemeraldExpansion = metadata,
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
        )
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )
        assertEquals(setOf(1, 5, 6, 7), SpriteMaterializer.pokemon(rom, layout).keys)

        val species = CatalogMaterializer.materialize(rom, analysis, layout).speciesById

        assertEquals(CapabilityStatus.AVAILABLE, species.getValue(2).sprite.status)
        assertEquals(species.getValue(1).sprite.value, species.getValue(2).sprite.value)
        assertTrue(species.getValue(2).sprite.reasons.single().contains("inferred"))
        assertEquals(CapabilityStatus.NOT_FOUND, species.getValue(3).sprite.status)
        assertEquals(CapabilityStatus.NOT_FOUND, species.getValue(4).sprite.status)
        assertEquals(CapabilityStatus.AVAILABLE, species.getValue(5).sprite.status)
        assertTrue(species.getValue(1).sprite.value != species.getValue(5).sprite.value)
        assertEquals(CapabilityStatus.NOT_FOUND, species.getValue(8).sprite.status)
    }

    @Test
    fun materializerJoinsExpansionDescriptionsByInternalSpeciesId() {
        val bytes = ByteArray(1024)
        val stride = 180
        val first = stride
        repeat(6) { bytes[first + it] = (40 + it).toByte() }
        bytes[first + 6] = 12
        bytes[first + 7] = 3
        encodeGbaText(bytes, first + 31, "MOUSE")
        encodeGbaText(bytes, first + 44, "PIKA")
        putU16(bytes, first + 60, 25)
        putU16(bytes, first + 62, 4)
        putU16(bytes, first + 64, 60)
        putGbaPointer(bytes, first + 76, 500)
        encodeGbaText(bytes, 500, "ELECTRIC MOUSE")
        val metadata = PokeemeraldExpansionMetadata(
            0x204, 1, 15, 3, stride, 44, 13, 31, 60, 62, 64, 76, 88, 96,
            24, 21, 148, 152, 156, 160, 64, 28, 20, 20,
        )
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 2,
            moveCount = 0,
            tables = ProfileTables(
                speciesNames = TableLayout(44, 2, 13, stride = stride),
                baseStats = TableLayout(0, 2, stride, stride = stride),
                descriptions = TableLayout(0, 2, stride, stride = stride, pointerOffsets = listOf(76)),
            ),
            pokeemeraldExpansion = metadata,
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
        )
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(rom, analysis, layout)
        val species = catalog.speciesById.getValue(1)

        assertEquals(25, species.dexNumber.value)
        assertEquals("ELECTRIC MOUSE", catalog.defaultLocalizedText()!!.speciesDescriptions.getValue(1).value)
    }

    @Test
    fun expansionDescriptionFailureIsIsolatedAndReportedAsPartial() {
        val bytes = ByteArray(2048)
        val stride = 180
        repeat(2) { index ->
            val id = index + 1
            val row = id * stride
            repeat(6) { stat -> bytes[row + stat] = (40 + stat).toByte() }
            bytes[row + 6] = 12
            bytes[row + 7] = 3
            encodeGbaText(bytes, row + 31, "MOUSE")
            encodeGbaText(bytes, row + 44, if (id == 1) "ONE" else "TWO")
            putU16(bytes, row + 60, id)
            putU16(bytes, row + 62, 4)
            putU16(bytes, row + 64, 60)
        }
        putGbaPointer(bytes, stride + 76, 1000)
        encodeGbaText(bytes, 1000, "VALID DESCRIPTION")
        putGbaPointer(bytes, stride * 2 + 76, bytes.lastIndex)
        bytes[bytes.lastIndex] = 0
        val metadata = PokeemeraldExpansionMetadata(
            0x204, 1, 15, 3, stride, 44, 13, 31, 60, 62, 64, 76, 88, 96,
            24, 21, 148, 152, 156, 160, 64, 28, 20, 20,
        )
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 3,
            moveCount = 0,
            tables = ProfileTables(
                speciesNames = TableLayout(44, 3, 13, stride = stride),
                baseStats = TableLayout(0, 3, stride, stride = stride),
                descriptions = TableLayout(
                    0,
                    3,
                    stride,
                    stride = stride,
                    pointerOffsets = listOf(76),
                ),
            ),
            pokeemeraldExpansion = metadata,
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
        )
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"),
            rom.sha256,
            rom.crc32,
            rom.size,
            SelectionStatus.SELECTED,
            EngineFamily.EMERALD,
            null,
            20,
            emptyList(),
            emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(rom, analysis, layout)

        val localized = catalog.defaultLocalizedText()!!
        assertEquals(
            "VALID DESCRIPTION",
            localized.speciesDescriptions.getValue(1).value,
        )
        assertNull(localized.speciesDescriptions[2])
        val evidence = localized.localizedCapabilities.getValue(
            LocalizedTextCapability.SPECIES_DESCRIPTIONS,
        )
        assertEquals(CapabilityStatus.PARTIAL, evidence.status)
        assertEquals(1, evidence.coveredRecords)
        assertEquals(2, evidence.expectedRecords)
        assertEquals(1, evidence.incompleteRecords)
    }

    @Test
    fun allMalformedMachineRowsDoNotHideBehindValidEggMoves() {
        val bytes = ByteArray(2048)
        val stride = 180
        repeat(2) { index ->
            val id = index + 1
            val row = id * stride
            repeat(6) { stat -> bytes[row + stat] = (40 + stat).toByte() }
            bytes[row + 6] = 12
            bytes[row + 7] = 3
            encodeGbaText(bytes, row + 44, if (id == 1) "ONE" else "TWO")
            putU16(bytes, row + 60, id)
            putU16(bytes, row + 62, 4)
            putU16(bytes, row + 64, 60)
            putGbaPointer(bytes, row + 152, 0x7FE)
            putGbaPointer(bytes, row + 156, 0x700)
        }
        putU16(bytes, 0x700, 44)
        putU16(bytes, 0x702, 0xFFFF)
        putU16(bytes, 0x7FE, 33)
        val metadata = PokeemeraldExpansionMetadata(
            0x204, 1, 15, 3, stride, 44, 13, 31, 60, 62, 64, 76, 88, 96,
            24, 21, 148, 152, 156, 160, 64, 28, 20, 20,
        )
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 3,
            moveCount = 100,
            tables = ProfileTables(
                speciesNames = TableLayout(44, 3, 13, stride = stride),
                baseStats = TableLayout(0, 3, stride, stride = stride),
            ),
            pokeemeraldExpansion = metadata,
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
        )
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"),
            rom.sha256,
            rom.crc32,
            rom.size,
            SelectionStatus.SELECTED,
            EngineFamily.EMERALD,
            null,
            20,
            emptyList(),
            emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(rom, analysis, layout)

        assertEquals(
            CapabilityStatus.NOT_FOUND,
            catalog.speciesById.getValue(1).moveAcquisitions.status,
        )
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            catalog.speciesById.getValue(2).moveAcquisitions.status,
        )
    }

    @Test
    fun materializerJoinsDexDescriptionToRomNativeSpeciesId() {
        val bytes = ByteArray(512) { 0xFF.toByte() }
        encodeGbaText(bytes, 0, "NONE")
        encodeGbaText(bytes, 11, "BULBA")
        val stats = 64
        repeat(6) { bytes[stats + 28 + it] = (40 + it).toByte() }
        bytes[stats + 34] = 12
        bytes[stats + 35] = 3
        val descriptions = 128
        encodeGbaText(bytes, descriptions + 32, "SEED")
        putU16(bytes, descriptions + 32 + 12, 7)
        putU16(bytes, descriptions + 32 + 14, 69)
        putGbaPointer(bytes, descriptions + 32 + 16, 400)
        encodeGbaText(bytes, 400, "A SEED")
        putIdentitySpeciesIndexEvidence(bytes, 480, speciesCount = 2)
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 2,
            moveCount = 0,
            tables = ProfileTables(
                speciesNames = TableLayout(0, 2, 11),
                baseStats = TableLayout(stats, 2, 28),
                descriptions = TableLayout(descriptions, 2, 32, pointerOffsets = listOf(16)),
            ),
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
        ).withTypedDescriptions(bytes)
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            header = RomHeader(Platform.GBA, "TEST", "TEST"),
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            size = rom.size,
            status = SelectionStatus.SELECTED,
            selectedFamily = EngineFamily.EMERALD,
            selectedProfile = null,
            runnerUpMargin = 20,
            probes = emptyList(),
            capabilities = emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(rom, analysis, layout)

        assertEquals("BULBA", catalog.defaultLocalizedText()!!.speciesNames.getValue(1).value)
        assertEquals("A SEED", catalog.defaultLocalizedText()!!.speciesDescriptions.getValue(1).value)
        assertEquals(1, catalog.speciesById.getValue(1).dexNumber.value)
    }

    @Test
    fun malformedOptionalLearnsetProducesPartialUsableCatalog() {
        val bytes = ByteArray(0x8000)
        encodeGbText(bytes, 0x100, "ONE")
        encodeGbText(bytes, 0x10A, "TWO")
        putU16(bytes, 0, 0x4020)
        putU16(bytes, 2, 0x7FFE)
        bytes[0x4020] = 0
        bytes[0x4021] = 5
        bytes[0x4022] = 33
        bytes[0x4023] = 0
        bytes[0x7FFE] = 0
        bytes[0x7FFF] = 5
        val relationships = TableLayout(
            offset = 0,
            count = 2,
            recordSize = 2,
            variableLength = true,
            bank = 1,
        )
        val layout = ResolvedRomLayout(
            family = EngineFamily.CRYSTAL,
            generation = 2,
            platform = Platform.GBC,
            speciesCount = 2,
            moveCount = 100,
            tables = ProfileTables(
                speciesNames = TableLayout(0x100, 2, 10),
                evolutions = relationships,
                learnsets = relationships,
            ),
        )
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            RomHeader(Platform.GBC, "TEST"),
            rom.sha256,
            rom.crc32,
            rom.size,
            SelectionStatus.SELECTED,
            EngineFamily.CRYSTAL,
            null,
            20,
            emptyList(),
            emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(
            rom,
            analysis,
            layout,
        )

        assertEquals(
            CapabilityStatus.AVAILABLE,
            catalog.speciesById.getValue(1).learnset.status,
        )
        assertEquals(
            listOf(LearnsetEntry(5, 33)),
            catalog.speciesById.getValue(1).learnset.value,
        )
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            catalog.speciesById.getValue(2).learnset.status,
        )
        assertNull(catalog.speciesById.getValue(2).learnset.value)
        val evidence = catalog.capabilities.getValue(RomCapability.LEARNSETS)
        assertEquals(CapabilityStatus.PARTIAL, evidence.status)
        assertTrue(evidence.compatible)
        assertEquals(1, evidence.coveredRecords)
        assertEquals(2, evidence.expectedRecords)
        assertEquals(1, evidence.incompleteRecords)
    }

    @Test
    fun parserDoesNotInventCatalogWithoutSelectedFamily() {
        val result = CatalogParser.parse(RomImage(ByteArray(512)))

        assertEquals(SelectionStatus.NO_FAMILY_MATCH, result.analysis.status)
        assertNull(result.catalog)
        assertNull(result.layout)
    }

    @Test
    fun materializerPublishesANavigableEssentialCatalogBeforeCompletion() {
        val bytes = ByteArray(256) { 0xFF.toByte() }
        encodeGbaText(bytes, 0, "NONE")
        encodeGbaText(bytes, 11, "BULBA")
        val stats = 64
        repeat(6) { bytes[stats + 28 + it] = (40 + it).toByte() }
        bytes[stats + 34] = 12
        bytes[stats + 35] = 3
        putIdentitySpeciesIndexEvidence(bytes, 240, speciesCount = 2)
        val languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            3,
            Platform.GBA,
            2,
            0,
            ProfileTables(
                speciesNames = TableLayout(0, 2, 11),
                baseStats = TableLayout(stats, 2, 28),
            ),
            languageManifest = languageManifest,
        )
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"),
            rom.sha256,
            rom.crc32,
            rom.size,
            SelectionStatus.SELECTED,
            EngineFamily.EMERALD,
            null,
            20,
            emptyList(),
            emptyList(),
        )
        val updates = mutableListOf<CatalogMaterializationProgress>()
        val work = mutableListOf<CatalogWorkProgress>()

        val final = CatalogMaterializer.materialize(
            rom = rom,
            analysis = analysis,
            layout = layout,
            onProgress = updates::add,
            onWork = work::add,
        )

        assertEquals(CatalogMaterializationPhase.ESSENTIAL, updates.first().phase)
        assertSame(languageManifest, updates.first().catalog.languageManifest)
        assertEquals(
            "BULBA",
            updates.first().catalog.defaultLocalizedText()!!.speciesNames.getValue(1).value,
        )
        assertTrue(updates.first().catalog.defaultLocalizedText()!!.speciesDescriptions.isEmpty())
        assertEquals(CatalogMaterializationPhase.COMPLETE, updates.last().phase)
        assertSame(languageManifest, final.languageManifest)
        assertEquals(final, updates.last().catalog)
        assertEquals(
            listOf(
                CatalogWorkModule.CORE_RECORDS,
                CatalogWorkModule.SPECIES_MEDIA,
                CatalogWorkModule.EVOLUTIONS_AND_LEARNSETS,
                CatalogWorkModule.ENCOUNTERS,
                CatalogWorkModule.MOVE_DATA,
                CatalogWorkModule.ABILITY_DATA,
                CatalogWorkModule.MAPS,
                CatalogWorkModule.TRAINER_AND_THEME,
                CatalogWorkModule.CATALOG_STORAGE,
            ),
            work.map(CatalogWorkProgress::module),
        )
        assertEquals(CatalogWorkModule.entries.size, work.last().totalUnits)
    }

    @Test
    fun catalogWorkObserverFailureNeverAbortsMaterialization() {
        val rom = RomImage(ByteArray(0x200))
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        )
        val analysis = ParseResult(
            RomHeader(Platform.GBA, "TEST", "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(
            rom = rom,
            analysis = analysis,
            layout = layout,
            onWork = { error("observer failure") },
        )

        assertEquals(rom.sha256, catalog.romSha256)
    }

    private class CancelAfterChecks(private val successfulChecks: Int) : ParserCancellationToken {
        var checks: Int = 0
            private set

        override fun throwIfCancellationRequested() {
            checks++
            if (checks > successfulChecks) throw ParserCancellationException()
        }
    }

    private fun encodeGbText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0x7F
                in 'A'..'Z' -> (0x80 + char.code - 'A'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0x50
    }

    private fun encodeGbaText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                in 'a'..'z' -> (0xD5 + char.code - 'a'.code).toByte()
                '.' -> 0xAD.toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putIdentitySpeciesIndexEvidence(
        target: ByteArray,
        offset: Int,
        speciesCount: Int,
    ) {
        (1 until speciesCount).forEach { speciesId ->
            putU16(target, offset + (speciesId - 1) * 2, speciesId)
        }
    }

    private fun ResolvedRomLayout.withTypedDescriptions(bytes: ByteArray): ResolvedRomLayout {
        val selected = requireNotNull(tables.descriptions)
        val table = DescriptionTableLayout(
            offset = selected.offset.toLong(),
            count = selected.count.toLong(),
            recordSize = selected.recordSize,
            pointerOffsets = selected.pointerOffsets,
        )
        val rom = RomImage(bytes)
        val decoded = DescriptionCodec().decode(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            table,
        ) as DescriptionTableOutcome.Decoded
        return copy(
            resolvedDatasets = ResolvedDatasetLayouts(
                descriptions = ResolvedDescriptionLayout(table, decoded.rows),
            ),
        )
    }

    private fun putGenThreeInfo(
        target: ByteArray,
        offset: Int,
        rate: Int,
        slotsOffset: Int,
        slotCount: Int,
        firstSpecies: Int,
    ) {
        target[offset] = rate.toByte()
        putGbaPointer(target, offset + 4, slotsOffset)
        repeat(slotCount) { slot ->
            val entry = slotsOffset + slot * 4
            target[entry] = (5 + slot).toByte()
            target[entry + 1] = (7 + slot).toByte()
            putU16(target, entry + 2, firstSpecies + slot)
        }
    }
}
