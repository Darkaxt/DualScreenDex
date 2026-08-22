package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.AttackMechanic
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleMechanicsAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleRecordAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleRoleContract
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MoveMechanicsAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MultiplyAttack
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ResolvedRetailBattleMechanics
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.RetailBattleMechanicsProof
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarField
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarWidth
import com.enrpau.dualscreendex.parser.model.EngineFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceBackedAbilityMechanicsResolverTest {
    @Test
    fun modernStarterAbilitiesExposeTheirActualActivationAndPowerBehavior() {
        val names = MutableList(82) { index -> if (index == 0) "-------" else "Ability $index" }
        mapOf(
            1 to "Stench", 22 to "Intimidate", 37 to "Huge Power", 55 to "Hustle",
            61 to "Shed Skin", 62 to "Guts", 65 to "Overgrow", 66 to "Blaze",
            67 to "Torrent", 68 to "Swarm", 74 to "Pure Power", 81 to "Pixilate",
        ).forEach { (id, name) -> names[id] = name }
        val table = AbilityNameTableLayout(0, 82, 13, 13)
        val rows = names.mapIndexed { id, name ->
            if (id == 0) AbilityNameRowOutcome.StructuralSentinel(0, name)
            else AbilityNameRowOutcome.Decoded(id, name)
        }
        val abilities = ResolvedAbilityNameLayout(table, rows, 82, emptyList())
        val record = BattleRecordAbi(0x58, ScalarField(0x02, ScalarWidth.U16), ability = ScalarField(0x20, ScalarWidth.U8))
        val abi = BattleMechanicsAbi(
            record,
            MoveMechanicsAbi(0x08000100, 12, ScalarField(0, ScalarWidth.U8), ScalarField(1, ScalarWidth.U8), ScalarField(2, ScalarWidth.U8)),
            (1..81).toSet(),
            BattleRoleContract.DirectPointers(0, 1),
        )
        val battle = ResolvedRetailBattleMechanics(
            0,
            abi,
            listOf(
                AttackMechanic(37, emptySet(), MultiplyAttack(2, 1)),
                AttackMechanic(74, emptySet(), MultiplyAttack(2, 1)),
            ),
            RetailBattleMechanicsProof(emptyList(), emptyList(), emptyList(), emptyList()),
        )

        val mechanics = SourceBackedAbilityMechanicsResolver.resolve(EngineFamily.EMERALD, abilities, battle)

        mapOf(65 to "Grass", 66 to "Fire", 67 to "Water", 68 to "Bug").forEach { (abilityId, type) ->
            val actual = mechanics.filter { it.abilityId == abilityId }
            assertTrue(actual.any { it.kind == SourceBackedAbilityMechanicKind.ACTIVATION_THRESHOLD && it.value == "HP ≤ 1/3" })
            assertTrue(actual.any { it.kind == SourceBackedAbilityMechanicKind.MULTIPLIER && it.value == "$type move power ×1.5" })
        }
        assertEquals(81 + 5 + 8, mechanics.size)
    }
}
