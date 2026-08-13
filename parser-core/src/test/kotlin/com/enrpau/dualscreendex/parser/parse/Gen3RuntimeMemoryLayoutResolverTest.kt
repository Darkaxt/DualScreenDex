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
        repeat(8) { putU32(bytes, 0x500 + it * 4, 0x02001004) }
        repeat(3) { putU32(bytes, 0x540 + it * 4, 0x02001001) }
        writeBattleLayoutReferences(bytes, 0x600, 0x0200203C)
        writeBattleFlagMutation(bytes, 0x400, 0x03001574, 0x439, 0x02, set = true)
        writeBattleFlagMutation(bytes, 0x440, 0x03001574, 0x439, 0x02, set = false)
        writeBattleTypeFlagCheck(bytes, 0x800, 0x02001234, 28)
        writeBattleTypeFlagCheck(bytes, 0x820, 0x02001234, 28)
        writeBattleTypeFlagCheck(bytes, 0x840, 0x02001234, 30)
        writeBattleTypeFlagCheck(bytes, 0x860, 0x02001234, 30)
        writeBattleTypeFlagCheck(bytes, 0x880, 0x02001234, 22)

        assertEquals(
            CatalogGen3RuntimeMemoryLayout(
                mainAddress = 0x03001574,
                inBattleAddress = 0x030019AD,
                inBattleMask = 0x02,
                saveBlock1MapGroupOffset = 4,
                saveBlock1MapNumberOffset = 5,
                multiUsePlayerCursorAddress = null,
                multiUsePlayerCursorEvidence = null,
                playerPartyCountAddress = 0x02001001,
                playerPartyAddress = 0x02001004,
                battleMonsAddress = 0x0200203C,
                battleTypeFlagsAddress = 0x02001234,
                trainerBattleMask = 1 shl 3,
                nonWildBattleMask = 0x8FFF8B72.toInt(),
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
    fun keepsTheMainAbiButWithholdsAnAmbiguousLivePartyPair() {
        val bytes = ByteArray(0x2000)
        writeCandidate(bytes, 0, 0x03001000)
        writeBattleFlagMutation(bytes, 0x500, 0x03001000, 0x439, 0x02, set = true)
        writeBattleFlagMutation(bytes, 0x540, 0x03001000, 0x439, 0x02, set = false)
        repeat(8) { putU32(bytes, 0x700 + it * 4, 0x02001004) }
        repeat(3) { putU32(bytes, 0x740 + it * 4, 0x02001001) }
        repeat(8) { putU32(bytes, 0x780 + it * 4, 0x02002004) }
        repeat(3) { putU32(bytes, 0x7C0 + it * 4, 0x02002001) }

        val resolved = requireNotNull(Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(bytes)))

        assertNull(resolved.playerPartyCountAddress)
        assertNull(resolved.playerPartyAddress)
    }

    @Test
    fun keepsTheMainAbiButWithholdsAmbiguousBattleLayoutReferences() {
        val bytes = ByteArray(0x3000)
        writeCandidate(bytes, 0, 0x03001000)
        writeBattleFlagMutation(bytes, 0x500, 0x03001000, 0x439, 0x02, set = true)
        writeBattleFlagMutation(bytes, 0x540, 0x03001000, 0x439, 0x02, set = false)
        writeBattleLayoutReferences(bytes, 0x800, 0x0200203C)
        writeBattleLayoutReferences(bytes, 0xA00, 0x0200303C)

        val resolved = requireNotNull(Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(bytes)))

        assertNull(resolved.battleMonsAddress)
    }

    @Test
    fun keepsTheMainAbiButWithholdsAmbiguousBattleTypeConsumerRoots() {
        val bytes = ByteArray(0x3000)
        writeCandidate(bytes, 0, 0x03001000)
        writeBattleFlagMutation(bytes, 0x500, 0x03001000, 0x439, 0x02, set = true)
        writeBattleFlagMutation(bytes, 0x540, 0x03001000, 0x439, 0x02, set = false)
        listOf(0x02001234, 0x02002234).forEachIndexed { rootIndex, address ->
            val start = 0x1000 + rootIndex * 0x100
            writeBattleTypeFlagCheck(bytes, start, address, 28)
            writeBattleTypeFlagCheck(bytes, start + 0x20, address, 28)
            writeBattleTypeFlagCheck(bytes, start + 0x40, address, 30)
            writeBattleTypeFlagCheck(bytes, start + 0x60, address, 30)
            writeBattleTypeFlagCheck(bytes, start + 0x80, address, 22)
        }

        val resolved = requireNotNull(Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(bytes)))

        assertNull(resolved.battleTypeFlagsAddress)
        assertNull(resolved.trainerBattleMask)
        assertNull(resolved.nonWildBattleMask)
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
                playerPartyCountAddress = 0x0201D9C5,
                playerPartyAddress = 0x0201D9C8,
                battleMonsAddress = 0x0200143C,
                battleTypeFlagsAddress = 0x020003A0,
                trainerBattleMask = 1 shl 3,
                nonWildBattleMask = 0x8FFF8B72.toInt(),
            ),
            Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(Files.readAllBytes(path))),
        )
    }

    private fun writeCandidate(bytes: ByteArray, start: Int, base: Int) {
        repeat(32) { putU32(bytes, start + it * 4, base) }
        repeat(3) { putU32(bytes, start + 0x200 + it * 4, base + 0x438) }
    }

    private fun writeBattleLayoutReferences(bytes: ByteArray, start: Int, battleMonsAddress: Int) {
        val related = listOf(
            battleMonsAddress to 12,
            battleMonsAddress - 0x1C to 6,
            battleMonsAddress - 0x10 to 4,
            battleMonsAddress + 0x438 to 5,
            battleMonsAddress + 0x43C to 3,
            battleMonsAddress + 4 * 0x58 to 7,
        )
        var offset = start
        related.forEach { (address, references) ->
            repeat(references) {
                putU32(bytes, offset, address)
                offset += 4
            }
        }
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

    private fun writeBattleTypeFlagCheck(bytes: ByteArray, start: Int, address: Int, shift: Int) {
        val literal = start + 0x10
        putU16(bytes, start, literalLoad(start, register = 3, literalOffset = literal))
        putU16(bytes, start + 2, 0x681B) // ldr r3, [r3]
        putU16(bytes, start + 4, (shift shl 6) or (3 shl 3) or 3) // lsls r3, r3, #shift
        putU16(bytes, start + 6, 0xD500) // bpl
        putU16(bytes, start + 8, 0x4770) // bx lr
        putU32(bytes, literal, address)
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
