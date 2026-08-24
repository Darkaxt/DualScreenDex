package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen1LocalMapResolverRealControlTest {
    @Test
    fun officialRedResolvesCanonicalLocalMaps() = assertControl(controls[0])

    @Test
    fun officialBlueResolvesCanonicalLocalMaps() = assertControl(controls[1])

    @Test
    fun officialYellowResolvesCanonicalLocalMaps() = assertControl(controls[2])

    private fun assertControl(control: Control) {
        val attempt = CatalogParser.parseCatching(realRom(control))
        assertEquals(SelectionStatus.SELECTED, attempt.analysis.status)
        val catalog = requireNotNull(attempt.catalog).getOrThrow()
        val localMaps = catalog.localMaps

        assertEquals(control.mapCount, localMaps.maps.size)
        assertEquals(control.mapCount, localMaps.assets.size)
        assertTrue(localMaps.maps.all { !it.displayName.isNullOrBlank() })
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.LOCAL_MAP).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.WORLD_MAP).status)
        val connectionFailures = catalog.capabilities.getValue(RomCapability.LOCAL_MAP).reasons.filter {
            it.startsWith("map 0x") && it.contains("connection")
        }
        assertTrue(connectionFailures.joinToString("\n"), connectionFailures.isEmpty())
        assertScenes(localMaps)
        control.maps.forEach { expected -> assertMap(localMaps, expected) }
    }

    private fun assertScenes(catalog: LocalMapCatalog) {
        assertTrue(catalog.scenes.isNotEmpty())
        val scene = catalog.scenes.single { generated ->
            generated.placements.any { it.baseAreaId == 0x00 } &&
                generated.placements.any { it.baseAreaId == 0x0c }
        }
        assertEquals("scene/0000", scene.key)
        val palletTown = scene.placements.single { it.baseAreaId == 0x00 }
        val route1 = scene.placements.single { it.baseAreaId == 0x0c }
        assertEquals(palletTown.gridX, route1.gridX)
        assertEquals(palletTown.gridY, route1.gridY + 36)

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
        val png = catalog.assets.getValue(map.imageAssetKey).bytes
        val image = requireNotNull(ImageIO.read(ByteArrayInputStream(png)))
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        repeat(image.height) { y ->
            repeat(image.width) { x ->
                buffer.clear()
                buffer.putInt(image.getRGB(x, y))
                digest.update(buffer.array())
            }
        }
        assertEquals(expected.argbSha256, digest.digest().toHex())
    }

    private fun realRom(control: Control): RomImage {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(control.romSha256, it.sha256) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class Control(
        val environmentVariable: String,
        val romSha256: String,
        val mapCount: Int,
        val maps: List<ExpectedMap>,
    )

    private data class ExpectedMap(
        val baseAreaId: Int,
        val displayName: String,
        val gridWidth: Int,
        val gridHeight: Int,
        val argbSha256: String,
    )

    private companion object {
        val redBlueMaps = listOf(
            ExpectedMap(
                baseAreaId = 0x00,
                displayName = "PALLET TOWN",
                gridWidth = 20,
                gridHeight = 18,
                argbSha256 = "5a8e5aa1eb5646891443b07a95e1d600d1cb1a6a293c3039e3638fa4b06c75ad",
            ),
            ExpectedMap(
                baseAreaId = 0x0C,
                displayName = "ROUTE 1",
                gridWidth = 20,
                gridHeight = 36,
                argbSha256 = "cc2f4e538eb96827191defa350275a6aaf084c2138f13a8ba18a67f450770308",
            ),
            ExpectedMap(
                baseAreaId = 0x1C,
                displayName = "ROUTE 17",
                gridWidth = 20,
                gridHeight = 144,
                argbSha256 = "1adb0b1a2317a65e64016419d59f5065ad9b68effcca33ecea7b22a048c75f8e",
            ),
            ExpectedMap(
                baseAreaId = 0x33,
                displayName = "VIRIDIAN FOREST",
                gridWidth = 34,
                gridHeight = 48,
                argbSha256 = "9976be17417650ed3694b046fa167aafe48ee6d7e23b0731adec748fcca7487b",
            ),
        )
        val controls = listOf(
            Control(
                environmentVariable = "DUALDEX_POKERED_ROM",
                romSha256 = "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
                mapCount = 226,
                maps = redBlueMaps,
            ),
            Control(
                environmentVariable = "DUALDEX_POKEBLUE_ROM",
                romSha256 = "2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d",
                mapCount = 226,
                maps = redBlueMaps,
            ),
            Control(
                environmentVariable = "DUALDEX_POKEYELLOW_ROM",
                romSha256 = "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
                mapCount = 227,
                maps = listOf(
                    ExpectedMap(
                        baseAreaId = 0x00,
                        displayName = "PALLET TOWN",
                        gridWidth = 20,
                        gridHeight = 18,
                        argbSha256 = "7c7816eddb245ec5291eb2c2739f4b9da30f23e247ba22df714dae987478ca58",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x0C,
                        displayName = "ROUTE 1",
                        gridWidth = 20,
                        gridHeight = 36,
                        argbSha256 = "465b26b092ddee6e2629de2ab9a17b88655e9fb8e391717db5ab05f97421b850",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x1C,
                        displayName = "ROUTE 17",
                        gridWidth = 20,
                        gridHeight = 144,
                        argbSha256 = "e6c9f350a4f87308456d98d8635c214df6afbac3327f069ff9872b60fa6b88e8",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x33,
                        displayName = "VIRIDIAN FOREST",
                        gridWidth = 34,
                        gridHeight = 48,
                        argbSha256 = "9976be17417650ed3694b046fa167aafe48ee6d7e23b0731adec748fcca7487b",
                    ),
                    ExpectedMap(
                        baseAreaId = 0xF8,
                        displayName = "SEA ROUTE 19",
                        gridWidth = 14,
                        gridHeight = 8,
                        argbSha256 = "a84861f0093e6a085231b25c3f04272b48503558512f866950b90976009dbd45",
                    ),
                ),
            ),
        )
    }
}
