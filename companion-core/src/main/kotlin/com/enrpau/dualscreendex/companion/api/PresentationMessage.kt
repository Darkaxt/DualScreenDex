package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.companion.battle.AppliedDamageCondition
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind

data class PresentationMessageView(
    val code: String,
    val level: Int? = null,
    val itemId: Int? = null,
    val methodId: Int? = null,
    val parameter: Int? = null,
    val slotNumber: Int? = null,
    val boxNumber: Int? = null,
    val index: Int? = null,
    val numerator: Int? = null,
    val denominator: Int? = null,
    val conditionValue: Long? = null,
) {
    init {
        val parsed = requireNotNull(PresentationMessageCode.entries.firstOrNull { it.name == code }) {
            "presentation message code is not recognized"
        }
        require(listOfNotNull(level, itemId, methodId, parameter, slotNumber, boxNumber, index).all { it >= 0 }) {
            "presentation message integer arguments must be nonnegative"
        }
        require(numerator == null || numerator >= 0) { "presentation message numerator must be nonnegative" }
        require(denominator == null || denominator > 0) { "presentation message denominator must be positive" }
        require(parsed.accepts(this)) { "presentation message arguments do not match its code" }
    }

    private fun hasNoArguments(): Boolean =
        level == null && itemId == null && methodId == null && parameter == null &&
            slotNumber == null && boxNumber == null && index == null && numerator == null &&
            denominator == null && conditionValue == null

    private fun PresentationMessageCode.accepts(message: PresentationMessageView): Boolean = when (this) {
        PresentationMessageCode.MOVE_LEVEL,
        PresentationMessageCode.EVOLUTION_LEVEL,
        -> message.level != null && message.argumentCount() == 1
        PresentationMessageCode.EVOLUTION_TRADE_WITH_ITEM,
        PresentationMessageCode.EVOLUTION_USE_ITEM,
        -> message.itemId != null && message.argumentCount() == 1
        PresentationMessageCode.EVOLUTION_UNKNOWN ->
            message.methodId != null && message.parameter != null && message.argumentCount() == 2
        PresentationMessageCode.SPECIMEN_PARTY_SLOT ->
            message.slotNumber != null && message.argumentCount() == 1
        PresentationMessageCode.SPECIMEN_BOX_SLOT ->
            message.boxNumber != null && message.slotNumber != null && message.argumentCount() == 2
        PresentationMessageCode.RULESET_EXPANDED ->
            message.index != null && message.argumentCount() == 1
        in ABILITY_MECHANIC_CODES ->
            message.numerator != null && message.denominator != null && message.argumentCount() == 2
        in ABILITY_CONDITION_CODES ->
            message.conditionValue != null && message.argumentCount() == 1
        else -> message.hasNoArguments()
    }

    private fun argumentCount(): Int = listOfNotNull(
        level,
        itemId,
        methodId,
        parameter,
        slotNumber,
        boxNumber,
        index,
        numerator,
        denominator,
        conditionValue,
    ).size
}

enum class PresentationMessageCode {
    MOVE_INITIAL,
    MOVE_LEVEL,
    RULESET_DEFAULT,
    RULESET_BASE,
    RULESET_EXPANDED,
    RULESET_OTHER,
    EVOLUTION_LEVEL,
    EVOLUTION_TRADE,
    EVOLUTION_TRADE_WITH_ITEM,
    EVOLUTION_USE_ITEM,
    EVOLUTION_HIGH_FRIENDSHIP,
    EVOLUTION_UNKNOWN,
    SPECIMEN_PARTY_SLOT,
    SPECIMEN_BOX_SLOT,
    ABILITY_MECHANIC_BEHAVIOR,
    ABILITY_MECHANIC_ACTIVATION_THRESHOLD,
    ABILITY_MECHANIC_MULTIPLIER,
    ABILITY_MECHANIC_STAT_STAGE,
    ABILITY_MECHANIC_STATUS_CURE,
    ABILITY_MECHANIC_TYPE_CHANGE,
    ABILITY_MECHANIC_AI_RATING,
    ABILITY_MECHANIC_FLAG,
    ABILITY_CONDITION_MOVE_SPLIT,
    ABILITY_CONDITION_ATTACKER_STATUS_NON_ZERO,
    ABILITY_CONDITION_SWITCH_IN,
    ABILITY_CONDITION_MOVE_POWER_NON_ZERO,
    ABILITY_CONDITION_ATTACKING_MOVE_TYPE,
    DAMAGE_CONDITION_STAB,
    DAMAGE_CONDITION_STATUS,
    DAMAGE_CONDITION_CRITICAL,
    DAMAGE_CONDITION_WEATHER,
    DAMAGE_CONDITION_ABILITY,
    DAMAGE_CONDITION_ITEM,
    DAMAGE_CONDITION_FIELD,
    DAMAGE_CONDITION_MULTI_HIT,
    DAMAGE_CONDITION_FIXED_DAMAGE,
    DAMAGE_RANGE_BOUNDED,
}

object PresentationMessages {
    fun normalizedMove(initial: Boolean, level: Int?): PresentationMessageView = when {
        initial -> plain(PresentationMessageCode.MOVE_INITIAL)
        level != null -> PresentationMessageView(PresentationMessageCode.MOVE_LEVEL.name, level = level)
        else -> error("normalized move message requires initial or level authority")
    }

    fun ruleset(label: String): PresentationMessageView? = when {
        label == "Default" -> plain(PresentationMessageCode.RULESET_DEFAULT)
        label == "Base" -> plain(PresentationMessageCode.RULESET_BASE)
        label.startsWith("Expanded ") -> label.removePrefix("Expanded ").toIntOrNull()?.takeIf { it >= 0 }?.let {
            PresentationMessageView(PresentationMessageCode.RULESET_EXPANDED.name, index = it)
        }
        else -> null
    }

    fun evolution(generation: Int, methodId: Int, parameter: Int): PresentationMessageView = when {
        generation == 3 && methodId == 4 || generation <= 2 && methodId == 1 ->
            PresentationMessageView(PresentationMessageCode.EVOLUTION_LEVEL.name, level = parameter)
        generation == 3 && methodId == 5 || generation <= 2 && methodId == 3 ->
            plain(PresentationMessageCode.EVOLUTION_TRADE)
        generation == 3 && methodId == 6 ->
            PresentationMessageView(PresentationMessageCode.EVOLUTION_TRADE_WITH_ITEM.name, itemId = parameter)
        generation == 3 && methodId == 7 || generation <= 2 && methodId == 2 ->
            PresentationMessageView(PresentationMessageCode.EVOLUTION_USE_ITEM.name, itemId = parameter)
        generation == 3 && methodId in 1..3 -> plain(PresentationMessageCode.EVOLUTION_HIGH_FRIENDSHIP)
        else -> PresentationMessageView(
            PresentationMessageCode.EVOLUTION_UNKNOWN.name,
            methodId = methodId,
            parameter = parameter,
        )
    }

    fun specimenLocation(boxNumber: Int?, slotNumber: Int): PresentationMessageView = if (boxNumber == null) {
        PresentationMessageView(PresentationMessageCode.SPECIMEN_PARTY_SLOT.name, slotNumber = slotNumber)
    } else {
        PresentationMessageView(
            PresentationMessageCode.SPECIMEN_BOX_SLOT.name,
            boxNumber = boxNumber,
            slotNumber = slotNumber,
        )
    }

    fun abilityMechanic(
        kind: AbilityMechanicKind,
        numerator: Int,
        denominator: Int,
    ) = PresentationMessageView(
        code = "ABILITY_MECHANIC_${kind.name}",
        numerator = numerator,
        denominator = denominator,
    )

    fun abilityCondition(kind: AbilityMechanicConditionKind, value: Long) = PresentationMessageView(
        code = "ABILITY_CONDITION_${kind.name}",
        conditionValue = value,
    )

    fun damageCondition(condition: AppliedDamageCondition) =
        plain(PresentationMessageCode.valueOf("DAMAGE_CONDITION_${condition.name}"))

    fun damageRangeBounded() = plain(PresentationMessageCode.DAMAGE_RANGE_BOUNDED)

    fun otherRuleset() = plain(PresentationMessageCode.RULESET_OTHER)

    private fun plain(code: PresentationMessageCode) = PresentationMessageView(code.name)
}

private val ABILITY_MECHANIC_CODES = PresentationMessageCode.entries
    .filter { it.name.startsWith("ABILITY_MECHANIC_") }
    .toSet()
private val ABILITY_CONDITION_CODES = PresentationMessageCode.entries
    .filter { it.name.startsWith("ABILITY_CONDITION_") }
    .toSet()
