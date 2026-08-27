package com.enrpau.dualscreendex.companion.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageForecastCalculatorTest {
    @Test
    fun `official first generation random range and level critical rule are integer accurate`() {
        val ordinary = available(
            DamageForecastCalculator.calculate(input(formula = gen1Formula())),
        )
        val critical = available(
            DamageForecastCalculator.calculate(input(formula = gen1Formula(), critical = true)),
        )

        assertEquals(InclusiveRange(58, 69), ordinary.damage)
        assertEquals(InclusiveRange(109, 129), critical.damage)
        assertTrue(AppliedDamageCondition.CRITICAL in critical.appliedConditions)
    }

    @Test
    fun `official second generation applies proven burn before the base formula`() {
        val forecast = available(
            DamageForecastCalculator.calculate(
                input(
                    formula = gen2Formula(),
                    modifiers = listOf(
                        modifier(AppliedDamageCondition.STAB, 3, 2, "Same-type attack bonus"),
                        modifier(
                            AppliedDamageCondition.STATUS,
                            1,
                            2,
                            "Burn",
                            DamageModifierStage.ATTACK_STAT,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(InclusiveRange(30, 36), forecast.damage)
        assertEquals(listOf(AppliedDamageCondition.STAB, AppliedDamageCondition.STATUS), forecast.appliedConditions)
    }

    @Test
    fun `official third generation applies STAB effectiveness and its native random range`() {
        val forecast = available(
            DamageForecastCalculator.calculate(
                input(
                    formula = gen3Formula(),
                    effectiveness = 200,
                ),
            ),
        )

        assertEquals(InclusiveRange(117, 138), forecast.damage)
        assertEquals(DecimalRange(117.0, 138.0), forecast.targetHpPercent)
        assertEquals(InclusiveRange(1, 1), forecast.hitsToKnockOut)
        assertEquals(200, forecast.effectivenessPercent)
    }

    @Test
    fun `weather ability and item modifiers are applied only when explicitly provided`() {
        val forecast = available(
            DamageForecastCalculator.calculate(
                input(
                    formula = gen3Formula(),
                    modifiers = listOf(
                        modifier(AppliedDamageCondition.STAB, 3, 2, "Same-type attack bonus"),
                        modifier(AppliedDamageCondition.WEATHER, 3, 2, "Rain"),
                        modifier(AppliedDamageCondition.ABILITY, 2, 1, "Power-boosting ability"),
                        modifier(AppliedDamageCondition.ITEM, 3, 2, "Held item"),
                    ),
                ),
            ),
        )

        assertEquals(InclusiveRange(262, 309), forecast.damage)
        assertEquals(
            listOf(
                AppliedDamageCondition.STAB,
                AppliedDamageCondition.WEATHER,
                AppliedDamageCondition.ABILITY,
                AppliedDamageCondition.ITEM,
            ),
            forecast.appliedConditions,
        )
    }

    @Test
    fun `finite hidden ability alternatives produce a bounded enclosing range`() {
        val forecast = available(
            DamageForecastCalculator.calculate(
                input(
                    formula = gen3Formula(),
                    alternatives = listOf(
                        BoundedDamageModifier(
                            kind = AppliedDamageCondition.ABILITY,
                            minimumNumerator = 1,
                            maximumNumerator = 2,
                            denominator = 1,
                            proof = SemanticProof.CONTROL_VALIDATED,
                            playerExplanation = "The target may have a damage-reducing ability.",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(DamageForecastConfidence.BOUNDED, forecast.confidence)
        assertEquals(InclusiveRange(58, 138), forecast.damage)
        assertEquals("The target may have a damage-reducing ability.", forecast.uncertainty)
    }

    @Test
    fun `multi-hit and fixed-damage rules produce complete per-use ranges`() {
        val multiHit = available(
            DamageForecastCalculator.calculate(
                input(
                    formula = gen3Formula(),
                    move = move(power = 10, hitCount = InclusiveRange(2, 5)),
                ),
            ),
        )
        val fixed = available(
            DamageForecastCalculator.calculate(
                input(
                    formula = gen3Formula(),
                    move = move(
                        power = 0,
                        fixedDamageRule = FixedDamageRule.LEVEL,
                    ),
                    attacker = battler(level = 30),
                ),
            ),
        )

        assertEquals(InclusiveRange(14, 45), multiHit.damage)
        assertTrue(AppliedDamageCondition.MULTI_HIT in multiHit.appliedConditions)
        assertEquals(InclusiveRange(30, 30), fixed.damage)
        assertTrue(AppliedDamageCondition.FIXED_DAMAGE in fixed.appliedConditions)
    }

    @Test
    fun `immunity is exact while missing or unbounded semantics are absent`() {
        val immune = available(
            DamageForecastCalculator.calculate(input(formula = gen3Formula(), effectiveness = 0)),
        )
        val missingFormula = DamageForecastCalculator.calculate(null)
        val unknownMechanic = DamageForecastCalculator.calculate(
            input(
                formula = gen3Formula(),
                unboundedUnknowns = listOf("The move has an unresolved damage effect."),
            ),
        )

        assertEquals(InclusiveRange(0, 0), immune.damage)
        assertTrue(missingFormula is DamageForecast.Absent)
        assertTrue(unknownMechanic is DamageForecast.Absent)
    }

    @Test
    fun `status moves do not invent a damage calculation`() {
        val result = DamageForecastCalculator.calculate(
            input(
                formula = gen3Formula(),
                move = move(power = 0, category = ForecastMoveCategory.STATUS),
            ),
        )

        assertTrue(result is DamageForecast.Absent)
    }

    @Test
    fun `zero power without a proven fixed damage rule fails closed`() {
        val result = DamageForecastCalculator.calculate(
            input(
                formula = gen3Formula(),
                move = move(power = 0),
            ),
        )

        assertTrue(result is DamageForecast.Absent)
    }

    private fun input(
        formula: DamageFormulaEvidence,
        attacker: ForecastBattler = battler(),
        target: ForecastBattler = battler(),
        move: ForecastMove = move(),
        effectiveness: Int = 100,
        modifiers: List<ProvenDamageModifier> = listOf(
            modifier(AppliedDamageCondition.STAB, 3, 2, "Same-type attack bonus"),
        ),
        alternatives: List<BoundedDamageModifier> = emptyList(),
        critical: Boolean = false,
        unboundedUnknowns: List<String> = emptyList(),
    ) = DamageForecastInput(
        formula = formula,
        attacker = attacker,
        target = target,
        move = move,
        effectivenessPercent = effectiveness,
        provenModifiers = modifiers,
        boundedAlternatives = alternatives,
        critical = critical,
        unboundedUnknowns = unboundedUnknowns,
    )

    private fun battler(level: Int = 50) = ForecastBattler(
        level = level,
        currentHp = 100,
        maximumHp = 100,
        attack = 100,
        defense = 100,
        specialAttack = 100,
        specialDefense = 100,
        typeIds = listOf(10),
        status = ForecastStatus.NONE,
        abilityId = null,
        heldItemId = null,
    )

    private fun move(
        power: Int = 100,
        category: ForecastMoveCategory = ForecastMoveCategory.PHYSICAL,
        hitCount: InclusiveRange = InclusiveRange(1, 1),
        fixedDamageRule: FixedDamageRule = FixedDamageRule.NONE,
    ) = ForecastMove(
        id = 1,
        typeId = 10,
        category = category,
        power = power,
        accuracyPercent = 100,
        hitCount = hitCount,
        fixedDamageRule = fixedDamageRule,
    )

    private fun modifier(
        kind: AppliedDamageCondition,
        numerator: Int,
        denominator: Int,
        label: String,
        stage: DamageModifierStage = DamageModifierStage.FINAL_DAMAGE,
    ) = ProvenDamageModifier(kind, numerator, denominator, SemanticProof.STRUCTURAL, label, stage)

    private fun gen1Formula() = formula("official-first-generation", 217..255, 255, CriticalRule.LEVEL_DOUBLING)
    private fun gen2Formula() = formula("official-second-generation", 217..255, 255, CriticalRule.DAMAGE_MULTIPLIER)
    private fun gen3Formula() = formula("official-third-generation", 85..100, 100, CriticalRule.DAMAGE_MULTIPLIER)

    private fun formula(
        key: String,
        random: IntRange,
        denominator: Int,
        criticalRule: CriticalRule,
    ) = DamageFormulaEvidence(
        key = key,
        proof = SemanticProof.CONTROL_VALIDATED,
        randomNumerators = random,
        randomDenominator = denominator,
        criticalRule = criticalRule,
        criticalNumerator = 2,
        criticalDenominator = 1,
    )

    private fun available(forecast: DamageForecast): DamageForecast.Available {
        assertTrue(forecast is DamageForecast.Available)
        return forecast as DamageForecast.Available
    }
}
