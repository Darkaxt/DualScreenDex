package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
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

class RetailComputedDispatchMechanicsLiveRomTest {
    @Test
    fun `typed ability dispatch proves only complete Clover attack handlers`() {
        val image = loadControl()
        val parsed = ParserOrchestrator.analyze(image)
        val layout = parsed.probes.single { it.family == parsed.selectedFamily }.resolvedLayout!!
        val moves = layout.resolvedDatasets.moveDetails!!
        val abilityIds = layout.resolvedDatasets.abilityNames!!.decodedDirectAbilityIds()

        val result = resolve(image, moves, abilityIds)

        assertEquals(expectedMechanics, result.attackMechanics.toSet())
        assertTrue(
            result.fieldReads.any {
                it.role == BattleRecordRole.ATTACKER &&
                    it.field == ScalarField(0x02, ScalarWidth.U16) &&
                    it.access == it.field
            },
        )
        assertTrue(
            result.fieldReads.any {
                it.role == BattleRecordRole.ATTACKER &&
                    it.field == ScalarField(0x20, ScalarWidth.U8) &&
                    it.access == it.field
            },
        )

        listOf(37, 55, 74).forEach { abilityId ->
            val corrupted = image.slice(0, image.size)
            repeat(4) { corrupted[DISPATCH_TABLE + abilityId * 4 + it] = 0 }
            val counterfactual = resolve(RomImage(corrupted), moves, abilityIds)
            assertEquals(
                "only corrupted ability $abilityId must be withheld",
                expectedMechanics.filterNot { it.abilityId == abilityId }.toSet(),
                counterfactual.attackMechanics.toSet(),
            )
        }
    }

    @Test
    fun `resolver consumes the selected Clover ABI without rediscovering competing stat fields`() {
        val image = loadControl()
        val parsed = ParserOrchestrator.analyze(image)
        val layout = parsed.probes.single { it.family == parsed.selectedFamily }.resolvedLayout!!
        val moves = layout.resolvedDatasets.moveDetails!!
        val abilityIds = layout.resolvedDatasets.abilityNames!!.decodedDirectAbilityIds()
        val selectedAbi = typedAbi(moves, abilityIds)

        val resolved = RetailBattleMechanicsResolver.resolve(
            RomAnalysisSession(image, RomHeaderReader.read(image)),
            moves,
            abilityIds,
            selectedAbi,
        )

        assertTrue(resolved.toString(), resolved is RetailBattleMechanicsResolution.Resolved)
        val mechanics = (resolved as RetailBattleMechanicsResolution.Resolved).layout
        assertEquals(ROUTINE_ENTRY, mechanics.routineEntry)
        assertEquals(selectedAbi, mechanics.abi)
        assertEquals(expectedMechanics, mechanics.mechanics.toSet())
    }

    @Test
    fun `resolver rejects a selected ABI contradicted by decoded caller stride`() {
        val image = loadControl()
        val parsed = ParserOrchestrator.analyze(image)
        val layout = parsed.probes.single { it.family == parsed.selectedFamily }.resolvedLayout!!
        val moves = layout.resolvedDatasets.moveDetails!!
        val abilityIds = layout.resolvedDatasets.abilityNames!!.decodedDirectAbilityIds()
        val contradicted = typedAbi(moves, abilityIds, recordStride = 0x5C)

        val resolution = RetailBattleMechanicsResolver.resolve(
            RomAnalysisSession(image, RomHeaderReader.read(image)),
            moves,
            abilityIds,
            contradicted,
        )

        assertTrue(resolution.toString(), resolution is RetailBattleMechanicsResolution.Unavailable)
        assertTrue(
            (resolution as RetailBattleMechanicsResolution.Unavailable).reason,
            resolution.reason.contains("selected ABI record stride"),
        )
    }

    private fun resolve(
        image: RomImage,
        moves: ResolvedMoveDetailsLayout,
        abilityIds: Set<Int>,
    ): BattleRoleProvenanceResult = BattleRoleProvenance.analyze(
        image = image,
        entry = ROUTINE_ENTRY,
        instructionSet = Arm7InstructionSet.THUMB,
        abi = typedAbi(moves, abilityIds),
        maxDecodedInstructions = 4_096,
    )

    private fun typedAbi(
        moves: ResolvedMoveDetailsLayout,
        abilityIds: Set<Int>,
        recordStride: Int = 0x58,
    ) = BattleMechanicsAbi(
        record = BattleRecordAbi(
            stride = recordStride,
            attack = ScalarField(0x02, ScalarWidth.U16),
            ability = ScalarField(0x20, ScalarWidth.U8),
        ),
        move = MoveMechanicsAbi(
            tableRoot = 0x0800_0000 + moves.table.offset.toInt(),
            stride = moves.table.abi.recordSize,
            effect = ScalarField(0, ScalarWidth.U8),
            power = ScalarField(1, ScalarWidth.U8),
            type = ScalarField(2, ScalarWidth.U8),
        ),
        activeAbilityIds = abilityIds,
        roleContract = BattleRoleContract.DirectPointers(0, 1),
    )

    private fun loadControl(): RomImage {
        val path = Path.of(
            "D:/Temp/dualdex-expanded-corpus/roms/0033-ae1f81f2f6ea/Clover (v1.3.3).gba",
        )
        assumeTrue("Clover control does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also {
            assertEquals("42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9", it.sha256)
        }
    }

    private companion object {
        const val DISPATCH_TABLE = 0xBA160C
        const val ROUTINE_ENTRY = 0xBA07E0
        val expectedMechanics = setOf(
            AttackMechanic(37, setOf(MechanicPredicate.AttackerAbility(37)), MultiplyAttack(2, 1)),
            AttackMechanic(55, setOf(MechanicPredicate.AttackerAbility(55)), MultiplyAttack(3, 2)),
            AttackMechanic(74, setOf(MechanicPredicate.AttackerAbility(74)), MultiplyAttack(2, 1)),
        )
    }
}
