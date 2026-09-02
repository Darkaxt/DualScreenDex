package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.resolvedLanguageManifest
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class EncounterReferencedSpeciesClosureTest {
    @Test
    fun closesPositiveEncounterIdsOnlyThroughTheirDecodedGen3NameRows() {
        val bytes = ByteArray(0x200)
        writeName(bytes, 0x40, 0, "NONE")
        writeName(bytes, 0x40, 1, "EXISTING")
        writeName(bytes, 0x40, 2, "REALMON")
        val existing = species(1)
        val encounters = listOf(
            EncounterArea(
                id = 1,
                name = CatalogField.available("Route"),
                methodId = 1,
                slots = listOf(
                    EncounterSlot(0, 2, 2, 10),
                    EncounterSlot(1, 3, 3, 10),
                    EncounterSlot(2, 4, 4, 10),
                    EncounterSlot(9, 5, 5, 10),
                ),
            ),
        )

        val closed = EncounterReferencedSpeciesClosure.close(
            rom = RomImage(bytes),
            layout = ResolvedRomLayout(
                family = EngineFamily.EMERALD,
                generation = 3,
                platform = Platform.GBA,
                speciesCount = 3,
                moveCount = 0,
                tables = ProfileTables(speciesNames = TableLayout(0x40, 3, 11)),
                languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
            ),
            namesStatus = CapabilityStatus.PARTIAL,
            species = mapOf(1 to existing),
            encounters = encounters,
        )

        assertEquals(setOf(1, 2), closed.keys)
        assertSame(existing, closed.getValue(1))
        assertEquals("REALMON", closed.getValue(2).name.value)
        assertEquals(CapabilityStatus.NOT_FOUND, closed.getValue(2).dexNumber.status)
        assertEquals(CapabilityStatus.NOT_FOUND, closed.getValue(2).baseStats.status)
        assertFalse(0 in closed)
        assertFalse(9 in closed)
    }

    private fun species(id: Int) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(id),
        name = CatalogField.available("EXISTING"),
        typeIds = CatalogField.notFound("fixture"),
        baseStats = CatalogField.notFound("fixture"),
        sprite = CatalogField.notFound("fixture"),
    )

    private fun writeName(bytes: ByteArray, offset: Int, id: Int, name: String) {
        val record = offset + id * 11
        name.forEachIndexed { index, character -> bytes[record + index] = gba(character) }
        bytes[record + name.length] = 0xFF.toByte()
    }

    private fun gba(character: Char): Byte = when (character) {
        in 'A'..'Z' -> (0xBB + character.code - 'A'.code).toByte()
        else -> error("unsupported fixture character $character")
    }
}
