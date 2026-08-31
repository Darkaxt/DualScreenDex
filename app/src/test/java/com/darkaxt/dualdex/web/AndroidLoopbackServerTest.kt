package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import com.darkaxt.dualdex.save.SaveSnapshot
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CaptureBallRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.IndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapLightingPolicy
import com.enrpau.dualscreendex.parser.catalog.LocalMapRasterCodec
import com.enrpau.dualscreendex.parser.catalog.MapLightingPalettes
import com.enrpau.dualscreendex.parser.catalog.MapTimeBlend
import com.enrpau.dualscreendex.parser.catalog.MapTimePaletteModel
import com.enrpau.dualscreendex.parser.catalog.TimedIndexedMapAsset
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.RenderedMapAsset
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class AndroidLoopbackServerTest {
    @Test
    fun rejectsTheNinthSimultaneousConnectionWithoutCreatingAnotherWorker() {
        val server = AndroidLoopbackServer(ProductionCompanionRuntime()) { null }
        val occupied = mutableListOf<Socket>()
        try {
            server.start()
            repeat(8) {
                occupied += Socket(AndroidLoopbackServer.LOOPBACK_HOST, server.address.port).apply {
                    getOutputStream().write("GET /api/health HTTP/1.1\r\n".toByteArray(Charsets.US_ASCII))
                    getOutputStream().flush()
                }
            }
            val excess = Socket(AndroidLoopbackServer.LOOPBACK_HOST, server.address.port)
            excess.use {
                it.getOutputStream().write("GET /api/health HTTP/1.1\r\n\r\n".toByteArray(Charsets.US_ASCII))
                it.getOutputStream().flush()

                assertRawApiError(
                    it.getInputStream().readBytes().toString(Charsets.UTF_8),
                    status = 503,
                    code = "SERVER_BUSY",
                    retryable = true,
                )
            }
            val capacity = server.capacitySnapshot()
            assertTrue(capacity.workerThreads <= 4)
            assertTrue(capacity.activeConnections <= 8)
        } finally {
            occupied.forEach { runCatching { it.close() } }
            server.close()
        }
    }

    @Test
    fun timesOutFourPartialRequestsAndRecoversForBootstrap() {
        val server = AndroidLoopbackServer(
            ProductionCompanionRuntime(),
            requestReadTimeoutMillis = 250,
            requestLifetimeMillis = 1_000,
            responseWriteTimeoutMillis = 1_000,
            assetLoader = { null },
        )
        val partial = mutableListOf<Socket>()
        try {
            server.start()
            repeat(4) {
                partial += Socket(AndroidLoopbackServer.LOOPBACK_HOST, server.address.port).apply {
                    soTimeout = 5_000
                    getOutputStream().write(
                        "GET /api/bootstrap HTTP/1.1\r\nHost: 127.0.0.1\r\n".toByteArray(Charsets.US_ASCII),
                    )
                    getOutputStream().flush()
                }
            }

            partial.forEach { client ->
                assertRawApiError(
                    client.getInputStream().readBytes().toString(Charsets.UTF_8),
                    status = 400,
                    code = "REQUEST_TIMEOUT",
                    retryable = true,
                )
            }
            val bootstrap = URI("http://127.0.0.1:${server.address.port}/api/bootstrap")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(200, bootstrap.responseCode)
            assertTrue(bootstrap.inputStream.reader().readText().contains("\"state\""))
        } finally {
            partial.forEach { runCatching { it.close() } }
            server.close()
        }
    }

    @Test
    fun closeInterruptsAClientBlockedInRequestHeaders() {
        val server = AndroidLoopbackServer(ProductionCompanionRuntime()) { null }
        val client = Socket()
        var serverClosed = false
        try {
            server.start()
            client.connect(InetSocketAddress(AndroidLoopbackServer.LOOPBACK_HOST, server.address.port))
            client.soTimeout = 1_000
            client.getOutputStream().write("GET /api/health HTTP/1.1\r\n".toByteArray(Charsets.US_ASCII))
            client.getOutputStream().flush()
            assertTrue(waitUntil(1_000) { server.capacitySnapshot().activeConnections == 1 })

            server.close()
            serverClosed = true

            val result = runCatching { client.getInputStream().read() }
            assertFalse(result.exceptionOrNull() is SocketTimeoutException)
            assertTrue(result.getOrNull() == -1 || result.exceptionOrNull() != null)
        } finally {
            runCatching { client.close() }
            if (!serverClosed) server.close()
        }
    }

    @Test
    fun boundsHeaderCountAndAggregateHeaderBytesWithoutStoppingTheServer() {
        val server = AndroidLoopbackServer(ProductionCompanionRuntime()) { null }
        try {
            server.start()
            val tooManyHeaders = buildString {
                append("GET /api/health HTTP/1.1\r\n")
                repeat(101) { append("X-Header-$it: value\r\n") }
                append("\r\n")
            }
            assertRawApiError(
                rawRequest(server.address.port, tooManyHeaders),
                status = 400,
                code = "INVALID_REQUEST",
                retryable = false,
            )

            val tooManyHeaderBytes = buildString {
                append("GET /api/health HTTP/1.1\r\n")
                repeat(64) { index ->
                    append("X-Large-$index: ")
                    append("a".repeat(1_100))
                    append("\r\n")
                }
                append("\r\n")
            }
            assertRawApiError(
                rawRequest(server.address.port, tooManyHeaderBytes),
                status = 400,
                code = "INVALID_REQUEST",
                retryable = false,
            )

            assertTrue(URI("http://127.0.0.1:${server.address.port}/api/health").toURL().readText().contains("true"))
        } finally {
            server.close()
        }
    }

    @Test
    fun containsAGsonWrappedPeerDisconnectDuringStreamedJson() {
        val request = "GET /api/large-json HTTP/1.1\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val brokenPipe = object : OutputStream() {
            override fun write(value: Int) = throw SocketException("Broken pipe")
            override fun write(bytes: ByteArray, offset: Int, length: Int) = throw SocketException("Broken pipe")
        }
        val client = object : Socket() {
            override fun getInputStream() = ByteArrayInputStream(request)
            override fun getOutputStream() = brokenPipe
            override fun close() = Unit
        }
        val server = AndroidLoopbackServer(
            ProductionCompanionRuntime(),
            additionalGetRoutes = mapOf(
                "/api/large-json" to { mapOf("payload" to "x".repeat(16 * 1_024)) },
            ),
            assetLoader = { null },
        )
        try {
            val handle = AndroidLoopbackServer::class.java
                .getDeclaredMethod("handle", Socket::class.java)
                .apply { isAccessible = true }

            handle.invoke(server, client)
        } finally {
            server.close()
        }
    }

    @Test
    fun responseProgrammingFailuresStillEscapeTheClientWorker() {
        val request = "GET /api/health HTTP/1.1\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val failedOutput = object : OutputStream() {
            override fun write(value: Int) = throw IllegalStateException("synthetic writer defect")
            override fun write(bytes: ByteArray, offset: Int, length: Int) =
                throw IllegalStateException("synthetic writer defect")
        }
        val client = object : Socket() {
            override fun getInputStream() = ByteArrayInputStream(request)
            override fun getOutputStream() = failedOutput
            override fun close() = Unit
        }
        val server = AndroidLoopbackServer(ProductionCompanionRuntime()) { null }
        try {
            val handle = AndroidLoopbackServer::class.java
                .getDeclaredMethod("handle", Socket::class.java)
                .apply { isAccessible = true }

            val failure = assertThrows(InvocationTargetException::class.java) {
                handle.invoke(server, client)
            }
            assertTrue(failure.cause is IllegalStateException)
        } finally {
            server.close()
        }
    }

    @Test
    fun boundsAResponseWriteWhenTheClientDoesNotRead() {
        val largeAsset = ByteArray(16 * 1024 * 1024) { 1 }
        val server = AndroidLoopbackServer(
            ProductionCompanionRuntime(),
            responseWriteTimeoutMillis = 500,
            assetLoader = { path -> largeAsset.takeIf { path == "large.bin" } },
        )
        val client = Socket().apply { receiveBufferSize = 1_024 }
        try {
            server.start()
            client.connect(InetSocketAddress(AndroidLoopbackServer.LOOPBACK_HOST, server.address.port))
            client.getOutputStream().write("GET /large.bin HTTP/1.1\r\n\r\n".toByteArray(Charsets.US_ASCII))
            client.getOutputStream().flush()
            assertTrue(waitUntil(1_000) { server.capacitySnapshot().activeConnections == 1 })

            assertTrue(waitUntil(5_000) { server.capacitySnapshot().activeConnections == 0 })
            val bootstrap = URI("http://127.0.0.1:${server.address.port}/api/bootstrap")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(200, bootstrap.responseCode)
        } finally {
            client.close()
            server.close()
        }
    }

    @Test
    fun apiErrorsUseTheSharedSafeEnvelopeForEveryServerErrorStatus() {
        val server = AndroidLoopbackServer(ProductionCompanionRuntime()) { null }
        server.setMapperHandler(object : MapperHttpHandler {
            override fun state(): Any = throw RuntimeException("private mapper implementation detail")
            override fun action(type: String, values: Map<String, String?>): Any = Unit
            override fun exportRaw(): ByteArray = byteArrayOf()
        })
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"

            assertApiError(
                URI("$base/api/specimens").toURL().openConnection() as HttpURLConnection,
                status = 400,
                code = "INVALID_REQUEST",
                retryable = false,
            )
            val unknownAction = (URI("$base/api/actions").toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                outputStream.use { it.write("{\"type\":\"private-action-detail\"}".toByteArray()) }
            }
            val invalidActionMessage = assertApiError(
                unknownAction,
                status = 400,
                code = "INVALID_REQUEST",
                retryable = false,
            )
            assertFalse(invalidActionMessage.contains("private-action-detail"))
            assertApiError(
                URI("$base/api/unknown").toURL().openConnection() as HttpURLConnection,
                status = 404,
                code = "NOT_FOUND",
                retryable = false,
            )
            assertApiError(
                (URI("$base/api/unknown").toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                },
                status = 404,
                code = "NOT_FOUND",
                retryable = false,
            )
            assertApiError(
                URI("$base/api/sprites/species/not-a-species.png").toURL().openConnection() as HttpURLConnection,
                status = 404,
                code = "NOT_FOUND",
                retryable = false,
            )
            assertApiError(
                URI("$base/api/sprites/species/1.jpg").toURL().openConnection() as HttpURLConnection,
                status = 404,
                code = "NOT_FOUND",
                retryable = false,
            )
            assertApiError(
                URI("$base/api").toURL().openConnection() as HttpURLConnection,
                status = 404,
                code = "NOT_FOUND",
                retryable = false,
            )
            assertApiError(
                (URI("$base/api/health").toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                },
                status = 405,
                code = "METHOD_NOT_ALLOWED",
                retryable = false,
            )
            val internalMessage = assertApiError(
                URI("$base/api/mapper/state").toURL().openConnection() as HttpURLConnection,
                status = 500,
                code = "INTERNAL_ERROR",
                retryable = true,
            )
            assertFalse(internalMessage.contains("private mapper"))
        } finally {
            server.close()
        }
    }

    @Test
    fun exposesAdditionalGetRouteOnlyWhenConfigured() {
        val productionServer = AndroidLoopbackServer(ProductionCompanionRuntime()) { null }
        val extendedServer = AndroidLoopbackServer(
            ProductionCompanionRuntime(),
            additionalGetRoutes = mapOf(
                "/api/test/runtime-identity" to {
                    mapOf(
                        "applicationId" to "test.application",
                        "transport" to "TEST_TRANSPORT",
                        "scenarioId" to "test-scenario",
                    )
                },
            ),
            assetLoader = { null },
        )
        try {
            productionServer.start()
            extendedServer.start()

            assertApiError(
                URI("http://127.0.0.1:${productionServer.address.port}/api/test/runtime-identity")
                    .toURL().openConnection() as HttpURLConnection,
                status = 404,
                code = "NOT_FOUND",
                retryable = false,
            )

            val identityConnection = URI(
                "http://127.0.0.1:${extendedServer.address.port}/api/test/runtime-identity",
            ).toURL().openConnection() as HttpURLConnection
            assertEquals(200, identityConnection.responseCode)
            assertEquals("no-store", identityConnection.getHeaderField("Cache-Control"))
            val identity = JsonParser.parseString(identityConnection.inputStream.reader().readText()).asJsonObject
            assertEquals(setOf("applicationId", "transport", "scenarioId"), identity.keySet())
            assertEquals("test.application", identity.get("applicationId").asString)
            assertEquals("TEST_TRANSPORT", identity.get("transport").asString)
            assertEquals("test-scenario", identity.get("scenarioId").asString)

            assertApiError(
                (URI("http://127.0.0.1:${extendedServer.address.port}/api/test/runtime-identity")
                    .toURL().openConnection() as HttpURLConnection).apply { requestMethod = "POST" },
                status = 405,
                code = "METHOD_NOT_ALLOWED",
                retryable = false,
            )
        } finally {
            productionServer.close()
            extendedServer.close()
        }
    }

    @Test
    fun fallsBackToTheSpaOnlyForExtensionlessHtmlNavigation() {
        val index = "<html>DualDex SPA</html>".toByteArray()
        val script = "export const ready = true".toByteArray()
        val server = AndroidLoopbackServer(ProductionCompanionRuntime()) { path ->
            when (path) {
                "index.html" -> index
                "assets/app.js" -> script
                else -> null
            }
        }
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            val navigation = (URI("$base/pokedex/species/25").toURL().openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "text/html")
            }
            assertEquals(200, navigation.responseCode)
            assertEquals("text/html; charset=utf-8", navigation.contentType)
            assertEquals(index.toList(), navigation.inputStream.readBytes().toList())

            val nonNavigation = (URI("$base/pokedex/species/25").toURL().openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/json")
            }
            assertEquals(404, nonNavigation.responseCode)
            assertFalse(nonNavigation.errorStream.readBytes().toList() == index.toList())

            val existingScript = URI("$base/assets/app.js").toURL().openConnection() as HttpURLConnection
            assertEquals(200, existingScript.responseCode)
            assertEquals("no-cache", existingScript.getHeaderField("Cache-Control"))
            assertEquals(script.toList(), existingScript.inputStream.readBytes().toList())

            val encodedAsset = (URI("$base/assets/missing%2Ejs").toURL().openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "text/html")
            }
            assertEquals(404, encodedAsset.responseCode)
            assertEquals("no-cache", encodedAsset.getHeaderField("Cache-Control"))
            assertFalse(encodedAsset.errorStream.readBytes().toList() == index.toList())

            listOf("missing.js", "missing.css", "missing.png", "missing.svg", "missing.map").forEach { path ->
                val missing = URI("$base/assets/$path").toURL().openConnection() as HttpURLConnection
                assertEquals(path, 404, missing.responseCode)
                assertEquals(path, "no-cache", missing.getHeaderField("Cache-Control"))
                assertFalse(path, missing.errorStream.readBytes().toList() == index.toList())
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun catalogMediaRequiresRevalidationInsteadOfImmutableCaching() {
        val sprite = RgbaSprite(1, 1, intArrayOf(0xff123456.toInt()))
        val trainerSprite = RgbaSprite(64, 64, IntArray(64 * 64) { 0xff123456.toInt() })
        val runtime = ProductionCompanionRuntime().apply {
            loadCatalog(
                "media.gba",
                ParsedCatalog(
                    "catalog-sha",
                    EngineFamily.EMERALD,
                    Platform.GBA,
                    speciesById = mapOf(
                        1 to SpeciesRecord(
                            id = 1,
                            dexNumber = CatalogField.available(1),
                            name = CatalogField.available("SPECIES"),
                            typeIds = CatalogField.available(emptyList()),
                            baseStats = CatalogField.available(BaseStats(1, 1, 1, 1, 1, 1)),
                            sprite = CatalogField.available(sprite),
                        ),
                    ),
                    captureBallsById = mapOf(
                        1 to CaptureBallRecord(
                            id = 1,
                            name = CatalogField.available("BALL"),
                            sprite = CatalogField.available(sprite),
                        ),
                    ),
                    trainerAssets = TrainerAssetCatalog(
                        avatarAssetKeys = mapOf(0 to "trainer/avatar", 1 to "trainer/avatar"),
                        assets = mapOf("trainer/avatar" to trainerSprite),
                    ),
                ),
            )
        }
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            listOf(
                "/api/sprites/species/1.png",
                "/api/sprites/balls/1.png",
                "/api/trainer-assets/trainer%2Favatar.png",
            ).forEach { path ->
                val media = URI(base + path).toURL().openConnection() as HttpURLConnection
                assertEquals(path, 200, media.responseCode)
                assertEquals(path, "no-cache", media.getHeaderField("Cache-Control"))
                assertFalse(path, media.getHeaderField("Cache-Control").contains("immutable"))
                media.inputStream.use { it.readBytes() }
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun spoolsFixedAndChunkedRomUploadsAndDeletesEveryOwnedBody() {
        Files.createDirectories(Path.of("build"))
        val spoolDirectory = Files.createTempDirectory(Path.of("build"), "loopback-upload-")
        val createdBodies = mutableListOf<Path>()
        val runtime = ProductionCompanionRuntime(
            parseCatalog = { rom, _, _ ->
                ParsedCatalog(rom.sha256, EngineFamily.EMERALD, Platform.GBA)
            },
        )
        val server = AndroidLoopbackServer(
            runtime,
            requestBodySpoolFactory = {
                Files.createTempFile(spoolDirectory, "request-", ".body").also(createdBodies::add)
            },
            assetLoader = { null },
        )
        try {
            server.start()
            val url = "http://127.0.0.1:${server.address.port}/api/load?name=fixture.gba"

            assertEquals(200, postRom(url, ByteArray(0x200), chunked = false))
            assertEquals(200, postRom(url, ByteArray(0x201) { 1 }, chunked = true))
            assertEquals(
                400,
                postRom(
                    "http://127.0.0.1:${server.address.port}/api/load?name=fixture.txt",
                    byteArrayOf(1, 2, 3),
                    chunked = false,
                ),
            )

            assertEquals(3, createdBodies.size)
            assertTrue(createdBodies.all(Files::notExists))
            assertFalse(Files.list(spoolDirectory).use { it.findAny().isPresent })
        } finally {
            server.close()
            createdBodies.forEach(Files::deleteIfExists)
            Files.deleteIfExists(spoolDirectory)
        }
    }

    @Test
    fun deletesSpoolWhenBodyWritingFailsWithOutOfMemory() {
        Files.createDirectories(Path.of("build"))
        val spoolDirectory = Files.createTempDirectory(Path.of("build"), "loopback-oom-spool-")
        val createdBodies = mutableListOf<Path>()
        val server = AndroidLoopbackServer(
            ProductionCompanionRuntime(),
            requestBodySpoolFactory = {
                Files.createTempFile(spoolDirectory, "request-", ".body").also(createdBodies::add)
            },
            assetLoader = { null },
        )
        try {
            val writer: (OutputStream) -> Long = {
                throw OutOfMemoryError("synthetic request body writer failure")
            }
            val spoolBody = AndroidLoopbackServer::class.java.getDeclaredMethod("spoolBody", Function1::class.java)
            spoolBody.isAccessible = true

            val failure = assertThrows(InvocationTargetException::class.java) {
                spoolBody.invoke(server, writer)
            }

            assertTrue(failure.cause is OutOfMemoryError)
            assertEquals(1, createdBodies.size)
            assertTrue(createdBodies.all(Files::notExists))
            assertFalse(Files.list(spoolDirectory).use { it.findAny().isPresent })
        } finally {
            server.close()
            createdBodies.forEach(Files::deleteIfExists)
            Files.deleteIfExists(spoolDirectory)
        }
    }

    @Test
    fun rejectsChunkedQuotaOverflowBeforeWritingItAndKeepsTheServerUsable() {
        Files.createDirectories(Path.of("build"))
        val spoolDirectory = Files.createTempDirectory(Path.of("build"), "loopback-chunked-quota-")
        val createdBodies = mutableListOf<Path>()
        val server = AndroidLoopbackServer(
            ProductionCompanionRuntime(),
            requestBodySpoolFactory = {
                Files.createTempFile(spoolDirectory, "request-", ".body").also(createdBodies::add)
            },
            requestReadTimeoutMillis = 250,
            assetLoader = { null },
        )
        try {
            server.start()
            val boundaryPrefix = "{\"type\":\"SETTINGS\",\"padding\":\""
            val boundarySuffix = "\"}"
            val boundary = boundaryPrefix + "x".repeat(1024 * 1024 - boundaryPrefix.length - boundarySuffix.length) + boundarySuffix
            assertEquals(1024 * 1024, boundary.toByteArray().size)
            val boundaryResponse = rawRequest(
                server.address.port,
                "POST /api/actions HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
                    "${boundary.length.toString(16)}\r\n$boundary\r\n0\r\n\r\n",
            )
            assertTrue(boundaryResponse.startsWith("HTTP/1.1 200 "))
            assertTrue(boundaryResponse.substringAfter("\r\n\r\n").contains("\"version\":"))

            Socket(AndroidLoopbackServer.LOOPBACK_HOST, server.address.port).use { client ->
                client.soTimeout = 2_000
                client.getOutputStream().write(
                    (
                        "POST /api/actions HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
                            "1\r\nx\r\n7fffffffffffffff\r\n"
                        ).toByteArray(Charsets.US_ASCII),
                )
                client.getOutputStream().flush()
                assertRawApiError(
                    client.getInputStream().readBytes().toString(Charsets.UTF_8),
                    status = 400,
                    code = "INVALID_REQUEST",
                    retryable = false,
                )
            }

            assertEquals(2, createdBodies.size)
            assertTrue(createdBodies.all(Files::notExists))
            assertFalse(Files.list(spoolDirectory).use { it.findAny().isPresent })
            val bootstrap = URI("http://127.0.0.1:${server.address.port}/api/bootstrap")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(200, bootstrap.responseCode)
            assertTrue(bootstrap.inputStream.reader().readText().contains("\"state\""))
        } finally {
            server.close()
            createdBodies.forEach(Files::deleteIfExists)
            Files.deleteIfExists(spoolDirectory)
        }
    }

    @Test
    fun containsManualSourceFailuresAndKeepsServingRequests() {
        val runtime = ProductionCompanionRuntime()
        val server = AndroidLoopbackServer(
            runtime,
            romSourceLoader = { name, _ ->
                if (name == "memory.gba") throw OutOfMemoryError("private source path and allocator detail")
                else throw IllegalStateException("private source path and parser detail")
            },
            assetLoader = { null },
        )
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"

            fun upload(name: String): String {
                val connection = URI("$base/api/load?name=$name").toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0x200)
                connection.outputStream.use { it.write(ByteArray(0x200)) }
                assertEquals(400, connection.responseCode)
                return connection.errorStream.reader().readText()
            }

            val memoryFailure = upload("memory.gba")
            assertTrue(memoryFailure.contains("There was not enough free memory to open this game guide"))
            assertFalse(memoryFailure.contains("private source path"))
            assertFalse(memoryFailure.contains("OutOfMemoryError"))

            val ordinaryFailure = upload("ordinary.gba")
            assertTrue(ordinaryFailure.contains("This game guide could not be opened. You can try again."))
            assertFalse(ordinaryFailure.contains("private source path"))
            assertFalse(ordinaryFailure.contains("IllegalStateException"))
            assertTrue(URI("$base/api/health").toURL().readText().contains("\"ok\":true"))
        } finally {
            server.close()
        }
    }

    @Test
    fun routesMapLocationSelectionThroughTheLoopbackActionApi() {
        val areaId = 0x0011 * 10 + 1
        val secondAreaId = 0x0012 * 10 + 1
        val runtime = ProductionCompanionRuntime().apply {
            loadCatalog(
                "map.gba",
                ParsedCatalog(
                    "sha",
                    EngineFamily.EMERALD,
                    Platform.GBA,
                    encounterAreas = listOf(
                        EncounterArea(areaId, CatalogField.available("Oldale grass"), 1, listOf(EncounterSlot(1, 2, 3, 100))),
                        EncounterArea(secondAreaId, CatalogField.available("Oldale water"), 2, listOf(EncounterSlot(2, 3, 4, 100))),
                    ),
                    worldMaps = WorldMapCatalog(
                        regions = listOf(
                            WorldMapRegion(
                                "region-0", "Hoenn", 8, 8, 1, 1, "world/region-0",
                                listOf(WorldMapLocation("oldale", "Oldale Town", setOf(0x0011, 0x0012), listOf(WorldMapCell(0, 0, 1, 1)))),
                            ),
                        ),
                        assets = mapOf("world/region-0" to RgbaSprite(8, 8, IntArray(64))),
                    ),
                ),
            )
        }
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            runtime.action("OPEN_SPECIES", mapOf("speciesId" to "1"))
            server.start()
            val body = post(
                "http://127.0.0.1:${server.address.port}/api/actions",
                """{"type":"MAP_AREA","regionKey":"region-0","locationKey":"oldale"}""",
            )

            assertTrue(body.contains("\"filter\":\"AREA\""))
            assertTrue(body.contains("\"screen\":\"POKEDEX\""))
            assertTrue(body.contains("\"selectedAreaId\":$areaId"))
            assertTrue(body.contains("\"selectedAreaIds\":[$areaId,$secondAreaId]"))
            assertTrue(body.contains("\"currentAreaIds\":[$areaId,$secondAreaId]"))
        } finally {
            server.close()
        }
    }

    @Test
    fun servesOnlyCatalogOwnedNormalizedMapPngAssets() {
        val pixels = IntArray(8 * 8) { 0xff123456.toInt() }
        val palettes = MapLightingPalettes(
            morning = IntArray(32) { 0xff110000.toInt() + it },
            day = IntArray(32) { 0xff220000.toInt() + it },
            night = IntArray(32) { 0xff330000.toInt() + it },
            dark = IntArray(32) { 0xff440000.toInt() + it },
        )
        val corruptIndices = LocalMapRasterCodec.compress(ByteArray(256))
        val runtime = ProductionCompanionRuntime().apply {
            loadCatalog(
                "map.gba",
                ParsedCatalog(
                    "sha",
                    EngineFamily.EMERALD,
                    Platform.GBA,
                    worldMaps = WorldMapCatalog(
                        regions = listOf(
                            WorldMapRegion(
                                "region-0",
                                null,
                                8,
                                8,
                                1,
                                1,
                                "world/region-0",
                                listOf(
                                    WorldMapLocation(
                                        "section-0",
                                        "Region 0",
                                        setOf(1),
                                        listOf(WorldMapCell(0, 0, 1, 1)),
                                    ),
                                ),
                            ),
                        ),
                        assets = mapOf("world/region-0" to RgbaSprite(8, 8, pixels)),
                    ),
                    localMaps = LocalMapCatalog(
                        maps = listOf(
                            LocalMap("local/0001", "Local", 1, 16, 16, 1, 1, "local/0001/map"),
                            LocalMap("local/0002", "Dynamic", 2, 16, 16, 1, 1, "local/0002/map"),
                            LocalMap("local/0003", "Corrupt", 3, 16, 16, 1, 1, "local/0003/map"),
                            LocalMap("local/0004", "Timed", 4, 16, 16, 1, 1, "local/0004/map"),
                        ),
                        assets = mapOf(
                            "local/0001/map" to PngMapAsset(
                                byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10),
                            ),
                        ),
                        indexedAssets = mapOf(
                            "local/0002/map" to IndexedMapAsset(
                                16,
                                16,
                                LocalMapRasterCodec.compress(ByteArray(256) { (it % 32).toByte() }),
                                LocalMapLightingPolicy.AUTO,
                                palettes,
                            ),
                            "local/0003/map" to IndexedMapAsset(
                                16,
                                16,
                                corruptIndices,
                                LocalMapLightingPolicy.AUTO,
                                palettes,
                            ),
                        ),
                        timedAssets = mapOf("local/0004/map" to timedAsset()),
                    ),
                ),
            )
        }
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            corruptIndices.fill(0)
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            val map = URI("$base/api/maps/world%2Fregion-0.png").toURL().openConnection() as HttpURLConnection
            assertEquals(200, map.responseCode)
            assertEquals("image/png", map.contentType)
            assertEquals("no-cache", map.getHeaderField("Cache-Control"))
            assertTrue(map.inputStream.readBytes().copyOfRange(1, 4).contentEquals("PNG".toByteArray()))
            val local = URI("$base/api/maps/local%2F0001%2Fmap.png").toURL().openConnection() as HttpURLConnection
            assertEquals(200, local.responseCode)
            assertEquals("image/png", local.contentType)
            val localBytes = local.inputStream.readBytes()
            assertTrue(localBytes.copyOfRange(1, 4).contentEquals("PNG".toByteArray()))
            val staticNight = URI("$base/api/maps/local%2F0001%2Fmap.png?lighting=NIGHT")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(localBytes.toList(), staticNight.inputStream.readBytes().toList())
            assertEquals(local.getHeaderField("ETag"), staticNight.getHeaderField("ETag"))

            val omitted = URI("$base/api/maps/local%2F0002%2Fmap.png").toURL().openConnection() as HttpURLConnection
            val day = URI("$base/api/maps/local%2F0002%2Fmap.png?lighting=DAY")
                .toURL().openConnection() as HttpURLConnection
            val night = URI("$base/api/maps/local%2F0002%2Fmap.png?lighting=NIGHT")
                .toURL().openConnection() as HttpURLConnection
            val omittedBytes = omitted.inputStream.readBytes()
            val dayBytes = day.inputStream.readBytes()
            val nightBytes = night.inputStream.readBytes()
            assertEquals(dayBytes.toList(), omittedBytes.toList())
            assertEquals(palettes.day[0], ImageIO.read(ByteArrayInputStream(dayBytes)).getRGB(0, 0))
            assertEquals(palettes.night[0], ImageIO.read(ByteArrayInputStream(nightBytes)).getRGB(0, 0))
            assertTrue(day.getHeaderField("ETag") != night.getHeaderField("ETag"))
            val invalid = URI("$base/api/maps/local%2F0002%2Fmap.png?lighting=INVALID")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(400, invalid.responseCode)
            val corrupt = URI("$base/api/maps/local%2F0003%2Fmap.png?lighting=DAY")
                .toURL().openConnection() as HttpURLConnection
            assertMapUnavailable(corrupt, "IllegalArgumentException")

            val noon = URI("$base/api/maps/local%2F0004%2Fmap.png?hour=12&minute=0")
                .toURL().openConnection() as HttpURLConnection
            val lateNight = URI("$base/api/maps/local%2F0004%2Fmap.png?hour=23&minute=0")
                .toURL().openConnection() as HttpURLConnection
            assertTrue(noon.inputStream.readBytes().toList() != lateNight.inputStream.readBytes().toList())
            assertTrue(noon.getHeaderField("ETag") != lateNight.getHeaderField("ETag"))
            val invalidTime = URI("$base/api/maps/local%2F0004%2Fmap.png?hour=24&minute=0")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(400, invalidTime.responseCode)

            val missing = URI("$base/api/maps/world%2Fmissing.png").toURL().openConnection() as HttpURLConnection
            assertEquals(404, missing.responseCode)
            val traversal = URI("$base/api/maps/..%2Fsecret.png").toURL().openConnection() as HttpURLConnection
            assertEquals(404, traversal.responseCode)
        } finally {
            server.close()
        }
    }

    @Test
    fun distinguishesMissingMapsFromRecoverableRendererFailuresWithoutLeakingDetails() {
        var recover = false
        val runtime = ProductionCompanionRuntime(
            mapAssetRenderer = { _, key, _, _ ->
                when (key) {
                    "missing" -> null
                    "exception", "recover" -> {
                        if (!recover) throw IllegalStateException("private renderer detail")
                        RenderedMapAsset(byteArrayOf(1), null)
                    }
                    "memory" -> throw OutOfMemoryError("private allocator detail")
                    else -> error("unexpected map key")
                }
            },
        ).apply {
            loadCatalog("maps.gba", ParsedCatalog("map-catalog", EngineFamily.EMERALD, Platform.GBA))
        }
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"

            assertApiError(
                URI("$base/api/maps/missing.png").toURL().openConnection() as HttpURLConnection,
                status = 404,
                code = "NOT_FOUND",
                retryable = false,
            )
            val exception = assertMapUnavailable(
                URI("$base/api/maps/exception.png").toURL().openConnection() as HttpURLConnection,
                diagnostic = "IllegalStateException",
            )
            assertFalse(exception.contains("private renderer detail"))
            val memory = assertMapUnavailable(
                URI("$base/api/maps/memory.png").toURL().openConnection() as HttpURLConnection,
                diagnostic = "OutOfMemoryError",
            )
            assertFalse(memory.contains("private allocator detail"))
            val bootstrap = URI("$base/api/bootstrap").toURL().openConnection() as HttpURLConnection
            assertEquals(200, bootstrap.responseCode)
            bootstrap.inputStream.use { it.readBytes() }

            recover = true
            val recovered = URI("$base/api/maps/recover.png").toURL().openConnection() as HttpURLConnection
            assertEquals(200, recovered.responseCode)
            assertEquals(listOf(1.toByte()), recovered.inputStream.readBytes().toList())
        } finally {
            server.close()
        }
    }

    @Test
    fun bindsOnlyToLoopbackAndServesPackagedUiAndCatalog() {
        val runtime = ProductionCompanionRuntime().apply {
            loadCatalog(
                "fixture.gba",
                ParsedCatalog("sha", EngineFamily.EMERALD, Platform.GBA, romCrc32 = "89ABCDEF"),
            )
        }
        val server = AndroidLoopbackServer(runtime) { path ->
            if (path == "index.html") "<html>DualDex production</html>".toByteArray() else null
        }
        try {
            server.start()
            assertTrue(server.address.address.isLoopbackAddress)

            val root = URI("http://127.0.0.1:${server.address.port}/").toURL().openConnection() as HttpURLConnection
            assertEquals(200, root.responseCode)
            assertTrue(root.inputStream.reader().readText().contains("DualDex production"))

            val bootstrapConnection = URI("http://127.0.0.1:${server.address.port}/api/bootstrap")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(-1L, bootstrapConnection.contentLengthLong)
            val bootstrap = bootstrapConnection.inputStream.reader().readText()
            assertTrue(bootstrap.contains("\"crc32\":\"89ABCDEF\""))
            assertTrue(bootstrap.contains("\"catalogHash\":\"sha\""))
            assertTrue(bootstrap.contains("\"mapperAvailable\":true"))
            assertTrue(bootstrap.contains("\"battle\":null"))

            val state = URI("http://127.0.0.1:${server.address.port}/api/state")
                .toURL().readText()
            assertTrue(state.contains("\"catalogHash\":\"sha\""))
            assertTrue(state.contains("\"mapperAvailable\":true"))
        } finally {
            server.close()
        }
    }

    @Test
    fun recoveryOnlySaveRamChangeAdvancesStateDeliveryExactlyOnce() {
        val hash = "7".repeat(64)
        val runtime = ProductionCompanionRuntime().apply {
            loadCatalog("fixture.gba", ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA))
        }
        val stateOwner = runtime.transientGameState as UnifiedGameStateDecoder
        val snapshot = SaveSnapshot(
            romIdentity = hash,
            saveIdentity = "8".repeat(64),
            saveGeneration = 3,
            saveCounter = 1,
            currentArea = null,
            seenDexNumbers = emptySet(),
            caughtDexNumbers = emptySet(),
            party = emptyList(),
            storedIndividuals = emptyList(),
            capabilities = emptyMap(),
        )
        assertTrue(runtime.applySaveSnapshot(snapshot, SaveRamView(status = "MATCHED")))
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            val beforeVersion = runtime.stateView().version
            val changedStatus = SaveRamView(
                status = "STALE",
                message = "Checkpoint storage is temporarily unavailable; retrying.",
            )

            stateOwner.acceptRecoveryStatus(changedStatus)

            val changed = URI("$base/api/state?sinceVersion=$beforeVersion")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(200, changed.responseCode)
            val changedBody = changed.inputStream.reader().readText()
            assertTrue(changedBody.contains("\"status\":\"STALE\""))
            assertTrue(changedBody.contains("Checkpoint storage is temporarily unavailable; retrying."))
            val changedVersion = runtime.stateView().version
            assertTrue(changedVersion > beforeVersion)

            stateOwner.acceptRecoveryStatus(changedStatus)

            val unchanged = URI("$base/api/state?sinceVersion=$changedVersion")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(204, unchanged.responseCode)
            assertTrue(unchanged.inputStream.readBytes().isEmpty())
        } finally {
            server.close()
            runtime.close()
        }
    }

    @Test
    fun returnsNativeRuntimeChangesAfterTheClientCurrentVersion() {
        val runtime = ProductionCompanionRuntime()
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            val currentVersion = runtime.stateView().version

            runtime.updateRetroArch(RetroArchView(storageGrant = "GRANTED", romGrant = "INDEXING"))

            val changed = URI("$base/api/state?sinceVersion=$currentVersion")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(200, changed.responseCode)
            val body = changed.inputStream.reader().readText()
            assertTrue(body.contains("\"version\":"))
            assertTrue(body.contains("\"storageGrant\":\"GRANTED\""))
            assertTrue(runtime.stateView().version > currentVersion)
        } finally {
            server.close()
            runtime.close()
        }
    }

    @Test
    fun suppressesTheStateBodyWhenTheClientAlreadyHasTheCurrentVersion() {
        val runtime = ProductionCompanionRuntime().apply {
            loadCatalog("fixture.gba", ParsedCatalog("sha", EngineFamily.EMERALD, Platform.GBA))
        }
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            val currentVersion = runtime.stateView().version

            val unchanged = URI("$base/api/state?sinceVersion=$currentVersion")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(204, unchanged.responseCode)
            assertEquals(-1L, unchanged.contentLengthLong)
            assertNull(unchanged.contentType)
            assertEquals("no-store", unchanged.getHeaderField("Cache-Control"))
            assertTrue(unchanged.inputStream.readBytes().isEmpty())

            val changed = URI("$base/api/state?sinceVersion=${(currentVersion - 1).coerceAtLeast(0)}")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(200, changed.responseCode)
            assertTrue(changed.inputStream.reader().readText().contains("\"version\":$currentVersion"))
            val metrics = server.stateResponseMetrics()
            assertEquals(2L, metrics.requests)
            assertEquals(1L, metrics.noContentResponses)
            assertEquals(1L, metrics.bodyResponses)
            assertTrue(metrics.bodyBytes > 0L)
        } finally {
            server.close()
        }
    }

    @Test
    fun rejectsMalformedAndNegativeStateVersionsWithTheApiErrorEnvelope() {
        val server = AndroidLoopbackServer(ProductionCompanionRuntime()) { null }
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"

            listOf("invalid", "-1").forEach { sinceVersion ->
                assertApiError(
                    URI("$base/api/state?sinceVersion=$sinceVersion").toURL().openConnection() as HttpURLConnection,
                    status = 400,
                    code = "INVALID_REQUEST",
                    retryable = false,
                )
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun returnsCurrentStateWhenClientVersionIsAheadAfterAServerReset() {
        val runtime = ProductionCompanionRuntime()
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            server.start()
            val currentVersion = runtime.stateView().version

            val resetEvidence = URI(
                "http://127.0.0.1:${server.address.port}/api/state?sinceVersion=${currentVersion + 1}",
            ).toURL().openConnection() as HttpURLConnection

            assertEquals(200, resetEvidence.responseCode)
            assertTrue(resetEvidence.inputStream.reader().readText().contains("\"version\":$currentVersion"))
        } finally {
            server.close()
        }
    }

    @Test
    fun secondsOnlyUnifiedClockSampleLeavesStateVersionAndHttpBodyUnchanged() {
        val hash = "5".repeat(64)
        val source = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val runtime = ProductionCompanionRuntime(transientGameState = source)
        runtime.loadCatalog(
            "seconds.gbc",
            ParsedCatalog(hash, EngineFamily.GOLD_SILVER, Platform.GBC),
        )
        source.beginSession(
            com.darkaxt.dualdex.live.TransientGameStateContext(
                romIdentity = hash,
                generation = 2,
                catalog = com.darkaxt.dualdex.battle.BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
            ),
        )
        val battle = com.darkaxt.dualdex.battle.LiveBattleState(
            active = false,
            sample = null,
            encounterKind = com.darkaxt.dualdex.battle.BattleEncounterKind.UNKNOWN,
        )
        source.acceptExistingGenerationSample(
            sampleId = 1,
            battle = battle,
            areaBaseId = 0x1803,
            mapPosition = com.darkaxt.dualdex.battle.RuntimeMapPosition(8, 5),
            clock = com.darkaxt.dualdex.battle.LiveClockState(6, 0, 1),
        )
        val version = runtime.stateView().version
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            server.start()
            source.acceptExistingGenerationSample(
                sampleId = 2,
                battle = battle,
                areaBaseId = 0x1803,
                mapPosition = com.darkaxt.dualdex.battle.RuntimeMapPosition(8, 5),
                clock = com.darkaxt.dualdex.battle.LiveClockState(6, 0, 2),
            )

            assertEquals(version, runtime.stateView().version)
            val unchanged = URI(
                "http://127.0.0.1:${server.address.port}/api/state?sinceVersion=$version",
            ).toURL().openConnection() as HttpURLConnection
            assertEquals(204, unchanged.responseCode)
            assertTrue(unchanged.inputStream.readBytes().isEmpty())
        } finally {
            server.close()
            runtime.close()
        }
    }

    @Test
    fun mapperRoutesRemainSeparateFromProductionActionsAndExportTheAcknowledgedSession() {
        val runtime = ProductionCompanionRuntime()
        val server = AndroidLoopbackServer(runtime) { if (it == "index.html") byteArrayOf() else null }
        server.setMapperHandler(object : MapperHttpHandler {
            override fun state(): Any = mapOf("enabled" to false)
            override fun action(type: String, values: Map<String, String?>): Any = mapOf("action" to type, "values" to values)
            override fun exportRaw(): ByteArray = "{\"containsRawMemory\":true}".toByteArray()
        })
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            assertTrue(URI("$base/api/mapper/state").toURL().readText().contains("\"enabled\":false"))
            assertTrue(post("$base/api/mapper/actions", "{\"type\":\"ENABLE\",\"privacyAcknowledged\":true}").contains("\"action\":\"ENABLE\""))
            assertTrue(post("$base/api/mapper/export", "{}").contains("\"containsRawMemory\":true"))
        } finally {
            server.close()
        }
    }

    private fun assertApiError(
        connection: HttpURLConnection,
        status: Int,
        code: String,
        retryable: Boolean,
    ): String {
        assertEquals(status, connection.responseCode)
        assertEquals("application/json; charset=utf-8", connection.contentType)
        assertEquals("no-store", connection.getHeaderField("Cache-Control"))
        val body = requireNotNull(connection.errorStream).reader().readText()
        return assertApiErrorBody(body, code, retryable)
    }

    private fun assertMapUnavailable(connection: HttpURLConnection, diagnostic: String): String {
        assertEquals(503, connection.responseCode)
        assertEquals("application/json; charset=utf-8", connection.contentType)
        assertEquals("no-store", connection.getHeaderField("Cache-Control"))
        val body = requireNotNull(connection.errorStream).reader().readText()
        val envelope = JsonParser.parseString(body).asJsonObject
        assertEquals(setOf("error", "diagnostic"), envelope.keySet())
        assertEquals(diagnostic, envelope.get("diagnostic").asString)
        assertTrue(envelope.get("diagnostic").asString.length <= 64)
        val error = envelope.getAsJsonObject("error")
        assertEquals(setOf("code", "message", "retryable"), error.keySet())
        assertEquals("MAP_UNAVAILABLE", error.get("code").asString)
        assertTrue(error.get("retryable").asBoolean)
        assertEquals("The map is temporarily unavailable. Try again.", error.get("message").asString)
        return body
    }

    private fun assertRawApiError(
        response: String,
        status: Int,
        code: String,
        retryable: Boolean,
    ) {
        val headers = response.substringBefore("\r\n\r\n")
        assertTrue(headers.startsWith("HTTP/1.1 $status "))
        assertTrue(headers.contains("Content-Type: application/json; charset=utf-8", ignoreCase = true))
        assertTrue(headers.contains("Cache-Control: no-store", ignoreCase = true))
        assertApiErrorBody(response.substringAfter("\r\n\r\n"), code, retryable)
    }

    private fun assertApiErrorBody(body: String, code: String, retryable: Boolean): String {
        val envelope = JsonParser.parseString(body).asJsonObject
        assertEquals(setOf("error"), envelope.keySet())
        val error = envelope.getAsJsonObject("error")
        assertEquals(setOf("code", "message", "retryable"), error.keySet())
        assertEquals(code, error.get("code").asString)
        assertEquals(retryable, error.get("retryable").asBoolean)
        return error.get("message").asString.also { assertTrue(it.isNotBlank()) }
    }

    private fun rawRequest(port: Int, request: String): String =
        Socket(AndroidLoopbackServer.LOOPBACK_HOST, port).use { client ->
            client.soTimeout = 5_000
            client.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            client.getOutputStream().flush()
            client.shutdownOutput()
            client.getInputStream().readBytes().toString(Charsets.UTF_8)
        }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
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

    private fun post(url: String, body: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        assertEquals(200, connection.responseCode)
        return connection.inputStream.reader().readText()
    }

    private fun postRom(url: String, body: ByteArray, chunked: Boolean): Int {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        if (chunked) connection.setChunkedStreamingMode(128) else connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
        (if (status >= 400) connection.errorStream else connection.inputStream)?.use { it.readBytes() }
        return status
    }
}
