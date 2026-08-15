package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.enrpau.dualscreendex.parser.parse.LocalMapResolution
import com.enrpau.dualscreendex.parser.parse.WorldMapResolution
import com.enrpau.dualscreendex.parser.parse.Gen3SaveBlock1PointerResolver
import com.enrpau.dualscreendex.parser.parse.Gen3RuntimeMemoryLayoutResolver
import com.enrpau.dualscreendex.parser.sprite.BallSpriteMaterializer
import com.enrpau.dualscreendex.parser.sprite.SpriteMaterializer
import java.util.Locale

data class CatalogParseResult(
    val analysis: ParseResult,
    val layout: ResolvedRomLayout?,
    val catalog: ParsedCatalog?,
)

data class CatalogParseAttempt(
    val analysis: ParseResult,
    val layout: ResolvedRomLayout?,
    val catalog: Result<ParsedCatalog>?,
)

enum class CatalogMaterializationPhase { ESSENTIAL, SPECIES_MEDIA, RELATIONSHIPS, EXTENDED, COMPLETE }

data class CatalogMaterializationProgress(
    val phase: CatalogMaterializationPhase,
    val completedUnits: Int,
    val totalUnits: Int,
    val catalog: ParsedCatalog,
)

object CatalogParser {
    fun parse(
        rom: RomImage,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
    ): CatalogParseResult {
        val attempt = parseCatching(rom, onProgress)
        return CatalogParseResult(
            attempt.analysis,
            attempt.layout,
            attempt.catalog?.getOrThrow(),
        )
    }

    fun parseCatching(
        rom: RomImage,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
    ): CatalogParseAttempt {
        val context = ParserOrchestrator.analyzeForCatalog(rom)
        val analysis = context.analysis
        if (analysis.status != SelectionStatus.SELECTED || analysis.selectedFamily == null) {
            return CatalogParseAttempt(analysis, null, null)
        }
        val layout = analysis.probes.singleOrNull { it.family == analysis.selectedFamily }?.resolvedLayout
            ?: return CatalogParseAttempt(analysis, null, null)
        return CatalogParseAttempt(
            analysis,
            layout,
            runCatching {
                CatalogMaterializer.materialize(
                    rom,
                    analysis,
                    layout,
                    onProgress,
                    context.resolveGen3AreaNames,
                    context.resolveWorldMap,
                    context.resolveLocalMaps,
                )
            },
        )
    }
}

object CatalogMaterializer {
    fun materialize(
        rom: RomImage,
        analysis: ParseResult,
        layout: ResolvedRomLayout,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
        resolveGen3AreaNames: ((Set<Int>) -> Map<Int, String>)? = null,
        resolveWorldMap: ((Int, Set<Int>) -> WorldMapResolution)? = null,
        resolveLocalMaps: ((Int, Set<Int>) -> LocalMapResolution)? = null,
    ): ParsedCatalog {
        val rawSpecies = RecordMaterializers.species(rom, layout)
        val baseSpecies = if (layout.generation == 3 && layout.pokeemeraldExpansion == null) {
            layout.resolvedDatasets.abilityNames?.catalogDirectAbilityIds()?.let { catalogIds ->
                rawSpecies.mapValues { (_, species) ->
                    val abilityIds = species.abilityIds.value ?: return@mapValues species
                    species.copy(abilityIds = CatalogField.available(abilityIds.filter(catalogIds::contains)))
                }
            } ?: rawSpecies
        } else {
            rawSpecies
        }
        val rawMoves = RecordMaterializers.moves(rom, layout)
        val chart = if (layout.generation == 3) {
            layout.resolvedDatasets.typeChart?.catalogMatchups().orEmpty()
        } else {
            RecordMaterializers.typeChart(rom, layout)
        }
        val baseTypes = TypePresentationMaterializer.apply(RecordMaterializers.types(layout, baseSpecies, chart, rawMoves))
        val abilities = RecordMaterializers.abilities(rom, layout)
        val initialCapabilities = analysis.capabilities.associateBy { it.capability }.toMutableMap().also { capabilities ->
            capabilities[RomCapability.TYPE_PRESENTATION] = collectionCapability(
                RomCapability.TYPE_PRESENTATION,
                baseTypes.values.count { it.presentation.status == CapabilityStatus.AVAILABLE },
                "family type colors with explicit accessible fallback for custom IDs",
                "no materialized types were available for presentation",
            )
        }
        val essentialCatalog = ParsedCatalog(
            romSha256 = analysis.sha256,
            family = layout.family,
            platform = layout.platform,
            romCrc32 = analysis.crc32,
            speciesById = baseSpecies,
            movesById = rawMoves,
            typesById = baseTypes,
            abilitiesById = abilities,
            typeChart = chart,
            capabilities = initialCapabilities,
        )
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.ESSENTIAL, 1, 5, essentialCatalog))

        val descriptions = RelationshipMaterializers.descriptions(rom, layout)
        val sprites = SpriteMaterializer.pokemon(rom, layout)
        val resolvedSprites = resolveSpriteAliases(baseSpecies, sprites, layout.generation)
        val mediaSpecies = baseSpecies.mapValues { (id, record) ->
            val dex = record.dexNumber.value ?: id
            val descriptionKey = when {
                layout.pokeemeraldExpansion != null -> id
                layout.generation == 3 -> dex
                else -> id
            }
            val description = descriptions[descriptionKey]
            val sprite = resolvedSprites[id]
            record.copy(
                sprite = sprite?.let { CatalogField(CapabilityStatus.AVAILABLE, it.sprite, it.reasons) }
                    ?: CatalogField.notFound("sprite could not be decoded for species $id"),
                description = description?.text?.let(CatalogField.Companion::available)
                    ?: CatalogField.notFound("description could not be decoded for species $id"),
                height = description?.height?.let(CatalogField.Companion::available)
                    ?: CatalogField.notFound("height could not be decoded for species $id"),
                weight = description?.weight?.let(CatalogField.Companion::available)
                    ?: CatalogField.notFound("weight could not be decoded for species $id"),
            )
        }
        val mediaCatalog = essentialCatalog.copy(speciesById = mediaSpecies)
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.SPECIES_MEDIA, 2, 5, mediaCatalog))

        val evolutions = RelationshipMaterializers.evolutions(rom, layout)
        val learnsets = RelationshipMaterializers.learnsets(rom, layout)
        val encounterMaterialization = EncounterMaterializer.materializeWithEvidence(rom, layout)
        val rawEncounters = encounterMaterialization.areas
        val runtimeMetadata = if (layout.generation == 3) {
            CatalogRuntimeMetadata(
                gen3SaveBlock1PointerAddress = Gen3SaveBlock1PointerResolver.resolve(rom),
                gen3RuntimeMemoryLayout = Gen3RuntimeMemoryLayoutResolver.resolve(rom),
                areaNamesByBaseId = if (layout.pokeemeraldExpansion == null && resolveGen3AreaNames != null) {
                    resolveGen3AreaNames(rawEncounters.mapTo(linkedSetOf()) { it.id / 10 })
                } else {
                    emptyMap()
                },
            )
        } else {
            CatalogRuntimeMetadata()
        }
        val encounters = applyResolvedAreaNames(rawEncounters, runtimeMetadata.areaNamesByBaseId)
        val relationshipSpecies = mediaSpecies.mapValues { (id, record) ->
            record.copy(
                evolutionEdges = if (layout.tables.evolutions != null) {
                    CatalogField.available(evolutions[id].orEmpty())
                } else {
                    CatalogField.notFound("evolution table was not resolved")
                },
                learnset = if (layout.tables.learnsets != null) {
                    CatalogField.available(learnsets[id].orEmpty())
                } else {
                    CatalogField.notFound("learnset table was not resolved")
                },
            )
        }
        val closedRelationshipSpecies = EncounterReferencedSpeciesClosure.close(
            rom = rom,
            generation = layout.generation,
            names = layout.tables.speciesNames,
            namesStatus = initialCapabilities[RomCapability.SPECIES_NAMES]?.status,
            species = relationshipSpecies,
            encounters = encounters,
        )
        val relationshipCatalog = mediaCatalog.copy(
            speciesById = closedRelationshipSpecies,
            encounterAreas = encounters,
            runtimeMetadata = runtimeMetadata,
        )
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.RELATIONSHIPS, 3, 5, relationshipCatalog))

        val learnsetRulesets = LearnsetRulesetMaterializer.materialize(rom, layout, learnsets)
        val moveDescriptions = MoveDescriptionMaterializer.materialize(rom, layout)
        val abilityDescriptions = AbilityDescriptionMaterializer.materialize(rom, layout)
        val abilityMechanics = AbilityMechanicsMaterializer.materialize(rom, layout, abilities)
        val moveAcquisitions = MoveAcquisitionMaterializer.materialize(rom, layout)
        val species = closedRelationshipSpecies.mapValues { (id, record) ->
            record.copy(
                moveAcquisitions = if (moveAcquisitions.evidence.values.any { it.compatible }) {
                    CatalogField.available(moveAcquisitions.acquisitionsBySpecies[id].orEmpty())
                } else {
                    CatalogField.notFound("non-level move acquisition tables were not resolved")
                },
            )
        }
        val validTypeIds = layout.resolvedDatasets.typeChart?.table?.typeCount
            ?.let { typeCount -> (0 until typeCount).toSet() }
            ?: baseTypes.keys
        val closedMoves = ReferencedMoveCatalogClosure.close(
            moves = rawMoves,
            species = species,
            rulesets = learnsetRulesets,
            typedDetails = layout.resolvedDatasets.moveDetails?.catalogDetails().orEmpty(),
            validTypeIds = validTypeIds,
        )
        val moves = closedMoves.mapValues { (id, move) ->
            val effectText = moveDescriptions?.descriptions?.get(id)
            move.copy(
                effectText = when {
                    effectText != null -> CatalogField.available(effectText)
                    layout.generation == 1 -> CatalogField.notApplicable(
                        "this engine does not expose a compatible move-description pointer table",
                    )
                    else -> CatalogField.notFound("move description was not resolved from the ROM")
                },
            )
        }
        val types = ReferencedTypeCatalogClosure.close(baseTypes, moves, validTypeIds)
        val enrichedAbilities = abilities.mapValues { (id, ability) ->
            val description = abilityDescriptions?.descriptions?.get(id)
            ability.copy(
                description = when {
                    description != null -> CatalogField.available(description)
                    layout.generation < 3 -> CatalogField.notApplicable("abilities are not part of this engine")
                    else -> CatalogField.notFound("ability description was not resolved from the ROM")
                },
                mechanics = abilityMechanics?.mechanicsByAbility?.get(id)?.let(CatalogField.Companion::available)
                    ?: if (layout.generation < 3) {
                        CatalogField.notApplicable("abilities are not part of this engine")
                    } else {
                        CatalogField.notFound("ability mechanics were not resolved from ROM code")
                    },
            )
        }
        val balls = if (layout.generation == 3) BallSpriteMaterializer.captureBalls(rom) else emptyMap()
        val capabilities = initialCapabilities.toMutableMap()
        capabilities[RomCapability.MOVE_DESCRIPTIONS] = if (moveDescriptions != null) {
            val expected = moves.keys.count { it > 0 }
            val covered = moveDescriptions.descriptions.keys.count { it > 0 && it in moves }
            val complete = covered >= expected
            CapabilityEvidence(
                capability = RomCapability.MOVE_DESCRIPTIONS,
                compatible = true,
                confidence = moveDescriptions.confidence,
                offset = moveDescriptions.sourceOffset,
                count = covered,
                reasons = listOf(
                    if (complete) "decoded a validated move-description pointer table"
                    else "decoded a partial move-description pointer table ($covered/$expected)",
                ),
                status = if (complete) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL,
                validRecords = covered,
                totalRecords = expected,
                coveredRecords = covered,
                expectedRecords = expected,
            )
        } else {
            CapabilityEvidence(
                capability = RomCapability.MOVE_DESCRIPTIONS,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation == 1) "this engine has no compatible move-description pointer table"
                    else "move-description pointer table was not resolved",
                ),
                status = if (layout.generation == 1) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
            )
        }
        capabilities[RomCapability.ABILITY_DESCRIPTIONS] = if (abilityDescriptions != null) {
            val expected = abilities.keys.count { it > 0 }
            val covered = abilityDescriptions.descriptions.keys.count { it > 0 && it in abilities }
            val complete = covered >= expected
            CapabilityEvidence(
                capability = RomCapability.ABILITY_DESCRIPTIONS,
                compatible = true,
                confidence = abilityDescriptions.confidence,
                offset = abilityDescriptions.sourceOffset,
                count = covered,
                reasons = listOf(
                    if (complete) "decoded a structurally referenced ability-description pointer table"
                    else "decoded a structurally referenced partial ability-description table ($covered/$expected)",
                ),
                status = if (complete) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL,
                reviewStatus = if (complete) CapabilityReviewStatus.NONE else CapabilityReviewStatus.MANUAL_REVIEW,
                coveredRecords = covered,
                expectedRecords = expected,
                incompleteRecords = (expected - covered).coerceAtLeast(0),
            )
        } else {
            CapabilityEvidence(
                capability = RomCapability.ABILITY_DESCRIPTIONS,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation < 3) "abilities are not part of this engine"
                    else "ability-description pointer table was not resolved",
                ),
                status = if (layout.generation < 3) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
            )
        }
        capabilities[RomCapability.ABILITY_MECHANICS] = if (abilityMechanics != null) {
            val covered = abilityMechanics.mechanicsByAbility.size
            val expected = abilities.size
            val numericCovered = abilityMechanics.mechanicsByAbility.count { (_, mechanics) ->
                mechanics.any { it.kind != AbilityMechanicKind.BEHAVIOR }
            }
            CapabilityEvidence(
                capability = RomCapability.ABILITY_MECHANICS,
                compatible = true,
                confidence = abilityMechanics.confidence,
                offset = abilityMechanics.sourceOffset,
                count = covered,
                coveredRecords = covered,
                expectedRecords = expected,
                incompleteRecords = (expected - covered).coerceAtLeast(0),
                reasons = listOf(
                    "mapped source-backed behavior for $covered/$expected abilities",
                    "decoded numeric mechanics for $numericCovered/$expected abilities",
                ),
                status = if (covered == expected) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL,
            )
        } else {
            initialCapabilities[RomCapability.ABILITY_MECHANICS] ?: CapabilityEvidence(
                capability = RomCapability.ABILITY_MECHANICS,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation < 3) "abilities are not part of this engine"
                    else "structured ability values were not resolved from compiled battle code",
                ),
                status = if (layout.generation < 3) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
            )
        }
        moveAcquisitions.evidence.values.forEach { evidence -> capabilities[evidence.capability] = evidence }
        capabilities[RomCapability.AREA_ENCOUNTERS] = CapabilityEvidence(
            capability = RomCapability.AREA_ENCOUNTERS,
            compatible = encounters.isNotEmpty(),
            confidence = if (encounters.isNotEmpty()) 1.0 else 0.0,
            offset = encounterMaterialization.selectedRootOffset,
            count = encounters.size.takeIf { it > 0 },
            recordSize = encounterMaterialization.headerSize,
            reasons = encounterMaterialization.reasons,
            status = encounterMaterialization.status,
            reviewStatus = encounterMaterialization.reviewStatus,
        )
        capabilities[RomCapability.BALL_CATALOG] = if (balls.values.any { it.sprite.status == CapabilityStatus.AVAILABLE }) {
            CapabilityEvidence(
                capability = RomCapability.BALL_CATALOG,
                compatible = true,
                confidence = balls.values.count { it.sprite.status == CapabilityStatus.AVAILABLE }.toDouble() / balls.size,
                count = balls.size,
                reasons = listOf("located compressed ball graphics and palette tables"),
                status = CapabilityStatus.AVAILABLE,
            )
        } else {
            CapabilityEvidence(
                capability = RomCapability.BALL_CATALOG,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation == 3) "compressed ball graphics tables were not located"
                    else "per-ball capture artwork is not part of this engine's individual record",
                ),
                status = if (layout.generation == 3) CapabilityStatus.NOT_FOUND else CapabilityStatus.NOT_APPLICABLE,
            )
        }
        val encounterAreaIdStride = if (layout.pokeemeraldExpansion == null) 10 else 100
        val worldMapResolution = if (layout.generation in 1..3 && resolveWorldMap != null) {
            try {
                resolveWorldMap(
                    layout.generation,
                    encounters.mapTo(linkedSetOf()) { it.id / encounterAreaIdStride },
                ).also { resolution ->
                    if (resolution is WorldMapResolution.Resolved) resolution.catalog.validate()
                }
            } catch (failure: Exception) {
                WorldMapResolution.Unavailable(
                    stage = "resolver-exception",
                    reason = "optional world-map resolution failed closed (${failure.javaClass.simpleName})",
                )
            }
        } else {
            null
        }
        val worldMaps = (worldMapResolution as? WorldMapResolution.Resolved)?.catalog ?: WorldMapCatalog()
        capabilities[RomCapability.WORLD_MAP] = when (worldMapResolution) {
            is WorldMapResolution.Resolved -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = true,
                confidence = 1.0,
                count = worldMapResolution.catalog.regions.size,
                reasons = worldMapResolution.reasons,
                status = CapabilityStatus.AVAILABLE,
            )
            is WorldMapResolution.Ambiguous -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "world-map stage: ${worldMapResolution.stage}",
                    worldMapResolution.reason,
                ),
                status = CapabilityStatus.AMBIGUOUS,
                reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
            )
            is WorldMapResolution.BudgetExceeded -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "world-map stage: ${worldMapResolution.stage}",
                    worldMapResolution.reason,
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
            is WorldMapResolution.Unavailable -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "world-map stage: ${worldMapResolution.stage}",
                    worldMapResolution.reason,
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
            null -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf("world maps are not part of this engine's normalized parser path"),
                status = CapabilityStatus.NOT_APPLICABLE,
            )
        }
        val encounterBaseIds = encounters.mapTo(linkedSetOf()) { it.id / encounterAreaIdStride }
        val localMapResolution = if (
            (layout.generation == 1 || layout.generation == 3) && resolveLocalMaps != null
        ) {
            try {
                resolveLocalMaps(layout.generation, encounterBaseIds).also { resolution ->
                    if (resolution is LocalMapResolution.Resolved) resolution.catalog.validate()
                }
            } catch (failure: Exception) {
                LocalMapResolution.Unavailable(
                    stage = "resolver-exception",
                    reason = "optional local-map resolution failed closed (${failure.javaClass.simpleName})",
                )
            }
        } else {
            null
        }
        val localMaps = (localMapResolution as? LocalMapResolution.Resolved)?.catalog ?: LocalMapCatalog()
        capabilities[RomCapability.LOCAL_MAP] = when (localMapResolution) {
            is LocalMapResolution.Resolved -> {
                val total = localMapResolution.catalog.maps.size + localMapResolution.skippedMaps
                CapabilityEvidence(
                    capability = RomCapability.LOCAL_MAP,
                    compatible = true,
                    confidence = localMapResolution.catalog.maps.size.toDouble() / total.coerceAtLeast(1),
                    count = localMapResolution.catalog.maps.size,
                    reasons = localMapResolution.reasons + if (localMapResolution.skippedMaps > 0) {
                        listOf("skipped ${localMapResolution.skippedMaps} maps that failed bounded rendering")
                    } else {
                        emptyList()
                    },
                    status = if (localMapResolution.skippedMaps == 0) {
                        CapabilityStatus.AVAILABLE
                    } else {
                        CapabilityStatus.PARTIAL
                    },
                )
            }
            is LocalMapResolution.BudgetExceeded -> CapabilityEvidence(
                capability = RomCapability.LOCAL_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "local-map stage: ${localMapResolution.stage}",
                    localMapResolution.reason,
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
            is LocalMapResolution.Unavailable -> CapabilityEvidence(
                capability = RomCapability.LOCAL_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "local-map stage: ${localMapResolution.stage}",
                    localMapResolution.reason,
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
            null -> CapabilityEvidence(
                capability = RomCapability.LOCAL_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf("local maps are not part of this engine's normalized parser path"),
                status = CapabilityStatus.NOT_APPLICABLE,
            )
        }
        val catalog = ParsedCatalog(
            romSha256 = analysis.sha256,
            family = layout.family,
            platform = layout.platform,
            romCrc32 = analysis.crc32,
            speciesById = species,
            movesById = moves,
            typesById = types,
            abilitiesById = enrichedAbilities,
            typeChart = chart,
            encounterAreas = encounters,
            captureBallsById = balls,
            learnsetRulesets = learnsetRulesets,
            runtimeMetadata = runtimeMetadata,
            worldMaps = worldMaps,
            localMaps = localMaps,
            capabilities = capabilities,
            diagnostics = buildList {
                moveDescriptions?.let {
                    add(
                        "move descriptions: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)}",
                    )
                }
                abilityDescriptions?.let {
                    add(
                        "ability descriptions: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)}",
                    )
                }
                abilityMechanics?.let {
                    add(
                        "ability mechanics: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)} " +
                            "abilities=${it.mechanicsByAbility.size}",
                    )
                }
                learnsetRulesets.forEach {
                    add(
                        "learnset ruleset ${it.id}: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)} primary=${it.primary}",
                    )
                }
                encounterMaterialization.selectedRootOffset?.let { root ->
                    add(
                        "area encounters: root=0x${root.toString(16)} " +
                            "headerSize=${encounterMaterialization.headerSize} " +
                            "headers=${encounterMaterialization.headerCount} " +
                            "populatedMethods=${encounterMaterialization.populatedMethodCount} " +
                            "areas=${encounters.size} references=${encounterMaterialization.referenceCount} " +
                            "candidates=${encounterMaterialization.candidateCount}",
                    )
                }
            },
        )
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.EXTENDED, 4, 5, catalog))
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.COMPLETE, 5, 5, catalog))
        return catalog
    }

    private fun applyResolvedAreaNames(
        areas: List<EncounterArea>,
        namesByBaseId: Map<Int, String>,
    ): List<EncounterArea> = areas.map { area ->
        val resolvedName = namesByBaseId[area.id / 10] ?: return@map area
        val methodName = area.name.value
            ?.substringAfter(" - ", missingDelimiterValue = "")
            ?.trim()
            .orEmpty()
        area.copy(
            name = CatalogField.available(
                if (methodName.isEmpty()) resolvedName else "$resolvedName - $methodName",
            ),
        )
    }

    private fun resolveSpriteAliases(
        species: Map<Int, SpeciesRecord>,
        decoded: Map<Int, RgbaSprite>,
        generation: Int,
    ): Map<Int, ResolvedSprite> {
        fun spriteFor(id: Int, record: SpeciesRecord): RgbaSprite? =
            decoded[if (generation == 1) record.dexNumber.value ?: id else id]
        fun normalizedName(record: SpeciesRecord): String? = record.name.value
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.uppercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)

        val resolved = species.mapNotNull { (id, record) ->
            spriteFor(id, record)?.let { id to ResolvedSprite(it) }
        }.toMap().toMutableMap()
        species.forEach { (id, record) ->
            if (id in resolved) return@forEach
            val dex = record.dexNumber.value?.takeIf { it > 0 } ?: return@forEach
            val name = normalizedName(record) ?: return@forEach
            val candidates = species.asSequence()
                .filter { (candidateId, candidate) ->
                    candidateId != id && candidate.dexNumber.value == dex && normalizedName(candidate) == name
                }
                .mapNotNull { (candidateId, candidate) ->
                    spriteFor(candidateId, candidate)?.let { candidateId to it }
                }
                .toList()
            val uniqueSprites = candidates.map { it.second }.distinct()
            if (uniqueSprites.size == 1) {
                val donorId = candidates.minOf { it.first }
                resolved[id] = ResolvedSprite(
                    uniqueSprites.single(),
                    listOf("inferred ROM sprite from species $donorId with the same normalized name and Pokédex index"),
                )
            }
        }
        return resolved
    }

    private data class ResolvedSprite(
        val sprite: RgbaSprite,
        val reasons: List<String> = emptyList(),
    )

    private fun collectionCapability(
        capability: RomCapability,
        count: Int,
        availableReason: String,
        unavailableReason: String,
    ) = CapabilityEvidence(
        capability = capability,
        compatible = count > 0,
        confidence = if (count > 0) 1.0 else 0.0,
        count = count.takeIf { it > 0 },
        reasons = listOf(if (count > 0) availableReason else unavailableReason),
        status = if (count > 0) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
    )
}
