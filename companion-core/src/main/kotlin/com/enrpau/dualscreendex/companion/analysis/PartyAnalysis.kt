package com.enrpau.dualscreendex.companion.analysis

data class PartyAnalysis(
    val teamSummary: PartyTeamSummary,
    val offensiveCoverage: OffensiveCoverage?,
    val defensiveProfile: DefensiveProfile?,
    val development: PartyDevelopment,
)

data class PartyTeamSummary(
    val partySize: Int,
    val minimumLevel: Int?,
    val maximumLevel: Int?,
    val faintedCount: Int,
    val statusCount: Int,
    val moveDistribution: MoveCategoryDistribution?,
)

data class MoveCategoryDistribution(
    val physical: Int,
    val special: Int,
    val status: Int,
    val unresolved: Int,
)

data class OffensiveCoverage(
    val contributingMoveCount: Int,
    val types: List<OffensiveTypeCoverage>,
)

data class OffensiveTypeCoverage(
    val defendingTypeId: Int,
    val outcome: OffensiveCoverageOutcome,
    val bestMultiplierPercent: Int?,
    val attackingTypeIds: List<Int>,
    val memberSlots: List<Int>,
)

enum class OffensiveCoverageOutcome {
    SUPER_EFFECTIVE,
    NEUTRAL_ONLY,
    NO_EFFECTIVE_KNOWN_OPTION,
}

data class DefensiveProfile(
    val members: List<PartyMemberDefense>,
    val unavailableMemberSlots: List<Int>,
    val repeatedWeaknesses: List<RepeatedWeakness>,
)

data class PartyMemberDefense(
    val slot: Int,
    val speciesId: Int,
    val typeIds: List<Int>,
    val availableForImmediateBattle: Boolean,
    val weaknessTypeIds: List<Int>,
    val resistanceTypeIds: List<Int>,
    val immunityTypeIds: List<Int>,
    val abilityModifiers: List<AppliedAbilityModifier>,
)

data class AppliedAbilityModifier(
    val abilityId: Int,
    val attackingTypeId: Int,
    val numerator: Int,
    val denominator: Int,
)

data class RepeatedWeakness(val attackingTypeId: Int, val memberCount: Int)

data class PartyDevelopment(
    val evolutionOpportunities: List<EvolutionOpportunity>,
    val nearbyMoves: List<NearbyMove>,
    val moveRoleGaps: List<MoveRoleGap>,
)

data class EvolutionOpportunity(
    val slot: Int,
    val speciesId: Int,
    val targetSpeciesId: Int,
    val methodId: Int,
    val parameter: Int,
    val availableNow: Boolean?,
)

data class NearbyMove(
    val slot: Int,
    val speciesId: Int,
    val moveId: Int,
    val level: Int,
    val levelsAway: Int,
)

enum class MoveRoleGap { PHYSICAL, SPECIAL }
