package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.companion.api.*
import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** Actual SQLite/cache-only lifecycle over fabricated512-byte input, never an original. */
class WesternGen3SemanticCaptureTest {
    @Test fun independentCatalogBoundaryRejectsConsistentlyWrongProductionAndOverlayNames() {
        val f = fixture()
        WesternGen3SemanticOracle.checkCatalog(f.oracle, f.catalog, f.fields.keys, f.fields) { _, block -> block() }
        val wrong = fixture(wrong = true)
        // Old diagnostic legitimately accepts internal parity. Independent comparison must fail it.
        WesternGen3BaselineCapture.checkCatalogItems(wrong.catalog, wrong.fields.keys, wrong.fields) { _, block -> block() }
        assertTrue(outcomes { check -> WesternGen3SemanticOracle.checkCatalog(wrong.oracle, wrong.catalog,
            wrong.fields.keys, wrong.fields, check) }.any { it.isFailure })
        val overlay = requireNotNull(f.catalog.defaultLocalizedText())
        val unresolved = f.catalog.copy(localMaps = f.catalog.localMaps.copy(pois = f.catalog.localMaps.pois.map {
            it.copy(textObligation = LocalMapPoiTextObligation.UNRESOLVED)
        }), localization = CatalogLocalization(f.catalog.languageManifest, mapOf(overlay.language to CatalogLanguageOverlay(
            overlay.language, overlay.overlayVersion, overlay.localizedCapabilities +
                (LocalizedTextCapability.POI_TEXT to LocalizedCapabilityState.notFound("synthetic unresolved", 3)),
            itemNames = overlay.itemNames,
        ))))
        assertTrue(outcomes { check -> WesternGen3SemanticOracle.checkCatalog(f.oracle, unresolved,
            f.fields.keys, f.fields, check) }.any { it.isFailure })
    }

    @Test fun realSyntheticSqliteAndCacheOnlyApiVisitBothIndependentBoundariesWithoutReparse() {
        val f = fixture()
        val receipt = WesternGen3BaselineCapture.Receipt(temp("sqlite"), f.oracle.identity, f.oracle)
        WesternGen3BaselineCapture.persistAndCheck(receipt, JdbcTestCatalogDatabaseFactory, f.rom, f.catalog, f.fields.keys, f.fields)
        assertEquals(receipt.errors.toString(), emptyMap<String, String>(), receipt.errors)
        assertTrue(receipt.completed.containsAll(setOf("sqlite", "api", "semantic-sqlite", "semantic-api")))
        assertEquals("PASS", receipt.checks["sqlite.semantic.overlay.required-names"])
        assertEquals("PASS", receipt.checks["api.semantic.required-names"])
        assertEquals("PASS", receipt.checks["sqlite.whole-catalog-parity"])
        assertEquals(0, receipt.values["apiReparses"])
        receipt.finish()
        assertEquals(false, receipt.values["semanticAcceptance"]) // missing original capture paths must not grant acceptance
        assertEquals(0, receipt.values["acceptedNames"])
    }

    @Test fun actualSqliteAndCacheOnlyApiRejectWrongButInternallyConsistentNames() {
        val f = fixture(wrong = true)
        val receipt = WesternGen3BaselineCapture.Receipt(temp("sqlite-wrong"), f.oracle.identity, f.oracle)
        WesternGen3BaselineCapture.persistAndCheck(receipt, JdbcTestCatalogDatabaseFactory, f.rom, f.catalog, f.fields.keys, f.fields)
        assertEquals("PASS", receipt.checks["sqlite.whole-catalog-parity"])
        assertEquals("FAIL", receipt.checks["sqlite.semantic.overlay.required-names"])
        assertEquals("FAIL", receipt.checks["api.semantic.required-names"])
        assertEquals(0, receipt.values["apiReparses"])
        receipt.finish(); assertEquals(false, receipt.values["semanticAcceptance"])
    }

    @Test fun semanticTerminalRequiresAllDiagnosticAndSemanticPathsAndSingleInvocationCounters() {
        val f = fixture()
        val paths = WesternGen3BaselineCapture.requiredPaths + WesternGen3SemanticOracle.requiredPaths
        for (missing in paths) {
            val receipt = terminal(f, paths - missing)
            receipt.finish()
            assertEquals("missing $missing", false, receipt.values["semanticAcceptance"])
            assertEquals(0, receipt.values["acceptedNames"])
        }
        for (counter in listOf("inputReads", "inputReadAttempts", "originalAnalysisInvocations", "itemProducerCalls")) {
            val receipt = terminal(f, paths); receipt.values[counter] = 2; receipt.finish()
            assertEquals("repeated $counter", false, receipt.values["semanticAcceptance"])
        }
        val reparsed = terminal(f, paths); reparsed.values["apiReparses"] = 1; reparsed.finish()
        assertEquals(false, reparsed.values["semanticAcceptance"])
        for (boundary in WesternGen3SemanticOracle.requiredBoundaries) {
            val missing = terminal(f, paths); missing.semanticBoundaryCounts.remove(boundary); missing.finish()
            assertEquals("missing count $boundary", false, missing.values["semanticAcceptance"])
            val collapsed = terminal(f, paths); collapsed.semanticBoundaryCounts[boundary] = 2; collapsed.finish()
            assertEquals("collapsed count $boundary", false, collapsed.values["semanticAcceptance"])
        }
        val failed = terminal(f, paths); failed.check("independent-name") { assertEquals("POKé BALL", "WRONG") }; failed.finish()
        assertEquals(false, failed.values["semanticAcceptance"])
        val complete = terminal(f, paths); complete.finish()
        assertEquals("SEMANTIC_ACCEPTANCE_COMPLETE", complete.values["terminal"])
        assertEquals(true, complete.values["semanticAcceptance"]); assertEquals(3, complete.values["acceptedNames"])
    }

    @Test fun diagnosticReceiptStaysNonSemanticWithoutOracleEvenWhenSemanticPathNamesArePresent() {
        val receipt = WesternGen3BaselineCapture.Receipt(temp("diagnostic"), JsonObject())
        receipt.completed += WesternGen3BaselineCapture.requiredPaths + WesternGen3SemanticOracle.requiredPaths
        receipt.finish()
        assertEquals("DIAGNOSTIC_CAPTURE_COMPLETE", receipt.values["terminal"])
        assertEquals(false, receipt.values["semanticAcceptance"]); assertEquals(0, receipt.values["acceptedNames"])
    }

    private fun terminal(f: Fixture, paths: Set<String>) = WesternGen3BaselineCapture.Receipt(temp("terminal"), f.oracle.identity, f.oracle).apply {
        completed += paths
        for (counter in listOf("inputReads", "inputReadAttempts", "originalAnalysisInvocations", "itemProducerCalls")) values[counter] = 1
        values["apiReparses"] = 0; values["requestedCount"] = 3; values["availableCount"] = 3
        semanticBoundaryCounts.putAll(WesternGen3SemanticOracle.requiredBoundaries.associateWith { 3 })
    }
    private fun outcomes(run: ((String, () -> Unit) -> Unit) -> Unit): List<Result<Unit>> {
        val results = linkedMapOf<String, Result<Unit>>()
        run { key, block -> require(key !in results); results[key] = runCatching(block) }
        return results.values.toList()
    }
    private fun temp(label: String): Path {
        val parent = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.takeIf(String::isNotBlank)?.let(Path::of)
        if (parent != null) Files.createDirectories(parent)
        return if (parent == null) Files.createTempDirectory("gen3-semantic-$label-") else Files.createTempDirectory(parent, "gen3-semantic-$label-")
    }
    private data class Fixture(val rom: RomImage, val catalog: ParsedCatalog, val fields: Map<Int, CatalogField<String>>,
        val oracle: WesternGen3SemanticOracle.Control)
    private fun fixture(wrong: Boolean = false): Fixture {
        val rom = RomImage(ByteArray(512) { (it * 11).toByte() }); val language = LanguageTag.ENGLISH
        val manifest = RomLanguageManifest(language, listOf(RomLanguageProjection(language, "gba-gen3-en", 1,
            LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val expected = linkedMapOf(0 to "????????", 1 to "POKé BALL", 2 to "POTION")
        val fields = expected.mapValues { (id, name) -> CatalogField.available(if (wrong && id == 1) "WRONG" else name) }
        val states = LocalizedTextCapability.entries.associateWith { when (it) {
            LocalizedTextCapability.ITEM_NAMES -> LocalizedCapabilityState.available(3)
            LocalizedTextCapability.LOCAL_MAP_NAMES -> LocalizedCapabilityState.notFound("synthetic", 1)
            LocalizedTextCapability.POI_TEXT -> LocalizedCapabilityState.available(3)
            else -> LocalizedCapabilityState.notApplicable("synthetic", 0)
        } }
        val catalog = ParsedCatalog(romSha256 = rom.sha256, romCrc32 = rom.crc32, family = EngineFamily.RUBY_SAPPHIRE, platform = Platform.GBA,
            captureBallsById = mapOf(0 to CaptureBallRecord(0, CatalogField.notApplicable("overlay"), CatalogField.notApplicable("synthetic"))),
            localMaps = LocalMapCatalog(maps = listOf(LocalMap("synthetic", null, 1, 16, 16, 1, 1, "synthetic/map")),
                assets = mapOf("synthetic/map" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
                pois = listOf(1, 2, 2).mapIndexed { index, id -> LocalMapPoi("synthetic/hidden/$index", "synthetic", 1, 0, 0,
                    LocalMapPoiKind.HIDDEN_ITEM, organicVisibility = LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                    item = LocalMapPoiItem(id, collectionFlagId = index), textObligation = LocalMapPoiTextObligation.ITEM_NAME) }),
            localization = CatalogLocalization(manifest, mapOf(language to CatalogLanguageOverlay(language, 1, states, itemNames = fields))))
        val identity = JsonObject().apply {
            addProperty("sha256", rom.sha256); addProperty("family", "RUBY_SAPPHIRE"); addProperty("language", "en")
        }
        return Fixture(rom, catalog, fields, WesternGen3SemanticOracle.Control(identity, expected))
    }
}
