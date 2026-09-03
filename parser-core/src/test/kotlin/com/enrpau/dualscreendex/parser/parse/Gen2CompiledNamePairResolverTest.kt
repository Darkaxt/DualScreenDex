package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2CompiledNamePairResolverTest {
    @Test
    fun resolvesAdjacentCompiledFarPointersByBothTableShapes() {
        val fixture = fixture(metadataOffset = 0x120, speciesRoot = 0x8000, moveRoot = 0xC000)

        val resolved = Gen2CompiledNamePairResolver.resolve(
            rom = RomImage(fixture),
            speciesCount = RECORD_COUNT,
            moveCount = RECORD_COUNT,
            codec = WesternPokemonTextCodecs.gen2Spanish,
            cancellation = ParserCancellationToken.NONE,
        )

        assertEquals(0x8000, resolved?.speciesNames?.offset)
        assertEquals(RECORD_COUNT, resolved?.speciesNames?.count)
        assertEquals(10, resolved?.speciesNames?.recordSize)
        assertEquals(0xC000, resolved?.moveNames?.offset)
        assertEquals(RECORD_COUNT, resolved?.moveNames?.count)
        assertEquals(true, resolved?.moveNames?.variableLength)
    }

    @Test
    fun rejectsAmbiguousCompiledNamePairs() {
        val first = fixture(metadataOffset = 0x120, speciesRoot = 0x8000, moveRoot = 0xC000)
        writeNamePair(first, metadataOffset = 0x180, speciesRoot = 0x10000, moveRoot = 0x14000)
        writeFixedNames(first, 0x10000)
        writeVariableNames(first, 0x14000)

        assertNull(
            Gen2CompiledNamePairResolver.resolve(
                rom = RomImage(first),
                speciesCount = RECORD_COUNT,
                moveCount = RECORD_COUNT,
                codec = WesternPokemonTextCodecs.gen2Spanish,
                cancellation = ParserCancellationToken.NONE,
            ),
        )
    }

    private fun fixture(metadataOffset: Int, speciesRoot: Int, moveRoot: Int): ByteArray =
        ByteArray(0x18000).also { bytes ->
            writeNamePair(bytes, metadataOffset, speciesRoot, moveRoot)
            writeFixedNames(bytes, speciesRoot)
            writeVariableNames(bytes, moveRoot)
        }

    private fun writeNamePair(bytes: ByteArray, metadataOffset: Int, speciesRoot: Int, moveRoot: Int) {
        writeFarPointer(bytes, metadataOffset, speciesRoot)
        writeFarPointer(bytes, metadataOffset + 3, moveRoot)
    }

    private fun writeFarPointer(bytes: ByteArray, offset: Int, target: Int) {
        val bank = target / 0x4000
        val address = 0x4000 + target % 0x4000
        bytes[offset] = bank.toByte()
        bytes[offset + 1] = address.toByte()
        bytes[offset + 2] = (address ushr 8).toByte()
    }

    private fun writeFixedNames(bytes: ByteArray, root: Int) {
        repeat(RECORD_COUNT) { index ->
            val name = "SPECIE${index % 10}"
            writeName(bytes, root + index * 10, name)
        }
    }

    private fun writeVariableNames(bytes: ByteArray, root: Int) {
        var cursor = root
        repeat(RECORD_COUNT) { index ->
            cursor = writeName(bytes, cursor, "MOVIMIENTO${index % 10}")
        }
    }

    private fun writeName(bytes: ByteArray, offset: Int, name: String): Int {
        name.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                in 'A'..'Z' -> (0x80 + character.code - 'A'.code).toByte()
                in '0'..'9' -> (0xF6 + character.code - '0'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        bytes[offset + name.length] = 0x50
        return offset + name.length + 1
    }

    private companion object {
        const val RECORD_COUNT = 24
    }
}
