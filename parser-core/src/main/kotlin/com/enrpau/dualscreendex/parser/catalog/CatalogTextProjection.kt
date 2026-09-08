package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability

class CatalogTextProjection private constructor(
    private val catalog: ParsedCatalog,
    val overlay: CatalogLanguageOverlay?,
) {
    val language: LanguageTag? = overlay?.language
    val overlayVersion: Long? = overlay?.overlayVersion
    val localizedCapabilities: Map<LocalizedTextCapability, LocalizedCapabilityState> =
        overlay?.localizedCapabilities.orEmpty()

    private val allowSharedText = overlay == null &&
        catalog.languageManifest.status == LanguageResolutionStatus.UNKNOWN &&
        catalog.localizedTextByLanguage.isEmpty()

    fun speciesName(id: Int): String? = text(overlay?.speciesNames?.get(id)?.value) {
        catalog.speciesById[id]?.name?.value
    }

    fun speciesDescription(id: Int): String? = text(overlay?.speciesDescriptions?.get(id)?.value) {
        catalog.speciesById[id]?.description?.value
    }

    fun moveName(id: Int): String? = text(overlay?.moveNames?.get(id)?.value) {
        catalog.movesById[id]?.name?.value
    }

    fun moveDescription(id: Int): String? = text(overlay?.moveDescriptions?.get(id)?.value) {
        catalog.movesById[id]?.effectText?.value
    }

    fun abilityName(id: Int): String? = text(overlay?.abilityNames?.get(id)?.value) {
        catalog.abilitiesById[id]?.name?.value
    }

    fun abilityDescription(id: Int): String? = text(overlay?.abilityDescriptions?.get(id)?.value) {
        catalog.abilitiesById[id]?.description?.value
    }

    fun typeName(id: Int): String? = text(overlay?.typeNames?.get(id)?.value) {
        catalog.typesById[id]?.name?.value
    }

    fun natureName(id: Int): String? = text(overlay?.natureNames?.get(id)?.value) {
        catalog.naturesById[id]?.name
    }

    fun itemName(id: Int): String? = text(overlay?.itemNames?.get(id)?.value) {
        catalog.captureBallsById[id]?.name?.value ?: catalog.localMaps.pois
            .firstOrNull { it.item?.itemId == id }
            ?.item
            ?.displayName
    }

    fun areaName(baseAreaId: Int): String? = text(overlay?.areaNames?.get(baseAreaId)?.value) {
        catalog.runtimeMetadata.areaNamesByBaseId[baseAreaId]
    }

    fun localMapName(key: String): String? = text(overlay?.localMapNames?.get(key)?.value) {
        catalog.localMaps.maps.firstOrNull { it.key == key }?.displayName
    }

    fun worldRegionName(key: String): String? = text(overlay?.worldRegionNames?.get(key)?.value) {
        catalog.worldMaps.regions.firstOrNull { it.key == key }?.displayName
    }

    fun worldLocationName(regionKey: String, locationKey: String): String? = text(
        overlay?.worldLocationNames?.get(WorldLocationKey(regionKey, locationKey))?.value,
    ) {
        catalog.worldMaps.regions.firstOrNull { it.key == regionKey }
            ?.locations
            ?.firstOrNull { it.key == locationKey }
            ?.displayName
    }

    fun encounterAreaName(id: Int): String? = text(overlay?.encounterAreaNames?.get(id)?.value) {
        catalog.encounterAreas.firstOrNull { it.id == id }?.name?.value
    }

    private val poisByKey = catalog.localMaps.pois.associateBy(LocalMapPoi::key)
    private val mapsByBaseArea = catalog.localMaps.maps.associateBy(LocalMap::baseAreaId)
    private val poiText = overlay?.let {
        CatalogPoiTextResolver(catalog.localMaps, it.poiTexts, it.itemNames, it.localMapNames,
            poiTextAuthorized(catalog.capabilities[RomCapability.LOCAL_MAP]))
    }

    fun poiDisplayName(key: String, trainerGender: Int? = null): String? {
        val poi = poisByKey[key] ?: return null
        if (overlay != null) {
            if (poi.textObligation == LocalMapPoiTextObligation.ITEM_NAME) return null
            return poiText?.label(poi, trainerGender)
        }
        if (!allowSharedText) return null
        return when (poi.textObligation) {
            LocalMapPoiTextObligation.DESTINATION_NAME -> mapsByBaseArea[poi.destinationBaseAreaId]?.displayName
            LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT -> if (poi.displayNamesByTrainerGender.keys == setOf(0, 1)) {
                trainerGender?.let(poi.displayNamesByTrainerGender::get)
                    ?: poi.displayNamesByTrainerGender.values.distinct().singleOrNull().takeIf { trainerGender == null }
            } else null
            else -> trainerGender?.let(poi.displayNamesByTrainerGender::get)
                ?: poi.displayName
                ?: poi.displayNamesByTrainerGender.toSortedMap().values.firstOrNull()
        }
    }

    fun poiItemName(key: String, itemId: Int?): String? {
        val poi = poisByKey[key] ?: return null
        if (poi.item == null || poi.item.itemId != itemId) return null
        if (overlay != null) {
            if (poi.textObligation != LocalMapPoiTextObligation.ITEM_NAME) return null
            return poiText?.label(poi)
        }
        return if (allowSharedText) itemId?.let(::itemName) ?: poi.item.displayName else null
    }

    fun poiLabel(key: String, trainerGender: Int? = null): String? {
        val poi = poisByKey[key] ?: return null
        return if (poi.item != null) poiItemName(key, poi.item.itemId) else poiDisplayName(key, trainerGender)
    }

    private inline fun text(localized: String?, shared: () -> String?): String? =
        localized ?: if (allowSharedText) shared() else null

    companion object {
        fun default(catalog: ParsedCatalog): CatalogTextProjection =
            CatalogTextProjection(catalog, catalog.defaultLocalizedText())

        fun forLanguage(catalog: ParsedCatalog, language: LanguageTag): CatalogTextProjection? =
            catalog.localizedText(language)?.let { overlay -> CatalogTextProjection(catalog, overlay) }
    }
}

/** Shared by extraction, reopen validation and projection: references never copy localized strings. */
internal class CatalogPoiTextResolver(
    private val localMaps: LocalMapCatalog,
    private val poiTexts: Map<String, CatalogPoiText>,
    private val itemNames: Map<Int, CatalogField<String>>,
    private val localMapNames: Map<String, CatalogField<String>>,
    private val authorized: Boolean = true,
) {
    private val mapsByBaseArea = localMaps.maps.groupBy(LocalMap::baseAreaId)
        .mapValues { (_, maps) -> maps.singleOrNull() }

    fun label(poi: LocalMapPoi, trainerGender: Int? = null): String? {
        if (!authorized) return null
        val direct = poiTexts[poi.key]
        return when (poi.textObligation) {
            LocalMapPoiTextObligation.DIRECT_TEXT -> direct?.displayName?.value
            LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT -> {
                val names = direct?.displayNamesByTrainerGender.orEmpty()
                if (names.keys != setOf(0, 1)) null
                else if (trainerGender != null) names[trainerGender]?.value
                else names.values.map { it.value }.distinct().singleOrNull()
            }
            LocalMapPoiTextObligation.ITEM_NAME -> poi.item?.let { item ->
                if (item.itemId != null) itemNames[item.itemId]?.value else direct?.itemDisplayName?.value
            }
            LocalMapPoiTextObligation.DESTINATION_NAME -> mapsByBaseArea[poi.destinationBaseAreaId]
                ?.let { localMapNames[it.key]?.value }
            LocalMapPoiTextObligation.UNRESOLVED -> null
        }
    }

    fun coveredRecords(): Int = if (!authorized) 0 else localMaps.pois.count { poi ->
        if (poi.textObligation == LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT) {
            label(poi, 0) != null && label(poi, 1) != null
        } else label(poi) != null
    }
}

internal fun poiTextAuthorized(evidence: CapabilityEvidence?): Boolean = evidence?.status !in setOf(
    CapabilityStatus.AMBIGUOUS, CapabilityStatus.NOT_FOUND, CapabilityStatus.NOT_APPLICABLE,
)

fun ParsedCatalog.defaultTextProjection(): CatalogTextProjection = CatalogTextProjection.default(this)

fun ParsedCatalog.textProjection(language: LanguageTag): CatalogTextProjection? =
    CatalogTextProjection.forLanguage(this, language)
