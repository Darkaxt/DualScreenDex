package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.parser.catalog.IndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapLightingPolicy
import com.enrpau.dualscreendex.parser.catalog.LocalMapRasterCodec
import com.enrpau.dualscreendex.parser.catalog.MapLightingPalettes
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import javax.imageio.ImageIO

class ServerContractTest {
    @Test
    fun bindsOnlyToLoopbackAndServesHealth() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val server = DualDexServer(DualDexRuntime(), root, 0)
        try {
            server.start()
            assertTrue(server.address.address.isLoopbackAddress)
            val connection = URI("http://127.0.0.1:${server.address.port}/api/health").toURL().openConnection() as HttpURLConnection
            assertEquals(200, connection.responseCode)
            assertTrue(connection.inputStream.reader().readText().contains("true"))

            val bootstrap = URI("http://127.0.0.1:${server.address.port}/api/bootstrap").toURL().readText()
            assertTrue(bootstrap.contains("\"battle\":null"))
            assertTrue(bootstrap.contains("\"selectedSpeciesId\":null"))

            val state = URI("http://127.0.0.1:${server.address.port}/api/state").toURL().readText()
            assertTrue(state.contains("\"battle\":null"))
            assertTrue(state.contains("\"selectedSpeciesId\":null"))
            assertTrue(!state.contains("<!doctype html>"))
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun servesCatalogOwnedLocalMapPngAssets() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val palettes = MapLightingPalettes(
            morning = IntArray(32) { 0xff110000.toInt() + it },
            day = IntArray(32) { 0xff220000.toInt() + it },
            night = IntArray(32) { 0xff330000.toInt() + it },
            dark = IntArray(32) { 0xff440000.toInt() + it },
        )
        val corruptIndices = LocalMapRasterCodec.compress(ByteArray(256))
        val runtime = DualDexRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                "sha256",
                EngineFamily.EMERALD,
                Platform.GBA,
                localMaps = LocalMapCatalog(
                    maps = listOf(
                        LocalMap("local/0010", "Route 101", 0x0010, 16, 16, 1, 1, "local/0010/map"),
                        LocalMap("local/0011", "Route 102", 0x0011, 16, 16, 1, 1, "local/0011/map"),
                        LocalMap("local/0012", "Corrupt", 0x0012, 16, 16, 1, 1, "local/0012/map"),
                    ),
                    assets = mapOf(
                        "local/0010/map" to PngMapAsset(
                            byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10),
                        ),
                    ),
                    indexedAssets = mapOf(
                        "local/0011/map" to IndexedMapAsset(
                            16,
                            16,
                            LocalMapRasterCodec.compress(ByteArray(256) { (it % 32).toByte() }),
                            LocalMapLightingPolicy.AUTO,
                            palettes,
                        ),
                        "local/0012/map" to IndexedMapAsset(
                            16,
                            16,
                            corruptIndices,
                            LocalMapLightingPolicy.AUTO,
                            palettes,
                        ),
                    ),
                ),
            ),
        )
        val server = DualDexServer(runtime, root, 0)
        try {
            corruptIndices.fill(0)
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            val map = URI("$base/api/maps/local%2F0010%2Fmap.png").toURL().openConnection() as HttpURLConnection

            assertEquals(200, map.responseCode)
            assertEquals("image/png", map.contentType)
            val staticBytes = map.inputStream.readBytes()
            assertTrue(staticBytes.copyOfRange(1, 4).contentEquals("PNG".toByteArray()))
            val staticNight = URI("$base/api/maps/local%2F0010%2Fmap.png?lighting=NIGHT")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(staticBytes.toList(), staticNight.inputStream.readBytes().toList())
            assertEquals(map.getHeaderField("ETag"), staticNight.getHeaderField("ETag"))

            val omitted = URI("$base/api/maps/local%2F0011%2Fmap.png").toURL().openConnection() as HttpURLConnection
            val day = URI("$base/api/maps/local%2F0011%2Fmap.png?lighting=DAY")
                .toURL().openConnection() as HttpURLConnection
            val night = URI("$base/api/maps/local%2F0011%2Fmap.png?lighting=NIGHT")
                .toURL().openConnection() as HttpURLConnection
            val omittedBytes = omitted.inputStream.readBytes()
            val dayBytes = day.inputStream.readBytes()
            val nightBytes = night.inputStream.readBytes()
            assertEquals(dayBytes.toList(), omittedBytes.toList())
            assertEquals(palettes.day[0], ImageIO.read(ByteArrayInputStream(dayBytes)).getRGB(0, 0))
            assertEquals(palettes.night[0], ImageIO.read(ByteArrayInputStream(nightBytes)).getRGB(0, 0))
            assertTrue(day.getHeaderField("ETag") != night.getHeaderField("ETag"))
            val invalid = URI("$base/api/maps/local%2F0011%2Fmap.png?lighting=INVALID")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(400, invalid.responseCode)
            val corrupt = URI("$base/api/maps/local%2F0012%2Fmap.png?lighting=DAY")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(404, corrupt.responseCode)

            val missing = URI("$base/api/maps/local%2Fmissing.png").toURL().openConnection() as HttpURLConnection
            assertEquals(404, missing.responseCode)
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun servesCopyableCatalogDiagnosticsWithoutRomBytes() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val runtime = DualDexRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog("sha256", EngineFamily.EMERALD, Platform.GBA, romCrc32 = "89ABCDEF"),
        )
        val server = DualDexServer(runtime, root, 0)
        try {
            server.start()
            val diagnostics = URI("http://127.0.0.1:${server.address.port}/api/diagnostics").toURL().readText()
            assertTrue(diagnostics.contains("\"romName\":\"fixture.gba\""))
            assertTrue(diagnostics.contains("\"sha256\":\"sha256\""))
            assertTrue(diagnostics.contains("\"crc32\":\"89ABCDEF\""))
            assertTrue(!diagnostics.contains("romBytes"))
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }
}
