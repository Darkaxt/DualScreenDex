package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PokeemeraldExpansionResolverTest {
    @Test
    fun resolvesPublishedCountsPointersAndValidatedRecordShapes() {
        val bytes = fixture()

        val resolved = PokeemeraldExpansionResolver.resolve(RomImage(bytes))

        assertNotNull(resolved)
        requireNotNull(resolved)
        assertEquals(20, resolved.speciesCount)
        assertEquals(16, resolved.moveCount)
        assertEquals(8, resolved.abilityCount)
        assertEquals(0x1000 + 44, resolved.tables.speciesNames?.offset)
        assertEquals(180, resolved.tables.speciesNames?.stride)
        assertEquals(0x5000, resolved.tables.moveNames?.offset)
        assertEquals(64, resolved.tables.moveNames?.stride)
        assertEquals(true, resolved.tables.moveNames?.valuesArePointers)
        assertEquals(0x7000, resolved.tables.abilities?.offset)
        assertEquals(28, resolved.tables.abilities?.stride)
        assertEquals(1, resolved.firstRegisters.speciesNationalDex)
        assertEquals("BULBA", resolved.firstRegisters.speciesName)
        assertEquals("POUND", resolved.firstRegisters.moveName)
        assertEquals("STENCH", resolved.firstRegisters.abilityName)
    }

    private fun fixture(): ByteArray {
        val bytes = ByteArray(0x9000)
        writeAscii(bytes, 0x108, "pokemon emerald version")
        writePointer(bytes, 0x1BC, 0x1000)
        writePointer(bytes, 0x1CC, 0x5000)

        writeAscii(bytes, 0x204, "RHHEXP")
        bytes[0x20A] = 1
        bytes[0x20B] = 15
        bytes[0x20C] = 3
        writeU16(bytes, 0x20E, 16)
        writeU16(bytes, 0x210, 20)
        writeU16(bytes, 0x212, 8)
        writePointer(bytes, 0x214, 0x7000)

        repeat(20) { id ->
            val base = 0x1000 + id * 180
            if (id > 0) {
                repeat(6) { bytes[base + it] = (40 + id).toByte() }
                bytes[base + 6] = 12
                bytes[base + 7] = 3
                bytes[base + 21] = 4
                writeU16(bytes, base + 24, 65)
                encodeGba(bytes, base + 31, "SEED")
                encodeGba(bytes, base + 44, if (id == 1) "BULBA" else "MON")
                writeU16(bytes, base + 60, id)
                writeU16(bytes, base + 62, 7)
                writeU16(bytes, base + 64, 69)
                writePointer(bytes, base + 76, 0x8000)
                writePointer(bytes, base + 88, 0x8100)
                writePointer(bytes, base + 96, 0x8200)
                writePointer(bytes, base + 148, 0x8300)
                writePointer(bytes, base + 152, 0x8400)
                writePointer(bytes, base + 156, 0x8500)
                writePointer(bytes, base + 160, 0x8600)
            }
        }

        repeat(16) { id ->
            val base = 0x5000 + id * 64
            writePointer(bytes, base, 0x6000 + id * 16)
            writePointer(bytes, base + 4, 0x6500)
            encodeGba(bytes, 0x6000 + id * 16, if (id == 1) "POUND" else if (id == 0) "NONE" else "MOVE")
            val type = if (id == 2) 19 else 1
            writeU16(bytes, base + 10, type or (0 shl 5) or (40 shl 7))
            writeU16(bytes, base + 12, 100)
            bytes[base + 14] = 35
        }

        repeat(8) { id ->
            val base = 0x7000 + id * 28
            encodeGba(bytes, base, if (id == 1) "STENCH" else if (id == 0) "NONE" else "ABILITY")
            writePointer(bytes, base + 20, 0x6500)
        }
        encodeGba(bytes, 0x6500, "DESCRIPTION")
        encodeGba(bytes, 0x8000, "A SEED POKEMON")
        repeat(20 * 20) { index -> writeU32(bytes, 0x8800 + index * 4, 4096) }
        writeU32(bytes, 0x8800 + (1 * 20 + 6) * 4, 2048)
        writeU32(bytes, 0x8800 + (1 * 20 + 8) * 4, 0)
        writeU32(bytes, 0x8800 + (2 * 20 + 1) * 4, 8192)
        return bytes
    }

    private fun encodeGba(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                ' ' -> 0
                else -> error("unsupported fixture character $char")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun writeAscii(target: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(target, offset)
    }

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun writePointer(target: ByteArray, offset: Int, romOffset: Int) {
        val value = 0x08000000 + romOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun writeU32(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
