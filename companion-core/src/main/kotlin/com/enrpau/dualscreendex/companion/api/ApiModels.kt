package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.companion.knowledge.KnowledgePolicy
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.owned.PreferredIndividualSelector
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.LearnsetNormalizer
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

data class BootstrapView(val catalog: CatalogView?, val state: StateView)

data class CatalogView(
    val hash: String,
    val crc32: String,
    val family: String,
    val platform: String,
    val rulesets: List<RulesetView>,
    val species: List<SpeciesView>,
    val moves: List<MoveView>,
    val types: List<TypeView>,
    val areas: List<AreaView>,
    val balls: List<BallView>,
    val capabilities: Map<String, String>,
)

data class SpeciesView(
    val id: Int,
    val dex: Int,
    val name: String,
    val typeIds: List<Int>,
    val stats: Map<String, Int>?,
    val description: String?,
    val height: Int?,
    val weight: Int?,
    val learnset: List<LearnsetView>,
    val learnsets: Map<String, List<LearnsetView>>,
    val normalizedLearnsets: Map<String, List<NormalizedMoveView>>,
    val moveAcquisitions: List<MoveAcquisitionView>,
    val abilities: List<AbilityView>,
    val evolutions: List<EvolutionView>,
    val hasSprite: Boolean,
)

data class LearnsetView(val level: Int, val moveId: Int)
data class NormalizedMoveView(val moveId: Int, val initial: Boolean, val levels: List<Int>, val label: String)
data class RulesetView(
    val id: String,
    val label: String,
    val sourceOffset: Int,
    val confidence: Double,
    val primary: Boolean,
)
data class MoveAcquisitionView(val moveId: Int, val method: String, val sourceId: Int?)
data class AbilityMechanicView(
    val kind: String,
    val label: String,
    val value: String,
    val numerator: Int,
    val denominator: Int,
)
data class AbilityView(
    val id: Int,
    val name: String,
    val description: String?,
    val mechanics: List<AbilityMechanicView>,
)
data class EvolutionView(
    val targetSpeciesId: Int,
    val targetName: String,
    val methodId: Int,
    val parameter: Int,
    val condition: String,
)
data class MoveView(
    val id: Int,
    val name: String,
    val typeId: Int?,
    val category: String?,
    val power: Int?,
    val accuracy: Int?,
    val pp: Int?,
    val priority: Int?,
    val effectId: Int?,
    val description: String?,
)
data class TypeView(val id: Int, val name: String, val foreground: String?, val background: String?, val border: String?)
data class EncounterSlotView(
    val speciesId: Int,
    val minimumLevel: Int,
    val maximumLevel: Int,
    val weight: Int?,
)
data class AreaView(
    val id: Int,
    val name: String,
    val methodId: Int,
    val speciesIds: List<Int>,
    val slots: List<EncounterSlotView>,
)
data class BallView(val id: Int, val name: String, val generic: Boolean, val hasSprite: Boolean)

data class DiagnosticCapabilityView(
    val capability: String,
    val status: String,
    val confidence: Double,
    val offset: Int?,
    val count: Int?,
    val recordSize: Int?,
    val reasons: List<String>,
)

data class DiagnosticView(
    val romName: String?,
    val sha256: String,
    val crc32: String,
    val family: String,
    val platform: String,
    val activeRulesetId: String?,
    val rulesetAssumed: Boolean,
    val rulesets: List<RulesetView>,
    val capabilities: List<DiagnosticCapabilityView>,
    val parserDiagnostics: List<String>,
    val species: SpeciesView?,
    val move: MoveView?,
)

data class StateView(
    val version: Long,
    val screen: String,
    val priorScreen: String,
    val settingsReturnScreen: String,
    val selectedSpeciesId: Int?,
    val filter: String,
    val selectedAreaId: Int?,
    val battleTab: String,
    val settings: Any,
    val speciesState: Map<Int, SpeciesStateView>,
    val observedMoves: Map<Int, List<ObservedMoveView>>,
    val battle: BattleView?,
    val catalogReady: Boolean,
    val catalogName: String?,
    val error: String?,
    val activeRulesetId: String?,
    val rulesetAssumed: Boolean,
    val loading: CatalogLoadingView,
)
data class CatalogLoadingView(
    val active: Boolean,
    val phase: String,
    val completedUnits: Int,
    val totalUnits: Int,
)

data class SpeciesStateView(val seen: Boolean, val caught: Boolean, val team: Boolean, val ballId: Int?)
data class BattleView(
    val opponents: List<OpponentView>,
    val targetIndex: Int,
    val selectedMoveId: Int?,
    val effectiveness: String?,
    val effectivenessKnown: Boolean,
)
data class OpponentView(
    val speciesId: Int,
    val level: Int,
    val rarity: String,
    val moves: List<ObservedMoveView>,
)
data class ObservedMoveView(val moveId: Int, val encounters: Int, val lastSeen: Long)

object ApiViewBuilder {
    fun catalog(catalog: ParsedCatalog): CatalogView = CatalogView(
        hash = catalog.romSha256,
        crc32 = catalog.romCrc32,
        family = catalog.family.name,
        platform = catalog.platform.name,
        rulesets = catalog.learnsetRulesets.map {
            RulesetView(it.id, it.label, it.sourceOffset, it.confidence, it.primary)
        },
        species = catalog.navigableSpecies().sortedWith(compareBy({ it.dexNumber.value }, { it.id })).map { species ->
            val stats = species.baseStats.value
            val rulesetLearnsets = catalog.learnsetRulesets.associate { ruleset ->
                ruleset.id to ruleset.entriesBySpecies[species.id].orEmpty()
            }.ifEmpty {
                mapOf("default" to species.learnset.value.orEmpty())
            }
            SpeciesView(
                id = species.id,
                dex = species.dexNumber.value ?: species.id,
                name = species.name.value ?: "#${species.id}",
                typeIds = species.typeIds.value.orEmpty(),
                stats = stats?.let {
                    linkedMapOf(
                        "HP" to it.hp,
                        "ATTACK" to it.attack,
                        "DEFENSE" to it.defense,
                        "SPEED" to it.speed,
                        "SP. ATK" to it.specialAttack,
                        "SP. DEF" to it.specialDefense,
                    )
                },
                description = species.description.value,
                height = species.height.value,
                weight = species.weight.value,
                learnset = species.learnset.value.orEmpty().map { LearnsetView(it.level, it.moveId) },
                learnsets = rulesetLearnsets.mapValues { (_, entries) ->
                    entries.map { LearnsetView(it.level, it.moveId) }
                },
                normalizedLearnsets = rulesetLearnsets.mapValues { (_, entries) ->
                    LearnsetNormalizer.normalize(entries).map { normalized ->
                        val parts = buildList {
                            if (normalized.initial) add("Initial")
                            normalized.levels.forEach { add("Lv $it") }
                        }
                        NormalizedMoveView(
                            normalized.moveId,
                            normalized.initial,
                            normalized.levels,
                            parts.joinToString(" · "),
                        )
                    }
                },
                moveAcquisitions = species.moveAcquisitions.value.orEmpty().map {
                    MoveAcquisitionView(it.moveId, it.method.name, it.sourceId)
                },
                abilities = species.abilityIds.value.orEmpty().mapNotNull { abilityId ->
                    val ability = catalog.abilitiesById[abilityId]
                    val name = ability?.name?.value
                    if (abilityId == 0 || name.isNullOrBlank()) null
                    else AbilityView(
                        abilityId,
                        name,
                        ability.description.value,
                        ability.mechanics.value.orEmpty().map { mechanic ->
                            AbilityMechanicView(
                                mechanic.kind.name,
                                mechanic.label,
                                mechanic.value,
                                mechanic.numerator,
                                mechanic.denominator,
                            )
                        },
                    )
                },
                evolutions = species.evolutionEdges.value.orEmpty().map { edge ->
                    EvolutionView(
                        edge.targetSpeciesId,
                        catalog.speciesById[edge.targetSpeciesId]?.name?.value ?: "Species ${edge.targetSpeciesId}",
                        edge.methodId,
                        edge.parameter,
                        evolutionCondition(catalog, edge),
                    )
                },
                hasSprite = species.sprite.value != null,
            )
        },
        moves = catalog.movesById.values.sortedBy { it.id }.map {
            MoveView(
                it.id,
                it.name.value ?: "#${it.id}",
                it.typeId.value,
                it.category.value?.name,
                it.power.value,
                it.accuracy.value,
                it.pp.value,
                it.priority.value,
                it.effectId.value,
                it.effectText.value,
            )
        },
        types = catalog.typesById.values.sortedBy { it.id }.map {
            val presentation = it.presentation.value
            TypeView(
                it.id,
                it.name.value ?: "TYPE ${it.id}",
                presentation?.foregroundArgb?.toCss(),
                presentation?.backgroundArgb?.toCss(),
                presentation?.borderArgb?.toCss(),
            )
        },
        areas = catalog.encounterAreas.sortedBy { it.id }.map {
            AreaView(
                it.id,
                it.name.value ?: "Area ${it.id}",
                it.methodId,
                it.slots.map { slot -> slot.speciesId }.filter { id -> id > 0 }.distinct(),
                it.slots.map { slot ->
                    EncounterSlotView(slot.speciesId, slot.minimumLevel, slot.maximumLevel, slot.weight)
                },
            )
        },
        balls = catalog.captureBallsById.values.sortedBy { it.id }.map {
            BallView(it.id, it.name.value ?: "Ball ${it.id}", it.generic, it.sprite.value != null)
        },
        capabilities = catalog.capabilities.mapKeys { it.key.name }.mapValues { it.value.status.name },
    )

    fun state(
        snapshot: AppSnapshot,
        catalog: ParsedCatalog?,
        truth: Effectiveness? = null,
        activeRulesetId: String? = null,
        rulesetAssumed: Boolean = true,
    ): StateView {
        val speciesState = catalog?.navigableSpecies()?.associate { species ->
            val owned = snapshot.ledger.owned.filter { it.speciesId == species.id }
            species.id to SpeciesStateView(
                seen = species.id in snapshot.ledger.seenSpecies,
                caught = owned.isNotEmpty(),
                team = species.id in snapshot.ledger.teamSpecies,
                ballId = PreferredIndividualSelector.select(owned)?.captureBallId
                    ?.takeIf { it in catalog.captureBallsById },
            )
        }.orEmpty()
        val activeBattle = snapshot.battle
        val target = activeBattle?.opponents?.getOrNull(activeBattle.targetIndex)
        val knownEffectiveness = target?.let { opponent ->
            activeBattle.selectedMoveId?.let { moveId ->
                KnowledgePolicy.matchup(snapshot.settings.knowledgeMode, opponent.speciesId, moveId, truth, snapshot.ledger)
            }
        }
        return StateView(
            snapshot.version,
            snapshot.screen.name,
            snapshot.priorScreen.name,
            snapshot.settingsReturnScreen.name,
            snapshot.selectedSpeciesId,
            snapshot.filter.name,
            snapshot.selectedAreaId,
            snapshot.battleTab.name,
            snapshot.settings,
            speciesState,
            snapshot.ledger.observedMoves.mapValues { (_, observations) ->
                observations.map { ObservedMoveView(it.moveId, it.encounterCount, it.lastSeenSequence) }
            },
            snapshot.battle?.let { battle ->
                BattleView(
                    opponents = battle.opponents.map { opponent ->
                        val generation = when (catalog?.platform?.name) {
                            "GBA" -> 3
                            "GBC" -> 2
                            else -> 1
                        }
                        val individual = com.enrpau.dualscreendex.companion.model.OwnedPokemon(
                            "battle",
                            opponent.speciesId,
                            generation,
                            opponent.level,
                            ivs = opponent.ivs,
                            dvs = opponent.dvs,
                        )
                        val prefix = PreferredIndividualSelector.levelPrefix(opponent.level, battle.playerReferenceLevel)
                        val tier = PreferredIndividualSelector.tier(individual)
                        OpponentView(
                            opponent.speciesId,
                            opponent.level,
                            listOfNotNull(prefix, tier).joinToString(" "),
                            opponent.moveHistory.map { ObservedMoveView(it.moveId, it.encounterCount, it.lastSeenSequence) },
                        )
                    },
                    targetIndex = battle.targetIndex,
                    selectedMoveId = battle.selectedMoveId,
                    effectiveness = knownEffectiveness?.name,
                    effectivenessKnown = knownEffectiveness != null,
                )
            },
            snapshot.catalogReady,
            snapshot.catalogName,
            snapshot.error,
            activeRulesetId,
            rulesetAssumed,
            CatalogLoadingView(
                snapshot.catalogLoading.active,
                snapshot.catalogLoading.phase,
                snapshot.catalogLoading.completedUnits,
                snapshot.catalogLoading.totalUnits,
            ),
        )
    }

    fun diagnostics(
        catalog: ParsedCatalog,
        romName: String?,
        activeRulesetId: String?,
        rulesetAssumed: Boolean,
        speciesId: Int?,
        moveId: Int?,
    ): DiagnosticView {
        val view = catalog(catalog)
        return DiagnosticView(
            romName = romName,
            sha256 = catalog.romSha256,
            crc32 = catalog.romCrc32,
            family = catalog.family.name,
            platform = catalog.platform.name,
            activeRulesetId = activeRulesetId,
            rulesetAssumed = rulesetAssumed,
            rulesets = view.rulesets,
            capabilities = catalog.capabilities.values.sortedBy { it.capability.ordinal }.map {
                DiagnosticCapabilityView(
                    it.capability.name,
                    it.status.name,
                    it.confidence,
                    it.offset,
                    it.count,
                    it.recordSize,
                    it.reasons,
                )
            },
            parserDiagnostics = catalog.diagnostics,
            species = speciesId?.let { id -> view.species.firstOrNull { it.id == id } },
            move = moveId?.let { id -> view.moves.firstOrNull { it.id == id } },
        )
    }

    private fun Int.toCss(): String = "#%02X%02X%02X%02X".format(
        this ushr 16 and 0xFF,
        this ushr 8 and 0xFF,
        this and 0xFF,
        this ushr 24 and 0xFF,
    )

    private fun evolutionCondition(catalog: ParsedCatalog, edge: EvolutionEdge): String {
        val generation = when (catalog.platform) {
            com.enrpau.dualscreendex.parser.model.Platform.GBA -> 3
            com.enrpau.dualscreendex.parser.model.Platform.GBC -> 2
            else -> 1
        }
        return when {
            generation == 3 && edge.methodId == 4 -> "Level ${edge.parameter}"
            generation <= 2 && edge.methodId == 1 -> "Level ${edge.parameter}"
            generation == 3 && edge.methodId == 5 -> "Trade"
            generation <= 2 && edge.methodId == 3 -> "Trade"
            generation == 3 && edge.methodId == 6 -> "Trade with item ${edge.parameter}"
            (generation == 3 && edge.methodId == 7) || (generation <= 2 && edge.methodId == 2) -> "Use item ${edge.parameter}"
            generation == 3 && edge.methodId in 1..3 -> "High friendship"
            else -> "Method ${edge.methodId} · parameter ${edge.parameter}"
        }
    }
}
