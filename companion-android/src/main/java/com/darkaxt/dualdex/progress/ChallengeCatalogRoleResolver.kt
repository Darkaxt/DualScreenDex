package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.defaultTextProjection

/** Resolves only structural catalog roles; it never selects by ROM identity or ancestry. */
object ChallengeCatalogRoleResolver {
    fun resolve(catalog: ParsedCatalog): ChallengeCatalogBindings {
        val badgeCount = catalog.runtimeMetadata.gen3RuntimeMemoryLayout
            ?.saveRuntimeAbi
            ?.trainer
            ?.badgeFlags
            ?.size
            ?.takeIf { it > 0 }
        val regionalSpeciesIds = catalog.navigableSpecies()
            .mapTo(linkedSetOf()) { it.id }
        val text = catalog.defaultTextProjection()
        val mapsByArea = catalog.localMaps.maps.groupBy { it.baseAreaId }
        val allWorldLocations = catalog.worldMaps.regions.flatMap { region ->
            region.locations.map { location -> region.key to location }
        }
        val useStructuralWorldLabels = allWorldLocations.isNotEmpty() &&
            allWorldLocations.none { (regionKey, location) ->
                !text.worldLocationName(regionKey, location.key).isNullOrBlank()
            }
        val areaCollectibles = catalog.localMaps.pois
            .asSequence()
            .filter { poi ->
                poi.kind in setOf(LocalMapPoiKind.VISIBLE_ITEM, LocalMapPoiKind.HIDDEN_ITEM) &&
                    poi.item?.collectionFlagId != null
            }
            .groupBy { it.baseAreaId }
            .entries
            .sortedBy { it.key }
            .mapNotNull { (baseAreaId, pois) ->
                val matchingWorldLocations = allWorldLocations.filter { (_, location) ->
                    baseAreaId in location.baseAreaIds
                }
                val localizedNames = buildSet {
                    text.areaName(baseAreaId)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let(::add)
                    mapsByArea[baseAreaId].orEmpty()
                        .mapNotNull { map -> text.localMapName(map.key)?.trim()?.takeIf(String::isNotEmpty) }
                        .forEach(::add)
                    matchingWorldLocations
                        .mapNotNull { (regionKey, location) ->
                            text.worldLocationName(regionKey, location.key)?.trim()
                        }
                        .filter(String::isNotEmpty)
                        .forEach(::add)
                }
                val displayName = localizedNames.singleOrNull()
                    ?: localizedNames.takeIf { it.isEmpty() && useStructuralWorldLabels }?.let {
                        matchingWorldLocations.mapNotNull { (_, location) ->
                            location.key.removePrefix(WORLD_SECTION_KEY_PREFIX)
                                .toIntOrNull()
                                ?.let { section -> "Map section $section" }
                        }.distinct().singleOrNull()
                    }
                    ?: return@mapNotNull null
                val keys = pois.mapTo(sortedSetOf()) { it.key }
                AreaCollectibleBinding(
                    key = "base-$baseAreaId",
                    displayName = displayName,
                    poiKeys = keys,
                    baseAreaId = baseAreaId,
                )
            }
        return ChallengeCatalogBindings(
            badgeCount = badgeCount,
            regionalSpeciesIds = regionalSpeciesIds,
            areaCollectibles = areaCollectibles,
            // Trainer identities and game-specific mechanic adapters do not yet have parser roles.
            gymLeaders = emptyList(),
            provenAdapters = emptySet(),
        )
    }

    private const val WORLD_SECTION_KEY_PREFIX = "section-"
}
