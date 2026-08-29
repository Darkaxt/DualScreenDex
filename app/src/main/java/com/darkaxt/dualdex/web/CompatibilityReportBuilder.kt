package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.companion.api.DiagnosticCacheView
import com.enrpau.dualscreendex.companion.api.DiagnosticEnvironmentView
import com.enrpau.dualscreendex.companion.api.DiagnosticMapView
import com.enrpau.dualscreendex.companion.api.DiagnosticRuntimeView
import com.enrpau.dualscreendex.companion.api.DiagnosticView
import com.enrpau.dualscreendex.companion.api.StateView
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.google.gson.GsonBuilder

object CompatibilityReportBuilder {
    fun build(
        base: DiagnosticView,
        catalog: ParsedCatalog,
        state: StateView,
        cacheStats: MapAssetRenderCacheStats,
        appVersion: String?,
        catalogSchemaVersion: Int,
        parserSchemaVersion: Int,
    ): DiagnosticView {
        val currentArea = state.currentAreaBaseId
        val localMap = catalog.localMaps.maps.firstOrNull { it.baseAreaId == currentArea }
        val scene = catalog.localMaps.scenes.firstOrNull { candidate ->
            candidate.placements.any { it.baseAreaId == currentArea }
        }
        val activePlacement = scene?.placements?.firstOrNull { it.baseAreaId == currentArea }
        val activeLocalMap = activePlacement?.let { placement ->
            catalog.localMaps.maps.firstOrNull { it.key == placement.localMapKey }
        } ?: localMap
        val atlasRegion = catalog.worldMaps.regions.firstOrNull { region ->
            region.locations.any { location -> currentArea in location.baseAreaIds }
        } ?: catalog.worldMaps.regions.firstOrNull()
        val presentation = when {
            scene != null -> "LOCAL_SCENE"
            localMap != null -> "LOCAL_MAP"
            atlasRegion != null -> "ATLAS"
            else -> "UNAVAILABLE"
        }
        val activeLocalKeys = when {
            scene != null -> scene.placements.mapTo(linkedSetOf()) { it.localMapKey }
            localMap != null -> setOf(localMap.key)
            else -> emptySet()
        }
        val position = state.currentMapPosition
        val playerPositionStatus = when {
            activeLocalMap == null -> "NOT_APPLICABLE"
            position == null -> "UNAVAILABLE"
            position.x !in 0 until activeLocalMap.gridWidth || position.y !in 0 until activeLocalMap.gridHeight ->
                "OUT_OF_BOUNDS"
            else -> "VALID"
        }
        val dynamicLighting = activeLocalMap?.imageAssetKey?.let { key ->
            key in catalog.localMaps.indexedAssets || key in catalog.localMaps.timedAssets
        } == true
        val gameTime = state.gameTime
        val lighting = when {
            activeLocalMap == null -> "NOT_APPLICABLE"
            !dynamicLighting -> "STATIC"
            gameTime?.hours != null && gameTime.minutes != null -> "LIVE_GAME_CLOCK"
            else -> "SAFE_DEFAULT"
        }
        val visiblePois = state.localMapPois.filter { it.localMapKey in activeLocalKeys }
        val sanitized = base.copy(
            romName = base.romName?.let(::sanitize),
            capabilities = base.capabilities.map { capability ->
                capability.copy(reasons = capability.reasons.map(::sanitize))
            },
            parserDiagnostics = base.parserDiagnostics.map(::sanitize),
        )
        return sanitized.copy(
            reportSchemaVersion = 2,
            environment = DiagnosticEnvironmentView(appVersion, catalogSchemaVersion, parserSchemaVersion),
            runtime = DiagnosticRuntimeView(
                retroArchConnection = state.retroArch.connection,
                contentResolution = state.retroArch.resolution,
                gameAccessReady = state.gameAccessReady,
                saveRamStatus = state.saveRam.status,
                saveAutosaveStatus = state.saveRam.autosaveStatus,
                saveCapabilities = state.saveRam.capabilities.toSortedMap(),
                catalogLoadingActive = state.loading.active,
                catalogLoadingPhase = state.loading.phase,
                catalogLoadingCompletedUnits = state.loading.completedUnits,
                catalogLoadingTotalUnits = state.loading.totalUnits,
            ),
            map = DiagnosticMapView(
                presentation = presentation,
                currentAreaBaseId = currentArea,
                currentAreaName = state.currentAreaName,
                localMapKey = activeLocalMap?.key,
                sceneKey = scene?.key,
                atlasRegionKey = atlasRegion?.key,
                playerPositionStatus = playerPositionStatus,
                playerX = position?.x,
                playerY = position?.y,
                lighting = lighting,
                totalPois = catalog.localMaps.pois.count { it.localMapKey in activeLocalKeys },
                visiblePois = visiblePois.size,
                collectedPois = visiblePois.count { it.state == "COLLECTED" },
                localMapStatus = catalog.capabilityStatus(RomCapability.LOCAL_MAP),
                worldMapStatus = catalog.capabilityStatus(RomCapability.WORLD_MAP),
                fallbackReason = when {
                    scene != null || localMap != null -> null
                    atlasRegion == null -> "NO_MAP_CAPABILITY"
                    currentArea == null -> "NO_CURRENT_AREA"
                    else -> "LOCAL_MAP_UNAVAILABLE"
                },
            ),
            cache = DiagnosticCacheView(
                entries = cacheStats.entries,
                encodedBytes = cacheStats.encodedBytes,
                hits = cacheStats.hits,
                renders = cacheStats.renders,
                evictions = cacheStats.evictions,
            ),
        )
    }

    private fun ParsedCatalog.capabilityStatus(capability: RomCapability): String =
        capabilities[capability]?.status?.name ?: CapabilityStatus.NOT_FOUND.name

    private fun sanitize(value: String): String = value.replace(PRIVATE_PATH, "[path omitted]")

    private val PRIVATE_PATH = Regex(
        """(?:[A-Za-z]:[\\/]|\\\\[^\\/\r\n]+[\\/]|/(?:data|storage|sdcard|home|Users|private|var|tmp|mnt|media|Volumes)/|(?:content|file)://)[^\r\n]*""",
        RegexOption.IGNORE_CASE,
    )
}

object CompatibilityReportSerializer {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    fun toBytes(report: DiagnosticView): ByteArray {
        val export = linkedMapOf<String, Any?>(
            "reportSchemaVersion" to report.reportSchemaVersion,
            "family" to report.family,
            "platform" to report.platform,
            "activeRulesetId" to report.activeRulesetId,
            "rulesetAssumed" to report.rulesetAssumed,
            "rulesets" to report.rulesets,
            "capabilities" to report.capabilities,
            "parserDiagnostics" to report.parserDiagnostics,
            "environment" to report.environment,
            "runtime" to report.runtime,
            "map" to report.map?.let { map ->
                linkedMapOf(
                    "presentation" to map.presentation,
                    "playerPositionStatus" to map.playerPositionStatus,
                    "lighting" to map.lighting,
                    "totalPois" to map.totalPois,
                    "visiblePois" to map.visiblePois,
                    "collectedPois" to map.collectedPois,
                    "localMapStatus" to map.localMapStatus,
                    "worldMapStatus" to map.worldMapStatus,
                    "fallbackReason" to map.fallbackReason,
                )
            },
            "cache" to report.cache,
            "privacy" to report.privacy,
        )
        return gson.toJson(export).toByteArray(Charsets.UTF_8)
    }
}
