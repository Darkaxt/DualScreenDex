package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.companion.api.RetroArchView
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
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.Socket
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

                assertTrue(it.getInputStream().bufferedReader().readLine().contains(" 503 "))
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
            assertEquals(404, corrupt.responseCode)

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
            assertTrue(bootstrap.contains("\"battle\":null"))
        } finally {
            server.close()
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
            assertEquals(0L, unchanged.contentLengthLong)
            assertNull(unchanged.contentType)
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
