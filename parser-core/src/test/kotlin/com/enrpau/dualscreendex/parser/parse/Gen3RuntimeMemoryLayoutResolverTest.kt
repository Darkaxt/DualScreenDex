package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.catalog.RuntimeMemoryEvidence
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class Gen3RuntimeMemoryLayoutResolverTest {
    @Test
    fun publishesTheMainAbiOnlyWhenTheRomStructurallyReferencesItsBaseAndTail() {
        val bytes = ByteArray(0x1000)
        repeat(32) { putU32(bytes, it * 4, 0x03001574) }
        repeat(3) { putU32(bytes, 0x200 + it * 4, 0x030019AC) }
        repeat(4) { putU32(bytes, 0x300 + it * 4, 0x03002378) }

        assertEquals(
            CatalogGen3RuntimeMemoryLayout(
                mainStructSize = 0x43C,
                inBattleByteOffset = 0x439,
                inBattleMask = 0x02,
                saveBlock1MapGroupOffset = 4,
                saveBlock1MapNumberOffset = 5,
                multiUsePlayerCursorOffsetFromMain = 0xE04,
                multiUsePlayerCursorEvidence = RuntimeMemoryEvidence.SOURCE_PROVEN_UNTESTED,
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
                0x43C, 0x439, 0x02, 4, 5,
                multiUsePlayerCursorOffsetFromMain = 0xE04,
                multiUsePlayerCursorEvidence = RuntimeMemoryEvidence.SOURCE_PROVEN_UNTESTED,
            ),
            Gen3RuntimeMemoryLayoutResolver.resolve(RomImage(Files.readAllBytes(path))),
        )
    }

    private fun writeCandidate(bytes: ByteArray, start: Int, base: Int) {
        repeat(32) { putU32(bytes, start + it * 4, base) }
        repeat(3) { putU32(bytes, start + 0x200 + it * 4, base + 0x438) }
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
