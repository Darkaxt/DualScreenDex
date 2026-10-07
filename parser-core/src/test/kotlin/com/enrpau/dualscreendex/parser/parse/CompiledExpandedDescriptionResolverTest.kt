package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.descriptions.putExpandedDescription
import com.enrpau.dualscreendex.parser.dataset.descriptions.putU16
import com.enrpau.dualscreendex.parser.dataset.descriptions.putU32
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test

class CompiledExpandedDescriptionResolverTest {
    @Test
    fun relocatedCompleteAccessorsFieldsBoundariesAndAliasJoinsResolve() {
        for (root in listOf(0x2000, 0x2800)) {
            val fixture = expandedDexFixture(root)
            val resolved = requireNotNull(resolve(fixture.bytes)) { "complete compiled expanded Dex contract is missing" }
            assertEquals(root.toLong(), resolved.table.offset)
            assertEquals(3L, resolved.table.count)
            assertEquals(listOf(20), resolved.table.pointerOffsets)
            assertEquals(mapOf(1 to 1, 2 to 2, 3 to 2, 4 to 1), resolved.compiledRowBinding!!.rows)
            assertEquals(700, resolved.catalogDescriptions().getValue(1).height)
            assertEquals(6900, resolved.catalogDescriptions().getValue(1).weight)
        }
    }

    @Test
    fun equivalentRelocatedLookupRootsDoNotCreateConflictingAliasAuthority() {
        val fixture = expandedDexFixture()
        putExpandedLookup(fixture.bytes, 0xc0, 0x1200)
        listOf(1, 2, 2, 1).forEachIndexed { i, value -> putU16(fixture.bytes, 0x1200 + i * 2, value) }
        putExpandedCaller(fixture.bytes, 0x220, 0xc0, 0x300)
        assertNotNull(resolve(fixture.bytes))
    }

    @Test
    fun pointerFieldNeedsUnclobberedFullStrideAndWordLoad() {
        for (mutation in listOf<(ByteArray) -> Unit>(
            { putU16(it, 0x182, 0x00b0) }, // wrong index scale
            { putU16(it, 0x184, 0x2000) }, // clobbered index
            { putU16(it, 0x188, 0x3110) }, // legacy pointer field
            { putU16(it, 0x18a, 0x1849) }, // wrong address destination
            { putU16(it, 0x18c, 0x8804) }, // halfword, not pointer
            { putU16(it, 0x184, 0xe000) }, // branch before read
            { putExpandedBl(it, 0x184, 0x700) }, // call before read
        )) {
            val fixture = expandedDexFixture()
            mutation(fixture.bytes)
            assertNull(resolve(fixture.bytes))
        }
    }

    @Test
    fun bothSelectorBranchesMustUseTheCorrectRootDimensionsAndReturn() {
        for (mutation in listOf<(ByteArray) -> Unit>(
            { putU16(it, 0x300 + 12, 0xd103) },
            { putU16(it, 0x300 + 32, 0x8988) },
            { putU16(it, 0x300 + 50, 0x89c8) },
            { putU16(it, 0x300 + 54, 0x4700) },
            { putU32(it, 0x338, 0x08002800) },
            { putU16(it, 0x300, 0xb510) },
        )) {
            val fixture = expandedDexFixture()
            mutation(fixture.bytes)
            assertNull(resolve(fixture.bytes))
        }
    }

    @Test
    fun scalarRunAndUnconsumedPaddingAreNotExtentAuthority() {
        val fixture = expandedDexFixture()
        fixture.bytes.fill(0, 0x500, 0x524)
        assertNull(resolve(fixture.bytes))
    }

    @Test
    fun paletteBoundaryRequiresExactObjectSizeCopyServiceAndPreservedCallee() {
        for (mutation in listOf<(ByteArray) -> Unit>(
            { putU16(it, 0x50a, 0x2210) },
            { putU32(it, 0x520, 0x08002070) },
            { putU16(it, 0x600 + 18, 0x0c2d) },
            { putU16(it, 0x600 + 44, 0x4708) },
            { putU16(it, 0x700, 0xdf0c) },
            { putExpandedBl(it, 0x600 + 36, 0x704) },
        )) {
            val fixture = expandedDexFixture()
            mutation(fixture.bytes)
            assertNull(resolve(fixture.bytes))
        }
    }

    @Test
    fun mapReturnValueMustReachTheAccessorWithoutClobberOrWrongFrame() {
        for (mutation in listOf<(ByteArray) -> Unit>(
            { putU16(it, 0x204, 0x2000) },
            { putU16(it, 0x206, 0x0c08) },
            { putU16(it, 0x208, 0x2102) },
            { putU16(it, 0x40, 0xb510) },
            { putU16(it, 0x40 + 18, 0x8809) },
            { putU16(it, 0x40 + 12, 0x3a01) },
        )) {
            val fixture = expandedDexFixture()
            mutation(fixture.bytes)
            assertNull(resolve(fixture.bytes))
        }
    }

    @Test
    fun incompleteOutOfExtentAndConflictingNativeJoinsReject() {
        for (values in listOf(listOf(1, 2, 3, 1), listOf(1, 0, 2, 1), listOf(1, 1, 1, 1))) {
            val fixture = expandedDexFixture()
            values.forEachIndexed { i, value -> putU16(fixture.bytes, 0x1000 + i * 2, value) }
            assertNull(resolve(fixture.bytes))
        }
        val fixture = expandedDexFixture()
        putExpandedLookup(fixture.bytes, 0xc0, 0x1200)
        listOf(2, 1, 2, 1).forEachIndexed { i, value -> putU16(fixture.bytes, 0x1200 + i * 2, value) }
        putExpandedCaller(fixture.bytes, 0x220, 0xc0, 0x300)
        assertNull(resolve(fixture.bytes))
    }

    @Test
    fun competingCompleteDescriptionRootsCannotBeRanked() {
        val fixture = expandedDexFixture()
        putExpandedContract(fixture.bytes, 0x2800, 0x400, 0xc0, 0x1200, 0x880, 0x900, 0xa00, 0xb00, 0x4400)
        putExpandedCaller(fixture.bytes, 0x220, 0xc0, 0x400)
        assertNull(resolve(fixture.bytes))
    }

    @Test
    fun malformedActiveRowAndTruncatedContractReject() {
        val fixture = expandedDexFixture()
        putU32(fixture.bytes, fixture.root + 2 * 36 + 20, 0x02000000)
        assertNull(resolve(fixture.bytes))
        assertNull(resolve(expandedDexFixture().bytes.copyOf(0x2030)))
    }

    @Test
    fun workCandidateRootNominationAndExtentBudgetsFailClosed() {
        for (limits in listOf(ResolutionLimits(maxProbeWorkPerDataset = 1),
            ResolutionLimits(maxDatasetExtentBytes = 35), ResolutionLimits(maxNominatedGbaReferenceSites = 1))) {
            val fixture = expandedDexFixture()
            putExpandedCaller(fixture.bytes, 0x220, 0x40, 0x300)
            assertNull(resolve(fixture.bytes, limits))
        }
        for (limits in listOf(ResolutionLimits(maxProbeRootsPerDataset = 1), ResolutionLimits(maxCandidatesPerDataset = 1))) {
            val fixture = expandedDexFixture()
            putExpandedContract(fixture.bytes, 0x2800, 0x400, 0xc0, 0x1200, 0x880, 0x900, 0xa00, 0xb00, 0x4400)
            putExpandedCaller(fixture.bytes, 0x220, 0xc0, 0x400)
            assertNull(resolve(fixture.bytes, limits))
        }
    }

    @Test
    fun exhaustedExtentNominationCannotHideACompetingRoot() {
        val fixture = expandedDexFixture()
        putExpandedContract(fixture.bytes, 0x2800, 0x400, 0xc0, 0x1200, 0x880, 0x900, 0xa00, 0xb00, 0x4400)
        putExpandedCaller(fixture.bytes, 0x220, 0xc0, 0x400)
        for (site in listOf(0xc00, 0xc40, 0xc80, 0xcc0)) {
            fixture.bytes.copyInto(fixture.bytes, site, 0x900, 0x924)
            putExpandedBl(fixture.bytes, site + 12, 0xa00)
        }
        assertNull(resolve(fixture.bytes, ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 4)))
    }

    @Test
    fun pointerLiteralNominationHonorsThePerCandidateReferenceBudget() {
        assertNull(resolve(expandedDexFixture().bytes, ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 2)))
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationPropagatesInsteadOfClosingAsMissing() {
        val fixture = expandedDexFixture()
        var checks = 0
        Gen3CompiledExpandedDescriptionResolver.resolve(RomAnalysisSession(RomImage(fixture.bytes),
            RomHeader(Platform.GBA, "SYNTHETIC"), cancellation = ParserCancellationToken {
                if (++checks == 3) throw ParserCancellationException()
            }), 5, PokemonTextCodec.gbaEnglish)
    }

    private fun resolve(bytes: ByteArray, limits: ResolutionLimits = ResolutionLimits()) =
        Gen3CompiledExpandedDescriptionResolver.resolve(RomAnalysisSession(RomImage(bytes),
            RomHeader(Platform.GBA, "SYNTHETIC"), limits = limits), 5, PokemonTextCodec.gbaEnglish)
}

internal data class ExpandedDexFixture(val bytes: ByteArray, val root: Int)

internal fun expandedDexFixture(root: Int = 0x2000): ExpandedDexFixture {
    val bytes = ByteArray(0x5000) { 0x7f }
    putExpandedContract(bytes, root, 0x300, 0x40, 0x1000, 0x180, 0x500, 0x600, 0x700, 0x4000)
    putExpandedCaller(bytes, 0x200, 0x40, 0x300)
    return ExpandedDexFixture(bytes, root)
}

internal fun putExpandedContract(bytes: ByteArray, root: Int, accessor: Int, wrapper: Int, map: Int,
    pointer: Int, palette: Int, callee: Int, copy: Int, text: Int) {
    bytes.fill(0, root, root + 3 * 36)
    repeat(3) { i -> putExpandedDescription(bytes, root + i * 36, text + i * 0x40,
        if (i == 0) "UNKNOWN" else "ABCDEFGHIJKLM", if (i == 0) 0 else 700, if (i == 0) 0 else 6900) }
    putExpandedLookup(bytes, wrapper, map)
    listOf(1, 2, 2, 1).forEachIndexed { i, value -> putU16(bytes, map + i * 2, value) }
    listOf(0xb500, 0x0400, 0x0c02, 0x0609, 0x0e09, 0x2900, 0xd003, 0x2901, 0xd00a, 0x2001, 0xe00e)
        .forEachIndexed { i, word -> putU16(bytes, accessor + i * 2, word) }
    for ((offset, field) in listOf(22 to 14, 40 to 16)) {
        listOf(0x4803, 0x00d1, 0x1889, 0x0089, 0x1809, 0x8808 or ((field / 2) shl 6))
            .forEachIndexed { i, word -> putU16(bytes, accessor + offset + i * 2, word) }
        putU16(bytes, accessor + offset + 12, if (offset == 22) 0xe007 else 0xbc02)
        if (offset == 40) putU16(bytes, accessor + 54, 0x4708)
        putU32(bytes, ((accessor + offset + 4) and -4) + 12, 0x08000000 + root)
    }
    listOf(0x4907, 0x00f0, 0x1980, 0x0080, 0x3114, 0x1840, 0x6804)
        .forEachIndexed { i, word -> putU16(bytes, pointer + i * 2, word) }
    putU32(bytes, pointer + 32, 0x08000000 + root)
    bytes.fill(0, root + 3 * 36, root + 3 * 36 + 32)
    listOf(0x4807, 0x7961, 0x0909, 0x3110, 0x0109, 0x2220)
        .forEachIndexed { i, word -> putU16(bytes, palette + i * 2, word) }
    putExpandedBl(bytes, palette + 12, callee)
    putU32(bytes, palette + 32, 0x08000000 + root + 3 * 36)
    listOf(0xb570, 0x1c06, 0x1c0c, 0x1c15, 0x0424, 0x042d, 0x0be4, 0x4908,
        0x1861, 0x0c6d, 0x1c2a, 0, 0, 0x4806, 0x1824, 0x1c30, 0x1c21, 0x1c2a,
        0, 0, 0xbc70, 0xbc01, 0x4700, 0).forEachIndexed { i, word -> putU16(bytes, callee + i * 2, word) }
    putExpandedBl(bytes, callee + 22, copy)
    putExpandedBl(bytes, callee + 36, copy)
    putU32(bytes, callee + 48, 0x02001000)
    putU32(bytes, callee + 52, 0x02001400)
    putU16(bytes, copy, 0xdf0b)
    putU16(bytes, copy + 2, 0x4770)
}

internal fun putExpandedLookup(bytes: ByteArray, start: Int, root: Int) {
    listOf(0xb500, 0x0400, 0x0c01, 0x2900, 0xd008, 0x4803, 0x3901, 0x0049, 0x1809, 0x8808, 0xe003, 0)
        .forEachIndexed { i, word -> putU16(bytes, start + i * 2, word) }
    putU32(bytes, start + 24, 0x08000000 + root)
    listOf(0x2000, 0xbc02, 0x4708).forEachIndexed { i, word -> putU16(bytes, start + 28 + i * 2, word) }
}

internal fun putExpandedCaller(bytes: ByteArray, start: Int, wrapper: Int, accessor: Int) {
    putExpandedBl(bytes, start, wrapper)
    putU16(bytes, start + 4, 0x0400)
    putU16(bytes, start + 6, 0x0c00)
    putU16(bytes, start + 8, 0x2101)
    putExpandedBl(bytes, start + 10, accessor)
}

internal fun putExpandedBl(bytes: ByteArray, start: Int, target: Int) {
    val displacement = target - start - 4
    putU16(bytes, start, 0xf000 or ((displacement shr 12) and 0x7ff))
    putU16(bytes, start + 2, 0xf800 or ((displacement shr 1) and 0x7ff))
}
