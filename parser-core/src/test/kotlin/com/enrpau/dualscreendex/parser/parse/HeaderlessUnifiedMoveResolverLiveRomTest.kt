package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HeaderlessUnifiedMoveResolverLiveRomTest {
    @Test
    fun selectsTheUniqueCompiledReferencedOrdinaryMoveTableAndRejectsRealMutations() {
        val configured = System.getenv("DUALDEX_DREAMSTONE_ROM")
        assumeTrue("set DUALDEX_DREAMSTONE_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val original = Files.readAllBytes(path)
        assertEquals(EXPECTED_SHA, RomImage(original).sha256)

        val originalRom = RomImage(original)
        val originalSession = RomAnalysisSession(originalRom, RomHeaderReader.read(originalRom))
        val index = requireNotNull(originalSession.gbaReferenceIndex)
        assertTrue(!index.overflowed)
        assertEquals(379, index.target(MOVE_ROOT)?.count)
        val completeSites = originalSession.nominatedGbaReferenceSites(MOVE_ROOT)
        assertTrue(completeSites?.siteEvidenceAvailable == true)
        assertEquals(379, completeSites?.instructionSites?.size)
        val selected = HeaderlessUnifiedMoveResolver.resolve(originalSession, MOVE_COUNT)
        assertNotNull(selected)
        requireNotNull(selected)
        assertEquals(MOVE_ROOT, selected.tables.moveNames?.offset)
        assertEquals(MOVE_ROOT, selected.tables.moveData?.offset)
        assertEquals(MOVE_COUNT, selected.moveCount)
        assertEquals(MOVE_COUNT, selected.resolvedMoveDetails.rows.size)
        assertTrue(selected.moveNamesEvidence.compatible)
        assertTrue(selected.moveDataEvidence.compatible)

        val malformed = original.copyOf()
        putU32(malformed, MOVE_ROOT + MOVE_STRIDE, 0x07000000)
        assertNull(resolve(malformed))

        val ambiguous = original.copyOf()
        ambiguous.copyInto(
            destination = ambiguous,
            destinationOffset = DUPLICATE_ROOT,
            startIndex = MOVE_ROOT,
            endIndex = MOVE_ROOT + MOVE_COUNT * MOVE_STRIDE,
        )
        putU32(ambiguous, COMPILED_LITERAL, GBA_ROM_BASE + DUPLICATE_ROOT)
        assertNull(resolve(ambiguous))
    }

    private fun resolve(bytes: ByteArray): HeaderlessUnifiedMoveResolver.Resolution? {
        val rom = RomImage(bytes)
        return HeaderlessUnifiedMoveResolver.resolve(
            session = RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            ordinaryMoveCount = MOVE_COUNT,
        )
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val EXPECTED_SHA = "ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220"
        const val GBA_ROM_BASE = 0x08000000
        const val MOVE_ROOT = 0x759858
        const val MOVE_STRIDE = 48
        const val MOVE_COUNT = 848
        const val COMPILED_LITERAL = 0x9BCC
        const val DUPLICATE_ROOT = 0x1BCA098
    }
}
