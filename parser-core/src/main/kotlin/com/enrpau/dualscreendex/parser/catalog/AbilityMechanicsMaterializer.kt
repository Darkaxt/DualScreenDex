package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.AttackMechanic
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MechanicPredicate
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.CompiledAbilityRatingResolver
import com.enrpau.dualscreendex.parser.dataset.abilities.ResolvedCompiledAbilityRatings
import com.enrpau.dualscreendex.parser.dataset.abilities.SourceBackedAbilityMechanic
import com.enrpau.dualscreendex.parser.dataset.abilities.SourceBackedAbilityMechanicKind
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import java.util.Locale

enum class AbilityMechanicKind {
    BEHAVIOR, ACTIVATION_THRESHOLD, MULTIPLIER, STAT_STAGE, STATUS_CURE, TYPE_CHANGE, AI_RATING, FLAG,
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
        abilityDescriptions: AbilityDescriptionResult? = null,
    ): AbilityMechanicsResult? = materialize(
        rom = rom,
        layout = layout,
        abilities = abilities,
        abilityDescriptions = abilityDescriptions,
        compiledRatings = null,
    )

    fun materialize(
        session: RomAnalysisSession,
        layout: ResolvedRomLayout,
        abilities: Map<Int, AbilityRecord>,
        abilityDescriptions: AbilityDescriptionResult? = null,
    ): AbilityMechanicsResult? = materialize(
        rom = session.rom,
        layout = layout,
        abilities = abilities,
        abilityDescriptions = abilityDescriptions,
        compiledRatings = if (
            layout.pokeemeraldExpansion == null &&
            layout.headerlessUnifiedSpecies?.abilities?.abilityRatingOffset == null &&
            layout.resolvedDatasets.abilityMechanics == null
        ) {
            CompiledAbilityRatingResolver.resolve(session, abilities.keys)
        } else {
            null
        },
    )

    private fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        abilities: Map<Int, AbilityRecord>,
        abilityDescriptions: AbilityDescriptionResult?,
        compiledRatings: ResolvedCompiledAbilityRatings?,
    ): AbilityMechanicsResult? {
        if (layout.generation != 3) return null
        val embeddedMechanics = layout.pokeemeraldExpansion?.let { expansion ->
            EmbeddedAbilityMechanics(
                recordSize = expansion.abilityRecordSize,
                ratingOffset = expansion.abilityDescriptionPointerOffset + 4,
                flagsOffset = expansion.abilityDescriptionPointerOffset + 5,
            )
        } ?: layout.headerlessUnifiedSpecies?.abilities?.let { abilities ->
            val ratingOffset = abilities.abilityRatingOffset ?: return@let null
            val flagsOffset = abilities.abilityFlagsOffset ?: return@let null
            EmbeddedAbilityMechanics(abilities.abilityRecordSize, ratingOffset, flagsOffset)
        }
        embeddedMechanics?.let { embedded ->
            val table = layout.tables.abilities ?: return null
            val stride = table.stride ?: embedded.recordSize
            val mechanics = abilities.keys.associateWith { id ->
                val record = table.offset + id * stride
                val rating = rom.u8(record + embedded.ratingOffset).toByte().toInt()
                val flags = rom.u8(record + embedded.flagsOffset)
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
                sourceOffset = table.offset + embedded.ratingOffset,
                confidence = 1.0,
                mechanicsByAbility = mechanics,
            )
        }

        compiledRatings?.let { ratings ->
            return AbilityMechanicsResult(
                sourceOffset = ratings.sourceOffset,
                confidence = 1.0,
                mechanicsByAbility = ratings.ratingsByAbility.mapValues { (_, rating) ->
                    listOf(AbilityMechanic(AbilityMechanicKind.AI_RATING, "AI rating", rating.toString(), rating, 1))
                },
            )
        }

        val resolved = layout.resolvedDatasets.abilityMechanics
        val binaryMechanics = resolved?.let { evidence ->
            evidence.mechanics.mapNotNull { mechanic ->
                mechanic.takeIf { it.abilityId in abilities }?.toCatalogMechanic()
            } + evidence.sourceBackedMechanics.mapNotNull { mechanic ->
                mechanic.takeIf { it.abilityId in abilities }?.toCatalogMechanic()
            }
        }.orEmpty()
        val documentedMechanics = if (binaryMechanics.mapTo(mutableSetOf()) { it.first }.containsAll(abilities.keys)) {
            emptyList()
        } else {
            documentedAbilityProfile(abilities, abilityDescriptions)
        }
        val mechanics = (binaryMechanics + documentedMechanics)
            .groupBy { it.first }
            .mapValues { (_, entries) -> entries.map { it.second }.distinct() }
        if (mechanics.isEmpty()) return null
        return AbilityMechanicsResult(
            sourceOffset = resolved?.routineEntry ?: requireNotNull(abilityDescriptions).sourceOffset,
            confidence = 1.0,
            mechanicsByAbility = mechanics,
        )
    }

    private fun documentedAbilityProfile(
        abilities: Map<Int, AbilityRecord>,
        descriptions: AbilityDescriptionResult?,
    ): List<Pair<Int, AbilityMechanic>> {
        if (abilities.isEmpty() || descriptions == null) return emptyList()
        if (abilities.keys.any { it !in descriptions.descriptions }) return emptyList()
        return abilities.keys.sorted().map { id ->
            val decoded = descriptions.descriptions.getValue(id)
            val behavior = if (decoded.equals("No description", ignoreCase = true)) {
                val normalizedName = abilities.getValue(id).name.value.orEmpty().uppercase(Locale.ROOT)
                STANDARD_BEHAVIOR_FALLBACKS[normalizedName] ?: return emptyList()
            } else {
                decoded.takeIf(String::isNotBlank) ?: return emptyList()
            }
            id to AbilityMechanic(
                kind = AbilityMechanicKind.BEHAVIOR,
                label = "Effect",
                value = behavior,
                numerator = 1,
                denominator = 1,
            )
        }
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
                SourceBackedAbilityMechanicKind.BEHAVIOR -> AbilityMechanicKind.BEHAVIOR
                SourceBackedAbilityMechanicKind.ACTIVATION_THRESHOLD -> AbilityMechanicKind.ACTIVATION_THRESHOLD
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

    private data class EmbeddedAbilityMechanics(
        val recordSize: Int,
        val ratingOffset: Int,
        val flagsOffset: Int,
    )

    private val EXPANSION_FLAG_LABELS = listOf(
        "Cannot be copied",
        "Cannot be swapped",
        "Cannot be traced",
        "Cannot be suppressed",
        "Cannot be overwritten",
        "Breakable",
        "Fails on Imposter",
    )

    private val STANDARD_BEHAVIOR_FALLBACKS = mapOf(
        "IRON FIST" to "Boosts punching moves.",
        "RECKLESS" to "Boosts recoil moves.",
        "SHEER FORCE" to "Boosts moves with added effects but removes those effects.",
        "DEFEATIST" to "Halves Attack and Sp. Atk below half HP.",
        "SAND FORCE" to "Boosts Rock, Ground, and Steel moves in a sandstorm.",
        "STRONG JAW" to "Boosts biting moves.",
        "MEGA LAUNCHR" to "Boosts pulse and aura moves.",
        "TOUGH CLAWS" to "Boosts contact moves.",
    )
}
