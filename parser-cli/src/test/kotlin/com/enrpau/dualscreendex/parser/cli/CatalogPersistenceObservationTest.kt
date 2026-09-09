package com.enrpau.dualscreendex.parser.cli

import com.darkaxt.dualdex.catalog.*
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.*
import com.google.gson.Gson
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CatalogPersistenceObservationTest {
    @Test fun actualPersistencePublishesMeasuredLogicalDigests() = withRoot { root ->
        val catalog = catalog()
        val expected = CatalogLogicalDigest.sha256(catalog)
        var opens = 0
        var closes = 0
        val cache = CatalogCache(root, object : CatalogDatabaseFactory {
            override fun open(file: File): CatalogDatabase {
                assertEquals("previous database must close before reopen", opens, closes)
                opens++
                val actual = JdbcCatalogDatabaseFactory.open(file)
                return object : CatalogDatabase by actual {
                    override fun close() { actual.close(); closes++ }
                }
            }
        })
        val metrics = persist(cache, catalog)
        assertEquals(2, opens)
        assertEquals(2, closes)
        val reopened = requireNotNull(CatalogCache(root, JdbcCatalogDatabaseFactory).readComplete(catalog.romSha256))
        assertEquals(catalog, reopened.catalog)
        val json = Gson().toJsonTree(metrics).asJsonObject
        assertTrue("missing logicalDigestVersion from actual persistence", json.has("logicalDigestVersion"))
        assertEquals(CatalogLogicalDigest.version, json["logicalDigestVersion"].asInt)
        assertEquals(expected, json["beforeCatalogSha256"].asString)
        assertEquals(CatalogLogicalDigest.sha256(reopened.catalog), json["afterCatalogSha256"].asString)
        assertEquals(json["beforeCatalogSha256"], json["afterCatalogSha256"])
        assertTrue(metrics.bytes > 0)
        assertEquals(cache.fileFor(catalog.romSha256).name, metrics.fileName)
        assertEquals(reopened.committedSections.size, metrics.sections)
        assertTrue(metrics.writeMillis >= 0 && metrics.reopenMillis >= 0)
    }

    @Test fun preWriteDigestLimitFailureDoesNotOpenDatabase() = withRoot { root ->
        val catalog = catalog(listOf("x".repeat(CatalogLogicalDigest.maximumSectionBytes + 1)))
        var opens = 0
        val cache = CatalogCache(root, object : CatalogDatabaseFactory {
            override fun open(file: File): CatalogDatabase {
                opens++
                return JdbcCatalogDatabaseFactory.open(file)
            }
        })
        val failure = runCatching { persist(cache, catalog) }.exceptionOrNull()
        assertNotNull("oversized section must fail closed", failure)
        assertEquals("logical digest must fail before any database open", 0, opens)
        assertFalse(cache.fileFor(catalog.romSha256).exists())
    }

    @Test fun validButChangedReopenStillFailsOriginalEquality() = withRoot { root ->
        val catalog = catalog()
        val changed = catalog.copy(diagnostics = listOf("valid changed persisted section"))
        var opens = 0
        val cache = CatalogCache(root, object : CatalogDatabaseFactory {
            override fun open(file: File): CatalogDatabase {
                opens++
                if (opens == 2) {
                    JdbcCatalogDatabaseFactory.open(file).use { database ->
                        CatalogWriter(database).write(changed, source(), CatalogWriteProgress.complete())
                    }
                }
                return JdbcCatalogDatabaseFactory.open(file)
            }
        })
        val failure = runCatching { persist(cache, catalog) }.exceptionOrNull()
        assertNotNull("valid changed database must not publish metrics", failure)
        assertEquals("reopened SQLite catalog differs from parsed catalog", failure!!.message)
        assertEquals(2, opens)
        assertEquals(changed, requireNotNull(CatalogCache(root, JdbcCatalogDatabaseFactory)
            .readComplete(catalog.romSha256)).catalog)
    }

    @Test fun mutationAtDatabaseOpenCannotMasqueradeAsPreWriteParity() = withRoot { root ->
        val diagnostics = mutableListOf("before database open")
        val catalog = catalog(diagnostics)
        val before = CatalogLogicalDigest.sha256(catalog)
        var opens = 0
        val cache = CatalogCache(root, object : CatalogDatabaseFactory {
            override fun open(file: File): CatalogDatabase {
                opens++
                if (opens == 1) diagnostics[0] = "changed at database open"
                return JdbcCatalogDatabaseFactory.open(file)
            }
        })
        val failure = runCatching { persist(cache, catalog) }.exceptionOrNull()
        val reopened = requireNotNull(CatalogCache(root, JdbcCatalogDatabaseFactory).readComplete(catalog.romSha256))
        assertEquals("original model equality alone cannot detect this mutation", catalog, reopened.catalog)
        assertNotEquals(before, CatalogLogicalDigest.sha256(reopened.catalog))
        assertNotNull("pre-write digest parity must reject mutation even when model equality passes", failure)
        assertEquals("reopened SQLite catalog logical digest differs from pre-write catalog", failure!!.message)
        assertEquals(2, opens)
        assertTrue("observation failure must not silently delete a valid written database", cache.fileFor(catalog.romSha256).isFile)
    }

    // Reflection keeps the existing private production seam unchanged and makes pre-feature RED executable.
    private fun persist(cache: CatalogCache, catalog: ParsedCatalog): CatalogPersistenceMetrics {
        val method = Class.forName("com.enrpau.dualscreendex.parser.cli.MainKt").getDeclaredMethod(
            "persistCatalog", CatalogCache::class.java, CorpusInput::class.java,
            ParseResult::class.java, ParsedCatalog::class.java)
        method.isAccessible = true
        val analysis = ParseResult(RomHeader(Platform.GB, "FABRICATED"), catalog.romSha256,
            catalog.romCrc32, 1, SelectionStatus.SELECTED, EngineFamily.RED_BLUE, null, null,
            emptyList(), emptyList())
        try {
            return method.invoke(null, cache, CorpusInput("fabricated.gb", "fabricated.gb"), analysis, catalog) as CatalogPersistenceMetrics
        } catch (failure: InvocationTargetException) { throw failure.targetException }
    }

    private fun catalog(diagnostics: List<String> = listOf("fabricated persistence boundary")) = ParsedCatalog(
        romSha256 = "ab".repeat(32), romCrc32 = "1234ABCD", family = EngineFamily.RED_BLUE,
        platform = Platform.GB, diagnostics = diagnostics,
    )
    private fun source() = CatalogSourceMetadata.fromDisplayName("fabricated.gb", 1, "FABRICATED")
    private fun withRoot(test: (File) -> Unit) {
        val root = Files.createTempDirectory("dualdex-persist-unit-").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }
}
