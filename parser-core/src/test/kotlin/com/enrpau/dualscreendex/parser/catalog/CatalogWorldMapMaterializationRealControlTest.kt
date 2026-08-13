package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CatalogWorldMapMaterializationRealControlTest {
    @Test
    fun redCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_POKERED_ROM",
        "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
        listOf("gen1-kanto"),
        listOf("d55384218790ed7744af655bef486bcba8b1a932aa81e3d5701871f8ac60eca4"),
    )

    @Test
    fun blueCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_POKEBLUE_ROM",
        "2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d",
        listOf("gen1-kanto"),
        listOf("d55384218790ed7744af655bef486bcba8b1a932aa81e3d5701871f8ac60eca4"),
    )

    @Test
    fun yellowCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_POKEYELLOW_ROM",
        "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
        listOf("gen1-kanto"),
        listOf("d55384218790ed7744af655bef486bcba8b1a932aa81e3d5701871f8ac60eca4"),
    )

    @Test
    fun goldCatalogMaterializesTheTwoExactNormalizedMaps() = assertCatalog(
        "DUALDEX_POKEGOLD_ROM",
        "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
        listOf("gen2-johto", "gen2-kanto"),
        GEN2_RASTERS,
    )

    @Test
    fun silverCatalogMaterializesTheTwoExactNormalizedMaps() = assertCatalog(
        "DUALDEX_POKESILVER_ROM",
        "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
        listOf("gen2-johto", "gen2-kanto"),
        GEN2_RASTERS,
    )

    @Test
    fun crystalCatalogMaterializesTheTwoExactNormalizedMaps() = assertCatalog(
        "DUALDEX_POKECRYSTAL_ROM",
        "d6702e353dcbe2d2c69183046c878ef13a0dae4006e8cdff521cca83dd1582fe",
        listOf("gen2-johto", "gen2-kanto"),
        GEN2_RASTERS,
    )

    @Test
    fun officialEmeraldCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_OFFICIAL_EMERALD_ROM",
        "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
        listOf("gen3-region-0"),
        listOf("1c3a1bf13c851dcc707f1f3f71c8f90e703a0faf0832917a0195618952a77aab"),
    )

    @Test
    fun modernEmeraldCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_MODERN_EMERALD_ROM",
        "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
        listOf("gen3-region-0"),
        listOf("0163d9b5e747d788db925776c25a087a1cc4bbfa34fd3e021580aa8756717fb0"),
    )

    @Test
    fun classicCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_CLASSIC_ROM",
        "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
        listOf("gen3-region-0"),
        listOf("dc326776034d066f0b2691e14f2325e78d6761b40db6da52c8454ab8fe46a46f"),
    )

    @Test
    fun fireRedCatalogMaterializesFourExactNormalizedMaps() = assertCatalog(
        "DUALDEX_FIRERED_ROM",
        "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
        (0..3).map { "gen3-region-$it" },
        listOf(
            "250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654",
            "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135",
            "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a",
            "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b",
        ),
    )

    @Test
    fun leafGreenCatalogMaterializesFourExactNormalizedMaps() = assertCatalog(
        "DUALDEX_LEAFGREEN_ROM",
        "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
        (0..3).map { "gen3-region-$it" },
        listOf(
            "250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654",
            "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135",
            "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a",
            "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b",
        ),
    )

    @Test
    fun darkCryCatalogMaterializesFourExactNormalizedMaps() = assertCatalog(
        "DUALDEX_DARK_CRY_ROM",
        "e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed",
        (0..3).map { "gen3-region-$it" },
        listOf(
            "bb44c69d073c93911dd47d6121b936e40174cb84ca5128a5d0912ea6981b36d7",
            "1933d3f93fc82dcfc0f7f5c5db82a9f98d264108ac3fb9f6da4aa46aa41c1d0d",
            "182c44baf94103874a3aa76867b6640d74bc6b0709cdd551bcd30ba358f2e4e6",
            "9aa8f8db6faf3d0317a5a3dececeac4fed923816f7b1ff105293111c129de19c",
        ),
    )

    @Test
    fun cloverPaddedAssetsReachTheTypedSemanticJoin() = assertTypedSemanticJoin(
        "DUALDEX_CLOVER_ROM",
        "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
    )

    @Test
    fun darkVioletPaddedAssetsReachTheTypedSemanticJoin() = assertTypedSemanticJoin(
        "DUALDEX_DARK_VIOLET_ROM",
        "6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21",
    )

    @Test
    fun darkWorshipPaddedAssetsReachTheTypedSemanticJoin() = assertTypedSemanticJoin(
        "DUALDEX_DARK_WORSHIP_ROM",
        "930663704d1a84b93815d276703114e88785de94fcb3230d832ef07dc399f1d8",
    )

    private fun assertCatalog(
        environmentVariable: String,
        expectedRomSha: String,
        expectedRegionKeys: List<String>,
        expectedArgb: List<String>,
    ) {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedRomSha, rom.sha256)

        val parsed = CatalogParser.parse(rom)
        val catalog = requireNotNull(parsed.catalog)

        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.WORLD_MAP).status)
        assertEquals(expectedArgb.size, catalog.worldMaps.regions.size)
        assertEquals(expectedRegionKeys, catalog.worldMaps.regions.map { it.key })
        assertEquals(
            expectedArgb,
            catalog.worldMaps.regions.map { region ->
                val raster = catalog.worldMaps.assets.getValue(region.imageAssetKey)
                val digest = MessageDigest.getInstance("SHA-256")
                raster.argb.forEach { value ->
                    digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
                }
                digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            },
        )
    }

    private fun assertTypedSemanticJoin(environmentVariable: String, expectedRomSha: String) {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedRomSha, rom.sha256)

        val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
        val capability = catalog.capabilities.getValue(RomCapability.WORLD_MAP)
        assertEquals(CapabilityStatus.NOT_FOUND, capability.status)
        assertTrue(capability.reasons.contains("world-map stage: map-header-join"))
        assertTrue(capability.reasons.any { "semantic section join" in it })
        assertTrue(catalog.worldMaps.regions.isEmpty())
        assertTrue(catalog.worldMaps.assets.isEmpty())
    }

    private companion object {
        val GEN2_RASTERS = listOf(
            "adb9cefb64aece67c7cff271b70183af5dafa7c3e95beffd31436a7cab79a5e9",
            "c53b3c2e032545fa2452bbadd4a29aea8619cc852b9ed45d17d6d8475cebe5b7",
        )
    }
}
