package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

class AndroidLoopbackServerTest {
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

            val bootstrap = URI("http://127.0.0.1:${server.address.port}/api/bootstrap").toURL().readText()
            assertTrue(bootstrap.contains("\"crc32\":\"89ABCDEF\""))
            assertTrue(bootstrap.contains("\"battle\":null"))
        } finally {
            server.close()
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

    private fun post(url: String, body: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        assertEquals(200, connection.responseCode)
        return connection.inputStream.reader().readText()
    }
}
