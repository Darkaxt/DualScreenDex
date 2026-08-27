package com.enrpau.dualscreendex.simulator

import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OpponentState
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord

data class SimulationRequest(
    val seed: Long,
    val opponentCount: Int = 1,
    val minimumLevel: Int = 20,
    val maximumLevel: Int = 45,
    val captured: Boolean = false,
    val areaId: Int? = null,
    val encounterOrdinal: Long = 0,
)

data class SimulationResult(
    val battle: BattleState,
    val ledger: KnowledgeLedger,
)

class EncounterSimulator(private val catalog: ParsedCatalog) {
    fun generate(
        request: SimulationRequest,
        previous: KnowledgeLedger = KnowledgeLedger(),
        activeRulesetId: String? = null,
    ): SimulationResult {
        require(request.opponentCount in 1..2) { "the POC supports one or two opponents" }
        require(request.minimumLevel in 1..100 && request.maximumLevel in request.minimumLevel..100)
        val random = SplitMix64(request.seed)
        val pool = speciesPool(request.areaId).ifEmpty {
            catalog.navigableSpecies().filter { it.sprite.value != null }
        }
        require(pool.isNotEmpty()) { "catalog contains no encounter-ready species" }
        val generation = generation()

        val opponents = List(request.opponentCount) { index ->
            val species = pool[random.nextInt(pool.size)]
            val level = request.minimumLevel + random.nextInt(request.maximumLevel - request.minimumLevel + 1)
            val possibleMoves = activeLearnset(species, activeRulesetId)
                .filter { it.level <= level && it.moveId in catalog.movesById }
                .map { it.moveId }
                .distinct()
            val observed = possibleMoves.shuffledWith(random).take(minOf(4, possibleMoves.size)).map { moveId ->
                MoveObservation(moveId, 1 + random.nextInt(4))
            }
            OpponentState(
                speciesId = species.id,
                level = level,
                typeIds = species.typeIds.value.orEmpty(),
                ivs = if (generation == 3) List(6) { random.nextInt(32) } else emptyList(),
                dvs = if (generation < 3) List(4) { random.nextInt(16) } else emptyList(),
                moveHistory = observed.sortedWith(
                    compareByDescending<MoveObservation> { it.frequency }.thenBy { it.moveId },
                ),
            )
        }
        val seen = previous.seenSpecies + opponents.map { it.speciesId }
        val owned = previous.owned.toMutableList()
        if (request.captured) {
            opponents.distinctBy { it.speciesId }.forEach { opponent ->
                repeat(2) { copy ->
                    owned += OwnedPokemon(
                        stableKey = "sim-${request.seed}-${request.encounterOrdinal}-${opponent.speciesId}-$copy",
                        speciesId = opponent.speciesId,
                        generation = generation,
                        level = opponent.level + copy,
                        ivs = if (generation == 3) {
                            if (copy == 0) opponent.ivs else List(6) { random.nextInt(32) }
                        } else emptyList(),
                        dvs = if (generation < 3) {
                            if (copy == 0) opponent.dvs else List(4) { random.nextInt(16) }
                        } else emptyList(),
                        captureBallId = catalog.captureBallsById.keys.sorted().takeIf { it.isNotEmpty() }
                            ?.let { ids -> ids[random.nextInt(ids.size)] },
                    )
                }
            }
        }
        val observed = previous.observedMoves.toMutableMap()
        opponents.forEach { opponent ->
            val merged = (observed[opponent.speciesId].orEmpty() + opponent.moveHistory)
                .groupBy { it.moveId }
                .map { (moveId, history) ->
                    MoveObservation(
                        moveId = moveId,
                        frequency = history.sumOf { it.frequency },
                    )
                }
                .sortedWith(compareByDescending<MoveObservation> { it.frequency }.thenBy { it.moveId })
            observed[opponent.speciesId] = merged
        }
        val playerReference = (request.minimumLevel + request.maximumLevel) / 2
        val selectedMove = catalog.movesById.values.firstOrNull { move ->
            (move.power.value ?: 0) > 0 && move.typeId.value != null
        }?.id
        val ledger = previous.copy(
            seenSpecies = seen,
            owned = owned,
            observedMoves = observed,
            knownMoves = previous.knownMoves + listOfNotNull(selectedMove),
        )
        return SimulationResult(
            BattleState(opponents, selectedMoveId = selectedMove, playerReferenceLevel = playerReference),
            ledger,
        )
    }

    fun effectiveness(moveId: Int, speciesId: Int): Effectiveness? {
        val moveType = catalog.movesById[moveId]?.typeId?.value ?: return null
        val defenderTypes = catalog.speciesById[speciesId]?.typeIds?.value.orEmpty().distinct()
        if (defenderTypes.isEmpty()) return null
        val multiplier = defenderTypes.fold(100) { total, typeId ->
            val edge = catalog.typeChart.lastOrNull {
                it.attackingTypeId == moveType && it.defendingTypeId == typeId
            }?.multiplierPercent ?: 100
            total * edge / 100
        }
        return when {
            multiplier == 0 -> Effectiveness.NO_EFFECT
            multiplier < 100 -> Effectiveness.RESISTED
            multiplier > 100 -> Effectiveness.SUPER_EFFECTIVE
            else -> Effectiveness.NEUTRAL
        }
    }

    private fun speciesPool(areaId: Int?): List<SpeciesRecord> {
        if (areaId == null) return catalog.navigableSpecies().filter { it.sprite.value != null }
        val ids = catalog.encounterAreas.filter { it.id == areaId }.flatMap { area -> area.slots.map { it.speciesId } }.toSet()
        return ids.mapNotNull(catalog.speciesById::get).filter { it.sprite.value != null && (it.dexNumber.value ?: 0) > 0 }
    }

    private fun activeLearnset(species: SpeciesRecord, activeRulesetId: String?): List<com.enrpau.dualscreendex.parser.catalog.LearnsetEntry> {
        val rulesets = catalog.learnsetRulesets
        if (rulesets.isEmpty()) return species.learnset.value.orEmpty()
        val ruleset = if (activeRulesetId == null) {
            require(rulesets.size == 1) { "level-up ruleset is unresolved for this multi-table catalog" }
            rulesets.single()
        } else {
            requireNotNull(rulesets.firstOrNull { it.id == activeRulesetId }) {
                "unknown catalog ruleset: $activeRulesetId"
            }
        }
        return ruleset.entriesBySpecies[species.id].orEmpty()
    }

    private fun generation(): Int = when (catalog.platform.name) {
        "GBA" -> 3
        "GBC" -> 2
        else -> 1
    }

    private fun <T> List<T>.shuffledWith(random: SplitMix64): List<T> {
        val values = toMutableList()
        for (index in values.lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            val value = values[index]
            values[index] = values[swap]
            values[swap] = value
        }
        return values
    }
}
