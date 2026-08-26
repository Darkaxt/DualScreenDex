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
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
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
        assertEquals(81, capability.count)
        assertEquals(81, capability.coveredRecords)
        assertEquals(81, capability.expectedRecords)
        assertEquals(
            (1..81).toSet(),
            catalog.abilitiesById.filterValues { ability ->
                ability.mechanics.value.orEmpty().any { it.kind.name == "BEHAVIOR" }
            }.keys,
        )
        assertEquals(
            AbilityMechanic(
                AbilityMechanicKind.STAT_STAGE,
                "Opponents' Attack",
                "−1 stage on switch-in",
                -1,
                1,
                listOf(AbilityMechanicCondition(AbilityMechanicConditionKind.SWITCH_IN, 1, "Switch-in")),
            ),
            catalog.abilitiesById.getValue(22).mechanics.value?.single { it.kind == AbilityMechanicKind.STAT_STAGE },
        )
        assertEquals(
            AbilityMechanic(AbilityMechanicKind.MULTIPLIER, "Attack", "Attack ×1.5", 3, 2),
            catalog.abilitiesById.getValue(55).mechanics.value?.single { it.kind == AbilityMechanicKind.MULTIPLIER },
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
            catalog.abilitiesById.getValue(61).mechanics.value?.single { it.kind == AbilityMechanicKind.STATUS_CURE },
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
            catalog.abilitiesById.getValue(81).mechanics.value?.single { it.kind == AbilityMechanicKind.TYPE_CHANGE },
        )
        assertEquals(
            "Attack ×2",
            catalog.abilitiesById.getValue(37).mechanics.value?.single { it.kind == AbilityMechanicKind.MULTIPLIER }?.value,
        )
        val guts = catalog.abilitiesById.getValue(62).mechanics.value
            ?.single { it.kind == AbilityMechanicKind.MULTIPLIER }
        assertEquals("Attack ×1.5", guts?.value)
        assertEquals("While affected by status", guts?.conditions?.single()?.label)
        assertEquals(
            "Attack ×2",
            catalog.abilitiesById.getValue(74).mechanics.value?.single { it.kind == AbilityMechanicKind.MULTIPLIER }?.value,
        )
        assertEquals(
            listOf(AbilityMechanicCondition(AbilityMechanicConditionKind.ATTACKING_MOVE_TYPE, 4, "Ground moves")),
            catalog.abilitiesById.getValue(26).mechanics.value
                ?.single { it.kind == AbilityMechanicKind.MULTIPLIER }
                ?.conditions,
        )
    }

    @Test
    fun `all five official retail catalogs publish behavior plus validated numeric mechanics`() {
        listOf(
            Control(
                "Ruby",
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Ruby Version (USA, Europe) (Rev 2).gba",
                "0fdd36e92b75bed65d09df4635ab0b707b288c2bf1dc4c6e7a4a4f0eebe9d64c",
            ),
            Control(
                "Sapphire",
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba",
                "02ca41513580a8b780989dee428df747b52a0b1a55bec617886b4059eb1152fb",
            ),
            Control(
                "Emerald",
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Emerald Version (USA, Europe).gba",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
            ),
            Control(
                "FireRed",
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
            ),
            Control(
                "LeafGreen",
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba",
                "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
            ),
        ).forEach { control ->
            val loaded = control.load()
            val rom = loaded.rom
            val layout = loaded.layout
            val abilities = RecordMaterializers.abilities(rom, layout)
            val catalog = CatalogMaterializer.materialize(rom, loaded.parse, layout)
            val result = requireNotNull(AbilityMechanicsMaterializer.materialize(rom, layout, abilities))

            val capability = catalog.capabilities.getValue(RomCapability.ABILITY_MECHANICS)
            assertEquals("${control.name}: ${capability.reasons}", CapabilityStatus.AVAILABLE, capability.status)
            assertEquals(control.name, 77, capability.count)
            assertEquals(control.name, 77, capability.coveredRecords)
            assertEquals(control.name, 77, capability.expectedRecords)
            assertEquals(control.name, (1..77).toSet(), result.mechanicsByAbility.keys)
            assertEquals(
                control.name,
                (1..77).toSet(),
                result.mechanicsByAbility.filterValues { mechanics ->
                    mechanics.any { it.kind.name == "BEHAVIOR" }
                }.keys,
            )
            assertEquals(
                control.name,
                "Defined but inactive in this engine",
                result.mechanicsByAbility.getValue(76).single { it.kind.name == "BEHAVIOR" }.value,
            )

            assertEquals(
                listOf(AbilityMechanic(AbilityMechanicKind.MULTIPLIER, "Attack", "Attack ×2", 2, 1)),
                result.mechanicsByAbility.getValue(37).filterNot { it.kind.name == "BEHAVIOR" },
            )
            assertEquals(
                result.mechanicsByAbility.getValue(37).filterNot { it.kind.name == "BEHAVIOR" },
                result.mechanicsByAbility.getValue(74).filterNot { it.kind.name == "BEHAVIOR" },
            )
            assertEquals(
                listOf(
                    AbilityMechanic(AbilityMechanicKind.ACTIVATION_THRESHOLD, "Activation", "HP ≤ 1/3", 1, 3),
                    AbilityMechanic(AbilityMechanicKind.MULTIPLIER, "Power", "Grass move power ×1.5", 3, 2),
                ),
                result.mechanicsByAbility.getValue(65).filterNot { it.kind.name == "BEHAVIOR" },
            )
            assertEquals(
                control.name,
                setOf(10, 11, 18, 26, 37, 47, 65, 66, 67, 68, 74),
                result.mechanicsByAbility.filterValues { mechanics ->
                    mechanics.any { it.kind.name != "BEHAVIOR" }
                }.keys,
            )
            assertEquals(
                control.name,
                listOf(AbilityMechanicCondition(AbilityMechanicConditionKind.ATTACKING_MOVE_TYPE, 4, "Ground moves")),
                catalog.abilitiesById.getValue(26).mechanics.value
                    ?.single { it.kind == AbilityMechanicKind.MULTIPLIER }
                    ?.conditions,
            )
        }
    }

    @Test
    fun `normal catalog path withholds analyzer-only Classic and explicit-ABI-only Clover`() {
        listOf(
            Control(
                "Classic",
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0029-a5f22adc2c2f/Classic (v1.5.0b).gba",
                "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
            ) to CapabilityStatus.NOT_FOUND,
            Control(
                "Clover",
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0033-ae1f81f2f6ea/Clover (v1.3.3).gba",
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
