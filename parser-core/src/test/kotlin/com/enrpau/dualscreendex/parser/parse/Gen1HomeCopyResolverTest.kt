package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Gen1HomeCopyResolverTest {
    @Test
    fun resolvesFixedNamesCopiedThroughAVerifiedRestart() {
        val result = Gen1CompiledNameResolver.resolve(RomImage(fixture()), 190)

        assertNotNull(result)
        assertEquals(0x9000, result?.offset)
        assertEquals(190, result?.count)
        assertEquals(10, result?.recordSize)
    }

    @Test
    fun resolvesBaseStatsWithHelperCalledBankAuthority() {
        val result = Gen1CompiledBaseResolver.resolveWithAuthority(RomImage(fixture()), 151)

        assertNotNull(result)
        assertEquals(0xD000, result?.table?.offset)
        assertEquals(28, result?.table?.recordSize)
        assertEquals(false, result?.bankLocal)
    }

    @Test
    fun resolvesBaseStatsWithHelperBankAuthorityAndRestartCopy() {
        val bytes = fixture()
        write(bytes, 0x520 + 16, 0xE7, 0, 0)
        val result = Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151)

        assertNotNull(result)
        assertEquals(0xD000, result?.offset)
        assertEquals(151, result?.count)
    }

    @Test
    fun rejectsHelperCalledBankAuthorityWithoutMatchingRestoration() {
        val bytes = fixture()
        putWord(bytes, 0x540 + 5, 0x200)

        assertNull(Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151))
    }

    @Test
    fun rejectsHelperBankStoresOutsideTheMbcWindow() {
        val bytes = fixture()
        putWord(bytes, 0x240 + 3, 0xC000)

        assertNull(Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151))
    }

    @Test
    fun rejectsAnOptimizedCopyWithAnUnprovenInternalCall() {
        val bytes = fixture()
        putWord(bytes, 0x220 + 10, 0x231)

        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 190))
        assertNull(Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151))
    }

    private fun fixture(): ByteArray = ByteArray(0x18000).also { bytes ->
        write(bytes, 0x20, 0xC3, 0x20, 0x02)
        write(bytes, 0x200, 0xA7, 0xC8, 0x09, 0x3D, 0x20, 0xFC, 0xC9)
        write(bytes, 0x220,
            0x78, 0xA7, 0x28, 0x0C, 0x79, 0xA7, 0x28, 0x01,
            0x04, 0xCD, 0x30, 0x02, 0x05, 0x20, 0xFA, 0xC9,
            0x2A, 0x12, 0x13, 0x0D, 0x20, 0xFA, 0xC9)
        write(bytes, 0x240, 0xE0, 0xB8, 0xEA, 0x00, 0x20, 0xC9)
        write(bytes, 0x400,
            0xE5, 0xF0, 0xB8, 0xF5, 0x3E, 0x02, 0xE0, 0xB8,
            0xEA, 0x00, 0x20, 0xFA, 0x10, 0xD0, 0x3D,
            0x21, 0x00, 0x50, 0x0E, 0x0A, 0x06, 0x00,
            0xCD, 0x00, 0x02, 0x11, 0x80, 0xC8, 0xD5,
            0x01, 0x0A, 0x00, 0xE7, 0x21, 0x8A, 0xC8,
            0x36, 0x50, 0xD1, 0xF1, 0xE0, 0xB8,
            0xEA, 0x00, 0x20, 0xE1, 0xC9)
        repeat(190) { index ->
            repeat(10) { bytes[0x9000 + index * 10 + it] = 0x50 }
            write(bytes, 0x9000 + index * 10, 0x80, 0x81)
        }
        write(bytes, 0x500,
            0xF0, 0xB8, 0xF5, 0x3E, 0x03, 0xCD,
            0x40, 0x02, 0xC5, 0xD5, 0xE5)
        write(bytes, 0x520,
            0x3D, 0x01, 0x1C, 0x00, 0x21, 0x00, 0x50,
            0xCD, 0x00, 0x02, 0x11, 0x40, 0xD0,
            0x01, 0x1C, 0x00, 0xCD, 0x20, 0x02)
        write(bytes, 0x540,
            0xE1, 0xD1, 0xC1, 0xF1, 0xCD, 0x40, 0x02, 0xC9)
        repeat(151) { index ->
            write(bytes, 0xD000 + index * 28,
                index + 1, 60, 60, 60, 60, 60, 4, 4)
        }
    }

    private fun write(bytes: ByteArray, offset: Int, vararg values: Int) {
        values.forEachIndexed { index, value -> bytes[offset + index] = value.toByte() }
    }

    private fun putWord(bytes: ByteArray, offset: Int, value: Int) {
        write(bytes, offset, value and 0xFF, value ushr 8)
    }
}
