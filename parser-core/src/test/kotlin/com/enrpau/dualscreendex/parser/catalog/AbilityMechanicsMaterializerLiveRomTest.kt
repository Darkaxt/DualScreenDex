package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleMechanicsAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleRecordAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleRoleContract
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MoveMechanicsAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarField
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarWidth
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class AbilityMechanicsMaterializerLiveRomTest {
    @Test
    fun `catalog materializer consumes a parser supplied typed ABI`() {
        val (rom, rawLayout) = Control(
            "Clover",
            "D:/Temp/dualdex-expanded-corpus/roms/0033-ae1f81f2f6ea/Clover (v1.3.3).gba",
            "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
        ).load()
        val datasets = rawLayout.resolvedDatasets
        val moves = requireNotNull(datasets.moveDetails)
        val abilityIds = requireNotNull(datasets.abilityNames).decodedDirectAbilityIds()
        val selectedAbi = BattleMechanicsAbi(
            record = BattleRecordAbi(
                stride = 0x58,
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
        val layout = rawLayout.copy(
            resolvedDatasets = ResolvedDatasetLayouts(
                typeChart = datasets.typeChart,
                descriptions = datasets.descriptions,
                evolutions = datasets.evolutions,
                learnsets = datasets.learnsets,
                moveDetails = moves,
                abilityNames = datasets.abilityNames,
                battleMechanicsAbi = selectedAbi,
            ),
        )

        val result = AbilityMechanicsMaterializer.materialize(
            rom,
            layout,
            RecordMaterializers.abilities(rom, layout),
        )

        assertEquals(setOf(37, 55, 74), result?.mechanicsByAbility?.keys)
        assertEquals("Attack ×1.5", result?.mechanicsByAbility?.get(55)?.single()?.value)
    }

    @Test
    fun `official retail catalog publishes the semantic proof and truthful capability`() {
        val (rom, layout) = Control(
            "Emerald",
            "D:/Temp/dualdex-official-roms/Pokemon - Emerald Version (USA, Europe).gba",
            "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
        ).load()
        val analysis = ParserOrchestrator.analyze(rom)

        val catalog = CatalogMaterializer.materialize(rom, analysis, layout)

        val capability = catalog.capabilities.getValue(RomCapability.ABILITY_MECHANICS)
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(2, capability.count)
        assertEquals(
            listOf(AbilityMechanic(AbilityMechanicKind.MULTIPLIER, "Attack", "Attack ×2", 2, 1)),
            catalog.abilitiesById.getValue(37).mechanics.value,
        )
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.abilitiesById.getValue(65).mechanics.status)
    }

    @Test
    fun `official retail catalogs expose only semantically proven attack mechanics`() {
        listOf(
            Control(
                "Emerald",
                "D:/Temp/dualdex-official-roms/Pokemon - Emerald Version (USA, Europe).gba",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
            ),
            Control(
                "FireRed",
                "D:/Temp/dualdex-official-roms/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
            ),
        ).forEach { control ->
            val (rom, layout) = control.load()
            val abilities = RecordMaterializers.abilities(rom, layout)
            val result = AbilityMechanicsMaterializer.materialize(rom, layout, abilities)

            assertEquals("${control.name}: ${result?.mechanicsByAbility}", setOf(37, 74), result?.mechanicsByAbility?.keys)
            assertEquals(
                listOf(AbilityMechanic(AbilityMechanicKind.MULTIPLIER, "Attack", "Attack ×2", 2, 1)),
                result?.mechanicsByAbility?.get(37),
            )
            assertEquals(result?.mechanicsByAbility?.get(37), result?.mechanicsByAbility?.get(74))
        }
    }

    @Test
    fun `normal catalog path withholds analyzer-only Classic and explicit-ABI-only Clover`() {
        listOf(
            Control(
                "Classic",
                "D:/Temp/dualdex-expanded-corpus/roms/0029-a5f22adc2c2f/Classic (v1.5.0b).gba",
                "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
            ),
            Control(
                "Clover",
                "D:/Temp/dualdex-expanded-corpus/roms/0033-ae1f81f2f6ea/Clover (v1.3.3).gba",
                "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
            ),
        ).forEach { control ->
            val (rom, layout) = control.load()
            val abilities = RecordMaterializers.abilities(rom, layout)
            assertNull(
                "${control.name} requires a normal production ABI proof",
                AbilityMechanicsMaterializer.materialize(rom, layout, abilities),
            )
        }
    }

    private data class Control(val name: String, val path: String, val sha256: String) {
        fun load(): Pair<RomImage, com.enrpau.dualscreendex.parser.model.ResolvedRomLayout> {
            val romPath = Path.of(path)
            assumeTrue("live ROM does not exist: $romPath", Files.isRegularFile(romPath))
            val rom = RomImage(Files.readAllBytes(romPath))
            assertEquals(sha256, rom.sha256)
            val parse = ParserOrchestrator.analyze(rom)
            val layout = parse.probes.single { it.family == parse.selectedFamily }.resolvedLayout
            return rom to requireNotNull(layout) { "${name} parser layout" }
        }
    }
}
