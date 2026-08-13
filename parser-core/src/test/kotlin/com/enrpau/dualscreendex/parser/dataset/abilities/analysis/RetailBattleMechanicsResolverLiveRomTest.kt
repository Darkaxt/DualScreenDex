package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7ControlEffect
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RetailBattleMechanicsResolverLiveRomTest {
    @Test
    fun `official Emerald and FRLG derive one typed retail battle routine from compiled roles`() {
        listOf(
            Control(
                path = "D:/Temp/dualdex-official-roms/Pokemon - Emerald Version (USA, Europe).gba",
                sha256 = "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                routineEntry = 0x6957C,
                callerSites = setOf(0x420A4, 0x45FAE, 0x46DD0, 0x46EC6, 0x5086A, 0x54658, 0x17F032),
            ),
            Control(
                path = "D:/Temp/dualdex-official-roms/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
                sha256 = "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                routineEntry = 0x3ED00,
                callerSites = setOf(0x198A4, 0x1D72A, 0x1E5F4, 0x1E6EA, 0x27A5E, 0x2B89A),
            ),
        ).forEach { control ->
            val image = control.load()
            val parse = ParserOrchestrator.analyze(image)
            val layout = parse.probes.single { it.family == parse.selectedFamily }.resolvedLayout!!
            val moves = layout.resolvedDatasets.moveDetails!!
            val abilityIds = layout.resolvedDatasets.abilityNames!!.decodedDirectAbilityIds()
            val result = RetailBattleMechanicsResolver.resolve(
                RomAnalysisSession(image, RomHeaderReader.read(image)),
                moves,
                abilityIds,
            )
            assertTrue(result.toString(), result is RetailBattleMechanicsResolution.Resolved)
            val resolved = (result as RetailBattleMechanicsResolution.Resolved).layout
            assertEquals(control.routineEntry, resolved.routineEntry)
            assertEquals(0x58, resolved.abi.record.stride)
            assertEquals(ScalarField(0x02, ScalarWidth.U16), resolved.abi.record.attack)
            assertEquals(ScalarField(0x20, ScalarWidth.U8), resolved.abi.record.ability)
            assertEquals(control.callerSites, resolved.proof.decodedCallSites.toSet())
            assertTrue(control.callerSites.containsAll(resolved.proof.callerEvidence.map { it.callSite }))
            assertTrue(resolved.proof.callerEvidence.map { it.callSite }.distinct().size >= 2)
            assertTrue(resolved.proof.callerEvidence.map { it.callerLineageEntry }.distinct().size >= 2)
            assertTrue(
                "at least one role proof must preserve callee-saved provenance across a decoded call",
                resolved.proof.callerEvidence.any { evidence ->
                    (evidence.callerLineageEntry until evidence.callSite step 2).any { offset ->
                        val decoded = ThumbDecoder.decode(image, offset) as? Arm7DecodeResult.Decoded
                        decoded?.instruction?.controlEffect is Arm7ControlEffect.Call
                    }
                },
            )
            assertTrue(resolved.proof.moveTableReferenceSites.isNotEmpty())
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
        }
    }

    private data class Control(
        val path: String,
        val sha256: String,
        val routineEntry: Int,
        val callerSites: Set<Int>,
    ) {
        fun load(): RomImage {
            val romPath = Path.of(path)
            assumeTrue("live ROM does not exist: $romPath", Files.isRegularFile(romPath))
            return RomImage(Files.readAllBytes(romPath)).also { assertEquals(sha256, it.sha256) }
        }
    }
}
