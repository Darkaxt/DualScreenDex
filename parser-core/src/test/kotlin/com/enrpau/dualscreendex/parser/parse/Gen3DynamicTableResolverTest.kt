package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class Gen3DynamicTableResolverTest {
    @Test
    fun relocatesReferencedDpeStatsAndCfruMoveRecordsWhenPublishedSlotsChangedMeaning() {
        val bytes = ByteArray(0x12000)
        val wrongStats = 0x2000
        val wrongMoves = 0x3000
        val stats = 0x5000
        val moves = 0x8000
        val speciesCount = 80
        val moveCount = 70

        repeat(speciesCount) { id ->
            val base = stats + id * 28
            if (id > 0) {
                repeat(6) { field -> bytes[base + field] = (35 + id % 80 + field).toByte() }
                bytes[base + 6] = (id % 24).toByte()
                bytes[base + 7] = ((id + 1) % 24).toByte()
            }
        }
        repeat(moveCount) { id ->
            val base = moves + id * 16
            if (id > 0) {
                writeU16(bytes, base, id % 300)
                writeU16(bytes, base + 2, 20 + id % 150)
                bytes[base + 4] = (id % 24).toByte()
                bytes[base + 5] = 100
                bytes[base + 6] = 20
                bytes[base + 7] = 10
                bytes[base + 9] = if (id % 2 == 0) 1 else 0
                bytes[base + 10] = (id % 3).toByte()
            }
        }
        repeat(5) { writePointer(bytes, 0x100 + it * 4, stats) }
        repeat(4) { writePointer(bytes, 0x140 + it * 4, moves) }

        val resolved = Gen3DynamicTableResolver.resolve(
            RomImage(bytes),
            ProfileTables(
                baseStats = TableLayout(wrongStats, speciesCount, 28),
                moveData = TableLayout(wrongMoves, moveCount, 12),
            ),
            speciesCount,
            moveCount,
        )

        assertEquals(stats, resolved.baseStats?.offset)
        assertEquals(28, resolved.baseStats?.recordSize)
        assertEquals(moves, resolved.moveData?.offset)
        assertEquals(16, resolved.moveData?.recordSize)
        assertEquals(TableRecordFormat.CFRU_MOVE_16, resolved.moveData?.format)
    }

    private fun writePointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000 + target
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}
