package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
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

class RetailPatchedVeneerMechanicsLiveRomTest {
    @Test
    fun `literal loaded BX patch remains part of the typed semantic routine`() {
        val image = loadControl()
        val parsed = ParserOrchestrator.analyze(image)
        val layout = parsed.probes.single { it.family == parsed.selectedFamily }.resolvedLayout!!
        val moves = layout.resolvedDatasets.moveDetails!!
        val abilityIds = layout.resolvedDatasets.abilityNames!!.decodedDirectAbilityIds()

        val result = resolve(image, moves, abilityIds)

        assertTrue(result.toString(), result is RetailBattleMechanicsResolution.Resolved)
        val resolved = (result as RetailBattleMechanicsResolution.Resolved).layout
        assertEquals(0x6957C, resolved.routineEntry)
        assertEquals(0x58, resolved.abi.record.stride)
        assertEquals(ScalarField(0x02, ScalarWidth.U16), resolved.abi.record.attack)
        assertEquals(ScalarField(0x20, ScalarWidth.U8), resolved.abi.record.ability)
        assertEquals(expectedRetailMechanics, resolved.mechanics.toSet())
        assertTrue(resolved.proof.literalVeneerSites.containsAll(setOf(0x6960C, 0xBD65DE)))

        val corrupted = image.slice(0, image.size)
        val entryLoad = (ThumbDecoder.decode(image, resolved.proof.literalVeneerSites.min()) as Arm7DecodeResult.Decoded)
            .instruction as Arm7MemoryTransfer
        val literalOffset = (entryLoad.address as Arm7Address.PcRelative).resolvedAddress.toInt()
        repeat(4) { corrupted[literalOffset + it] = 0 }
        assertTrue(
            "a corrupted literal veneer must fail closed",
            resolve(RomImage(corrupted), moves, abilityIds) !is RetailBattleMechanicsResolution.Resolved,
        )
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

    private fun loadControl(): RomImage {
        val path = Path.of(
            "D:/Temp/dualdex-expanded-corpus/roms/0020-520ba69bb172/Blazing Emerald (v1.6).gba",
        )
        assumeTrue("Blazing Emerald control does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also {
            assertEquals("2ff14043118132e9816fac3f20b3a85011b3e8ac5361a0499264dbebe4f096dc", it.sha256)
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
    }
}
