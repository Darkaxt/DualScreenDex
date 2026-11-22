package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedNamesWithEmptySlotsTest {
    private val codec = PokemonTextCodec.gbaEnglish

    @Test
    fun reservedEmptySlotsStayUncoveredInsideAProvenTable() {
        // Sovereign-shaped: an interior block of species without names, final slot populated.
        val evidence = validate(count = 400, empty = 240 until 330)

        assertTrue(evidence.reasons.joinToString(), evidence.compatible)
        assertEquals(310, evidence.validRecords)
        assertEquals(309, evidence.coveredRecords)
        assertEquals(399, evidence.expectedRecords)
        assertTrue(evidence.reasons.single().startsWith(TableValidators.PLACEHOLDER_NAMES_REASON))
    }

    @Test
    fun zeroTailIsNotAProvenExtent() {
        assertFalse(validate(count = 400, empty = 330 until 400).compatible)
    }

    @Test
    fun malformedSlotsBeyondOnePercentRejectTheTable() {
        assertFalse(validate(count = 400, empty = 240 until 330, malformed = 100 until 105).compatible)
        assertTrue(validate(count = 400, empty = 240 until 330, malformed = 100 until 104).compatible)
    }

    @Test
    fun mostlyEmptyTablesAreRejected() {
        assertFalse(validate(count = 400, empty = 100 until 301).compatible)
    }

    private fun validate(count: Int, empty: IntRange, malformed: IntRange = IntRange.EMPTY) =
        TableValidators.fixedNamesWithEmptySlots(rom(count, empty, malformed), OFFSET, count, WIDTH, codec)

    private fun rom(count: Int, empty: IntRange, malformed: IntRange): RomImage {
        val bytes = ByteArray(OFFSET + count * WIDTH + 0x100)
        repeat(count) { index ->
            val base = OFFSET + index * WIDTH
            when (index) {
                in empty -> Unit
                in malformed -> bytes.fill(0x7F, base, base + WIDTH)
                else -> {
                    "MON".forEachIndexed { at, character -> bytes[base + at] = (0xBB + character.code - 'A'.code).toByte() }
                    bytes[base + 3] = 0xFF.toByte()
                }
            }
        }
        return RomImage(bytes)
    }

    private companion object {
        const val OFFSET = 0x100
        const val WIDTH = 11
    }
}
