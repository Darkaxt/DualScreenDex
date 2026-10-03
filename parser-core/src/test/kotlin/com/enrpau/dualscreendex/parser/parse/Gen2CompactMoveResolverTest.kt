package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2CompactMoveResolverTest {
    @Test fun resolvesCompleteCopyCategoryAndBankRestorationChain() {
        val resolved = requireNotNull(resolve(fixture()))
        assertEquals(TABLE, resolved.offset)
        assertEquals(COUNT, resolved.count)
        assertEquals(8, resolved.recordSize)
        assertEquals(1, resolved.bank)
    }

    @Test fun derivesRelocatedRootAndHelpersRatherThanFixedAddresses() {
        val bytes = fixture()
        bytes.copyInto(bytes, TABLE + 0x1000, TABLE, TABLE + COUNT * 8)
        bytes.word(CONSUMER + 2, TABLE + 0x1000)
        bytes.copyInto(bytes, CATEGORY + 0x100, CATEGORY, CATEGORY + 40)
        bytes.word(CONSUMER + 16, CATEGORY + 0x100)
        assertEquals(TABLE + 0x1000, requireNotNull(resolve(bytes)).offset)
    }

    @Test fun rejectsEveryChangedStructuralConsumerAndCategoryInstruction() {
        val original = fixture()
        val offsets = listOf(0, 1, 4, 5, 6, 7, 8, 10, 11, 14, 15, 18, 19, 20, 21, 22, 23)
        offsets.forEach { index ->
            assertNull("copy consumer instruction $index", resolve(original.copyOf().also {
                it[CONSUMER + index] = (it[CONSUMER + index].toInt() xor 1).toByte()
            }))
        }
        val categoryOffsets = listOf(0, 1, 2, 3, 4, 5, 7, 10, 11, 12, 13, 14, 15,
            18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 29, 32, 33, 35, 36, 37, 38, 39)
        categoryOffsets.forEach { index ->
            assertNull("category instruction $index", resolve(original.copyOf().also {
                it[CATEGORY + index] = (it[CATEGORY + index].toInt() xor 1).toByte()
            }))
        }
    }

    @Test fun rejectsBrokenMultiplyCopyReadAndBankRestoration() {
        val original = fixture()
        listOf(MULTIPLY + 4, COPY + 3, READ + 3, RESTORE + 5, STACK + 9).forEach { offset ->
            assertNull(resolve(original.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }))
        }
        assertNull(resolve(original.copyOf().also { it.word(CATEGORY + 30, READ + 1) }))
        assertNull(resolve(original.copyOf().also { it[CATEGORY + 28] = 2 }))
        assertNull(resolve(original.copyOf().also { it.word(CATEGORY + 16, 0x8000) }))
        assertNull(resolve(original.copyOf().also { it[CATEGORY + 19] = 0xC0.toByte() }))
    }

    @Test fun validatesEveryRecordWithoutTrimmingOrPlausibilityThresholds() {
        val original = fixture()
        assertNull(resolve(original.copyOf().also { it[TABLE + 2 * 8] = 1 }))
        assertNull(resolve(original.copyOf().also { it[TABLE + 2 * 8 + 3] = 19 }))
        assertNull(resolve(original.copyOf().also { it[TABLE + 2 * 8 + 5] = 0 }))
        assertNull(resolve(original.copyOf().also { it[TABLE + 2 * 8 + 7] = 3 }))
        assertNull(resolve(original, 0))
        assertNull(resolve(original, 256))
        assertNull(resolve(original, COUNT + 1))
    }

    @Test fun rejectsBankCrossingTablesAndNonHomeConsumers() {
        val original = fixture()
        assertNull(resolve(original.copyOf().also { it.word(CONSUMER + 2, 0x7FF0) }))
        assertNull(resolve(original.copyOf().also { it[CONSUMER + 9] = 0 }))
        val nonHome = original.copyOf()
        nonHome.copyInto(nonHome, CONSUMER + 0x4000, CONSUMER, CONSUMER + 24)
        nonHome.fill(0, CONSUMER, CONSUMER + 24)
        assertNull(resolve(nonHome))
        assertNull(resolve(original.copyOf(TABLE + COUNT * 8 - 1)))
    }

    @Test fun rejectsCompetingCompleteTableRoots() {
        val bytes = fixture()
        bytes.copyInto(bytes, CONSUMER + 0x100, CONSUMER, CONSUMER + 24)
        bytes.copyInto(bytes, TABLE + 0x1000, TABLE, TABLE + COUNT * 8)
        bytes.word(CONSUMER + 0x100 + 2, TABLE + 0x1000)
        assertNull(resolve(bytes))
    }

    @Test fun materializesStoredCategoriesInsteadOfRetailTypeBasedGuesses() {
        val bytes = fixture()
        val layout = ResolvedRomLayout(
            EngineFamily.CRYSTAL, 2, Platform.GBC, null, COUNT,
            ProfileTables(moveData = TableLayout(TABLE, COUNT, 8, format = TableRecordFormat.GEN2_SPLIT_MOVE_8)),
        )
        val moves = RecordMaterializers.moves(RomImage(bytes), layout)
        assertEquals(MoveCategory.SPECIAL, moves[1]?.category?.value)
        assertEquals(MoveCategory.PHYSICAL, moves[2]?.category?.value)
        assertEquals(MoveCategory.STATUS, moves[3]?.category?.value)
        assertEquals(80, moves[1]?.power?.value)
        assertEquals(25, moves[1]?.pp?.value)
        assertEquals(230, moves[1]?.accuracy?.value)
        assertEquals(6, moves[1]?.effectId?.value)
    }

    private fun resolve(bytes: ByteArray, count: Int = COUNT): TableLayout? =
        Gen2CompactMoveResolver.resolve(RomImage(bytes), count, (0..18).toSet())

    private fun fixture(): ByteArray = ByteArray(0x8000).also { bytes ->
        bytes.put(CONSUMER, "3d 21 00 42 01 08 00 df 3e 01 d5 cd 00 03 e1 cd 80 02 01 07 00 09 77 c9")
        bytes.put(CATEGORY, "e5 01 07 00 09 3e 01 cd 20 03 e1 fe 02 c8 47 fa 00 c2 cb 7f 78 c0 e5 01 03 00 09 3e 01 cd 20 03 e1 fe 09 3e 00 d8 3c c9")
        bytes.put(COPY, "cd 80 01 04 0c 18 03 2a 12 13 0d 20 fa 05 20 f7 c9")
        bytes.put(READ, "cd 80 01 7e c9")
        bytes.put(STACK, "e8 fd d5 e5 f8 08 56 5f f0 87 32 7b 5e 36 02 2b 36 00 2b 72 2b 73 e1 d1 c3 08 00")
        bytes.put(RESTORE, "f5 e5 f8 04 7e cf e1 f1 33 c9")
        bytes.put(0x08, "e0 87 ea 00 20 c9")
        bytes.put(0x18, "c3 00 01")
        bytes.put(MULTIPLY, "a7 c8 c5 1f 30 01 09 cb 21 cb 10 a7 20 f5 c1 c9")
        bytes.put(TABLE, "01 06 50 00 e6 19 00 01 02 07 50 0b e6 0f 00 00 03 08 00 09 ff 0a 00 02")
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val CONSUMER = 0x400
        const val CATEGORY = 0x280
        const val MULTIPLY = 0x100
        const val STACK = 0x180
        const val RESTORE = 0x200
        const val COPY = 0x300
        const val READ = 0x320
        const val TABLE = 0x4200
        const val COUNT = 3
    }
}
