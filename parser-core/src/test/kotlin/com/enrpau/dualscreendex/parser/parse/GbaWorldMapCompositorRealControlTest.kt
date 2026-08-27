package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.GbaDecodeContract
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
    fun dreamstoneLoaderUsesItsFullSourceOwnedTextBackground() {
        val rom = control("DUALDEX_DREAMSTONE_ROM", DREAMSTONE_SHA)
        val tiles = decoded(rom, 0xE84DB8, DREAMSTONE_TILES_SHA)
        val tilemap = decoded(rom, 0xE843E8, DREAMSTONE_MAP_SHA)
        val palette = palette(rom, 0xE826E8, 32, DREAMSTONE_PALETTE_SHA)

        val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))

        assertEquals(GbaWorldMapFormat.TILED_8BPP_32X20, result.format)
        assertEquals(224, result.raster.width)
        assertEquals(120, result.raster.height)
        assertEquals(
            "0cc223adec5306d6cdce6bc584a66f882f283fe5064eff114784143fab8da5e8",
            sha256(result.raster.argb),
        )
        mapOf(
            (0 to 0) to 0xFF94B573.toInt(),
            (40 to 16) to 0xFF637B9C.toInt(),
            (111 to 60) to 0xFF639C5A.toInt(),
            (223 to 119) to 0xFF9CD6FF.toInt(),
        ).forEach { (position, expected) ->
            assertEquals(
                "pixel $position",
                expected,
                result.raster.argb[position.second * result.raster.width + position.first],
            )
        }
    }

    @Test
    fun dreamstoneFlyGraphicsCannotSatisfyThe8BppMap() {
        val rom = control("DUALDEX_DREAMSTONE_ROM", DREAMSTONE_SHA)
        val flyTiles = GbaRomCompression.decodeAt(
            rom,
            0xE846E8,
            GbaDecodeContract.WORLD_MAP,
        )
        val tilemap = decoded(rom, 0xE843E8, DREAMSTONE_MAP_SHA)
        val palette = palette(rom, 0xE826E8, 32, DREAMSTONE_PALETTE_SHA)

        assertEquals(7680, flyTiles.size)
        val result = GbaWorldMapCompositor.compose(flyTiles, tilemap, palette)
        assertTrue("expected uncovered 8bpp tile rejection, got $result", result is GbaWorldMapComposition.Rejected)
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
    fun darkCryLoaderRetainsFourExactRasterPlanesBeforeLocationBinding() {
        val rom = control("DUALDEX_DARK_CRY_ROM", DARK_CRY_SHA)
        val tiles = decoded(rom, 0x7680c1, DARK_CRY_TILES_SHA)
        val palette = palette(rom, 0x3ef2dc, 80, DARK_CRY_PALETTE_SHA)
        val expected = listOf(
            Triple(
                0x769161,
                DARK_CRY_SHORT_MAP_SHA,
                "bb44c69d073c93911dd47d6121b936e40174cb84ca5128a5d0912ea6981b36d7",
            ),
            Triple(
                0x3f0afc,
                "72c0b2615eaf061f5490779a3caf0470dbb88b08dd010c4887b8ed6d61eac124",
                "1933d3f93fc82dcfc0f7f5c5db82a9f98d264108ac3fb9f6da4aa46aa41c1d0d",
            ),
            Triple(
                0x3f0c0c,
                "cd68b8a70e21ac1f53344160234d1eabb7221040c62102c3a648faa550eb40db",
                "182c44baf94103874a3aa76867b6640d74bc6b0709cdd551bcd30ba358f2e4e6",
            ),
            Triple(
                0x3f0cf0,
                "2d0f7a665f88f15d28c213f7e490c232c3eeea8ffc12156aafce32499caa2400",
                "9aa8f8db6faf3d0317a5a3dececeac4fed923816f7b1ff105293111c129de19c",
            ),
        )

        assertEquals(
            expected.map { it.third },
            expected.map { (offset, mapSha, _) ->
                val tilemap = decoded(rom, offset, mapSha)
                val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))
                sha256(result.raster.argb)
            },
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
    fun loaderPaddedFrlgPlanesIgnoreOnlyTheAlignedSuffix() {
        val rom = control("DUALDEX_CLOVER_ROM", CLOVER_SHA)
        val tiles = decoded(rom, 0x6e883c, CLOVER_TILES_SHA)
        val palette = palette(rom, 0x3ef2dc, 80, CLOVER_PALETTE_SHA)
        val expected = listOf(
            Triple(0x2e3f28, "25afbba07dde3f6bdd79e4f0d58f0f87f0eb65f933cf7f24f88299e21651b65e", "50a41e7a72bddfb8812a99ff01ac3e26170d5ab2ca5ed179b885c7ad21ed0ebb"),
            Triple(0x2e41f4, "1a63c7ba062cb3a1697a0f5bd3367e156fc4a6e22ad416ec92b9d415383c91b7", "4cf81884ab3be1fd315555385dd719cab2b5d46880f17982a5fc3ceb5ba838da"),
            Triple(0x2e42f0, "bba3e926dd362fa9765e18fe0809eb20f97f5be2248f9397c7b65f5690140aac", "c79660d299bd1fb315c32cacfd21a41d10d76ef20fffacea67267609fb038bf2"),
            Triple(0x2e43c0, "3cca16b016e43718ec12831257b49967e62ce319af96bf9f32a9222b9292e37b", "11fef4f3fdbc027b99034f2389238b5d4c88938292dac94fded4c7ee45fcd08e"),
        )

        expected.forEach { (offset, mapHash, rasterHash) ->
            val tilemap = decoded(rom, offset, mapHash)
            val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))
            assertEquals(rasterHash, sha256(result.raster.argb))
        }
        val padded = decoded(rom, expected.first().first, expected.first().second)
        assertTrue(GbaWorldMapCompositor.compose(tiles, padded.copyOf(1218), palette) is GbaWorldMapComposition.Rejected)
        assertTrue(GbaWorldMapCompositor.compose(tiles, padded.copyOf(1264), palette) is GbaWorldMapComposition.Rejected)
    }

    @Test
    fun darkVioletPaddedPlanesRenderExactLoaderOrderedRasters() = assertPaddedTextControl(
        env = "DUALDEX_DARK_VIOLET_ROM",
        romSha = DARK_VIOLET_SHA,
        tilesOffset = 0x3ef61c,
        tilesSha = DARK_VIOLET_TILES_SHA,
        paletteOffset = 0x3ef2dc,
        paletteSha = DARK_VIOLET_PALETTE_SHA,
        maps = listOf(
            PaddedExpected(0xa1a4f8, "6fe122a8797614c976392afe7708ba9439972ee8106a7215952b979536bb0af8", "117e4d9c854ec0b80ab942dcd7f65d8e52d8826589e93fa88532a8ce60422118"),
            PaddedExpected(0x7fcdc8, "dce92a60b1d269913828c0c189de1a13b322539aa19294b62a3cba34cf2128ba", "da5db5e336b772d95b541a793b3d44a6dc6ce628e43f6077b65c430b024e4aa1"),
            PaddedExpected(0x3f0c0c, "cd68b8a70e21ac1f53344160234d1eabb7221040c62102c3a648faa550eb40db", "17a547a2ecec1d3f93abfd74f569f250a92f16e303f93c12c1311566538db0bf"),
            PaddedExpected(0x3f0cf0, "2d0f7a665f88f15d28c213f7e490c232c3eeea8ffc12156aafce32499caa2400", "fd9e4540d935e9756f5fe9c7c519a9c7cbc3920778e61a1df0c9737c511d6b3d"),
        ),
    )

    @Test
    fun darkWorshipPaddedPlaneRendersExactLoaderOrderedRasters() = assertPaddedTextControl(
        env = "DUALDEX_DARK_WORSHIP_ROM",
        romSha = DARK_WORSHIP_SHA,
        tilesOffset = 0xaf690c,
        tilesSha = DARK_WORSHIP_TILES_SHA,
        paletteOffset = 0x3ef2dc,
        paletteSha = DARK_WORSHIP_PALETTE_SHA,
        maps = listOf(
            PaddedExpected(0xac0454, "211c05cda89a2124c6c27949d3689edf10860e65f3f02330cc613d2ec42296f4", "55f12ce30d00015e28c278bd9b8a5eafaa392a0ae19947b504e4c637e58b2457"),
            PaddedExpected(0x3f0afc, "72c0b2615eaf061f5490779a3caf0470dbb88b08dd010c4887b8ed6d61eac124", "118b80b0aed6e7bbd80318230aced72dcb8c469634a87f5d34f57d36a3b83673"),
            PaddedExpected(0x3f0c0c, "cd68b8a70e21ac1f53344160234d1eabb7221040c62102c3a648faa550eb40db", "b56fd1bb7f986ec6c638f82b5f07bbe9f5b6654b495d7e680f1bc5ca061786e4"),
            PaddedExpected(0x3f0cf0, "2d0f7a665f88f15d28c213f7e490c232c3eeea8ffc12156aafce32499caa2400", "74811ed87bb54a05512ddf99c383b1b2d9b90fb3b1110547ddfeb9ec4ae79d8d"),
        ),
    )

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

    private fun assertPaddedTextControl(
        env: String,
        romSha: String,
        tilesOffset: Int,
        tilesSha: String,
        paletteOffset: Int,
        paletteSha: String,
        maps: List<PaddedExpected>,
    ) {
        val rom = control(env, romSha)
        val tiles = decoded(rom, tilesOffset, tilesSha)
        val palette = palette(rom, paletteOffset, 80, paletteSha)
        maps.forEach { expected ->
            val tilemap = decoded(rom, expected.offset, expected.mapSha)
            val result = resolved(GbaWorldMapCompositor.compose(tiles, tilemap, palette))
            assertEquals(expected.argbSha, sha256(result.raster.argb))
        }
    }

    private fun control(env: String, expectedSha: String): RomImage {
        val configured = System.getenv(env)
        assumeTrue("set $env to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(expectedSha, it.sha256) }
    }

    private fun decoded(rom: RomImage, offset: Int, expectedSha: String): ByteArray =
        GbaRomCompression.decodeAt(
            rom,
            offset,
            GbaDecodeContract.WORLD_MAP,
        ).also { assertEquals(expectedSha, sha256(it)) }

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

    private data class PaddedExpected(val offset: Int, val mapSha: String, val argbSha: String)

    private companion object {
        const val OFFICIAL_EMERALD_SHA = "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"
        const val MODERN_EMERALD_SHA = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895"
        const val CLASSIC_SHA = "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c"
        const val DREAMSTONE_SHA = "ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220"
        const val FIRERED_SHA = "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059"
        const val LEAFGREEN_SHA = "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825"
        const val DARK_CRY_SHA = "e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed"
        const val CLOVER_SHA = "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9"
        const val DARK_VIOLET_SHA = "6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21"
        const val DARK_WORSHIP_SHA = "930663704d1a84b93815d276703114e88785de94fcb3230d832ef07dc399f1d8"
        const val OFFICIAL_EMERALD_TILES_SHA = "7fab32a15049c96dd3d8eb9c0ef9ff969a0254c6d96c8538b4b104e8af13dd39"
        const val OFFICIAL_EMERALD_MAP_SHA = "dcf3d464dad11083ece52687184c89ab069c108340ecf5540eb0f14c6d8c8096"
        const val MODERN_EMERALD_TILES_SHA = "5828ca11400d78d81f09aad639ee481155eda63f923dc6ca1175ea6193367148"
        const val MODERN_EMERALD_MAP_SHA = "1627ca00f20c0a593ed30d4657cd165bdf92f31c30b4304464aed3e2688de873"
        const val CLASSIC_TILES_SHA = "ce2b7db0298fe504ec250092748c940649deb61ad342655480576aef34622de8"
        const val CLASSIC_MAP_SHA = "8675dbba552d2ca9f2179bf15597fa1ed1612a2a39faf54dda173b887d4836a1"
        const val DREAMSTONE_TILES_SHA = "5eca908524ca693e227ed449cad81775ec9e469f546052a357783f16a44a8dc3"
        const val DREAMSTONE_MAP_SHA = "d9494bb021eac071d380cb64b7f1b1858564c408969c917f4e081ba39aece47b"
        const val DREAMSTONE_PALETTE_SHA = "37d8bdae4a9d5e868d62ab007bcda0d2114867bd6f1e5dcc3f39a10f9b909b05"
        const val EMERALD_PALETTE_SHA = "795a5502910a4a8d226589bfd0d8c421111e30db3d152acaf66186e6659b4563"
        const val FRLG_TILES_SHA = "f9e8ddc403b2efcd9eaf87a8a1f16d9248f92d2372e42fb7aa88b09aed5fb3b4"
        const val FRLG_PALETTE_SHA = "116382eeea3b668f188e80eb49f7440b1daeb0732cd81da2401da887e1e0e227"
        const val DARK_CRY_TILES_SHA = "ee83cb51854bb3f67a88e43cd254ef34c4bf9239e439929432bbd8cd381a9547"
        const val DARK_CRY_SHORT_MAP_SHA = "6d330c519ae07ce7e8e09fd6dc30de980e2445c956496d87269a5db477a9b1cc"
        const val DARK_CRY_PALETTE_SHA = "e3ef03b01b555aa548511076cabe462f6a2d95cb668a5a4c2b9e4271ed18b060"
        const val CLOVER_TILES_SHA = "e5baeb6c709c6505c38f5232cbcad29bda8be1a9acb103e9d3634cb8c6b5671a"
        const val CLOVER_PALETTE_SHA = "ca3c2fe1cf1fcef24e37cb0fe36513ebe1f5328701ff26ec551a59cec750b835"
        const val DARK_VIOLET_TILES_SHA = "7eee4d0d02a9dba75a5b884139f3025cf2ff59d9f6c57ec0e111816865a130c6"
        const val DARK_VIOLET_PALETTE_SHA = "c61e97327587313eca268aba4a512aaaddf16c1df537aff20c55b29d6a4b7bb2"
        const val DARK_WORSHIP_TILES_SHA = "65955d16fe4058c532db1c11bb4604d7f538d6deb3764261a030c7018684ad87"
        const val DARK_WORSHIP_PALETTE_SHA = "da53a2920bc1e8999ffdf53786787adc6ec5575e285faed22a91f5a07321415d"
    }
}
