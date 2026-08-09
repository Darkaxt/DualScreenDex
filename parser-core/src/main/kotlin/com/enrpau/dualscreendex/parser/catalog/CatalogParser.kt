package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.enrpau.dualscreendex.parser.sprite.BallSpriteMaterializer
import com.enrpau.dualscreendex.parser.sprite.SpriteMaterializer

data class CatalogParseResult(
    val analysis: ParseResult,
    val layout: ResolvedRomLayout?,
    val catalog: ParsedCatalog?,
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
        val analysis = ParserOrchestrator.analyze(rom)
        if (analysis.status != SelectionStatus.SELECTED || analysis.selectedFamily == null) {
            return CatalogParseResult(analysis, null, null)
        }
        val layout = analysis.probes.singleOrNull { it.family == analysis.selectedFamily }?.resolvedLayout
            ?: return CatalogParseResult(analysis, null, null)
        return CatalogParseResult(analysis, layout, CatalogMaterializer.materialize(rom, analysis, layout, onProgress))
    }
}

object CatalogMaterializer {
    fun materialize(
        rom: RomImage,
        analysis: ParseResult,
        layout: ResolvedRomLayout,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
    ): ParsedCatalog {
        val baseSpecies = RecordMaterializers.species(rom, layout)
        val rawMoves = RecordMaterializers.moves(rom, layout)
        val chart = RecordMaterializers.typeChart(rom, layout)
        val types = TypePresentationMaterializer.apply(RecordMaterializers.types(layout, baseSpecies, chart, rawMoves))
        val abilities = RecordMaterializers.abilities(rom, layout)
        val initialCapabilities = analysis.capabilities.associateBy { it.capability }.toMutableMap().also { capabilities ->
            capabilities[RomCapability.TYPE_PRESENTATION] = collectionCapability(
                RomCapability.TYPE_PRESENTATION,
                types.values.count { it.presentation.status == CapabilityStatus.AVAILABLE },
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
            typesById = types,
            abilitiesById = abilities,
            typeChart = chart,
            capabilities = initialCapabilities,
        )
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.ESSENTIAL, 1, 5, essentialCatalog))

        val descriptions = RelationshipMaterializers.descriptions(rom, layout)
        val sprites = SpriteMaterializer.pokemon(rom, layout)
        val mediaSpecies = baseSpecies.mapValues { (id, record) ->
            val dex = record.dexNumber.value ?: id
            val description = descriptions[if (layout.generation == 3) dex else id]
            val sprite = sprites[if (layout.generation == 1) dex else id]
            record.copy(
                sprite = sprite?.let(CatalogField.Companion::available)
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
        val encounters = EncounterMaterializer.materialize(rom, layout)
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
        val relationshipCatalog = mediaCatalog.copy(
            speciesById = relationshipSpecies,
            encounterAreas = encounters,
        )
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.RELATIONSHIPS, 3, 5, relationshipCatalog))

        val learnsetRulesets = LearnsetRulesetMaterializer.materialize(rom, layout, learnsets)
        val moveDescriptions = MoveDescriptionMaterializer.materialize(rom, layout)
        val abilityDescriptions = AbilityDescriptionMaterializer.materialize(rom, layout)
        val moveAcquisitions = MoveAcquisitionMaterializer.materialize(rom, layout)
        val species = relationshipSpecies.mapValues { (id, record) ->
            record.copy(
                moveAcquisitions = if (moveAcquisitions.evidence.values.any { it.compatible }) {
                    CatalogField.available(moveAcquisitions.acquisitionsBySpecies[id].orEmpty())
                } else {
                    CatalogField.notFound("non-level move acquisition tables were not resolved")
                },
            )
        }
        val moves = rawMoves.mapValues { (id, move) ->
            val effectText = moveDescriptions?.descriptions?.get(id)
            move.copy(
                effectText = when {
                    effectText != null -> CatalogField.available(effectText)
                    layout.generation < 3 -> CatalogField.notApplicable(
                        "this engine does not expose a compatible move-description pointer table",
                    )
                    else -> CatalogField.notFound("move description was not resolved from the ROM")
                },
            )
        }
        val enrichedAbilities = abilities.mapValues { (id, ability) ->
            val description = abilityDescriptions?.descriptions?.get(id)
            ability.copy(
                description = when {
                    description != null -> CatalogField.available(description)
                    layout.generation < 3 -> CatalogField.notApplicable("abilities are not part of this engine")
                    else -> CatalogField.notFound("ability description was not resolved from the ROM")
                },
            )
        }
        val balls = if (layout.generation == 3) BallSpriteMaterializer.captureBalls(rom) else emptyMap()
        val capabilities = initialCapabilities.toMutableMap()
        capabilities[RomCapability.MOVE_DESCRIPTIONS] = if (moveDescriptions != null) {
            CapabilityEvidence(
                capability = RomCapability.MOVE_DESCRIPTIONS,
                compatible = true,
                confidence = moveDescriptions.confidence,
                offset = moveDescriptions.sourceOffset,
                count = moveDescriptions.descriptions.size,
                reasons = listOf("decoded a validated move-description pointer table"),
                status = CapabilityStatus.AVAILABLE,
            )
        } else {
            CapabilityEvidence(
                capability = RomCapability.MOVE_DESCRIPTIONS,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation < 3) "this engine has no compatible move-description pointer table"
                    else "move-description pointer table was not resolved",
                ),
                status = if (layout.generation < 3) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
            )
        }
        capabilities[RomCapability.ABILITY_DESCRIPTIONS] = if (abilityDescriptions != null) {
            CapabilityEvidence(
                capability = RomCapability.ABILITY_DESCRIPTIONS,
                compatible = true,
                confidence = abilityDescriptions.confidence,
                offset = abilityDescriptions.sourceOffset,
                count = abilityDescriptions.descriptions.size,
                reasons = listOf("decoded a validated ability-description pointer table"),
                status = CapabilityStatus.AVAILABLE,
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
        moveAcquisitions.evidence.values.forEach { evidence -> capabilities[evidence.capability] = evidence }
        capabilities[RomCapability.AREA_ENCOUNTERS] = collectionCapability(
            RomCapability.AREA_ENCOUNTERS,
            encounters.size,
            "structurally decoded encounter areas",
            "encounter tables were not located",
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
                learnsetRulesets.forEach {
                    add(
                        "learnset ruleset ${it.id}: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)} primary=${it.primary}",
                    )
                }
            },
        )
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.EXTENDED, 4, 5, catalog))
        onProgress?.invoke(CatalogMaterializationProgress(CatalogMaterializationPhase.COMPLETE, 5, 5, catalog))
        return catalog
    }

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
