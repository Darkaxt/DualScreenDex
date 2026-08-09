package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.companion.knowledge.KnowledgePolicy
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.owned.PreferredIndividualSelector
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

data class BootstrapView(val catalog: CatalogView?, val state: StateView)

data class CatalogView(
    val hash: String,
    val family: String,
    val platform: String,
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
    val hasSprite: Boolean,
)

data class LearnsetView(val level: Int, val moveId: Int)
data class MoveView(
    val id: Int,
    val name: String,
    val typeId: Int?,
    val category: String?,
    val power: Int?,
    val accuracy: Int?,
    val pp: Int?,
    val priority: Int?,
)
data class TypeView(val id: Int, val name: String, val foreground: String?, val background: String?, val border: String?)
data class AreaView(val id: Int, val name: String, val speciesIds: List<Int>)
data class BallView(val id: Int, val name: String, val generic: Boolean, val hasSprite: Boolean)

data class StateView(
    val version: Long,
    val screen: String,
    val priorScreen: String,
    val selectedSpeciesId: Int?,
    val filter: String,
    val selectedAreaId: Int?,
    val battleTab: String,
    val settings: Any,
    val speciesState: Map<Int, SpeciesStateView>,
    val battle: BattleView?,
    val catalogReady: Boolean,
    val catalogName: String?,
    val error: String?,
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
        family = catalog.family.name,
        platform = catalog.platform.name,
        species = catalog.navigableSpecies().sortedWith(compareBy({ it.dexNumber.value }, { it.id })).map { species ->
            val stats = species.baseStats.value
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
            AreaView(it.id, it.name.value ?: "Area ${it.id}", it.slots.map { slot -> slot.speciesId }.filter { id -> id > 0 }.distinct())
        },
        balls = catalog.captureBallsById.values.sortedBy { it.id }.map {
            BallView(it.id, it.name.value ?: "Ball ${it.id}", it.generic, it.sprite.value != null)
        },
        capabilities = catalog.capabilities.mapKeys { it.key.name }.mapValues { it.value.status.name },
    )

    fun state(snapshot: AppSnapshot, catalog: ParsedCatalog?, truth: Effectiveness? = null): StateView {
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
            snapshot.selectedSpeciesId,
            snapshot.filter.name,
            snapshot.selectedAreaId,
            snapshot.battleTab.name,
            snapshot.settings,
            speciesState,
            snapshot.battle?.let { battle ->
                BattleView(
                    opponents = battle.opponents.map { opponent ->
                        val individual = com.enrpau.dualscreendex.companion.model.OwnedPokemon(
                            "battle", opponent.speciesId, 3, opponent.level, ivs = opponent.ivs,
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
        )
    }

    private fun Int.toCss(): String = "#%02X%02X%02X%02X".format(
        this ushr 16 and 0xFF,
        this ushr 8 and 0xFF,
        this and 0xFF,
        this ushr 24 and 0xFF,
    )
}
