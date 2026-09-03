package com.enrpau.dualscreendex.companion.battle

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.defaultTextProjection
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DamageForecastRealControlTest {
    @Test
    fun `official first second and third generation forecasts consume real parsed move inputs`() {
        val controls = listOf(
            OfficialControl(
                "red",
                "DUALDEX_POKERED_ROM",
                "D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb",
                formula("decoded-first-generation", 217..255, 255, CriticalRule.LEVEL_DOUBLING),
            ),
            OfficialControl(
                "crystal",
                "DUALDEX_POKECRYSTAL_ROM",
                "D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc",
                formula("decoded-second-generation", 217..255, 255, CriticalRule.DAMAGE_MULTIPLIER),
            ),
            OfficialControl(
                "emerald",
                "DUALDEX_OFFICIAL_EMERALD_ROM",
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Emerald Version (USA, Europe).gba",
                formula("decoded-third-generation", 85..100, 100, CriticalRule.DAMAGE_MULTIPLIER),
            ),
        )

        controls.forEach { control ->
            val catalog = parse(control.path())
            val tackle = catalog.movesById.getValue(33)
            assertEquals(
                "${control.id} Tackle",
                "TACKLE",
                catalog.defaultTextProjection().moveName(tackle.id)?.uppercase(),
            )
            assertEquals("${control.id} Tackle power", 35, tackle.power.value)
            assertEquals("${control.id} Tackle type", 0, tackle.typeId.value)
            assertEquals("${control.id} Tackle category", MoveCategory.PHYSICAL, tackle.category.value)

            val result = DamageForecastCalculator.calculate(input(control.formula, tackle))
            assertTrue("${control.id} forecast must be available", result is DamageForecast.Available)
            assertEquals(InclusiveRange(21, 25), (result as DamageForecast.Available).damage)
        }
    }

    @Test
    fun `source backed hack controls reject altered or unavailable formula semantics`() {
        val controls = listOf(
            HackControl(
                "modern-emerald",
                "DUALDEX_MODERN_EMERALD_ROM",
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
                "D:/Temp/PokemonHacks/sources/Game Boy Advance/Modern Emerald/src/pokemon.c",
                "optionsDifficulty",
            ),
            HackControl(
                "unbound",
                "DUALDEX_UNBOUND_ROM",
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
                "D:/Temp/PokemonHacks/sources/Dynamic-Pokemon-Expansion-Unbound",
                null,
            ),
            HackControl(
                "odyssey",
                "DUALDEX_ODYSSEY_ROM",
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
                "D:/Temp/PokemonHacks/sources/Pokemon-Odyssey-Docs-App",
                null,
            ),
        )

        controls.forEach { control ->
            val path = control.path()
            val catalog = parse(path)
            assertTrue("${control.id} must provide parsed moves", catalog.movesById.isNotEmpty())
            assertTrue("${control.id} source control is missing", Files.exists(Path.of(control.sourcePath)))
            control.alteredSourceMarker?.let { marker ->
                assertTrue(
                    "${control.id} source must prove altered battle semantics",
                    Files.readString(Path.of(control.sourcePath)).contains(marker),
                )
            }

            // No decoded formula proof is supplied for these controls. In particular, sharing a
            // Gen III table shape must never cause the retail damage formula to be assumed.
            assertTrue(DamageForecastCalculator.calculate(null) is DamageForecast.Absent)
        }
    }

    private fun parse(path: Path): ParsedCatalog {
        assertTrue("real ROM control does not exist: $path", Files.isRegularFile(path))
        return assertNotNullCatalog(CatalogParser.parse(RomSourceLoader.load(path).rom).catalog, path)
    }

    private fun input(formula: DamageFormulaEvidence, move: MoveRecord) = DamageForecastInput(
        formula = formula,
        attacker = battler(),
        target = battler(),
        move = ForecastMove(
            id = move.id,
            typeId = requireNotNull(move.typeId.value),
            category = when (move.category.value) {
                MoveCategory.PHYSICAL -> ForecastMoveCategory.PHYSICAL
                MoveCategory.SPECIAL -> ForecastMoveCategory.SPECIAL
                else -> ForecastMoveCategory.STATUS
            },
            power = requireNotNull(move.power.value),
            accuracyPercent = normalizeAccuracy(requireNotNull(move.accuracy.value)),
        ),
        effectivenessPercent = 100,
        provenModifiers = listOf(
            ProvenDamageModifier(
                AppliedDamageCondition.STAB,
                3,
                2,
                SemanticProof.STRUCTURAL,
                "Same-type attack bonus",
            ),
        ),
    )

    private fun battler() = ForecastBattler(
        level = 50,
        currentHp = 100,
        maximumHp = 100,
        attack = 100,
        defense = 100,
        specialAttack = 100,
        specialDefense = 100,
        typeIds = listOf(0),
        status = ForecastStatus.NONE,
        abilityId = null,
        heldItemId = null,
    )

    private fun normalizeAccuracy(raw: Int): Int =
        if (raw <= 100) raw else ((raw * 100) + 127) / 255

    private fun formula(key: String, random: IntRange, denominator: Int, critical: CriticalRule) =
        DamageFormulaEvidence(
            key = key,
            proof = SemanticProof.CONTROL_VALIDATED,
            randomNumerators = random,
            randomDenominator = denominator,
            criticalRule = critical,
            criticalNumerator = 2,
            criticalDenominator = 1,
        )

    private fun assertNotNullCatalog(catalog: ParsedCatalog?, path: Path): ParsedCatalog {
        assertNotNull("catalog was not selected for $path", catalog)
        return requireNotNull(catalog)
    }

    private data class OfficialControl(
        val id: String,
        val environmentVariable: String,
        val fallback: String,
        val formula: DamageFormulaEvidence,
    ) {
        fun path(): Path = controlPath(environmentVariable, fallback)
    }

    private data class HackControl(
        val id: String,
        val environmentVariable: String,
        val fallback: String,
        val sourcePath: String,
        val alteredSourceMarker: String?,
    ) {
        fun path(): Path = controlPath(environmentVariable, fallback)
    }

    companion object {
        private fun controlPath(environmentVariable: String, fallback: String): Path {
            val configured = System.getenv(environmentVariable)?.takeIf(String::isNotBlank)
            val path = Path.of(configured ?: fallback)
            if (configured == null) {
                assumeTrue(
                    "set $environmentVariable to run this exact real-ROM control",
                    Files.isRegularFile(path),
                )
            } else {
                assertTrue("configured real ROM control does not exist: $path", Files.isRegularFile(path))
            }
            return path
        }
    }
}
