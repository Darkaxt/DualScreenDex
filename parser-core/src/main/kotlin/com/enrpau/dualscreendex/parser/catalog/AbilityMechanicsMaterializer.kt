package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.AttackMechanic
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MechanicPredicate
import com.enrpau.dualscreendex.parser.dataset.abilities.SourceBackedAbilityMechanic
import com.enrpau.dualscreendex.parser.dataset.abilities.SourceBackedAbilityMechanicKind
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import java.util.Locale

enum class AbilityMechanicKind {
    ACTIVATION_THRESHOLD, MULTIPLIER, STAT_STAGE, STATUS_CURE, TYPE_CHANGE, AI_RATING, FLAG,
}

enum class AbilityMechanicConditionKind {
    MOVE_SPLIT, ATTACKER_STATUS_NON_ZERO, SWITCH_IN, MOVE_POWER_NON_ZERO,
}

data class AbilityMechanicCondition(
    val kind: AbilityMechanicConditionKind,
    val value: Long,
    val label: String,
)

data class AbilityMechanic(
    val kind: AbilityMechanicKind,
    val label: String,
    val value: String,
    val numerator: Int,
    val denominator: Int,
    val conditions: List<AbilityMechanicCondition> = emptyList(),
)

data class AbilityMechanicsResult(
    val sourceOffset: Int,
    val confidence: Double,
    val mechanicsByAbility: Map<Int, List<AbilityMechanic>>,
)

object AbilityMechanicsMaterializer {
    fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        abilities: Map<Int, AbilityRecord>,
    ): AbilityMechanicsResult? {
        if (layout.generation != 3) return null
        layout.pokeemeraldExpansion?.let { expansion ->
            val table = layout.tables.abilities ?: return null
            val stride = table.stride ?: expansion.abilityRecordSize
            val mechanics = abilities.keys.associateWith { id ->
                val record = table.offset + id * stride
                val rating = rom.u8(record + expansion.abilityDescriptionPointerOffset + 4).toByte().toInt()
                val flags = rom.u8(record + expansion.abilityDescriptionPointerOffset + 5)
                buildList {
                    add(AbilityMechanic(AbilityMechanicKind.AI_RATING, "AI rating", rating.toString(), rating, 1))
                    EXPANSION_FLAG_LABELS.forEachIndexed { bit, label ->
                        if (flags and (1 shl bit) != 0) {
                            add(AbilityMechanic(AbilityMechanicKind.FLAG, label, "Yes", 1, 1))
                        }
                    }
                }
            }
            return AbilityMechanicsResult(
                sourceOffset = table.offset + expansion.abilityDescriptionPointerOffset + 4,
                confidence = 1.0,
                mechanicsByAbility = mechanics,
            )
        }
        val resolved = layout.resolvedDatasets.abilityMechanics ?: return null
        val mechanics = (resolved.mechanics.mapNotNull { mechanic ->
            mechanic.takeIf { it.abilityId in abilities }?.toCatalogMechanic()
        } + resolved.sourceBackedMechanics.mapNotNull { mechanic ->
            mechanic.takeIf { it.abilityId in abilities }?.toCatalogMechanic()
        })
            .groupBy { it.first }
            .mapValues { (_, entries) -> entries.map { it.second }.distinct() }
        if (mechanics.isEmpty()) return null
        return AbilityMechanicsResult(
            sourceOffset = resolved.routineEntry,
            confidence = 1.0,
            mechanicsByAbility = mechanics,
        )
    }

    private fun AttackMechanic.toCatalogMechanic(): Pair<Int, AbilityMechanic>? {
        if (MechanicPredicate.AttackerAbility(abilityId) !in predicates) return null
        val conditions = predicates.filterNot { it is MechanicPredicate.AttackerAbility }.map { predicate ->
            when (predicate) {
                is MechanicPredicate.MoveSplit -> AbilityMechanicCondition(
                    AbilityMechanicConditionKind.MOVE_SPLIT,
                    predicate.splitId.toLong(),
                    when (predicate.splitId) {
                        0 -> "Physical moves"
                        1 -> "Special moves"
                        else -> "Move split ${predicate.splitId}"
                    },
                )
                is MechanicPredicate.AttackerStatusNonZero -> AbilityMechanicCondition(
                    AbilityMechanicConditionKind.ATTACKER_STATUS_NON_ZERO,
                    predicate.mask,
                    "While affected by status",
                )
                is MechanicPredicate.AttackerAbility -> return null
            }
        }.sortedBy { it.kind.name }
        return abilityId to AbilityMechanic(
            kind = AbilityMechanicKind.MULTIPLIER,
            label = "Attack",
            value = "Attack ×${formatRatio(effect.numerator, effect.denominator)}",
            numerator = effect.numerator,
            denominator = effect.denominator,
            conditions = conditions,
        )
    }

    private fun SourceBackedAbilityMechanic.toCatalogMechanic(): Pair<Int, AbilityMechanic> = abilityId to
        AbilityMechanic(
            kind = when (kind) {
                SourceBackedAbilityMechanicKind.MULTIPLIER -> AbilityMechanicKind.MULTIPLIER
                SourceBackedAbilityMechanicKind.STAT_STAGE -> AbilityMechanicKind.STAT_STAGE
                SourceBackedAbilityMechanicKind.STATUS_CURE -> AbilityMechanicKind.STATUS_CURE
                SourceBackedAbilityMechanicKind.TYPE_CHANGE -> AbilityMechanicKind.TYPE_CHANGE
            },
            label = label,
            value = value,
            numerator = numerator,
            denominator = denominator,
            conditions = condition?.let(::sourceCondition)?.let(::listOf).orEmpty(),
        )

    private fun sourceCondition(label: String): AbilityMechanicCondition = when (label) {
        "Switch-in" -> AbilityMechanicCondition(AbilityMechanicConditionKind.SWITCH_IN, 1, label)
        "While affected by status" -> AbilityMechanicCondition(
            AbilityMechanicConditionKind.ATTACKER_STATUS_NON_ZERO,
            0xFFFF_FFFFL,
            label,
        )
        "Damaging Normal-type moves" -> AbilityMechanicCondition(
            AbilityMechanicConditionKind.MOVE_POWER_NON_ZERO,
            1,
            label,
        )
        else -> error("unsupported source-backed ability condition: $label")
    }

    private fun formatRatio(numerator: Int, denominator: Int): String {
        val value = numerator.toDouble() / denominator
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            "%.2f".format(Locale.ROOT, value).trimEnd('0')
        }
    }

    private val EXPANSION_FLAG_LABELS = listOf(
        "Cannot be copied",
        "Cannot be swapped",
        "Cannot be traced",
        "Cannot be suppressed",
        "Cannot be overwritten",
        "Breakable",
        "Fails on Imposter",
    )
}
