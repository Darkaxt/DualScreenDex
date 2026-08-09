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
}
