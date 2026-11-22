package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.TrainerAssetCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import java.net.HttpURLConnection
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainerAssetApiTest {
    @Test
    fun servesOnlyExactCatalogOwnedTrainerAssets() {
        val sprite = RgbaSprite(64, 64, IntArray(64 * 64) { 0xff315a84.toInt() })
        val runtime = ProductionCompanionRuntime().apply {
            loadCatalog(
                "emerald.gba",
                ParsedCatalog(
                    "sha",
                    EngineFamily.EMERALD,
                    Platform.GBA,
                    trainerAssets = TrainerAssetCatalog(
                        avatarAssetKeys = mapOf(0 to "trainer/avatar/male", 1 to "trainer/avatar/female"),
                        assets = mapOf(
                            "trainer/avatar/male" to sprite,
                            "trainer/avatar/female" to sprite,
                        ),
                    ),
                ),
            )
        }
        val server = AndroidLoopbackServer(runtime) { null }
        try {
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            val asset = URI("$base/api/trainer-assets/trainer%2Favatar%2Fmale.png")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(200, asset.responseCode)
            assertEquals("image/png", asset.contentType)
            assertTrue(asset.inputStream.readBytes().copyOfRange(1, 4).contentEquals("PNG".toByteArray()))

            val missing = URI("$base/api/trainer-assets/trainer%2Fmissing.png")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(404, missing.responseCode)
            val traversal = URI("$base/api/trainer-assets/..%2Fsecret.png")
                .toURL().openConnection() as HttpURLConnection
            assertEquals(404, traversal.responseCode)
        } finally {
            server.close()
        }
    }
}
