package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2ClassicMoveContractTest {
    @Test fun resolvesMaskedTypeAfterCompleteClassicFarCopy() {
        val result = requireNotNull(resolve(fixture(true)))
        assertEquals(0x5000, result.offset)
        assertEquals("GEN2_MASKED_MOVE_7", result.format.name)
    }

    @Test fun resolvesBranchedPrinterWithoutClaimingRuntimeExceptionType() {
        val result = requireNotNull(resolve(fixture(false)))
        assertEquals(0x5000, result.offset)
        assertEquals(7, result.recordSize)
    }

    @Test fun materializesMaskedNativeTypeWithoutGuessingCategoryBits() {
        val bytes = fixture(true)
        val data = requireNotNull(resolve(bytes))
        val moves = RecordMaterializers.moves(RomImage(bytes), ResolvedRomLayout(
            EngineFamily.CRYSTAL, 2, Platform.GBC, null, 3, ProfileTables(moveData = data),
        ))
        assertEquals(20, moves[1]?.typeId?.value)
        assertEquals(80, moves[1]?.power?.value)
        assertEquals(10, moves[1]?.pp?.value)
        assertNull(moves[1]?.category?.value)
    }

    @Test fun extendedMaskedTypeRequiresAnIndependentlyDecodedNativeDomain() {
        val bytes = fixture(true)
        bytes[0x5003] = 0x9C.toByte()
        assertNull(resolve(bytes))
        assertEquals(0x5000, requireNotNull(Gen2CompiledMoveResolver.resolve(
            RomImage(bytes), 3, (0..28).toSet(),
        )).offset)
    }

    @Test fun rejectsMalformedCopyMultiplyMaskAndBranchLinkage() {
        for (masked in listOf(false, true)) {
            val original = fixture(masked)
            val offsets = listOf(0x500 + 4, 0x600 + 8, 0x700 + 6, 0x10 + 1) +
                if (masked) listOf(0x4000 + 24) else listOf(0x4000 + 5, 0x4000 + 31, 0x4000 + 36)
            for (offset in offsets) assertNull("instruction $offset", resolve(original.copyOf().also {
                it[offset] = (it[offset].toInt() xor 1).toByte()
            }))
        }
    }

    @Test fun rejectsTruncationBankCrossingAndIncompleteRecordDomain() {
        val bytes = fixture(true)
        assertNull(resolve(bytes.copyOf(0x5014)))
        assertNull(resolve(bytes.copyOf(0x4002)))
        assertNull(resolve(bytes.copyOf().also { it.word(0x4000 + 7, 0x7FF0) }))
        assertNull(resolve(bytes.copyOf().also { it[0x5000 + 14] = 1 }))
        assertNull(resolve(bytes.copyOf().also { it[0x5000 + 19] = 0 }))
    }

    private fun resolve(bytes: ByteArray) = Gen2CompiledMoveResolver.resolve(RomImage(bytes), 3)

    private fun fixture(masked: Boolean): ByteArray = ByteArray(0x8000).also { bytes ->
        bytes.put(0x10, "e0 9d ea 00 20 c9")
        bytes.put(0x500, "a7 c8 09 3d 20 fc c9")
        bytes.put(0x600, "e0 8b f0 9d f5 f0 8b d7 cd 00 07 f1 d7 c9")
        bytes.put(0x700, "2a 12 13 0b 79 b0 20 f8 c9")
        if (masked) {
            bytes.put(0x4000, "e5 78 3d 01 07 00 21 00 50 cd 00 05 11 00 c1 3e 01 cd 00 06 fa 03 c1 e6 3f e1 47 78 e5 87 21 00 48 5f 16 00 19 2a 5f 56 e1 c3 00 09")
        } else {
            bytes.put(0x4000, "e5 78 fe 02 28 19 3d 01 07 00 21 00 50 cd 00 05 11 00 c1 3e 01 cd 00 06 fa 03 c1 e1 47 18 05 cd 00 47 e1 47 78 e5 87 21 00 48 5f 16 00 19 2a 5f 56 e1 c3 00 09")
        }
        repeat(3) { index ->
            bytes.put(0x5000 + index * 7, "00 01 50 14 ff 0a 00")
            bytes[0x5000 + index * 7] = (index + 1).toByte()
            if (masked) bytes[0x5000 + index * 7 + 3] = 0x94.toByte()
        }
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }
}
