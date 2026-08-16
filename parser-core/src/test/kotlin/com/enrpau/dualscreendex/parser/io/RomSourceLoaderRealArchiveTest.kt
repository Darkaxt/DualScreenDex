package com.enrpau.dualscreendex.parser.io

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Path

class RomSourceLoaderRealArchiveTest {
    @Test
    fun `Unbound ZIP and 7z load the same exact ROM payload`() {
        val zip = configuredPath("DUALDEX_UNBOUND_ZIP")
        val sevenZip = configuredPath("DUALDEX_UNBOUND_7Z")

        val loaded = listOf(RomSourceLoader.load(zip), RomSourceLoader.load(sevenZip))

        assertEquals(setOf(EXPECTED_SHA256), loaded.map { it.rom.sha256 }.toSet())
        assertEquals(setOf(EXPECTED_CRC32), loaded.map { it.rom.crc32 }.toSet())
        assertEquals(setOf(32 * 1024 * 1024), loaded.map { it.rom.size }.toSet())
        assertEquals(
            setOf("Pokemon Unbound.gba", "Unbound (v2.1.1.1).gba"),
            loaded.map { it.displayName.substringAfter('!') }.toSet(),
        )
    }

    private fun configuredPath(name: String): Path {
        val configured = System.getenv(name)
        assumeTrue("set $name to run this real-ROM control", !configured.isNullOrBlank())
        return Path.of(requireNotNull(configured))
    }

    private companion object {
        const val EXPECTED_SHA256 = "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"
        const val EXPECTED_CRC32 = "4B3D4957"
    }
}
