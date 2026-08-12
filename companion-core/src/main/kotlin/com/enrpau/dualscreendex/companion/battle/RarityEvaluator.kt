package com.enrpau.dualscreendex.companion.battle

import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.companion.owned.PreferredIndividualSelector
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import kotlin.math.roundToInt

enum class RelativeTier(val adjustment: Double) {
    WEAK(-0.5),
    ORDINARY(0.0),
    COMPETENT(0.5),
    STRONG(0.5),
    MAJOR(0.5),
}

enum class InnateTier(val baseStars: Int) {
    FODDER(0),
    STANDARD(1),
    TRAINED(2),
    VETERAN(3),
    ELITE(4),
    ACE(5),
}

enum class AreaRarityOutcome {
    AREA_UNAVAILABLE,
    AREA_NOT_IN_CATALOG,
    SPECIES_LEVEL_NOT_IN_AREA,
    INVALID_WEIGHTS,
    AMBIGUOUS_TIER,
    APPLIED,
    APPLIED_UNIQUE_ENCOUNTER,
}

data class RarityAssessment(
    val relativeTier: RelativeTier?,
    val innateTier: InnateTier?,
    val baseStars: Int?,
    val areaAdjustment: Double?,
    val stars: Double?,
    val areaOutcome: AreaRarityOutcome,
    val currentAreaBaseId: Int?,
    val matchingAreaCount: Int,
    val candidateAreaCount: Int,
)

object RarityEvaluator {
    fun evaluate(
        individual: OwnedPokemon,
        currentAreaBaseId: Int?,
        encounterAreas: List<EncounterArea>,
    ): RarityAssessment {
        val innateTier = innateTier(individual)
        val area = relativeAssessment(individual, currentAreaBaseId, encounterAreas)
        val relativeTier = area.tier
        val areaAdjustment = relativeTier?.adjustment
        val baseStars = innateTier?.baseStars
        return RarityAssessment(
            relativeTier = relativeTier,
            innateTier = innateTier,
            baseStars = baseStars,
            areaAdjustment = areaAdjustment,
            stars = baseStars?.let { (it + (areaAdjustment ?: 0.0)).coerceIn(0.0, 5.0) },
            areaOutcome = area.outcome,
            currentAreaBaseId = currentAreaBaseId,
            matchingAreaCount = area.matchingAreaCount,
            candidateAreaCount = area.candidateAreaCount,
        )
    }

    private fun innateTier(individual: OwnedPokemon): InnateTier? = when (PreferredIndividualSelector.innateAverage(individual)) {
        in 0..9 -> InnateTier.FODDER
        in 10..17 -> InnateTier.STANDARD
        in 18..23 -> InnateTier.TRAINED
        in 24..27 -> InnateTier.VETERAN
        in 28..29 -> InnateTier.ELITE
        in 30..31 -> InnateTier.ACE
        else -> null
    }

    private fun relativeAssessment(
        individual: OwnedPokemon,
        currentAreaBaseId: Int?,
        encounterAreas: List<EncounterArea>,
    ): RelativeAssessment {
        if (encounterAreas.isEmpty()) {
            return RelativeAssessment(outcome = AreaRarityOutcome.AREA_UNAVAILABLE)
        }
        val capableAreas = encounterAreas.filter { area ->
            area.slots.any { slot ->
                slot.speciesId == individual.speciesId && individual.level in slot.minimumLevel..slot.maximumLevel
            }
        }
        val matchingAreas = currentAreaBaseId?.let { baseId ->
            encounterAreas.filter { area -> area.id / 10 == baseId }
        }.orEmpty()
        val localCandidates = matchingAreas.filter(capableAreas::contains)
        if (localCandidates.isNotEmpty()) {
            return assessCandidates(
                individual = individual,
                candidates = localCandidates,
                matchingAreaCount = matchingAreas.size,
                successOutcome = AreaRarityOutcome.APPLIED,
            )
        }
        if (currentAreaBaseId == null) {
            return RelativeAssessment(
                outcome = AreaRarityOutcome.AREA_UNAVAILABLE,
                candidateAreaCount = capableAreas.size,
            )
        }
        if (matchingAreas.isEmpty()) {
            return RelativeAssessment(
                outcome = AreaRarityOutcome.AREA_NOT_IN_CATALOG,
                candidateAreaCount = capableAreas.size,
            )
        }
        return RelativeAssessment(
            outcome = AreaRarityOutcome.SPECIES_LEVEL_NOT_IN_AREA,
            matchingAreaCount = matchingAreas.size,
            candidateAreaCount = capableAreas.size,
        )
    }

    private fun assessCandidates(
        individual: OwnedPokemon,
        candidates: List<EncounterArea>,
        matchingAreaCount: Int,
        successOutcome: AreaRarityOutcome,
    ): RelativeAssessment {
        if (candidates.isEmpty()) {
            return RelativeAssessment(
                outcome = AreaRarityOutcome.SPECIES_LEVEL_NOT_IN_AREA,
                matchingAreaCount = matchingAreaCount,
            )
        }

        val tiers = candidates.map { area ->
            val referenceLevel = weightedReferenceLevel(area) ?: return RelativeAssessment(
                outcome = AreaRarityOutcome.INVALID_WEIGHTS,
                matchingAreaCount = matchingAreaCount,
                candidateAreaCount = candidates.size,
            )
            classifyRelativeLevel(individual.level - referenceLevel)
        }
        val tier = tiers.distinct().singleOrNull() ?: return RelativeAssessment(
            outcome = AreaRarityOutcome.AMBIGUOUS_TIER,
            matchingAreaCount = matchingAreaCount,
            candidateAreaCount = candidates.size,
        )
        return RelativeAssessment(
            tier = tier,
            outcome = successOutcome,
            matchingAreaCount = matchingAreaCount,
            candidateAreaCount = candidates.size,
        )
    }

    private fun weightedReferenceLevel(area: EncounterArea): Int? {
        if (area.slots.isEmpty()) return null
        var weightedLevels = 0.0
        var totalWeight = 0L
        area.slots.forEach { slot ->
            val weight = slot.weight ?: return null
            if (weight <= 0 || slot.minimumLevel <= 0 || slot.maximumLevel < slot.minimumLevel) return null
            weightedLevels += ((slot.minimumLevel + slot.maximumLevel) / 2.0) * weight
            totalWeight += weight
        }
        if (totalWeight <= 0L) return null
        return (weightedLevels / totalWeight).roundToInt()
    }

    private fun classifyRelativeLevel(difference: Int): RelativeTier = when (difference) {
        in Int.MIN_VALUE..-3 -> RelativeTier.WEAK
        in -2..1 -> RelativeTier.ORDINARY
        in 2..3 -> RelativeTier.COMPETENT
        in 4..5 -> RelativeTier.STRONG
        else -> RelativeTier.MAJOR
    }

    private data class RelativeAssessment(
        val tier: RelativeTier? = null,
        val outcome: AreaRarityOutcome,
        val matchingAreaCount: Int = 0,
        val candidateAreaCount: Int = 0,
    )
}
