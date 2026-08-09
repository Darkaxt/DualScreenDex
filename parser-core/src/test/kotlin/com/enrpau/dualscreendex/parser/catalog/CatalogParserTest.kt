package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogParserTest {
    @Test
    fun materializerJoinsDexDescriptionToRomNativeSpeciesId() {
        val bytes = ByteArray(512) { 0xFF.toByte() }
        encodeGbaText(bytes, 0, "NONE")
        encodeGbaText(bytes, 11, "BULBA")
        val stats = 64
        repeat(6) { bytes[stats + 28 + it] = (40 + it).toByte() }
        bytes[stats + 34] = 12
        bytes[stats + 35] = 3
        val descriptions = 128
        encodeGbaText(bytes, descriptions + 32, "SEED")
        putGbaPointer(bytes, descriptions + 32 + 16, 400)
        encodeGbaText(bytes, 400, "A SEED")
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 2,
            moveCount = 0,
            tables = ProfileTables(
                speciesNames = TableLayout(0, 2, 11),
                baseStats = TableLayout(stats, 2, 28),
                descriptions = TableLayout(descriptions, 2, 32, pointerOffsets = listOf(16)),
            ),
        )
        val rom = RomImage(bytes)
        val analysis = ParseResult(
            header = RomHeader(Platform.GBA, "TEST", "TEST"),
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            size = rom.size,
            status = SelectionStatus.SELECTED,
            selectedFamily = EngineFamily.EMERALD,
            selectedProfile = null,
            runnerUpMargin = 20,
            probes = emptyList(),
            capabilities = emptyList(),
        )

        val catalog = CatalogMaterializer.materialize(rom, analysis, layout)

        assertEquals("BULBA", catalog.speciesById.getValue(1).name.value)
        assertEquals("A SEED", catalog.speciesById.getValue(1).description.value)
        assertEquals(1, catalog.speciesById.getValue(1).dexNumber.value)
    }

    @Test
    fun parserDoesNotInventCatalogWithoutSelectedFamily() {
        val result = CatalogParser.parse(RomImage(ByteArray(512)))

        assertEquals(SelectionStatus.NO_FAMILY_MATCH, result.analysis.status)
        assertNull(result.catalog)
        assertNull(result.layout)
    }

    private fun encodeGbaText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
