package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.GameClock
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.LiveMapPosition
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoi
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapScene
import com.enrpau.dualscreendex.parser.catalog.LocalMapScenePlacement
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityReportBuilderTest {
    @Test
    fun reportsCurrentMapRuntimeCacheAndPrivacyWithoutPrivatePaths() {
        val catalog = catalog()
        val snapshot = AppSnapshot(
            settings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
            liveAreaBaseId = 1,
            liveMapPosition = LiveMapPosition(0, 0),
            gameTime = GameClock(10, 30),
            gameAccessReady = true,
            catalogReady = true,
            catalogName = "test.gba",
        )
        val state = ApiViewBuilder.state(
            snapshot,
            catalog,
            retroArch = RetroArchView(connection = "CONNECTED", resolution = "RESOLVED"),
            saveRam = SaveRamView(
                status = "MATCHED",
                autosaveStatus = "VERIFIED",
                capabilities = mapOf("POKEDEX" to "AVAILABLE"),
            ),
        )
        val base = ApiViewBuilder.diagnostics(
            catalog,
            "test.gba",
            activeRulesetId = null,
            rulesetAssumed = false,
            speciesId = null,
            moveId = null,
        )
        val report = CompatibilityReportBuilder.build(
            base = base,
            catalog = catalog,
            state = state,
            cacheStats = MapAssetRenderCacheStats(2, 1_024, 4, 2, 0),
            appVersion = "1.1.0-test",
            catalogSchemaVersion = 6,
            parserSchemaVersion = 42,
        )

        assertEquals(2, report.reportSchemaVersion)
        assertEquals("CONNECTED", report.runtime?.retroArchConnection)
        assertEquals("LOCAL_SCENE", report.map?.presentation)
        assertEquals("scene/test", report.map?.sceneKey)
        assertEquals("VALID", report.map?.playerPositionStatus)
        assertEquals("STATIC", report.map?.lighting)
        assertEquals(1, report.map?.totalPois)
        assertEquals(1, report.map?.visiblePois)
        assertEquals(2, report.cache?.entries)
        assertFalse(report.privacy.containsRomBytes)
        assertFalse(report.privacy.containsMemoryBytes)
        assertFalse(report.privacy.containsSaveData)
        assertFalse(report.privacy.containsPrivatePaths)

        val json = CompatibilityReportSerializer.toBytes(report).toString(Charsets.UTF_8)
        assertTrue(json.contains("\"reportSchemaVersion\": 2"))
        assertTrue(json.contains("[path omitted]"))
        assertFalse(json.contains("\"romName\""))
        assertFalse(json.contains("\"sha256\""))
        assertFalse(json.contains("\"crc32\""))
        assertFalse(json.contains("\"currentAreaBaseId\""))
        assertFalse(json.contains("\"currentAreaName\""))
        assertFalse(json.contains("\"localMapKey\""))
        assertFalse(json.contains("\"sceneKey\""))
        assertFalse(json.contains("\"atlasRegionKey\""))
        assertFalse(json.contains("\"playerX\""))
        assertFalse(json.contains("\"playerY\""))
        assertFalse(json.contains("scene/test"))
        assertFalse(json.contains("D:/private"))
        assertFalse(json.contains("/data/user/0"))
        assertFalse(json.contains("/home/player"))
        assertFalse(json.contains("server"))
        assertFalse(json.contains("content://"))
    }

    private fun catalog(): ParsedCatalog {
        val png = PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val first = LocalMap("map/one", "One", 1, 16, 16, 1, 1, "asset/one")
        val second = LocalMap("map/two", "Two", 2, 16, 16, 1, 1, "asset/two")
        return ParsedCatalog(
            romSha256 = "a".repeat(64),
            romCrc32 = "1234ABCD",
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            localMaps = LocalMapCatalog(
                maps = listOf(first, second),
                assets = mapOf("asset/one" to png, "asset/two" to png),
                scenes = listOf(
                    LocalMapScene(
                        key = "scene/test",
                        gridWidth = 2,
                        gridHeight = 1,
                        placements = listOf(
                            LocalMapScenePlacement("map/one", 1, 0, 0),
                            LocalMapScenePlacement("map/two", 2, 1, 0),
                        ),
                    ),
                ),
                pois = listOf(LocalMapPoi("poi/door", "map/one", 1, 0, 0, LocalMapPoiKind.PLACE, displayName = "Door")),
            ),
            capabilities = mapOf(
                RomCapability.LOCAL_MAP to CapabilityEvidence(
                    capability = RomCapability.LOCAL_MAP,
                    compatible = true,
                    confidence = 0.5,
                    status = CapabilityStatus.PARTIAL,
                    coveredRecords = 1,
                    expectedRecords = 2,
                    incompleteRecords = 1,
                    reasons = listOf("Skipped map from D:/private/game.gba"),
                ),
            ),
            diagnostics = listOf(
                "Loaded D:/private/game.gba",
                "Cached /data/user/0/com.darkaxt.dualdex/files/game.gba",
                "Imported /home/player/roms/game.gba",
                "Opened \\\\server\\share\\game.gba",
                "Selected content://provider/document/game",
            ),
        )
    }
}
