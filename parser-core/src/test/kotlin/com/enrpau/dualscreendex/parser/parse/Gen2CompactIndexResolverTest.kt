package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen2CompactCoreMetadata
import com.enrpau.dualscreendex.parser.model.Gen2CompactSpeciesSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2CompactIndexResolverTest {
    @Test fun provesZeroBasedCanonicalBoundaryAndNonSpeciesHoles() {
        val resolved = requireNotNull(zero(zeroFixture()))
        assertEquals(292, resolved.nameSlots)
        assertEquals(291, resolved.baseSlots)
        assertEquals(289, resolved.metadata.slots.size)
        val slots = resolved.metadata.slots.associateBy { it.id }
        assertEquals(Gen2CompactSpeciesSlot(1, 1, 0), slots[1])
        assertEquals(Gen2CompactSpeciesSlot(254, 254, 253), slots[254])
        assertEquals(Gen2CompactSpeciesSlot(257, 257, 256), slots[257])
        assertEquals(Gen2CompactSpeciesSlot(291, 291, 290), slots[291])
        assertFalse(0 in slots || 255 in slots || 256 in slots)
    }

    @Test fun appliesCompiledVariantMatchesRatherThanIdMinusOne() {
        val bytes = zeroFixture()
        bytes.put(BASE_TABLE, "01 00 02 02 01 20 00")
        val resolved = requireNotNull(zero(bytes))
        val slots = resolved.metadata.slots.associateBy { it.id }
        assertEquals(294, resolved.baseSlots)
        assertEquals(291, slots[1]?.baseIndex)
        assertEquals(1, slots[2]?.baseIndex)
        assertEquals(293, slots[257]?.baseIndex)
        assertEquals(257, slots[257]?.nameIndex)
    }

    @Test fun keepsOneBasedNameAndBaseVariantTablesIndependent() {
        val bytes = oneFixture()
        bytes.put(BASE_TABLE, "07 02 08 20 00")
        bytes.put(NAME_TABLE, "08 20 00")
        val resolved = requireNotNull(one(bytes))
        val slots = resolved.metadata.slots.associateBy { it.id }
        assertEquals(257, resolved.nameSlots)
        assertEquals(257, resolved.baseSlots)
        assertEquals(255, slots.size)
        assertEquals(Gen2CompactSpeciesSlot(8, 8, 7), slots[8])
        assertEquals(Gen2CompactSpeciesSlot(264, 256, 256), slots[264])
        assertFalse(255 in slots || 256 in slots)
    }

    @Test fun doesNotPromoteAlternateFormNamesToCanonicalSpecies() {
        val bytes = oneFixture()
        bytes.put(NAME_TABLE, "09 02 08 20 00")
        bytes.put(BASE_TABLE, "08 20 00")
        val resolved = requireNotNull(one(bytes))
        assertEquals(258, resolved.nameSlots)
        assertEquals(255, resolved.metadata.slots.size)
        assertEquals(Gen2CompactSpeciesSlot(264, 257, 255), resolved.metadata.slots.last())
    }

    @Test fun derivesRelocatedRootsInverseConstantsAndCanonicalLimit() {
        val bytes = zeroFixture()
        bytes.word(ZERO + 1, BASE_TABLE + 0x100)
        bytes.word(ZERO + 24, 260)
        val resolved = requireNotNull(zero(bytes))
        assertEquals(261, resolved.nameSlots)
        assertEquals(258, resolved.metadata.slots.size)
        val one = oneFixture()
        one.word(ONE_BASE + 1, BASE_TABLE + 0x100 - 1)
        one.word(ONE_BASE + 8, -(BASE_TABLE + 0x100))
        assertEquals(254, requireNotNull(one(one)).metadata.slots.size)
    }

    @Test fun rejectsChangedZeroBasedBranchesArithmeticAndRestoration() {
        val original = zeroFixture()
        val offsets = listOf(ZERO + 3, ZERO + 5, ZERO + 9, ZERO + 10, ZERO + 13, ZERO + 14,
            ZERO + 15, ZERO + 16, ZERO + 17, ZERO + 18, ZERO + 20, ZERO + 22, ZERO + 23,
            ZERO + 26, ZERO + 27, ZERO + 28, ZERO + 29, ZERO + 30, ZERO + 31,
            ZERO_HELPER, ZERO_HELPER + 2, ZERO_HELPER + 6, ZERO_HELPER + 11,
            ZERO_HELPER + 15, ZERO_HELPER + 16, ZERO_HELPER + 20, ZERO_HELPER + 21,
            ZERO_HELPER + 22, ZERO_HELPER + 24, ZERO_HELPER + 26, ZERO_HELPER + 29,
            ZERO_HELPER + 30, ZERO_HELPER + 31, ZERO_HELPER + 32,
            COMPARE + 5, COMPARE + 10, COMPARE + 14, COMPARE + 17, COMPARE + 18,
            COMPARE + 20, COMPARE + 21, CONVERTER + 1, CONVERTER + 4, CONVERTER + 5)
        offsets.forEach { offset ->
            assertNull("zero-based instruction $offset", zero(original.copyOf().also {
                it[offset] = (it[offset].toInt() xor 1).toByte()
            }))
        }
        assertNull(zero(original.copyOf().also { it.word(ZERO_HELPER + 27, CONVERTER + 1) }))
        assertNull(zero(original.copyOf().also { it.word(ZERO + 24, 511) }))
    }

    @Test fun rejectsChangedOneBasedTablesInverseConstantsAndSharedHelper() {
        val original = oneFixture()
        listOf(ONE_BASE + 3, ONE_BASE + 6, ONE_BASE + 7, ONE_FINAL + 1,
            ONE_FINAL + 3, ONE_FINAL + 5, ONE_FINAL + 6, ONE_FINAL + 9,
            ONE_NAME + 10, ONE_NAME + 11, ONE_HELPER + 4, ONE_HELPER + 8,
            ONE_HELPER + 14, ONE_HELPER + 17, ONE_HELPER + 22, ONE_HELPER + 26,
            ONE_HELPER + 28, ONE_HELPER + 30, ONE_HELPER + 31, ONE_HELPER + 32,
            ONE_HELPER + 35, ONE_HELPER + 38, ONE_HELPER + 41, ONE_HELPER + 43).forEach { offset ->
            assertNull("one-based instruction $offset", one(original.copyOf().also {
                it[offset] = (it[offset].toInt() xor 1).toByte()
            }))
        }
        assertNull(one(original.copyOf().also { it.word(ONE_BASE + 8, -BASE_TABLE + 1) }))
        assertNull(one(original.copyOf().also { it.word(ONE_NAME + 4, ONE_HELPER + 1) }))
        assertNull(one(original.copyOf().also { it.word(ONE_NAME + 8, -NAME_TABLE + 1) }))
    }

    @Test fun rejectsUnterminatedConflictingOrInvalidVariantTablesAndMissingDomainProof() {
        for (modernZero in listOf(true, false)) {
            val original = if (modernZero) zeroFixture() else oneFixture()
            val resolve = if (modernZero) ::zero else ::one
            assertNull(resolve(original.copyOf().also { it.put(BASE_TABLE, "01 02 01 02 00") }))
            assertNull(resolve(original.copyOf().also { it.put(BASE_TABLE, "ff 02 00") }))
            assertNull(resolve(original.copyOf().also { it[PREDICATE + 2] = 3 }))
            assertNull(resolve(original.copyOf().also { it.fill(0x01, BASE_TABLE, 0x4000) }))
            assertNull(resolve(original.copyOf(0x180)))
        }
        val bytes = oneFixture()
        bytes.put(NAME_TABLE, "08 20 08 20 00")
        assertNull(one(bytes))
    }

    @Test fun metadataRetainsImmutableCanonicalJoins() {
        val mutable = mutableListOf(Gen2CompactSpeciesSlot(1, 1, 0))
        val metadata = Gen2CompactCoreMetadata(mutable)
        mutable.clear()
        assertEquals(listOf(Gen2CompactSpeciesSlot(1, 1, 0)), metadata.slots)
        assertTrue(runCatching { (metadata.slots as MutableList).clear() }.isFailure)
    }

    private fun zero(bytes: ByteArray): Gen2CompactIndexResolution? =
        Gen2CompactIndexResolver.zeroBased(RomImage(bytes), ZERO, CONVERTER)

    private fun one(bytes: ByteArray): Gen2CompactIndexResolution? =
        Gen2CompactIndexResolver.oneBased(RomImage(bytes), ONE_BASE, ONE_NAME)

    private fun zeroFixture(): ByteArray = ByteArray(0x4000).also { bytes ->
        bytes.put(PREDICATE, "3c fe 02 3d c9")
        bytes.put(CONVERTER, "e6 20 cb 37 1f c9")
        bytes.put(COMPARE, "c5 4f 87 79 38 02 cb b8 a8 28 07 fe 20 30 03 a8 e6 1f a7 79 c1 c9")
        bytes.put(ZERO, "21 00 10 d5 7c 2f 57 7d 2f 5f cd 20 02 30 03 3f d1 c9 19 cb 3c cb 1d 11 23 01 19 44 4d 37 d1 c9")
        bytes.put(ZERO_HELPER, "0c 28 13 0d 78 e6 3f 47 2a a7 28 0d b9 2a 20 f8 cd 80 01 20 f3 c9 06 00 0d 78 cd 00 01 47 0d 37 c9")
    }

    private fun oneFixture(): ByteArray = ByteArray(0x4000).also { bytes ->
        bytes.put(PREDICATE, "3c fe 02 3d c9")
        bytes.put(ONE_BASE, "21 ff 0f cd 80 03 d8 01 00 f0 09 cb 3c cb 1d 2b 24 44 4d c9")
        bytes.put(ONE_NAME, "21 ff 11 cd 80 03 d8 01 00 ee 18 be")
        bytes.put(ONE_HELPER, "78 e6 3f 28 23 fe 01 28 1f 47 23 2a a7 28 19 b9 20 f8 3e 3f a6 28 f3 fe 20 20 06 cb 68 28 eb 23 c9 2a cb 6f b8 20 e4 c9 06 00 37 c9")
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val PREDICATE = 0x80
        const val CONVERTER = 0x100
        const val COMPARE = 0x180
        const val ZERO = 0x200
        const val ZERO_HELPER = ZERO + 32
        const val ONE_BASE = 0x300
        const val ONE_FINAL = ONE_BASE + 10
        const val ONE_NAME = 0x340
        const val ONE_HELPER = 0x380
        const val BASE_TABLE = 0x1000
        const val NAME_TABLE = 0x1200
    }
}
