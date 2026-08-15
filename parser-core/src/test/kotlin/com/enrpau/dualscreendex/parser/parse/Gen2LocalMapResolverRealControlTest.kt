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
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen2LocalMapResolverRealControlTest {
    @Test
    fun officialGoldResolvesCanonicalLocalMaps() = assertControl(controls[0])

    @Test
    fun officialSilverResolvesCanonicalLocalMaps() = assertControl(controls[1])

    @Test
    fun officialCrystalResolvesCanonicalLocalMaps() = assertControl(controls[2])

    private fun assertControl(control: Control) {
        val attempt = CatalogParser.parseCatching(realRom(control))
        assertEquals(SelectionStatus.SELECTED, attempt.analysis.status)
        val catalog = requireNotNull(attempt.catalog).getOrThrow()
        val localMaps = catalog.localMaps

        assertEquals(control.mapCount, localMaps.maps.size)
        assertEquals(control.mapCount, localMaps.assets.size)
        assertEquals(control.namedMapCount, localMaps.maps.count { !it.displayName.isNullOrBlank() })
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.LOCAL_MAP).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.WORLD_MAP).status)
        control.maps.forEach { expected -> assertMap(localMaps, expected) }
    }

    private fun assertMap(catalog: LocalMapCatalog, expected: ExpectedMap) {
        val map = catalog.maps.single { it.baseAreaId == expected.baseAreaId }
        assertEquals(expected.displayName, map.displayName)
        assertEquals(expected.gridWidth, map.gridWidth)
        assertEquals(expected.gridHeight, map.gridHeight)
        assertEquals(expected.gridWidth * 16, map.pixelWidth)
        assertEquals(expected.gridHeight * 16, map.pixelHeight)
        val image = requireNotNull(
            ImageIO.read(ByteArrayInputStream(catalog.assets.getValue(map.imageAssetKey).bytes)),
        )
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
        val namedMapCount: Int,
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
        val goldSilverMaps = listOf(
            ExpectedMap(
                baseAreaId = 0x1804,
                displayName = "NEW BARK TOWN",
                gridWidth = 20,
                gridHeight = 18,
                argbSha256 = "0b671d4fbe1b6c50ac63208d54808f9e0e71975573b849142bf4a263ca265e20",
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
                maps = goldSilverMaps,
            ),
            Control(
                environmentVariable = "DUALDEX_POKESILVER_ROM",
                romSha256 = "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
                mapCount = 368,
                namedMapCount = 364,
                maps = goldSilverMaps,
            ),
            Control(
                environmentVariable = "DUALDEX_POKECRYSTAL_ROM",
                romSha256 = "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2",
                mapCount = 388,
                namedMapCount = 382,
                maps = listOf(
                    ExpectedMap(
                        baseAreaId = 0x1804,
                        displayName = "NEW BARK TOWN",
                        gridWidth = 20,
                        gridHeight = 18,
                        argbSha256 = "0b671d4fbe1b6c50ac63208d54808f9e0e71975573b849142bf4a263ca265e20",
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
