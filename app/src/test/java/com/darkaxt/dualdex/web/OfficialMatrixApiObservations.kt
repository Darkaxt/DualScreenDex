package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.companion.api.BootstrapView
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.LocalMapNameDisposition
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability.*
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.defaultTextProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.google.gson.JsonObject

/** Internal consistency observations, not linguistic expectations or semantic acceptance. */
internal object OfficialMatrixApiObservations {
    private data class TextKey(val first: String, val second: String? = null)
    private fun key(value: Any) = TextKey(value.toString())
    private fun <K : Any> Map<K, CatalogField<String>>.texts() = map { key(it.key) to it.value.value }
    private fun renderAnonymousPlayerPlaceholder(value: String): String = value
        .replace("{PLAYER}'s", "Your", ignoreCase = true)
        .replace("{PLAYER}’s", "Your", ignoreCase = true)
        .replace("{PLAYER}", "You", ignoreCase = true)

    fun observe(catalog: ParsedCatalog, response: BootstrapView): JsonObject {
        val api = requireNotNull(response.catalog)
        val text = catalog.defaultTextProjection()
        val overlay = requireNotNull(text.overlay)
        val fields = JsonObject()
        val inspected = mutableSetOf<LocalizedTextCapability>()
        var mixed = 0
        var fallback = 0
        var shared = 0
        fun inspect(
            capability: LocalizedTextCapability,
            selected: List<Pair<TextKey, String?>>,
            projected: List<Pair<TextKey, String?>>,
            observed: List<Pair<TextKey, String?>>,
            backing: List<String?>,
            normalizeExpected: (String) -> String = { it },
        ) {
            check(inspected.add(capability)) { "projection group inspected twice" }
            val projectedKeys = projected.mapTo(hashSetOf()) { it.first }
            val selectedText = selected.mapNotNull { (key, value) ->
                value?.takeIf { key in projectedKeys }?.let { key to normalizeExpected(it) }
            }.toMap()
            val projectedText = projected.mapNotNull { (key, value) ->
                value?.let { key to normalizeExpected(it) }
            }.toMap()
            val grouped = observed.groupBy({ it.first }, { it.second })
            val conflicting = grouped.values.any { it.distinct().size > 1 }
            // Preserve the first actual value; conflicts remain explicit in the measurement.
            val observedText = grouped.mapValues { it.value.first() }.filterValues { it != null }
            if (conflicting || selectedText != projectedText || projectedText != observedText) mixed++
            if ((projectedText.keys + observedText.keys).any { selectedText[it] == null }) fallback++
            if (backing.any { !it.isNullOrBlank() }) shared++
            fields.add(capability.name, JsonObject().apply {
                observedText.entries.sortedWith(compareBy({ it.key.first }, { it.key.second.orEmpty() })).forEach { (key, value) ->
                    if (key.second == null) addProperty(key.first, value)
                    else {
                        val region = getAsJsonObject(key.first) ?: JsonObject().also { add(key.first, it) }
                        region.addProperty(key.second, value)
                    }
                }
            })
        }
        val species = catalog.navigableSpecies()
        inspect(SPECIES_NAMES, overlay.speciesNames.texts(), species.map { key(it.id) to text.speciesName(it.id) },
            api.species.map { key(it.id) to it.name }, species.map { it.name.value })
        inspect(SPECIES_DESCRIPTIONS, overlay.speciesDescriptions.texts(), species.map { key(it.id) to text.speciesDescription(it.id) },
            api.species.map { key(it.id) to it.description }, species.map { it.description.value })
        val moves = catalog.movesById.values
        inspect(MOVE_NAMES, overlay.moveNames.texts(), moves.map { key(it.id) to text.moveName(it.id) },
            api.moves.map { key(it.id) to it.name }, moves.map { it.name.value })
        inspect(MOVE_DESCRIPTIONS, overlay.moveDescriptions.texts(), moves.map { key(it.id) to text.moveDescription(it.id) },
            api.moves.map { key(it.id) to it.description }, moves.map { it.effectText.value })
        val abilityIds = species.flatMap { it.abilityIds.value.orEmpty() }.filter { it > 0 }.toSet()
        val abilities = abilityIds.mapNotNull(catalog.abilitiesById::get)
        val apiAbilities = api.species.flatMap { it.abilities }
        inspect(ABILITY_NAMES, overlay.abilityNames.texts(), abilities.map { key(it.id) to text.abilityName(it.id) },
            apiAbilities.map { key(it.id) to it.name }, abilities.map { it.name.value })
        inspect(ABILITY_DESCRIPTIONS, overlay.abilityDescriptions.texts(), abilities.map { key(it.id) to text.abilityDescription(it.id) },
            apiAbilities.map { key(it.id) to it.description }, abilities.map { it.description.value })
        val types = catalog.typesById.values
        inspect(TYPE_NAMES, overlay.typeNames.texts(), types.map { key(it.id) to text.typeName(it.id) },
            api.types.map { key(it.id) to it.name }, types.map { it.name.value })
        val natures = catalog.naturesById
        inspect(NATURE_NAMES, overlay.natureNames.texts(), natures.keys.map { key(it) to text.natureName(it) },
            api.natures.map { key(it.id) to it.name }, natures.values.map { it.name })
        val pois = catalog.localMaps.pois
        val items = catalog.captureBallsById.keys + pois.mapNotNull { it.item?.itemId } + overlay.itemNames.keys
        inspect(ITEM_NAMES, overlay.itemNames.texts(), items.map { key(it) to text.itemName(it) },
            api.balls.map { key(it.id) to it.name } + response.state.localMapPois.mapNotNull { poi ->
                poi.itemId?.let { key(it) to poi.itemName }
            }, catalog.captureBallsById.values.map { it.name.value } + pois.map { it.item?.displayName })
        val areaIds = overlay.areaNames.keys
        inspect(AREA_NAMES, overlay.areaNames.texts(), areaIds.map { key(it) to text.areaName(it) },
            response.state.areaGuide?.areas.orEmpty()
                .filter { it.baseAreaId in areaIds }
                .map { key(it.baseAreaId) to it.name },
            areaIds.map { catalog.runtimeMetadata.areaNamesByBaseId[it] })
        val maps = catalog.localMaps.maps
        inspect(LOCAL_MAP_NAMES, overlay.localMapNames.texts(), maps.map { key(it.key) to text.localMapName(it.key) },
            api.localMaps.map { key(it.key) to it.displayName }, maps.map { it.displayName })
        val regions = catalog.worldMaps.regions
        inspect(WORLD_REGION_NAMES, overlay.worldRegionNames.texts(), regions.map { key(it.key) to text.worldRegionName(it.key) },
            api.worldMaps.map { key(it.key) to it.displayName }, regions.map { it.displayName })
        inspect(WORLD_LOCATION_NAMES,
            overlay.worldLocationNames.map { TextKey(it.key.regionKey, it.key.locationKey) to it.value.value },
            regions.flatMap { region -> region.locations.map { TextKey(region.key, it.key) to text.worldLocationName(region.key, it.key) } },
            api.worldMaps.flatMap { region -> region.locations.map { TextKey(region.key, it.key) to it.displayName } },
            regions.flatMap { it.locations }.map { it.displayName })
        inspect(ENCOUNTER_AREA_NAMES, overlay.encounterAreaNames.texts(), catalog.encounterAreas.map { key(it.id) to text.encounterAreaName(it.id) },
            api.areas.map { key(it.id) to it.name }, catalog.encounterAreas.map { it.name.value })
        val mapsByArea = maps.groupBy { it.baseAreaId }
        val selectedPois = pois.map { poi ->
            val direct = overlay.poiTexts[poi.key]
            val value = when (poi.textObligation) {
                LocalMapPoiTextObligation.DIRECT_TEXT -> direct?.displayName?.value
                LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT -> direct?.displayNamesByTrainerGender
                    ?.takeIf { it.keys == setOf(0, 1) }?.values?.map { it.value }?.distinct()?.singleOrNull()
                LocalMapPoiTextObligation.ITEM_NAME -> poi.item?.let { item ->
                    if (item.itemId != null) overlay.itemNames[item.itemId]?.value else direct?.itemDisplayName?.value
                }
                LocalMapPoiTextObligation.DESTINATION_NAME -> mapsByArea[poi.destinationBaseAreaId]?.singleOrNull()
                    ?.takeIf { it.nameDisposition == LocalMapNameDisposition.STATIC_NAME_REQUIRED }
                    ?.let { overlay.localMapNames[it.key]?.value }
                LocalMapPoiTextObligation.UNRESOLVED -> null
            }
            key(poi.key) to value
        }
        inspect(POI_TEXT, selectedPois, pois.map { key(it.key) to text.poiLabel(it.key) },
            response.state.localMapPois.map { key(it.key) to if (it.category in setOf("AVAILABLE_ITEM", "COLLECTED_ITEM")) it.itemName else it.displayName },
            pois.flatMap { listOf(it.displayName, it.item?.displayName) + it.displayNamesByTrainerGender.values },
            ::renderAnonymousPlayerPlaceholder)
        check(inspected == LocalizedTextCapability.entries.toSet()) { "uninspected projection group" }
        val apiTypes = api.types.groupBy { it.id }
        val typeMismatches = (catalog.typesById.keys + apiTypes.keys).count { id ->
            val observed = apiTypes[id]?.singleOrNull()
            val projected = text.typeName(id)
            id !in catalog.typesById || observed == null || projected.isNullOrBlank() || observed.name != projected
        }
        return JsonObject().apply {
            add("fields", fields)
            add("measurements", JsonObject().apply {
                add("projectionIsolation", JsonObject().apply {
                    addProperty("fieldsChecked", inspected.size)
                    addProperty("mixedFields", mixed)
                    addProperty("fallbackFields", fallback)
                    addProperty("sharedTextFields", shared)
                })
                add("typeSemantics", JsonObject().apply {
                    addProperty("typesChecked", types.size)
                    addProperty("unresolvedTypes", types.count { it.semanticRole.status != CapabilityStatus.AVAILABLE || it.semanticRole.value == null })
                    addProperty("mismatchedTypes", typeMismatches)
                })
            })
        }
    }
}
