package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class OfficialMatrixApiBatchCaptureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun capturesEveryConfiguredCacheUnderOneExactBinding() {
        val root = temporary.root.toPath()
        val cache = root.resolve("cache")
        Files.createDirectory(cache)
        val controls = listOf(
            control("1".repeat(64), "a".repeat(64), EngineFamily.RED_BLUE, "en", "gb-gen1-en"),
            control("2".repeat(64), "b".repeat(64), EngineFamily.CRYSTAL, "ja", "gb-gen2-ja"),
        )
        val observed = mutableListOf<MatrixCacheControl>()
        val result = OfficialMatrixApiBatchCapture.capture(
            cacheDirectory = cache,
            workingDirectory = root.resolve("working"),
            controls = controls,
            databaseFactory = CatalogDatabaseFactory { error("fake capture must not open a database") },
            binding = BINDING,
        ) { _, _, expected, _, binding ->
            assertEquals(BINDING, binding)
            observed += expected
            JsonObject().apply {
                addProperty("romSha256", expected.romSha256)
                addProperty("acceptance", false)
            }
        }

        assertEquals(listOf("1".repeat(64), "2".repeat(64)), observed.map { it.romSha256 })
        assertEquals(1, result.get("schemaVersion").asInt)
        assertEquals(2, result.getAsJsonObject("controls").size())
        assertEquals("2".repeat(64), result.getAsJsonObject("controls")
            .getAsJsonObject("2".repeat(64)).get("romSha256").asString)
        assertFalse(result.has("acceptance"))
    }

    @Test fun rejectsDuplicateIdentitiesBeforeCreatingWorkingFiles() {
        val root = temporary.root.toPath()
        val cache = root.resolve("cache")
        Files.createDirectory(cache)
        val control = control("1".repeat(64), "a".repeat(64), EngineFamily.RED_BLUE, "en", "gb-gen1-en")
        assertThrows(IllegalArgumentException::class.java) {
            OfficialMatrixApiBatchCapture.capture(
                cache,
                root.resolve("working"),
                listOf(control, control),
                CatalogDatabaseFactory { error("must not open") },
                BINDING,
            ) { _, _, _, _, _ -> error("must not capture") }
        }
        assertFalse(Files.exists(root.resolve("working")))
    }

    private fun control(
        romSha256: String,
        cacheSha256: String,
        family: EngineFamily,
        language: String,
        codecId: String,
    ) = MatrixCacheControl(romSha256, cacheSha256, family, language, codecId, 1)

    private companion object {
        val BINDING = MatrixG3Binding(
            "c".repeat(40),
            "d".repeat(64),
            "e".repeat(64),
            "f".repeat(64),
            "0".repeat(64),
        )
    }
}
