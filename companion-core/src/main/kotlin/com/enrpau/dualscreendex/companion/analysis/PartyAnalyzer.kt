package com.enrpau.dualscreendex.companion.analysis

import com.darkaxt.dualdex.save.OwnedIndividual
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.model.Platform

object PartyAnalyzer {
    fun analyze(
        party: List<OwnedIndividual>,
        catalog: ParsedCatalog,
        activeRulesetId: String? = null,
    ): PartyAnalysis {
        val immutableParty = party.toList()
        val chart = validatedTypeChart(catalog)
        return PartyAnalysis(
            teamSummary = teamSummary(immutableParty, catalog),
            offensiveCoverage = chart?.let { offensiveCoverage(immutableParty, catalog, it) },
            defensiveProfile = chart?.let { defensiveProfile(immutableParty, catalog, it) },
            development = development(immutableParty, catalog, activeRulesetId),
        )
    }

    private fun teamSummary(party: List<OwnedIndividual>, catalog: ParsedCatalog): PartyTeamSummary {
        val levels = party.mapNotNull { it.level }
        val moveCategories = party.flatMap { member ->
            member.details?.moveIds.orEmpty().filter { it > 0 }.map { moveId ->
                catalog.movesById[moveId]?.category?.value
            }
        }
        val distribution = moveCategories.takeIf { it.isNotEmpty() }?.let { categories ->
            MoveCategoryDistribution(
                physical = categories.count { it == MoveCategory.PHYSICAL },
                special = categories.count { it == MoveCategory.SPECIAL },
                status = categories.count { it == MoveCategory.STATUS },
                unresolved = categories.count { it == null || it == MoveCategory.UNKNOWN },
            )
        }
        return PartyTeamSummary(
            partySize = party.size,
            minimumLevel = levels.minOrNull(),
            maximumLevel = levels.maxOrNull(),
            faintedCount = party.count { it.details?.currentHp == 0 },
            statusCount = party.count { (it.details?.status ?: 0L) != 0L },
            moveDistribution = distribution,
        )
    }

    private fun offensiveCoverage(
        party: List<OwnedIndividual>,
        catalog: ParsedCatalog,
        chart: Map<Pair<Int, Int>, Int>,
    ): OffensiveCoverage {
        val moves = party.flatMapIndexed { slot, member ->
            member.details?.moveIds.orEmpty().filter { it > 0 }.mapNotNull { moveId ->
                val move = catalog.movesById[moveId] ?: return@mapNotNull null
                val power = move.power.value ?: return@mapNotNull null
                val typeId = move.typeId.value ?: return@mapNotNull null
                if (power <= 0 || move.category.value == MoveCategory.STATUS || typeId !in catalog.typesById) {
                    return@mapNotNull null
                }
                ResolvedDamagingMove(slot, moveId, typeId)
            }
        }
        val types = catalog.typesById.keys.sorted().map { defendingTypeId ->
            val evaluated = moves.map { move ->
                move to (chart[move.typeId to defendingTypeId] ?: NEUTRAL_MULTIPLIER)
            }
            val best = evaluated.maxOfOrNull { it.second }
            val bestMoves = best?.let { resolved -> evaluated.filter { it.second == resolved }.map { it.first } }.orEmpty()
            OffensiveTypeCoverage(
                defendingTypeId = defendingTypeId,
                outcome = when {
                    best == null || best < NEUTRAL_MULTIPLIER ->
                        OffensiveCoverageOutcome.NO_EFFECTIVE_KNOWN_OPTION
                    best == NEUTRAL_MULTIPLIER -> OffensiveCoverageOutcome.NEUTRAL_ONLY
                    else -> OffensiveCoverageOutcome.SUPER_EFFECTIVE
                },
                bestMultiplierPercent = best,
                attackingTypeIds = bestMoves.map { it.typeId }.distinct().sorted(),
                memberSlots = bestMoves.map { it.slot }.distinct().sorted(),
            )
        }
        return OffensiveCoverage(
            contributingMoveCount = moves.distinctBy { it.slot to it.moveId }.size,
            types = types,
        )
    }

    private fun defensiveProfile(
        party: List<OwnedIndividual>,
        catalog: ParsedCatalog,
        chart: Map<Pair<Int, Int>, Int>,
    ): DefensiveProfile {
        val unavailable = mutableListOf<Int>()
        val members = party.mapIndexedNotNull { slot, member ->
            val species = catalog.speciesById[member.speciesId]
            val typeIds = species?.typeIds?.value
                ?.filter { it in catalog.typesById }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
            if (typeIds == null) {
                unavailable += slot
                return@mapIndexedNotNull null
            }
            val ability = member.details?.abilityId?.let(catalog.abilitiesById::get)
            val abilityModifiers = mutableListOf<AppliedAbilityModifier>()
            val multipliers = catalog.typesById.keys.sorted().associateWith { attackingTypeId ->
                var multiplier = typeIds.fold(NEUTRAL_MULTIPLIER.toLong()) { current, defendingTypeId ->
                    current * (chart[attackingTypeId to defendingTypeId] ?: NEUTRAL_MULTIPLIER) /
                        NEUTRAL_MULTIPLIER
                }
                ability?.mechanics?.value.orEmpty().forEach { mechanic ->
                    val typedConditions = mechanic.conditions
                    val applies = mechanic.kind == AbilityMechanicKind.MULTIPLIER &&
                        mechanic.numerator >= 0 && mechanic.denominator > 0 &&
                        typedConditions.isNotEmpty() &&
                        typedConditions.all { it.kind == AbilityMechanicConditionKind.ATTACKING_MOVE_TYPE } &&
                        typedConditions.any { it.value == attackingTypeId.toLong() }
                    if (applies && multiplier * mechanic.numerator % mechanic.denominator == 0L) {
                        val abilityId = ability?.id ?: return@forEach
                        multiplier = multiplier * mechanic.numerator / mechanic.denominator
                        abilityModifiers += AppliedAbilityModifier(
                            abilityId = abilityId,
                            attackingTypeId = attackingTypeId,
                            numerator = mechanic.numerator,
                            denominator = mechanic.denominator,
                        )
                    }
                }
                multiplier.toInt()
            }
            PartyMemberDefense(
                slot = slot,
                speciesId = member.speciesId,
                typeIds = typeIds.sorted(),
                availableForImmediateBattle = !member.isEgg && member.details?.currentHp?.let { it > 0 } == true,
                weaknessTypeIds = multipliers.filterValues { it > NEUTRAL_MULTIPLIER }.keys.sorted(),
                resistanceTypeIds = multipliers.filterValues { it in 1 until NEUTRAL_MULTIPLIER }.keys.sorted(),
                immunityTypeIds = multipliers.filterValues { it == 0 }.keys.sorted(),
                abilityModifiers = abilityModifiers.distinct().sortedBy { it.attackingTypeId },
            )
        }
        val repeatedWeaknesses = catalog.typesById.keys.sorted().mapNotNull { attackingTypeId ->
            val count = members.count { attackingTypeId in it.weaknessTypeIds }
            count.takeIf { it >= 2 }?.let { RepeatedWeakness(attackingTypeId, it) }
        }
        return DefensiveProfile(
            members = members,
            unavailableMemberSlots = unavailable,
            repeatedWeaknesses = repeatedWeaknesses,
        )
    }

    private fun development(
        party: List<OwnedIndividual>,
        catalog: ParsedCatalog,
        activeRulesetId: String?,
    ): PartyDevelopment {
        val evolutions = mutableListOf<EvolutionOpportunity>()
        val nearbyMoves = mutableListOf<NearbyMove>()
        party.forEachIndexed { slot, member ->
            val species = catalog.speciesById[member.speciesId] ?: return@forEachIndexed
            species.evolutionEdges.value.orEmpty().forEach { edge ->
                val requiredLevel = levelRequirement(catalog.platform, edge.methodId, edge.parameter)
                evolutions += EvolutionOpportunity(
                    slot = slot,
                    speciesId = member.speciesId,
                    targetSpeciesId = edge.targetSpeciesId,
                    methodId = edge.methodId,
                    parameter = edge.parameter,
                    availableNow = requiredLevel?.let { level -> member.level?.let { it >= level } },
                )
            }
            val level = member.level ?: return@forEachIndexed
            val knownMoves = member.details?.moveIds.orEmpty().filter { it > 0 }.toSet()
            activeLearnset(catalog, member.speciesId, activeRulesetId, species.learnset.value.orEmpty())
                .asSequence()
                .filter { it.level > level && it.level <= level + NEARBY_LEVEL_WINDOW }
                .filter { it.moveId > 0 && it.moveId !in knownMoves && it.moveId in catalog.movesById }
                .distinctBy { it.level to it.moveId }
                .sortedWith(compareBy({ it.level }, { it.moveId }))
                .forEach { entry ->
                    nearbyMoves += NearbyMove(slot, member.speciesId, entry.moveId, entry.level, entry.level - level)
                }
        }
        return PartyDevelopment(
            evolutionOpportunities = evolutions.sortedWith(compareBy({ it.slot }, { it.targetSpeciesId }, { it.methodId })),
            nearbyMoves = nearbyMoves.sortedWith(compareBy({ it.slot }, { it.level }, { it.moveId })),
            moveRoleGaps = moveRoleGaps(party, catalog),
        )
    }

    private fun moveRoleGaps(party: List<OwnedIndividual>, catalog: ParsedCatalog): List<MoveRoleGap> {
        if (party.isEmpty()) return emptyList()
        val moveIds = party.flatMap { it.details?.moveIds.orEmpty() }.filter { it > 0 }
        if (moveIds.isEmpty()) return emptyList()
        val categories = moveIds.map { catalog.movesById[it]?.category?.value }
        if (categories.any { it == null || it == MoveCategory.UNKNOWN }) return emptyList()
        return buildList {
            if (MoveCategory.PHYSICAL !in categories) add(MoveRoleGap.PHYSICAL)
            if (MoveCategory.SPECIAL !in categories) add(MoveRoleGap.SPECIAL)
        }
    }

    private fun activeLearnset(
        catalog: ParsedCatalog,
        speciesId: Int,
        activeRulesetId: String?,
        fallback: List<LearnsetEntry>,
    ): List<LearnsetEntry> {
        val ruleset = activeRulesetId?.let { id -> catalog.learnsetRulesets.firstOrNull { it.id == id } }
            ?: catalog.learnsetRulesets.firstOrNull { it.primary }
        return ruleset?.entriesBySpecies?.get(speciesId) ?: fallback
    }

    private fun levelRequirement(platform: Platform, methodId: Int, parameter: Int): Int? = when {
        platform == Platform.GBA && methodId == 4 && parameter > 0 -> parameter
        platform in setOf(Platform.GB, Platform.GBC) && methodId == 1 && parameter > 0 -> parameter
        else -> null
    }

    private fun validatedTypeChart(catalog: ParsedCatalog): Map<Pair<Int, Int>, Int>? {
        if (catalog.typeChart.isEmpty() || catalog.typesById.isEmpty()) return null
        val activeTypeIds = catalog.typesById.keys
        if (catalog.typeChart.any {
                it.attackingTypeId !in activeTypeIds || it.defendingTypeId !in activeTypeIds ||
                    it.multiplierPercent < 0
            }
        ) return null
        val grouped = catalog.typeChart.groupBy { it.attackingTypeId to it.defendingTypeId }
        if (grouped.values.any { entries -> entries.map(TypeMatchup::multiplierPercent).distinct().size != 1 }) return null
        return grouped.mapValues { (_, entries) -> entries.first().multiplierPercent }
    }

    private data class ResolvedDamagingMove(val slot: Int, val moveId: Int, val typeId: Int)

    private const val NEUTRAL_MULTIPLIER = 100
    private const val NEARBY_LEVEL_WINDOW = 5
}
