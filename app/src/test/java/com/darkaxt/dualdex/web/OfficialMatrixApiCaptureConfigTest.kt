package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class OfficialMatrixApiCaptureConfigTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun readsPinnedExplicitCapturePartition() {
        val path = temporary.newFile("capture.json").toPath()
        Files.write(path, """
            {
              "schemaVersion": 1,
              "cacheDirectory": "D:/private/caches",
              "binding": {
                "sourceCommit": "${"a".repeat(40)}",
                "sourceSha256": "${"b".repeat(64)}",
                "reportSha256": "${"c".repeat(64)}",
                "receiptSha256": "${"d".repeat(64)}",
                "generatorSha256": "${"e".repeat(64)}"
              },
              "controls": [{
                "romSha256": "${"f".repeat(64)}",
                "cacheSha256": "${"0".repeat(64)}",
                "family": "RUBY_SAPPHIRE",
                "language": "ja",
                "codecId": "gba-gen3-ja",
                "codecVersion": 1
              }]
            }
        """.trimIndent().toByteArray())
        val digest = OfficialMatrixApiCaptureConfig.digest(path)
        val config = OfficialMatrixApiCaptureConfig.read(path, digest)

        assertEquals(1, config.controls.size)
        assertEquals(EngineFamily.RUBY_SAPPHIRE, config.controls.single().family)
        assertEquals("ja", config.controls.single().language)
        assertEquals("D:/private/caches", config.cacheDirectory)
    }

    @Test fun rejectsDigestOrSchemaMismatch() {
        val path = temporary.newFile("capture.json").toPath()
        val gson = GsonBuilder().create()
        val config = MatrixApiCaptureConfig(
            schemaVersion = 2,
            cacheDirectory = "D:/private/caches",
            binding = MatrixG3Binding(
                "a".repeat(40), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64),
            ),
            controls = listOf(
                MatrixCacheControl(
                    "f".repeat(64), "0".repeat(64), EngineFamily.EMERALD, "en", "gba-gen3-en", 1,
                ),
            ),
        )
        Files.write(path, gson.toJson(config).toByteArray())

        assertThrows(IllegalArgumentException::class.java) {
            OfficialMatrixApiCaptureConfig.read(path, "1".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OfficialMatrixApiCaptureConfig.read(path, OfficialMatrixApiCaptureConfig.digest(path))
        }
    }
}
