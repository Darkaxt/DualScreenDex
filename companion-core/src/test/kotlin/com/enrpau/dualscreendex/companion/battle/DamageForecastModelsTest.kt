package com.enrpau.dualscreendex.companion.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageForecastModelsTest {
    @Test
    fun `exact forecast carries one proven damage interval and player values`() {
        val forecast = DamageForecast.Available(
            confidence = DamageForecastConfidence.EXACT,
            damage = InclusiveRange(42, 50),
            targetHpPercent = DecimalRange(42.0, 50.0),
            hitsToKnockOut = InclusiveRange(2, 3),
            accuracyPercent = 100,
            effectivenessPercent = 200,
            appliedConditions = listOf(AppliedDamageCondition.STAB),
        )

        assertEquals(42, forecast.damage.minimum)
        assertEquals(50, forecast.damage.maximum)
        assertEquals(listOf(AppliedDamageCondition.STAB), forecast.appliedConditions)
        assertEquals(null, forecast.uncertainty)
    }

    @Test
    fun `bounded forecast requires a player-facing uncertainty explanation`() {
        assertThrows(IllegalArgumentException::class.java) {
            DamageForecast.Available(
                confidence = DamageForecastConfidence.BOUNDED,
                damage = InclusiveRange(20, 60),
                targetHpPercent = DecimalRange(20.0, 60.0),
                hitsToKnockOut = InclusiveRange(2, 5),
                accuracyPercent = 90,
                effectivenessPercent = 100,
            )
        }

        val forecast = DamageForecast.Available(
            confidence = DamageForecastConfidence.BOUNDED,
            damage = InclusiveRange(20, 60),
            targetHpPercent = DecimalRange(20.0, 60.0),
            hitsToKnockOut = InclusiveRange(2, 5),
            accuracyPercent = 90,
            effectivenessPercent = 100,
            uncertainty = "The target may have a damage-reducing ability.",
        )

        assertEquals(DamageForecastConfidence.BOUNDED, forecast.confidence)
    }

    @Test
    fun `absent forecast carries concise player-facing copy rather than a capability code`() {
        val forecast: DamageForecast = DamageForecast.Absent("Not enough battle information yet.")

        assertTrue(forecast is DamageForecast.Absent)
        assertEquals("Not enough battle information yet.", (forecast as DamageForecast.Absent).message)
        assertThrows(IllegalArgumentException::class.java) { DamageForecast.Absent("THUMB_NOT_FOUND") }
    }

    @Test
    fun `formula and modifier inputs require explicit semantic proof`() {
        val formula = DamageFormulaEvidence(
            key = "portable-standard-base",
            proof = SemanticProof.STRUCTURAL,
            randomNumerators = 85..100,
            randomDenominator = 100,
            criticalRule = CriticalRule.DAMAGE_MULTIPLIER,
            criticalNumerator = 2,
            criticalDenominator = 1,
        )
        val modifier = ProvenDamageModifier(
            kind = AppliedDamageCondition.WEATHER,
            numerator = 3,
            denominator = 2,
            proof = SemanticProof.SOURCE_VALIDATED,
            playerLabel = "Rain",
        )

        assertEquals(SemanticProof.STRUCTURAL, formula.proof)
        assertEquals("Rain", modifier.playerLabel)
        assertThrows(IllegalArgumentException::class.java) {
            formula.copy(key = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            modifier.copy(playerLabel = "ability=0x41 pointer=0x02024000")
        }
    }

    @Test
    fun `forecast input separates proven modifiers from finite bounded alternatives`() {
        val input = DamageForecastInput(
            formula = formula(),
            attacker = battler(attack = 80, defense = 60, currentHp = 90, maximumHp = 100),
            target = battler(attack = 65, defense = 70, currentHp = 100, maximumHp = 100),
            move = ForecastMove(
                id = 1,
                typeId = 10,
                category = ForecastMoveCategory.PHYSICAL,
                power = 40,
                accuracyPercent = 100,
            ),
            effectivenessPercent = 100,
            provenModifiers = listOf(
                ProvenDamageModifier(
                    AppliedDamageCondition.STAB,
                    3,
                    2,
                    SemanticProof.STRUCTURAL,
                    "Same-type attack bonus",
                ),
            ),
            boundedAlternatives = listOf(
                BoundedDamageModifier(
                    kind = AppliedDamageCondition.ABILITY,
                    minimumNumerator = 1,
                    maximumNumerator = 2,
                    denominator = 1,
                    proof = SemanticProof.CONTROL_VALIDATED,
                    playerExplanation = "The target may have a damage-reducing ability.",
                ),
            ),
        )

        assertEquals(1, input.provenModifiers.size)
        assertEquals(1, input.boundedAlternatives.size)
    }

    private fun formula() = DamageFormulaEvidence(
        key = "portable-standard-base",
        proof = SemanticProof.STRUCTURAL,
        randomNumerators = 85..100,
        randomDenominator = 100,
        criticalRule = CriticalRule.DAMAGE_MULTIPLIER,
        criticalNumerator = 2,
        criticalDenominator = 1,
    )

    private fun battler(
        attack: Int,
        defense: Int,
        currentHp: Int,
        maximumHp: Int,
    ) = ForecastBattler(
        level = 20,
        currentHp = currentHp,
        maximumHp = maximumHp,
        attack = attack,
        defense = defense,
        specialAttack = attack,
        specialDefense = defense,
        typeIds = listOf(10),
        status = ForecastStatus.NONE,
        abilityId = null,
        heldItemId = null,
    )
}
