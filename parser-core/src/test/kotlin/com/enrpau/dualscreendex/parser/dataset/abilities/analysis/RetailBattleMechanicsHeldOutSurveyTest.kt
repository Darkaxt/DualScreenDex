package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RetailBattleMechanicsHeldOutSurveyTest {
    @Test
    fun `one held-out ROM per retail family reports its structural mechanics stage`() {
        listOf(
            Control(
                name = "FRLG A Grand Day Out",
                path = "D:/Temp/dualdex-expanded-corpus/roms/0001-5fe542e89b10/A Grand Day Out.gba",
                sha256 = "2005275fc54ae63f3d1bc50c49980e87dcd9ecae5e4733d322bb2a2c99270916",
            ),
            Control(
                name = "Emerald All In",
                path = "D:/Temp/dualdex-expanded-corpus/roms/0009-5e9acef5d42e/All In (v1.0).gba",
                sha256 = "baf1bad15fd25fa8103d53021991bdadb64c142f8108efd29c14cd01ba069905",
            ),
            Control(
                name = "RS Dragonstone",
                path = "D:/Temp/dualdex-expanded-corpus/roms/0047-c91b401cbf5d/Dragonstone (v1.63).gba",
                sha256 = "2772296094b37c36ddf5735e58e54520bdde88a318c033e4817e40cc44676698",
            ),
        ).forEach { control ->
            val image = control.load()
            val parse = ParserOrchestrator.analyze(image)
            val layout = parse.probes.single { it.family == parse.selectedFamily }.resolvedLayout
            assertNotNull("${control.name} parser layout", layout)
            val datasets = requireNotNull(layout).resolvedDatasets
            val result = RetailBattleMechanicsResolver.resolve(
                RomAnalysisSession(image, RomHeaderReader.read(image)),
                requireNotNull(datasets.moveDetails) { "${control.name} move details" },
                requireNotNull(datasets.abilityNames) { "${control.name} ability names" }
                    .decodedDirectAbilityIds(),
            )
            assertTrue("${control.name}: $result", result is RetailBattleMechanicsResolution.Resolved)
            val resolved = (result as RetailBattleMechanicsResolution.Resolved).layout
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
            assertTrue(resolved.proof.callerEvidence.map { it.callSite }.distinct().size >= 2)
            assertTrue(
                resolved.proof.decodedCallSites.containsAll(
                    resolved.proof.callerEvidence.map { it.callSite },
                ),
            )
            assertTrue(resolved.proof.moveTableReferenceSites.isNotEmpty())
        }
    }

    private data class Control(
        val name: String,
        val path: String,
        val sha256: String,
    ) {
        fun load(): RomImage {
            val romPath = Path.of(path)
            assumeTrue("held-out ROM does not exist: $romPath", Files.isRegularFile(romPath))
            return RomImage(Files.readAllBytes(romPath)).also { assertEquals(sha256, it.sha256) }
        }
    }
}
