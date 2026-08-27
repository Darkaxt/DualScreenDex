package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

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
            .mapNotNull { species -> species.dexNumber.value?.takeIf { it > 0 }?.let { species.id } }
            .toSet()
            .takeIf { candidates ->
                candidates.isNotEmpty() && candidates.all(catalog.speciesById::containsKey)
            }
            .orEmpty()
        val mapsByArea = catalog.localMaps.maps.groupBy { it.baseAreaId }
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
                val names = buildSet {
                    catalog.runtimeMetadata.areaNamesByBaseId[baseAreaId]
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let(::add)
                    mapsByArea[baseAreaId].orEmpty()
                        .mapNotNull { it.displayName?.trim()?.takeIf(String::isNotEmpty) }
                        .forEach(::add)
                    catalog.worldMaps.regions.asSequence()
                        .flatMap { it.locations.asSequence() }
                        .filter { baseAreaId in it.baseAreaIds }
                        .map { it.displayName.trim() }
                        .filter(String::isNotEmpty)
                        .forEach(::add)
                }
                val displayName = names.singleOrNull() ?: return@mapNotNull null
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
}
