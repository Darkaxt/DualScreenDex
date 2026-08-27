package com.enrpau.dualscreendex.companion.battle

import kotlin.math.ceil

enum class DamageForecastConfidence { EXACT, BOUNDED }

data class InclusiveRange(val minimum: Int, val maximum: Int) {
    init {
        require(minimum >= 0) { "range minimum must not be negative" }
        require(maximum >= minimum) { "range maximum must not be below its minimum" }
    }
}

data class DecimalRange(val minimum: Double, val maximum: Double) {
    init {
        require(minimum.isFinite() && maximum.isFinite()) { "decimal range must be finite" }
        require(minimum >= 0.0) { "decimal range minimum must not be negative" }
        require(maximum >= minimum) { "decimal range maximum must not be below its minimum" }
    }
}

sealed interface DamageForecast {
    data class Available(
        val confidence: DamageForecastConfidence,
        val damage: InclusiveRange,
        val targetHpPercent: DecimalRange,
        val hitsToKnockOut: InclusiveRange,
        val accuracyPercent: Int,
        val effectivenessPercent: Int,
        val appliedConditions: List<AppliedDamageCondition> = emptyList(),
        val conditionLabels: List<String> = emptyList(),
        val uncertainty: String? = null,
    ) : DamageForecast {
        init {
            require(damage.minimum > 0 || effectivenessPercent == 0) {
                "positive effectiveness requires a positive minimum damage"
            }
            require(hitsToKnockOut.minimum > 0) { "hits to knock out must be positive" }
            require(accuracyPercent in 0..100) { "accuracy must be a percentage" }
            require(effectivenessPercent >= 0) { "effectiveness must not be negative" }
            require(conditionLabels.all(::isPlayerFacingText)) { "condition labels must be player-facing" }
            when (confidence) {
                DamageForecastConfidence.EXACT -> require(uncertainty == null) {
                    "an exact forecast cannot carry uncertainty"
                }
                DamageForecastConfidence.BOUNDED -> require(isPlayerFacingText(uncertainty)) {
                    "a bounded forecast requires a player-facing uncertainty explanation"
                }
            }
        }
    }

    data class Absent(val message: String) : DamageForecast {
        init {
            require(isPlayerFacingText(message)) { "an absent forecast requires player-facing copy" }
        }
    }
}

enum class SemanticProof { STRUCTURAL, SOURCE_VALIDATED, CONTROL_VALIDATED }

enum class BaseDamageRule { STANDARD_INTEGER }

enum class CriticalRule { NONE, LEVEL_DOUBLING, DAMAGE_MULTIPLIER }

data class DamageFormulaEvidence(
    val key: String,
    val proof: SemanticProof,
    val randomNumerators: IntRange,
    val randomDenominator: Int,
    val criticalRule: CriticalRule,
    val criticalNumerator: Int,
    val criticalDenominator: Int,
    val baseRule: BaseDamageRule = BaseDamageRule.STANDARD_INTEGER,
) {
    init {
        require(key.isNotBlank()) { "formula evidence key must not be blank" }
        require(randomNumerators.first > 0 && randomNumerators.last >= randomNumerators.first) {
            "formula random range must be positive and ordered"
        }
        require(randomDenominator >= randomNumerators.last) {
            "formula random denominator must contain the numerator range"
        }
        require(criticalNumerator > 0 && criticalDenominator > 0) {
            "critical ratio must be positive"
        }
    }
}

enum class ForecastMoveCategory { PHYSICAL, SPECIAL, STATUS }

enum class ForecastStatus { NONE, BURNED, POISONED, PARALYZED, ASLEEP, FROZEN }

data class ForecastBattler(
    val level: Int,
    val currentHp: Int,
    val maximumHp: Int,
    val attack: Int,
    val defense: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val typeIds: List<Int>,
    val status: ForecastStatus,
    val abilityId: Int?,
    val heldItemId: Int?,
    val battlerIndex: Int? = null,
) {
    init {
        require(level > 0) { "battler level must be positive" }
        require(maximumHp > 0 && currentHp in 0..maximumHp) { "battler HP is invalid" }
        require(listOf(attack, defense, specialAttack, specialDefense).all { it > 0 }) {
            "battle stats must be positive"
        }
        require(typeIds.isNotEmpty() && typeIds.all { it >= 0 }) { "battler types are invalid" }
        require(abilityId == null || abilityId > 0) { "ability ID must be positive" }
        require(heldItemId == null || heldItemId > 0) { "held item ID must be positive" }
        require(battlerIndex == null || battlerIndex >= 0) { "battler index must not be negative" }
    }
}

enum class FixedDamageRule { NONE, LEVEL, VALUE }

data class ForecastMove(
    val id: Int,
    val typeId: Int,
    val category: ForecastMoveCategory,
    val power: Int,
    val accuracyPercent: Int,
    val hitCount: InclusiveRange = InclusiveRange(1, 1),
    val fixedDamageRule: FixedDamageRule = FixedDamageRule.NONE,
    val fixedDamageValue: Int? = null,
) {
    init {
        require(id > 0) { "move ID must be positive" }
        require(typeId >= 0) { "move type ID must not be negative" }
        require(power >= 0) { "move power must not be negative" }
        require(accuracyPercent in 0..100) { "move accuracy must be a percentage" }
        require(hitCount.minimum > 0) { "move hit count must be positive" }
        require((fixedDamageRule == FixedDamageRule.VALUE) == (fixedDamageValue != null)) {
            "fixed damage value must be present exactly for value-based damage"
        }
        require(fixedDamageValue == null || fixedDamageValue > 0) { "fixed damage must be positive" }
    }
}

enum class AppliedDamageCondition { STAB, STATUS, CRITICAL, WEATHER, ABILITY, ITEM, FIELD, MULTI_HIT, FIXED_DAMAGE }

enum class DamageModifierStage { ATTACK_STAT, BASE_DAMAGE, FINAL_DAMAGE }

data class ProvenDamageModifier(
    val kind: AppliedDamageCondition,
    val numerator: Int,
    val denominator: Int,
    val proof: SemanticProof,
    val playerLabel: String,
    val stage: DamageModifierStage = DamageModifierStage.FINAL_DAMAGE,
) {
    init {
        require(numerator >= 0 && denominator > 0) { "modifier ratio is invalid" }
        require(isPlayerFacingText(playerLabel)) { "modifier label must be player-facing" }
    }
}

data class BoundedDamageModifier(
    val kind: AppliedDamageCondition,
    val minimumNumerator: Int,
    val maximumNumerator: Int,
    val denominator: Int,
    val proof: SemanticProof,
    val playerExplanation: String,
    val stage: DamageModifierStage = DamageModifierStage.FINAL_DAMAGE,
) {
    init {
        require(minimumNumerator >= 0 && maximumNumerator >= minimumNumerator && denominator > 0) {
            "bounded modifier ratio is invalid"
        }
        require(isPlayerFacingText(playerExplanation)) {
            "bounded modifier requires a player-facing explanation"
        }
    }
}

data class DamageForecastInput(
    val formula: DamageFormulaEvidence,
    val attacker: ForecastBattler,
    val target: ForecastBattler,
    val move: ForecastMove,
    val effectivenessPercent: Int,
    val provenModifiers: List<ProvenDamageModifier> = emptyList(),
    val boundedAlternatives: List<BoundedDamageModifier> = emptyList(),
    val critical: Boolean = false,
    val unboundedUnknowns: List<String> = emptyList(),
) {
    init {
        require(effectivenessPercent >= 0) { "effectiveness must not be negative" }
        require(unboundedUnknowns.all(::isPlayerFacingText)) { "unknowns must use player-facing copy" }
    }
}

internal fun hitsToKnockOut(targetHp: Int, damage: InclusiveRange): InclusiveRange {
    if (damage.maximum == 0) return InclusiveRange(Int.MAX_VALUE, Int.MAX_VALUE)
    val best = ceil(targetHp.toDouble() / damage.maximum).toInt().coerceAtLeast(1)
    val worst = if (damage.minimum == 0) Int.MAX_VALUE else ceil(targetHp.toDouble() / damage.minimum).toInt()
    return InclusiveRange(best, worst)
}

private val TECHNICAL_COPY = Regex(
    "(?:\\bTHUMB\\b|\\b(?:pointer|offset|capability|provenance|parser|compiled source)\\b|0x[0-9a-f]+|[A-Z]{2,}_[A-Z0-9_]+)",
    RegexOption.IGNORE_CASE,
)

private fun isPlayerFacingText(value: String?): Boolean =
    value?.trim()?.takeIf(String::isNotEmpty)?.let { !TECHNICAL_COPY.containsMatchIn(it) } == true
