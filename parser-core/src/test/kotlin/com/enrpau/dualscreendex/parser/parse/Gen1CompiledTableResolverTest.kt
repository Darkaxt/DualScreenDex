package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Gen1CompiledTableResolverTest {
    @Test
    fun resolvesCombinedRelationshipsFromAScaledPointerConsumer() {
        val bytes = ByteArray(0xC000)
        writeRelationshipConsumer(bytes, 0x8100, 0x5000)
        writeRelationshipTable(bytes, 0x9000, 0x5200, count = 3, level = 201)

        val layout = Gen1CompiledRelationshipResolver.resolve(
            RomImage(bytes),
            preferredCount = 4,
            fallbackCounts = listOf(3),
        )

        assertNotNull(layout)
        assertEquals(0x9000, layout?.offset)
        assertEquals(3, layout?.count)
        assertEquals(2, layout?.recordSize)
        assertEquals(2, layout?.bank)
    }

    @Test
    fun resolvesCombinedRelationshipsFromADoubleAddPointerConsumer() {
        val bytes = ByteArray(0xC000)
        writeRelationshipConsumer(bytes, 0x8100, 0x5000, scaledIndex = false)
        writeRelationshipTable(bytes, 0x9000, 0x5200, count = 3, level = 50)

        val layout = Gen1CompiledRelationshipResolver.resolve(
            RomImage(bytes),
            preferredCount = 3,
            fallbackCounts = emptyList(),
        )

        assertNotNull(layout)
        assertEquals(0x9000, layout?.offset)
    }

    @Test
    fun rejectsAmbiguousRelationshipConsumers() {
        val bytes = ByteArray(0x14000)
        writeRelationshipConsumer(bytes, 0x8100, 0x5000)
        writeRelationshipTable(bytes, 0x9000, 0x5200, count = 3, level = 50)
        writeRelationshipConsumer(bytes, 0xC100, 0x5000)
        writeRelationshipTable(bytes, 0xD000, 0x5200, count = 3, level = 50)

        val layout = Gen1CompiledRelationshipResolver.resolve(
            RomImage(bytes),
            preferredCount = 3,
            fallbackCounts = emptyList(),
        )

        assertNull(layout)
    }

    @Test
    fun resolvesLegacyTypeTripletsFromTheirBattleConsumer() {
        val bytes = ByteArray(0xC000)
        writeTypeChartConsumer(bytes, 0x8200, 0x5600)
        repeat(10) { index ->
            val offset = 0x9600 + index * 3
            bytes[offset] = (index % 29).toByte()
            bytes[offset + 1] = ((index + 1) % 29).toByte()
            bytes[offset + 2] = if (index % 2 == 0) 20 else 5
        }
        bytes[0x9600 + 30] = 0xFF.toByte()

        val layout = Gen1CompiledTypeChartResolver.resolve(RomImage(bytes))

        assertNotNull(layout)
        assertEquals(0x9600, layout?.offset)
        assertEquals(3, layout?.recordSize)
        assertEquals(true, layout?.variableLength)
    }

    private fun writeRelationshipConsumer(
        bytes: ByteArray,
        offset: Int,
        rootAddress: Int,
        scaledIndex: Boolean = true,
    ) {
        val consumer = if (scaledIndex) {
            byteArrayOf(0x87.toByte(), 0xCB.toByte(), 0x10, 0x4F, 0x09, 0x2A, 0x66, 0x6F)
        } else {
            byteArrayOf(0x4F, 0x09, 0x09, 0x2A, 0x66, 0x6F)
        }
        byteArrayOf(
            0x21, rootAddress.toByte(), (rootAddress ushr 8).toByte(),
            *consumer,
        ).copyInto(bytes, offset)
    }

    private fun writeRelationshipTable(
        bytes: ByteArray,
        tableOffset: Int,
        firstRecordAddress: Int,
        count: Int,
        level: Int,
    ) {
        val bankOffset = tableOffset / 0x4000 * 0x4000
        val firstRecordOffset = bankOffset + firstRecordAddress - 0x4000
        repeat(count) { index ->
            putU16(bytes, tableOffset + index * 2, firstRecordAddress + index * 0x10)
            byteArrayOf(
                1, 16, ((index + 1) % count + 1).toByte(),
                0,
                level.toByte(), (index + 1).toByte(),
                0,
            ).copyInto(bytes, firstRecordOffset + index * 0x10)
        }
    }

    private fun writeTypeChartConsumer(bytes: ByteArray, offset: Int, rootAddress: Int) {
        byteArrayOf(
            0x21, rootAddress.toByte(), (rootAddress ushr 8).toByte(),
            0x2A, 0xFE.toByte(), 0xFF.toByte(), 0x28, 0x10,
            0xB8.toByte(), 0x20, 0x08, 0x7E, 0xB9.toByte(), 0x28, 0x04,
        ).copyInto(bytes, offset)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}
