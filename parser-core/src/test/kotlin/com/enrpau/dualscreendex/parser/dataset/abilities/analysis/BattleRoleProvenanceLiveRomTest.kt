package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataProcessing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Immediate
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Instruction
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryWidth
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Register
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7RegisterOperand
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class BattleRoleProvenanceLiveRomTest {
    @Test
    fun `Classic independent status consumer proves full field width from typed root and stride`() {
        val control = Control(
            environmentVariable = "DUALDEX_CLASSIC_ROM",
            fallbackPath = "D:/Temp/dualdex-hack-roms/Classic (v1.5.0b).gba",
            sha256 = "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
            routineEntry = 0x3BF90,
            expectedAttackLoad = 0,
            moveRoot = 0x0835_81F8,
        )
        val image = control.load()
        val rootLoad = image.decodeAs<Arm7MemoryTransfer>(0x3BFB8)
        val rootAddress = rootLoad.address as Arm7Address.PcRelative
        assertEquals(0x0202_30F8, image.u32le(rootAddress.resolvedAddress.toInt()).toInt())

        assertDataOperation(image, 0x3BFBA, Arm7DataOperation.ADD, Arm7Register.R6, Arm7RegisterOperand(Arm7Register.R5), Arm7Immediate(0))
        assertDataOperation(image, 0x3BFBC, Arm7DataOperation.ADD, Arm7Register.R6, Arm7RegisterOperand(Arm7Register.R6), Arm7Immediate(0x50))
        assertDataOperation(image, 0x3BFBE, Arm7DataOperation.MOVE, Arm7Register.R0, null, Arm7Immediate(0x5C))
        assertDataOperation(image, 0x3BFC0, Arm7DataOperation.ADD, Arm7Register.R2, Arm7RegisterOperand(Arm7Register.R4), Arm7Immediate(0))
        assertDataOperation(
            image,
            0x3BFC2,
            Arm7DataOperation.MULTIPLY,
            Arm7Register.R2,
            Arm7RegisterOperand(Arm7Register.R2),
            Arm7RegisterOperand(Arm7Register.R0),
        )
        assertDataOperation(
            image,
            0x3BFD2,
            Arm7DataOperation.ADD,
            Arm7Register.R2,
            Arm7RegisterOperand(Arm7Register.R2),
            Arm7RegisterOperand(Arm7Register.R6),
        )
        val statusLoad = image.decodeAs<Arm7MemoryTransfer>(0x3BFD4)
        assertTrue(statusLoad.load)
        assertEquals(Arm7MemoryWidth.WORD, statusLoad.width)
        assertEquals(Arm7Address.RegisterOffset(Arm7Register.R2), statusLoad.address)
    }

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
        assertTrue(result.toString(), result.fieldReads.any {
            it.role == BattleRecordRole.ATTACKER &&
                it.field == ScalarField(0x50, ScalarWidth.U32) &&
                it.access == ScalarField(0x50, ScalarWidth.U8) &&
                it.instructionOffset == 0x50CAE
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
        assertEquals(
            result.toString(),
            setOf(
                AttackMechanic(
                    abilityId = 37,
                    predicates = setOf(
                        MechanicPredicate.AttackerAbility(37),
                        MechanicPredicate.MoveSplit(0),
                    ),
                    effect = MultiplyAttack(2, 1),
                ),
                AttackMechanic(
                    abilityId = 74,
                    predicates = setOf(
                        MechanicPredicate.AttackerAbility(74),
                        MechanicPredicate.MoveSplit(0),
                    ),
                    effect = MultiplyAttack(2, 1),
                ),
                AttackMechanic(
                    abilityId = 55,
                    predicates = setOf(
                        MechanicPredicate.AttackerAbility(55),
                        MechanicPredicate.MoveSplit(0),
                    ),
                    effect = MultiplyAttack(3, 2),
                ),
                AttackMechanic(
                    abilityId = 62,
                    predicates = setOf(
                        MechanicPredicate.AttackerAbility(62),
                        MechanicPredicate.AttackerStatusNonZero(0xFF),
                        MechanicPredicate.MoveSplit(0),
                    ),
                    effect = MultiplyAttack(3, 2),
                ),
            ),
            result.attackMechanics.toSet(),
        )
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
            status = ScalarField(0x50, ScalarWidth.U32),
        ),
        move = MoveMechanicsAbi(
            tableRoot = moveRoot,
            stride = 20,
            effect = ScalarField(0, ScalarWidth.U16),
            power = ScalarField(2, ScalarWidth.U16),
            type = ScalarField(4, ScalarWidth.U8),
            category = ScalarField(16, ScalarWidth.U8),
            effectiveSplitContextPointer = 0x0202_3598,
            effectiveSplitPackedField = ScalarField(0x2D4, ScalarWidth.U8),
            effectiveSplitMask = 0x60,
        ),
        activeAbilityIds = setOf(37, 55, 62, 74),
        roleContract = BattleRoleContract.IndexedArray(
            battleArrayRoot = 0x0202_30F8,
            attackerIndexParameterRegister = 1,
            defenderIndexParameterRegister = 2,
        ),
        moveParameterRegister = 0,
    )

    private fun assertDataOperation(
        image: RomImage,
        offset: Int,
        operation: Arm7DataOperation,
        destination: Arm7Register,
        first: Any?,
        second: Any,
    ) {
        val instruction = image.decodeAs<Arm7DataProcessing>(offset)
        assertEquals(operation, instruction.operation)
        assertEquals(destination, instruction.destination)
        assertEquals(first, instruction.first)
        assertEquals(second, instruction.second)
    }

    private inline fun <reified T : Arm7Instruction> RomImage.decodeAs(offset: Int): T {
        val result = ThumbDecoder.decode(this, offset)
        assertTrue("expected decoded instruction at 0x${offset.toString(16)}, got $result", result is Arm7DecodeResult.Decoded)
        val instruction = (result as Arm7DecodeResult.Decoded).instruction
        assertTrue("expected ${T::class.simpleName} at 0x${offset.toString(16)}, got $instruction", instruction is T)
        return instruction as T
    }

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
