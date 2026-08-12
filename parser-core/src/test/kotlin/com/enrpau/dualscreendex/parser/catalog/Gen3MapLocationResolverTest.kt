package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3MapLocationResolverTest {
    @Test
    fun resolvesEveryMapNameFromTheEncounterProvenMapGroupsAndRegionTable() {
        val bytes = ByteArray(0x1000)
        putPointer(bytes, 0x20C, 0x200)
        putPointer(bytes, 0x210, 0x208)
        putPointer(bytes, 0x200, 0x300)
        putPointer(bytes, 0x204, 0x31C)
        putPointer(bytes, 0x208, 0x338)
        writeMapHeader(bytes, 0x300, 0)
        writeMapHeader(bytes, 0x31C, 1)
        writeMapHeader(bytes, 0x338, 2)
        writeRegionEntry(bytes, 0x600, 0, 0x700, "Oldale Town")
        writeRegionEntry(bytes, 0x600, 1, 0x720, "Route 101")
        writeRegionEntry(bytes, 0x600, 2, 0x740, "Littleroot Town")
        putPointer(bytes, 0x900, 0x600)

        val names = Gen3MapLocationResolver.resolve(
            RomImage(bytes),
            setOf(0x0000, 0x0001, 0x0100),
        )

        assertEquals("Oldale Town", names[0x0000])
        assertEquals("Route 101", names[0x0001])
        assertEquals("Littleroot Town", names[0x0100])
        assertEquals(3, names.size)
    }

    @Test
    fun returnsNoNamesWhenEncounterKeysDoNotProveOneMapGroupsRoot() {
        assertTrue(Gen3MapLocationResolver.resolve(RomImage(ByteArray(0x400)), setOf(0x0000)).isEmpty())
    }

    private fun writeMapHeader(bytes: ByteArray, offset: Int, regionSection: Int) {
        putPointer(bytes, offset, 0x500)
        putPointer(bytes, offset + 4, 0x520)
        putPointer(bytes, offset + 8, 0x540)
        putU16(bytes, offset + 0x12, 1)
        bytes[offset + 0x14] = regionSection.toByte()
    }

    private fun writeRegionEntry(bytes: ByteArray, root: Int, index: Int, textOffset: Int, text: String) {
        val offset = root + index * 8
        bytes[offset] = 1
        bytes[offset + 1] = 1
        bytes[offset + 2] = 1
        bytes[offset + 3] = 1
        putPointer(bytes, offset + 4, textOffset)
        text.forEachIndexed { characterIndex, character ->
            bytes[textOffset + characterIndex] = when (character) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + character.code - 'A'.code).toByte()
                in 'a'..'z' -> (0xD5 + character.code - 'a'.code).toByte()
                in '0'..'9' -> (0xA1 + character.code - '0'.code).toByte()
                else -> error("unsupported fixture character $character")
            }
        }
        bytes[textOffset + text.length] = 0xFF.toByte()
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) = putU32(bytes, offset, 0x08000000 + target)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
