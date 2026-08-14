package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GbaWorldMapTableRealControlTest {
    @Test
    fun battleTheaterRendersEveryTableOwnedRegion() {
        val rom = control()
        val outputDirectory = System.getenv("DUALDEX_BATTLE_THEATER_RENDER_DIRECTORY")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        outputDirectory?.let(Files::createDirectories)

        val hashes = listOf(
            RegionAsset(0xCBD458, 0xCBD668, 0xCBE7FC),
            RegionAsset(0xCB8CD0, 0xCB8EB0, 0xCB9814),
            RegionAsset(0xCB830C, 0xCB83C8, 0xCB87A4),
            RegionAsset(0xCB7CA0, 0xCB7D28, 0xCB7F84),
            RegionAsset(0xCB7130, 0xCB7208, 0xCB7694),
        ).mapIndexed { slot, asset ->
            val composition = GbaWorldMapCompositor.compose(
                GbaRomCompression.decodeAt(rom, asset.graphicsOffset),
                GbaRomCompression.decodeAt(rom, asset.mapOffset),
                ShortArray(PALETTE_COLORS) { index ->
                    rom.u16le(asset.paletteOffset + index * 2).toShort()
                },
            )
            assertTrue("region $slot did not compose: $composition", composition is GbaWorldMapComposition.Resolved)
            composition as GbaWorldMapComposition.Resolved
            assertEquals(GbaWorldMapFormat.AFFINE_8BPP_64X64, composition.format)
            assertEquals(224, composition.raster.width)
            assertEquals(120, composition.raster.height)
            outputDirectory?.let { directory ->
                Files.write(
                    directory.resolve("gen3-region-$slot.png"),
                    PngEncoder.encode(composition.raster),
                )
            }
            sha256(PngEncoder.encode(composition.raster))
        }

        assertEquals(EXPECTED_PNG_HASHES, hashes)
    }

    private fun control(): RomImage {
        val configured = System.getenv("DUALDEX_BATTLE_THEATER_ROM")
        assumeTrue("set DUALDEX_BATTLE_THEATER_ROM to run this control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(BATTLE_THEATER_SHA, it.sha256) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private data class RegionAsset(
        val mapOffset: Int,
        val graphicsOffset: Int,
        val paletteOffset: Int,
    )

    private companion object {
        const val PALETTE_COLORS = 48
        const val BATTLE_THEATER_SHA =
            "99c84950e2be2f887a84bdc32c741c92385bb4a54843d871a8876e9b47e1d59d"
        val EXPECTED_PNG_HASHES = listOf(
            "c9d5f2a5c77c0df16c14c73a15577f0c6f4a05794c191ebe72ed5a24724aadc6",
            "66ff671b9c80c39ab3c026944b39f9057cdf57e9f5144f82815812368f0b145d",
            "721d9471fc45ff6f293c727afa95b256f546fa1612ab9fcb609d6728d0dd5522",
            "cdf91ee5bcffa00246b6a8e0f3dfa5883e978bd4ac25606e28b4852c9f60723e",
            "073217c3c8f59efe635d01f06101413c492ce9eeeda45b9547df9d28d7dd72a0",
        )
    }
}
