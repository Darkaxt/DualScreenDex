package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class CatalogWorldMapMaterializationRealControlTest {
    @Test
    fun officialEmeraldCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_OFFICIAL_EMERALD_ROM",
        "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
        listOf("1c3a1bf13c851dcc707f1f3f71c8f90e703a0faf0832917a0195618952a77aab"),
    )

    @Test
    fun modernEmeraldCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_MODERN_EMERALD_ROM",
        "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
        listOf("0163d9b5e747d788db925776c25a087a1cc4bbfa34fd3e021580aa8756717fb0"),
    )

    @Test
    fun classicCatalogMaterializesTheExactNormalizedMap() = assertCatalog(
        "DUALDEX_CLASSIC_ROM",
        "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
        listOf("dc326776034d066f0b2691e14f2325e78d6761b40db6da52c8454ab8fe46a46f"),
    )

    @Test
    fun fireRedCatalogMaterializesFourExactNormalizedMaps() = assertCatalog(
        "DUALDEX_FIRERED_ROM",
        "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
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
        listOf(
            "250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654",
            "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135",
            "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a",
            "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b",
        ),
    )

    private fun assertCatalog(environmentVariable: String, expectedRomSha: String, expectedArgb: List<String>) {
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
}
