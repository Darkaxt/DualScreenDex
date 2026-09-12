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

    @Test
    fun progressMessagesUseClosedCodesAndTypedCounts() {
        assertEquals(
            PresentationMessageView(code = "PROGRESS_METRIC_DEX_SEEN"),
            PresentationMessages.progressMetric("seen"),
        )
        assertEquals(
            PresentationMessageView(code = "TIMELINE_CAPTURES", count = 2),
            PresentationMessages.timelineChange("captures", 2),
        )
        assertNull(PresentationMessages.progressMetric("private-diagnostic"))
        assertNull(PresentationMessages.timelineChange("private-diagnostic", 1))
    }

    @Test
    fun operationalMessagesDoNotExposeBackendProse() {
        assertEquals(
            PresentationMessageView("GUIDE_LOAD_FAILED"),
            PresentationMessages.guideLoadFailed("private failure detail"),
        )
        assertEquals(
            PresentationMessageView("CATALOG_LOADING_FIRST_PREPARATION"),
            PresentationMessages.catalogLoading("Preparing your game guide for the first time."),
        )
        assertEquals(
            PresentationMessageView("API_SERVER_BUSY"),
            PresentationMessages.apiError("SERVER_BUSY"),
        )
        assertNull(PresentationMessages.catalogLoading("private failure detail"))
        assertNull(PresentationMessages.apiError("PRIVATE_FAILURE"))
    }

    @Test
    fun challengeMessagesKeepRomNativeNamesAsTypedArguments() {
        val fixedKeys = listOf(
            "collection-first-partner",
            "collection-growing-roster",
            "party-new-form",
            "exploration-open-road",
            "exploration-curious-eye",
            "battle-seasoned",
            "progress-first-badge",
            "progress-all-badges",
            "collection-regional-record",
            "exploration-area-items",
            "battle-leader-no-items",
            "special-minigame",
        )
        assertEquals(12, fixedKeys.count { PresentationMessages.challengeTitle(it, "Viridian Forest") != null })
        assertEquals(12, fixedKeys.count { PresentationMessages.challengeDescription(it, "Viridian Forest") != null })
        assertEquals(
            PresentationMessageView(code = "CHALLENGE_EXPLORATION_AREA_ITEMS_TITLE"),
            PresentationMessages.challengeTitle("exploration-area-items", "Viridian Forest"),
        )
        assertEquals(
            PresentationMessageView(
                code = "CHALLENGE_EXPLORATION_AREA_ITEMS_DESCRIPTION",
                subject = "Viridian Forest",
            ),
            PresentationMessages.challengeDescription("exploration-area-items", "Viridian Forest"),
        )
        assertEquals(
            PresentationMessageView(code = "CHALLENGE_SPECIAL_MINIGAME_TITLE", subject = "bug catching"),
            PresentationMessages.challengeTitle("special-minigame", "bug catching"),
        )
        assertNull(PresentationMessages.challengeTitle("unknown-template", null))
    }
}
