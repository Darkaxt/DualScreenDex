package com.enrpau.dualscreendex.parser.analysis

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Test

class NominatedGbaReferenceSitesTest {
    @Test
    fun enumeratesAnOverflowedHotTargetOncePerSessionAndFailsClosedWhenIncomplete() {
        val target = 0x300
        val bytes = ByteArray(0x500)
        val sites = listOf(0x20, 0x40, 0x60)
        sites.forEachIndexed { index, site ->
            val literal = 0x100 + index * 4
            putU16(bytes, site, 0x4800 or ((literal - ((site + 4) and -4)) / 4))
            putU32(bytes, literal, 0x08000000L + target)
        }
        var builds = 0
        val factory = GbaReferenceIndexFactory { rom, limits ->
            builds += 1
            SafeGbaReferenceIndexBuilder.build(rom, limits)
        }
        val session = RomAnalysisSession(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, ""),
            limits = ResolutionLimits(
                maxCompiledReferenceSitesPerCandidate = 2,
                maxNominatedGbaReferenceSites = 3,
            ),
            gbaReferenceIndexFactory = factory,
        )

        assertFalse(requireNotNull(session.gbaReferenceIndex?.target(target)).siteEvidenceAvailable)
        val first = requireNotNull(session.nominatedGbaReferenceSites(target))
        assertEquals(sites, first.instructionSites)
        assertSame(first, session.nominatedGbaReferenceSites(target))
        assertEquals(1, builds)

        val incomplete = RomAnalysisSession(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, ""),
            limits = ResolutionLimits(
                maxCompiledReferenceSitesPerCandidate = 2,
                maxNominatedGbaReferenceSites = 2,
            ),
        ).nominatedGbaReferenceSites(target)
        assertFalse(requireNotNull(incomplete).siteEvidenceAvailable)
        assertEquals(emptyList<Int>(), incomplete.instructionSites)
        assertNotSame(first, incomplete)

        val mismatchedCount = RomAnalysisSession(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, ""),
            gbaReferenceIndexFactory = GbaReferenceIndexFactory {
                _, _ -> GbaReferenceIndex.countsOnlyForTesting(mapOf(target to sites.size + 1))
            },
        ).nominatedGbaReferenceSites(target)
        assertFalse(requireNotNull(mismatchedCount).siteEvidenceAvailable)
        assertEquals(emptyList<Int>(), mismatchedCount.instructionSites)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
