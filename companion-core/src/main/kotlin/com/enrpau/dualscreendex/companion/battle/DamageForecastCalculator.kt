package com.enrpau.dualscreendex.companion.battle

import kotlin.math.round

object DamageForecastCalculator {
    fun calculate(input: DamageForecastInput?): DamageForecast {
        input ?: return DamageForecast.Absent("Not enough battle information yet.")
        if (input.unboundedUnknowns.isNotEmpty()) {
            return DamageForecast.Absent("Not enough battle information yet.")
        }
        if (input.move.category == ForecastMoveCategory.STATUS) {
            return DamageForecast.Absent("This move does not have a predictable damage range.")
        }
        if (input.move.fixedDamageRule == FixedDamageRule.NONE && input.move.power <= 0) {
            return DamageForecast.Absent("This move does not have a predictable damage range.")
        }
        if (input.critical && input.formula.criticalRule == CriticalRule.NONE) {
            return DamageForecast.Absent("Not enough battle information yet.")
        }

        val damage = when (input.move.fixedDamageRule) {
            FixedDamageRule.NONE -> calculatedRange(input)
            FixedDamageRule.LEVEL -> fixedRange(input, input.attacker.level)
            FixedDamageRule.VALUE -> fixedRange(input, requireNotNull(input.move.fixedDamageValue))
        }
        val conditions = buildList {
            addAll(input.provenModifiers.map(ProvenDamageModifier::kind))
            if (input.critical) add(AppliedDamageCondition.CRITICAL)
            if (input.move.hitCount.maximum > 1) add(AppliedDamageCondition.MULTI_HIT)
            if (input.move.fixedDamageRule != FixedDamageRule.NONE) add(AppliedDamageCondition.FIXED_DAMAGE)
        }.distinct()
        val labels = input.provenModifiers.map(ProvenDamageModifier::playerLabel).distinct()
        val uncertainty = input.boundedAlternatives
            .map(BoundedDamageModifier::playerExplanation)
            .distinct()
            .joinToString(" ")
            .ifBlank { null }

        return DamageForecast.Available(
            confidence = if (input.boundedAlternatives.isEmpty()) {
                DamageForecastConfidence.EXACT
            } else {
                DamageForecastConfidence.BOUNDED
            },
            damage = damage,
            targetHpPercent = DecimalRange(
                percent(damage.minimum, input.target.currentHp),
                percent(damage.maximum, input.target.currentHp),
            ),
            hitsToKnockOut = hitsToKnockOut(input.target.currentHp, damage),
            accuracyPercent = input.move.accuracyPercent,
            effectivenessPercent = input.effectivenessPercent,
            appliedConditions = conditions,
            conditionLabels = labels,
            uncertainty = uncertainty,
        )
    }

    private fun calculatedRange(input: DamageForecastInput): InclusiveRange {
        if (input.move.power <= 0) return InclusiveRange(0, 0)
        if (input.effectivenessPercent == 0) return InclusiveRange(0, 0)
        val minimum = calculatedDamage(input, lowerBound = true)
        val maximum = calculatedDamage(input, lowerBound = false)
        return InclusiveRange(minimum, maximum)
    }

    private fun calculatedDamage(input: DamageForecastInput, lowerBound: Boolean): Int {
        val attackBase = when (input.move.category) {
            ForecastMoveCategory.PHYSICAL -> input.attacker.attack
            ForecastMoveCategory.SPECIAL -> input.attacker.specialAttack
            ForecastMoveCategory.STATUS -> return 0
        }
        val defense = when (input.move.category) {
            ForecastMoveCategory.PHYSICAL -> input.target.defense
            ForecastMoveCategory.SPECIAL -> input.target.specialDefense
            ForecastMoveCategory.STATUS -> return 0
        }
        val attack = applyStage(input, DamageModifierStage.ATTACK_STAT, attackBase, lowerBound)
            .coerceAtLeast(1)
        val level = when {
            input.critical && input.formula.criticalRule == CriticalRule.LEVEL_DOUBLING -> applyRatio(
                input.attacker.level,
                input.formula.criticalNumerator,
                input.formula.criticalDenominator,
            )
            else -> input.attacker.level
        }

        var damage = when (input.formula.baseRule) {
            BaseDamageRule.STANDARD_INTEGER -> standardBase(level, input.move.power, attack, defense)
        }
        damage = applyStage(input, DamageModifierStage.BASE_DAMAGE, damage, lowerBound)
        if (input.critical && input.formula.criticalRule == CriticalRule.DAMAGE_MULTIPLIER) {
            damage = applyRatio(
                damage,
                input.formula.criticalNumerator,
                input.formula.criticalDenominator,
            )
        }
        damage = applyStage(input, DamageModifierStage.FINAL_DAMAGE, damage, lowerBound)
        damage = applyRatio(damage, input.effectivenessPercent, 100)
        val randomNumerator = if (lowerBound) {
            input.formula.randomNumerators.first
        } else {
            input.formula.randomNumerators.last
        }
        damage = applyRatio(damage, randomNumerator, input.formula.randomDenominator)
        if (input.effectivenessPercent > 0) damage = damage.coerceAtLeast(1)
        val hits = if (lowerBound) input.move.hitCount.minimum else input.move.hitCount.maximum
        return multiplySaturated(damage, hits)
    }

    private fun applyStage(
        input: DamageForecastInput,
        stage: DamageModifierStage,
        initial: Int,
        lowerBound: Boolean,
    ): Int {
        var value = initial
        input.provenModifiers.filter { it.stage == stage }.forEach { modifier ->
            value = applyRatio(value, modifier.numerator, modifier.denominator)
        }
        input.boundedAlternatives.filter { it.stage == stage }.forEach { modifier ->
            value = applyRatio(
                value,
                if (lowerBound) modifier.minimumNumerator else modifier.maximumNumerator,
                modifier.denominator,
            )
        }
        return value
    }

    private fun fixedRange(input: DamageForecastInput, perHit: Int): InclusiveRange {
        if (input.effectivenessPercent == 0) return InclusiveRange(0, 0)
        return InclusiveRange(
            multiplySaturated(perHit, input.move.hitCount.minimum),
            multiplySaturated(perHit, input.move.hitCount.maximum),
        )
    }

    private fun standardBase(level: Int, power: Int, attack: Int, defense: Int): Int {
        val levelFactor = (2L * level / 5L) + 2L
        val scaled = levelFactor * power.toLong() * attack.toLong() / defense.toLong()
        return ((scaled / 50L) + 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun applyRatio(value: Int, numerator: Int, denominator: Int): Int =
        (value.toLong() * numerator.toLong() / denominator.toLong())
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

    private fun multiplySaturated(value: Int, count: Int): Int =
        (value.toLong() * count.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun percent(damage: Int, currentHp: Int): Double {
        if (currentHp <= 0) return 0.0
        return round((damage.toDouble() * 100.0 / currentHp.toDouble()) * 100.0) / 100.0
    }
}
