package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WorldMapCatalogApiRealControlTest {
    @Test
    fun redSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[0])

    @Test
    fun blueSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[1])

    @Test
    fun yellowSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[2])

    @Test
    fun goldRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[3])

    @Test
    fun silverRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[4])

    @Test
    fun crystalRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[5])

    @Test
    fun officialEmeraldSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[6])

    @Test
    fun modernEmeraldSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[7])

    @Test
    fun classicSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[8])

    @Test
    fun fireRedFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[9])

    @Test
    fun leafGreenFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[10])

    @Test
    fun darkCryLocationBindingFailurePersistsAndExposesNoMapAssets() = assertNoMapRoundTrip(controls[11])

    @Test
    fun darkVioletFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[12])

    @Test
    fun cloverFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[13])

    private fun assertRoundTrip(control: Control) {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val romPath = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $romPath", Files.isRegularFile(romPath))
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(control.romSha256, rom.sha256)

        val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
        assertEquals(control.pngHashes.size, catalog.worldMaps.regions.size)
        assertEquals(
            control.regionKeys,
            catalog.worldMaps.regions.map { it.key },
        )

        val root = newRoot()
        var server: AndroidLoopbackServer? = null
        try {
            val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct(romPath.fileName.toString(), rom.size, "REAL-CONTROL"),
                CatalogWriteProgress.complete(),
            )
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            assertEquals(catalog.worldMaps, reopened.worldMaps)
            val parsedEvolutionEdges = catalog.navigableSpecies().associate { species ->
                species.id to species.evolutionEdges.value.orEmpty()
            }
            val reopenedEvolutionEdges = reopened.navigableSpecies().associate { species ->
                species.id to species.evolutionEdges.value.orEmpty()
            }
            assertEquals(parsedEvolutionEdges, reopenedEvolutionEdges)

            val runtime = ProductionCompanionRuntime().apply { loadCatalog(romPath.fileName.toString(), reopened) }
            assertEquals(
                reopenedEvolutionEdges.values.sumOf(List<*>::size),
                requireNotNull(runtime.bootstrap().catalog).species.sumOf { it.evolutions.size },
            )
            server = AndroidLoopbackServer(runtime) { null }.also { it.start() }
            val base = "http://127.0.0.1:${server.address.port}"
            val actualPngHashes = reopened.worldMaps.regions.map { region ->
                val key = URLEncoder.encode(region.imageAssetKey, StandardCharsets.UTF_8)
                val response = URI("$base/api/maps/$key.png").toURL().openConnection() as HttpURLConnection
                assertEquals(region.imageAssetKey, 200, response.responseCode)
                assertEquals(region.imageAssetKey, "image/png", response.contentType)
                val bytes = response.inputStream.use { it.readBytes() }
                assertTrue(region.imageAssetKey, bytes.copyOfRange(1, 4).contentEquals("PNG".toByteArray()))
                sha256(bytes)
            }
            assertEquals(control.pngHashes, actualPngHashes)
        } finally {
            server?.close()
            deleteTree(root)
        }
    }

    private fun assertNoMapRoundTrip(control: Control) {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val romPath = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $romPath", Files.isRegularFile(romPath))
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(control.romSha256, rom.sha256)
        assertTrue(control.regionKeys.isEmpty())
        assertTrue(control.pngHashes.isEmpty())

        val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
        val capability = catalog.capabilities.getValue(RomCapability.WORLD_MAP)
        assertEquals(CapabilityStatus.NOT_FOUND, capability.status)
        assertTrue(capability.reasons.contains("world-map stage: encounter-binding"))
        assertTrue(capability.reasons.contains("text-map region 3 retained no encounter binding"))
        assertTrue(catalog.worldMaps.regions.isEmpty())
        assertTrue(catalog.worldMaps.assets.isEmpty())

        val root = newRoot()
        var server: AndroidLoopbackServer? = null
        try {
            val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct(romPath.fileName.toString(), rom.size, "REAL-CONTROL"),
                CatalogWriteProgress.complete(),
            )
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            assertEquals(capability, reopened.capabilities.getValue(RomCapability.WORLD_MAP))
            assertTrue(reopened.worldMaps.regions.isEmpty())
            assertTrue(reopened.worldMaps.assets.isEmpty())

            val runtime = ProductionCompanionRuntime().apply { loadCatalog(romPath.fileName.toString(), reopened) }
            server = AndroidLoopbackServer(runtime) { null }.also { it.start() }
            val key = URLEncoder.encode("world/gen3-region-0", StandardCharsets.UTF_8)
            val response = URI("http://127.0.0.1:${server.address.port}/api/maps/$key.png")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(404, response.responseCode)
        } finally {
            server?.close()
            deleteTree(root)
        }
    }

    private fun newRoot(): Path {
        val configuredRoot = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.takeIf(String::isNotBlank)
        val parent = configuredRoot?.let(Path::of)
        if (parent != null) Files.createDirectories(parent)
        return if (parent == null) Files.createTempDirectory("dualdex-map-roundtrip-")
        else Files.createTempDirectory(parent, "dualdex-map-roundtrip-")
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class Control(
        val environmentVariable: String,
        val romSha256: String,
        val regionKeys: List<String>,
        val pngHashes: List<String>,
    )

    private companion object {
        val GEN2_PNGS = listOf(
            "23739bddf01b2c98a03ca1c4af28ade7d751623ec8063311dd2b8b366c81c516",
            "c06748683d60a89e4d2984bbcb565dc854ddd7942295d5039b80bcabe223258d",
        )
        val controls = listOf(
            Control(
                "DUALDEX_POKERED_ROM",
                "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEBLUE_ROM",
                "2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEYELLOW_ROM",
                "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEGOLD_ROM",
                "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_POKESILVER_ROM",
                "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_POKECRYSTAL_ROM",
                "d6702e353dcbe2d2c69183046c878ef13a0dae4006e8cdff521cca83dd1582fe",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_OFFICIAL_EMERALD_ROM",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                listOf("gen3-region-0"),
                listOf("c9d5f2a5c77c0df16c14c73a15577f0c6f4a05794c191ebe72ed5a24724aadc6"),
            ),
            Control(
                "DUALDEX_MODERN_EMERALD_ROM",
                "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
                listOf("gen3-region-0"),
                listOf("80c4a69b9372276818768123dcd7cad09bcced88720704c8f424bc4501931ffe"),
            ),
            Control(
                "DUALDEX_CLASSIC_ROM",
                "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
                listOf("gen3-region-0"),
                listOf("0c171c9fe8175629aa47de4e2854a334a2025f21b9196ba2f4c57a8cdcbc67ec"),
            ),
            Control(
                "DUALDEX_FIRERED_ROM",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa",
                    "5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70",
                    "d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160",
                    "2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765",
                ),
            ),
            Control(
                "DUALDEX_LEAFGREEN_ROM",
                "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa",
                    "5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70",
                    "d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160",
                    "2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765",
                ),
            ),
            Control(
                "DUALDEX_DARK_CRY_ROM",
                "e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed",
                emptyList(),
                emptyList(),
            ),
            Control(
                "DUALDEX_DARK_VIOLET_ROM",
                "6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "d5f07e96179d64e411ac4dec65c6b5d45fd190391b153a67c0a12927ab0a63bb",
                    "5a5da685c0211d1639f9de29c0749239db8ed22aa819b249e23bb940fa43c32c",
                    "e46976338b3b08670f1c2e846100a58bfc7f337ba92a0f989024aa357b0f8778",
                    "d7a86d7147422ba4dc09e72e14a1ba8c5bc3f2feafcb6e78cf4aac7875c7a68a",
                ),
            ),
            Control(
                "DUALDEX_CLOVER_ROM",
                "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "66ec72ca90e7220017cad597c5cc6be2c4901d214d467cd9c9749a1804da748a",
                    "0906e9ede556e27e166ebcd7610e3b09af40fd86cbd3066427a3d44eb95324bc",
                    "f8c5f9d281dd36609b1a62c256c777dcae3a105cb1d8eaa7052acdde728381bd",
                    "1b898a83a7cd7d677ea1aec26b6f9f5bece1dae2fc5096f5cfb2e8ab04cc34ac",
                ),
            ),
        )
    }
}

private object JdbcTestCatalogDatabaseFactory : CatalogDatabaseFactory {
    override fun open(file: File): CatalogDatabase {
        Class.forName("org.sqlite.JDBC")
        return JdbcTestCatalogDatabase(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"))
    }
}

private class JdbcTestCatalogDatabase(private val connection: Connection) : CatalogDatabase {
    override fun <T> transaction(block: () -> T): T {
        val original = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            connection.autoCommit = original
        }
    }

    override fun execute(sql: String, arguments: List<Any?>) {
        connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    override fun <T> query(sql: String, arguments: List<Any?>, map: (CatalogRow) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(map(object : CatalogRow {
                            override fun string(column: String): String? = result.getString(column)
                            override fun long(column: String): Long? = result.getLong(column).takeUnless { result.wasNull() }
                            override fun bytes(column: String): ByteArray? = result.getBytes(column)
                        }))
                    }
                }
            }
        }

    override fun close() = connection.close()
}
