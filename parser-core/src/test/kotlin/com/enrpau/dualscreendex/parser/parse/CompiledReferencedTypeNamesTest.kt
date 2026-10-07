package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer
import com.enrpau.dualscreendex.parser.catalog.defaultTextProjection
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsCodec
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableLayout
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableOutcome
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.resolvedLanguageManifest
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test

class CompiledReferencedTypeNamesTest {
    @Test
    fun selectedNumericTypeConsumerOwnsOnlyTheRequestedPrefixAfterRelocation() {
        for (shift in listOf(0, 0x1000)) {
            val fixture = fixture(shift)
            val result = fixture.resolve()
            assertNotNull(result)
            assertEquals(0x2000 + shift, result?.offset)
            assertEquals(19, result?.count)
            val decoded = result?.let {
                CompiledTypeNameResolver.decode(RomImage(fixture.bytes), 3, it, PokemonTextCodec.gbaEnglish)
            }
            assertEquals("Fairy", decoded?.get(18)?.name)
            assertEquals(TypeSemanticRole.FAIRY, decoded?.get(18)?.semanticRole)
        }
    }

    @Test
    fun readableAdjacentNamesAndReferencedIdsAloneAreNotAuthority() {
        val fixture = fixture()
        fixture.bytes.fill(0, 0x400, 0x500)
        assertNull(fixture.resolve())
    }

    @Test
    fun equivalentConsumersAgreeWithoutReferencePopularity() {
        val fixture = fixture()
        consumer(fixture.bytes, 0x800, 0x2000, 0x3000, 0x1800)
        assertEquals(19, fixture.resolve()?.count)
    }

    @Test
    fun competingCompleteRootsRejectBeforeReadingTheirLabels() {
        for (malformed in listOf(false, true)) {
            val fixture = fixture()
            names(fixture.bytes, 0x2400)
            consumer(fixture.bytes, 0x800, 0x2400, 0x3000, 0x1800)
            if (malformed) fixture.bytes.fill(0, 0x2400, 0x2485)
            assertNull(fixture.resolve())
        }
    }

    @Test
    fun numericOriginStrideTypeByteAndCopyArgumentMustBeUnclobbered() {
        for ((offset, replacement) in listOf(
            0x406 to 0x2000, // Numeric root overwritten.
            0x40c to 0x7822, // Byte rather than u16 move.
            0x40e to 0x0051, // Wrong numeric stride.
            0x416 to 0x788a, // Wrong numeric type field.
            0x418 to 0x0091, // Wrong name stride.
            0x41a to 0x1a81, // Different index origin.
            0x41e to 0x2000, // Text address not passed to r1.
            0x1804 to 0xe003, // Copy loop executes a different path.
            0x1810 to 0x2800, // Wrong terminator.
            0x181c to 0x4770, // Wrong return/frame.
        )) {
            val fixture = fixture()
            word(fixture.bytes, offset, replacement)
            assertNull("mutation ${offset.toString(16)}", fixture.resolve())
        }
        val fixture = fixture()
        pointer(fixture.bytes, 0x440, 0x3200)
        assertNull(fixture.resolve())
    }

    @Test
    fun arbitraryReturningCalleeIsNotATextCopyLeaf() {
        val fixture = fixture()
        word(fixture.bytes, 0x1800, 0x4770)
        assertNull(fixture.resolve())
    }

    @Test
    fun selectedNumericAbiAndActualNativeTypeMustAgree() {
        for (mutation in listOf<(Fixture) -> Fixture>(
            { it.copy(layout = it.layout.copy(tables = ProfileTables(moveData =
                TableLayout(0x3000, 17, 20)))) },
            { it.copy(layout = it.layout.copy(tables = ProfileTables(moveData =
                TableLayout(0x3000, 17, 20, stride = 24, format = TableRecordFormat.PACKED_FLAGS_MOVE_20)))) },
            { it.copy(layout = it.layout.copy(moveCount = 18)) },
            { it.bytes[0x3000 + 16 * 20 + 3] = 0; it },
        )) assertNull(mutation(fixture()).resolve())
    }

    @Test
    fun completeRequestedRowsRequireTerminatedKnownUniqueSemanticTokens() {
        for (mutation in listOf<(ByteArray) -> Unit>(
            { it.fill(0xbb.toByte(), 0x2000 + 18 * 7, 0x2000 + 19 * 7) },
            { it[0x2000 + 18 * 7] = 0xfc.toByte(); it[0x2000 + 18 * 7 + 1] = 0x7f },
            { text(it, 0x2000 + 18 * 7, "") },
            { text(it, 0x2000 + 18 * 7, "Normal") },
            { text(it, 0x2000 + 18 * 7, "Odd") },
            { text(it, 0x2000 + 7 * 7, "Odd") },
        )) {
            val fixture = fixture()
            mutation(fixture.bytes)
            assertNull(fixture.resolve())
        }
        val fixture = fixture()
        assertNull(fixture.copy(bytes = fixture.bytes.copyOf(0x2000 + 18 * 7 + 3)).resolve())
    }

    @Test
    fun standardDomainIsNotGloballyExtendedByReadability() {
        val fixture = fixture()
        val standard = fixture.layout.languageManifest.defaultProjection()!!.localizedTables.typeNames!!
        assertEquals(18, CompiledTypeNameResolver.decode(RomImage(fixture.bytes), 3,
            standard, PokemonTextCodec.gbaEnglish)?.size)
        assertNull(CompiledTypeNameResolver.decode(RomImage(fixture.bytes), 3,
            standard.copy(count = 19), PokemonTextCodec.gbaEnglish))
        assertNull(fixture.resolve(ids = (0 until 18).toSet()))
        assertNull(fixture.resolve(ids = setOf(-1, 18)))
        assertNull(fixture.resolve(ids = setOf(18, 256)))
    }

    @Test
    fun partialWorkExtentAndSiteScansAreTerminal() {
        for (limits in listOf(
            ResolutionLimits(maxProbeWorkPerDataset = 1),
            ResolutionLimits(maxDatasetExtentBytes = 16),
            ResolutionLimits(maxDatasetExtentBytes = 0x6000L - 1),
            ResolutionLimits(maxDatasetExtentBytes = 0x6000L + 17 * 20 + 19 * 7 - 1),
            ResolutionLimits(maxNominatedGbaReferenceSites = 1),
            ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
        )) {
            val fixture = fixture()
            consumer(fixture.bytes, 0x800, 0x2000, 0x3000, 0x1800)
            assertNull(fixture.resolve(limits))
        }
        val fixture = fixture()
        consumer(fixture.bytes, 0x800, 0x2000, 0x3000, 0x1800)
        word(fixture.bytes, 0x816, 0x788a)
        assertNull(fixture.resolve(ResolutionLimits(maxNominatedGbaReferenceSites = 1)))
    }

    @Test
    fun everyCatalogPhasePublishesTheProvedDescriptorWithoutChangingTheCoreLayout() {
        val fixture = fixture()
        fixture.bytes.fill(0, 0x3000, 0x3000 + 17 * 20)
        for (id in 1 until 17) {
            text(fixture.bytes, 0x3500 + id * 13, "Move")
            fixture.bytes[0x3000 + id * 20 + 2] = 40
            fixture.bytes[0x3000 + id * 20 + 3] = 18
            fixture.bytes[0x3000 + id * 20 + 4] = 95
            fixture.bytes[0x3000 + id * 20 + 5] = 10
            fixture.bytes[0x3000 + id * 20 + 11] = 1
        }
        val rom = RomImage(fixture.bytes)
        val header = RomHeader(Platform.GBA, "TYPE PREFIX TEST")
        val table = MoveDetailsTableLayout(0x3000, 17, MoveDetailsAbi.PACKED_FLAGS_MOVE_20)
        val decoded = MoveDetailsCodec().decode(RomAnalysisSession(rom, header), table) as MoveDetailsTableOutcome.Decoded
        val layout = fixture.layout.copy(speciesCount = 0,
            tables = fixture.layout.tables.copy(moveNames = TableLayout(0x3500, 17, 13)),
            resolvedDatasets = ResolvedDatasetLayouts(moveDetails = ResolvedMoveDetailsLayout(table, decoded.rows)))
        val analysis = ParseResult(header, rom.sha256, rom.crc32, rom.size, SelectionStatus.SELECTED,
            EngineFamily.EMERALD, null, 20, emptyList(), emptyList())
        var phases = 0
        val catalog = CatalogMaterializer.materialize(rom, analysis, layout, onProgress = {
            phases++
            assertEquals("Fairy", it.catalog.defaultTextProjection().typeName(18))
            assertEquals(19, it.catalog.languageManifest.defaultProjection()?.localizedTables?.typeNames?.count)
        }, resolveMoveDescriptions = { null })
        assertTrue(phases > 1)
        assertEquals("Fairy", catalog.defaultTextProjection().typeName(18))
        assertEquals(TypeSemanticRole.FAIRY, catalog.typesById[18]?.semanticRole?.value)
        assertEquals(18, layout.languageManifest.defaultProjection()?.localizedTables?.typeNames?.count)
        assertEquals(layout.tables.moveData, fixture.layout.tables.moveData)
    }

    @Test
    fun incompleteReferenceSamplesCannotHideALateCompetingRoot() {
        val fixture = fixture()
        names(fixture.bytes, 0x2400)
        for (index in 1..16) consumer(fixture.bytes, 0x400 + index * 0x100, 0x2000, 0x3000, 0x1800)
        consumer(fixture.bytes, 0x1500, 0x2400, 0x3000, 0x1800)
        assertNull(fixture.resolve(ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 32)))
    }

    @Test
    fun rootAndCandidateCapsCannotPublishAnEarlierWitness() {
        for (limits in listOf(ResolutionLimits(maxProbeRootsPerDataset = 1),
            ResolutionLimits(maxCandidatesPerDataset = 1))) {
            val fixture = fixture()
            names(fixture.bytes, 0x2400)
            consumer(fixture.bytes, 0x800, 0x2400, 0x3000, 0x1800)
            assertNull(fixture.resolve(limits))
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationPropagatesDuringCompleteConsumerDiscovery() {
        var checks = 0
        fixture().resolve(cancellation = ParserCancellationToken {
            if (++checks == 3) throw ParserCancellationException()
        })
    }

    private data class Fixture(val bytes: ByteArray, val layout: ResolvedRomLayout) {
        fun resolve(limits: ResolutionLimits = ResolutionLimits(), ids: Set<Int> = (0..18).toSet(),
                    cancellation: ParserCancellationToken = ParserCancellationToken.NONE) =
            CompiledReferencedTypeNames.resolve(RomImage(bytes), layout, ids, cancellation, limits)
    }

    private fun fixture(shift: Int = 0): Fixture {
        val bytes = ByteArray(0x6000) { 0x7f }
        names(bytes, 0x2000 + shift)
        repeat(17) { bytes[0x3000 + shift + it * 20 + 3] = (if (it == 16) 18 else it).toByte() }
        consumer(bytes, 0x400 + shift, 0x2000 + shift, 0x3000 + shift, 0x1800 + shift)
        val manifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish).withDefaultLocalizedTables(
            LocalizedTableLayout(typeNames = TableLayout(0x2000 + shift, 18, 7)))
        return Fixture(bytes, ResolvedRomLayout(EngineFamily.EMERALD, 3, Platform.GBA, 4, 17,
            ProfileTables(moveData = TableLayout(0x3000 + shift, 17, 20,
                format = TableRecordFormat.PACKED_FLAGS_MOVE_20)), languageManifest = manifest))
    }

    private fun names(bytes: ByteArray, root: Int) {
        listOf("Normal", "Fight", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel",
            "???", "Fire", "Water", "Grass", "Electr", "Psychc", "Ice", "Dragon", "Dark", "Fairy")
            .forEachIndexed { id, name -> text(bytes, root + id * 7, name) }
    }

    /** Individually assembled numeric20/type7/copy role; no ROM payload or fixed production roots. */
    private fun consumer(bytes: ByteArray, entry: Int, root: Int, numeric: Int, copy: Int) {
        words(bytes, entry, 0x4b0f, 0x4910, 0x186d, 0x7829, 0x0049, 0x1864,
            0x8822, 0x0091, 0x1889, 0x0089, 0x18c9, 0x78ca, 0x00d1, 0x1a89, 0x4a0a, 0x1889)
        bl(bytes, entry + 0x20, copy)
        pointer(bytes, entry + 0x40, numeric)
        word(bytes, entry + 0x44, 0x20)
        word(bytes, entry + 0x46, 0)
        pointer(bytes, entry + 0x48, root)
        words(bytes, copy, 0xb500, 0x1c03, 0xe002, 0x701a, 0x3301, 0x3101,
            0x780a, 0x1c10, 0x28ff, 0xd1f8, 0x20ff, 0x7018, 0x1c18, 0xbc02, 0x4708)
    }

    private fun text(bytes: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            bytes[offset + index] = when (char) {
                '?' -> 0xac.toByte()
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
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
    private fun bl(bytes: ByteArray, site: Int, target: Int) {
        val displacement = target - site - 4
        word(bytes, site, 0xf000 or ((displacement shr 12) and 0x7ff))
        word(bytes, site + 2, 0xf800 or ((displacement shr 1) and 0x7ff))
    }
}
