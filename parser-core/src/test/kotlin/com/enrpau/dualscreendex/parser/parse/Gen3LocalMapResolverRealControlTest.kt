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

class Gen3LocalMapResolverRealControlTest {
    @Test
    fun modernEmeraldResolvesPrimaryAndSecondaryTilesetLocalMaps() {
        val attempt = CatalogParser.parseCatching(realRom())
        assertEquals(SelectionStatus.SELECTED, attempt.analysis.status)
        val catalog = requireNotNull(attempt.catalog).getOrThrow()
        val localMaps = catalog.localMaps

        assertEquals(133, localMaps.maps.size)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.LOCAL_MAP).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.WORLD_MAP).status)
        assertMap(
            localMaps,
            baseAreaId = 0x0010,
            gridWidth = 20,
            gridHeight = 20,
            expectedArgbSha256 = "78b05f42cced70d02c30bc7443b3829032af00642c36d4b4725b55943ee72e58",
        )
        assertMap(
            localMaps,
            baseAreaId = 0x0011,
            gridWidth = 50,
            gridHeight = 20,
            expectedArgbSha256 = "39f0b8578ec8933fa817d134d9e7a196bfc6da1c8e9f7d9f21380ce2ae1b1af6",
        )
    }

    private fun assertMap(
        catalog: LocalMapCatalog,
        baseAreaId: Int,
        gridWidth: Int,
        gridHeight: Int,
        expectedArgbSha256: String,
    ) {
        val map = catalog.maps.single { it.baseAreaId == baseAreaId }
        assertEquals(gridWidth, map.gridWidth)
        assertEquals(gridHeight, map.gridHeight)
        assertEquals(gridWidth * 16, map.pixelWidth)
        assertEquals(gridHeight * 16, map.pixelHeight)
        val png = requireNotNull(catalog.assets[map.imageAssetKey]).bytes
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
        assertEquals(expectedArgbSha256, digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }

    private fun realRom(): RomImage {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also {
            assertEquals("21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895", it.sha256)
        }
    }
}
