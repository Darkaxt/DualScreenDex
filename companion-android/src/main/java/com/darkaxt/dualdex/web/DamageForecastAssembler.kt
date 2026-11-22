package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.battle.BattleMemorySample
import com.darkaxt.dualdex.battle.BattleMonSnapshot
import com.enrpau.dualscreendex.companion.battle.AppliedDamageCondition
import com.enrpau.dualscreendex.companion.battle.BoundedDamageModifier
import com.enrpau.dualscreendex.companion.battle.DamageForecast
import com.enrpau.dualscreendex.companion.battle.DamageForecastCalculator
import com.enrpau.dualscreendex.companion.battle.DamageForecastInput
import com.enrpau.dualscreendex.companion.battle.DamageFormulaEvidence
import com.enrpau.dualscreendex.companion.battle.DamageModifierStage
import com.enrpau.dualscreendex.companion.battle.FixedDamageRule
import com.enrpau.dualscreendex.companion.battle.ForecastBattler
import com.enrpau.dualscreendex.companion.battle.ForecastMove
import com.enrpau.dualscreendex.companion.battle.ForecastMoveCategory
import com.enrpau.dualscreendex.companion.battle.ForecastStatus
import com.enrpau.dualscreendex.companion.battle.ProvenDamageModifier
import com.enrpau.dualscreendex.companion.battle.SemanticProof
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.CatalogTextProjection
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.catalog.defaultTextProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability

internal object DamageForecastAssembler {
    data class ConditionalWeatherPolicy(
        val boundedAlternatives: List<BoundedDamageModifier> = emptyList(),
        val unboundedUnknowns: List<String> = emptyList(),
    )

    /**
     * ROM-only type interpretation for the forecast's conditional weather policy. This does not
     * establish engine weather applicability, a damage formula, or current battle/weather state.
     */
    fun conditionalWeatherPolicy(catalog: ParsedCatalog, typeId: Int): ConditionalWeatherPolicy =
        when (catalog.typesById[typeId]?.semanticRole?.value) {
            TypeSemanticRole.FIRE,
            TypeSemanticRole.WATER,
            -> ConditionalWeatherPolicy(
                boundedAlternatives = listOf(BoundedDamageModifier(
                    kind = AppliedDamageCondition.WEATHER,
                    minimumNumerator = 1,
                    maximumNumerator = 3,
                    denominator = 2,
                    proof = SemanticProof.SOURCE_VALIDATED,
                    playerExplanation = "Weather may change this range.",
                )),
            )
            null -> ConditionalWeatherPolicy(
                unboundedUnknowns = listOf("Weather interaction for this move's type is unresolved."),
            )
            else -> ConditionalWeatherPolicy()
        }

    fun input(
        sample: BattleMemorySample,
        catalog: ParsedCatalog,
        knowledgeMode: KnowledgeMode,
        formula: DamageFormulaEvidence?,
    ): DamageForecastInput? {
        formula ?: return null
        val players = sample.battlers.filter { it.position and 1 == 0 }
        val attacker = sample.commandOwnerBattlerIndex
            ?.let { owner -> players.singleOrNull { it.battlerIndex == owner } }
            ?: players.singleOrNull()
            ?: return null
        val target = sample.opponents.getOrNull(sample.target.opponentIndex) ?: return null
        val moveId = sample.selectedMoveId ?: return null
        val moveRecord = catalog.movesById[moveId] ?: return null
        val category = when (moveRecord.category.value) {
            MoveCategory.PHYSICAL -> ForecastMoveCategory.PHYSICAL
            MoveCategory.SPECIAL -> ForecastMoveCategory.SPECIAL
            MoveCategory.STATUS -> ForecastMoveCategory.STATUS
            else -> return null
        }
        val typeId = moveRecord.typeId.value ?: return null
        val power = moveRecord.power.value ?: return null
        val accuracy = moveRecord.accuracy.value ?: return null
        val effectId = moveRecord.effectId.value
        val unknowns = mutableListOf<String>()
        if (effectId == null || effectId != 0) unknowns += "This move has an unresolved damage effect."
        if (attacker.heldItemId != null || target.heldItemId != null) {
            unknowns += "A held item may change the damage."
        }

        val text = catalog.defaultTextProjection()
        val modifiers = mutableListOf<ProvenDamageModifier>()
        if (typeId in attacker.typeIds) {
            modifiers += ProvenDamageModifier(
                AppliedDamageCondition.STAB,
                3,
                2,
                SemanticProof.STRUCTURAL,
                "Same-type attack bonus",
            )
        }
        addAttackerAbilityModifier(attacker, typeId, category, catalog, text, modifiers, unknowns)
        protectTargetAbility(target, typeId, catalog, knowledgeMode, unknowns)

        val weatherPolicy = conditionalWeatherPolicy(catalog, typeId)
        unknowns += weatherPolicy.unboundedUnknowns
        if (status(attacker.status) == ForecastStatus.BURNED && category == ForecastMoveCategory.PHYSICAL) {
            unknowns += "Burn may change this move's damage."
        }

        return DamageForecastInput(
            formula = formula,
            attacker = attacker.toForecastBattler() ?: return null,
            target = target.toForecastBattler() ?: return null,
            move = ForecastMove(
                id = moveId,
                typeId = typeId,
                category = category,
                power = power,
                accuracyPercent = normalizeAccuracy(accuracy),
                fixedDamageRule = FixedDamageRule.NONE,
            ),
            effectivenessPercent = effectiveness(catalog, typeId, target.typeIds),
            provenModifiers = modifiers,
            boundedAlternatives = weatherPolicy.boundedAlternatives,
            unboundedUnknowns = unknowns.distinct(),
        )
    }

    private fun BattleMonSnapshot.toForecastBattler(): ForecastBattler? = ForecastBattler(
        level = level,
        currentHp = hp,
        maximumHp = maxHp,
        attack = attack ?: return null,
        defense = defense ?: return null,
        specialAttack = specialAttack ?: return null,
        specialDefense = specialDefense ?: return null,
        typeIds = typeIds,
        status = status(status),
        abilityId = abilityId.takeIf { it > 0 },
        heldItemId = heldItemId,
        battlerIndex = battlerIndex,
    )

    private fun addAttackerAbilityModifier(
        attacker: BattleMonSnapshot,
        moveTypeId: Int,
        category: ForecastMoveCategory,
        catalog: ParsedCatalog,
        text: CatalogTextProjection,
        modifiers: MutableList<ProvenDamageModifier>,
        unknowns: MutableList<String>,
    ) {
        val abilityId = attacker.abilityId.takeIf { it > 0 } ?: return
        val ability = catalog.abilitiesById[abilityId]
        val mechanics = ability?.mechanics?.value
        if (mechanics == null) {
            unknowns += "The active ability may change the damage."
            return
        }
        val damageRelevant = mechanics.filter { mechanic ->
            mechanic.kind in setOf(
                AbilityMechanicKind.MULTIPLIER,
                AbilityMechanicKind.ACTIVATION_THRESHOLD,
                AbilityMechanicKind.TYPE_CHANGE,
                AbilityMechanicKind.STAT_STAGE,
            )
        }
        damageRelevant.filter { it.kind == AbilityMechanicKind.MULTIPLIER }.forEach { mechanic ->
            if (mechanic.conditions.all { condition ->
                    when (condition.kind) {
                        AbilityMechanicConditionKind.MOVE_SPLIT -> condition.value.toInt() == when (category) {
                            ForecastMoveCategory.PHYSICAL -> 0
                            ForecastMoveCategory.SPECIAL -> 1
                            ForecastMoveCategory.STATUS -> 2
                        }
                        AbilityMechanicConditionKind.ATTACKER_STATUS_NON_ZERO -> (attacker.status ?: 0L) != 0L
                        AbilityMechanicConditionKind.MOVE_POWER_NON_ZERO -> true
                        AbilityMechanicConditionKind.ATTACKING_MOVE_TYPE -> condition.value.toInt() == moveTypeId
                        AbilityMechanicConditionKind.SWITCH_IN -> false
                    }
                }
            ) {
                modifiers += ProvenDamageModifier(
                    AppliedDamageCondition.ABILITY,
                    mechanic.numerator,
                    mechanic.denominator,
                    SemanticProof.STRUCTURAL,
                    "${text.abilityName(abilityId) ?: "Ability"} is active",
                    DamageModifierStage.ATTACK_STAT,
                )
            }
        }
        if (damageRelevant.any { it.kind != AbilityMechanicKind.MULTIPLIER }) {
            unknowns += "The active ability may change the damage."
        }
    }

    private fun protectTargetAbility(
        target: BattleMonSnapshot,
        moveTypeId: Int,
        catalog: ParsedCatalog,
        knowledgeMode: KnowledgeMode,
        unknowns: MutableList<String>,
    ) {
        val candidates = when (knowledgeMode) {
            KnowledgeMode.DISCOVERED -> setOfNotNull(target.abilityId.takeIf { it > 0 })
            KnowledgeMode.ORGANIC, KnowledgeMode.HIDDEN ->
                catalog.speciesById[target.speciesId]?.abilityIds?.value.orEmpty().toSet()
        }
        val mayChangeIncomingDamage = candidates.any { abilityId ->
            val mechanics = catalog.abilitiesById[abilityId]?.mechanics?.value ?: return@any true
            mechanics.any { mechanic ->
                mechanic.kind in setOf(
                    AbilityMechanicKind.MULTIPLIER,
                    AbilityMechanicKind.ACTIVATION_THRESHOLD,
                    AbilityMechanicKind.TYPE_CHANGE,
                    AbilityMechanicKind.STAT_STAGE,
                ) && mechanic.conditions.all { condition ->
                    condition.kind != AbilityMechanicConditionKind.ATTACKING_MOVE_TYPE ||
                        condition.value.toInt() == moveTypeId
                }
            }
        }
        if (mayChangeIncomingDamage) unknowns += "The target's ability may change the damage."
    }

    private fun effectiveness(catalog: ParsedCatalog, attackingType: Int, defendingTypes: List<Int>): Int {
        var multiplier = 100L
        defendingTypes.distinct().forEach { defendingType ->
            val factor = catalog.typeChart.lastOrNull {
                it.attackingTypeId == attackingType && it.defendingTypeId == defendingType
            }?.multiplierPercent ?: 100
            multiplier = multiplier * factor / 100
        }
        return multiplier.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun status(raw: Long?): ForecastStatus = when {
        raw == null || raw == 0L -> ForecastStatus.NONE
        raw and 0x10L != 0L -> ForecastStatus.BURNED
        raw and 0x08L != 0L || raw and 0x80L != 0L -> ForecastStatus.POISONED
        raw and 0x40L != 0L -> ForecastStatus.PARALYZED
        raw and 0x20L != 0L -> ForecastStatus.FROZEN
        raw and 0x07L != 0L -> ForecastStatus.ASLEEP
        else -> ForecastStatus.NONE
    }

    private fun normalizeAccuracy(raw: Int): Int = if (raw <= 100) raw else ((raw * 100) + 127) / 255
}

internal class DamageForecastMemoizer {
    private var previousInput: DamageForecastInput? = null
    private var previousResult: DamageForecast? = null
    var recomputationCount: Long = 0
        private set
    var calculationCpuNanos: Long = 0
        private set
    val retainedInputCount: Long
        get() = if (previousResult == null) 0 else 1

    fun forecast(input: DamageForecastInput?): DamageForecast {
        if (previousResult != null && previousInput == input) return requireNotNull(previousResult)
        val started = System.nanoTime()
        return DamageForecastCalculator.calculate(input).also { result ->
            calculationCpuNanos += System.nanoTime() - started
            previousInput = input
            previousResult = result
            recomputationCount++
        }
    }

    fun clear() {
        previousInput = null
        previousResult = null
    }
}

internal object DamageFormulaPolicy {
    fun resolve(catalog: ParsedCatalog): DamageFormulaEvidence? {
        val mechanicEvidence = catalog.capabilities[RomCapability.ABILITY_MECHANICS] ?: return null
        if (mechanicEvidence.status != CapabilityStatus.AVAILABLE) return null
        if (catalog.abilitiesById.keys != (1..77).toSet()) return null
        if (catalog.abilitiesById.values.any { it.mechanics.value == null }) return null
        return DamageFormulaEvidence(
            key = "decoded-retail-third-generation",
            proof = SemanticProof.CONTROL_VALIDATED,
            randomNumerators = 85..100,
            randomDenominator = 100,
            criticalRule = com.enrpau.dualscreendex.companion.battle.CriticalRule.DAMAGE_MULTIPLIER,
            criticalNumerator = 2,
            criticalDenominator = 1,
        )
    }
}
