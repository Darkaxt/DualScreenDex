package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DamageFormulaPolicyRealControlTest {
    @Test
    fun `official third generation controls admit the validated formula while altered hack surfaces reject it`() {
        listOf(
            "DUALDEX_OFFICIAL_RUBY_ROM" to
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Ruby Version (USA, Europe) (Rev 2).gba",
            "DUALDEX_OFFICIAL_SAPPHIRE_ROM" to
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba",
            "DUALDEX_OFFICIAL_EMERALD_ROM" to
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Emerald Version (USA, Europe).gba",
            "DUALDEX_OFFICIAL_FIRERED_ROM" to
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
            "DUALDEX_OFFICIAL_LEAFGREEN_ROM" to
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba",
        ).forEach { (environment, fallback) ->
            assertNotNull(environment, DamageFormulaPolicy.resolve(catalog(environment, fallback)))
        }

        listOf(
            "DUALDEX_MODERN_EMERALD_ROM" to
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
            "DUALDEX_UNBOUND_ROM" to
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
            "DUALDEX_ODYSSEY_ROM" to
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
        ).forEach { (environment, fallback) ->
            assertNull(environment, DamageFormulaPolicy.resolve(catalog(environment, fallback)))
        }
    }

    private fun catalog(environment: String, fallback: String) =
        Path.of(System.getenv(environment) ?: fallback).let { path ->
            assertTrue("real ROM control does not exist: $path", Files.isRegularFile(path))
            requireNotNull(CatalogParser.parse(RomSourceLoader.load(path).rom).catalog)
        }
}
