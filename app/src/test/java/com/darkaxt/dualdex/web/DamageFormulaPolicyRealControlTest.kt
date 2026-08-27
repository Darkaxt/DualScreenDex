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
    fun `official Emerald admits decoded retail formula while altered hack surfaces reject it`() {
        val official = catalog(
            "DUALDEX_OFFICIAL_EMERALD_ROM",
            "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Emerald Version (USA, Europe).gba",
        )
        assertNotNull(DamageFormulaPolicy.resolve(official))

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
