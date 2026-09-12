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
    val count: Long? = null,
    val subject: String? = null,
) {
    init {
        val parsed = requireNotNull(PresentationMessageCode.entries.firstOrNull { it.name == code }) {
            "presentation message code is not recognized"
        }
        require(listOfNotNull(level, itemId, methodId, parameter, slotNumber, boxNumber, index).all { it >= 0 }) {
            "presentation message integer arguments must be nonnegative"
        }
        require(numerator == null || numerator >= 0 || parsed in SIGNED_NUMERATOR_CODES) {
            "presentation message numerator must be nonnegative unless its code accepts signed values"
        }
        require(denominator == null || denominator > 0) { "presentation message denominator must be positive" }
        require(count == null || count > 0) { "presentation message count must be positive" }
        require(subject == null || subject.isNotBlank() && subject.length <= MAX_SUBJECT_LENGTH && subject.none(Char::isISOControl)) {
            "presentation message subject must be bounded display text"
        }
        require(parsed.accepts(this)) { "presentation message arguments do not match its code" }
    }

    private fun hasNoArguments(): Boolean = argumentCount() == 0

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
        in ABILITY_MECHANIC_ARGUMENT_CODES,
        in ABILITY_VALUE_RATIO_CODES,
        -> message.numerator != null && message.denominator != null && message.argumentCount() == 2
        PresentationMessageCode.ABILITY_VALUE_AI_RATING ->
            message.conditionValue != null && message.argumentCount() == 1
        in ABILITY_CONDITION_CODES ->
            message.conditionValue != null && message.argumentCount() == 1
        in TIMELINE_CODES -> message.count != null && message.argumentCount() == 1
        in CHALLENGE_SUBJECT_CODES -> message.subject != null && message.argumentCount() == 1
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
        count,
        subject,
    ).size

    private companion object {
        const val MAX_SUBJECT_LENGTH = 128
    }
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
    ABILITY_MECHANIC_ATTACK,
    ABILITY_MECHANIC_MOVE_POWER,
    ABILITY_MECHANIC_INCOMING_DAMAGE,
    ABILITY_MECHANIC_OPPONENT_ATTACK,
    ABILITY_MECHANIC_NONVOLATILE_STATUS,
    ABILITY_MECHANIC_FLAG_CANNOT_BE_COPIED,
    ABILITY_MECHANIC_FLAG_CANNOT_BE_SWAPPED,
    ABILITY_MECHANIC_FLAG_CANNOT_BE_TRACED,
    ABILITY_MECHANIC_FLAG_CANNOT_BE_SUPPRESSED,
    ABILITY_MECHANIC_FLAG_CANNOT_BE_OVERWRITTEN,
    ABILITY_MECHANIC_FLAG_BREAKABLE,
    ABILITY_MECHANIC_FLAG_FAILS_ON_IMPOSTER,
    ABILITY_VALUE_HP_THRESHOLD,
    ABILITY_VALUE_ATTACK_MULTIPLIER,
    ABILITY_VALUE_GRASS_MOVE_POWER_MULTIPLIER,
    ABILITY_VALUE_FIRE_MOVE_POWER_MULTIPLIER,
    ABILITY_VALUE_WATER_MOVE_POWER_MULTIPLIER,
    ABILITY_VALUE_BUG_MOVE_POWER_MULTIPLIER,
    ABILITY_VALUE_INCOMING_DAMAGE_MULTIPLIER,
    ABILITY_VALUE_STAT_STAGE,
    ABILITY_VALUE_STATUS_CURE_CHANCE,
    ABILITY_VALUE_NORMAL_TO_FAIRY,
    ABILITY_VALUE_AI_RATING,
    ABILITY_VALUE_ENABLED,
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
    GUIDE_LOAD_FAILED,
    CATALOG_LOADING_FIRST_PREPARATION,
    CATALOG_LOADING_VERSION_REFRESH,
    CATALOG_LOADING_CACHE_RECOVERY,
    RETROARCH_GAME_OPEN_FAILED,
    API_SERVER_BUSY,
    API_METHOD_NOT_ALLOWED,
    API_REQUEST_TIMEOUT,
    API_GUIDE_LOAD_FAILED,
    API_INVALID_REQUEST,
    API_INTERNAL_ERROR,
    API_NOT_FOUND,
    API_MAP_UNAVAILABLE,
    PROGRESS_METRIC_PLAY_TIME,
    PROGRESS_METRIC_BADGES,
    PROGRESS_METRIC_DEX_SEEN,
    PROGRESS_METRIC_DEX_CAUGHT,
    PROGRESS_METRIC_MONEY,
    PROGRESS_METRIC_BATTLES,
    PROGRESS_METRIC_WILD_ENCOUNTERS,
    PROGRESS_METRIC_TRAINER_BATTLES,
    PROGRESS_METRIC_CAPTURES,
    PROGRESS_METRIC_EVOLUTIONS,
    PROGRESS_METRIC_AREAS_VISITED,
    PROGRESS_METRIC_POINTS_DISCOVERED,
    PROGRESS_METRIC_PARTY_CHANGES,
    PROGRESS_METRIC_SAVES_OBSERVED,
    PROGRESS_METRIC_CHALLENGES_COMPLETED,
    TIMELINE_BATTLES,
    TIMELINE_WILD_ENCOUNTERS,
    TIMELINE_TRAINER_BATTLES,
    TIMELINE_CAPTURES,
    TIMELINE_EVOLUTIONS,
    TIMELINE_AREAS_VISITED,
    TIMELINE_POINTS_DISCOVERED,
    TIMELINE_PARTY_CHANGES,
    TIMELINE_SAVES_OBSERVED,
    TIMELINE_CHALLENGES_COMPLETED,
    CHALLENGE_COLLECTION_FIRST_PARTNER_TITLE,
    CHALLENGE_COLLECTION_FIRST_PARTNER_DESCRIPTION,
    CHALLENGE_COLLECTION_GROWING_ROSTER_TITLE,
    CHALLENGE_COLLECTION_GROWING_ROSTER_DESCRIPTION,
    CHALLENGE_PARTY_NEW_FORM_TITLE,
    CHALLENGE_PARTY_NEW_FORM_DESCRIPTION,
    CHALLENGE_EXPLORATION_OPEN_ROAD_TITLE,
    CHALLENGE_EXPLORATION_OPEN_ROAD_DESCRIPTION,
    CHALLENGE_EXPLORATION_CURIOUS_EYE_TITLE,
    CHALLENGE_EXPLORATION_CURIOUS_EYE_DESCRIPTION,
    CHALLENGE_BATTLE_SEASONED_TITLE,
    CHALLENGE_BATTLE_SEASONED_DESCRIPTION,
    CHALLENGE_PROGRESS_FIRST_BADGE_TITLE,
    CHALLENGE_PROGRESS_FIRST_BADGE_DESCRIPTION,
    CHALLENGE_PROGRESS_ALL_BADGES_TITLE,
    CHALLENGE_PROGRESS_ALL_BADGES_DESCRIPTION,
    CHALLENGE_COLLECTION_REGIONAL_RECORD_TITLE,
    CHALLENGE_COLLECTION_REGIONAL_RECORD_DESCRIPTION,
    CHALLENGE_EXPLORATION_AREA_ITEMS_TITLE,
    CHALLENGE_EXPLORATION_AREA_ITEMS_DESCRIPTION,
    CHALLENGE_BATTLE_LEADER_NO_ITEMS_TITLE,
    CHALLENGE_BATTLE_LEADER_NO_ITEMS_DESCRIPTION,
    CHALLENGE_SPECIAL_MINIGAME_TITLE,
    CHALLENGE_SPECIAL_MINIGAME_DESCRIPTION,
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

    fun abilityMechanicLabel(
        kind: AbilityMechanicKind,
        label: String,
        numerator: Int,
        denominator: Int,
    ): PresentationMessageView? {
        val code = when (kind) {
            AbilityMechanicKind.BEHAVIOR -> return null
            AbilityMechanicKind.ACTIVATION_THRESHOLD -> if (label == "Activation") {
                PresentationMessageCode.ABILITY_MECHANIC_ACTIVATION_THRESHOLD
            } else {
                return null
            }
            AbilityMechanicKind.MULTIPLIER -> when (label) {
                "Attack" -> PresentationMessageCode.ABILITY_MECHANIC_ATTACK
                "Power" -> PresentationMessageCode.ABILITY_MECHANIC_MOVE_POWER
                "Incoming damage" -> PresentationMessageCode.ABILITY_MECHANIC_INCOMING_DAMAGE
                else -> return null
            }
            AbilityMechanicKind.STAT_STAGE -> when (label) {
                "Opponents' Attack" -> PresentationMessageCode.ABILITY_MECHANIC_OPPONENT_ATTACK
                else -> return null
            }
            AbilityMechanicKind.STATUS_CURE -> when (label) {
                "Nonvolatile status" -> PresentationMessageCode.ABILITY_MECHANIC_NONVOLATILE_STATUS
                else -> return null
            }
            AbilityMechanicKind.TYPE_CHANGE -> if (label == "Move type") {
                PresentationMessageCode.ABILITY_MECHANIC_TYPE_CHANGE
            } else {
                return null
            }
            AbilityMechanicKind.AI_RATING -> if (label == "AI rating") {
                PresentationMessageCode.ABILITY_MECHANIC_AI_RATING
            } else {
                return null
            }
            AbilityMechanicKind.FLAG -> FLAG_CODES[label] ?: return null
        }
        return if (kind == AbilityMechanicKind.FLAG) {
            plain(code)
        } else {
            PresentationMessageView(code.name, numerator = numerator, denominator = denominator)
        }
    }

    fun abilityMechanicValue(
        kind: AbilityMechanicKind,
        label: String,
        value: String,
        numerator: Int,
        denominator: Int,
    ): PresentationMessageView? = when (kind) {
        AbilityMechanicKind.BEHAVIOR -> null
        AbilityMechanicKind.ACTIVATION_THRESHOLD ->
            ratioMessage(PresentationMessageCode.ABILITY_VALUE_HP_THRESHOLD, numerator, denominator)
        AbilityMechanicKind.MULTIPLIER -> when (label) {
            "Attack" -> ratioMessage(PresentationMessageCode.ABILITY_VALUE_ATTACK_MULTIPLIER, numerator, denominator)
            "Power" -> POWER_VALUE_CODES[value]?.let {
                ratioMessage(it, numerator, denominator)
            }
            "Incoming damage" ->
                ratioMessage(PresentationMessageCode.ABILITY_VALUE_INCOMING_DAMAGE_MULTIPLIER, numerator, denominator)
            else -> null
        }
        AbilityMechanicKind.STAT_STAGE ->
            ratioMessage(PresentationMessageCode.ABILITY_VALUE_STAT_STAGE, numerator, denominator)
        AbilityMechanicKind.STATUS_CURE ->
            ratioMessage(PresentationMessageCode.ABILITY_VALUE_STATUS_CURE_CHANCE, numerator, denominator)
        AbilityMechanicKind.TYPE_CHANGE -> if (value == "Normal → Fairy") {
            plain(PresentationMessageCode.ABILITY_VALUE_NORMAL_TO_FAIRY)
        } else {
            null
        }
        AbilityMechanicKind.AI_RATING -> PresentationMessageView(
            PresentationMessageCode.ABILITY_VALUE_AI_RATING.name,
            conditionValue = numerator.toLong(),
        )
        AbilityMechanicKind.FLAG -> if (value == "Yes" && label in FLAG_CODES) {
            plain(PresentationMessageCode.ABILITY_VALUE_ENABLED)
        } else {
            null
        }
    }

    fun abilityCondition(kind: AbilityMechanicConditionKind, value: Long) = PresentationMessageView(
        code = "ABILITY_CONDITION_${kind.name}",
        conditionValue = value,
    )

    fun damageCondition(condition: AppliedDamageCondition) =
        plain(PresentationMessageCode.valueOf("DAMAGE_CONDITION_${condition.name}"))

    fun damageRangeBounded() = plain(PresentationMessageCode.DAMAGE_RANGE_BOUNDED)

    fun otherRuleset() = plain(PresentationMessageCode.RULESET_OTHER)

    fun guideLoadFailed(@Suppress("UNUSED_PARAMETER") diagnostic: String? = null) =
        plain(PresentationMessageCode.GUIDE_LOAD_FAILED)

    fun catalogLoading(diagnostic: String?): PresentationMessageView? = when (diagnostic) {
        "Preparing your game guide for the first time." ->
            plain(PresentationMessageCode.CATALOG_LOADING_FIRST_PREPARATION)
        "Saved guide data needs to be refreshed for this version." ->
            plain(PresentationMessageCode.CATALOG_LOADING_VERSION_REFRESH)
        "Saved guide data could not be reopened, so it is being prepared again." ->
            plain(PresentationMessageCode.CATALOG_LOADING_CACHE_RECOVERY)
        else -> null
    }

    fun retroArchGameOpenFailed() = plain(PresentationMessageCode.RETROARCH_GAME_OPEN_FAILED)

    fun apiError(code: String): PresentationMessageView? = API_ERROR_CODES[code]?.let(::plain)

    fun progressMetric(key: String): PresentationMessageView? = PROGRESS_METRIC_CODES[key]?.let(::plain)

    fun timelineChange(key: String, count: Long): PresentationMessageView? =
        TIMELINE_CODES_BY_KEY[key]?.let { PresentationMessageView(it.name, count = count) }

    fun challengeTitle(templateKey: String, subject: String?): PresentationMessageView? = when (templateKey) {
        "collection-first-partner" -> plain(PresentationMessageCode.CHALLENGE_COLLECTION_FIRST_PARTNER_TITLE)
        "collection-growing-roster" -> plain(PresentationMessageCode.CHALLENGE_COLLECTION_GROWING_ROSTER_TITLE)
        "party-new-form" -> plain(PresentationMessageCode.CHALLENGE_PARTY_NEW_FORM_TITLE)
        "exploration-open-road" -> plain(PresentationMessageCode.CHALLENGE_EXPLORATION_OPEN_ROAD_TITLE)
        "exploration-curious-eye" -> plain(PresentationMessageCode.CHALLENGE_EXPLORATION_CURIOUS_EYE_TITLE)
        "battle-seasoned" -> plain(PresentationMessageCode.CHALLENGE_BATTLE_SEASONED_TITLE)
        "progress-first-badge" -> plain(PresentationMessageCode.CHALLENGE_PROGRESS_FIRST_BADGE_TITLE)
        "progress-all-badges" -> plain(PresentationMessageCode.CHALLENGE_PROGRESS_ALL_BADGES_TITLE)
        "collection-regional-record" -> plain(PresentationMessageCode.CHALLENGE_COLLECTION_REGIONAL_RECORD_TITLE)
        "exploration-area-items" -> plain(PresentationMessageCode.CHALLENGE_EXPLORATION_AREA_ITEMS_TITLE)
        "battle-leader-no-items" -> plain(PresentationMessageCode.CHALLENGE_BATTLE_LEADER_NO_ITEMS_TITLE)
        "special-minigame" -> subjectMessage(PresentationMessageCode.CHALLENGE_SPECIAL_MINIGAME_TITLE, subject)
        else -> null
    }

    fun challengeDescription(templateKey: String, subject: String?): PresentationMessageView? = when (templateKey) {
        "collection-first-partner" -> plain(PresentationMessageCode.CHALLENGE_COLLECTION_FIRST_PARTNER_DESCRIPTION)
        "collection-growing-roster" -> plain(PresentationMessageCode.CHALLENGE_COLLECTION_GROWING_ROSTER_DESCRIPTION)
        "party-new-form" -> plain(PresentationMessageCode.CHALLENGE_PARTY_NEW_FORM_DESCRIPTION)
        "exploration-open-road" -> plain(PresentationMessageCode.CHALLENGE_EXPLORATION_OPEN_ROAD_DESCRIPTION)
        "exploration-curious-eye" -> plain(PresentationMessageCode.CHALLENGE_EXPLORATION_CURIOUS_EYE_DESCRIPTION)
        "battle-seasoned" -> plain(PresentationMessageCode.CHALLENGE_BATTLE_SEASONED_DESCRIPTION)
        "progress-first-badge" -> plain(PresentationMessageCode.CHALLENGE_PROGRESS_FIRST_BADGE_DESCRIPTION)
        "progress-all-badges" -> plain(PresentationMessageCode.CHALLENGE_PROGRESS_ALL_BADGES_DESCRIPTION)
        "collection-regional-record" -> plain(PresentationMessageCode.CHALLENGE_COLLECTION_REGIONAL_RECORD_DESCRIPTION)
        "exploration-area-items" -> subjectMessage(PresentationMessageCode.CHALLENGE_EXPLORATION_AREA_ITEMS_DESCRIPTION, subject)
        "battle-leader-no-items" -> subjectMessage(PresentationMessageCode.CHALLENGE_BATTLE_LEADER_NO_ITEMS_DESCRIPTION, subject)
        "special-minigame" -> subjectMessage(PresentationMessageCode.CHALLENGE_SPECIAL_MINIGAME_DESCRIPTION, subject)
        else -> null
    }

    private fun ratioMessage(code: PresentationMessageCode, numerator: Int, denominator: Int) =
        PresentationMessageView(code.name, numerator = numerator, denominator = denominator)

    private fun subjectMessage(code: PresentationMessageCode, subject: String?) =
        subject?.let { PresentationMessageView(code.name, subject = it) }

    private fun plain(code: PresentationMessageCode) = PresentationMessageView(code.name)
}

private val API_ERROR_CODES = mapOf(
    "SERVER_BUSY" to PresentationMessageCode.API_SERVER_BUSY,
    "METHOD_NOT_ALLOWED" to PresentationMessageCode.API_METHOD_NOT_ALLOWED,
    "REQUEST_TIMEOUT" to PresentationMessageCode.API_REQUEST_TIMEOUT,
    "GUIDE_LOAD_FAILED" to PresentationMessageCode.API_GUIDE_LOAD_FAILED,
    "INVALID_REQUEST" to PresentationMessageCode.API_INVALID_REQUEST,
    "INTERNAL_ERROR" to PresentationMessageCode.API_INTERNAL_ERROR,
    "NOT_FOUND" to PresentationMessageCode.API_NOT_FOUND,
    "MAP_UNAVAILABLE" to PresentationMessageCode.API_MAP_UNAVAILABLE,
)
private val PROGRESS_METRIC_CODES = mapOf(
    "play-time" to PresentationMessageCode.PROGRESS_METRIC_PLAY_TIME,
    "badges" to PresentationMessageCode.PROGRESS_METRIC_BADGES,
    "seen" to PresentationMessageCode.PROGRESS_METRIC_DEX_SEEN,
    "caught" to PresentationMessageCode.PROGRESS_METRIC_DEX_CAUGHT,
    "money" to PresentationMessageCode.PROGRESS_METRIC_MONEY,
    "battles" to PresentationMessageCode.PROGRESS_METRIC_BATTLES,
    "wildEncounters" to PresentationMessageCode.PROGRESS_METRIC_WILD_ENCOUNTERS,
    "trainerBattles" to PresentationMessageCode.PROGRESS_METRIC_TRAINER_BATTLES,
    "captures" to PresentationMessageCode.PROGRESS_METRIC_CAPTURES,
    "evolutions" to PresentationMessageCode.PROGRESS_METRIC_EVOLUTIONS,
    "areas" to PresentationMessageCode.PROGRESS_METRIC_AREAS_VISITED,
    "pois" to PresentationMessageCode.PROGRESS_METRIC_POINTS_DISCOVERED,
    "partyChanges" to PresentationMessageCode.PROGRESS_METRIC_PARTY_CHANGES,
    "saves" to PresentationMessageCode.PROGRESS_METRIC_SAVES_OBSERVED,
    "challenges" to PresentationMessageCode.PROGRESS_METRIC_CHALLENGES_COMPLETED,
)
private val TIMELINE_CODES_BY_KEY = mapOf(
    "battles" to PresentationMessageCode.TIMELINE_BATTLES,
    "wildEncounters" to PresentationMessageCode.TIMELINE_WILD_ENCOUNTERS,
    "trainerBattles" to PresentationMessageCode.TIMELINE_TRAINER_BATTLES,
    "captures" to PresentationMessageCode.TIMELINE_CAPTURES,
    "evolutions" to PresentationMessageCode.TIMELINE_EVOLUTIONS,
    "areas" to PresentationMessageCode.TIMELINE_AREAS_VISITED,
    "pois" to PresentationMessageCode.TIMELINE_POINTS_DISCOVERED,
    "partyChanges" to PresentationMessageCode.TIMELINE_PARTY_CHANGES,
    "saves" to PresentationMessageCode.TIMELINE_SAVES_OBSERVED,
    "challenges" to PresentationMessageCode.TIMELINE_CHALLENGES_COMPLETED,
)
private val FLAG_CODES = mapOf(
    "Cannot be copied" to PresentationMessageCode.ABILITY_MECHANIC_FLAG_CANNOT_BE_COPIED,
    "Cannot be swapped" to PresentationMessageCode.ABILITY_MECHANIC_FLAG_CANNOT_BE_SWAPPED,
    "Cannot be traced" to PresentationMessageCode.ABILITY_MECHANIC_FLAG_CANNOT_BE_TRACED,
    "Cannot be suppressed" to PresentationMessageCode.ABILITY_MECHANIC_FLAG_CANNOT_BE_SUPPRESSED,
    "Cannot be overwritten" to PresentationMessageCode.ABILITY_MECHANIC_FLAG_CANNOT_BE_OVERWRITTEN,
    "Breakable" to PresentationMessageCode.ABILITY_MECHANIC_FLAG_BREAKABLE,
    "Fails on Imposter" to PresentationMessageCode.ABILITY_MECHANIC_FLAG_FAILS_ON_IMPOSTER,
)
private val POWER_VALUE_CODES = mapOf(
    "Grass move power ×1.5" to PresentationMessageCode.ABILITY_VALUE_GRASS_MOVE_POWER_MULTIPLIER,
    "Fire move power ×1.5" to PresentationMessageCode.ABILITY_VALUE_FIRE_MOVE_POWER_MULTIPLIER,
    "Water move power ×1.5" to PresentationMessageCode.ABILITY_VALUE_WATER_MOVE_POWER_MULTIPLIER,
    "Bug move power ×1.5" to PresentationMessageCode.ABILITY_VALUE_BUG_MOVE_POWER_MULTIPLIER,
)
private val ABILITY_MECHANIC_ARGUMENT_CODES = setOf(
    PresentationMessageCode.ABILITY_MECHANIC_BEHAVIOR,
    PresentationMessageCode.ABILITY_MECHANIC_ACTIVATION_THRESHOLD,
    PresentationMessageCode.ABILITY_MECHANIC_MULTIPLIER,
    PresentationMessageCode.ABILITY_MECHANIC_STAT_STAGE,
    PresentationMessageCode.ABILITY_MECHANIC_STATUS_CURE,
    PresentationMessageCode.ABILITY_MECHANIC_TYPE_CHANGE,
    PresentationMessageCode.ABILITY_MECHANIC_AI_RATING,
    PresentationMessageCode.ABILITY_MECHANIC_FLAG,
    PresentationMessageCode.ABILITY_MECHANIC_ATTACK,
    PresentationMessageCode.ABILITY_MECHANIC_MOVE_POWER,
    PresentationMessageCode.ABILITY_MECHANIC_INCOMING_DAMAGE,
    PresentationMessageCode.ABILITY_MECHANIC_OPPONENT_ATTACK,
    PresentationMessageCode.ABILITY_MECHANIC_NONVOLATILE_STATUS,
)
private val ABILITY_VALUE_RATIO_CODES = setOf(
    PresentationMessageCode.ABILITY_VALUE_HP_THRESHOLD,
    PresentationMessageCode.ABILITY_VALUE_ATTACK_MULTIPLIER,
    PresentationMessageCode.ABILITY_VALUE_GRASS_MOVE_POWER_MULTIPLIER,
    PresentationMessageCode.ABILITY_VALUE_FIRE_MOVE_POWER_MULTIPLIER,
    PresentationMessageCode.ABILITY_VALUE_WATER_MOVE_POWER_MULTIPLIER,
    PresentationMessageCode.ABILITY_VALUE_BUG_MOVE_POWER_MULTIPLIER,
    PresentationMessageCode.ABILITY_VALUE_INCOMING_DAMAGE_MULTIPLIER,
    PresentationMessageCode.ABILITY_VALUE_STAT_STAGE,
    PresentationMessageCode.ABILITY_VALUE_STATUS_CURE_CHANCE,
)
private val SIGNED_NUMERATOR_CODES = setOf(
    PresentationMessageCode.ABILITY_MECHANIC_STAT_STAGE,
    PresentationMessageCode.ABILITY_MECHANIC_OPPONENT_ATTACK,
    PresentationMessageCode.ABILITY_MECHANIC_AI_RATING,
    PresentationMessageCode.ABILITY_VALUE_STAT_STAGE,
)
private val ABILITY_CONDITION_CODES = PresentationMessageCode.entries
    .filter { it.name.startsWith("ABILITY_CONDITION_") }
    .toSet()
private val TIMELINE_CODES = TIMELINE_CODES_BY_KEY.values.toSet()
private val CHALLENGE_SUBJECT_CODES = setOf(
    PresentationMessageCode.CHALLENGE_EXPLORATION_AREA_ITEMS_DESCRIPTION,
    PresentationMessageCode.CHALLENGE_BATTLE_LEADER_NO_ITEMS_DESCRIPTION,
    PresentationMessageCode.CHALLENGE_SPECIAL_MINIGAME_TITLE,
    PresentationMessageCode.CHALLENGE_SPECIAL_MINIGAME_DESCRIPTION,
)
