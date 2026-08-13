package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RetailSingleCallerMechanicsLiveRomTest {
    @Test
    fun `one complete caller is diagnostic strength rather than an arbitrary rejection`() {
        val image = loadControl()
        val parse = ParserOrchestrator.analyze(image)
        val layout = parse.probes.single { it.family == parse.selectedFamily }.resolvedLayout!!
        val moves = layout.resolvedDatasets.moveDetails!!
        val abilityIds = layout.resolvedDatasets.abilityNames!!.decodedDirectAbilityIds()
        val result = resolve(image, moves, abilityIds)

        assertTrue(result.toString(), result is RetailBattleMechanicsResolution.Resolved)
        val resolved = (result as RetailBattleMechanicsResolution.Resolved).layout
        assertEquals(1, resolved.proof.callerEvidence.size)
        assertEquals(0x58, resolved.abi.record.stride)
        assertEquals(ScalarField(0x02, ScalarWidth.U16), resolved.abi.record.attack)
        assertEquals(ScalarField(0x20, ScalarWidth.U8), resolved.abi.record.ability)
        assertEquals(
            setOf(
                AttackMechanic(
                    37,
                    setOf(MechanicPredicate.AttackerAbility(37)),
                    MultiplyAttack(2, 1),
                ),
                AttackMechanic(
                    74,
                    setOf(MechanicPredicate.AttackerAbility(74)),
                    MultiplyAttack(2, 1),
                ),
            ),
            resolved.mechanics.toSet(),
        )

        val callerCounterfactual = image.mutableBytes()
        val caller = resolved.proof.callerEvidence.single()
        val callerRootLiterals = (caller.callerLineageEntry until caller.callSite step 2).mapNotNull { offset ->
            val instruction = (ThumbDecoder.decode(image, offset) as? Arm7DecodeResult.Decoded)
                ?.instruction as? Arm7MemoryTransfer
            val address = instruction?.address as? Arm7Address.PcRelative
            address?.resolvedAddress?.toInt()?.takeIf { literal ->
                instruction.load && literal in 0..image.size - 4 &&
                    image.u32le(literal).toInt() == caller.battleArrayRoot
            }
        }.distinct()
        assertTrue("sole caller must decode its battle-array root literal", callerRootLiterals.isNotEmpty())
        callerRootLiterals.forEach { callerCounterfactual.zeroWord(it) }
        assertWithheld(RomImage(callerCounterfactual), moves, abilityIds)

        val moveCounterfactual = image.mutableBytes()
        val moveRootLiterals = resolved.proof.moveTableReferenceSites.mapNotNull { offset ->
            val instruction = (ThumbDecoder.decode(image, offset) as? Arm7DecodeResult.Decoded)
                ?.instruction as? Arm7MemoryTransfer
            (instruction?.address as? Arm7Address.PcRelative)?.resolvedAddress?.toInt()
        }.distinct()
        assertTrue("selected move table must be backed by decoded literals", moveRootLiterals.isNotEmpty())
        moveRootLiterals.forEach { moveCounterfactual.zeroWord(it) }
        assertWithheld(RomImage(moveCounterfactual), moves, abilityIds)

        val semantic = BattleRoleProvenance.analyze(
            image,
            resolved.routineEntry,
            Arm7InstructionSet.THUMB,
            resolved.abi,
            4_096,
        )
        val abilityLoads = semantic.fieldReads.filter {
            it.role == BattleRecordRole.ATTACKER &&
                it.field == resolved.abi.record.ability &&
                it.access == resolved.abi.record.ability
        }.map { it.instructionOffset }.distinct()
        assertTrue("ability predicate must depend on typed attacker field loads", abilityLoads.isNotEmpty())
        val abilityCounterfactual = image.mutableBytes()
        abilityLoads.forEach { offset ->
            abilityCounterfactual[offset] = 0
            abilityCounterfactual[offset + 1] = 0 // lsl r0, r0, #0: no memory-derived ability value.
        }
        assertWithheld(RomImage(abilityCounterfactual), moves, abilityIds)
    }

    @Test
    fun `the complete one-caller first50 cluster resolves without contradictory extras`() {
        singleCallerControls.forEach { control ->
            val image = control.load()
            val parse = ParserOrchestrator.analyze(image)
            val layout = parse.probes.single { it.family == parse.selectedFamily }.resolvedLayout!!
            val result = resolve(
                image,
                layout.resolvedDatasets.moveDetails!!,
                layout.resolvedDatasets.abilityNames!!.decodedDirectAbilityIds(),
            )
            assertTrue("${control.name}: $result", result is RetailBattleMechanicsResolution.Resolved)
            val resolved = (result as RetailBattleMechanicsResolution.Resolved).layout
            assertEquals("${control.name} caller strength", 1, resolved.proof.callerEvidence.size)
            assertEquals("${control.name} stride", 0x58, resolved.abi.record.stride)
            assertEquals("${control.name} attack", ScalarField(0x02, ScalarWidth.U16), resolved.abi.record.attack)
            assertEquals("${control.name} ability", ScalarField(0x20, ScalarWidth.U8), resolved.abi.record.ability)
            assertEquals("${control.name} mechanics", expectedRetailMechanics, resolved.mechanics.toSet())
        }
    }

    private fun resolve(
        image: RomImage,
        moves: ResolvedMoveDetailsLayout,
        abilityIds: Set<Int>,
    ): RetailBattleMechanicsResolution = RetailBattleMechanicsResolver.resolve(
        RomAnalysisSession(image, RomHeaderReader.read(image)),
        moves,
        abilityIds,
    )

    private fun assertWithheld(
        image: RomImage,
        moves: ResolvedMoveDetailsLayout,
        abilityIds: Set<Int>,
    ) {
        val result = resolve(image, moves, abilityIds)
        assertTrue("counterfactual must fail closed, got $result", result !is RetailBattleMechanicsResolution.Resolved)
    }

    private fun RomImage.mutableBytes(): ByteArray = slice(0, size)

    private fun ByteArray.zeroWord(offset: Int) {
        repeat(4) { this[offset + it] = 0 }
    }

    private fun loadControl(): RomImage = singleCallerControls.first().load()

    private data class Control(
        val name: String,
        val path: String,
        val sha256: String,
    ) {
        fun load(): RomImage {
            val romPath = Path.of(path)
            assumeTrue("single-caller control does not exist: $romPath", Files.isRegularFile(romPath))
            return RomImage(Files.readAllBytes(romPath)).also { assertEquals(sha256, it.sha256) }
        }
    }

    private companion object {
        val expectedRetailMechanics = setOf(
            AttackMechanic(
                37,
                setOf(MechanicPredicate.AttackerAbility(37)),
                MultiplyAttack(2, 1),
            ),
            AttackMechanic(
                74,
                setOf(MechanicPredicate.AttackerAbility(74)),
                MultiplyAttack(2, 1),
            ),
        )
        val singleCallerControls = listOf(
            Control(
                "Adventure Red Chapter",
                "D:/Temp/dualdex-expanded-corpus/roms/0003-4ff0e5bd78bf/Adventure Red Chapter (Beta 15 + Expansion Fix C).gba",
                "75ca054238d41b38df5113ccb89af765561ce8963f78f7eb1befab6310306600",
            ),
            Control(
                "Cloud White",
                "D:/Temp/dualdex-expanded-corpus/roms/0030-f220bcb5586f/Cloud White (v523d).gba",
                "f70922408ea71257a2893f06b51cc02aa890e573beb1b84043a100060de1d11d",
            ),
            Control(
                "Cloud White 2",
                "D:/Temp/dualdex-expanded-corpus/roms/0031-96b27b2960a5/Cloud White 2 (v279).gba",
                "6d9075a559c289eee4f336c925b46fdba55f34c6baa0576626d4a3b71513d879",
            ),
            Control(
                "Cloud White 3",
                "D:/Temp/dualdex-expanded-corpus/roms/0032-35a4d2b9276b/Cloud White 3 (v277).gba",
                "7ced98ef9232e3d09892c4e960e326eac8daf3c596f54d773661cc227d25b8e9",
            ),
            Control(
                "Crystal Advance Redux",
                "D:/Temp/dualdex-expanded-corpus/roms/0036-d86043088166/Crystal Advance Redux (7-8-26).gba",
                "fbbcbf32afd427afa5de45799923c414c21b77917004477f214c9f5cd87537b6",
            ),
            Control(
                "Dark Rising Order Destroyed",
                "D:/Temp/dualdex-expanded-corpus/roms/0039-64676931bd53/Dark Rising - Order Destroyed.gba",
                "71b44f3b4be1b17428dd3fcb1c37002268c7b832dc49626b9d57bf56de10f387",
            ),
            Control(
                "Dark Rising Origins",
                "D:/Temp/dualdex-expanded-corpus/roms/0041-d626fb65d28b/Dark Rising Origins - Worlds Collide.gba",
                "c6440addb23d76f514d0ba4baf049a5c34a0d7c0938a5c6ee4fbfa3792f9daea",
            ),
            Control(
                "Dreams",
                "D:/Temp/dualdex-expanded-corpus/roms/0048-1cc23c79cb05/Dreams (v1.5.3).gba",
                "ad73b864873f17add4f931315d3162b792b19c65133c7a6819a85866b1afa403",
            ),
        )
    }
}
