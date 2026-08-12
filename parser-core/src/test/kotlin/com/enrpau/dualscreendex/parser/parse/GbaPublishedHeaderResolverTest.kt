package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GbaPublishedHeaderResolverTest {
    @Test
    fun resolvesCompactGfHeaderPointerBlock() {
        val bytes = ByteArray(0x20000)
        val roots = listOf(0x8000, 0x9000, 0xA000, 0xB000, 0xC000, 0xD000, 0xE000)
        roots.forEachIndexed { index, target -> writePointer(bytes, 0x1AC + index * 4, target) }

        val resolved = GbaPublishedHeaderResolver.resolve(RomImage(bytes))

        assertEquals(0x8000, resolved.baseStats)
        assertEquals(0x9000, resolved.abilities)
        assertEquals(0xA000, resolved.abilityDescriptions)
        assertEquals(0xC000, resolved.moveData)
    }

    @Test
    fun resolvesStandardGfHeaderPointerBlock() {
        val bytes = ByteArray(0x24000)
        val roots = listOf(0x10000, 0x11000, 0x12000, 0x13000, 0x14000, 0x15000, 0x16000)
        roots.forEachIndexed { index, target -> writePointer(bytes, 0x1BC + index * 4, target) }

        val resolved = GbaPublishedHeaderResolver.resolve(RomImage(bytes))

        assertEquals(0x10000, resolved.baseStats)
        assertEquals(0x11000, resolved.abilities)
        assertEquals(0x12000, resolved.abilityDescriptions)
        assertEquals(0x14000, resolved.moveData)
    }

    @Test
    fun resolvesFreeSeenFlagsGfHeaderPointerBlock() {
        val bytes = ByteArray(0x22000)
        val roots = listOf(0xE000, 0xF000, 0x10000, 0x11000, 0x12000, 0x13000, 0x14000)
        roots.forEachIndexed { index, target -> writePointer(bytes, 0x1B4 + index * 4, target) }

        val resolved = GbaPublishedHeaderResolver.resolve(RomImage(bytes))

        assertEquals(0xE000, resolved.baseStats)
        assertEquals(0xF000, resolved.abilities)
        assertEquals(0x10000, resolved.abilityDescriptions)
        assertEquals(0x12000, resolved.moveData)
    }

    @Test
    fun prefersSemanticallyCoherentBlockWhenAllPublishedWindowsAreComplete() {
        val bytes = ByteArray(0x40000)
        val speciesNames = 0x3000
        val moveNames = 0x4000
        val stats = 0x8000
        val moves = 0xC000
        val speciesCount = 40
        val moveCount = 50
        writePointer(bytes, 0x144, speciesNames)
        writePointer(bytes, 0x148, moveNames)
        repeat(speciesCount) { id -> encodeGbaName(bytes, speciesNames + id * 11, "MON") }
        repeat(moveCount) { id -> encodeGbaName(bytes, moveNames + id * 13, "MOVE") }
        repeat(speciesCount) { id ->
            val base = stats + id * 28
            if (id > 0) {
                repeat(6) { field -> bytes[base + field] = (40 + field).toByte() }
                bytes[base + 6] = 12
                bytes[base + 7] = 3
            }
        }
        repeat(moveCount) { id ->
            val base = moves + id * 12
            if (id > 0) {
                bytes[base + 1] = 40
                bytes[base + 2] = (id % 18).toByte()
                bytes[base + 3] = 100
                bytes[base + 4] = 20
            }
        }
        val compactRoots = listOf(stats, 0x10000, 0x11000, 0x12000, moves, 0x13000, 0x14000)
        compactRoots.forEachIndexed { index, target -> writePointer(bytes, 0x1AC + index * 4, target) }
        // Complete the two higher overlapping windows with structurally valid but semantically unrelated pointers.
        listOf(0x15000, 0x16000, 0x17000, 0x18000).forEachIndexed { index, target ->
            writePointer(bytes, 0x1C8 + index * 4, target)
        }

        val resolved = GbaPublishedHeaderResolver.resolve(RomImage(bytes))

        assertEquals(stats, resolved.baseStats)
        assertEquals(moves, resolved.moveData)
    }

    @Test
    fun rejectsEquallyCompletePublishedBlocksWithoutSemanticEvidence() {
        val bytes = ByteArray(0x30000)
        repeat(11) { index -> writePointer(bytes, 0x1AC + index * 4, 0x8000 + index * 0x1000) }

        val resolved = GbaPublishedHeaderResolver.resolve(RomImage(bytes))

        assertNull(resolved.baseStats)
        assertNull(resolved.abilities)
        assertNull(resolved.abilityDescriptions)
        assertNull(resolved.moveData)
    }

    private fun encodeGbaName(bytes: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            bytes[offset + index] = (0xBB + char.code - 'A'.code).toByte()
        }
        bytes[offset + value.length] = 0xFF.toByte()
    }

    private fun writePointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000 + target
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
