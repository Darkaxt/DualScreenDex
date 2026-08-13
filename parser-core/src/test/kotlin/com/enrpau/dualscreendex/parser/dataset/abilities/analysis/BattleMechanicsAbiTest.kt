package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BattleMechanicsAbiTest {
    @Test
    fun `retail direct-pointer ABI retains typed parser authority`() {
        val abi = BattleMechanicsAbi(
            record = retailRecord(),
            move = MoveMechanicsAbi(
                tableRoot = 0x0810_0000,
                stride = 12,
                effect = ScalarField(0, ScalarWidth.U8),
                power = ScalarField(1, ScalarWidth.U8),
                type = ScalarField(2, ScalarWidth.U8),
                category = null,
            ),
            activeAbilityIds = setOf(37, 55, 62, 74),
            roleContract = BattleRoleContract.DirectPointers(
                attackerParameterRegister = 0,
                defenderParameterRegister = 1,
            ),
        )

        assertEquals(ScalarWidth.U8, abi.record.ability.width)
        assertEquals(0x20, abi.record.ability.offset)
        assertEquals(12, abi.move.stride)
        assertEquals(listOf(37, 55, 62, 74), abi.activeAbilityIds)
    }

    @Test
    fun `Classic indexed ABI supports a widened ability field and split metadata`() {
        val abi = BattleMechanicsAbi(
            record = retailRecord().copy(
                stride = 0x5C,
                ability = ScalarField(0x20, ScalarWidth.U16),
                hp = ScalarField(0x2A, ScalarWidth.U16),
                maxHp = ScalarField(0x2E, ScalarWidth.U16),
                status = ScalarField(0x50, ScalarWidth.U32),
            ),
            move = MoveMechanicsAbi(
                tableRoot = 0x0820_0000,
                stride = 20,
                effect = ScalarField(0, ScalarWidth.U16),
                power = ScalarField(2, ScalarWidth.U16),
                type = ScalarField(4, ScalarWidth.U8),
                category = ScalarField(16, ScalarWidth.U8),
            ),
            activeAbilityIds = setOf(37, 55, 62, 74),
            roleContract = BattleRoleContract.IndexedArray(
                battleArrayRoot = 0x0202_4000,
                attackerIndexParameterRegister = 1,
                defenderIndexParameterRegister = 2,
            ),
        )

        assertEquals(ScalarWidth.U16, abi.record.ability.width)
        assertEquals(ScalarField(16, ScalarWidth.U8), abi.move.category)
        assertEquals(0x0202_4000, (abi.roleContract as BattleRoleContract.IndexedArray).battleArrayRoot)
    }

    @Test
    fun `field must fit inside its record`() {
        assertThrows(IllegalArgumentException::class.java) {
            retailRecord().copy(status = ScalarField(0x56, ScalarWidth.U32))
        }
    }

    @Test
    fun `ability domain excludes zero and duplicate selection evidence`() {
        assertThrows(IllegalArgumentException::class.java) {
            BattleMechanicsAbi(
                record = retailRecord(),
                move = MoveMechanicsAbi(
                    tableRoot = 0x0810_0000,
                    stride = 12,
                    effect = ScalarField(0, ScalarWidth.U8),
                    power = ScalarField(1, ScalarWidth.U8),
                    type = ScalarField(2, ScalarWidth.U8),
                ),
                activeAbilityIds = setOf(0, 37),
                roleContract = BattleRoleContract.DirectPointers(0, 1),
            )
        }
    }

    private fun retailRecord() = BattleRecordAbi(
        stride = 0x58,
        attack = ScalarField(0x02, ScalarWidth.U16),
        defense = ScalarField(0x04, ScalarWidth.U16),
        specialAttack = ScalarField(0x08, ScalarWidth.U16),
        specialDefense = ScalarField(0x0A, ScalarWidth.U16),
        ability = ScalarField(0x20, ScalarWidth.U8),
        hp = ScalarField(0x28, ScalarWidth.U16),
        maxHp = ScalarField(0x2C, ScalarWidth.U16),
        status = ScalarField(0x4C, ScalarWidth.U32),
    )
}
