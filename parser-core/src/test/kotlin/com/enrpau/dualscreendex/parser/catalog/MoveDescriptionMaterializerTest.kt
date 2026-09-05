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
