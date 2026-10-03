package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2CompiledSpriteBankContractTest {
    @Test fun resolvesCompleteBankRemapBeforeExpandedSpriteValidation() {
        val result = requireNotNull(resolve(fixture()))
        assertEquals(253, result.count)
        assertEquals(0x4000, result.offset)
        assertEquals(mapOf(0x12 to 3), result.bankRemap)
    }

    @Test fun derivesRelocatedTransformAndLookupRoot() {
        val bytes = fixture()
        bytes.copyInto(bytes, 0x380, 0x300, 0x310)
        bytes.word(0x100 + 34, 0x380)
        bytes.word(0x380 + 8, 0x390)
        bytes[0x390] = 3
        assertEquals(mapOf(0x12 to 3), requireNotNull(resolve(bytes)).bankRemap)
    }

    @Test fun rejectsChangedTransformReadMultiplyAndRestorationBodies() {
        val bytes = fixture()
        for (offset in listOf(0x100 + 36, 0x300, 0x304, 0x306, 0x30A, 0x30D, 0x500 + 4,
            0x600 + 13, 0x620 + 12, 0x10 + 1)) {
            assertNull("unproven sprite bank instruction $offset", resolve(bytes.copyOf().also {
                it[offset] = (it[offset].toInt() xor 1).toByte()
            }))
        }
    }

    @Test fun rejectsNegativeLookupIndexAndOutOfRomBank() {
        val bytes = fixture()
        assertNull(resolve(bytes.copyOf().also { it[0x303] = 0x13 }))
        assertNull(resolve(bytes.copyOf().also { it[0x30F] = 5 }))
    }

    private fun resolve(bytes: ByteArray) = Gen2CompiledSpriteResolver.resolve(RomImage(bytes), 253)

    private fun fixture(): ByteArray = ByteArray(0x10000).also { bytes ->
        bytes.put(0x10, "e0 9d ea 00 20 c9")
        bytes.put(0x100, "fa 00 c1 fe c9 28 07 fa 00 c1 16 01 18 05 fa 00 c2 16 02 21 00 40 3d 01 06 00 cd 00 05 7a cd 00 06 cd 00 03 f5 23 7a cd 20 06 c1 c9")
        bytes.put(0x300, "e5 c5 d6 12 4f 06 00 21 0f 03 09 7e c1 e1 c9 03")
        bytes.put(0x500, "a7 c8 09 3d 20 fc c9")
        bytes.put(0x600, "e0 8b f0 9d f5 f0 8b d7 7e e0 8b f1 d7 f0 8b c9")
        bytes.put(0x620, "e0 8b f0 9d f5 f0 8b d7 2a 66 6f f1 d7 c9")
        for (root in listOf(0x4000, 0x8000)) repeat(if (root == 0x4000) 253 else 26) { index ->
            bytes.put(root + index * 6, "12 00 41 12 00 41")
        }
        bytes.put(0xC100, "00 12 ff")
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }
}
