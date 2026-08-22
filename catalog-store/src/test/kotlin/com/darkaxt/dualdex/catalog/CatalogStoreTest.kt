package com.darkaxt.dualdex.catalog

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
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
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
import org.junit.Assume.assumeTrue
import org.junit.Test

class CatalogStoreTest {
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
        assertCatalogReferencesClose(reopened)
        assertEquals(second.romSha256, reopened.romSha256)
        assertEquals(CatalogSchema.requiredSections, stored.committedSections)
        assertEquals(16, stored.committedSections.size)
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
        assertCatalogReferencesClose(reopened)
        assertEquals(CatalogSchema.requiredSections, stored.committedSections)
        assertEquals(16, stored.committedSections.size)
        assertDatabaseIntegrity(cache.fileFor(second.romSha256))
    }

    @Test
    fun `large binary catalog section survives streamed gzip json persistence`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val bytes = ByteArray(12 * 1024 * 1024) { index -> (index * 31 + index / 257).toByte() }.also { png ->
            byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10).copyInto(png)
        }
        val map = LocalMap("local/0001", "Large", 1, 16, 16, 1, 1, "local/0001/map")
        val catalog = completeCatalog("6".repeat(64)).copy(
            localMaps = LocalMapCatalog(listOf(map), mapOf(map.imageAssetKey to PngMapAsset(bytes))),
        )

        cache.write(
            catalog,
            CatalogSourceMetadata.direct("Large.gba", 32 * 1024 * 1024, "LARGE"),
            CatalogWriteProgress.complete(),
        )
        val reopened = requireNotNull(cache.readComplete(catalog.romSha256))

        assertTrue(reopened.catalog.localMaps.assets.getValue(map.imageAssetKey).bytes.contentEquals(bytes))
        assertEquals("gzip+json", JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.query(
                "SELECT encoding FROM catalog_sections WHERE name = 'local_maps'",
            ) { row -> row.string("encoding") }.single()
        })
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
                            displayName = "Region 0",
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
        val catalog = completeCatalog("7".repeat(64)).copy(
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

        assertEquals(31, CatalogSchema.parserSchemaVersion)
        assertEquals(worldMaps, reopened?.catalog?.worldMaps)
        assertEquals(localMaps.maps, reopened?.catalog?.localMaps?.maps)
        assertEquals(localMaps.scenes, reopened?.catalog?.localMaps?.scenes)
        assertEquals(localMaps.pois, reopened?.catalog?.localMaps?.pois)
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
        assertEquals(CatalogSchema.requiredSections, reopened?.committedSections)
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

        assertEquals(CatalogSchema.requiredSections, stored.committedSections)
        assertEquals(1, reopened.worldMaps.regions.size)
        assertEquals(557, reopened.localMaps.maps.size)
        assertEquals(
            mapOf(0 to "trainer/avatar/male", 1 to "trainer/avatar/female"),
            reopened.trainerAssets.avatarAssetKeys,
        )
        assertEquals(reopened.trainerAssets.avatarAssetKeys.values.toSet(), reopened.trainerAssets.assets.keys)
        reopened.trainerAssets.avatarAssetKeys.values.forEach { key ->
            assertEquals(64, reopened.trainerAssets.assets.getValue(key).width)
            assertEquals(64, reopened.trainerAssets.assets.getValue(key).height)
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
        assertEquals(catalog.localMaps.assets.keys, reopened.localMaps.assets.keys)
        val palletTownAsset = catalog.localMaps.maps.single { it.baseAreaId == 0x00 }.imageAssetKey
        assertTrue(
            catalog.localMaps.assets.getValue(palletTownAsset).bytes.contentEquals(
                reopened.localMaps.assets.getValue(palletTownAsset).bytes,
            ),
        )
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
    }

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

        assertEquals(31, CatalogSchema.parserSchemaVersion)
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
        assertEquals("Route 101", reopened?.catalog?.runtimeMetadata?.areaNamesByBaseId?.get(0x0010))
        assertEquals(CatalogSchema.requiredSections, reopened?.committedSections)
        assertEquals(catalog.theme, reopened?.catalog?.theme)
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
    fun `previous parser revision is invalidated so corrected local map POIs are rebuilt`() {
        val root = newRoot()
        val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
        val catalog = completeCatalog("d".repeat(64))
        val source = CatalogSourceMetadata.direct("Yellow.gb", 1_048_576, "POKEMON YELLOW")
        cache.write(catalog, source, CatalogWriteProgress.complete())

        JdbcCatalogDatabaseFactory.open(cache.fileFor(catalog.romSha256)).use { database ->
            database.execute(
                "UPDATE catalog_metadata SET parser_schema_version = ? WHERE id = 1",
                listOf(CatalogSchema.parserSchemaVersion - 1),
            )
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

    private fun assertDatabaseIntegrity(file: java.io.File) {
        assertTrue(file.length() > 0)
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            assertEquals(listOf("ok"), database.query("PRAGMA quick_check") { row -> row.string("quick_check") })
            assertTrue(database.query("PRAGMA foreign_key_check") { row -> row.string("table") }.isEmpty())
            assertEquals(
                listOf(14L),
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

    private fun completeCatalog(hash: String): ParsedCatalog {
        val sprite = RgbaSprite(2, 2, intArrayOf(0x00000000, 0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt()))
        val avatar = RgbaSprite(64, 64, IntArray(64 * 64) { 0xff406080.toInt() })
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
        return ParsedCatalog(
            romSha256 = hash,
            romCrc32 = "8C7DBECA",
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(6 to species),
            movesById = mapOf(53 to move),
            typesById = mapOf(10 to TypeRecord(10, CatalogField.available("Fire"), CatalogField.available(typePresentation))),
            abilitiesById = mapOf(66 to ability),
            naturesById = mapOf(
                0 to NatureRecord(
                    id = 0,
                    name = "Resolute",
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
            trainerAssets = TrainerAssetCatalog(
                avatarAssetKeys = mapOf(
                    0 to "trainer/avatar/male",
                    1 to "trainer/avatar/female",
                ),
                badgeAssetKeys = badgeKeys,
                assets = buildMap {
                    put("trainer/avatar/male", avatar)
                    put("trainer/avatar/female", avatar)
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
