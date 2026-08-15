package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MultiplyAttack
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ResolvedRetailBattleMechanics
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarField
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarWidth

/**
 * Source-backed mechanics for the Modern Emerald 3.5 engine family.
 *
 * Eligibility is structural: the parser must independently select the complete 81-ability
 * domain, the recompiled retail move ABI, and the decoded battle-record/routine contract. The
 * canonical names are semantic keys inside that selected domain; no ROM name, hash, address, or
 * byte signature selects this family. Exact behavior values come from the corresponding public
 * source oracle and are published only after the ROM proves the shared compiled mechanics seam.
 */
object SourceBackedAbilityMechanicsResolver {
    fun resolve(
        abilityNames: ResolvedAbilityNameLayout,
        battle: ResolvedRetailBattleMechanics,
    ): List<SourceBackedAbilityMechanic> {
        if (abilityNames.baseAbilityCount != 81) return emptyList()
        if (battle.abi.record.stride != 0x58) return emptyList()
        if (battle.abi.record.attack != ScalarField(0x02, ScalarWidth.U16)) return emptyList()
        if (battle.abi.record.ability != ScalarField(0x20, ScalarWidth.U8)) return emptyList()
        if (battle.abi.move.stride != 12) return emptyList()
        val names = abilityNames.catalogAbilities().mapValues { it.value.name.value }
        if (EXPECTED_NAMES.any { (id, name) -> names[id] != name }) return emptyList()
        val binary = battle.mechanics.associateBy { it.abilityId }
        if (binary[37]?.effect != MultiplyAttack(2, 1)) return emptyList()
        if (binary[74]?.effect != MultiplyAttack(2, 1)) return emptyList()

        return listOf(
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

    private val EXPECTED_NAMES = mapOf(
        22 to "Intimidate",
        37 to "Huge Power",
        55 to "Hustle",
        61 to "Shed Skin",
        62 to "Guts",
        74 to "Pure Power",
        81 to "Pixilate",
    )
}
