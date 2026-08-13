package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GbaWorldMapCompositorRealControlTest {
    @Test
    fun officialEmeraldUsesTheOneByteAffineMap() {
        val rom = control("DUALDEX_OFFICIAL_EMERALD_ROM", OFFICIAL_EMERALD_SHA)
        val tiles = decoded(rom, 0x59f77c, OFFICIAL_EMERALD_TILES_SHA)
        val tilemap = decoded(rom, 0x5a04e0, OFFICIAL_EMERALD_MAP_SHA)
        val palette = palette(rom, 0x59f73c, 32, EMERALD_PALETTE_SHA)

        val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))

        assertEquals(GbaWorldMapFormat.AFFINE_8BPP_64X64, result.format)
        assertRaster(
            result.raster,
            224,
            120,
            "1c3a1bf13c851dcc707f1f3f71c8f90e703a0faf0832917a0195618952a77aab",
            "c9d5f2a5c77c0df16c14c73a15577f0c6f4a05794c191ebe72ed5a24724aadc6",
            mapOf(
                (0 to 0) to 0xFF9CD6FF.toInt(),
                (40 to 16) to 0xFF63D600.toInt(),
                (111 to 60) to 0xFF007300.toInt(),
                (223 to 119) to 0xFFA5B5FF.toInt(),
            ),
        )
    }

    @Test
    fun modernEmeraldUsesItsSourceOwnedOneByteAffineMap() {
        val rom = control("DUALDEX_MODERN_EMERALD_ROM", MODERN_EMERALD_SHA)
        val tiles = decoded(rom, 0x90d27c, MODERN_EMERALD_TILES_SHA)
        val tilemap = decoded(rom, 0x90e028, MODERN_EMERALD_MAP_SHA)
        val palette = palette(rom, 0x90d23c, 32, EMERALD_PALETTE_SHA)

        val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))

        assertEquals(GbaWorldMapFormat.AFFINE_8BPP_64X64, result.format)
        assertRaster(
            result.raster,
            224,
            120,
            "0163d9b5e747d788db925776c25a087a1cc4bbfa34fd3e021580aa8756717fb0",
            "80c4a69b9372276818768123dcd7cad09bcced88720704c8f424bc4501931ffe",
            mapOf(
                (0 to 0) to 0xFF9CD6FF.toInt(),
                (80 to 40) to 0xFF39AD08.toInt(),
                (160 to 80) to 0xFF4A9CE7.toInt(),
                (223 to 119) to 0xFFA5B5FF.toInt(),
            ),
        )
    }

    @Test
    fun classicUsesItsFullSourceOwnedAffineCanvas() {
        val rom = control("DUALDEX_CLASSIC_ROM", CLASSIC_SHA)
        val tiles = decoded(rom, 0x910390, CLASSIC_TILES_SHA)
        val tilemap = decoded(rom, 0x910cdc, CLASSIC_MAP_SHA)
        val palette = palette(rom, 0x910350, 32, EMERALD_PALETTE_SHA)

        val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))

        assertEquals(GbaWorldMapFormat.AFFINE_8BPP_64X64, result.format)
        assertRaster(
            result.raster,
            224,
            120,
            "dc326776034d066f0b2691e14f2325e78d6761b40db6da52c8454ab8fe46a46f",
            "0c171c9fe8175629aa47de4e2854a334a2025f21b9196ba2f4c57a8cdcbc67ec",
            mapOf(
                (0 to 0) to 0xFF39AD08.toInt(),
                (8 to 0) to 0xFF63D600.toInt(),
                (160 to 80) to 0xFF9CD6FF.toInt(),
                (223 to 119) to 0xFFA5B5FF.toInt(),
            ),
        )
    }

    @Test
    fun fireRedAndLeafGreenRenderAllFourTextMapIdentities() {
        assertFrlgControl(
            env = "DUALDEX_FIRERED_ROM",
            romSha = FIRERED_SHA,
            paletteOffset = 0x3ef34c,
            tilesOffset = 0x3ef68c,
            mapOffsets = intArrayOf(0x3f090c, 0x3f0b6c, 0x3f0c7c, 0x3f0d60),
        )
        assertFrlgControl(
            env = "DUALDEX_LEAFGREEN_ROM",
            romSha = LEAFGREEN_SHA,
            paletteOffset = 0x3ef188,
            tilesOffset = 0x3ef4c8,
            mapOffsets = intArrayOf(0x3f0748, 0x3f09a8, 0x3f0ab8, 0x3f0b9c),
        )
    }

    @Test
    fun cropCompleteFrlgPlaneMayOmitTheUnusedBottomRowTail() {
        val rom = control("DUALDEX_DARK_CRY_ROM", DARK_CRY_SHA)
        val tiles = decoded(rom, 0x7680c1, DARK_CRY_TILES_SHA)
        val tilemap = decoded(rom, 0x769161, DARK_CRY_SHORT_MAP_SHA)
        val palette = palette(rom, 0x3ef2dc, 80, DARK_CRY_PALETTE_SHA)

        val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))

        assertEquals(GbaWorldMapFormat.TEXT_4BPP_30X20, result.format)
        assertRaster(
            result.raster,
            176,
            120,
            "bb44c69d073c93911dd47d6121b936e40174cb84ca5128a5d0912ea6981b36d7",
            "9bc538416978211d88e36bd8440a423957517718c51c838895e0f67432ef35c0",
            mapOf(
                (0 to 0) to -4868683,
                (80 to 40) to -5391014,
                (175 to 119) to -11363262,
            ),
        )
    }

    @Test
    fun cropCompleteFrlgBoundaryRejectsOneMissingRequiredCell() {
        val rom = control("DUALDEX_DARK_CRY_ROM", DARK_CRY_SHA)
        val tiles = decoded(rom, 0x7680c1, DARK_CRY_TILES_SHA)
        val tilemap = decoded(rom, 0x769161, DARK_CRY_SHORT_MAP_SHA)
        val palette = palette(rom, 0x3ef2dc, 80, DARK_CRY_PALETTE_SHA)

        val exactCrop = resolved(GbaWorldMapCompositor.compose(tiles, tilemap.copyOf(1132), palette))
        assertEquals(
            "bb44c69d073c93911dd47d6121b936e40174cb84ca5128a5d0912ea6981b36d7",
            sha256(exactCrop.raster.argb),
        )
        val missingLastRequiredCell = GbaWorldMapCompositor.compose(tiles, tilemap.copyOf(1130), palette)
        assertTrue(missingLastRequiredCell is GbaWorldMapComposition.Rejected)
    }

    @Test
    fun provenTwoKilobyteClassicDecoyFailsClosed() {
        val rom = control("DUALDEX_CLASSIC_ROM", CLASSIC_SHA)
        val unrelatedTiles = decoded(rom, 0x326470, "5249cad1ec29f6364150204a47a070d8c8d50dc454c99bb7f9f84c2146ca4063")
        val unrelatedMap = decoded(rom, 0x326680, "dd8438271c30a80b064de4b4685ef84058a9f46ca2b01cdeff2f8bd06a4de31d")
        val palette = palette(rom, 0x910350, 32, EMERALD_PALETTE_SHA)

        val result = GbaWorldMapCompositor.compose(unrelatedTiles, unrelatedMap, palette)

        assertTrue(result is GbaWorldMapComposition.Rejected)
    }

    private fun assertFrlgControl(
        env: String,
        romSha: String,
        paletteOffset: Int,
        tilesOffset: Int,
        mapOffsets: IntArray,
    ) {
        val rom = control(env, romSha)
        val palette = palette(rom, paletteOffset, 80, FRLG_PALETTE_SHA)
        val tiles = decoded(rom, tilesOffset, FRLG_TILES_SHA)
        val expected = listOf(
            FrlgExpected(
                "c9f38c5d52099958c18efe737a45ba04ce8101b0eb349e9d2243bf8324d82b49",
                "250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654",
                "c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa",
                0xFF39AD08.toInt(),
            ),
            FrlgExpected(
                "72c0b2615eaf061f5490779a3caf0470dbb88b08dd010c4887b8ed6d61eac124",
                "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135",
                "5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70",
                0xFF9CD6FF.toInt(),
            ),
            FrlgExpected(
                "cd68b8a70e21ac1f53344160234d1eabb7221040c62102c3a648faa550eb40db",
                "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a",
                "d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160",
                0xFF9CD6FF.toInt(),
            ),
            FrlgExpected(
                "2d0f7a665f88f15d28c213f7e490c232c3eeea8ffc12156aafce32499caa2400",
                "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b",
                "2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765",
                0xFF9CD6FF.toInt(),
            ),
        )
        assertEquals(expected.size, mapOffsets.size)
        val actualPngHashes = mapOffsets.mapIndexed { index, offset ->
            val tilemap = decoded(rom, offset, expected[index].mapSha)
            val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))
            assertEquals(GbaWorldMapFormat.TEXT_4BPP_30X20, result.format)
            assertEquals(176, result.raster.width)
            assertEquals(120, result.raster.height)
            assertEquals(expected[index].argbSha, sha256(result.raster.argb))
            assertEquals(expected[index].topLeft, result.raster.argb.first())
            sha256(PngEncoder.encode(result.raster))
        }
        assertEquals(expected.map(FrlgExpected::pngSha), actualPngHashes)
    }

    private fun control(env: String, expectedSha: String): RomImage {
        val configured = System.getenv(env)
        assumeTrue("set $env to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(expectedSha, it.sha256) }
    }

    private fun decoded(rom: RomImage, offset: Int, expectedSha: String): ByteArray =
        GbaRomCompression.decodeAt(rom, offset).also { assertEquals(expectedSha, sha256(it)) }

    private fun palette(
        rom: RomImage,
        offset: Int,
        colors: Int,
        expectedSha: String,
    ): ShortArray {
        val bytes = rom.slice(offset, colors * 2)
        assertEquals(expectedSha, sha256(bytes))
        return ShortArray(colors) { index -> rom.u16le(offset + index * 2).toShort() }
    }

    private fun resolved(result: GbaWorldMapComposition): GbaWorldMapComposition.Resolved {
        assertTrue("expected resolved composition, got $result", result is GbaWorldMapComposition.Resolved)
        return result as GbaWorldMapComposition.Resolved
    }

    private fun assertRaster(
        raster: RgbaSprite,
        width: Int,
        height: Int,
        expectedArgbSha: String,
        expectedPngSha: String,
        expectedPixels: Map<Pair<Int, Int>, Int>,
    ) {
        assertEquals(width, raster.width)
        assertEquals(height, raster.height)
        assertEquals(expectedArgbSha, sha256(raster.argb))
        assertEquals(expectedPngSha, sha256(PngEncoder.encode(raster)))
        expectedPixels.forEach { (position, expected) ->
            assertEquals("pixel $position", expected, raster.argb[position.second * width + position.first])
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun sha256(values: IntArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value -> digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()) }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class FrlgExpected(
        val mapSha: String,
        val argbSha: String,
        val pngSha: String,
        val topLeft: Int,
    )

    private companion object {
        const val OFFICIAL_EMERALD_SHA = "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"
        const val MODERN_EMERALD_SHA = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895"
        const val CLASSIC_SHA = "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c"
        const val FIRERED_SHA = "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059"
        const val LEAFGREEN_SHA = "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825"
        const val DARK_CRY_SHA = "e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed"
        const val OFFICIAL_EMERALD_TILES_SHA = "7fab32a15049c96dd3d8eb9c0ef9ff969a0254c6d96c8538b4b104e8af13dd39"
        const val OFFICIAL_EMERALD_MAP_SHA = "dcf3d464dad11083ece52687184c89ab069c108340ecf5540eb0f14c6d8c8096"
        const val MODERN_EMERALD_TILES_SHA = "5828ca11400d78d81f09aad639ee481155eda63f923dc6ca1175ea6193367148"
        const val MODERN_EMERALD_MAP_SHA = "1627ca00f20c0a593ed30d4657cd165bdf92f31c30b4304464aed3e2688de873"
        const val CLASSIC_TILES_SHA = "ce2b7db0298fe504ec250092748c940649deb61ad342655480576aef34622de8"
        const val CLASSIC_MAP_SHA = "8675dbba552d2ca9f2179bf15597fa1ed1612a2a39faf54dda173b887d4836a1"
        const val EMERALD_PALETTE_SHA = "795a5502910a4a8d226589bfd0d8c421111e30db3d152acaf66186e6659b4563"
        const val FRLG_TILES_SHA = "f9e8ddc403b2efcd9eaf87a8a1f16d9248f92d2372e42fb7aa88b09aed5fb3b4"
        const val FRLG_PALETTE_SHA = "116382eeea3b668f188e80eb49f7440b1daeb0732cd81da2401da887e1e0e227"
        const val DARK_CRY_TILES_SHA = "ee83cb51854bb3f67a88e43cd254ef34c4bf9239e439929432bbd8cd381a9547"
        const val DARK_CRY_SHORT_MAP_SHA = "6d330c519ae07ce7e8e09fd6dc30de980e2445c956496d87269a5db477a9b1cc"
        const val DARK_CRY_PALETTE_SHA = "e3ef03b01b555aa548511076cabe462f6a2d95cb668a5a4c2b9e4271ed18b060"
    }
}
