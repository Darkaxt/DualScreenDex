package com.enrpau.dualscreendex.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files

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
        } finally {
            server.close()
            Files.deleteIfExists(root)
        }
    }
}
