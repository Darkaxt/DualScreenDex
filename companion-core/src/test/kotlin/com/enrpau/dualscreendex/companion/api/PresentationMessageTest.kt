package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.companion.battle.AppliedDamageCondition
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresentationMessageTest {
    @Test
    fun evolutionMessagesUseClosedCodesAndTypedArguments() {
        assertEquals(
            PresentationMessageView(code = "EVOLUTION_LEVEL", level = 16),
            PresentationMessages.evolution(generation = 1, methodId = 1, parameter = 16),
        )
        assertEquals(
            PresentationMessageView(code = "EVOLUTION_TRADE_WITH_ITEM", itemId = 42),
            PresentationMessages.evolution(generation = 3, methodId = 6, parameter = 42),
        )
        assertEquals(
            PresentationMessageView(code = "EVOLUTION_UNKNOWN", methodId = 99, parameter = 7),
            PresentationMessages.evolution(generation = 3, methodId = 99, parameter = 7),
        )
    }

    @Test
    fun generatedLabelsNeverRequireEnglishSentenceParsing() {
        assertEquals(
            PresentationMessageView(code = "MOVE_LEVEL", level = 12),
            PresentationMessages.normalizedMove(initial = false, level = 12),
        )
        assertEquals(
            PresentationMessageView(code = "SPECIMEN_BOX_SLOT", boxNumber = 3, slotNumber = 5),
            PresentationMessages.specimenLocation(boxNumber = 3, slotNumber = 5),
        )
        assertEquals(
            PresentationMessageView(
                code = "ABILITY_MECHANIC_MULTIPLIER",
                numerator = 0,
                denominator = 1,
            ),
            PresentationMessages.abilityMechanic(AbilityMechanicKind.MULTIPLIER, 0, 1),
        )
        assertEquals(
            PresentationMessageView(
                code = "ABILITY_CONDITION_MOVE_SPLIT",
                conditionValue = 1,
            ),
            PresentationMessages.abilityCondition(AbilityMechanicConditionKind.MOVE_SPLIT, 1),
        )
    }

    @Test
    fun damageMessagesAreDerivedFromClosedSemanticState() {
        assertEquals(
            AppliedDamageCondition.entries.map { "DAMAGE_CONDITION_${it.name}" },
            AppliedDamageCondition.entries.map { PresentationMessages.damageCondition(it).code },
        )
        assertEquals(
            PresentationMessageView(code = "DAMAGE_RANGE_BOUNDED"),
            PresentationMessages.damageRangeBounded(),
        )
        assertNull(PresentationMessages.ruleset("unrecognized fixture label"))
    }
}
