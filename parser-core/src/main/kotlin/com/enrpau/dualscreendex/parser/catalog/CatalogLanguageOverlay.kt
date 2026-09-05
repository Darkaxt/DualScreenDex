package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import java.util.Collections

enum class LocalizedTextCapability {
    SPECIES_NAMES,
    SPECIES_DESCRIPTIONS,
    MOVE_NAMES,
    MOVE_DESCRIPTIONS,
    ABILITY_NAMES,
    ABILITY_DESCRIPTIONS,
    TYPE_NAMES,
    NATURE_NAMES,
    ITEM_NAMES,
    AREA_NAMES,
    LOCAL_MAP_NAMES,
    WORLD_REGION_NAMES,
    WORLD_LOCATION_NAMES,
    ENCOUNTER_AREA_NAMES,
    POI_TEXT,
}

class LocalizedCapabilityState(
    val status: CapabilityStatus,
    val confidence: Double,
    val coveredRecords: Int,
    val expectedRecords: Int,
    val reviewStatus: CapabilityReviewStatus = CapabilityReviewStatus.NONE,
    val validatorReviewRecommended: Boolean = false,
    reasons: List<String> = emptyList(),
) {
    val reasons: List<String> = Collections.unmodifiableList(reasons.toList())
    val incompleteRecords: Int = expectedRecords - coveredRecords

    init {
        require(confidence in 0.0..1.0) { "localized capability confidence must be in 0.0..1.0" }
        require(coveredRecords in 0..expectedRecords) {
            "localized capability coverage must remain within its expected total"
        }
        when (status) {
            CapabilityStatus.AVAILABLE -> require(expectedRecords > 0 && coveredRecords == expectedRecords) {
                "available localized capability must cover a non-empty expected domain"
            }
            CapabilityStatus.PARTIAL -> require(coveredRecords in 1 until expectedRecords) {
                "partial localized capability must cover only part of its expected domain"
            }
            CapabilityStatus.AMBIGUOUS,
            CapabilityStatus.NOT_FOUND,
            CapabilityStatus.NOT_APPLICABLE,
            -> require(coveredRecords == 0) { "unavailable localized capability cannot publish text" }
        }
    }

    override fun equals(other: Any?): Boolean = other is LocalizedCapabilityState &&
        status == other.status &&
        confidence == other.confidence &&
        coveredRecords == other.coveredRecords &&
        expectedRecords == other.expectedRecords &&
        reviewStatus == other.reviewStatus &&
        validatorReviewRecommended == other.validatorReviewRecommended &&
        reasons == other.reasons

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + coveredRecords
        result = 31 * result + expectedRecords
        result = 31 * result + reviewStatus.hashCode()
        result = 31 * result + validatorReviewRecommended.hashCode()
        result = 31 * result + reasons.hashCode()
        return result
    }

    companion object {
        fun available(records: Int, confidence: Double = 1.0, reasons: List<String> = emptyList()) =
            LocalizedCapabilityState(
                status = CapabilityStatus.AVAILABLE,
                confidence = confidence,
                coveredRecords = records,
                expectedRecords = records,
                reasons = reasons,
            )

        fun unavailable(
            status: CapabilityStatus,
            expectedRecords: Int,
            confidence: Double = 0.0,
            reasons: List<String> = emptyList(),
            reviewStatus: CapabilityReviewStatus = CapabilityReviewStatus.NONE,
            validatorReviewRecommended: Boolean = false,
        ) = LocalizedCapabilityState(
            status = status,
            confidence = confidence,
            coveredRecords = 0,
            expectedRecords = expectedRecords,
            reviewStatus = reviewStatus,
            validatorReviewRecommended = validatorReviewRecommended,
            reasons = reasons,
        )

        fun notFound(reason: String, expectedRecords: Int = 0) = unavailable(
            CapabilityStatus.NOT_FOUND,
            expectedRecords,
            reasons = listOf(reason),
        )

        fun notApplicable(reason: String, expectedRecords: Int = 0) = unavailable(
            CapabilityStatus.NOT_APPLICABLE,
            expectedRecords,
            confidence = 1.0,
            reasons = listOf(reason),
        )

        fun ambiguous(reason: String, expectedRecords: Int = 0) = unavailable(
            CapabilityStatus.AMBIGUOUS,
            expectedRecords,
            reasons = listOf(reason),
        )
    }
}

data class WorldLocationKey(
    val regionKey: String,
    val locationKey: String,
) {
    init {
        require(regionKey.isNotBlank() && locationKey.isNotBlank()) {
            "world-location text keys must be non-blank"
        }
        require(regionKey.length <= MAXIMUM_KEY_CHARACTERS && locationKey.length <= MAXIMUM_KEY_CHARACTERS) {
            "world-location text keys are too long"
        }
    }
}

class CatalogPoiText(
    displayName: CatalogField<String>? = null,
    displayNamesByTrainerGender: Map<Int, CatalogField<String>> = emptyMap(),
    itemDisplayName: CatalogField<String>? = null,
) {
    val displayName = displayName?.immutableTextField()
    val displayNamesByTrainerGender: Map<Int, CatalogField<String>> =
        immutableTextMap(displayNamesByTrainerGender) { it in 0..1 }
    val itemDisplayName = itemDisplayName?.immutableTextField()

    init {
        require(displayName != null || displayNamesByTrainerGender.isNotEmpty() || itemDisplayName != null) {
            "localized POI text must contain at least one value"
        }
    }

    override fun equals(other: Any?): Boolean = other is CatalogPoiText &&
        displayName == other.displayName &&
        displayNamesByTrainerGender == other.displayNamesByTrainerGender &&
        itemDisplayName == other.itemDisplayName

    override fun hashCode(): Int {
        var result = displayName?.hashCode() ?: 0
        result = 31 * result + displayNamesByTrainerGender.hashCode()
        result = 31 * result + (itemDisplayName?.hashCode() ?: 0)
        return result
    }
}

class CatalogLanguageOverlay(
    val language: LanguageTag,
    val overlayVersion: Long,
    localizedCapabilities: Map<LocalizedTextCapability, LocalizedCapabilityState>,
    speciesNames: Map<Int, CatalogField<String>> = emptyMap(),
    speciesDescriptions: Map<Int, CatalogField<String>> = emptyMap(),
    moveNames: Map<Int, CatalogField<String>> = emptyMap(),
    moveDescriptions: Map<Int, CatalogField<String>> = emptyMap(),
    abilityNames: Map<Int, CatalogField<String>> = emptyMap(),
    abilityDescriptions: Map<Int, CatalogField<String>> = emptyMap(),
    typeNames: Map<Int, CatalogField<String>> = emptyMap(),
    natureNames: Map<Int, CatalogField<String>> = emptyMap(),
    itemNames: Map<Int, CatalogField<String>> = emptyMap(),
    areaNames: Map<Int, CatalogField<String>> = emptyMap(),
    localMapNames: Map<String, CatalogField<String>> = emptyMap(),
    worldRegionNames: Map<String, CatalogField<String>> = emptyMap(),
    worldLocationNames: Map<WorldLocationKey, CatalogField<String>> = emptyMap(),
    encounterAreaNames: Map<Int, CatalogField<String>> = emptyMap(),
    poiTexts: Map<String, CatalogPoiText> = emptyMap(),
) {
    val localizedCapabilities: Map<LocalizedTextCapability, LocalizedCapabilityState> =
        Collections.unmodifiableMap(LinkedHashMap(localizedCapabilities))
    val speciesNames = immutableTextMap(speciesNames) { it >= 0 }
    val speciesDescriptions = immutableTextMap(speciesDescriptions) { it >= 0 }
    val moveNames = immutableTextMap(moveNames) { it >= 0 }
    val moveDescriptions = immutableTextMap(moveDescriptions) { it >= 0 }
    val abilityNames = immutableTextMap(abilityNames) { it >= 0 }
    val abilityDescriptions = immutableTextMap(abilityDescriptions) { it >= 0 }
    val typeNames = immutableTextMap(typeNames) { it >= 0 }
    val natureNames = immutableTextMap(natureNames) { it >= 0 }
    val itemNames = immutableTextMap(itemNames) { it in 0..0xFFFF }
    val areaNames = immutableTextMap(areaNames) { it in 0..0xFFFF }
    val localMapNames = immutableTextMap(localMapNames, ::isValidStringKey)
    val worldRegionNames = immutableTextMap(worldRegionNames, ::isValidStringKey)
    val worldLocationNames = immutableTextMap(worldLocationNames) { true }
    val encounterAreaNames = immutableTextMap(encounterAreaNames) { it >= 0 }
    val poiTexts: Map<String, CatalogPoiText> = Collections.unmodifiableMap(
        LinkedHashMap(poiTexts).also { values ->
            require(values.keys.all(::isValidStringKey)) { "localized POI keys must be bounded and non-blank" }
        },
    )

    init {
        require(overlayVersion > 0) { "catalog overlay version must be positive" }
        require(localizedCapabilities.keys == LocalizedTextCapability.entries.toSet()) {
            "catalog overlay must publish every localized capability state"
        }
        requireCoverage(LocalizedTextCapability.SPECIES_NAMES, speciesNames.size)
        requireCoverage(LocalizedTextCapability.SPECIES_DESCRIPTIONS, speciesDescriptions.size)
        requireCoverage(LocalizedTextCapability.MOVE_NAMES, moveNames.size)
        requireCoverage(LocalizedTextCapability.MOVE_DESCRIPTIONS, moveDescriptions.size)
        requireCoverage(LocalizedTextCapability.ABILITY_NAMES, abilityNames.size)
        requireCoverage(LocalizedTextCapability.ABILITY_DESCRIPTIONS, abilityDescriptions.size)
        requireCoverage(LocalizedTextCapability.TYPE_NAMES, typeNames.size)
        requireCoverage(LocalizedTextCapability.NATURE_NAMES, natureNames.size)
        requireCoverage(LocalizedTextCapability.ITEM_NAMES, itemNames.size)
        requireCoverage(LocalizedTextCapability.AREA_NAMES, areaNames.size)
        requireCoverage(LocalizedTextCapability.LOCAL_MAP_NAMES, localMapNames.size)
        requireCoverage(LocalizedTextCapability.WORLD_REGION_NAMES, worldRegionNames.size)
        requireCoverage(LocalizedTextCapability.WORLD_LOCATION_NAMES, worldLocationNames.size)
        requireCoverage(LocalizedTextCapability.ENCOUNTER_AREA_NAMES, encounterAreaNames.size)
        requireCoverage(LocalizedTextCapability.POI_TEXT, poiTexts.size)
        require(totalEntryCount() <= MAXIMUM_OVERLAY_ENTRIES) { "catalog overlay entry limit exceeded" }
        require(totalTextCharacters() <= MAXIMUM_OVERLAY_TEXT_CHARACTERS) {
            "catalog overlay text-character limit exceeded"
        }
    }

    internal fun validateKeys(catalog: ParsedCatalog) {
        requireSubset(speciesNames.keys, catalog.speciesById.keys, "species name", "species")
        val speciesDescriptionIds = catalog.speciesById
            .filter { (id, record) -> id > 0 && record.dexNumber.status != CapabilityStatus.NOT_APPLICABLE &&
                record.description.status != CapabilityStatus.NOT_APPLICABLE }
            .keys
        requireSubset(
            speciesDescriptions.keys,
            speciesDescriptionIds,
            "species description",
            "described species",
        )
        requireSubset(moveNames.keys, catalog.movesById.keys, "move name", "move")
        val describedMoveIds = catalog.movesById.keys.filterTo(hashSetOf()) { it > 0 }
        requireSubset(moveDescriptions.keys, describedMoveIds, "move description", "described move")
        requireSubset(abilityNames.keys, catalog.abilitiesById.keys, "ability name", "ability")
        val describedAbilityIds = catalog.abilitiesById.keys.filterTo(hashSetOf()) { it > 0 }
        requireSubset(
            abilityDescriptions.keys,
            describedAbilityIds,
            "ability description",
            "described ability",
        )
        requireSubset(typeNames.keys, catalog.typesById.keys, "type name", "type")
        requireSubset(natureNames.keys, catalog.naturesById.keys, "nature name", "nature")
        val itemIds = buildSet {
            addAll(catalog.captureBallsById.keys)
            addAll(catalog.localMaps.pois.mapNotNull { it.item?.itemId })
        }
        requireSubset(itemNames.keys, itemIds, "item name", "item")
        requireSubset(areaNames.keys, catalog.runtimeMetadata.areaBaseIds, "area name", "area")
        requireSubset(localMapNames.keys, catalog.localMaps.maps.mapTo(hashSetOf(), LocalMap::key), "local-map name", "map")
        requireSubset(
            worldRegionNames.keys,
            catalog.worldMaps.regions.mapTo(hashSetOf(), WorldMapRegion::key),
            "world-region name",
            "region",
        )
        val worldLocationKeys = catalog.worldMaps.regions.flatMapTo(hashSetOf()) { region ->
            region.locations.map { WorldLocationKey(region.key, it.key) }
        }
        requireSubset(worldLocationNames.keys, worldLocationKeys, "world-location name", "location")
        requireSubset(encounterAreaNames.keys, catalog.encounterAreas.mapTo(hashSetOf(), EncounterArea::id), "encounter name", "area")
        requireSubset(poiTexts.keys, catalog.localMaps.pois.mapTo(hashSetOf(), LocalMapPoi::key), "POI text", "POI")

        requireExpected(LocalizedTextCapability.SPECIES_NAMES, catalog.speciesById.size)
        requireExpected(
            LocalizedTextCapability.SPECIES_DESCRIPTIONS,
            speciesDescriptionIds.size,
        )
        requireExpected(LocalizedTextCapability.MOVE_NAMES, catalog.movesById.size)
        requireExpected(LocalizedTextCapability.MOVE_DESCRIPTIONS, describedMoveIds.size)
        requireExpected(LocalizedTextCapability.ABILITY_NAMES, catalog.abilitiesById.size)
        requireExpected(LocalizedTextCapability.ABILITY_DESCRIPTIONS, describedAbilityIds.size)
        requireExpected(LocalizedTextCapability.TYPE_NAMES, catalog.typesById.size)
        requireExpected(LocalizedTextCapability.NATURE_NAMES, catalog.naturesById.size)
        requireExpected(LocalizedTextCapability.ITEM_NAMES, itemIds.size)
        requireExpected(LocalizedTextCapability.AREA_NAMES, catalog.runtimeMetadata.areaBaseIds.size)
        requireExpected(LocalizedTextCapability.LOCAL_MAP_NAMES, catalog.localMaps.maps.size)
        requireExpected(LocalizedTextCapability.WORLD_REGION_NAMES, catalog.worldMaps.regions.size)
        requireExpected(LocalizedTextCapability.WORLD_LOCATION_NAMES, worldLocationKeys.size)
        requireExpected(LocalizedTextCapability.ENCOUNTER_AREA_NAMES, catalog.encounterAreas.size)
        requireExpected(LocalizedTextCapability.POI_TEXT, catalog.localMaps.pois.size)
    }

    private fun requireCoverage(capability: LocalizedTextCapability, entryCount: Int) {
        require(localizedCapabilities.getValue(capability).coveredRecords == entryCount) {
            "localized $capability coverage must equal its stored entry count"
        }
    }

    private fun requireExpected(capability: LocalizedTextCapability, expectedRecords: Int) {
        require(localizedCapabilities.getValue(capability).expectedRecords == expectedRecords) {
            "localized $capability expected coverage must match its shared entity domain"
        }
    }

    private fun totalEntryCount(): Int =
        speciesNames.size + speciesDescriptions.size + moveNames.size + moveDescriptions.size +
            abilityNames.size + abilityDescriptions.size + typeNames.size + natureNames.size + itemNames.size +
            areaNames.size + localMapNames.size + worldRegionNames.size + worldLocationNames.size +
            encounterAreaNames.size + poiTexts.size

    private fun totalTextCharacters(): Long = sequence {
        yieldAll(speciesNames.values)
        yieldAll(speciesDescriptions.values)
        yieldAll(moveNames.values)
        yieldAll(moveDescriptions.values)
        yieldAll(abilityNames.values)
        yieldAll(abilityDescriptions.values)
        yieldAll(typeNames.values)
        yieldAll(natureNames.values)
        yieldAll(itemNames.values)
        yieldAll(areaNames.values)
        yieldAll(localMapNames.values)
        yieldAll(worldRegionNames.values)
        yieldAll(worldLocationNames.values)
        yieldAll(encounterAreaNames.values)
        poiTexts.values.forEach { poi ->
            poi.displayName?.let { yield(it) }
            yieldAll(poi.displayNamesByTrainerGender.values)
            poi.itemDisplayName?.let { yield(it) }
        }
    }.sumOf { it.value.orEmpty().length.toLong() }

    override fun equals(other: Any?): Boolean = other is CatalogLanguageOverlay &&
        language == other.language && overlayVersion == other.overlayVersion &&
        localizedCapabilities == other.localizedCapabilities && speciesNames == other.speciesNames &&
        speciesDescriptions == other.speciesDescriptions && moveNames == other.moveNames &&
        moveDescriptions == other.moveDescriptions && abilityNames == other.abilityNames &&
        abilityDescriptions == other.abilityDescriptions && typeNames == other.typeNames &&
        natureNames == other.natureNames && itemNames == other.itemNames && areaNames == other.areaNames &&
        localMapNames == other.localMapNames && worldRegionNames == other.worldRegionNames &&
        worldLocationNames == other.worldLocationNames && encounterAreaNames == other.encounterAreaNames &&
        poiTexts == other.poiTexts

    override fun hashCode(): Int = listOf(
        language, overlayVersion, localizedCapabilities, speciesNames, speciesDescriptions, moveNames,
        moveDescriptions, abilityNames, abilityDescriptions, typeNames, natureNames, itemNames, areaNames,
        localMapNames, worldRegionNames, worldLocationNames, encounterAreaNames, poiTexts,
    ).fold(1) { result, value -> 31 * result + value.hashCode() }

    companion object {
        fun unavailable(
            language: LanguageTag,
            overlayVersion: Long,
            expectedRecords: Map<LocalizedTextCapability, Int>,
        ) = CatalogLanguageOverlay(
            language = language,
            overlayVersion = overlayVersion,
            localizedCapabilities = LocalizedTextCapability.entries.associateWith { capability ->
                LocalizedCapabilityState.notFound(
                    "localized projection was not materialized",
                    expectedRecords.getValue(capability),
                )
            },
        )
    }
}

class CatalogLocalization(
    val manifest: RomLanguageManifest,
    overlays: Map<LanguageTag, CatalogLanguageOverlay> = emptyMap(),
) {
    val overlays: Map<LanguageTag, CatalogLanguageOverlay> = Collections.unmodifiableMap(LinkedHashMap(overlays))

    init {
        require(overlays.size <= MAXIMUM_LANGUAGE_OVERLAYS) { "catalog language overlay count limit exceeded" }
        require(overlays.all { (language, overlay) -> language == overlay.language }) {
            "catalog overlay map keys must match overlay languages"
        }
        val resolvedLanguages = manifest.projections
            .filter { it.status == LanguageResolutionStatus.RESOLVED }
            .mapTo(linkedSetOf()) { it.language }
        require(overlays.keys == resolvedLanguages) {
            "catalog overlays must exactly cover resolved language projections"
        }
        if (manifest.status == LanguageResolutionStatus.RESOLVED) {
            require(manifest.defaultLanguage in overlays) {
                "resolved catalog language default requires an overlay"
            }
        }
    }

    fun overlay(language: LanguageTag): CatalogLanguageOverlay? = overlays[language]

    fun defaultOverlay(): CatalogLanguageOverlay? = manifest.defaultLanguage?.let(overlays::get)

    internal fun validateKeys(catalog: ParsedCatalog) = overlays.values.forEach { it.validateKeys(catalog) }

    override fun equals(other: Any?): Boolean = other is CatalogLocalization &&
        manifest == other.manifest && overlays == other.overlays

    override fun hashCode(): Int = 31 * manifest.hashCode() + overlays.hashCode()

    companion object {
        const val MAXIMUM_LANGUAGE_OVERLAYS = 16
        val UNKNOWN = CatalogLocalization(RomLanguageManifest.UNKNOWN)
    }
}

private fun CatalogField<String>.immutableTextField(): CatalogField<String> {
    require(status == CapabilityStatus.AVAILABLE && !value.isNullOrBlank()) {
        "localized text maps may contain only available non-blank values"
    }
    require(value.length <= MAXIMUM_TEXT_CHARACTERS) { "localized text value is too long" }
    return copy(reasons = Collections.unmodifiableList(reasons.toList()))
}

private fun <K> immutableTextMap(
    source: Map<K, CatalogField<String>>,
    validKey: (K) -> Boolean,
): Map<K, CatalogField<String>> {
    require(source.size <= MAXIMUM_TEXT_MAP_ENTRIES) { "localized text map entry limit exceeded" }
    require(source.keys.all(validKey)) { "localized text map contains an invalid key" }
    return Collections.unmodifiableMap(
        LinkedHashMap(source.mapValues { (_, value) -> value.immutableTextField() }),
    )
}

private fun isValidStringKey(value: String): Boolean =
    value.isNotBlank() && value.length <= MAXIMUM_KEY_CHARACTERS

private fun <T> requireSubset(actual: Set<T>, expected: Set<T>, label: String, target: String) {
    require(actual.all(expected::contains)) { "localized $label references an unknown shared $target" }
}

private const val MAXIMUM_TEXT_MAP_ENTRIES = 65_536
private const val MAXIMUM_TEXT_CHARACTERS = 4_096
private const val MAXIMUM_KEY_CHARACTERS = 512
private const val MAXIMUM_OVERLAY_ENTRIES = 262_144
private const val MAXIMUM_OVERLAY_TEXT_CHARACTERS = 16L * 1024 * 1024
