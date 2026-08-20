package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3MapLightingResolverTest {
    @Test
    fun resolvesUniqueCompiledNormalAndBrightBlendTables() {
        val bytes = ByteArray(512)
        writeBlendTable(bytes, TABLE_OFFSET, 0x9D7474, 0xA8B0E0)
        writeBlendTable(bytes, TABLE_OFFSET + TABLE_BYTES, 0xBB9E9E, 0xA8B0E0)
        repeat(2) { index ->
            putInt(bytes, 0x20 + index * 4, GBA_ROM_BASE + TABLE_OFFSET)
            putInt(bytes, 0x40 + index * 4, GBA_ROM_BASE + TABLE_OFFSET + TABLE_BYTES)
        }

        val resolved = requireNotNull(Gen3MapLightingResolver.resolve(RomImage(bytes)))

        assertEquals(0x9D7474, resolved.night.blendColor)
        assertEquals(10, resolved.night.coefficient)
        assertEquals(0xA8B0E0, resolved.twilight.blendColor)
        assertEquals(4, resolved.twilight.coefficient)
        assertEquals(0, resolved.day.blendColor)
    }

    @Test
    fun rejectsUnreferencedOrAmbiguousBlendTables() {
        val unreferenced = ByteArray(512)
        writeBlendTable(unreferenced, TABLE_OFFSET, 0x9D7474, 0xA8B0E0)
        writeBlendTable(unreferenced, TABLE_OFFSET + TABLE_BYTES, 0xBB9E9E, 0xA8B0E0)
        assertNull(Gen3MapLightingResolver.resolve(RomImage(unreferenced)))

        val ambiguous = ByteArray(1024)
        writePairWithReferences(ambiguous, 0x100, 0x9D7474, 0xBB9E9E, 0x20)
        writePairWithReferences(ambiguous, 0x200, 0x806060, 0xA08080, 0x40)
        assertNull(Gen3MapLightingResolver.resolve(RomImage(ambiguous)))
    }

    private fun writePairWithReferences(
        bytes: ByteArray,
        offset: Int,
        normalNight: Int,
        brightNight: Int,
        referenceOffset: Int,
    ) {
        writeBlendTable(bytes, offset, normalNight, 0xA8B0E0)
        writeBlendTable(bytes, offset + TABLE_BYTES, brightNight, 0xA8B0E0)
        repeat(2) { index ->
            putInt(bytes, referenceOffset + index * 4, GBA_ROM_BASE + offset)
            putInt(bytes, referenceOffset + 8 + index * 4, GBA_ROM_BASE + offset + TABLE_BYTES)
        }
    }

    private fun writeBlendTable(bytes: ByteArray, offset: Int, night: Int, twilight: Int) {
        putInt(bytes, offset, packedBlend(night, tint = true, coefficient = 10))
        putInt(bytes, offset + 4, packedBlend(twilight, tint = true, coefficient = 4))
        putInt(bytes, offset + 8, 0)
    }

    private fun packedBlend(color: Int, tint: Boolean, coefficient: Int): Int =
        color or (if (tint) 1 shl 24 else 0) or (coefficient shl 25)

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }

    private companion object {
        const val GBA_ROM_BASE = 0x08000000
        const val TABLE_OFFSET = 0x100
        const val TABLE_BYTES = 12
    }
}
