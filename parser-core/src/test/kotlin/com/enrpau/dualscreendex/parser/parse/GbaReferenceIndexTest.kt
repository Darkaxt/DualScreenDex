package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.enrpau.dualscreendex.parser.analysis.GbaItemConsumerHints
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import org.junit.Assert.*
import org.junit.Test

class GbaReferenceIndexTest {
    @Test
    fun itemHintsAreImmutableValueEvidenceIncludedInIndexEquality() {
        val sites = mutableListOf(22)
        val hints = GbaItemConsumerHints(sites, 1, 64)
        sites.clear()
        assertEquals(listOf(22), hints.sites)
        assertThrows(UnsupportedOperationException::class.java) { (hints.sites as MutableList<Int>).clear() }
        assertEquals(hints, GbaItemConsumerHints(listOf(22), 1, 64))
        val original = GbaReferenceIndex.fromTargets(emptyMap(), 16, hints)
        assertEquals(original, GbaReferenceIndex.fromTargets(emptyMap(), 16, GbaItemConsumerHints(listOf(22), 1, 64)))
        assertEquals(original.hashCode(), GbaReferenceIndex.fromTargets(emptyMap(), 16, GbaItemConsumerHints(listOf(22), 1, 64)).hashCode())
        assertNotEquals(original, GbaReferenceIndex.fromTargets(emptyMap(), 16))
        assertNotEquals(original, GbaReferenceIndex.fromTargets(emptyMap(), 16, GbaItemConsumerHints(listOf(22), 2, 1)))
    }

    @Test
    fun itemHintsPrecedeTargetTruncationAndOverflowCountsDoNotPoisonNumericIndex() {
        val f = ItemConsumerFixture()
        repeat(65) { n ->
            val entry = 0x2000 + n * 40
            f.bytes.copyInto(f.bytes, entry, f.nameGetter, f.nameGetter + 36)
            f.bl(entry + 6, f.sanitizer)
        }
        val index = requireNotNull(f.session(limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1)).gbaReferenceIndex)
        val hints = requireNotNull(index.itemConsumerHints)
        assertFalse(index.overflowed)
        assertEquals(66, hints.observedSites)
        assertEquals(64, hints.sites.size)
        assertFalse(hints.complete)
        assertEquals(79, index.referenceCount(f.root))
        assertFalse(requireNotNull(index.target(f.root)).siteEvidenceAvailable)
        assertTrue(requireNotNull(index.target(f.root)).instructionSites.isEmpty())
    }

    @Test
    fun countsCompiledLiteralReferencesThroughOneBoundedIndex() {
        val bytes = ByteArray(0x200)
        putReference(bytes, 0x20, 0x80, 0x100)
        putReference(bytes, 0x24, 0x84, 0x100)
        putReference(bytes, 0x28, 0x88, 0x140)

        val result = GbaReferenceIndexBuilder.build(RomImage(bytes))

        assertTrue(!result.overflowed)
        assertEquals(2, result.counts[0x100])
        assertEquals(1, result.counts[0x140])
    }

    @Test
    fun failsClosedBeforeRetainingMoreDistinctTargetsThanTheBudget() {
        val bytes = ByteArray(0x200)
        putReference(bytes, 0x20, 0x80, 0x100)
        putReference(bytes, 0x24, 0x84, 0x120)
        putReference(bytes, 0x28, 0x88, 0x140)

        val result = GbaReferenceIndexBuilder.build(RomImage(bytes), maxDistinctTargets = 2)

        assertTrue(result.overflowed)
        assertTrue(result.counts.isEmpty())
        assertTrue(result.overflowReason?.contains("budget exceeded") == true)
    }

    private fun putReference(bytes: ByteArray, instructionOffset: Int, literalOffset: Int, target: Int) {
        val pc = (instructionOffset + 4) and -4
        putU16(bytes, instructionOffset, 0x4800 or ((literalOffset - pc) / 4))
        putU32(bytes, literalOffset, 0x08000000 + target)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
