package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NatureCatalogMaterializationLiveRomTest {
    @Test
    fun `Modern Emerald materializes its ROM-native Nature catalog`() = assertMaterialized(controls[0])

    @Test
    fun `Unbound materializes its ROM-native Nature catalog`() = assertMaterialized(controls[1])

    @Test
    fun `Odyssey materializes its ROM-native Nature catalog`() = assertMaterialized(controls[2])

    private fun assertMaterialized(control: Control) {
        val path = Path.of(control.path)
        assumeTrue("missing ${control.path}", Files.isRegularFile(path))
        val rom = Files.newInputStream(path).use(RomImage::from)
        assertEquals(control.sha256, rom.sha256)

        val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
        val evidence = catalog.capabilities.getValue(RomCapability.NATURES)
        assertEquals(evidence.reasons.joinToString("; "), CapabilityStatus.AVAILABLE, evidence.status)
        assertEquals(25, catalog.naturesById.size)
        assertEquals((0 until 25).toSet(), catalog.naturesById.keys)
        assertTrue(catalog.naturesById.values.all { it.name.isNotBlank() })
        assertTrue(catalog.naturesById.values.all { it.flavorModifiers != null })
        assertTrue(catalog.naturesById.values.any { it.raisedStat != null && it.loweredStat != null })
        assertTrue(catalog.naturesById.values.all { it.positivePercent == 110 && it.negativePercent == 90 })
    }

    private data class Control(val path: String, val sha256: String)

    private val controls = listOf(
        Control(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
        ),
        Control(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
            "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
        ),
        Control(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
            "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
        ),
    )
}
