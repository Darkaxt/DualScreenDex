package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag

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

    fun poiDisplayName(key: String, trainerGender: Int? = null): String? {
        val localized = overlay?.poiTexts?.get(key)?.let { poi ->
            trainerGender?.let(poi.displayNamesByTrainerGender::get)?.value
                ?: poi.displayName?.value
                ?: poi.displayNamesByTrainerGender.toSortedMap().values.firstOrNull()?.value
        }
        return text(localized) {
            catalog.localMaps.pois.firstOrNull { it.key == key }?.let { poi ->
                trainerGender?.let(poi.displayNamesByTrainerGender::get)
                    ?: poi.displayName
                    ?: poi.displayNamesByTrainerGender.toSortedMap().values.firstOrNull()
            }
        }
    }

    fun poiItemName(key: String, itemId: Int?): String? = itemId?.let(::itemName) ?: text(
        overlay?.poiTexts?.get(key)?.itemDisplayName?.value,
    ) {
        catalog.localMaps.pois.firstOrNull { it.key == key }?.item?.displayName
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

fun ParsedCatalog.defaultTextProjection(): CatalogTextProjection = CatalogTextProjection.default(this)

fun ParsedCatalog.textProjection(language: LanguageTag): CatalogTextProjection? =
    CatalogTextProjection.forLanguage(this, language)
