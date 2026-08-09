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

object CatalogParser {
    fun parse(rom: RomImage): CatalogParseResult {
        val analysis = ParserOrchestrator.analyze(rom)
        if (analysis.status != SelectionStatus.SELECTED || analysis.selectedFamily == null) {
            return CatalogParseResult(analysis, null, null)
        }
        val layout = analysis.probes.singleOrNull { it.family == analysis.selectedFamily }?.resolvedLayout
            ?: return CatalogParseResult(analysis, null, null)
        return CatalogParseResult(analysis, layout, CatalogMaterializer.materialize(rom, analysis, layout))
    }
}

object CatalogMaterializer {
    fun materialize(rom: RomImage, analysis: ParseResult, layout: ResolvedRomLayout): ParsedCatalog {
        val descriptions = RelationshipMaterializers.descriptions(rom, layout)
        val evolutions = RelationshipMaterializers.evolutions(rom, layout)
        val learnsets = RelationshipMaterializers.learnsets(rom, layout)
        val learnsetRulesets = LearnsetRulesetMaterializer.materialize(rom, layout, learnsets)
        val moveDescriptions = MoveDescriptionMaterializer.materialize(rom, layout)
        val moveAcquisitions = MoveAcquisitionMaterializer.materialize(rom, layout)
        val sprites = SpriteMaterializer.pokemon(rom, layout)
        val baseSpecies = RecordMaterializers.species(rom, layout)
        val species = baseSpecies.mapValues { (id, record) ->
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
                moveAcquisitions = if (moveAcquisitions.evidence.values.any { it.compatible }) {
                    CatalogField.available(moveAcquisitions.acquisitionsBySpecies[id].orEmpty())
                } else {
                    CatalogField.notFound("non-level move acquisition tables were not resolved")
                },
            )
        }
        val moves = RecordMaterializers.moves(rom, layout).mapValues { (id, move) ->
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
        val chart = RecordMaterializers.typeChart(rom, layout)
        val types = TypePresentationMaterializer.apply(RecordMaterializers.types(layout, species, chart, moves))
        val encounters = EncounterMaterializer.materialize(rom, layout)
        val balls = if (layout.generation == 3) BallSpriteMaterializer.captureBalls(rom) else emptyMap()
        val capabilities = analysis.capabilities.associateBy { it.capability }.toMutableMap()
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
        moveAcquisitions.evidence.values.forEach { evidence -> capabilities[evidence.capability] = evidence }
        capabilities[RomCapability.AREA_ENCOUNTERS] = collectionCapability(
            RomCapability.AREA_ENCOUNTERS,
            encounters.size,
            "structurally decoded encounter areas",
            "encounter tables were not located",
        )
        capabilities[RomCapability.TYPE_PRESENTATION] = collectionCapability(
            RomCapability.TYPE_PRESENTATION,
            types.values.count { it.presentation.status == CapabilityStatus.AVAILABLE },
            "family type colors with explicit accessible fallback for custom IDs",
            "no materialized types were available for presentation",
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
        return ParsedCatalog(
            romSha256 = analysis.sha256,
            family = layout.family,
            platform = layout.platform,
            romCrc32 = analysis.crc32,
            speciesById = species,
            movesById = moves,
            typesById = types,
            abilitiesById = RecordMaterializers.abilities(rom, layout),
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
                learnsetRulesets.forEach {
                    add(
                        "learnset ruleset ${it.id}: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)} primary=${it.primary}",
                    )
                }
            },
        )
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
