package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability

internal data class CatalogLocalizedTextExtraction(
    val speciesById: Map<Int, SpeciesRecord>,
    val movesById: Map<Int, MoveRecord>,
    val typesById: Map<Int, TypeRecord>,
    val abilitiesById: Map<Int, AbilityRecord>,
    val naturesById: Map<Int, NatureRecord>,
    val encounterAreas: List<EncounterArea>,
    val captureBallsById: Map<Int, CaptureBallRecord>,
    val runtimeMetadata: CatalogRuntimeMetadata,
    val worldMaps: WorldMapCatalog,
    val localMaps: LocalMapCatalog,
    val capabilities: Map<RomCapability, CapabilityEvidence>,
    val localization: CatalogLocalization,
)

internal object CatalogLocalizedTextExtractor {
    fun extract(
        manifest: RomLanguageManifest,
        speciesById: Map<Int, SpeciesRecord>,
        movesById: Map<Int, MoveRecord>,
        typesById: Map<Int, TypeRecord> = emptyMap(),
        abilitiesById: Map<Int, AbilityRecord>,
        naturesById: Map<Int, NatureRecord>,
        encounterAreas: List<EncounterArea> = emptyList(),
        captureBallsById: Map<Int, CaptureBallRecord> = emptyMap(),
        runtimeMetadata: CatalogRuntimeMetadata = CatalogRuntimeMetadata(),
        worldMaps: WorldMapCatalog = WorldMapCatalog(),
        localMaps: LocalMapCatalog = LocalMapCatalog(),
        capabilities: Map<RomCapability, CapabilityEvidence>,
        additionalOverlays: Map<LanguageTag, CatalogLanguageOverlay> = emptyMap(),
    ): CatalogLocalizedTextExtraction {
        val speciesNames = authorizedFields(
            speciesById,
            capabilities[RomCapability.SPECIES_NAMES],
        ) { it.name }
        val speciesDescriptions = authorizedFields(
            speciesById.filter { (id, record) ->
                id > 0 && record.dexNumber.status != CapabilityStatus.NOT_APPLICABLE &&
                    record.description.status != CapabilityStatus.NOT_APPLICABLE
            },
            capabilities[RomCapability.POKEDEX_DESCRIPTIONS],
        ) { it.description }
        val moveNames = authorizedFields(movesById, capabilities[RomCapability.MOVE_CATALOG]) { it.name }
        val moveDescriptions = authorizedFields(
            movesById.filterKeys { it > 0 },
            capabilities[RomCapability.MOVE_DESCRIPTIONS],
        ) { it.effectText }
        val abilityNames = authorizedFields(
            abilitiesById,
            capabilities[RomCapability.ABILITIES],
        ) { it.name }
        val abilityDescriptions = authorizedFields(
            abilitiesById.filterKeys { it > 0 },
            capabilities[RomCapability.ABILITY_DESCRIPTIONS],
        ) { it.description }
        val typeNames = availableFields(typesById) { it.name }
        val natureNames = naturesById.mapNotNull { (id, nature) ->
            nature.name?.takeIf(String::isNotBlank)?.let { id to CatalogField.available(it) }
        }.toMap(linkedMapOf())
        val itemNames = extractItemNames(captureBallsById, localMaps)
        val areaNames = runtimeMetadata.areaNamesByBaseId.mapNotNull { (id, name) ->
            name.takeIf(String::isNotBlank)?.let { id to CatalogField.available(it) }
        }.toMap(linkedMapOf())
        val localMapNames = authorizedMap(capabilities[RomCapability.LOCAL_MAP]) {
            localMaps.maps.mapNotNull { map ->
                map.displayName?.takeIf(String::isNotBlank)?.let { map.key to CatalogField.available(it) }
            }.toMap(linkedMapOf())
        }
        val worldRegionNames = authorizedMap(capabilities[RomCapability.WORLD_MAP]) {
            worldMaps.regions.mapNotNull { region ->
                region.displayName?.takeIf(String::isNotBlank)?.let { region.key to CatalogField.available(it) }
            }.toMap(linkedMapOf())
        }
        val worldLocationNames = authorizedMap(capabilities[RomCapability.WORLD_MAP]) {
            worldMaps.regions.flatMap { region ->
                region.locations.mapNotNull { location ->
                    location.displayName?.takeIf(String::isNotBlank)?.let {
                        WorldLocationKey(region.key, location.key) to CatalogField.available(it)
                    }
                }
            }.toMap(linkedMapOf())
        }
        val encounterAreaNames = authorizedFields(
            encounterAreas.associateBy(EncounterArea::id),
            capabilities[RomCapability.AREA_ENCOUNTERS],
        ) { it.name }
        val poiTexts = authorizedMap(capabilities[RomCapability.LOCAL_MAP]) {
            extractPoiTexts(localMaps)
        }
        val expectedRecords = expectedRecords(
            speciesById,
            movesById,
            typesById,
            abilitiesById,
            naturesById,
            encounterAreas,
            captureBallsById,
            runtimeMetadata,
            worldMaps,
            localMaps,
        )
        val localizedCapabilities = linkedMapOf(
            LocalizedTextCapability.SPECIES_NAMES to localizedState(
                capabilities[RomCapability.SPECIES_NAMES], speciesNames.size,
                expectedRecords.getValue(LocalizedTextCapability.SPECIES_NAMES),
            ),
            LocalizedTextCapability.SPECIES_DESCRIPTIONS to localizedState(
                capabilities[RomCapability.POKEDEX_DESCRIPTIONS], speciesDescriptions.size,
                expectedRecords.getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS),
            ),
            LocalizedTextCapability.MOVE_NAMES to localizedState(
                capabilities[RomCapability.MOVE_CATALOG], moveNames.size,
                expectedRecords.getValue(LocalizedTextCapability.MOVE_NAMES),
            ),
            LocalizedTextCapability.MOVE_DESCRIPTIONS to localizedState(
                capabilities[RomCapability.MOVE_DESCRIPTIONS], moveDescriptions.size,
                expectedRecords.getValue(LocalizedTextCapability.MOVE_DESCRIPTIONS),
            ),
            LocalizedTextCapability.ABILITY_NAMES to localizedState(
                capabilities[RomCapability.ABILITIES], abilityNames.size,
                expectedRecords.getValue(LocalizedTextCapability.ABILITY_NAMES),
            ),
            LocalizedTextCapability.ABILITY_DESCRIPTIONS to localizedState(
                capabilities[RomCapability.ABILITY_DESCRIPTIONS], abilityDescriptions.size,
                expectedRecords.getValue(LocalizedTextCapability.ABILITY_DESCRIPTIONS),
            ),
            LocalizedTextCapability.TYPE_NAMES to localizedState(
                null, typeNames.size, expectedRecords.getValue(LocalizedTextCapability.TYPE_NAMES),
            ),
            LocalizedTextCapability.NATURE_NAMES to localizedState(
                null, natureNames.size, expectedRecords.getValue(LocalizedTextCapability.NATURE_NAMES),
            ),
            LocalizedTextCapability.ITEM_NAMES to localizedState(
                null, itemNames.size, expectedRecords.getValue(LocalizedTextCapability.ITEM_NAMES),
            ),
            LocalizedTextCapability.AREA_NAMES to localizedState(
                null, areaNames.size,
                expectedRecords.getValue(LocalizedTextCapability.AREA_NAMES),
            ),
            LocalizedTextCapability.LOCAL_MAP_NAMES to localizedState(
                capabilities[RomCapability.LOCAL_MAP], localMapNames.size,
                expectedRecords.getValue(LocalizedTextCapability.LOCAL_MAP_NAMES),
            ),
            LocalizedTextCapability.WORLD_REGION_NAMES to localizedState(
                capabilities[RomCapability.WORLD_MAP], worldRegionNames.size,
                expectedRecords.getValue(LocalizedTextCapability.WORLD_REGION_NAMES),
            ),
            LocalizedTextCapability.WORLD_LOCATION_NAMES to localizedState(
                capabilities[RomCapability.WORLD_MAP], worldLocationNames.size,
                expectedRecords.getValue(LocalizedTextCapability.WORLD_LOCATION_NAMES),
            ),
            LocalizedTextCapability.ENCOUNTER_AREA_NAMES to localizedState(
                capabilities[RomCapability.AREA_ENCOUNTERS], encounterAreaNames.size,
                expectedRecords.getValue(LocalizedTextCapability.ENCOUNTER_AREA_NAMES),
            ),
            LocalizedTextCapability.POI_TEXT to localizedState(
                capabilities[RomCapability.LOCAL_MAP], poiTexts.size,
                expectedRecords.getValue(LocalizedTextCapability.POI_TEXT),
            ),
        )
        val defaultOverlay = manifest.defaultLanguage?.let { language ->
            CatalogLanguageOverlay(
                language = language,
                overlayVersion = OVERLAY_FORMAT_VERSION,
                localizedCapabilities = localizedCapabilities,
                speciesNames = speciesNames,
                speciesDescriptions = speciesDescriptions,
                moveNames = moveNames,
                moveDescriptions = moveDescriptions,
                abilityNames = abilityNames,
                abilityDescriptions = abilityDescriptions,
                typeNames = typeNames,
                natureNames = natureNames,
                itemNames = itemNames,
                areaNames = areaNames,
                localMapNames = localMapNames,
                worldRegionNames = worldRegionNames,
                worldLocationNames = worldLocationNames,
                encounterAreaNames = encounterAreaNames,
                poiTexts = poiTexts,
            )
        }
        require(defaultOverlay == null || defaultOverlay.language !in additionalOverlays) {
            "additional overlays cannot replace the parser-selected default projection"
        }
        val overlays = manifest.projections
            .filter { it.status == com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus.RESOLVED }
            .associateTo(linkedMapOf()) { projection ->
                val overlay = when (projection.language) {
                    defaultOverlay?.language -> defaultOverlay
                    else -> additionalOverlays[projection.language]
                        ?: CatalogLanguageOverlay.unavailable(
                            projection.language,
                            OVERLAY_FORMAT_VERSION,
                            expectedRecords,
                        )
                }
                projection.language to requireNotNull(overlay)
            }
        require(additionalOverlays.keys.all(overlays::containsKey)) {
            "additional overlay does not reference a resolved language projection"
        }

        val sharedAbilities = abilitiesById.mapValues { (_, record) ->
            record.withoutDescriptionDerivedBehavior()
        }
        val sharedAreaIds = runtimeMetadata.areaBaseIds + runtimeMetadata.areaNamesByBaseId.keys
        val sharedLocalMaps = localMaps.copy(
            maps = localMaps.maps.map { it.copy(displayName = null) },
            pois = localMaps.pois.map { poi ->
                poi.copy(
                    displayName = null,
                    displayNamesByTrainerGender = emptyMap(),
                    item = poi.item?.copy(displayName = null),
                )
            },
        )
        val sharedWorldMaps = worldMaps.copy(
            regions = worldMaps.regions.map { region ->
                region.copy(
                    displayName = null,
                    locations = region.locations.map { it.copy(displayName = null) },
                )
            },
        )
        return CatalogLocalizedTextExtraction(
            speciesById = speciesById.mapValues { (_, record) ->
                record.copy(
                    name = localizedPlaceholder("species name"),
                    // Preserve applicability, not prose, in the existing serialized shared field.
                    description = if (record.description.status == CapabilityStatus.NOT_APPLICABLE) {
                        record.description.copy(value = null)
                    } else CatalogField.notFound("species description is stored in the language overlay"),
                )
            },
            movesById = movesById.mapValues { (_, record) ->
                record.copy(
                    name = localizedPlaceholder("move name"),
                    effectText = localizedPlaceholder("move description"),
                )
            },
            typesById = typesById.mapValues { (_, record) ->
                record.copy(name = localizedPlaceholder("type name"))
            },
            abilitiesById = sharedAbilities.mapValues { (_, record) ->
                record.copy(
                    name = localizedPlaceholder("ability name"),
                    description = localizedPlaceholder("ability description"),
                )
            },
            naturesById = naturesById.mapValues { (_, record) -> record.copy(name = null) },
            encounterAreas = encounterAreas.map { area ->
                area.copy(name = localizedPlaceholder("encounter area label"))
            },
            captureBallsById = captureBallsById.mapValues { (_, ball) ->
                ball.copy(name = localizedPlaceholder("item name"))
            },
            runtimeMetadata = runtimeMetadata.copy(
                areaBaseIds = sharedAreaIds,
                areaNamesByBaseId = emptyMap(),
            ).validate(),
            worldMaps = sharedWorldMaps.validate(),
            localMaps = sharedLocalMaps.validate(),
            capabilities = sharedCapabilities(capabilities, movesById, sharedAbilities, naturesById),
            localization = CatalogLocalization(manifest, overlays),
        )
    }

    private fun AbilityRecord.withoutDescriptionDerivedBehavior(): AbilityRecord {
        val descriptionText = description.value ?: return this
        val resolvedMechanics = mechanics.value ?: return this
        val sharedMechanics = resolvedMechanics.filterNot { mechanic ->
            mechanic.kind == AbilityMechanicKind.BEHAVIOR && mechanic.value == descriptionText
        }
        if (sharedMechanics.size == resolvedMechanics.size) return this
        return copy(
            mechanics = if (sharedMechanics.isEmpty()) {
                CatalogField.notFound("description-derived behavior is stored in the language overlay")
            } else {
                mechanics.copy(value = sharedMechanics)
            },
        )
    }

    private fun extractItemNames(
        captureBallsById: Map<Int, CaptureBallRecord>,
        localMaps: LocalMapCatalog,
    ): Map<Int, CatalogField<String>> {
        val candidates = buildList {
            captureBallsById.forEach { (id, ball) -> ball.name.availableValue()?.let { add(id to it) } }
            localMaps.pois.forEach { poi ->
                val item = poi.item ?: return@forEach
                if (item.itemId != null && item.displayName != null) add(item.itemId to item.displayName)
            }
        }.groupBy({ it.first }, { it.second })
        return candidates.mapNotNull { (id, values) ->
            values.distinct().singleOrNull()?.let { id to CatalogField.available(it) }
        }.toMap(linkedMapOf())
    }

    private fun extractPoiTexts(localMaps: LocalMapCatalog): Map<String, CatalogPoiText> =
        localMaps.pois.mapNotNull { poi ->
            val displayName = poi.displayName?.let(CatalogField.Companion::available)
            val genderNames = poi.displayNamesByTrainerGender.mapValues { (_, value) -> CatalogField.available(value) }
            val itemDisplayName = poi.item?.takeIf { it.itemId == null }?.displayName
                ?.let(CatalogField.Companion::available)
            if (displayName == null && genderNames.isEmpty() && itemDisplayName == null) {
                null
            } else {
                poi.key to CatalogPoiText(displayName, genderNames, itemDisplayName)
            }
        }.toMap(linkedMapOf())

    private fun expectedRecords(
        speciesById: Map<Int, SpeciesRecord>,
        movesById: Map<Int, MoveRecord>,
        typesById: Map<Int, TypeRecord>,
        abilitiesById: Map<Int, AbilityRecord>,
        naturesById: Map<Int, NatureRecord>,
        encounterAreas: List<EncounterArea>,
        captureBallsById: Map<Int, CaptureBallRecord>,
        runtimeMetadata: CatalogRuntimeMetadata,
        worldMaps: WorldMapCatalog,
        localMaps: LocalMapCatalog,
    ): Map<LocalizedTextCapability, Int> {
        val itemIds = buildSet {
            addAll(captureBallsById.keys)
            addAll(localMaps.pois.mapNotNull { it.item?.itemId })
        }
        return mapOf(
            LocalizedTextCapability.SPECIES_NAMES to speciesById.size,
            LocalizedTextCapability.SPECIES_DESCRIPTIONS to speciesById.count { (id, record) ->
                id > 0 && record.dexNumber.status != CapabilityStatus.NOT_APPLICABLE &&
                    record.description.status != CapabilityStatus.NOT_APPLICABLE
            },
            LocalizedTextCapability.MOVE_NAMES to movesById.size,
            LocalizedTextCapability.MOVE_DESCRIPTIONS to movesById.keys.count { it > 0 },
            LocalizedTextCapability.ABILITY_NAMES to abilitiesById.size,
            LocalizedTextCapability.ABILITY_DESCRIPTIONS to abilitiesById.keys.count { it > 0 },
            LocalizedTextCapability.TYPE_NAMES to typesById.size,
            LocalizedTextCapability.NATURE_NAMES to naturesById.size,
            LocalizedTextCapability.ITEM_NAMES to itemIds.size,
            LocalizedTextCapability.AREA_NAMES to (runtimeMetadata.areaBaseIds + runtimeMetadata.areaNamesByBaseId.keys).size,
            LocalizedTextCapability.LOCAL_MAP_NAMES to localMaps.maps.size,
            LocalizedTextCapability.WORLD_REGION_NAMES to worldMaps.regions.size,
            LocalizedTextCapability.WORLD_LOCATION_NAMES to worldMaps.regions.sumOf { it.locations.size },
            LocalizedTextCapability.ENCOUNTER_AREA_NAMES to encounterAreas.size,
            LocalizedTextCapability.POI_TEXT to localMaps.pois.size,
        )
    }

    private fun localizedState(
        evidence: CapabilityEvidence?,
        coveredRecords: Int,
        expectedRecords: Int,
    ): LocalizedCapabilityState {
        val deniedStatus = evidence?.status?.takeIf {
            it == CapabilityStatus.AMBIGUOUS || it == CapabilityStatus.NOT_FOUND || it == CapabilityStatus.NOT_APPLICABLE
        }
        if (deniedStatus != null) {
            require(coveredRecords == 0) { "localized text cannot bypass unavailable capability evidence" }
            return LocalizedCapabilityState.unavailable(
                status = deniedStatus,
                expectedRecords = expectedRecords,
                confidence = evidence.confidence,
                reasons = evidence.reasons,
                reviewStatus = evidence.reviewStatus,
                validatorReviewRecommended = evidence.validatorReviewRecommended,
            )
        }
        val status = when {
            expectedRecords == 0 -> CapabilityStatus.NOT_APPLICABLE
            coveredRecords == expectedRecords -> CapabilityStatus.AVAILABLE
            coveredRecords > 0 -> CapabilityStatus.PARTIAL
            else -> CapabilityStatus.NOT_FOUND
        }
        return LocalizedCapabilityState(
            status = status,
            confidence = evidence?.confidence ?: if (coveredRecords > 0) 1.0 else 0.0,
            coveredRecords = coveredRecords,
            expectedRecords = expectedRecords,
            reviewStatus = evidence?.reviewStatus ?: CapabilityReviewStatus.NONE,
            validatorReviewRecommended = evidence?.validatorReviewRecommended ?: false,
            reasons = evidence?.reasons.orEmpty(),
        )
    }

    private fun sharedCapabilities(
        capabilities: Map<RomCapability, CapabilityEvidence>,
        movesById: Map<Int, MoveRecord>,
        abilitiesById: Map<Int, AbilityRecord>,
        naturesById: Map<Int, NatureRecord>,
    ): Map<RomCapability, CapabilityEvidence> = capabilities
        .filterKeys { !it.isLocalizedTextCapability() }
        .toMutableMap()
        .also { shared ->
            shared[RomCapability.MOVE_CATALOG]?.let { evidence ->
                shared[RomCapability.MOVE_CATALOG] = evidence.withSharedDomain(movesById.size, "shared move IDs and mechanics")
            }
            shared[RomCapability.ABILITIES]?.let { evidence ->
                shared[RomCapability.ABILITIES] = evidence.withSharedDomain(abilitiesById.size, "shared ability IDs")
            }
            shared[RomCapability.ABILITY_MECHANICS]?.let { evidence ->
                val expected = abilitiesById.size
                val covered = abilitiesById.values.count { ability ->
                    ability.mechanics.status == CapabilityStatus.AVAILABLE && !ability.mechanics.value.isNullOrEmpty()
                }
                shared[RomCapability.ABILITY_MECHANICS] = evidence.copy(
                    compatible = covered > 0,
                    confidence = if (covered > 0) evidence.confidence else 0.0,
                    count = covered,
                    reasons = listOf("retained non-localized ability mechanics for $covered/$expected abilities"),
                    status = when {
                        expected == 0 -> evidence.status
                        evidence.status == CapabilityStatus.AMBIGUOUS -> CapabilityStatus.AMBIGUOUS
                        covered == expected -> CapabilityStatus.AVAILABLE
                        covered > 0 -> CapabilityStatus.PARTIAL
                        else -> CapabilityStatus.NOT_FOUND
                    },
                    validRecords = covered,
                    totalRecords = expected,
                    coveredRecords = covered,
                    expectedRecords = expected,
                    incompleteRecords = expected - covered,
                )
            }
            shared[RomCapability.NATURES]?.let { evidence ->
                val flavorRecords = naturesById.values.count { it.flavorModifiers != null }
                val complete = naturesById.isNotEmpty() && flavorRecords == naturesById.size
                shared[RomCapability.NATURES] = evidence.copy(
                    compatible = naturesById.isNotEmpty(),
                    confidence = if (naturesById.isEmpty()) 0.0 else {
                        (naturesById.size + flavorRecords).toDouble() / (naturesById.size * 2)
                    },
                    count = naturesById.size,
                    reasons = listOf(
                        "decoded ROM-native Nature stat effects",
                        "decoded ROM-native Nature flavor affinities for $flavorRecords/${naturesById.size} records",
                    ),
                    status = when {
                        naturesById.isEmpty() -> evidence.status
                        complete -> CapabilityStatus.AVAILABLE
                        else -> CapabilityStatus.PARTIAL
                    },
                    validRecords = naturesById.size,
                    totalRecords = naturesById.size,
                    coveredRecords = if (complete) naturesById.size else flavorRecords,
                    expectedRecords = naturesById.size,
                    incompleteRecords = naturesById.size - flavorRecords,
                    reviewStatus = if (complete) CapabilityReviewStatus.NONE else CapabilityReviewStatus.MANUAL_REVIEW,
                )
            }
        }

    private fun CapabilityEvidence.withSharedDomain(size: Int, reason: String): CapabilityEvidence = copy(
        compatible = size > 0 || compatible,
        count = size,
        reasons = listOf(reason),
        status = when {
            size > 0 && status != CapabilityStatus.AMBIGUOUS -> CapabilityStatus.AVAILABLE
            else -> status
        },
        validRecords = size.takeIf { it > 0 },
        totalRecords = size.takeIf { it > 0 },
        coveredRecords = size.takeIf { it > 0 },
        expectedRecords = size.takeIf { it > 0 },
        incompleteRecords = 0.takeIf { size > 0 },
    )

    private fun RomCapability.isLocalizedTextCapability(): Boolean = when (this) {
        RomCapability.SPECIES_NAMES,
        RomCapability.POKEDEX_DESCRIPTIONS,
        RomCapability.MOVE_DESCRIPTIONS,
        RomCapability.ABILITY_DESCRIPTIONS,
        -> true
        else -> false
    }

    private fun <K, V> authorizedMap(
        evidence: CapabilityEvidence?,
        materialize: () -> Map<K, V>,
    ): Map<K, V> = if (evidence?.status in UNAVAILABLE_STATUSES) emptyMap() else materialize()

    private fun <T> authorizedFields(
        records: Map<Int, T>,
        evidence: CapabilityEvidence?,
        field: (T) -> CatalogField<String>,
    ): Map<Int, CatalogField<String>> = if (evidence?.status in UNAVAILABLE_STATUSES) {
        emptyMap()
    } else {
        availableFields(records, field)
    }

    private fun <T> availableFields(
        records: Map<Int, T>,
        field: (T) -> CatalogField<String>,
    ): Map<Int, CatalogField<String>> = records.mapNotNull { (id, record) ->
        field(record).takeIf {
            it.status == CapabilityStatus.AVAILABLE && !it.value.isNullOrBlank()
        }?.let { id to it }
    }.toMap(linkedMapOf())

    private fun CatalogField<String>.availableValue(): String? =
        value?.takeIf { status == CapabilityStatus.AVAILABLE && it.isNotBlank() }

    private fun localizedPlaceholder(label: String): CatalogField<String> =
        CatalogField.notApplicable("$label is stored in the language overlay")

    private val UNAVAILABLE_STATUSES = setOf(
        CapabilityStatus.AMBIGUOUS,
        CapabilityStatus.NOT_FOUND,
        CapabilityStatus.NOT_APPLICABLE,
    )
    private const val OVERLAY_FORMAT_VERSION = 1L
}
