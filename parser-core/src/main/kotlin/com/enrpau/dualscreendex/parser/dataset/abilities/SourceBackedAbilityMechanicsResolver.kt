package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MultiplyAttack
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ResolvedRetailBattleMechanics
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarField
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarWidth
import com.enrpau.dualscreendex.parser.model.EngineFamily
import java.util.Locale

/**
 * Source-oracle behavior profiles joined to a parser-selected ability domain and a decoded
 * retail battle ABI. Eligibility requires the common ARM7TDMI mechanics seam and the complete
 * source-family ability-name domain; no ROM name, hash, or address selects a profile.
 */
object SourceBackedAbilityMechanicsResolver {
    fun resolve(
        family: EngineFamily,
        abilityNames: ResolvedAbilityNameLayout,
        battle: ResolvedRetailBattleMechanics,
    ): List<SourceBackedAbilityMechanic> {
        if (battle.abi.record.stride != 0x58) return emptyList()
        if (battle.abi.record.attack != ScalarField(0x02, ScalarWidth.U16)) return emptyList()
        if (battle.abi.record.ability != ScalarField(0x20, ScalarWidth.U8)) return emptyList()
        if (battle.abi.move.stride != 12) return emptyList()
        val binary = battle.mechanics.associateBy { it.abilityId }
        if (binary[37]?.effect != MultiplyAttack(2, 1)) return emptyList()
        if (binary[74]?.effect != MultiplyAttack(2, 1)) return emptyList()

        val names = abilityNames.catalogAbilities().mapValues { (_, ability) ->
            ability.name.value.orEmpty().uppercase(Locale.ROOT)
        }
        return when (abilityNames.baseAbilityCount) {
            77 -> officialProfile(family, names)
            81 -> modernProfile(names)
            else -> emptyList()
        }
    }

    private fun officialProfile(
        family: EngineFamily,
        names: Map<Int, String>,
    ): List<SourceBackedAbilityMechanic> {
        val masks = when (family) {
            EngineFamily.RUBY_SAPPHIRE -> RUBY_SAPPHIRE_BEHAVIOR_MASKS
            EngineFamily.EMERALD -> EMERALD_BEHAVIOR_MASKS
            EngineFamily.FIRERED_LEAFGREEN -> FIRERED_LEAFGREEN_BEHAVIOR_MASKS
            else -> return emptyList()
        }
        if (OFFICIAL_NAMES.any { (id, expected) -> names[id] != expected }) return emptyList()
        return behaviorMechanics(masks) + starterBoostMechanics() + typedDefensiveMechanics()
    }

    private fun modernProfile(names: Map<Int, String>): List<SourceBackedAbilityMechanic> {
        if (MODERN_SENTINELS.any { (id, expected) -> names[id] != expected.uppercase(Locale.ROOT) }) {
            return emptyList()
        }
        return behaviorMechanics(MODERN_BEHAVIOR_MASKS) + starterBoostMechanics() + typedDefensiveMechanics() + listOf(
            SourceBackedAbilityMechanic(
                22,
                SourceBackedAbilityMechanicKind.STAT_STAGE,
                "Opponents' Attack",
                "−1 stage on switch-in",
                -1,
                1,
                "Switch-in",
            ),
            SourceBackedAbilityMechanic(
                55,
                SourceBackedAbilityMechanicKind.MULTIPLIER,
                "Attack",
                "Attack ×1.5",
                3,
                2,
            ),
            SourceBackedAbilityMechanic(
                61,
                SourceBackedAbilityMechanicKind.STATUS_CURE,
                "Nonvolatile status",
                "1/3 chance to cure",
                1,
                3,
                "While affected by status",
            ),
            SourceBackedAbilityMechanic(
                62,
                SourceBackedAbilityMechanicKind.MULTIPLIER,
                "Attack",
                "Attack ×1.5",
                3,
                2,
                "While affected by status",
            ),
            SourceBackedAbilityMechanic(
                81,
                SourceBackedAbilityMechanicKind.TYPE_CHANGE,
                "Move type",
                "Normal → Fairy",
                1,
                1,
                "Damaging Normal-type moves",
            ),
        )
    }

    private fun starterBoostMechanics(): List<SourceBackedAbilityMechanic> = listOf(
        65 to "Grass",
        66 to "Fire",
        67 to "Water",
        68 to "Bug",
    ).flatMap { (abilityId, type) ->
        listOf(
            SourceBackedAbilityMechanic(
                abilityId,
                SourceBackedAbilityMechanicKind.ACTIVATION_THRESHOLD,
                "Activation",
                "HP ≤ 1/3",
                1,
                3,
            ),
            SourceBackedAbilityMechanic(
                abilityId,
                SourceBackedAbilityMechanicKind.MULTIPLIER,
                "Power",
                "$type move power ×1.5",
                3,
                2,
            ),
        )
    }

    private fun typedDefensiveMechanics(): List<SourceBackedAbilityMechanic> = listOf(
        DefensiveTypeModifier(10, "Electric", 0, 1),
        DefensiveTypeModifier(11, "Water", 0, 1),
        DefensiveTypeModifier(18, "Fire", 0, 1),
        DefensiveTypeModifier(26, "Ground", 0, 1),
        DefensiveTypeModifier(47, "Fire", 1, 2),
        DefensiveTypeModifier(47, "Ice", 1, 2),
    ).map { modifier ->
        SourceBackedAbilityMechanic(
            abilityId = modifier.abilityId,
            kind = SourceBackedAbilityMechanicKind.MULTIPLIER,
            label = "Incoming damage",
            value = "${modifier.typeName} damage ×${formatRatio(modifier.numerator, modifier.denominator)}",
            numerator = modifier.numerator,
            denominator = modifier.denominator,
            incomingTypeName = modifier.typeName,
        )
    }

    private fun formatRatio(numerator: Int, denominator: Int): String {
        val value = numerator.toDouble() / denominator
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    private data class DefensiveTypeModifier(
        val abilityId: Int,
        val typeName: String,
        val numerator: Int,
        val denominator: Int,
    )

    private fun behaviorMechanics(masks: IntArray): List<SourceBackedAbilityMechanic> =
        masks.mapIndexed { index, mask ->
            SourceBackedAbilityMechanic(
                abilityId = index + 1,
                kind = SourceBackedAbilityMechanicKind.BEHAVIOR,
                label = "Implementation",
                value = when (mask) {
                    SOURCE_CODE -> "Compiled source behavior"
                    BATTLE_SCRIPT -> "Battle-script behavior"
                    SOURCE_CODE or BATTLE_SCRIPT -> "Compiled source and battle-script behavior"
                    else -> "Defined but inactive in this engine"
                },
                numerator = 1,
                denominator = 1,
            )
        }

    private const val SOURCE_CODE = 1
    private const val BATTLE_SCRIPT = 2

    private val OFFICIAL_NAMES = listOf(
        "STENCH", "DRIZZLE", "SPEED BOOST", "BATTLE ARMOR", "STURDY", "DAMP", "LIMBER",
        "SAND VEIL", "STATIC", "VOLT ABSORB", "WATER ABSORB", "OBLIVIOUS", "CLOUD NINE",
        "COMPOUNDEYES", "INSOMNIA", "COLOR CHANGE", "IMMUNITY", "FLASH FIRE", "SHIELD DUST",
        "OWN TEMPO", "SUCTION CUPS", "INTIMIDATE", "SHADOW TAG", "ROUGH SKIN", "WONDER GUARD",
        "LEVITATE", "EFFECT SPORE", "SYNCHRONIZE", "CLEAR BODY", "NATURAL CURE", "LIGHTNINGROD",
        "SERENE GRACE", "SWIFT SWIM", "CHLOROPHYLL", "ILLUMINATE", "TRACE", "HUGE POWER",
        "POISON POINT", "INNER FOCUS", "MAGMA ARMOR", "WATER VEIL", "MAGNET PULL", "SOUNDPROOF",
        "RAIN DISH", "SAND STREAM", "PRESSURE", "THICK FAT", "EARLY BIRD", "FLAME BODY",
        "RUN AWAY", "KEEN EYE", "HYPER CUTTER", "PICKUP", "TRUANT", "HUSTLE", "CUTE CHARM",
        "PLUS", "MINUS", "FORECAST", "STICKY HOLD", "SHED SKIN", "GUTS", "MARVEL SCALE",
        "LIQUID OOZE", "OVERGROW", "BLAZE", "TORRENT", "SWARM", "ROCK HEAD", "DROUGHT",
        "ARENA TRAP", "VITAL SPIRIT", "WHITE SMOKE", "PURE POWER", "SHELL ARMOR", "CACOPHONY",
        "AIR LOCK",
    ).mapIndexed { index, name -> index + 1 to name }.toMap()

    private val MODERN_SENTINELS = mapOf(
        1 to "Stench",
        22 to "Intimidate",
        37 to "Huge Power",
        55 to "Hustle",
        61 to "Shed Skin",
        62 to "Guts",
        74 to "Pure Power",
        81 to "Pixilate",
    )

    // Development-time source-oracle category masks. Index zero represents ability ID 1.
    private val RUBY_SAPPHIRE_BEHAVIOR_MASKS = intArrayOf(
        1,1,3,2,2,2,3,2,3,3,3,3,3,2,3,1,3,3,2,3,2,1,3,1,3,3,3,1,2,3,3,2,3,3,1,1,3,1,2,
        3,3,1,3,3,1,3,1,1,1,1,2,2,2,3,3,3,1,1,1,2,3,1,3,2,1,1,1,1,2,1,3,3,2,3,2,0,3,
    )
    private val EMERALD_BEHAVIOR_MASKS = intArrayOf(
        1,3,3,2,2,2,3,3,3,3,3,3,3,3,3,3,3,3,2,3,3,3,3,3,3,3,3,3,2,3,3,2,3,3,1,3,3,3,2,
        3,3,1,3,3,3,3,1,1,3,1,3,3,2,3,3,3,1,1,3,3,3,3,3,2,1,1,1,1,2,3,3,3,3,3,2,0,3,
    )
    private val FIRERED_LEAFGREEN_BEHAVIOR_MASKS = intArrayOf(
        1,3,3,2,2,2,3,2,3,3,3,3,3,2,3,3,3,3,2,3,2,3,3,3,3,3,3,3,2,3,3,2,3,3,1,3,3,3,2,
        3,3,1,3,3,3,3,1,1,3,1,2,2,2,3,3,3,1,1,3,2,3,1,3,2,1,1,1,1,2,3,3,3,2,3,2,0,3,
    )
    private val MODERN_BEHAVIOR_MASKS = intArrayOf(
        1,3,3,2,2,2,3,3,3,3,3,3,3,3,3,3,3,3,2,3,3,3,3,3,3,3,3,3,2,3,3,2,3,3,3,3,3,3,2,
        3,3,1,3,3,3,3,1,1,3,1,3,3,2,3,3,3,1,1,3,3,3,3,3,2,1,1,1,1,2,3,3,3,3,3,2,1,3,
        1,1,1,2,
    )
}
