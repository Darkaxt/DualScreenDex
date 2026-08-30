package com.darkaxt.dualdex

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RawLiveMemoryScenarioLoaderTest {
    @Test
    fun `decodes shared raw frames and transport fault scenarios`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val hash = bytes.sha256()
        val catalog = RawLiveMemoryScenarioLoader.decode(
            """
            {
              "schema": 1,
              "regionFrames": [
                {
                  "id": "raw-overworld",
                  "regions": [
                    {
                      "baseAddress": 33554432,
                      "size": 4,
                      "sha256": "$hash",
                      "base64Bytes": "$encoded"
                    }
                  ]
                }
              ],
              "scenarios": [
                {
                  "id": "normal",
                  "systemId": "game_boy_advance",
                  "gameBasename": "Modern Emerald.gba",
                  "crc32": "8C7DBECA",
                  "frames": [
                    { "id": "overworld", "sourceFrameId": "raw-overworld" }
                  ]
                },
                {
                  "id": "partial",
                  "systemId": "game_boy_advance",
                  "gameBasename": "Modern Emerald.gba",
                  "crc32": "8C7DBECA",
                  "frames": [
                    {
                      "id": "partial",
                      "sourceFrameId": "raw-overworld",
                      "readFaults": [
                        { "baseAddress": 33554432, "size": 4, "kind": "PARTIAL" }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals(listOf("normal", "partial"), catalog.scenarios.map(RawLiveMemoryScenario::id))
        assertEquals("normal", catalog.initialScenario.id)
        val controller = RawLiveMemoryQaController(catalog)
        assertEquals("normal", controller.snapshot().scenarioId)
        assertEquals("partial", controller.selectScenario("partial").scenarioId)
        assertTrue(controller.scenarioIds().containsAll(listOf("normal", "partial")))
        controller.close()
    }

    @Test
    fun `rejects altered region bytes and missing frame references`() {
        val encoded = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        val invalidHash = "00".repeat(32)
        val altered = """
            {
              "schema": 1,
              "regionFrames": [
                {
                  "id": "raw",
                  "regions": [{
                    "baseAddress": 33554432,
                    "size": 4,
                    "sha256": "$invalidHash",
                    "base64Bytes": "$encoded"
                  }]
                }
              ],
              "scenarios": [{
                "id": "normal",
                "systemId": "game_boy_advance",
                "gameBasename": "Modern Emerald.gba",
                "crc32": "8C7DBECA",
                "frames": [{ "id": "frame", "sourceFrameId": "raw" }]
              }]
            }
        """.trimIndent().toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            RawLiveMemoryScenarioLoader.decode(altered)
        }

        val missingReference = altered.toString(Charsets.UTF_8)
            .replace(invalidHash, byteArrayOf(1, 2, 3, 4).sha256())
            .replace("\"sourceFrameId\": \"raw\"", "\"sourceFrameId\": \"missing\"")
            .toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            RawLiveMemoryScenarioLoader.decode(missingReference)
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
