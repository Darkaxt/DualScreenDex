package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class Gen3RuntimeMemoryLayoutResolverTest {
    @Test
    fun decodesTheLiveBattleFieldAddressAndMaskFromRomInstructions() {
        val bytes = ByteArray(0x1000)
        repeat(32) { putU32(bytes, it * 4, 0x03001574) }
        repeat(3) { putU32(bytes, 0x200 + it * 4, 0x030019AC) }
        repeat(4) { putU32(bytes, 0x300 + it * 4, 0x03002378) }
        writeBattleFlagMutation(bytes, 0x400, 0x03001574, 0x439, 0x02, set = true)
        writeBattleFlagMutation(bytes, 0x440, 0x03001574, 0x439, 0x02, set = false)

        assertEquals(
            CatalogGen3RuntimeMemoryLayout(
                mainAddress = 0x03001574,
                inBattleAddress = 0x030019AD,
                inBattleMask = 0x02,
                saveBlock1MapGroupOffset = 4,
                saveBlock1MapNumberOffset = 5,
                multiUsePlayerCursorAddress = null,
                multiUsePlayerCursorEvidence = null,
            ),
            Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(bytes)),
        )
    }

    @Test
    fun failsClosedWhenTheMainAbiIsMissingOrAmbiguous() {
        assertNull(Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(ByteArray(0x1000))))

        val bytes = ByteArray(0x2000)
        writeCandidate(bytes, 0, 0x03001000)
        writeCandidate(bytes, 0x800, 0x03002000)
        assertNull(Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(bytes)))
    }

    @Test
    fun recognizesTheSourceVerifiedModernEmerald35Abi() {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))

        assertEquals(
            CatalogGen3RuntimeMemoryLayout(
                mainAddress = 0x03001574,
                inBattleAddress = 0x030019AD,
                inBattleMask = 0x02,
                saveBlock1MapGroupOffset = 4,
                saveBlock1MapNumberOffset = 5,
                multiUsePlayerCursorAddress = null,
                multiUsePlayerCursorEvidence = null,
            ),
            Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(Files.readAllBytes(path))),
        )
    }

    private fun writeCandidate(bytes: ByteArray, start: Int, base: Int) {
        repeat(32) { putU32(bytes, start + it * 4, base) }
        repeat(3) { putU32(bytes, start + 0x200 + it * 4, base + 0x438) }
    }

    private fun writeBattleFlagMutation(
        bytes: ByteArray,
        start: Int,
        base: Int,
        fieldOffset: Int,
        mask: Int,
        set: Boolean,
    ) {
        val baseLiteral = start + 0x20
        val offsetLiteral = start + 0x24
        putU16(bytes, start, 0x2000 or mask) // mov r0, #mask
        putU16(bytes, start + 2, literalLoad(start + 2, register = 3, literalOffset = baseLiteral))
        putU16(bytes, start + 4, literalLoad(start + 4, register = 1, literalOffset = offsetLiteral))
        putU16(bytes, start + 6, 0x5C5A) // ldrb r2, [r3, r1]
        putU16(bytes, start + 8, if (set) 0x4302 else 0x4382) // orr/bic r2, r0
        putU16(bytes, start + 10, 0x545A) // strb r2, [r3, r1]
        putU16(bytes, start + 12, 0x4770) // bx lr
        putU32(bytes, baseLiteral, base)
        putU32(bytes, offsetLiteral, fieldOffset)
    }

    private fun literalLoad(instructionOffset: Int, register: Int, literalOffset: Int): Int {
        val alignedPc = (instructionOffset + 4) and -4
        return 0x4800 or (register shl 8) or ((literalOffset - alignedPc) / 4)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
