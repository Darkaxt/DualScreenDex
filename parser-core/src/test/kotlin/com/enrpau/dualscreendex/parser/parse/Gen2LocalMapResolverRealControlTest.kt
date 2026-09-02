package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.EncounterMaterializer
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapLightingPolicy
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiService
import com.enrpau.dualscreendex.parser.catalog.LocalMapRasterRenderer
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen2LocalMapResolverRealControlTest {
    @Test
    fun officialGoldResolvesCanonicalLocalMaps() = assertControl(controls[0])

    @Test
    fun officialGoldRetainsStructuralLocalMapsWithoutTextAuthority() = assertStructuralControl(controls[0])

    @Test
    fun officialSilverResolvesCanonicalLocalMaps() = assertControl(controls[1])

    @Test
    fun officialCrystalResolvesCanonicalLocalMaps() = assertControl(controls[2])

    private fun assertStructuralControl(control: Control) {
        val rom = realRom(control)
        val analysis = ParserOrchestrator.analyze(rom)
        val layout = requireNotNull(
            analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout,
        )
        val family = requireNotNull(analysis.selectedFamily)
        val baseAreaIds = EncounterMaterializer.materialize(rom, layout).mapTo(linkedSetOf()) { it.id / 10 }
        val session = RomAnalysisSession(rom, RomHeaderReader.read(rom))
        val named = Gen2LocalMapResolver.resolve(session, baseAreaIds, family, requireNotNull(layout.defaultTextCodec()))
        val structural = Gen2LocalMapResolver.resolve(session, baseAreaIds, family, null)

        assertTrue("${control.environmentVariable}: $named", named is LocalMapResolution.Resolved)
        assertTrue("${control.environmentVariable}: $structural", structural is LocalMapResolution.Resolved)
        val namedCatalog = (named as LocalMapResolution.Resolved).catalog.validate()
        val structuralCatalog = (structural as LocalMapResolution.Resolved).catalog.validate()
        assertEquals(namedCatalog.maps.map { it.copy(displayName = null) }, structuralCatalog.maps)
        assertEquals(namedCatalog.indexedAssets, structuralCatalog.indexedAssets)
        assertEquals(namedCatalog.scenes, structuralCatalog.scenes)
        assertEquals(
            namedCatalog.pois.map { poi ->
                poi.copy(
                    displayName = null,
                    item = poi.item?.copy(displayName = null),
                    displayNamesByTrainerGender = emptyMap(),
                )
            },
            structuralCatalog.pois,
        )
    }

    private fun assertControl(control: Control) {
        val attempt = CatalogParser.parseCatching(realRom(control))
        assertEquals(SelectionStatus.SELECTED, attempt.analysis.status)
        val catalog = requireNotNull(attempt.catalog).getOrThrow()
        val localMaps = catalog.localMaps

        assertEquals(control.mapCount, localMaps.maps.size)
        assertEquals(0, localMaps.assets.size)
        assertEquals(control.mapCount, localMaps.indexedAssets.size)
        assertEquals(control.timeOfDayWramOffset, catalog.runtimeMetadata.gen2TimeOfDayWramOffset)
        assertTrue(
            localMaps.indexedAssets.values.sumOf { it.compressedIndices.size.toLong() } <
                localMaps.maps.sumOf { it.pixelWidth.toLong() * it.pixelHeight },
        )
        assertEquals(control.namedMapCount, localMaps.maps.count { !it.displayName.isNullOrBlank() })
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.LOCAL_MAP).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.WORLD_MAP).status)
        assertEquals(control.trainerGenders, catalog.trainerAssets.overworldAssetKeys.keys)
        catalog.trainerAssets.overworldAssetKeys.values.forEach { key ->
            assertEquals(16, catalog.trainerAssets.assets.getValue(key).width)
            assertEquals(16, catalog.trainerAssets.assets.getValue(key).height)
        }
        val connectionFailures = catalog.capabilities.getValue(RomCapability.LOCAL_MAP).reasons.filter {
            it.startsWith("map 0x") && it.contains("connection")
        }
        assertTrue(connectionFailures.joinToString("\n"), connectionFailures.isEmpty())
        assertScenes(localMaps)
        assertPois(localMaps)
        control.maps.forEach { expected -> assertMap(localMaps, expected) }
    }

    private fun assertPois(catalog: LocalMapCatalog) {
        val newBarkTown = catalog.pois.filter { it.baseAreaId == 0x1804 }
        assertEquals(5, newBarkTown.size)
        assertEquals(
            setOf(0x1805, 0x1806, 0x1808, 0x1809),
            newBarkTown.mapNotNullTo(mutableSetOf()) { it.destinationBaseAreaId },
        )
        assertTrue(newBarkTown.all { it.kind == LocalMapPoiKind.PLACE })
        assertTrue(newBarkTown.all { it.organicVisibility == LocalMapPoiOrganicVisibility.ENTRANCE_PROXIMITY })
        assertEquals("NEW BARK TOWN", newBarkTown.single { it.tileX == 8 && it.tileY == 8 }.displayName)

        val route29Potion = catalog.pois.single {
            it.baseAreaId == 0x1803 && it.tileX == 48 && it.tileY == 2
        }
        assertEquals(LocalMapPoiKind.VISIBLE_ITEM, route29Potion.kind)
        assertEquals(0x12, route29Potion.item?.itemId)
        assertEquals(1709, route29Potion.item?.collectionFlagId)

        val azaleaHiddenFullHeal = catalog.pois.single {
            it.baseAreaId == 0x0807 && it.tileX == 31 && it.tileY == 6
        }
        assertEquals(LocalMapPoiKind.HIDDEN_ITEM, azaleaHiddenFullHeal.kind)
        assertEquals(LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE, azaleaHiddenFullHeal.organicVisibility)
        assertEquals(0x26, azaleaHiddenFullHeal.item?.itemId)
        assertEquals(177, azaleaHiddenFullHeal.item?.collectionFlagId)

        val azaleaCenter = catalog.pois.single {
            it.baseAreaId == 0x0807 && it.tileX == 16 && it.tileY == 9
        }
        assertEquals(LocalMapPoiKind.SERVICE, azaleaCenter.kind)
        assertEquals(LocalMapPoiService.POKEMON_CENTER, azaleaCenter.service)
        assertEquals(0x0801, azaleaCenter.destinationBaseAreaId)

        val azaleaMart = catalog.pois.single {
            it.baseAreaId == 0x0807 && it.tileX == 22 && it.tileY == 5
        }
        assertEquals(LocalMapPoiKind.SERVICE, azaleaMart.kind)
        assertEquals(LocalMapPoiService.MART, azaleaMart.service)
        assertEquals(0x0803, azaleaMart.destinationBaseAreaId)
    }

    private fun assertScenes(catalog: LocalMapCatalog) {
        assertTrue(catalog.scenes.isNotEmpty())
        val scene = catalog.scenes.single { generated ->
            generated.placements.any { it.baseAreaId == 0x1804 } &&
                generated.placements.any { it.baseAreaId == 0x1803 }
        }
        val newBarkTown = scene.placements.single { it.baseAreaId == 0x1804 }
        val route29 = scene.placements.single { it.baseAreaId == 0x1803 }
        assertEquals(newBarkTown.gridX, route29.gridX + 60)
        assertEquals(newBarkTown.gridY, route29.gridY)

        val mapsByKey = catalog.maps.associateBy { it.key }
        val placedKeys = catalog.scenes.flatMap { it.placements }.map { it.localMapKey }
        assertEquals(placedKeys.size, placedKeys.toSet().size)
        catalog.scenes.forEach { generated ->
            generated.placements.indices.forEach { index ->
                val first = generated.placements[index]
                val firstMap = mapsByKey.getValue(first.localMapKey)
                generated.placements.drop(index + 1).forEach { second ->
                    val secondMap = mapsByKey.getValue(second.localMapKey)
                    val overlaps = first.gridX < second.gridX + secondMap.gridWidth &&
                        second.gridX < first.gridX + firstMap.gridWidth &&
                        first.gridY < second.gridY + secondMap.gridHeight &&
                        second.gridY < first.gridY + firstMap.gridHeight
                    assertTrue("${generated.key}: ${first.localMapKey} overlaps ${second.localMapKey}", !overlaps)
                }
            }
        }
    }

    private fun assertMap(catalog: LocalMapCatalog, expected: ExpectedMap) {
        val map = catalog.maps.single { it.baseAreaId == expected.baseAreaId }
        assertEquals(expected.displayName, map.displayName)
        assertEquals(expected.gridWidth, map.gridWidth)
        assertEquals(expected.gridHeight, map.gridHeight)
        assertEquals(expected.gridWidth * 16, map.pixelWidth)
        assertEquals(expected.gridHeight * 16, map.pixelHeight)
        val asset = catalog.indexedAssets.getValue(map.imageAssetKey)
        assertEquals(map.pixelWidth, asset.pixelWidth)
        assertEquals(map.pixelHeight, asset.pixelHeight)
        val hashes = MapLighting.entries.associateWith { lighting ->
            assertEquals(32, asset.palettes[lighting].size)
            LocalMapRasterRenderer.render(asset, lighting).also { rendered ->
                assertEquals(map.pixelWidth * map.pixelHeight, rendered.argb.size)
            }.argb.argbSha256()
        }
        assertEquals(expected.argbSha256, hashes.getValue(MapLighting.DAY))
        expected.lightingArgbSha256?.let { assertEquals(it, hashes) }
        if (expected.baseAreaId == 0x1804) {
            assertEquals(LocalMapLightingPolicy.AUTO, asset.lightingPolicy)
            assertEquals(4, hashes.values.toSet().size)
            if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
                println("local-map-lighting ${expected.displayName} $hashes")
            }
        }
    }

    private fun realRom(control: Control): RomImage {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(control.romSha256, it.sha256) }
    }

    private fun IntArray.argbSha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        forEach { color ->
            buffer.clear()
            buffer.putInt(color)
            digest.update(buffer.array())
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class Control(
        val environmentVariable: String,
        val romSha256: String,
        val mapCount: Int,
        val namedMapCount: Int,
        val timeOfDayWramOffset: Int,
        val trainerGenders: Set<Int>,
        val maps: List<ExpectedMap>,
    )

    private data class ExpectedMap(
        val baseAreaId: Int,
        val displayName: String,
        val gridWidth: Int,
        val gridHeight: Int,
        val argbSha256: String,
        val lightingArgbSha256: Map<MapLighting, String>? = null,
    )

    private companion object {
        val newBarkLightingHashes = mapOf(
            MapLighting.MORNING to "e67203f289cf6d80a8b41873fac8c9cf4aff06212389c7ceaa81c9125f3fc20d",
            MapLighting.DAY to "0b671d4fbe1b6c50ac63208d54808f9e0e71975573b849142bf4a263ca265e20",
            MapLighting.NIGHT to "36a28eb197176b611b1f98175455fff956f7266b1ffcf9fb51242a284a236118",
            MapLighting.DARK to "cdb73b0fa0857e485b40c127ce883277343e386719dc7a8d70f28c8bd8c86871",
        )
        val goldSilverMaps = listOf(
            ExpectedMap(
                baseAreaId = 0x1804,
                displayName = "NEW BARK TOWN",
                gridWidth = 20,
                gridHeight = 18,
                argbSha256 = "0b671d4fbe1b6c50ac63208d54808f9e0e71975573b849142bf4a263ca265e20",
                lightingArgbSha256 = newBarkLightingHashes,
            ),
            ExpectedMap(
                baseAreaId = 0x1803,
                displayName = "ROUTE 29",
                gridWidth = 60,
                gridHeight = 18,
                argbSha256 = "8170d6964bf21157d3fd9bc46acd69aa2b363d5be4ab05c95d9551435d8aa1da",
            ),
            ExpectedMap(
                baseAreaId = 0x0409,
                displayName = "ECRUTEAK CITY",
                gridWidth = 40,
                gridHeight = 36,
                argbSha256 = "d2dbf075c9002e7773bf78dff87884e63d198ca0e3ca45c16afb1e362fd3ce1d",
            ),
            ExpectedMap(
                baseAreaId = 0x0301,
                displayName = "SPROUT TOWER",
                gridWidth = 20,
                gridHeight = 16,
                argbSha256 = "4bfdf5809d92581ccca1b273cc25b0f93058a9cae47507ffbc4979030590b316",
            ),
        )
        val controls = listOf(
            Control(
                environmentVariable = "DUALDEX_POKEGOLD_ROM",
                romSha256 = "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
                mapCount = 368,
                namedMapCount = 364,
                timeOfDayWramOffset = 0x1568,
                trainerGenders = setOf(0),
                maps = goldSilverMaps,
            ),
            Control(
                environmentVariable = "DUALDEX_POKESILVER_ROM",
                romSha256 = "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
                mapCount = 368,
                namedMapCount = 364,
                timeOfDayWramOffset = 0x1568,
                trainerGenders = setOf(0),
                maps = goldSilverMaps,
            ),
            Control(
                environmentVariable = "DUALDEX_POKECRYSTAL_ROM",
                romSha256 = "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2",
                mapCount = 388,
                namedMapCount = 382,
                timeOfDayWramOffset = 0x1841,
                trainerGenders = setOf(0, 1),
                maps = listOf(
                    ExpectedMap(
                        baseAreaId = 0x1804,
                        displayName = "NEW BARK TOWN",
                        gridWidth = 20,
                        gridHeight = 18,
                        argbSha256 = "0b671d4fbe1b6c50ac63208d54808f9e0e71975573b849142bf4a263ca265e20",
                        lightingArgbSha256 = newBarkLightingHashes,
                    ),
                    ExpectedMap(
                        baseAreaId = 0x1803,
                        displayName = "ROUTE 29",
                        gridWidth = 60,
                        gridHeight = 18,
                        argbSha256 = "8170d6964bf21157d3fd9bc46acd69aa2b363d5be4ab05c95d9551435d8aa1da",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x0409,
                        displayName = "ECRUTEAK CITY",
                        gridWidth = 40,
                        gridHeight = 36,
                        argbSha256 = "135d1957f5b269f817fadba364280473bf78807f0b7ab5a4cde3da52a6a1ec3d",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x0301,
                        displayName = "SPROUT TOWER",
                        gridWidth = 20,
                        gridHeight = 16,
                        argbSha256 = "4803df1206b6690ca41250a071b126584b1f5ac467e847cfa0316f7e30dc07ed",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x1610,
                        displayName = "BATTLE TOWER",
                        gridWidth = 20,
                        gridHeight = 28,
                        argbSha256 = "e73819e64d8ed16a1923518872d119b1b4c6c25c439bdfb8182fa64626fbb006",
                    ),
                ),
            ),
        )
    }
}
