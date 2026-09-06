package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.SafeGbaReferenceIndexBuilder
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.resolvedLanguageManifest
import com.enrpau.dualscreendex.parser.language.textUnavailableLanguageManifests
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoveDescriptionMaterializerTest {
    @Test
    fun unknownAndAmbiguousLanguageDisableMoveDescriptions() {
        textUnavailableLanguageManifests.forEach { manifest ->
            assertNull(
                MoveDescriptionMaterializer.materialize(
                    RomImage(ByteArray(0x100)),
                    layout(moveCount = 4).copy(languageManifest = manifest),
                ),
            )
        }
    }

    @Test
    fun selectsTheCompiledReferencedTableAndRetainsExplicitBlankDescriptions() {
        val bytes = ByteArray(0x2000)
        val adjacentDecoy = 0x0FC
        val tableOffset = 0x100
        putGbaPointer(bytes, adjacentDecoy, 0x700)
        encodeGbaText(bytes, 0x700, "No move information.")
        listOf("A small flame attack.", "-", "Raises the user's Defense.", "A strong water attack.")
            .forEachIndexed { index, value ->
                val textOffset = 0x800 + index * 0x80
                putGbaPointer(bytes, tableOffset + index * 4, textOffset)
                encodeGbaText(bytes, textOffset, value)
            }
        val references = GbaReferenceIndex.countsOnlyForTesting(mapOf(tableOffset to 2))

        val result = MoveDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(moveCount = 5),
            references,
        )

        assertEquals(tableOffset, result?.sourceOffset)
        assertEquals(4, result?.descriptions?.size)
        assertEquals("-", result?.descriptions?.get(2))
        assertEquals("A strong water attack.", result?.descriptions?.get(4))
    }

    @Test
    fun decodesAValidatedGbaMoveDescriptionPointerTable() {
        val bytes = ByteArray(0x1000)
        val tableOffset = 0x100
        listOf("A small flame attack.", "Raises the user's Defense.", "Lowers the foe's accuracy.").forEachIndexed { index, text ->
            val textOffset = 0x400 + index * 0x40
            putGbaPointer(bytes, tableOffset + index * 4, textOffset)
            encodeGbaText(bytes, textOffset, text)
        }

        val result = MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = 4))

        assertEquals(tableOffset, result?.sourceOffset)
        assertEquals("A small flame attack.", result?.descriptions?.get(1))
        assertEquals("Lowers the foe's accuracy.", result?.descriptions?.get(3))
    }

    @Test
    fun decodesSparseGbaMoveDescriptionPointerTable() {
        val bytes = ByteArray(0x2000)
        val tableOffset = 0x100
        val descriptions = listOf(
            "A small flame attack.",
            "Raises the user's Defense.",
            "Lowers the foe's accuracy.",
            "A strong water attack.",
            "May lower the foe's Defense.",
            null,
            null,
            "A quick electric attack.",
            "Raises the user's Speed.",
            "May lower the foe's Speed.",
        )
        descriptions.forEachIndexed { index, text ->
            if (text == null) return@forEachIndexed
            val textOffset = 0x800 + index * 0x40
            putGbaPointer(bytes, tableOffset + index * 4, textOffset)
            encodeGbaText(bytes, textOffset, text)
        }
        putInt(bytes, tableOffset + 6 * 4, 0x12345678)

        val result = MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = 11))

        assertEquals(tableOffset, result?.sourceOffset)
        assertEquals(8, result?.descriptions?.size)
        assertEquals("May lower the foe's Speed.", result?.descriptions?.get(10))
    }

    @Test
    fun fallbackPointerScanChecksCancellationAtFixedIntervals() {
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 3) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            MoveDescriptionMaterializer.materialize(
                RomImage(ByteArray(16_384) { 0x08 }),
                layout(moveCount = 4),
                cancellation = cancellation,
                limits = ResolutionLimits(maxProbeWorkPerDataset = 128),
            )
        }

        assertEquals(3, checks)
    }

    @Test(timeout = 5_000)
    fun denseFallbackPointerDataFailsOnlyTheOptionalCapabilityAtItsBudget() {
        val result = MoveDescriptionMaterializer.materialize(
            RomImage(ByteArray(RomImage.MAX_SIZE_BYTES) { 0x08 }),
            layout(moveCount = 4),
            limits = ResolutionLimits(
                maxProbeRootsPerDataset = 16,
                maxProbeWorkPerDataset = 64,
                maxCandidatesPerDataset = 8,
            ),
        )

        assertNull(result)
    }

    @Test
    fun rejectsMoveCountWhosePointerTableCannotFitInTheRom() {
        val bytes = ByteArray(0x100)
        putGbaPointer(bytes, 0x20, 0x80)

        assertNull(MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = Int.MAX_VALUE)))
    }

    @Test
    fun rejectsPointerTablesWithUndecodableText() {
        val bytes = ByteArray(0x800)
        repeat(3) { index -> putGbaPointer(bytes, 0x100 + index * 4, 0x400 + index * 0x40) }

        assertNull(MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = 4)))
    }

    @Test
    fun rejectsReadableMusicIdentifierPointerTable() {
        val bytes = ByteArray(0x1000)
        listOf("MUS-PL-TY-BROADCAST", "MUS-HG-NEW-BARK", "BW-SEQ-BGM-PALPARK").forEachIndexed { index, text ->
            val textOffset = 0x400 + index * 0x40
            putGbaPointer(bytes, 0x100 + index * 4, textOffset)
            encodeGbaText(bytes, textOffset, text)
        }

        assertNull(MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = 4)))
    }

    @Test
    fun conflictingWesternReferencedTablesCannotBeChosenByTheFallback() {
        val bytes = ByteArray(0x1000)
        listOf(0x100, 0x200).forEach { root ->
            repeat(3) { id ->
                val target = 0x400 + (root / 0x100 - 1) * 0x200 + id * 0x40
                putGbaPointer(bytes, root + id * 4, target)
                encodeGbaText(bytes, target, "A small flame attack.")
            }
        }
        assertNull(MoveDescriptionMaterializer.materialize(
            RomImage(bytes), layout(4), GbaReferenceIndex.countsOnlyForTesting(mapOf(0x100 to 1, 0x200 to 1)),
        ))
    }

    @Test
    fun overflowingWesternReferencesCannotReenterTheUnprovenFallback() {
        val bytes = ByteArray(0x1000)
        repeat(3) { id ->
            putGbaPointer(bytes, 0x100 + id * 4, 0x400 + id * 0x40)
            encodeGbaText(bytes, 0x400 + id * 0x40, "A small flame attack.")
        }
        assertNull(MoveDescriptionMaterializer.materialize(
            RomImage(bytes), layout(4), GbaReferenceIndex.budgetExceeded("fixture reference limit"),
        ))
    }

    @Test
    fun nativeUnifiedEmbeddedDescriptionsRemainIndependentOfClassicConsumerDiscovery() {
        val fixture = nativeDirectFixture()
        repeat(10) { index ->
            putGbaPointer(fixture.bytes, 0x3000 + (index + 1) * 48 + 4, 0x4038 + index * 56)
        }
        val layout = fixture.layout.copy(tables = ProfileTables(moveData = TableLayout(
            0x3000, 11, 48, stride = 48,
            format = com.enrpau.dualscreendex.parser.model.TableRecordFormat.UNIFIED_MOVE_INFO_48,
        )))
        val result = MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), layout,
            GbaReferenceIndex.budgetExceeded("classic references unavailable"))
        assertEquals(0x3000, result?.sourceOffset)
        assertEquals(10, result?.descriptions?.size)
    }

    @Test
    fun nativeDirectConsumerIsRelocatableAndRepeatedReferencesAreNotAConflict() {
        val fixture = nativeDirectFixture()
        installRubyConsumer(fixture.bytes, 0x1400, 0x4000)
        val index = GbaReferenceIndex.fromTargets(mapOf(0x4000 to GbaTargetReferenceEvidence(
            2, listOf(0x1012, 0x1412), 2, 16, null,
        )), 32)
        assertEquals(10, fixture.materialize(index)?.descriptions?.size)
    }

    @Test
    fun nativeDirectRecordsUseTheSameMoveAsNumericDetailsNotTheLearnsetDecoy() {
        val fixture = nativeDirectFixture()
        val result = fixture.materialize()
        assertEquals(0x4038, result?.sourceOffset)
        assertEquals(10, result?.descriptions?.size)
        assertEquals("あいうえおかきくけこさしすせそ", result?.descriptions?.get(1))
        assertEquals("あいうえおかきくけこさしすせた", result?.descriptions?.get(10))
    }

    @Test
    fun nativeUnprovenLearnsetPointerProseIsRejectedWithoutReferences() {
        val fixture = nativeDirectFixture()
        assertNull(MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout))
    }

    @Test
    fun nativeCountsOnlyReferencesCannotAuthorizeReadablePointerProse() {
        val fixture = nativeDirectFixture()
        assertNull(fixture.materialize(GbaReferenceIndex.countsOnlyForTesting(mapOf(0x100 to 1, 0x4000 to 1))))
    }

    @Test
    fun nativeReferenceTargetOverflowCannotEnterThePointerFallback() {
        assertNull(nativeDirectFixture().materialize(GbaReferenceIndex.budgetExceeded("fixture target limit")))
    }

    @Test
    fun nativeDirectConsumerRequiresTheSelectedNumericRoot() {
        val fixture = nativeDirectFixture()
        putGbaPointer(fixture.bytes, 0x1090, 0x3200)
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeDirectConsumerRejectsAClobberedSharedMoveIndex() {
        val fixture = nativeDirectFixture()
        putShort(fixture.bytes, 0x0FD6, 0x2001) // Replace MOV r0,r4 before numeric helper.
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeDirectConsumerRejectsWrongScale() {
        val fixture = nativeDirectFixture()
        putShort(fixture.bytes, 0x100C, 0x0088) // move*4 instead of move*8.
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeDirectConsumerRejectsAnOverwrittenTextArgument() {
        val fixture = nativeDirectFixture()
        putShort(fixture.bytes, 0x1016, 0x2000)
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeDirectConsumerRequiresTheNumericTextSink() {
        val fixture = nativeDirectFixture()
        putBl(fixture.bytes, 0x101A, 0x2900)
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeSharedArbitraryCallIsNotATextSink() {
        val fixture = nativeDirectFixture()
        putShort(fixture.bytes, 0x2800, 0x4770) // Shared target merely returns.
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeDirectConsumerCannotExecuteThroughItsLiteralPool() {
        val fixture = nativeDirectFixture()
        putShort(fixture.bytes, 0x1020, 0x0000) // Remove BX after POP.
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeDirectRecordsCannotReadTheBiasedBaseAsMoveOne() {
        val fixture = nativeDirectFixture()
        putGbaPointer(fixture.bytes, 0x1028, 0x4038)
        assertNull(fixture.materialize(nativeReferences(0x4038 to 0x1012))) // Shifted final row leaves the text domain.
    }

    @Test
    fun nativeDirectRecordCannotBorrowATerminatorFromTheNextRow() {
        val fixture = nativeDirectFixture()
        fixture.bytes.fill(1, 0x4038, 0x4038 + 56)
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeDirectRecordsRequireTheLastRowToFit() {
        val fixture = nativeDirectFixture()
        assertNull(MoveDescriptionMaterializer.materialize(
            RomImage(fixture.bytes.copyOf(0x4000 + 11 * 56 - 1)), fixture.layout, fixture.references,
        ))
    }

    @Test
    fun nativeDirectDistinctAuthorityIsAConflictEvenWhenOneHasLessReadableText() {
        val fixture = nativeDirectFixture()
        installRubyConsumer(fixture.bytes, 0x1400, 0x5000)
        fixture.bytes.copyInto(fixture.bytes, 0x5038, 0x4038, 0x4000 + 11 * 56)
        fixture.bytes.fill(0, 0x5038, 0x5038 + 56)
        assertNull(fixture.materialize(nativeReferences(0x4000 to 0x1012, 0x5000 to 0x1412)))
    }

    @Test
    fun nativeIncompleteUnrelatedSitesRecoverWithoutAnyTextNomination() {
        val fixture = nativeRecoveryFixture(0x5000)
        assertEquals(10, fixture.materialize()?.descriptions?.size)
    }

    @Test
    fun nativeIncompleteAuthoritySitesRecoverAlongsideUnrelatedSites() {
        val fixture = nativeRecoveryFixture(0x4000)
        installLiteralReferences(fixture.bytes, 0x5000, 0x2300)
        val references = SafeGbaReferenceIndexBuilder.build(RomImage(fixture.bytes), ResolutionLimits())
        assertEquals(18, references.targets.getValue(0x4000).count)
        assertEquals(17, references.targets.getValue(0x5000).count)
        assertEquals(10, fixture.materialize(references)?.descriptions?.size)
    }

    @Test
    fun nativeRecoveredUnreadableCompetingAuthorityStillConflicts() {
        val fixture = nativeRecoveryFixture(0x5000)
        installRubyConsumer(fixture.bytes, 0x1400, 0x5000)
        val references = SafeGbaReferenceIndexBuilder.build(RomImage(fixture.bytes), ResolutionLimits())
        assertEquals(18, references.targets.getValue(0x5000).count)
        assertNull(fixture.materialize(references))
    }

    @Test
    fun nativeRecoveryReconcilesNonConsumerCountsBeforePublishingAnotherRoot() {
        val fixture = nativeRecoveryFixture(0x5000)
        val evidence = fixture.references.targets.getValue(0x5000)
        for (count in listOf(evidence.count - 1, evidence.count + 1)) {
            val references = GbaReferenceIndex.fromTargets(fixture.references.targets +
                (0x5000 to GbaTargetReferenceEvidence(count, emptyList(), count, 16, "fixture count mismatch")), 32)
            assertNull(fixture.materialize(references))
        }
    }

    @Test
    fun nativeRecoveryScanAndPerTargetSiteBudgetsAreTerminal() {
        val fixture = nativeRecoveryFixture(0x5000)
        listOf(
            ResolutionLimits(maxDatasetExtentBytes = fixture.bytes.size.toLong() - 1),
            ResolutionLimits(maxNominatedGbaReferenceSites = 16),
            ResolutionLimits(maxProbeWorkPerDataset = 16),
        ).forEach { limits ->
            assertNull(MoveDescriptionMaterializer.materialize(
                RomImage(fixture.bytes), fixture.layout, fixture.references, limits = limits,
            ))
        }
    }

    @Test
    fun nativeRecoveryChecksCancellationInsideTheSingleScan() {
        val fixture = nativeRecoveryFixture(0x5000)
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout, fixture.references,
                cancellation = ParserCancellationToken { if (++checks == 4) throw ParserCancellationException() })
        }
        assertEquals(4, checks)
    }

    private fun nativeRecoveryFixture(overflowingRoot: Int): NativeFixture {
        val fixture = nativeDirectFixture()
        installLiteralReferences(fixture.bytes, overflowingRoot, 0x2200)
        val references = SafeGbaReferenceIndexBuilder.build(RomImage(fixture.bytes), ResolutionLimits())
        assertEquals(emptyList<Int>(), references.targets.getValue(overflowingRoot).instructionSites)
        return fixture.copy(references = references)
    }

    private fun installLiteralReferences(bytes: ByteArray, root: Int, start: Int) {
        repeat(17) { index ->
            val site = start + index * 8
            putShort(bytes, site, 0x4800)
            putGbaPointer(bytes, site + 4, root)
        }
    }

    @Test
    fun nativeUnreadableCompetingAuthorityCannotDisappearWhenItsSitesOverflow() {
        val fixture = nativeDirectFixture()
        installRubyConsumer(fixture.bytes, 0x1400, 0x5000)
        fixture.bytes.copyInto(fixture.bytes, 0x5038, 0x4038, 0x4000 + 11 * 56)
        fixture.bytes.fill(0, 0x5038, 0x5038 + 56)
        val references = GbaReferenceIndex.fromTargets(mapOf(
            0x4000 to GbaTargetReferenceEvidence(1, listOf(0x1012), 1, 16, null),
            0x5000 to GbaTargetReferenceEvidence(17, emptyList(), 17, 16, "fixture site overflow"),
        ), 32)
        assertNull(fixture.materialize(references))
    }

    @Test
    fun nativeDirectIncompleteCandidateSitesCannotFallBack() {
        val fixture = nativeDirectFixture()
        val references = GbaReferenceIndex.fromTargets(mapOf(0x4000 to GbaTargetReferenceEvidence(
            count = 17, instructionSites = emptyList(), observedSites = 17, limitSites = 16,
            overflowReason = "fixture site limit",
        )), 32)
        assertNull(fixture.materialize(references))
    }

    @Test
    fun nativeDirectWorkRootCandidateAndExtentBudgetsAreTerminal() {
        val fixture = nativeDirectFixture()
        listOf(
            ResolutionLimits(maxProbeWorkPerDataset = 1),
            ResolutionLimits(maxProbeRootsPerDataset = 1),
            ResolutionLimits(maxDatasetExtentBytes = 55),
            ResolutionLimits(maxCandidatesPerDataset = 1),
        ).forEach { limits ->
            installRubyConsumer(fixture.bytes, 0x1400, 0x5000)
            assertNull(MoveDescriptionMaterializer.materialize(
                RomImage(fixture.bytes), fixture.layout,
                nativeReferences(0x4000 to 0x1012, 0x5000 to 0x1412), limits = limits,
            ))
        }
    }

    @Test
    fun nativeDirectDiscoveryPropagatesCancellation() {
        val fixture = nativeDirectFixture()
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            MoveDescriptionMaterializer.materialize(
                RomImage(fixture.bytes), fixture.layout, fixture.references,
                cancellation = ParserCancellationToken { if (++checks == 2) throw ParserCancellationException() },
            )
        }
        assertEquals(2, checks)
    }

    private data class NativeFixture(
        val bytes: ByteArray,
        val layout: ResolvedRomLayout,
        val references: GbaReferenceIndex,
    ) {
        fun materialize(index: GbaReferenceIndex = references) =
            MoveDescriptionMaterializer.materialize(RomImage(bytes), layout, index)
    }

    @Test
    fun nativeWindowConsumerRecoversDirectRowsWithTheSameNumericMoveAuthority() {
        val fixture = nativeWindowFixture()
        assertEquals(0x4038, fixture.materialize()?.sourceOffset)
        assertEquals("あいうえおかきくけこさしすせた", fixture.materialize()?.descriptions?.get(10))
    }

    @Test
    fun nativeWindowConsumerRejectsMoveClobberAndAnExecutedLiteralPool() {
        listOf(0x1236 to 0x2001, 0x1250 to 0x0000, 0x123E to 0x2000).forEach { (offset, word) ->
            val fixture = nativeWindowFixture()
            putShort(fixture.bytes, offset, word)
            assertNull(fixture.materialize())
        }
    }

    private fun nativeWindowFixture(): NativeFixture {
        val fixture = nativeDirectFixture()
        val bytes = fixture.bytes
        putWords(bytes, 0x1200, 0xB570, 0xB082, 0x0400, 0x0C04, 0x1C26, 0x4812, 0x2102)
        putBl(bytes, 0x120E, 0x2B00)
        putWords(bytes, 0x1212, 0x0600, 0x0E05, 0x1C28, 0x2100)
        putBl(bytes, 0x121A, 0x2B00)
        putWords(bytes, 0x121E, 0x2C00, 0xD038, 0x480D, 0x6800, 0x490D, 0x1840,
            0x7800, 0x2802, 0xD119, 0x1C20)
        putBl(bytes, 0x1232, 0x1800)
        putWords(bytes, 0x1236, 0x00E1, 0x1B09, 0x00C9, 0x4808, 0x1809, 0x2000,
            0x9000, 0x9001, 0x1C28, 0x2200, 0x2302)
        putBl(bytes, 0x124C, 0x2C00)
        putWords(bytes, 0x1250, 0xE018, 0)
        putGbaPointer(bytes, 0x1254, 0x2F00)
        putInt(bytes, 0x1258, 0x02001000)
        putInt(bytes, 0x125C, 0x1234)
        putGbaPointer(bytes, 0x1260, 0x4000)
        putWords(bytes, 0x1264, 0x4A09, 0x490A, 0x00F0, 0x1840, 0x7800, 0x0080,
            0x1880, 0x6801, 0x2000, 0x9000, 0x9001, 0x1C28, 0x2200, 0x2302)
        putBl(bytes, 0x1280, 0x2C00)
        putShort(bytes, 0x128A, 0xE006)
        putGbaPointer(bytes, 0x128C, 0x3100)
        putGbaPointer(bytes, 0x1290, 0x3200)
        putWords(bytes, 0x129A, 0x2000)
        putBl(bytes, 0x129C, 0x2B00)
        putWords(bytes, 0x12A0, 0xB002, 0xBC70, 0xBC01, 0x4700)
        val numeric = 0x1800
        putWords(bytes, numeric, 0xB570, 0xB082, 0x0400, 0x0C05, 0x2D00, 0xD049,
            0x2018, 0x9000, 0x2020, 0x9001, 0x200E, 0x2100, 0x2228, 0x2300)
        putBl(bytes, numeric + 0x1C, 0x2B00)
        putWords(bytes, numeric + 0x20, 0x4A05, 0x0069, 0x1948, 0x0080, 0x1882,
            0x7850, 0x1C0E, 0x2801, 0xD806, 0x4902, 0xE00C, 0)
        putGbaPointer(bytes, numeric + 0x38, 0x3000)
        putGbaPointer(bytes, numeric + 0x3C, 0x2A00)
        putWords(bytes, numeric + 0x50, 0x2000, 0x9000, 0x9001, 0x200E, 0x2228, 0x2302)
        putBl(bytes, numeric + 0x5C, 0x2C00)
        // Window-text wrapper preserves r1 text until passing it as the last stack argument.
        putWords(bytes, 0x2C00, 0xB570, 0xB085, 0x9C09, 0x9D0A, 0x0600, 0x0E00,
            0x0612, 0x0E12, 0x061B, 0x0E1B, 0x0624, 0x0E24, 0x062D, 0x0E2D,
            0x2600, 0x9600, 0x9401, 0x006C, 0x1964, 0x4D06, 0x1964, 0x9402,
            0x9603, 0x9104, 0x2101)
        putBl(bytes, 0x2C32, 0x2B00)
        putWords(bytes, 0x2C36, 0xB005, 0xBC70, 0xBC01, 0x4700)
        putGbaPointer(bytes, 0x2C40, 0x2F00)
        return fixture.copy(references = nativeReferences(0x4000 to 0x123C))
    }

    @Test
    fun nativeStateBufferConsumerUsesRelocatedSixtyByteRowsAndNumericMoveIds() {
        for (shift in listOf(0, 0x1000)) {
            val fixture = nativeStateBufferFixture(shift)
            val result = fixture.materialize()
            assertEquals(0x603C + shift, result?.sourceOffset)
            assertEquals(10, result?.descriptions?.size)
            assertEquals("あいうえおかきくけこさしすせそ", result?.descriptions?.get(1))
            assertEquals("あいうえおかきくけこさしすせた", result?.descriptions?.get(10))
        }
    }

    @Test
    fun nativeStateBufferWitnessIsIndependentOfFamilyAndRelocatesTheSharedField() {
        val fixture = nativeStateBufferFixture()
        for (family in listOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.EMERALD, EngineFamily.FIRERED_LEAFGREEN)) {
            assertEquals(10, fixture.copy(layout = fixture.layout.copy(family = family)).materialize()?.descriptions?.size)
        }
        // A consistent structural field relocation remains valid; one-sided mutations below do not.
        putInt(fixture.bytes, 0x40CC, 0x3304)
        putInt(fixture.bytes, 0x18A4, 0x3304)
        putInt(fixture.bytes, 0x1964, 0x3304)
        putInt(fixture.bytes, 0x1958, 0x3310)
        putInt(fixture.bytes, 0x195C, 0x32FA)
        assertEquals(10, fixture.materialize()?.descriptions?.size)
    }

    @Test
    fun nativeStateBufferRejectsMismatchedStateAndMoveFields() {
        listOf(0x40B8 to 0x02002004, 0x189C to 0x02002004, 0x40B8 to 0x08002000,
            0x40CC to 0x3206, 0x18A4 to 0x3206, 0x1964 to 0x3206,
            0x1958 to 0x3212, 0x195C to 0x31FC).forEach { (offset, value) ->
            assertStateMutation("state/field ${offset.toString(16)}") { putInt(it, offset, value) }
        }
    }

    @Test
    fun nativeStateBufferRejectsClobberedIndicesAndWrongNumericScaleOrRoot() {
        listOf(0x4084 to 0x2301, 0x4088 to 0x0089, 0x4090 to 0x7812,
            0x182E to 0x00B8, 0x183A to 0x2001, 0x18DC to 0x4443,
            0x18E0 to 0x0091, 0x18E8 to 0x7849, 0x183E to 0xD140).forEach { (offset, value) ->
            assertStateMutation("numeric index/scale ${offset.toString(16)}") { putShort(it, offset, value) }
        }
        assertStateMutation("unselected numeric root") { putGbaPointer(it, 0x1960, 0x3200) }
        val fixture = nativeStateBufferFixture()
        assertNull(fixture.copy(layout = fixture.layout.copy(tables = ProfileTables(
            moveData = TableLayout(0x3000, 11, 16),
        ))).materialize())
    }

    @Test
    fun nativeStateBufferRejectsWrongStrideBiasAndSummaryControlFlow() {
        listOf(0x4092 to 0x00D1, 0x4094 to 0x1889, 0x4096 to 0x00C9,
            0x409A to 0x2001, 0x400E to 0x2905, 0x4010 to 0xD800,
            0x4022 to 0xD000, 0x40B2 to 0x0000).forEach { (offset, value) ->
            assertStateMutation("summary stride/path ${offset.toString(16)}") { putShort(it, offset, value) }
        }
        val fixture = nativeStateBufferFixture()
        putGbaPointer(fixture.bytes, 0x40D0, 0x603C)
        assertNull(fixture.materialize(nativeReferences(0x603C to 0x4098, 0x3000 to 0x18D6)))
    }

    @Test
    fun nativeStateBufferRejectsBrokenArgumentTemplateAndPrinterRelationships() {
        listOf(0x409C to 0x9004, 0x2008 to 0xB083, 0x2018 to 0x9C0D,
            0x201A to 0x9401, 0x2040 to 0x9400, 0x2072 to 0x4669,
            0x2086 to 0x0000, 0x2214 to 0x9000, 0x2250 to 0x9000,
            0x253C to 0x1C29, 0x2542 to 0xC188, 0x2546 to 0x6048,
            0x2510 to 0xD100, 0x25D4 to 0x0000).forEach { (offset, value) ->
            assertStateMutation("text role ${offset.toString(16)}") { putShort(it, offset, value) }
        }
        listOf(0x40A4, 0x4070, 0x2076, 0x227C, 0x223C).forEach { site ->
            assertStateMutation("text call ${site.toString(16)}") { putBl(it, site, 0x2E00) }
        }
        assertStateMutation("shared arbitrary printer") { putShort(it, 0x2500, 0x4770) }
    }

    @Test
    fun nativeStateBufferRejectsSharedFontCalleeClobberingStoredCurrentChar() {
        assertStateMutation("shared callee overwrites template.currentChar before printer") {
            putWords(it, 0x2F00, 0x2000, 0x9000, 0x4770) // MOV r0,0; STR r0,[sp]; BX lr.
        }
    }

    @Test
    fun nativeStateBufferRejectsEveryChangedInstructionOnRelevantFontPaths() {
        val offsets = (0 until 0x1A step 2).toList() + (0x68 until 0x76 step 2).toList() +
            (0x7C until 0x8A step 2).toList() + listOf(0xDE, 0xE0)
        offsets.forEach { offset ->
            assertStateMutation("font leaf instruction ${offset.toString(16)}") {
                // After PUSH LR, [sp+4] is the caller's stored currentChar. No such write is allowed.
                putShort(it, 0x2F00 + offset, 0x9001)
            }
        }
        listOf(0x222E, 0x223A).forEach { site ->
            assertStateMutation("font argument must retain proved selector") { putShort(it, site, 0x2104) }
        }
        assertStateMutation("font leaf cannot call an unchecked descendant") { putBl(it, 0x2F68, 0x2E00) }
        assertStateMutation("font branch cannot fall into literal pool") { putShort(it, 0x2F74, 0x46C0) }
        assertStateMutation("font epilogue cannot return without restoring SP") { putShort(it, 0x2FDE, 0x4770) }
    }

    @Test
    fun nativeStateBufferRejectsUnboundFontDispatchAndLiteralRelationships() {
        listOf(0x2F1C to 0x2E00, 0x2F28 to 0x2F40, 0x2F2C to 0x2F40,
            0x2F28 to 0x2F69, 0x2F2C to 0x2F7D, 0x2F78 to 0x5D00, 0x2F8C to 0x5D00).forEach { (site, root) ->
            assertStateMutation("font dispatch/literal ${site.toString(16)}") { putGbaPointer(it, site, root) }
        }
        assertStateMutation("shared font table must be a bounded ROM declaration") {
            putInt(it, 0x2F78, 0x02002000); putInt(it, 0x2F8C, 0x02002000)
        }
        assertStateMutation("shared font table cannot point one byte beyond ROM") {
            putGbaPointer(it, 0x2F78, it.size); putGbaPointer(it, 0x2F8C, it.size)
        }
    }

    @Test
    fun nativeStateBufferFontLeafAndTableRelocateIndependently() {
        for (entry in listOf(0x2C00, 0x2D40, 0x7F00)) {
            val fixture = nativeStateBufferFixture()
            assertEquals(10, fixture.materialize()?.descriptions?.size)
            installStateFontAttributes(fixture.bytes, entry, 0x5D00)
            fixture.bytes.fill(0, 0x2F00, 0x2FE8)
            putBl(fixture.bytes, 0x2230, entry)
            putBl(fixture.bytes, 0x223C, entry)
            assertEquals("relocated leaf ${entry.toString(16)}", 10, fixture.materialize()?.descriptions?.size)
        }
    }

    @Test
    fun nativeStateBufferFontProofExcludesUnreachableAttributesOnly() {
        val fixture = nativeStateBufferFixture()
        assertEquals(10, fixture.materialize()?.descriptions?.size)
        // Caller selectors are exactly 2/3, so a different arm cannot execute on either path.
        putWords(fixture.bytes, 0x2F40, 0x2000, 0x9001, 0x4770)
        assertEquals(10, fixture.materialize()?.descriptions?.size)
        // Making that arm reachable must invalidate the proof, regardless of identical BL targets.
        putGbaPointer(fixture.bytes, 0x2F28, 0x2F40)
        assertNull(fixture.materialize())
    }

    @Test
    fun nativeStateBufferRejectsMissingOrTruncatedFontLeaf() {
        assertStateMutation("shared font callee body missing") { it.fill(0, 0x2F00, 0x2FE8) }
        val fixture = nativeStateBufferFixture()
        val extended = fixture.bytes.copyOf(0x8008)
        installStateFontAttributes(extended, 0x7F20, 0x5D00)
        putBl(extended, 0x2230, 0x7F20); putBl(extended, 0x223C, 0x7F20)
        assertEquals(10, fixture.copy(bytes = extended).materialize()?.descriptions?.size)
        assertNull("font return truncated at ROM end", fixture.copy(bytes = extended.copyOf(0x8000)).materialize())
    }

    @Test
    fun nativeStateBufferRequiresBothCompleteConsumerRolesAndNeverFallsBackToLearnsets() {
        val fixture = nativeStateBufferFixture()
        assertEquals(10, fixture.materialize()?.descriptions?.size)
        listOf(
            nativeReferences(0x6000 to 0x4098),
            nativeReferences(0x3000 to 0x18D6),
            nativeReferences(0x6000 to 0x409A, 0x3000 to 0x18D6),
            GbaReferenceIndex.countsOnlyForTesting(mapOf(0x6000 to 1, 0x3000 to 1)),
            GbaReferenceIndex.budgetExceeded("fixture overflow"),
        ).forEach { assertNull(fixture.materialize(it)) }
        assertNull(MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout))
    }

    @Test
    fun nativeStateBufferDuplicateSixtyByteWitnessesAreNotConflicts() {
        val fixture = nativeStateBufferFixture()
        // All local literals move with the summary; external calls are reassembled.
        fixture.bytes.copyInto(fixture.bytes, 0x4400, 0x4000, 0x40D4)
        putBl(fixture.bytes, 0x444C, 0x2200)
        putBl(fixture.bytes, 0x4470, 0x2200)
        putBl(fixture.bytes, 0x44A4, 0x2000)
        val references = SafeGbaReferenceIndexBuilder.build(RomImage(fixture.bytes), ResolutionLimits())
        assertEquals(10, fixture.materialize(references)?.descriptions?.size)
    }

    @Test
    fun nativeStateBufferConflictsWithFiftySixByteWitnessAtTheSameRootBeforeReadability() {
        val fixture = nativeStateBufferFixture()
        installRubyConsumer(fixture.bytes, 0x1000, 0x6000)
        // Make BOTH interpretations independently readable, so null cannot be accidental decode failure.
        val starts = (1..10).flatMap { listOf(0x6000 + it * 56, 0x6000 + it * 60) }
        val protected = starts.flatMap { (it until it + 5).toList() }.toSet()
        fixture.bytes.fill(1, 0x6000, 0x6000 + 11 * 60)
        starts.forEach { start ->
            val terminator = (start + 5 until start + 56).first { it !in protected }
            fixture.bytes[terminator] = 0xFF.toByte()
        }
        assertEquals(10, fixture.materialize(nativeReferences(0x6000 to 0x1012))?.descriptions?.size)
        assertEquals(10, fixture.materialize()?.descriptions?.size)
        for (sites in listOf(listOf(0x1012, 0x4098), listOf(0x4098, 0x1012))) {
            val references = GbaReferenceIndex.fromTargets(mapOf(
                0x6000 to GbaTargetReferenceEvidence(2, sites, 2, 16, null),
                0x3000 to GbaTargetReferenceEvidence(1, listOf(0x18D6), 1, 16, null),
            ), 32)
            assertNull(fixture.materialize(references))
        }
    }

    @Test
    fun nativeStateBufferUnreadableSixtyByteAuthorityCannotDisappearBesideFiftySixByteRows() {
        val fixture = nativeStateBufferFixture()
        installRubyConsumer(fixture.bytes, 0x1000, 0x5000)
        nativeDirectFixture().bytes.copyInto(fixture.bytes, 0x5038, 0x4038, 0x4000 + 11 * 56)
        assertEquals(10, fixture.materialize(nativeReferences(0x5000 to 0x1012))?.descriptions?.size)
        assertEquals(10, fixture.materialize()?.descriptions?.size)
        fixture.bytes.fill(0, 0x603C, 0x603C + 60)
        // Force recovery of BOTH prose and numeric roles alongside nonconsumer counts.
        installLiteralReferences(fixture.bytes, 0x6000, 0x100)
        installLiteralReferences(fixture.bytes, 0x3000, 0x300)
        val references = SafeGbaReferenceIndexBuilder.build(RomImage(fixture.bytes), ResolutionLimits())
        assertEquals(emptyList<Int>(), references.targets.getValue(0x6000).instructionSites)
        assertNull(fixture.materialize(references))
    }

    @Test
    fun nativeStateBufferIncompleteProseAndNumericReferencesRecoverTogether() {
        val fixture = nativeStateBufferFixture()
        installLiteralReferences(fixture.bytes, 0x6000, 0x100)
        installLiteralReferences(fixture.bytes, 0x3000, 0x300)
        installLiteralReferences(fixture.bytes, 0x7000, 0x500)
        val references = SafeGbaReferenceIndexBuilder.build(RomImage(fixture.bytes), ResolutionLimits())
        for (root in listOf(0x6000, 0x3000, 0x7000)) {
            assertEquals(emptyList<Int>(), references.targets.getValue(root).instructionSites)
        }
        assertEquals(10, fixture.materialize(references)?.descriptions?.size)
        for (root in listOf(0x6000, 0x3000, 0x7000)) {
            val count = references.targets.getValue(root).count
            for (wrongCount in listOf(count - 1, count + 1)) {
                assertNull(fixture.materialize(GbaReferenceIndex.fromTargets(references.targets +
                    (root to GbaTargetReferenceEvidence(wrongCount, emptyList(), wrongCount, 16, "mismatch")), 32)))
            }
        }
    }

    @Test
    fun nativeStateBufferSingleScanBudgetsAndCancellationAreTerminal() {
        val fixture = nativeStateBufferFixture()
        installLiteralReferences(fixture.bytes, 0x3000, 0x300)
        val references = SafeGbaReferenceIndexBuilder.build(RomImage(fixture.bytes), ResolutionLimits())
        assertEquals(10, fixture.materialize(references)?.descriptions?.size)
        for (limits in listOf(
            ResolutionLimits(maxDatasetExtentBytes = fixture.bytes.size.toLong() - 1),
            ResolutionLimits(maxNominatedGbaReferenceSites = 16),
            ResolutionLimits(maxProbeWorkPerDataset = 16),
        )) assertNull(MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout, references, limits = limits))
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout, references,
                cancellation = ParserCancellationToken { if (++checks == 4) throw ParserCancellationException() })
        }
        assertEquals(4, checks)
    }

    @Test
    fun nativeStateBufferWorkRootCandidateAndDecodeBudgetsAreTerminal() {
        val fixture = nativeStateBufferFixture()
        assertEquals(10, fixture.materialize()?.descriptions?.size)
        for (limits in listOf(ResolutionLimits(maxProbeWorkPerDataset = 1), ResolutionLimits(maxDatasetExtentBytes = 599))) {
            assertNull(MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout, fixture.references, limits = limits))
        }
        installRubyConsumer(fixture.bytes, 0x1000, 0x5000)
        val references = nativeReferences(0x6000 to 0x4098, 0x3000 to 0x18D6, 0x5000 to 0x1012)
        for (limits in listOf(ResolutionLimits(maxProbeRootsPerDataset = 1), ResolutionLimits(maxCandidatesPerDataset = 1))) {
            assertNull(MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout, references, limits = limits))
        }
    }

    @Test
    fun nativeStateBufferCancellationReachesTheNumericWitnessLoop() {
        val fixture = nativeStateBufferFixture()
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes), fixture.layout, fixture.references,
                cancellation = ParserCancellationToken { if (++checks == 3) throw ParserCancellationException() })
        }
        assertEquals(3, checks)
    }

    @Test
    fun nativeStateBufferRowsRespectBothBoundariesAndDoNotBorrowTerminators() {
        val fixture = nativeStateBufferFixture()
        // Row zero is not read, even if it is malformed. The final row must fit exactly.
        fixture.bytes.fill(0xFC.toByte(), 0x6000, 0x603C)
        assertEquals(10, fixture.materialize()?.descriptions?.size)
        val end = 0x6000 + 11 * 60
        assertEquals(10, MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes.copyOf(end)),
            fixture.layout, fixture.references)?.descriptions?.size)
        assertNull(MoveDescriptionMaterializer.materialize(RomImage(fixture.bytes.copyOf(end - 1)), fixture.layout, fixture.references))
        assertStateMutation("row terminator absent") { it.fill(1, 0x603C, 0x6078) }
        assertStateMutation("malformed final row") { it.fill(0, 0x6000 + 10 * 60, end) }
    }

    @Test
    fun nativeStateBufferRejectsMalformedTokensAndEmbeddedControlTerminators() {
        assertStateMutation("FF is a control argument, not a terminator") {
            it.fill(1, 0x603C, 0x6078)
            it[0x6075] = 0xFC.toByte(); it[0x6076] = 1; it[0x6077] = 0xFF.toByte()
        }
        assertStateMutation("truncated control at boundary") {
            it.fill(1, 0x603C, 0x6078); it[0x6077] = 0xFC.toByte()
        }
        assertStateMutation("too short") { it[0x6040] = 0xFF.toByte() }
        assertStateMutation("unknown control") {
            it[0x603C] = 0xFC.toByte(); it[0x603D] = 0x7F
            val decoded = com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3RubySapphire
                .decodeDetailed(it.copyOfRange(0x603C, 0x6078))
            assertEquals(true, decoded.terminated)
            assertEquals(1, decoded.invalidUnits) // FC 7F -> Invalid(byteCount=2), not whitespace.
            assertEquals(13.0 / 14.0, decoded.validRatio, 0.000001)
        }
    }

    private fun assertStateMutation(label: String, mutate: (ByteArray) -> Unit) {
        val fixture = nativeStateBufferFixture()
        assertEquals("positive parent: $label", 10, fixture.materialize()?.descriptions?.size)
        mutate(fixture.bytes)
        assertNull(label, fixture.materialize())
    }

    /** Source-role fixture assembled from Thumb instructions, not a retained ROM payload.
     * Code, ROM literals, RAM declarations and text roots are relocated independently of identity.
     * The numeric witness is the call-free nonzero-move type block, not an emulation of PP helpers.
     */
    private fun nativeStateBufferFixture(shift: Int = 0): NativeFixture {
        val fixture = nativeDirectFixture()
        val bytes = fixture.bytes
        val summary = 0x4000 + shift
        val numeric = 0x1800 + shift
        val wrapper = 0x2000 + shift
        val numericWrapper = 0x2200 + shift
        val printer = 0x2500 + shift
        val base = 0x6000 + shift
        val state = 0x02002000 + shift
        // Remove the independent 56-byte witness and rows, retaining the packed learnset decoy.
        bytes.fill(0, 0x0FCC, 0x1100)
        bytes.fill(0, 0x4000, 0x4300)
        putWords(bytes, summary, 0xB5F0, 0x4647, 0xB480, 0xB085, 0x482A, 0x4680,
            0x7801, 0x2904, 0xD84A, 0x4F29, 0x683B, 0x4A29, 0x1898, 0x7800,
            0x2802, 0xD001, 0x2904, 0xD041, 0x4C26, 0x1918, 0x7800, 0x4E26,
            0x9600, 0x2501, 0x426D, 0x9501, 0x4641, 0x780A, 0x0091, 0x1889,
            0x22C5, 0x0192, 0x1889, 0x1859, 0x9102, 0x2102, 0x2232, 0x2301)
        putBl(bytes, summary + 0x4C, numericWrapper)
        putWords(bytes, summary + 0x50, 0x683B, 0x1918, 0x7800, 0x9600, 0x9501,
            0x4641, 0x780A, 0x0091, 0x1889, 0x4A19, 0x1889, 0x185B, 0x9302,
            0x2102, 0x2232, 0x230F)
        putBl(bytes, summary + 0x70, numericWrapper)
        putWords(bytes, summary + 0x74, 0x683A, 0x1914, 0x7820, 0x2100, 0x9100,
            0x9101, 0x9602, 0x9503, 0x4643, 0x7819, 0x0049, 0x4B10, 0x18D2,
            0x1852, 0x8812, 0x0111, 0x1A89, 0x0089, 0x4A0D, 0x1889, 0x9104,
            0x2102, 0x2205, 0x2327)
        putBl(bytes, summary + 0xA4, wrapper)
        putWords(bytes, summary + 0xA8, 0xB005, 0xBC08, 0x4698, 0xBCF0, 0xBC01, 0x4700)
        listOf(state + 0x31, state, 0x31B4, 0x3004, 0x08005F00,
            0x315C, 0x3204, 0x08000000 + base).forEachIndexed { index, value ->
            putInt(bytes, summary + 0xB4 + index * 4, value)
        }
        // Shared u16 slot, nonzero branch and type consumer: no BL on this proven path.
        putWords(bytes, numeric + 0x2A, 0x4E1C, 0x6832, 0x0078, 0x491C, 0x4688,
            0x1851, 0x1809, 0x8809, 0x4681, 0x2900, 0xD141)
        putInt(bytes, numeric + 0x9C, state)
        putInt(bytes, numeric + 0xA4, 0x3204)
        putWords(bytes, numeric + 0xC4, 0x4824, 0x1811, 0x7808, 0x3001, 0x7008,
            0x6830, 0x4922, 0x1844, 0x444C, 0x4D22, 0x4A22, 0x1883, 0x444B,
            0x881A, 0x0051, 0x1889, 0x0089, 0x1949, 0x7889, 0x8021)
        putInt(bytes, numeric + 0x158, 0x3210)
        putInt(bytes, numeric + 0x15C, 0x31FA)
        putGbaPointer(bytes, numeric + 0x160, 0x3000 + shift)
        putInt(bytes, numeric + 0x164, 0x3204)
        // Ninth argument -> TextPrinterTemplate.currentChar, full call-free wrapper body.
        putWords(bytes, wrapper, 0xB570, 0x464E, 0x4645, 0xB460, 0xB084, 0x1C0D,
            0x990A, 0x4688, 0x990B, 0x4689, 0x9E0C, 0x990D, 0x9C0E, 0x9400,
            0x466C, 0x7120, 0x4668, 0x7145, 0x7182, 0x71C3, 0x466A, 0x7980,
            0x7210, 0x4668, 0x79C0, 0x7250, 0x4668, 0x4642, 0x7282, 0x464A,
            0x72C2, 0x7B23, 0x2210, 0x4252, 0x1C10, 0x4018, 0x7320, 0x466B,
            0x7870, 0x0100, 0x250F, 0x7318, 0x7833, 0x1C28, 0x4018, 0x7B63,
            0x401A, 0x4302, 0x7362, 0x466B, 0x78B0, 0x0100, 0x402A, 0x4302,
            0x735A, 0x0609, 0x0E09, 0x4668, 0x2200)
        putBl(bytes, wrapper + 0x76, printer)
        putWords(bytes, wrapper + 0x7A, 0xB004, 0xBC18, 0x4698, 0x46A1, 0xBC70, 0xBC01, 0x4700)
        // Seventh-argument wrapper: template creation and two matching font-attribute calls.
        installStateFontAttributes(bytes, 0x2F00 + shift, 0x5C00 + shift)
        putWords(bytes, numericWrapper, 0xB570, 0xB084, 0x1C0C, 0x9E08, 0x9D09,
            0x990A, 0x0624, 0x0E24, 0x062D, 0x0E2D, 0x9100, 0x4669, 0x7108,
            0x4668, 0x7144, 0x7182, 0x71C3, 0x7980, 0x7208, 0x4668, 0x79C0,
            0x7248, 0x1C20, 0x2102)
        putBl(bytes, numericWrapper + 0x30, 0x2F00 + shift)
        putWords(bytes, numericWrapper + 0x34, 0x4669, 0x7288, 0x1C20, 0x2103)
        putBl(bytes, numericWrapper + 0x3C, 0x2F00 + shift)
        putWords(bytes, numericWrapper + 0x40, 0x4669, 0x72C8, 0x466B, 0x7B1A,
            0x2110, 0x4249, 0x1C08, 0x4010, 0x7318, 0x466A, 0x7870, 0x0100,
            0x240F, 0x7310, 0x7832, 0x1C20, 0x4010, 0x7B5A, 0x4011, 0x4301,
            0x7359, 0x466A, 0x78B0, 0x0100, 0x4021, 0x4301, 0x7351,
            0x4668, 0x1C29, 0x2200)
        putBl(bytes, numericWrapper + 0x7C, printer)
        putWords(bytes, numericWrapper + 0x80, 0xB004, 0xBC70, 0xBC01, 0x4700)
        // Printer's source-defined template-copy path; no rendering descendants are interpreted.
        putWords(bytes, printer, 0xB5F0, 0x1C06, 0x4694, 0x0609, 0x0E0D, 0x4803,
            0x6800, 0x2800, 0xD104, 0x2000, 0xE05C)
        putInt(bytes, printer + 0x18, 0x03001000 + shift)
        putWords(bytes, printer + 0x1C, 0x4819, 0x2200, 0x2101, 0x76C1, 0x7702,
            0x7745, 0x7782, 0x77C2, 0x1C04, 0x2106, 0x301A, 0x7002, 0x3801,
            0x3901, 0x2900, 0xDAFA, 0x1C21, 0x1C30, 0xC88C, 0xC18C, 0x6800,
            0x6008, 0x4660, 0x6120)
        putInt(bytes, printer + 0x84, 0x02008000 + shift)
        putWords(bytes, printer + 0xD0, 0xBCF0, 0xBC02, 0x4708)
        repeat(10) { id ->
            val row = base + (id + 1) * 60
            repeat(14) { bytes[row + it] = (it + 1).toByte() }
            bytes[row + 14] = (if (id == 9) 16 else 15).toByte()
            bytes[row + 15] = 0xFF.toByte()
        }
        return fixture.copy(
            layout = fixture.layout.copy(tables = ProfileTables(moveData = TableLayout(0x3000 + shift, 11, 12))),
            references = nativeReferences(base to summary + 0x98, (0x3000 + shift) to numeric + 0xD6),
        )
    }

    /** Assembled switch-based font lookup: complete leaf, synthetic relocated table and font data. */
    private fun installStateFontAttributes(bytes: ByteArray, entry: Int, fonts: Int) {
        putWords(bytes, entry, 0xB500, 0x0600, 0x0E02, 0x0609, 0x0E09, 0x2000,
            0x2907, 0xD866, 0x0088, 0x4902, 0x1840, 0x6800, 0x4687, 0)
        putGbaPointer(bytes, entry + 0x1C, entry + 0x20)
        listOf(0x40, 0x54, 0x68, 0x7C, 0x90, 0xA4, 0xB8, 0xD0).forEachIndexed { id, case ->
            putGbaPointer(bytes, entry + 0x20 + id * 4, entry + case)
        }
        repeat(4) { id ->
            val case = entry + 0x40 + id * 0x14
            putWords(bytes, case, 0x4903, 0x0050, 0x1880, 0x0080, 0x1840,
                0x7900 + id * 0x40, 0xE047 - id * 10, 0)
            putGbaPointer(bytes, case + 0x10, fonts)
        }
        putWords(bytes, entry + 0x90, 0x4803, 0x0051, 0x1889, 0x0089, 0x1809, 0x7A08, 0xE012, 0)
        putGbaPointer(bytes, entry + 0xA0, fonts)
        putWords(bytes, entry + 0xA4, 0x4803, 0x0051, 0x1889, 0x0089, 0x1809, 0x7A08, 0xE014, 0)
        putGbaPointer(bytes, entry + 0xB4, fonts)
        putWords(bytes, entry + 0xB8, 0x4804, 0x0051, 0x1889, 0x0089, 0x1809, 0x7A48,
            0x0700, 0x0F00, 0xE009, 0)
        putGbaPointer(bytes, entry + 0xCC, fonts)
        putWords(bytes, entry + 0xD0, 0x4804, 0x0051, 0x1889, 0x0089, 0x1809, 0x7A48,
            0x0900, 0xBC02, 0x4708, 0)
        putGbaPointer(bytes, entry + 0xE4, fonts)
        bytes[fonts + 2 * 12 + 6] = 1
        bytes[fonts + 2 * 12 + 7] = 2
    }

    private fun nativeDirectFixture(): NativeFixture {
        val bytes = ByteArray(0x8000)
        // Packed (level << 9 | move) records are a deliberately readable Japanese decoy.
        repeat(10) { id ->
            putGbaPointer(bytes, 0x100 + id * 4, 0x800 + id * 32)
            repeat(6) { entry -> putShort(bytes, 0x800 + id * 32 + entry * 2, 0x0221 + entry * 0x0201) }
            putShort(bytes, 0x800 + id * 32 + 12, 0xFFFF)
        }
        repeat(10) { id ->
            val row = 0x4000 + (id + 1) * 56
            repeat(14) { bytes[row + it] = (it + 1).toByte() }
            bytes[row + 14] = (if (id == 9) 16 else 15).toByte()
            bytes[row + 15] = 0xFF.toByte()
        }
        installRubyConsumer(bytes, 0x1000, 0x4000)
        // Source-defined menu text wrapper: forwards text, window, font and coordinates.
        putWords(bytes, 0x2800, 0xB530, 0xB081, 0x1C05, 0x1C0B, 0x061B, 0x0E1B,
            0x0612, 0x0E12, 0x4806, 0x6800, 0x4906, 0x880C, 0x9200, 0x1C29, 0x1C22)
        putBl(bytes, 0x281E, 0x2B00)
        putWords(bytes, 0x2822, 0xB001, 0xBC30, 0xBC01, 0x4700)
        putInt(bytes, 0x282C, 0x02001000)
        putInt(bytes, 0x2830, 0x02001006)
        return NativeFixture(
            bytes,
            layout(11).copy(
                tables = ProfileTables(moveData = TableLayout(0x3000, 11, 12)),
                languageManifest = resolvedLanguageManifest(
                    com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3RubySapphire,
                    language = com.enrpau.dualscreendex.parser.language.LanguageTag.JAPANESE,
                ),
            ),
            nativeReferences(0x4000 to 0x1012),
        )
    }

    /** Synthetic relocated instructions; literals and BL displacements are generated independently. */
    private fun installRubyConsumer(bytes: ByteArray, entry: Int, base: Int) {
        putWords(bytes, entry, 0xB500, 0x0400, 0x0C01, 0x4807, 0x4281, 0xD008,
            0x00C8, 0x1A40, 0x00C0, 0x4905, 0x1840, 0x210B, 0x220F)
        putBl(bytes, entry + 0x1A, 0x2800)
        putWords(bytes, entry + 0x1E, 0xBC01, 0x4700, 0)
        putInt(bytes, entry + 0x24, 0xFFFF)
        putGbaPointer(bytes, entry + 0x28, base)
        // One basic block sends the same callee-saved move ID to prose and numeric details.
        putWords(bytes, entry - 0x34, 0x2802, 0xD10D, 0x1C20)
        putBl(bytes, entry - 0x2E, entry)
        putShort(bytes, entry - 0x2A, 0x1C20)
        putBl(bytes, entry - 0x28, entry + 0x60)
        val numeric = entry + 0x60
        putWords(bytes, numeric, 0xB530, 0xB082, 0x0400, 0x0C04, 0x4808, 0x4284, 0xD03A,
            0x4A08, 0x0061, 0x1908, 0x0080, 0x1882, 0x7850, 0x1C0D, 0x2801, 0xD80B,
            0x4804, 0x2107, 0x220F)
        putBl(bytes, numeric + 0x26, 0x2800)
        putShort(bytes, numeric + 0x2A, 0xE00F)
        putInt(bytes, numeric + 0x2C, 0xFFFF)
        putGbaPointer(bytes, numeric + 0x30, 0x3000)
        putGbaPointer(bytes, numeric + 0x34, 0x2A00)
    }

    private fun nativeReferences(vararg roots: Pair<Int, Int>) = GbaReferenceIndex.fromTargets(
        roots.associate { (root, site) -> root to GbaTargetReferenceEvidence(
            count = 1, instructionSites = listOf(site), observedSites = 1, limitSites = 16, overflowReason = null,
        ) }, 32,
    )

    private fun putWords(bytes: ByteArray, offset: Int, vararg words: Int) {
        words.forEachIndexed { index, word -> putShort(bytes, offset + index * 2, word) }
    }

    private fun putShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putBl(bytes: ByteArray, site: Int, target: Int) {
        val displacement = target - site - 4
        putShort(bytes, site, 0xF000 or ((displacement shr 12) and 0x7FF))
        putShort(bytes, site + 2, 0xF800 or ((displacement shr 1) and 0x7FF))
    }

    private fun layout(moveCount: Int) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 4,
        moveCount = moveCount,
        tables = ProfileTables(),
        languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
    )

    private fun encodeGbaText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                in 'a'..'z' -> (0xD5 + char.code - 'a'.code).toByte()
                '-' -> 0xAE.toByte()
                '.' -> 0xAD.toByte()
                '\'' -> 0xB4.toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        putInt(target, offset, value)
    }

    private fun putInt(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
