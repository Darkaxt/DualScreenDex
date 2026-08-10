package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipMaterializersTest {
    @Test
    fun materializesGbaDescriptionDimensionsAndTextPointer() {
        val bytes = ByteArray(256)
        encodeGbaText(bytes, 0, "SEED")
        putU16(bytes, 12, 7)
        putU16(bytes, 14, 69)
        putGbaPointer(bytes, 16, 128)
        encodeGbaText(bytes, 128, "A STRANGE SEED")
        val layout = layout(
            descriptions = TableLayout(0, 1, 32, pointerOffsets = listOf(16)),
        )

        val result = RelationshipMaterializers.descriptions(RomImage(bytes), layout).getValue(0)

        assertEquals("A STRANGE SEED", result.text)
        assertEquals(7, result.height)
        assertEquals(69, result.weight)
    }

    @Test
    fun materializesGbaEvolutionSlotsAndPreservesRawMethod() {
        val bytes = ByteArray(128)
        putU16(bytes, 0, 42)
        putU16(bytes, 2, 99)
        putU16(bytes, 4, 2)
        val layout = layout(
            evolutions = TableLayout(0, 2, 16, elementSize = 8),
        )

        val edges = RelationshipMaterializers.evolutions(RomImage(bytes), layout).getValue(0)

        assertEquals(1, edges.size)
        assertEquals(EvolutionEdge(2, 42, 99, bytes.copyOfRange(0, 8)), edges.single())
    }

    @Test
    fun materializesPackedGbaLevelUpLearnset() {
        val bytes = ByteArray(256)
        putGbaPointer(bytes, 0, 128)
        putU16(bytes, 128, (5 shl 9) or 33)
        putU16(bytes, 130, (10 shl 9) or 45)
        putU16(bytes, 132, 0xFFFF)
        val layout = layout(
            learnsets = TableLayout(0, 1, 4),
        ).copy(moveCount = 100)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(0)

        assertEquals(listOf(LearnsetEntry(5, 33), LearnsetEntry(10, 45)), entries)
    }

    @Test
    fun materializesExpandedGbaLevelUpLearnset() {
        val bytes = ByteArray(256)
        putGbaPointer(bytes, 0, 128)
        putU16(bytes, 128, 600)
        bytes[130] = 5
        putU16(bytes, 131, 700)
        bytes[133] = 10
        putU16(bytes, 134, 0)
        bytes[136] = 0xFF.toByte()
        val layout = layout(
            learnsets = TableLayout(0, 1, 4, elementSize = 3),
        ).copy(moveCount = 800)

        val entries = RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(0)

        assertEquals(listOf(LearnsetEntry(5, 600), LearnsetEntry(10, 700)), entries)
    }

    @Test
    fun materializesCombinedGenTwoEvolutionAndLearnsetStream() {
        val bytes = ByteArray(0x8000)
        putU16(bytes, 0, 0x4020)
        val record = 0x4020
        bytes[record] = 1
        bytes[record + 1] = 16
        bytes[record + 2] = 2
        bytes[record + 3] = 0
        bytes[record + 4] = 5
        bytes[record + 5] = 33
        bytes[record + 6] = 0
        val table = TableLayout(0, 1, 2, variableLength = true, bank = 1)
        val layout = ResolvedRomLayout(
            family = EngineFamily.CRYSTAL,
            generation = 2,
            platform = Platform.GBC,
            speciesCount = 1,
            moveCount = 100,
            tables = ProfileTables(evolutions = table, learnsets = table),
        )

        assertEquals(
            listOf(EvolutionEdge(2, 1, 16, byteArrayOf(1, 16, 2))),
            RelationshipMaterializers.evolutions(RomImage(bytes), layout).getValue(1),
        )
        assertEquals(
            listOf(LearnsetEntry(5, 33)),
            RelationshipMaterializers.learnsets(RomImage(bytes), layout).getValue(1),
        )
    }

    @Test
    fun followsGenOneFarTextPointerForDescription() {
        val bytes = ByteArray(0x8000)
        putU16(bytes, 0, 0x4020)
        encodeGbText(bytes, 0x4020, "SEED")
        val metadata = 0x4020 + 5
        bytes[metadata + 4] = 0x17
        putU16(bytes, metadata + 5, 0x4060)
        bytes[metadata + 7] = 1
        encodeGbText(bytes, 0x4060, "A SEED")
        val layout = ResolvedRomLayout(
            family = EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 1,
            moveCount = 1,
            tables = ProfileTables(descriptions = TableLayout(0, 1, 2, bank = 1)),
        )

        val description = RelationshipMaterializers.descriptions(RomImage(bytes), layout).getValue(1)

        assertEquals("SEED", description.category)
        assertEquals("A SEED", description.text)
    }

    private fun layout(
        descriptions: TableLayout? = null,
        evolutions: TableLayout? = null,
        learnsets: TableLayout? = null,
    ) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 2,
        moveCount = 100,
        tables = ProfileTables(
            descriptions = descriptions,
            evolutions = evolutions,
            learnsets = learnsets,
        ),
    )

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

    private fun encodeGbText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0x7F
                in 'A'..'Z' -> (0x80 + char.code - 'A'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0x50
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
