package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.parse.GbaPublishedHeaderResolver
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class AbilityMechanicsMaterializerLiveRomTest {
    @Test
    fun `Modern Emerald publishes every source and binary normalized ability mechanic`() {
        val loaded = Control(
            "Modern Emerald",
            "D:/Temp/dualdex-expanded-corpus/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
        ).load()

        val catalog = CatalogMaterializer.materialize(loaded.rom, loaded.parse, loaded.layout)

        assertEquals(0x67F958, GbaPublishedHeaderResolver.resolve(loaded.rom).abilityDescriptions)
        val descriptions = catalog.capabilities.getValue(RomCapability.ABILITY_DESCRIPTIONS)
        assertEquals(CapabilityStatus.AVAILABLE, descriptions.status)
        assertEquals(81, descriptions.count)
        assertEquals("Helps repel wild Pokémon.", catalog.abilitiesById.getValue(1).description.value)
        assertEquals("Normal moves become Fairy.", catalog.abilitiesById.getValue(81).description.value)

        val capability = catalog.capabilities.getValue(RomCapability.ABILITY_MECHANICS)
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(7, capability.count)
        assertEquals(
            AbilityMechanic(
                AbilityMechanicKind.STAT_STAGE,
                "Opponents' Attack",
                "−1 stage on switch-in",
                -1,
                1,
                listOf(AbilityMechanicCondition(AbilityMechanicConditionKind.SWITCH_IN, 1, "Switch-in")),
            ),
            catalog.abilitiesById.getValue(22).mechanics.value?.single(),
        )
        assertEquals(
            AbilityMechanic(AbilityMechanicKind.MULTIPLIER, "Attack", "Attack ×1.5", 3, 2),
            catalog.abilitiesById.getValue(55).mechanics.value?.single(),
        )
        assertEquals(
            AbilityMechanic(
                AbilityMechanicKind.STATUS_CURE,
                "Nonvolatile status",
                "1/3 chance to cure",
                1,
                3,
                listOf(AbilityMechanicCondition(
                    AbilityMechanicConditionKind.ATTACKER_STATUS_NON_ZERO,
                    0xFFFF_FFFFL,
                    "While affected by status",
                )),
            ),
            catalog.abilitiesById.getValue(61).mechanics.value?.single(),
        )
        assertEquals(
            AbilityMechanic(
                AbilityMechanicKind.TYPE_CHANGE,
                "Move type",
                "Normal → Fairy",
                1,
                1,
                listOf(AbilityMechanicCondition(
                    AbilityMechanicConditionKind.MOVE_POWER_NON_ZERO,
                    1,
                    "Damaging Normal-type moves",
                )),
            ),
            catalog.abilitiesById.getValue(81).mechanics.value?.single(),
        )
        assertEquals("Attack ×2", catalog.abilitiesById.getValue(37).mechanics.value?.single()?.value)
        val guts = catalog.abilitiesById.getValue(62).mechanics.value?.single()
        assertEquals("Attack ×1.5", guts?.value)
        assertEquals("While affected by status", guts?.conditions?.single()?.label)
        assertEquals("Attack ×2", catalog.abilitiesById.getValue(74).mechanics.value?.single()?.value)
    }

    @Test
    fun `official retail catalog publishes the semantic proof and truthful capability`() {
        val loaded = Control(
            "Emerald",
            "D:/Temp/dualdex-official-roms/Pokemon - Emerald Version (USA, Europe).gba",
            "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
        ).load()

        val catalog = CatalogMaterializer.materialize(loaded.rom, loaded.parse, loaded.layout)

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
            val loaded = control.load()
            val rom = loaded.rom
            val layout = loaded.layout
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
            ) to CapabilityStatus.NOT_FOUND,
            Control(
                "Clover",
                "D:/Temp/dualdex-expanded-corpus/roms/0033-ae1f81f2f6ea/Clover (v1.3.3).gba",
                "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
            ) to CapabilityStatus.AMBIGUOUS,
        ).forEach { (control, expectedStatus) ->
            val loaded = control.load()
            val rom = loaded.rom
            val layout = loaded.layout
            val abilities = RecordMaterializers.abilities(rom, layout)
            val evidence = loaded.parse.capabilities.single {
                it.capability == RomCapability.ABILITY_MECHANICS
            }
            assertEquals(expectedStatus, evidence.status)
            assertNull(
                "${control.name} requires a normal production ABI proof",
                AbilityMechanicsMaterializer.materialize(rom, layout, abilities),
            )
        }
    }

    private data class Control(val name: String, val path: String, val sha256: String) {
        fun load(): LoadedControl {
            val romPath = Path.of(path)
            assumeTrue("live ROM does not exist: $romPath", Files.isRegularFile(romPath))
            val rom = RomImage(Files.readAllBytes(romPath))
            assertEquals(sha256, rom.sha256)
            val parse = ParserOrchestrator.analyze(rom)
            val layout = parse.probes.single { it.family == parse.selectedFamily }.resolvedLayout
            return LoadedControl(rom, parse, requireNotNull(layout) { "${name} parser layout" })
        }
    }

    private data class LoadedControl(
        val rom: RomImage,
        val parse: com.enrpau.dualscreendex.parser.model.ParseResult,
        val layout: com.enrpau.dualscreendex.parser.model.ResolvedRomLayout,
    )
}
