package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.resolvedLanguageManifest
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test

class CompiledPointerMoveDescriptionTest {
    @Test
    fun completeNativePointerConsumerRetainsThreeDashTokensAfterRelocation() {
        for (shift in listOf(0, 0x1000)) {
            val fixture = fixture(shift)
            val result = fixture.resolve()
            assertEquals(0x2000 + shift, result?.sourceOffset)
            assertEquals(16, result?.descriptions?.size)
            assertEquals("---", result?.descriptions?.get(8))
            assertEquals("A small flame attack.", result?.descriptions?.get(16))
        }
    }

    @Test
    fun pageStateLiteralsAreNotReinterpretedAsRomPointers() {
        val fixture = fixture()
        pointer(fixture.bytes, 0x464, 0x02000000 - 0x08000000)
        pointer(fixture.bytes, 0x468, 0x1000 - 0x08000000)
        assertEquals(16, fixture.resolve()?.descriptions?.size)
    }

    @Test
    fun repeatedEquivalentConsumersAgreeWithoutRankingReferences() {
        val fixture = fixture()
        consumer(fixture.bytes, 0x800, 0x2000, 0x3000, 0x1800)
        assertEquals(16, fixture.resolve()?.descriptions?.size)
    }

    @Test
    fun placeholdersDoNotAuthorizeUnownedArraysOrAReinterpretedNumericAbi() {
        for (mutation in listOf<(Fixture) -> Fixture>(
            { it.bytes.fill(0, 0x400, 0x500); it },
            { it.copy(layout = it.layout.copy(tables = ProfileTables(moveData = TableLayout(0x3000, 17, 20)))) },
            { it.copy(layout = it.layout.copy(tables = ProfileTables(moveData = TableLayout(0x3200, 17, 20,
                format = TableRecordFormat.PACKED_FLAGS_MOVE_20)))) },
        )) {
            val fixture = mutation(fixture())
            assertFalse(fixture.resolve()?.descriptions?.containsKey(8) == true)
        }
    }

    @Test
    fun localIndexWordLoadBranchAndTextArgumentMustBeUnclobbered() {
        for ((offset, replacement) in listOf(
            0x416 to 0x2500, // Overwrites the retained u16 index before the numeric branch.
            0x43c to 0x2500, // Overwrites the index between numeric and pointer consumers.
            0x432 to 0x00e8, // Wrong numeric stride.
            0x456 to 0x1e60, // Different index register.
            0x458 to 0x0040, // Wrong pointer stride.
            0x45a to 0x2000, // Clobbered pointer address.
            0x45c to 0x8801, // Halfword, not pointer.
            0x45e to 0xe000, // Executes a literal pool instead of reaching the argument block.
            0x47c to 0x2100, // Overwrites r1 text before the call.
            0x1810 to 0x2100, // Wrapper clobbers text.
            0x182e to 0x9004, // Wrong text template slot.
            0x183a to 0x4770, // Wrong frame/return.
        )) {
            val fixture = fixture()
            word(fixture.bytes, offset, replacement)
            assertFalse("mutation ${offset.toString(16)}", fixture.resolve()?.descriptions?.containsKey(8) == true)
        }
    }

    @Test
    fun arbitraryReturningCalleeIsNotAProseSink() {
        val fixture = fixture()
        word(fixture.bytes, 0x1800, 0x4770)
        assertFalse(fixture.resolve()?.descriptions?.containsKey(8) == true)
    }

    @Test
    fun competingCompleteRootsRejectEvenWhenOneContainsMalformedText() {
        for (malformed in listOf(false, true)) {
            val fixture = fixture()
            table(fixture.bytes, 0x2200, 0x6000)
            consumer(fixture.bytes, 0x800, 0x2200, 0x3000, 0x1800)
            if (malformed) fixture.bytes.fill(0, 0x6000, 0x60c0)
            assertNull(fixture.resolve())
        }
    }

    @Test
    fun provedArrayRequiresCompleteTerminatedTokenDomain() {
        for (mutation in listOf<(ByteArray) -> Unit>(
            { pointer(it, 0x2000 + 7 * 4, it.size) },
            { it.fill(0xae.toByte(), 0x5000 + 7 * 0x100, 0x5000 + 7 * 0x100 + 192) },
            { it[0x5000 + 7 * 0x100] = 0xfc.toByte(); it[0x5000 + 7 * 0x100 + 1] = 0x7f },
            { text(it, 0x5000 + 7 * 0x100, "") },
            { text(it, 0x5000 + 7 * 0x100, "--") },
        )) {
            val fixture = fixture()
            mutation(fixture.bytes)
            assertNull(fixture.resolve())
        }
    }

    @Test
    fun workRootCandidateExtentAndSiteLimitsAreTerminal() {
        for (limits in listOf(
            ResolutionLimits(maxProbeWorkPerDataset = 1),
            ResolutionLimits(maxDatasetExtentBytes = 16),
            ResolutionLimits(maxDatasetExtentBytes = 0x9000L - 1),
            ResolutionLimits(maxNominatedGbaReferenceSites = 1),
            ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
        )) {
            val fixture = fixture()
            consumer(fixture.bytes, 0x800, 0x2000, 0x3000, 0x1800)
            assertNull(fixture.resolve(limits))
        }
        for (limits in listOf(ResolutionLimits(maxProbeRootsPerDataset = 1), ResolutionLimits(maxCandidatesPerDataset = 1))) {
            val fixture = fixture()
            table(fixture.bytes, 0x2200, 0x6000)
            consumer(fixture.bytes, 0x800, 0x2200, 0x3000, 0x1800)
            assertNull(fixture.resolve(limits))
        }
    }

    @Test
    fun nativeWordPointerArraysMustBeAligned() {
        val fixture = fixture()
        fixture.bytes.copyOfRange(0x2000, 0x2040).copyInto(fixture.bytes, 0x2001)
        pointer(fixture.bytes, 0x470, 0x2001)
        assertFalse(fixture.resolve()?.descriptions?.containsKey(8) == true)
    }

    @Test
    fun malformedNominatedSitesStillConsumeTheGlobalSiteAllowance() {
        val fixture = fixture()
        consumer(fixture.bytes, 0x800, 0x2000, 0x3000, 0x1800)
        word(fixture.bytes, 0x832, 0x00e8)
        assertNull(fixture.resolve(ResolutionLimits(maxNominatedGbaReferenceSites = 1)))
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationPropagatesDuringTheCompleteConsumerScan() {
        var checks = 0
        val fixture = fixture()
        MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout,
            cancellation = ParserCancellationToken { if (++checks == 3) throw ParserCancellationException() })
    }

    private data class Fixture(val bytes: ByteArray, val layout: ResolvedRomLayout) {
        fun resolve(limits: ResolutionLimits = ResolutionLimits()) =
            MoveDescriptionMaterializer.materialize(RomImage(bytes), layout, limits = limits)
    }

    private fun fixture(shift: Int = 0): Fixture {
        val bytes = ByteArray(0x9000) { 0x7f }
        table(bytes, 0x2000 + shift, 0x5000 + shift)
        consumer(bytes, 0x400 + shift, 0x2000 + shift, 0x3000 + shift, 0x1800 + shift)
        return Fixture(bytes, ResolvedRomLayout(
            family = EngineFamily.EMERALD, generation = 3, platform = Platform.GBA,
            speciesCount = 4, moveCount = 17,
            tables = ProfileTables(moveData = TableLayout(0x3000 + shift, 17, 20,
                format = TableRecordFormat.PACKED_FLAGS_MOVE_20)),
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
        ))
    }

    private fun table(bytes: ByteArray, root: Int, prose: Int) {
        repeat(16) { index ->
            pointer(bytes, root + index * 4, prose + index * 0x100)
            text(bytes, prose + index * 0x100, if (index == 7) "---" else "A small flame attack.")
        }
    }

    /** Individually assembled local numeric/pointer branch and text-wrapper ABI; no ROM payload. */
    private fun consumer(bytes: ByteArray, entry: Int, root: Int, numeric: Int, wrapper: Int) {
        words(bytes, entry, 0xb570, 0xb082, 0x0400, 0x0c05, 0x1c2c)
        words(bytes, entry + 0x0a, 0x4815, 0x2102)
        bl(bytes, entry + 0x0e, 0x1700)
        words(bytes, entry + 0x12, 0x0600, 0x0e06, 0x1c30, 0x2100)
        bl(bytes, entry + 0x1a, 0x1700)
        words(bytes, entry + 0x1e, 0x2d00, 0xd04c, 0x4810, 0x6800, 0x4910,
            0x1840, 0x7800, 0x2802, 0xd12d)
        words(bytes, entry + 0x30, 0x490e, 0x00a8, 0x1940, 0x0080, 0x1840, 0x7804)
        words(bytes, entry + 0x3c, 0x1c28)
        bl(bytes, entry + 0x3e, 0x1700)
        words(bytes, entry + 0x42, 0x0600, 0x0e00)
        bl(bytes, entry + 0x46, 0x1700)
        words(bytes, entry + 0x4a, 0x1c28)
        bl(bytes, entry + 0x4c, 0x1700)
        pointer(bytes, entry + 0x60, 0x1600)
        pointer(bytes, entry + 0x64, 0x1600)
        pointer(bytes, entry + 0x68, 0x1600)
        pointer(bytes, entry + 0x6c, numeric)
        words(bytes, entry + 0x50, 0x2ccc, 0xd00f, 0x4906, 0x1e68, 0x0080, 0x1840, 0x6801, 0xe00a)
        pointer(bytes, entry + 0x70, root)
        words(bytes, entry + 0x76, 0x2000, 0x9000, 0x9001, 0x1c30, 0x2206, 0x2301)
        bl(bytes, entry + 0x82, wrapper)
        words(bytes, wrapper, 0xb570, 0xb085, 0x9c09, 0x9d0a, 0x0600, 0x0e00,
            0x0612, 0x0e12, 0x061b, 0x0e1b, 0x0624, 0x0e24, 0x062d, 0x0e2d,
            0x2600, 0x9600, 0x9401, 0x006c, 0x1964, 0x4d06, 0x1964, 0x9402,
            0x9603, 0x9104, 0x2101)
        bl(bytes, wrapper + 0x32, 0x1700)
        words(bytes, wrapper + 0x36, 0xb005, 0xbc70, 0xbc01, 0x4700)
        pointer(bytes, wrapper + 0x40, 0x1600)
    }

    private fun text(bytes: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            bytes[offset + index] = when (char) {
                ' ' -> 0
                '-' -> 0xae.toByte()
                '.' -> 0xad.toByte()
                in 'A'..'Z' -> (0xbb + char.code - 'A'.code).toByte()
                in 'a'..'z' -> (0xd5 + char.code - 'a'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        bytes[offset + value.length] = 0xff.toByte()
    }

    private fun words(bytes: ByteArray, offset: Int, vararg values: Int) =
        values.forEachIndexed { index, value -> word(bytes, offset + index * 2, value) }

    private fun word(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun pointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000 + target
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun bl(bytes: ByteArray, site: Int, target: Int) {
        val displacement = target - site - 4
        word(bytes, site, 0xf000 or ((displacement shr 12) and 0x7ff))
        word(bytes, site + 2, 0xf800 or ((displacement shr 1) and 0x7ff))
    }
}
