package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class BattleRoleProvenanceLiveRomTest {
    @Test
    fun `retail Emerald and FRLG preserve the direct attacker pointer through compiler factoring`() {
        val controls = listOf(
            Control(
                environmentVariable = "DUALDEX_OFFICIAL_EMERALD_ROM",
                fallbackPath = "D:/Temp/dualdex-official-roms/Pokemon - Emerald Version (USA, Europe).gba",
                sha256 = "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                routineEntry = 0x6957C,
                expectedAttackLoad = 0x69602,
                moveRoot = 0x0831_C898,
            ),
            Control(
                environmentVariable = "DUALDEX_OFFICIAL_FIRERED_ROM",
                fallbackPath = "D:/Temp/dualdex-official-roms/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
                sha256 = "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                routineEntry = 0x3ED00,
                expectedAttackLoad = 0x3ED82,
                moveRoot = 0x0825_0C74,
            ),
            Control(
                environmentVariable = "DUALDEX_MODERN_EMERALD_ROM",
                fallbackPath = "D:/Temp/dualdex-expanded-corpus/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
                sha256 = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
                routineEntry = 0x18FBE8,
                expectedAttackLoad = 0x18FC7E,
                moveRoot = 0x088D_6924,
            ),
        )

        controls.forEach { control ->
            val result = BattleRoleProvenance.analyze(
                image = control.load(),
                entry = control.routineEntry,
                instructionSet = Arm7InstructionSet.THUMB,
                abi = retailAbi(control.moveRoot),
                maxDecodedInstructions = 4_096,
            )

            assertTrue(result.toString(), result.recordPointers.any {
                it.role == BattleRecordRole.ATTACKER &&
                    it.origin == BattleRecordOrigin.DIRECT_PARAMETER
            })
            assertTrue(result.toString(), result.fieldReads.any {
                it.role == BattleRecordRole.ATTACKER &&
                    it.field == ScalarField(0x02, ScalarWidth.U16) &&
                    it.instructionOffset == control.expectedAttackLoad
            })
            assertEquals(
                setOf(
                    AttackMechanic(
                        abilityId = 37,
                        predicates = setOf(MechanicPredicate.AttackerAbility(37)),
                        effect = MultiplyAttack(2, 1),
                    ),
                    AttackMechanic(
                        abilityId = 74,
                        predicates = setOf(MechanicPredicate.AttackerAbility(74)),
                        effect = MultiplyAttack(2, 1),
                    ),
                ),
                result.attackMechanics.toSet(),
            )
        }
    }

    @Test
    fun `Classic derives the attacker record from its typed indexed-array ABI`() {
        val control = Control(
            environmentVariable = "DUALDEX_CLASSIC_ROM",
            fallbackPath = "D:/Temp/dualdex-hack-roms/Classic (v1.5.0b).gba",
            sha256 = "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
            routineEntry = 0x5097C,
            expectedAttackLoad = 0x50A10,
            moveRoot = 0x0835_81F8,
        )
        val result = BattleRoleProvenance.analyze(
            image = control.load(),
            entry = control.routineEntry,
            instructionSet = Arm7InstructionSet.THUMB,
            abi = classicAbi(control.moveRoot),
            maxDecodedInstructions = 4_096,
        )

        assertTrue(result.toString(), result.recordPointers.any {
            it.role == BattleRecordRole.ATTACKER &&
                it.origin == BattleRecordOrigin.INDEXED_ARRAY &&
                it.instructionOffset == 0x509A6
        })
        assertTrue(result.toString(), result.fieldReads.any {
            it.role == BattleRecordRole.ATTACKER &&
                it.field == ScalarField(0x02, ScalarWidth.U16) &&
                it.instructionOffset == control.expectedAttackLoad
        })
        // Real discrepancy regression: CalcAttackStat crosses opaque helpers after the first
        // modifier dispatch. Volatile values must remain clobbered, while the attacker index in
        // callee-saved r5 must still form a typed record on the later branch.
        assertTrue(result.toString(), result.recordPointers.any {
            it.role == BattleRecordRole.ATTACKER &&
                it.origin == BattleRecordOrigin.INDEXED_ARRAY &&
                it.instructionOffset == 0x50B20
        })
        assertEquals(emptyList<FieldReadEvidence>(), result.fieldReads.filter {
            it.instructionOffset == control.expectedAttackLoad && it.role == BattleRecordRole.DEFENDER
        })
    }

    private fun retailAbi(moveRoot: Int) = BattleMechanicsAbi(
        record = BattleRecordAbi(
            stride = 0x58,
            attack = ScalarField(0x02, ScalarWidth.U16),
            ability = ScalarField(0x20, ScalarWidth.U8),
        ),
        move = MoveMechanicsAbi(
            tableRoot = moveRoot,
            stride = 12,
            effect = ScalarField(0, ScalarWidth.U8),
            power = ScalarField(1, ScalarWidth.U8),
            type = ScalarField(2, ScalarWidth.U8),
        ),
        activeAbilityIds = setOf(37, 55, 62, 74),
        roleContract = BattleRoleContract.DirectPointers(0, 1),
    )

    private fun classicAbi(moveRoot: Int) = BattleMechanicsAbi(
        record = BattleRecordAbi(
            stride = 0x5C,
            attack = ScalarField(0x02, ScalarWidth.U16),
            ability = ScalarField(0x20, ScalarWidth.U16),
        ),
        move = MoveMechanicsAbi(
            tableRoot = moveRoot,
            stride = 20,
            effect = ScalarField(0, ScalarWidth.U16),
            power = ScalarField(2, ScalarWidth.U16),
            type = ScalarField(4, ScalarWidth.U8),
            category = ScalarField(16, ScalarWidth.U8),
        ),
        activeAbilityIds = setOf(37, 55, 62, 74),
        roleContract = BattleRoleContract.IndexedArray(
            battleArrayRoot = 0x0202_30F8,
            attackerIndexParameterRegister = 1,
            defenderIndexParameterRegister = 2,
        ),
    )

    private data class Control(
        val environmentVariable: String,
        val fallbackPath: String,
        val sha256: String,
        val routineEntry: Int,
        val expectedAttackLoad: Int,
        val moveRoot: Int,
    ) {
        fun load(): RomImage {
            val configured = System.getenv(environmentVariable)?.takeIf(String::isNotBlank)
            val path = Path.of(configured ?: fallbackPath)
            assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
            return RomImage(Files.readAllBytes(path)).also { image ->
                assertEquals(sha256, image.sha256)
            }
        }
    }
}
