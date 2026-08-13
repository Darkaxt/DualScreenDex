package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomImage
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
    fun officialEmeraldSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[0])

    @Test
    fun modernEmeraldSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[1])

    @Test
    fun classicSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[2])

    @Test
    fun fireRedFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[3])

    @Test
    fun leafGreenFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[4])

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
            (0 until control.pngHashes.size).map { "gen3-region-$it" },
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

            val runtime = ProductionCompanionRuntime().apply { loadCatalog(romPath.fileName.toString(), reopened) }
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
        val pngHashes: List<String>,
    )

    private companion object {
        val controls = listOf(
            Control(
                "DUALDEX_OFFICIAL_EMERALD_ROM",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                listOf("c9d5f2a5c77c0df16c14c73a15577f0c6f4a05794c191ebe72ed5a24724aadc6"),
            ),
            Control(
                "DUALDEX_MODERN_EMERALD_ROM",
                "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
                listOf("80c4a69b9372276818768123dcd7cad09bcced88720704c8f424bc4501931ffe"),
            ),
            Control(
                "DUALDEX_CLASSIC_ROM",
                "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
                listOf("0c171c9fe8175629aa47de4e2854a334a2025f21b9196ba2f4c57a8cdcbc67ec"),
            ),
            Control(
                "DUALDEX_FIRERED_ROM",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
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
                listOf(
                    "c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa",
                    "5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70",
                    "d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160",
                    "2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765",
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
