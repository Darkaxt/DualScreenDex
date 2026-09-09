package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogLogicalDigest
import com.darkaxt.dualdex.catalog.CatalogSchema
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class OfficialMatrixApiCaptureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun capturesUnchangedBootstrapInsideMeasuredEnvelopeWithoutRomOrParser() {
        val fixture = fixture()
        val openedPaths = mutableListOf<Path>()
        val observed = capture(fixture, CatalogDatabaseFactory { file ->
            openedPaths.add(file.toPath().toAbsolutePath().normalize())
            JdbcTestCatalogDatabaseFactory.open(file)
        })
        val privateCopy = fixture.working.resolve("$SHA.sqlite").toAbsolutePath().normalize()
        assertTrue(openedPaths.size >= 2)
        assertTrue(openedPaths.all { it == privateCopy })
        assertFalse(openedPaths.contains(fixture.source.toAbsolutePath().normalize()))
        assertFalse(observed.get("acceptance").asBoolean)
        assertEquals("CACHE_ONLY_OBSERVATION", observed.get("scope").asString)
        assertEquals(fixture.expected.cacheSha256, observed.get("cacheSha256").asString)
        val envelope = observed.getAsJsonObject("bootstrap")
        assertEquals(0, envelope.get("parserInvocations").asInt)
        val response = envelope.getAsJsonObject("response")
        assertFalse(response.has("parserInvocations"))
        assertEquals(SHA, response.getAsJsonObject("catalog").get("hash").asString)
        assertEquals("Bulbizarre", response.getAsJsonObject("catalog").getAsJsonArray("species")[0]
            .asJsonObject.get("name").asString)
        assertEquals("fr", response.getAsJsonObject("language").get("activeLanguage").asString)
        assertEquals("ROM_DEFAULT", response.getAsJsonObject("language").get("authority").asString)
        assertEquals("CACHE_REOPEN", response.getAsJsonObject("state").getAsJsonObject("loading").get("phase").asString)
        assertEquals("RESTORE_CATALOG_BY_SHA", envelope.getAsJsonObject("captureProvenance").get("method").asString)
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
        assertTrue(Files.isRegularFile(fixture.working.resolve("$SHA.sqlite")))
    }

    @Test fun bindsLogicalDigestToTheCatalogActuallyReturnedByRestore() {
        val fixture = fixture()
        val changed = fixture.catalog.copy(diagnostics = listOf("changed private working copy"))
        var opens = 0
        val observed = capture(fixture, CatalogDatabaseFactory { file ->
            opens++
            if (opens == 2) {
                CatalogCache(file.parentFile, JdbcTestCatalogDatabaseFactory).write(
                    changed, CatalogSourceMetadata.direct("synthetic", 512, "FIXTURE"), CatalogWriteProgress.complete(),
                )
            }
            JdbcTestCatalogDatabaseFactory.open(file)
        })
        val logical = observed.getAsJsonObject("catalogLogicalDigest")
        assertNotNull("capture must include the exact restored catalog digest", logical)
        assertEquals(CatalogLogicalDigest.version, logical.get("version").asInt)
        assertEquals(CatalogLogicalDigest.sha256(changed), logical.get("sha256").asString)
        assertNotEquals(CatalogLogicalDigest.sha256(fixture.catalog), logical.get("sha256").asString)
        assertEquals("capture must not read the repository a second time", 2, opens)
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun explicitlyCapturesDiscoveredKnowledgeMode() {
        val envelope = capture(fixture()).getAsJsonObject("bootstrap")
        val provenance = envelope.getAsJsonObject("captureProvenance")
        assertTrue("knowledge mode must be recorded", provenance.has("knowledgeMode"))
        assertEquals("DISCOVERED", provenance.get("knowledgeMode").asString)
        assertEquals("DISCOVERED", envelope.getAsJsonObject("response")
            .getAsJsonObject("state").getAsJsonObject("settings").get("knowledgeMode").asString)
        assertEquals(CatalogLogicalDigest.version, provenance.get("catalogLogicalDigestVersion").asInt)
    }

    @Test fun refusesWrongDigestBeforeOpeningDatabase() {
        val fixture = fixture()
        val opens = AtomicInteger()
        val factory = CatalogDatabaseFactory { file -> opens.incrementAndGet(); JdbcTestCatalogDatabaseFactory.open(file) }
        assertThrows(IllegalArgumentException::class.java) {
            capture(fixture.copy(expected = fixture.expected.copy(cacheSha256 = "0".repeat(64))), factory)
        }
        assertEquals(0, opens.get())
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun rejectsWrongEmbeddedIdentityWithoutInvalidatingSourceCache() {
        val fixture = fixture()
        val other = "2".repeat(64)
        val renamed = fixture.source.resolveSibling("$other.sqlite")
        Files.copy(fixture.source, renamed)
        assertThrows(IllegalArgumentException::class.java) {
            capture(fixture.copy(source = renamed, expected = fixture.expected.copy(romSha256 = other)))
        }
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
        assertEquals(fixture.expected.cacheSha256, digest(renamed))
    }

    @Test fun rejectsStaleSchemaWithoutMigratingOrDeletingSourceCache() {
        val fixture = fixture()
        JdbcTestCatalogDatabaseFactory.open(fixture.source.toFile()).use { db ->
            db.execute("UPDATE catalog_metadata SET parser_schema_version = ?", listOf(CatalogSchema.parserSchemaVersion - 1))
        }
        val stale = fixture.copy(expected = fixture.expected.copy(cacheSha256 = digest(fixture.source)))
        assertThrows(IllegalArgumentException::class.java) { capture(stale) }
        assertEquals(stale.expected.cacheSha256, digest(stale.source))
    }

    @Test fun rejectsFamilyLanguageCodecAndVersionMismatch() {
        val fixture = fixture()
        val mismatches = listOf(
            fixture.expected.copy(family = EngineFamily.CRYSTAL),
            fixture.expected.copy(language = "en"),
            fixture.expected.copy(codecId = "other-codec"),
            fixture.expected.copy(codecVersion = 2),
        )
        mismatches.forEachIndexed { index, expected ->
            assertThrows(IllegalArgumentException::class.java) {
                capture(fixture.copy(working = fixture.working.resolveSibling("mismatch-$index"), expected = expected))
            }
        }
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun rejectsMissingSourceWithoutCreatingDatabase() {
        val fixture = fixture()
        val missing = fixture.source.resolveSibling("3".repeat(64) + ".sqlite")
        assertThrows(IllegalArgumentException::class.java) { capture(fixture.copy(source = missing)) }
        assertFalse(Files.exists(missing))
        assertFalse(Files.exists(fixture.working))
    }

    @Test fun rejectsSidecarsBeforeOpeningDatabase() {
        val fixture = fixture()
        val sidecar = fixture.source.resolveSibling(fixture.source.fileName.toString() + "-wal")
        Files.write(sidecar, byteArrayOf(1, 2, 3))
        val opens = AtomicInteger()
        val factory = CatalogDatabaseFactory { file -> opens.incrementAndGet(); JdbcTestCatalogDatabaseFactory.open(file) }
        assertThrows(IllegalArgumentException::class.java) { capture(fixture, factory) }
        assertEquals(0, opens.get())
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(sidecar))
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun rejectsExistingWorkingDirectoryWithoutOverwrite() {
        val fixture = fixture()
        Files.createDirectory(fixture.working)
        val marker = fixture.working.resolve("owned-by-other.txt")
        Files.write(marker, "preserve".toByteArray(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) { capture(fixture) }
        assertEquals("preserve", String(Files.readAllBytes(marker), Charsets.UTF_8))
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    @Test fun rejectsInvalidIdentitiesBeforeFilesystemAccess() {
        val fixture = fixture()
        for (identity in listOf("../outside", SHA.uppercase().replace('1', 'A'), "")) {
            assertThrows(IllegalArgumentException::class.java) {
                capture(fixture.copy(expected = fixture.expected.copy(romSha256 = identity)))
            }
        }
        assertFalse(Files.exists(fixture.working))
    }

    @Test fun embedsEveryRunBindingInsideTheHashedBootstrapEnvelope() {
        val observed = capture(fixture())
        val envelope = observed.getAsJsonObject("bootstrap")
        val binding = envelope.getAsJsonObject("captureProvenance").getAsJsonObject("binding")
        assertEquals(setOf("sourceCommit", "sourceSha256", "reportSha256", "receiptSha256", "generatorSha256"), binding.keySet())
        assertEquals(BINDING.sourceCommit, binding.get("sourceCommit").asString)
        assertEquals(BINDING.sourceSha256, binding.get("sourceSha256").asString)
        assertEquals(BINDING.reportSha256, binding.get("reportSha256").asString)
        assertEquals(BINDING.receiptSha256, binding.get("receiptSha256").asString)
        assertEquals(BINDING.generatorSha256, binding.get("generatorSha256").asString)
        assertFalse(envelope.has("binding"))
        assertFalse(envelope.getAsJsonObject("response").has("captureProvenance"))
    }

    @Test fun rejectsMalformedRunBindingsBeforeCopyOrDatabaseAccess() {
        val fixture = fixture()
        val bad = listOf(
            BINDING.copy(sourceCommit = "a".repeat(39)),
            BINDING.copy(sourceSha256 = "A".repeat(64)),
            BINDING.copy(reportSha256 = ""),
            BINDING.copy(receiptSha256 = "f".repeat(63)),
            BINDING.copy(generatorSha256 = "not-a-digest"),
        )
        bad.forEach { binding ->
            assertThrows(IllegalArgumentException::class.java) {
                OfficialMatrixApiCapture.capture(fixture.source, fixture.working, fixture.expected,
                    CatalogDatabaseFactory { error("invalid binding must not open a database") }, binding)
            }
        }
        assertFalse(Files.exists(fixture.working))
        assertEquals(fixture.expected.cacheSha256, digest(fixture.source))
    }

    private fun capture(fixture: Fixture, factory: CatalogDatabaseFactory = JdbcTestCatalogDatabaseFactory): JsonObject =
        OfficialMatrixApiCapture.capture(fixture.source, fixture.working, fixture.expected, factory, BINDING)

    private fun fixture(): Fixture {
        val root = temporary.newFolder().toPath()
        val language = LanguageTag.FRENCH
        val manifest = RomLanguageManifest(
            defaultLanguage = language,
            projections = listOf(RomLanguageProjection(language, "fixture-fr", 1,
                LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)),
            status = LanguageResolutionStatus.RESOLVED,
        )
        val overlay = CatalogLanguageOverlay(
            language = language,
            overlayVersion = 7,
            localizedCapabilities = LocalizedTextCapability.entries.associateWith {
                when (it) {
                    LocalizedTextCapability.SPECIES_NAMES -> LocalizedCapabilityState.available(1)
                    LocalizedTextCapability.SPECIES_DESCRIPTIONS -> LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_FOUND, 1)
                    else -> LocalizedCapabilityState.unavailable(CapabilityStatus.NOT_APPLICABLE, 0, 1.0)
                }
            },
            speciesNames = mapOf(1 to CatalogField.available("Bulbizarre")),
        )
        val catalog = ParsedCatalog(
            romSha256 = SHA, romCrc32 = "12345678", family = EngineFamily.EMERALD, platform = Platform.GBA,
            speciesById = mapOf(1 to SpeciesRecord(
                id = 1, dexNumber = CatalogField.available(1), name = CatalogField.notApplicable("overlay"),
                typeIds = CatalogField.notFound("fixture"), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"),
            )),
            localization = CatalogLocalization(manifest, mapOf(language to overlay)),
        )
        val cache = CatalogCache(root.resolve("input").toFile(), JdbcTestCatalogDatabaseFactory)
        cache.write(catalog, CatalogSourceMetadata.direct("synthetic", 512, "FIXTURE"), CatalogWriteProgress.complete())
        val source = cache.fileFor(SHA).toPath()
        return Fixture(source, root.resolve("capture"), MatrixCacheControl(SHA, digest(source), EngineFamily.EMERALD, "fr", "fixture-fr", 1), catalog)
    }

    private data class Fixture(val source: Path, val working: Path, val expected: MatrixCacheControl, val catalog: ParsedCatalog)
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
    private companion object {
        const val SHA = "1111111111111111111111111111111111111111111111111111111111111111"
        val BINDING = MatrixG3Binding("a".repeat(40), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64))
    }
}
