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
    fun resolvesMovesFromCompiledNameAndCopyConsumers() {
        val bytes = ByteArray(0x10000)
        writeMoveNameConsumer(bytes, 0x100, bank = 2)
        writeNamePointerConsumer(bytes, 0x200, pointerTable = 0x300)
        putU16(bytes, 0x302, 0x4000)
        writeMoveNames(bytes, 0x8000, count = 100)
        writeMoveDataConsumer(bytes, 0x500, rootAddress = 0x4000, bank = 3)
        writeMoveData(bytes, 0xC000, count = 100)

        val resolution = Gen1CompiledMoveResolver.resolve(RomImage(bytes))

        assertNotNull(resolution)
        assertEquals(0x8000, resolution?.moveNames?.offset)
        assertEquals(0xC000, resolution?.moveData?.offset)
        assertEquals(100, resolution?.moveNames?.count)
        assertEquals(100, resolution?.moveData?.count)
        assertEquals(6, resolution?.moveData?.recordSize)
    }

    @Test
    fun rejectsAmbiguousMoveCopyConsumers() {
        val bytes = ByteArray(0x14000)
        writeMoveNameConsumer(bytes, 0x100, bank = 2)
        writeNamePointerConsumer(bytes, 0x200, pointerTable = 0x300)
        putU16(bytes, 0x302, 0x4000)
        writeMoveNames(bytes, 0x8000, count = 100)
        writeMoveDataConsumer(bytes, 0x500, rootAddress = 0x4000, bank = 3)
        writeMoveData(bytes, 0xC000, count = 100)
        writeMoveDataConsumer(bytes, 0x600, rootAddress = 0x4000, bank = 4)
        writeMoveData(bytes, 0x10000, count = 100)

        assertNull(Gen1CompiledMoveResolver.resolve(RomImage(bytes)))
    }

    @Test
    fun resolvesMachineMovesFromTheirCompiledConsumers() {
        val bytes = ByteArray(0x10000)
        writeMachineConsumers(bytes, 0x8200, 0x8600)
        writeMachineMoves(bytes, 0x8600, count = 55)

        val layout = Gen1CompiledMachineResolver.resolve(RomImage(bytes), moveCount = 165)

        assertNotNull(layout)
        assertEquals(0x8600, layout?.offset)
        assertEquals(55, layout?.count)
        assertEquals(1, layout?.recordSize)
    }

    @Test
    fun resolvesMachineMovesFromASimplifiedSearchConsumer() {
        val bytes = ByteArray(0x10000)
        writeMachineConsumers(bytes, 0x8200, 0x8600, simplifiedSearch = true)
        writeMachineMoves(bytes, 0x8600, count = 55)

        val layout = Gen1CompiledMachineResolver.resolve(RomImage(bytes), moveCount = 165)

        assertNotNull(layout)
        assertEquals(0x8600, layout?.offset)
    }

    @Test
    fun resolvesMachineMovesFromATerminatedSearchConsumer() {
        val bytes = ByteArray(0x10000)
        writeMachineConsumers(bytes, 0x8200, 0x8600, terminatedSearch = true)
        writeMachineMoves(bytes, 0x8600, count = 55)

        val layout = Gen1CompiledMachineResolver.resolve(RomImage(bytes), moveCount = 165)

        assertNotNull(layout)
        assertEquals(0x8600, layout?.offset)
    }

    @Test
    fun rejectsAmbiguousCompiledMachineLists() {
        val bytes = ByteArray(0x14000)
        writeMachineConsumers(bytes, 0x8200, 0x8600)
        writeMachineMoves(bytes, 0x8600, count = 55)
        writeMachineConsumers(bytes, 0xC200, 0xC600)
        writeMachineMoves(bytes, 0xC600, count = 55, firstMove = 56)

        assertNull(Gen1CompiledMachineResolver.resolve(RomImage(bytes), moveCount = 165))
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

    private fun writeMachineConsumers(
        bytes: ByteArray,
        offset: Int,
        rootOffset: Int,
        simplifiedSearch: Boolean = false,
        terminatedSearch: Boolean = false,
    ) {
        val rootAddress = 0x4000 + rootOffset % 0x4000
        val searchConsumer = if (simplifiedSearch) {
            byteArrayOf(
                0xE5.toByte(), 0x47, 0x0E, 0x00,
                0x21, rootAddress.toByte(), (rootAddress ushr 8).toByte(),
                0x2A, 0xB8.toByte(), 0x28, 0x03, 0x0C, 0x18, 0xF9.toByte(),
                0xE1.toByte(), 0xC9.toByte(),
            )
        } else if (terminatedSearch) {
            byteArrayOf(
                0xE5.toByte(), 0xFA.toByte(), 0x00, 0xD0.toByte(), 0x47, 0x0E, 0x00,
                0x21, rootAddress.toByte(), (rootAddress ushr 8).toByte(),
                0x2A, 0xFE.toByte(), 0xFF.toByte(), 0x28, 0x06,
                0xB8.toByte(), 0x28, 0x03, 0x0C, 0x18, 0xF5.toByte(),
                0xE1.toByte(), 0x06, 0x02, 0xC3.toByte(), 0x00, 0x10,
            )
        } else {
            byteArrayOf(
                0xE5.toByte(), 0xFA.toByte(), 0x00, 0xD0.toByte(), 0x47, 0x0E, 0x00,
                0x21, rootAddress.toByte(), (rootAddress ushr 8).toByte(),
                0x2A, 0xB8.toByte(), 0x28, 0x03, 0x0C, 0x18, 0xF9.toByte(),
                0xE1.toByte(), 0x06, 0x02, 0xC3.toByte(), 0x00, 0x10,
            )
        }
        searchConsumer.copyInto(bytes, offset)
        byteArrayOf(
            0xFA.toByte(), 0x21, 0xD1.toByte(), 0x3D,
            0x21, rootAddress.toByte(), (rootAddress ushr 8).toByte(),
            0x06, 0x00, 0x4F, 0x09, 0x7E, 0xEA.toByte(), 0x21, 0xD1.toByte(), 0xC9.toByte(),
        ).copyInto(bytes, offset + 0x40)
    }

    private fun writeMachineMoves(bytes: ByteArray, offset: Int, count: Int, firstMove: Int = 1) {
        repeat(count) { index -> bytes[offset + index] = (firstMove + index).toByte() }
    }

    private fun writeMoveNameConsumer(bytes: ByteArray, offset: Int, bank: Int) {
        byteArrayOf(
            0xE5.toByte(), 0x3E, 0x02, 0xEA.toByte(), 0x00, 0xD0.toByte(),
            0xFA.toByte(), 0x01, 0xD0.toByte(), 0xEA.toByte(), 0x02, 0xD0.toByte(),
            0x3E, bank.toByte(), 0xEA.toByte(), 0x03, 0xD0.toByte(),
            0xCD.toByte(), 0x00, 0x10, 0x11, 0x00, 0xD1.toByte(), 0xE1.toByte(), 0xC9.toByte(),
        ).copyInto(bytes, offset)
    }

    private fun writeNamePointerConsumer(bytes: ByteArray, offset: Int, pointerTable: Int) {
        byteArrayOf(
            0x21, pointerTable.toByte(), (pointerTable ushr 8).toByte(),
            0x19, 0x2A, 0xE0.toByte(), 0x96.toByte(), 0x7E, 0xE0.toByte(), 0x95.toByte(),
            0xF0.toByte(), 0x95.toByte(), 0x67, 0xF0.toByte(), 0x96.toByte(), 0x6F,
        ).copyInto(bytes, offset)
    }

    private fun writeMoveNames(bytes: ByteArray, offset: Int, count: Int) {
        var cursor = offset
        repeat(count) { index ->
            val suffix = index % 10
            byteArrayOf(0x8C.toByte(), 0x8E.toByte(), 0x95.toByte(), 0x84.toByte(), (0xF6 + suffix).toByte(), 0x50)
                .copyInto(bytes, cursor)
            cursor += 6
        }
    }

    private fun writeMoveDataConsumer(bytes: ByteArray, offset: Int, rootAddress: Int, bank: Int) {
        byteArrayOf(
            0x3D, 0x21, rootAddress.toByte(), (rootAddress ushr 8).toByte(),
            0x01, 0x06, 0x00, 0xCD.toByte(), 0x00, 0x10,
            0x11, 0x00, 0xD2.toByte(), 0x3E, bank.toByte(), 0xCD.toByte(), 0x00, 0x11,
        ).copyInto(bytes, offset)
    }

    private fun writeMoveData(bytes: ByteArray, offset: Int, count: Int) {
        repeat(count) { index ->
            byteArrayOf(0, 1, (index + 1).toByte(), (index % 28).toByte(), 100, 15)
                .copyInto(bytes, offset + index * 6)
        }
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
