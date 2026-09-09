package com.enrpau.dualscreendex.parser.cli

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogLogicalDigest
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class CatalogPersistenceObservationCliTest {
    @Test fun selectedSyntheticInputPublishesTwoMatchingLogicalDigests() {
        val row = cached.persisted()
        val metrics = row.getAsJsonObject("persistence")
        assertEquals(setOf("fileName", "bytes", "writeMillis", "reopenMillis", "sections",
            "logicalDigestVersion", "beforeCatalogSha256", "afterCatalogSha256"), metrics.keySet())
        assertEquals(CatalogLogicalDigest.version, metrics["logicalDigestVersion"].asInt)
        val before = metrics["beforeCatalogSha256"].asString
        val after = metrics["afterCatalogSha256"].asString
        assertTrue(before.matches(Regex("[0-9a-f]{64}")))
        assertEquals(before, after)
        val identity = row.getAsJsonObject("result")["sha256"].asString
        val cache = CatalogCache(cached.directory.resolve("cache").toFile(), JdbcCatalogDatabaseFactory)
        val reopened = requireNotNull(cache.readComplete(identity))
        assertEquals(identity, reopened.catalog.romSha256)
        assertEquals(CatalogLogicalDigest.sha256(reopened.catalog), after)
        assertEquals(cache.fileFor(identity).name, metrics["fileName"].asString)
        assertEquals(cache.fileFor(identity).length(), metrics["bytes"].asLong)
        assertEquals(reopened.committedSections.size, metrics["sections"].asInt)
    }

    @Test fun nonPersistedRowsHaveNoLogicalDigestObservation() {
        val selectedWithoutCache = uncached.selected()
        val noCatalog = cached.rows.getValue("no-catalog.gb")
        assertEquals("NO_FAMILY_MATCH", noCatalog.getAsJsonObject("result")["status"].asString)
        assertFalse(noCatalog.has("catalog"))
        for (row in listOf(selectedWithoutCache, noCatalog)) {
            assertFalse(row.has("persistence"))
            assertFalse(row.has("persistenceError"))
            for (field in listOf("logicalDigestVersion", "beforeCatalogSha256", "afterCatalogSha256")) {
                assertFalse(row.has(field))
            }
        }
        assertFalse(Files.exists(uncached.directory.resolve("cache")))
    }

    @Test fun packagedReceiptBindsLogicalDigestReportAndRuntime() {
        for (capture in listOf(cached, uncached)) {
            val report = capture.report
            val receipt = capture.receipt
            assertEquals(16, report["schemaVersion"].asInt)
            assertEquals(1, receipt["schemaVersion"].asInt)
            assertEquals(16, receipt.getAsJsonObject("generator")["schemaVersion"].asInt)
            val manifest = capture.jars.sortedBy { it.fileName.toString() }.joinToString("") {
                "${it.fileName}\t${Files.size(it)}\t${SyntheticPersistenceCliFixture.digest(Files.readAllBytes(it))}\n"
            }
            val runtime = SyntheticPersistenceCliFixture.digest(manifest.toByteArray(Charsets.UTF_8))
            assertEquals(capture.source, report.getAsJsonObject("execution")["sourceCommit"].asString)
            assertEquals(capture.source, receipt["sourceCommit"].asString)
            assertEquals(runtime, report.getAsJsonObject("execution")["generatorSha256"].asString)
            assertEquals(runtime, receipt.getAsJsonObject("generator")["sha256"].asString)
            assertEquals("parser-cli", receipt.getAsJsonObject("generator")["name"].asString)
            assertEquals(SyntheticPersistenceCliFixture.digest(Files.readAllBytes(capture.directory.resolve("report.json"))),
                receipt["rawReportSha256"].asString)
            assertEquals(capture.fixtures.size, receipt["inputCount"].asInt)
            assertEquals(capture.fixtures.keys, capture.rows.keys)
            capture.fixtures.forEach { (name, bytes) ->
                val result = capture.rows.getValue(name).getAsJsonObject("result")
                assertEquals(SyntheticPersistenceCliFixture.digest(bytes), result["sha256"].asString)
                assertEquals(bytes.size, result["size"].asInt)
            }
        }
    }

    companion object {
        private lateinit var cached: SyntheticPersistenceCliFixture.Capture
        private lateinit var uncached: SyntheticPersistenceCliFixture.Capture
        @JvmStatic @BeforeClass fun runPackagedFixtures() {
            cached = SyntheticPersistenceCliFixture.run("persistence", true)
            cached.persisted() // Readiness is a prerequisite, never counted as missing-feature RED.
            uncached = SyntheticPersistenceCliFixture.run("without-cache", false)
            uncached.selected()
        }
    }
}
