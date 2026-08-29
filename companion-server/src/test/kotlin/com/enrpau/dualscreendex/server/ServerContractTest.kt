package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.IndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapLightingPolicy
import com.enrpau.dualscreendex.parser.catalog.LocalMapRasterCodec
import com.enrpau.dualscreendex.parser.catalog.MapLightingPalettes
import com.enrpau.dualscreendex.parser.catalog.MapTimeBlend
import com.enrpau.dualscreendex.parser.catalog.MapTimePaletteModel
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TimedIndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RenderedMapAsset
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    fun returnsNoContentOnlyWhenTheClientHasTheCurrentStateVersion() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val runtime = DualDexRuntime()
        val server = DualDexServer(runtime, root, 0)
        try {
            server.start()
            val currentVersion = runtime.stateView().version
            val unchanged = get(server, "/api/state?sinceVersion=$currentVersion")

            assertEquals(204, unchanged.responseCode)
            assertNull(unchanged.contentType)
            assertEquals("no-store", unchanged.getHeaderField("Cache-Control"))
            assertTrue(unchanged.inputStream.readBytes().isEmpty())
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun returnsCurrentStateWhenTheClientVersionIsAheadAfterAServerReset() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val runtime = DualDexRuntime()
        val server = DualDexServer(runtime, root, 0)
        try {
            server.start()
            val currentVersion = runtime.stateView().version
            val reset = get(server, "/api/state?sinceVersion=${currentVersion + 1}")

            assertEquals(200, reset.responseCode)
            assertTrue(reset.inputStream.reader().readText().contains("\"version\":$currentVersion"))
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun rejectsInvalidStateVersionsWithTheSharedJsonErrorEnvelope() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val server = DualDexServer(DualDexRuntime(), root, 0)
        try {
            server.start()

            assertApiError(get(server, "/api/state?sinceVersion=invalid"), 400, "INVALID_REQUEST", retryable = false)
            assertApiError(get(server, "/api/state?sinceVersion=-1"), 400, "INVALID_REQUEST", retryable = false)
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun usesTheSharedJsonErrorEnvelopeForDesktopApiFailures() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val server = DualDexServer(DualDexRuntime(), root, 0)
        try {
            server.start()

            assertApiError(get(server, "/api/missing"), 404, "NOT_FOUND", retryable = false)
            assertApiError(
                get(server, "/api/missing").apply { requestMethod = "DELETE" },
                404,
                "NOT_FOUND",
                retryable = false,
            )
            assertApiError(
                get(server, "/api/sprites/species/not-a-species.png"),
                404,
                "NOT_FOUND",
                retryable = false,
            )
            assertApiError(
                get(server, "/api/sprites/species/1.jpg"),
                404,
                "NOT_FOUND",
                retryable = false,
            )
            val wrongMethod = get(server, "/api/health").apply { requestMethod = "POST" }
            assertApiError(wrongMethod, 405, "METHOD_NOT_ALLOWED", retryable = false)
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun sanitizesUnexpectedDesktopApiFailures() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val runtime = DualDexRuntime()
        val gateway = DualDexRuntime::class.java.getDeclaredField("gateway")
        gateway.isAccessible = true
        gateway.set(runtime, null)
        val server = DualDexServer(runtime, root, 0)
        try {
            server.start()

            assertApiError(get(server, "/api/bootstrap"), 500, "INTERNAL_ERROR", retryable = true)
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun reportsTemporarilyUnavailableDesktopApiWorkAsRetryable() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val runtime = DualDexRuntime().also(DualDexRuntime::close)
        val server = DualDexServer(runtime, root, 0)
        try {
            server.start()
            val unavailable = get(server, "/api/load?name=fixture.gb").apply {
                requestMethod = "POST"
                doOutput = true
                outputStream.use { it.write(byteArrayOf(0)) }
            }

            assertApiError(unavailable, 503, "SERVER_BUSY", retryable = true)
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun returnsStructuredBusyWhenTheDesktopExecutorQueueIsSaturatedAndLaterBootstraps() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val server = DualDexServer(DualDexRuntime(), root, 0)
        val executorField = DualDexServer::class.java.getDeclaredField("requestExecutor")
        executorField.isAccessible = true
        val executor = executorField.get(server) as java.util.concurrent.ThreadPoolExecutor
        val workersStarted = java.util.concurrent.CountDownLatch(executor.corePoolSize)
        val release = java.util.concurrent.CountDownLatch(1)
        val blockers = buildList {
            repeat(executor.corePoolSize) {
                add(executor.submit {
                    workersStarted.countDown()
                    assertTrue(release.await(1, java.util.concurrent.TimeUnit.SECONDS))
                })
            }
            assertTrue(workersStarted.await(1, java.util.concurrent.TimeUnit.SECONDS))
            repeat(executor.queue.remainingCapacity()) {
                add(executor.submit {
                    assertTrue(release.await(1, java.util.concurrent.TimeUnit.SECONDS))
                })
            }
        }
        try {
            assertEquals(0, executor.queue.remainingCapacity())
            server.start()

            assertApiError(get(server, "/api/bootstrap"), 503, "SERVER_BUSY", retryable = true)
            assertEquals(503, get(server, "/index.html").responseCode)

            release.countDown()
            blockers.forEach { it.get(1, java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(200, get(server, "/api/bootstrap").responseCode)
        } finally {
            release.countDown()
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun fallsBackToTheSpaOnlyForExtensionlessHtmlNavigation() {
        val root = Files.createTempDirectory("dualdex-web-test")
        Files.writeString(root.resolve("index.html"), "<!doctype html><title>DualDex</title>")
        Files.writeString(root.resolve("app.js"), "export const ready = true")
        val server = DualDexServer(DualDexRuntime(), root, 0)
        try {
            server.start()

            val navigation = get(server, "/party/member/1", accept = "text/html")
            assertEquals(200, navigation.responseCode)
            assertEquals("text/html; charset=utf-8", navigation.contentType)
            assertEquals("no-cache", navigation.getHeaderField("Cache-Control"))
            assertTrue(navigation.inputStream.reader().readText().contains("DualDex"))

            val nonNavigation = get(server, "/party/member/1", accept = "application/json")
            assertEquals(404, nonNavigation.responseCode)
            assertFalse(nonNavigation.errorStream.reader().readText().contains("<!doctype html>"))

            val script = get(server, "/app.js")
            assertEquals(200, script.responseCode)
            assertEquals("text/javascript; charset=utf-8", script.contentType)
            assertEquals("no-cache", script.getHeaderField("Cache-Control"))
            assertTrue(script.inputStream.reader().readText().contains("ready"))

            val encodedAsset = get(server, "/missing%2Ejs", accept = "text/html")
            assertEquals(404, encodedAsset.responseCode)
            assertEquals("no-cache", encodedAsset.getHeaderField("Cache-Control"))
            assertFalse(encodedAsset.errorStream.reader().readText().contains("<!doctype html>"))

            listOf("/missing.js", "/missing.css", "/missing.png", "/missing.svg", "/missing.map").forEach { path ->
                val missing = get(server, path, accept = "*/*")
                assertEquals(path, 404, missing.responseCode)
                assertEquals(path, "no-cache", missing.getHeaderField("Cache-Control"))
                assertFalse(missing.errorStream.reader().readText().contains("<!doctype html>"))
            }
        } finally {
            server.close()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun requiresCatalogMediaToRevalidateInsteadOfCachingImmutableUrls() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val runtime = DualDexRuntime().apply { loadCatalog("fixture.gba", mediaCatalog()) }
        val server = DualDexServer(runtime, root, 0)
        try {
            server.start()

            val sprite = get(server, "/api/sprites/species/1.png")
            assertEquals(200, sprite.responseCode)
            assertEquals("no-cache", sprite.getHeaderField("Cache-Control"))
            assertFalse(sprite.getHeaderField("Cache-Control").contains("immutable"))
            val map = get(server, "/api/maps/local%2F0001%2Fmap.png")
            assertEquals(200, map.responseCode)
            assertEquals("no-cache", map.getHeaderField("Cache-Control"))
            assertFalse(map.getHeaderField("Cache-Control").contains("immutable"))
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun rejectsOversizedActionBodiesAndKeepsTheDesktopBootstrapReachable() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val server = DualDexServer(DualDexRuntime(), root, 0)
        try {
            server.start()
            val oversized = post(
                server,
                "/api/actions",
                "{\"type\":\"END_BATTLE\",\"padding\":\"${"x".repeat(1_048_576)}\"}",
            )

            assertApiError(oversized, 400, "INVALID_REQUEST", retryable = false)
            val bootstrap = get(server, "/api/bootstrap")
            assertEquals(200, bootstrap.responseCode)
            assertTrue(bootstrap.inputStream.reader().readText().contains("\"mapperAvailable\":false"))
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun materializationFailuresFromDesktopUploadsAreSanitizedAndRetryableThroughLoad() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val attempts = AtomicInteger()
        val runtime = DualDexRuntime(
            catalogParser = { _, _, _ -> ParsedCatalog("recovered", EngineFamily.EMERALD, Platform.GBA) },
            romSourceLoader = { name, _ ->
                if (attempts.getAndIncrement() == 0) throw OutOfMemoryError("uploaded source detail")
                LoadedRom(name, RomImage(byteArrayOf()))
            },
        )
        val server = DualDexServer(runtime, root, 0)
        try {
            server.start()
            val failed = post(server, "/api/load?name=untrusted.gba", "x")

            assertEquals(200, failed.responseCode)
            val failedBody = failed.inputStream.reader().readText()
            assertTrue(failedBody.contains("\"phase\":\"FAILED\""))
            assertTrue(failedBody.contains("This game guide could not be opened. You can try again."))
            assertFalse(failedBody.contains("uploaded source detail"))

            val retry = post(server, "/api/load?name=valid.gba", "x")
            assertEquals(200, retry.responseCode)
            assertTrue(retry.inputStream.reader().readText().contains("\"mapperAvailable\":false"))
            assertEquals(200, get(server, "/api/bootstrap").responseCode)
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun distinguishesMissingMapsFromRetryableRendererFailuresAndRecovers() {
        val root = Files.createTempDirectory("dualdex-web-test")
        val rendererFails = AtomicBoolean(true)
        val runtime = DualDexRuntime(
            mapAssetRenderer = { _, key, _, _ ->
                when (key) {
                    "missing" -> null
                    else -> {
                        if (rendererFails.get()) throw OutOfMemoryError("renderer allocator detail")
                        RenderedMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), null)
                    }
                }
            },
        ).apply {
            loadCatalog("fixture.gba", ParsedCatalog("map-catalog", EngineFamily.EMERALD, Platform.GBA))
        }
        val server = DualDexServer(runtime, root, 0)
        try {
            server.start()

            assertApiError(get(server, "/api/maps/missing.png"), 404, "NOT_FOUND", retryable = false)
            val unavailable = get(server, "/api/maps/rendered.png")
            assertMapUnavailable(unavailable, "OutOfMemoryError")
            assertEquals(200, get(server, "/api/bootstrap").responseCode)

            rendererFails.set(false)
            val recovered = get(server, "/api/maps/rendered.png")
            assertEquals(200, recovered.responseCode)
            assertEquals("image/png", recovered.contentType)
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun conflatingEventSlotRetainsOnlyTheLatestPendingState() {
        val slot = ConflatingSlot<String>()

        slot.replace("version-1")
        slot.replace("version-2")

        assertEquals("version-2", slot.take())
    }

    @Test
    fun closingAnEventSlotUnblocksAnIdleDesktopEventClient() {
        val slot = ConflatingSlot<String>()
        val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val pending = reader.submit<String?> { slot.take() }

            slot.close()

            assertNull(pending.get(1, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            reader.shutdownNow()
        }
    }

    @Test
    fun forcesBlockedDesktopEventWritesClosedAtTheDeadline() {
        val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val closed = java.util.concurrent.CountDownLatch(1)
            val releaseWrite = java.util.concurrent.CountDownLatch(1)
            val deadline = WriteDeadline(scheduler, 50)
            val pending = writer.submit {
                deadline.run(
                    onDeadline = {
                        closed.countDown()
                        releaseWrite.countDown()
                    },
                    action = { releaseWrite.await() },
                )
            }

            assertTrue(closed.await(1, java.util.concurrent.TimeUnit.SECONDS))
            pending.get(1, java.util.concurrent.TimeUnit.SECONDS)
        } finally {
            writer.shutdownNow()
            scheduler.shutdownNow()
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
                        LocalMap("local/0013", "Timed", 0x0013, 16, 16, 1, 1, "local/0013/map"),
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
                    timedAssets = mapOf(
                        "local/0013/map" to timedAsset(),
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
            assertMapUnavailable(corrupt, "IllegalArgumentException")

            val noon = URI("$base/api/maps/local%2F0013%2Fmap.png?hour=12&minute=0")
                .toURL().openConnection() as HttpURLConnection
            val lateNight = URI("$base/api/maps/local%2F0013%2Fmap.png?hour=23&minute=0")
                .toURL().openConnection() as HttpURLConnection
            assertTrue(noon.inputStream.readBytes().toList() != lateNight.inputStream.readBytes().toList())
            assertTrue(noon.getHeaderField("ETag") != lateNight.getHeaderField("ETag"))
            val incompleteTime = URI("$base/api/maps/local%2F0013%2Fmap.png?hour=12")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(400, incompleteTime.responseCode)

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

    private val networkTimeoutMillis = 1_000

    private fun get(
        server: DualDexServer,
        path: String,
        accept: String? = null,
    ): HttpURLConnection = URI("http://127.0.0.1:${server.address.port}$path")
        .toURL().openConnection().let { it as HttpURLConnection }
        .apply {
            connectTimeout = networkTimeoutMillis
            readTimeout = networkTimeoutMillis
            accept?.let { setRequestProperty("Accept", it) }
        }

    private fun post(server: DualDexServer, path: String, body: String): HttpURLConnection = get(server, path).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        val bytes = body.toByteArray(Charsets.UTF_8)
        setFixedLengthStreamingMode(bytes.size)
        outputStream.use { it.write(bytes) }
    }

    private fun assertApiError(
        connection: HttpURLConnection,
        status: Int,
        code: String,
        retryable: Boolean,
    ) {
        assertEquals(status, connection.responseCode)
        assertEquals("application/json; charset=utf-8", connection.contentType)
        assertEquals("no-store", connection.getHeaderField("Cache-Control"))
        val body = connection.errorStream.reader().readText()
        val root = JsonParser.parseString(body).asJsonObject
        assertEquals(setOf("error"), root.keySet())
        val error = root.getAsJsonObject("error")
        assertEquals(setOf("code", "message", "retryable"), error.keySet())
        assertEquals(code, error.get("code").asString)
        assertEquals(
            when (code) {
                "INVALID_REQUEST" -> "The request was invalid."
                "NOT_FOUND" -> "The requested resource was not found."
                "METHOD_NOT_ALLOWED" -> "The request method is not allowed."
                "INTERNAL_ERROR" -> "The server could not complete the request."
                "SERVER_BUSY" -> "The server is busy. Try again."
                else -> error("unexpected API error code: $code")
            },
            error.get("message").asString,
        )
        assertEquals(retryable, error.get("retryable").asBoolean)
        assertFalse(body.contains("Exception", ignoreCase = true))
    }

    private fun assertMapUnavailable(connection: HttpURLConnection, diagnostic: String) {
        assertEquals(503, connection.responseCode)
        assertEquals("application/json; charset=utf-8", connection.contentType)
        assertEquals("no-store", connection.getHeaderField("Cache-Control"))
        val body = connection.errorStream.reader().readText()
        val root = JsonParser.parseString(body).asJsonObject
        val error = root.getAsJsonObject("error")
        assertEquals("MAP_UNAVAILABLE", error.get("code").asString)
        assertEquals("The map is temporarily unavailable. Try again.", error.get("message").asString)
        assertTrue(error.get("retryable").asBoolean)
        assertEquals(diagnostic, root.get("diagnostic").asString)
        assertTrue(root.get("diagnostic").asString.length <= 64)
        assertFalse(body.contains("allocator detail"))
    }

    private fun mediaCatalog(): ParsedCatalog {
        val mapBytes = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val species = SpeciesRecord(
            id = 1,
            dexNumber = CatalogField.available(1),
            name = CatalogField.available("SPECIES"),
            typeIds = CatalogField.available(listOf(1)),
            baseStats = CatalogField.available(BaseStats(50, 50, 50, 50, 50, 50)),
            sprite = CatalogField.available(RgbaSprite(1, 1, intArrayOf(0xFFFFFFFF.toInt()))),
        )
        return ParsedCatalog(
            "media-hash",
            EngineFamily.EMERALD,
            Platform.GBA,
            speciesById = mapOf(1 to species),
            localMaps = LocalMapCatalog(
                maps = listOf(LocalMap("local/0001", "Fixture", 1, 16, 16, 1, 1, "local/0001/map")),
                assets = mapOf("local/0001/map" to PngMapAsset(mapBytes)),
            ),
        )
    }

    private fun timedAsset(): TimedIndexedMapAsset {
        val base = IntArray(256).also { it[17] = 0x001F }
        val alternate = base.copyOf().also { it[17] = 0x7C00 }
        return TimedIndexedMapAsset(
            pixelWidth = 16,
            pixelHeight = 16,
            compressedIndices = LocalMapRasterCodec.compress(ByteArray(256) { 17 }),
            baseColors = base,
            alternateColors = alternate,
            alternatePaletteMask = 1 shl 1,
            paletteModel = MapTimePaletteModel(
                night = MapTimeBlend(0x808080, tint = true, coefficient = 10),
                twilight = MapTimeBlend(0xA8B0E0, tint = true, coefficient = 4),
                day = MapTimeBlend(0, tint = false, coefficient = 0),
            ),
        )
    }
}
