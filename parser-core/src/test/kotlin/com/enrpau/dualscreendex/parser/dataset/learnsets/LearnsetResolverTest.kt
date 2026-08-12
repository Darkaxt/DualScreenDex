package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnsetResolverTest {
    private val resolver = LearnsetResolver()

    @Test
    fun selectedLayoutsResolveExactlyOnceWithoutConsultingReferenceDiscovery() {
        val bytes = ByteArray(0x1800)
        val base = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val expanded = LearnsetTableLayout(0x800, 3, LearnsetFormat.LevelU8MoveU16)
        putCompleteTable(bytes, base, 0x1000)
        putCompleteTable(bytes, expanded, 0x1200)
        writeVerifiedSelector(bytes, base.offset.toInt(), expanded.offset.toInt())
        var indexBuilds = 0
        val session = learnsetSession(
            bytes,
            onReferenceIndexBuild = { indexBuilds++ },
            referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                "selected layouts must not discover reference roots",
                observedTargets = 2,
                limitTargets = 1,
            ),
        )
        val descriptor = SaveBlock1LearnsetSelectorDescriptor(
            0x3DA6,
            2,
            base.offset,
            expanded.offset,
            VERIFIED_SELECTOR_CODE_OFFSET,
        )
        val proof = requireNotNull(
            SaveBlock1LearnsetSelectorProof.verify(
                session,
                descriptor,
                setOf(base.offset.toInt(), expanded.offset.toInt()),
            ),
        )

        val result = resolver.resolveSelectedGen3(
            session = session,
            moveCount = 800,
            selectedTables = listOf(
                SelectedLearnsetTable(base, confidence = 1.0, referenceCount = 8),
                SelectedLearnsetTable(expanded, confidence = 1.0, referenceCount = 8),
            ),
            primaryOffset = base.offset,
            selectorProof = proof,
        )

        val resolved = requireNotNull(result.resolved)
        assertEquals(0, indexBuilds)
        assertEquals(listOf(base.offset, expanded.offset), resolved.tables.map { it.layout.table.offset })
        assertEquals(base.offset, resolved.primary?.layout?.table?.offset)
        assertEquals(descriptor, resolved.selector)
        assertEquals(null, result.reason)
    }

    @Test
    fun validatedExactProfileResolvesBeforeTheCompiledReferenceIndexIsBuilt() {
        val layout = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val bytes = ByteArray(0x1000)
        putCompleteTable(bytes, layout, 0x800)
        var indexBuilds = 0
        val session = learnsetSession(
            bytes = bytes,
            exactLayout = layout,
            onReferenceIndexBuild = { indexBuilds++ },
            referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                "exact resolution must not request the index",
                observedTargets = 2,
                limitTargets = 1,
            ),
        )

        val result = resolver.resolveGen3(session, 3, 800, profileLayout = layout)
        val primary = result.primary as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, primary.candidate.source)
        assertEquals(layout, primary.candidate.layout.table)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun legacyStandardExactProfileMetadataStillTakesTheTypedPackedFastPath() {
        val layout = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val bytes = ByteArray(0x1000)
        putCompleteTable(bytes, layout, 0x800)
        var indexBuilds = 0
        val session = learnsetSession(
            bytes = bytes,
            exactLayout = layout,
            exactLegacyTable = TableLayout(
                offset = layout.offset.toInt(),
                count = layout.speciesCount,
                recordSize = 4,
                variableLength = true,
            ),
            onReferenceIndexBuild = { indexBuilds++ },
            referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                "legacy exact metadata must not fall through to reference discovery",
                observedTargets = 2,
                limitTargets = 1,
            ),
        )

        val result = resolver.resolveGen3(session, 3, 800, profileLayout = layout)
        val primary = result.primary as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, primary.candidate.source)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun legacyStandardExactProfileRejectsAPackedWidthNotDerivedFromMoveCount() {
        val layout = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val bytes = ByteArray(0x1000)
        putCompleteTable(bytes, layout, 0x800)
        var indexBuilds = 0
        val session = learnsetSession(
            bytes = bytes,
            exactLayout = layout,
            exactLegacyTable = TableLayout(
                offset = layout.offset.toInt(),
                count = layout.speciesCount,
                recordSize = 4,
                variableLength = true,
            ),
            exactMoveCount = 500,
            onReferenceIndexBuild = { indexBuilds++ },
            referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                "invalid exact packed width must fail before reference discovery",
                observedTargets = 2,
                limitTargets = 1,
            ),
        )

        val result = resolver.resolveGen3(session, 3, 500, profileLayout = layout)

        assertTrue(result.primary is DatasetResolution.Unavailable)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun sameRootAllZeroPackedAliasYieldsToPositiveLevelWideAbi() {
        val root = 0x400
        val bytes = ByteArray(0x1200)
        repeat(3) { species ->
            val target = 0x800 + species * 0x20
            putPointer(bytes, root + species * 4, target)
            listOf(33, 1, 45, 3, 73, 7).forEachIndexed { index, word ->
                putU16(bytes, target + index * 2, word)
            }
            putU16(bytes, target + 12, 0xFFFF)
        }

        val result = resolver.resolveGen3(
            learnsetSession(bytes, references = mapOf(root to 12)),
            expectedSpeciesCount = 3,
            moveCount = 755,
        )

        val resolved = result.primary as DatasetResolution.Resolved
        assertEquals(
            LearnsetFormat.MoveU16LevelU16,
            resolved.candidate.layout.table.format,
        )
        assertEquals(
            listOf(
                LearnsetEntryValue(level = 1, moveId = 33),
                LearnsetEntryValue(level = 3, moveId = 45),
                LearnsetEntryValue(level = 7, moveId = 73),
            ),
            (resolved.candidate.layout.rows.first() as LearnsetRowOutcome.Decoded).entries,
        )
    }

    @Test
    fun sameRootPackedViewWithANullDomainRowRemainsAmbiguous() {
        val root = 0x400
        val speciesCount = 10
        val bytes = ByteArray(0x1800)
        repeat(speciesCount - 1) { species ->
            val target = 0x800 + species * 0x20
            putPointer(bytes, root + species * 4, target)
            listOf(33, 1, 45, 3, 73, 7).forEachIndexed { index, word ->
                putU16(bytes, target + index * 2, word)
            }
            putU16(bytes, target + 12, 0xFFFF)
        }

        val result = resolver.resolveGen3(
            learnsetSession(bytes, references = mapOf(root to 12)),
            expectedSpeciesCount = speciesCount,
            moveCount = 755,
        )

        val ambiguous = result.primary as DatasetResolution.Ambiguous
        assertEquals(
            setOf(LearnsetFormat.PackedU16(10), LearnsetFormat.MoveU16LevelU16),
            ambiguous.candidates.map { it.layout.table.format }.toSet(),
        )
    }

    @Test
    fun compiledLevelMoveShapeCarriesTheActualInstructionSite() {
        val root = 0x400
        val bytes = ByteArray(0x1400)
        val layout = LearnsetTableLayout(root.toLong(), 3, LearnsetFormat.LevelU8MoveU16)
        putCompleteTable(bytes, layout, 0x900)
        putThumbLiteralReference(bytes, instructionOffset = 0x80, literalOffset = 0x100, target = root)

        val result = resolver.resolveGen3(
            learnsetSession(bytes, useDefaultReferenceIndex = true),
            expectedSpeciesCount = 3,
            moveCount = 800,
        )
        val primary = result.primary as DatasetResolution.Resolved

        assertEquals(LearnsetFormat.LevelU8MoveU16, primary.candidate.layout.table.format)
        assertEquals(listOf(0x80), primary.candidate.provenance.compiledReferenceSites.offsets)
    }

    @Test
    fun equallyStrongIndependentRootsStayAmbiguousAndAdjacencyCreatesNoAlternatives() {
        val bytes = ByteArray(0x1800)
        val count = 3
        val roots = listOf(0x400, 0x400 + count * 4)
        roots.forEachIndexed { index, root ->
            putCompleteTable(
                bytes,
                LearnsetTableLayout(root.toLong(), count, LearnsetFormat.PackedU16(10)),
                0x900 + index * 0x200,
            )
        }

        val result = resolver.resolveGen3(
            learnsetSession(bytes, references = roots.associateWith { 1 }),
            expectedSpeciesCount = count,
            moveCount = 800,
        )

        assertTrue(result.primary is DatasetResolution.Ambiguous)
        assertNull(result.selectorBoundAlternatives)
    }

    @Test
    fun directSelectorProofRetainsDistinctTypedFormatsWithoutOffsetRanking() {
        val bytes = ByteArray(0x1800)
        val zero = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val nonZero = LearnsetTableLayout(0x800, 3, LearnsetFormat.LevelU8MoveU16)
        putCompleteTable(bytes, zero, 0x1000)
        putCompleteTable(bytes, nonZero, 0x1200)
        writeVerifiedSelector(bytes, zero.offset.toInt(), nonZero.offset.toInt())
        var indexBuilds = 0
        val selector = SaveBlock1LearnsetSelectorDescriptor(
            saveBlock1ByteOffset = 0x3DA6,
            mask = 0x02,
            zeroTableOffset = zero.offset,
            nonZeroTableOffset = nonZero.offset,
            codeOffset = VERIFIED_SELECTOR_CODE_OFFSET,
        )
        val session = learnsetSession(
            bytes,
            onReferenceIndexBuild = { indexBuilds++ },
            referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                "direct selector proof must not build the reference index",
                observedTargets = 2,
                limitTargets = 1,
            ),
        )
        val proof = requireNotNull(
            SaveBlock1LearnsetSelectorProof.verify(
                session,
                selector,
                setOf(zero.offset.toInt(), nonZero.offset.toInt()),
            ),
        )

        val result = resolver.resolveGen3(
            session,
            expectedSpeciesCount = 3,
            moveCount = 800,
            selectorDescriptor = selector,
            selectorProof = proof,
        )
        val primary = result.primary as DatasetResolution.Resolved
        val alternatives = requireNotNull(result.selectorBoundAlternatives)

        assertEquals(zero, primary.candidate.layout.table)
        assertEquals(0, indexBuilds)
        assertEquals(zero.format, alternatives.zero.candidate.layout.table.format)
        assertEquals(nonZero.format, alternatives.nonZero.candidate.layout.table.format)
        assertEquals(listOf(VERIFIED_SELECTOR_CODE_OFFSET), primary.candidate.provenance.compiledReferenceSites.offsets)
    }

    @Test
    fun selectorBindingMergesExactAndDirectProvenanceWithoutReplacingTheExactPrimary() {
        val bytes = ByteArray(0x1800)
        val zero = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val nonZero = LearnsetTableLayout(0x800, 3, LearnsetFormat.LevelU8MoveU16)
        putCompleteTable(bytes, zero, 0x1000)
        putCompleteTable(bytes, nonZero, 0x1200)
        writeVerifiedSelector(bytes, zero.offset.toInt(), nonZero.offset.toInt())
        val descriptor = SaveBlock1LearnsetSelectorDescriptor(
            0x3DA6,
            2,
            zero.offset,
            nonZero.offset,
            VERIFIED_SELECTOR_CODE_OFFSET,
        )
        var indexBuilds = 0
        val session = learnsetSession(
            bytes,
            exactLayout = zero,
            onReferenceIndexBuild = { indexBuilds++ },
        )
        val proof = requireNotNull(
            SaveBlock1LearnsetSelectorProof.verify(
                session,
                descriptor,
                setOf(zero.offset.toInt(), nonZero.offset.toInt()),
            ),
        )

        val result = resolver.resolveGen3(
            session,
            3,
            800,
            profileLayout = zero,
            selectorProof = proof,
        )
        val primary = result.primary as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, primary.candidate.source)
        assertEquals(0, indexBuilds)
        assertEquals(
            setOf(CandidateSource.EXACT_PROFILE.name, CandidateSource.DIRECT_COMPILED_CONSUMER.name),
            primary.candidate.provenance.reasons
                .filter { it.kind == com.enrpau.dualscreendex.parser.resolution.CandidateReasonKind.INFORMATION }
                .map { it.message.removePrefix("validated candidate source ") }
                .toSet(),
        )
        assertEquals(
            listOf(VERIFIED_SELECTOR_CODE_OFFSET),
            primary.candidate.provenance.compiledReferenceSites.offsets,
        )
    }

    @Test
    fun selectorBindingRetainsInheritedAndPublishedSupportingProvenance() {
        val bytes = ByteArray(0x1800)
        val zero = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val nonZero = LearnsetTableLayout(0x800, 3, LearnsetFormat.LevelU8MoveU16)
        putCompleteTable(bytes, zero, 0x1000)
        putCompleteTable(bytes, nonZero, 0x1200)
        writeVerifiedSelector(bytes, zero.offset.toInt(), nonZero.offset.toInt())
        val descriptor = SaveBlock1LearnsetSelectorDescriptor(
            0x3DA6,
            2,
            zero.offset,
            nonZero.offset,
            VERIFIED_SELECTOR_CODE_OFFSET,
        )
        val session = learnsetSession(bytes, references = mapOf(zero.offset.toInt() to 1))
        val proof = requireNotNull(
            SaveBlock1LearnsetSelectorProof.verify(
                session,
                descriptor,
                setOf(zero.offset.toInt(), nonZero.offset.toInt()),
            ),
        )

        val result = resolver.resolveGen3(
            session,
            3,
            800,
            profileLayout = zero,
            publishedLayouts = listOf(zero),
            selectorProof = proof,
        )
        val primary = result.primary as DatasetResolution.Resolved
        val supportingSources = primary.candidate.provenance.reasons
            .filter { it.kind == com.enrpau.dualscreendex.parser.resolution.CandidateReasonKind.INFORMATION }
            .map { it.message.removePrefix("validated candidate source ") }
            .toSet()

        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, primary.candidate.source)
        assertTrue(CandidateSource.INHERITED_FAMILY_LAYOUT.name in supportingSources)
        assertTrue(CandidateSource.PUBLISHED_HEADER.name in supportingSources)
        assertTrue(CandidateSource.DIRECT_COMPILED_CONSUMER.name in supportingSources)
    }

    @Test
    fun incompleteSelectorProofDoesNotGrantAuthorityToItsOneValidArm() {
        val bytes = ByteArray(0x1400)
        val valid = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        putCompleteTable(bytes, valid, 0x900)
        writeVerifiedSelector(bytes, valid.offset.toInt(), 0x800)
        val selector = SaveBlock1LearnsetSelectorDescriptor(
            saveBlock1ByteOffset = 0x3DA6,
            mask = 2,
            zeroTableOffset = valid.offset,
            nonZeroTableOffset = 0x800,
            codeOffset = VERIFIED_SELECTOR_CODE_OFFSET,
        )
        val session = learnsetSession(bytes, references = mapOf(valid.offset.toInt() to 1))
        val proof = requireNotNull(
            SaveBlock1LearnsetSelectorProof.verify(session, selector, setOf(valid.offset.toInt(), 0x800)),
        )

        val result = resolver.resolveGen3(
            session,
            expectedSpeciesCount = 3,
            moveCount = 800,
            selectorDescriptor = selector,
            selectorProof = proof,
        )
        val primary = result.primary as DatasetResolution.Resolved

        assertEquals(CandidateSource.COMPILED_REFERENCE, primary.candidate.source)
        assertNull(result.selectorBoundAlternatives)
    }

    @Test
    fun corroboratedPublishedLayoutOutranksInheritedLayout() {
        val bytes = ByteArray(0x1800)
        val inherited = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val published = LearnsetTableLayout(0x800, 3, LearnsetFormat.PackedU16(10))
        putCompleteTable(bytes, inherited, 0x1000)
        putCompleteTable(bytes, published, 0x1200)

        val result = resolver.resolveGen3(
            learnsetSession(bytes, references = mapOf(published.offset.toInt() to 1)),
            expectedSpeciesCount = 3,
            moveCount = 800,
            profileLayout = inherited,
            publishedLayouts = listOf(published),
        )
        val primary = result.primary as DatasetResolution.Resolved

        assertEquals(published, primary.candidate.layout.table)
        assertEquals(CandidateSource.PUBLISHED_HEADER, primary.candidate.source)
    }

    @Test
    fun referenceRootProbeWorkAndExtentLimitsReturnTypedBudgetOutcomes() {
        val bytes = ByteArray(0x1800)
        val layouts = listOf(
            LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10)),
            LearnsetTableLayout(0x800, 3, LearnsetFormat.PackedU16(10)),
        )
        layouts.forEachIndexed { index, layout -> putCompleteTable(bytes, layout, 0x1000 + index * 0x200) }

        val reference = resolver.resolveGen3(
            learnsetSession(
                bytes,
                referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                    "reference target budget exceeded",
                    observedTargets = 2,
                    limitTargets = 1,
                ),
            ),
            3,
            800,
        ).primary as DatasetResolution.BudgetExceeded
        val roots = resolver.resolveGen3(
            learnsetSession(
                bytes,
                references = layouts.associate { it.offset.toInt() to 1 },
                limits = ResolutionLimits(maxProbeRootsPerDataset = 1),
            ),
            3,
            800,
        ).primary as DatasetResolution.BudgetExceeded
        val work = resolver.resolveGen3(
            learnsetSession(
                bytes,
                references = mapOf(layouts.first().offset.toInt() to 1),
                limits = ResolutionLimits(maxProbeWorkPerDataset = 1),
            ),
            3,
            800,
        ).primary as DatasetResolution.BudgetExceeded
        val extent = resolver.resolveGen3(
            learnsetSession(
                bytes,
                limits = ResolutionLimits(maxDatasetExtentBytes = 8),
            ),
            3,
            800,
            profileLayout = layouts.first(),
        ).primary as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.REFERENCE_TARGETS, reference.budgetKind)
        assertEquals(BudgetKind.PROBE_ROOTS, roots.budgetKind)
        assertEquals(BudgetKind.PROBE_WORK, work.budgetKind)
        assertEquals(BudgetKind.EXTENT, extent.budgetKind)
    }

    @Test
    fun selectorAlternativesExposeAnImmutableCandidateList() {
        val bytes = ByteArray(0x1800)
        val zero = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val nonZero = LearnsetTableLayout(0x800, 3, LearnsetFormat.LevelU8MoveU16)
        putCompleteTable(bytes, zero, 0x1000)
        putCompleteTable(bytes, nonZero, 0x1200)
        writeVerifiedSelector(bytes, zero.offset.toInt(), nonZero.offset.toInt())
        val descriptor = SaveBlock1LearnsetSelectorDescriptor(
            0x3DA6,
            2,
            zero.offset,
            nonZero.offset,
            VERIFIED_SELECTOR_CODE_OFFSET,
        )
        val session = learnsetSession(bytes)
        val proof = requireNotNull(
            SaveBlock1LearnsetSelectorProof.verify(
                session,
                descriptor,
                setOf(zero.offset.toInt(), nonZero.offset.toInt()),
            ),
        )
        val result = resolver.resolveGen3(
            session,
            3,
            800,
            selectorDescriptor = descriptor,
            selectorProof = proof,
        )
        val alternatives = requireNotNull(result.selectorBoundAlternatives)

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (alternatives.candidates as MutableList<*>).clear()
        }
        val equalCopy = SelectorBoundLearnsetAlternatives(
            alternatives.descriptor,
            alternatives.zero,
            alternatives.nonZero,
        )
        assertEquals(alternatives, equalCopy)
        assertEquals(alternatives.hashCode(), equalCopy.hashCode())
    }

    @Test
    fun selectorCodeOutsideTheAnalyzedRomCannotGrantDirectConsumerAuthority() {
        val bytes = ByteArray(0x1800)
        val zero = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val nonZero = LearnsetTableLayout(0x800, 3, LearnsetFormat.LevelU8MoveU16)
        putCompleteTable(bytes, zero, 0x1000)
        putCompleteTable(bytes, nonZero, 0x1200)

        val result = resolver.resolveGen3(
            learnsetSession(bytes),
            3,
            800,
            selectorDescriptor = SaveBlock1LearnsetSelectorDescriptor(
                0x3DA6,
                2,
                zero.offset,
                nonZero.offset,
                bytes.size,
            ),
        )

        assertTrue(result.primary is DatasetResolution.Unavailable)
        assertNull(result.selectorBoundAlternatives)
    }

    @Test
    fun selectorArmCandidatesShareOneGlobalCandidateBudget() {
        val bytes = ByteArray(0x1800)
        val roots = listOf(0x400, 0x800)
        roots.forEachIndexed { rootIndex, root ->
            repeat(3) { species ->
                val target = 0x1000 + rootIndex * 0x200 + species * 0x20
                putPointer(bytes, root + species * 4, target)
                // Valid as both packed-u16 (two level-zero moves) and wide (move 513 at level 50).
                putU16(bytes, target, 513)
                putU16(bytes, target + 2, 50)
                putU16(bytes, target + 4, 0xFFFF)
            }
        }
        writeVerifiedSelector(bytes, roots[0], roots[1])
        val descriptor = SaveBlock1LearnsetSelectorDescriptor(
            0x3DA6,
            2,
            roots[0].toLong(),
            roots[1].toLong(),
            VERIFIED_SELECTOR_CODE_OFFSET,
        )
        val session = learnsetSession(
            bytes,
            limits = ResolutionLimits(maxCandidatesPerDataset = 2),
        )
        val proof = requireNotNull(
            SaveBlock1LearnsetSelectorProof.verify(session, descriptor, roots.toSet()),
        )

        val result = resolver.resolveGen3(
            session,
            3,
            800,
            selectorDescriptor = descriptor,
            selectorProof = proof,
        )

        val exceeded = result.primary as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.CANDIDATES, exceeded.budgetKind)
        assertEquals(3L, exceeded.observed)
        assertEquals(2L, exceeded.limit)
    }

    @Test
    fun zeroFilledSelectorDescriptorCannotGrantDirectConsumerAuthority() {
        val bytes = ByteArray(0x1800)
        val zero = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        val nonZero = LearnsetTableLayout(0x800, 3, LearnsetFormat.LevelU8MoveU16)
        putCompleteTable(bytes, zero, 0x1000)
        putCompleteTable(bytes, nonZero, 0x1200)
        val descriptor = SaveBlock1LearnsetSelectorDescriptor(
            0x3DA6,
            2,
            zero.offset,
            nonZero.offset,
            0x200,
        )
        val session = learnsetSession(bytes)

        assertNull(
            SaveBlock1LearnsetSelectorProof.verify(
                session,
                descriptor,
                setOf(zero.offset.toInt(), nonZero.offset.toInt()),
            ),
        )

        val result = resolver.resolveGen3(
            session,
            3,
            800,
            selectorDescriptor = descriptor,
        )

        assertTrue(result.primary is DatasetResolution.Unavailable)
        assertNull(result.selectorBoundAlternatives)
    }

    @Test
    fun invalidSelectorDoesNotDisableTheExactPreIndexFastPath() {
        val bytes = ByteArray(0x1400)
        val exact = LearnsetTableLayout(0x400, 3, LearnsetFormat.PackedU16(10))
        putCompleteTable(bytes, exact, 0x900)
        var indexBuilds = 0

        val result = resolver.resolveGen3(
            learnsetSession(
                bytes,
                exactLayout = exact,
                onReferenceIndexBuild = { indexBuilds++ },
                referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                    "invalid selector must not disable exact authority",
                    observedTargets = 2,
                    limitTargets = 1,
                ),
            ),
            3,
            800,
            profileLayout = exact,
            selectorDescriptor = SaveBlock1LearnsetSelectorDescriptor(
                0x3DA6,
                2,
                exact.offset,
                0x800,
                0x200,
            ),
        )
        val primary = result.primary as DatasetResolution.Resolved

        assertEquals(CandidateSource.EXACT_PROFILE, primary.candidate.source)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun aggregateExtentLedgerCoversEveryFormatAttemptAtACompiledRoot() {
        val bytes = ByteArray(0x1200)
        val root = 0x400
        repeat(3) { species ->
            val target = 0x800 + species * 0x20
            putPointer(bytes, root + species * 4, target)
            putU16(bytes, target, 513)
            putU16(bytes, target + 2, 50)
            putU16(bytes, target + 4, 0xFFFF)
        }

        val result = resolver.resolveGen3(
            learnsetSession(
                bytes,
                references = mapOf(root to 1),
                limits = ResolutionLimits(maxDatasetExtentBytes = 20),
            ),
            3,
            800,
        )

        val exceeded = result.primary as DatasetResolution.BudgetExceeded
        assertEquals(BudgetKind.EXTENT, exceeded.budgetKind)
        assertEquals(24L, exceeded.observed)
        assertEquals(20L, exceeded.limit)
    }

    private fun putCompleteTable(
        bytes: ByteArray,
        layout: LearnsetTableLayout,
        dataOffset: Int,
    ) {
        repeat(layout.speciesCount) { species ->
            val target = dataOffset + species * 0x20
            putPointer(bytes, layout.offset.toInt() + species * 4, target)
            val entries = listOf(LearnsetEntryValue(if (species == 0) 37 else 29, 700 + species))
            when (layout.format) {
                is LearnsetFormat.PackedU16 -> putPacked(
                    bytes,
                    target,
                    entries,
                    layout.format.moveBits,
                )
                LearnsetFormat.LevelU8MoveU16 -> putLevelMove(bytes, target, entries)
                LearnsetFormat.MoveU16LevelU8 -> putMoveLevel(bytes, target, entries)
                LearnsetFormat.MoveU16LevelU16 -> putWide(bytes, target, entries)
            }
        }
    }

    private fun writeVerifiedSelector(bytes: ByteArray, zeroRoot: Int, nonZeroRoot: Int) {
        val offset = 0x20
        putU16(bytes, offset, 0x2102)
        putU16(bytes, offset + 2, 0xB500)
        putLiteralLoad(bytes, offset + 4, 3, 0x80)
        putU16(bytes, offset + 6, 0x681A)
        putLiteralLoad(bytes, offset + 8, 3, 0x84)
        putU16(bytes, offset + 10, 0x5CD2)
        putU16(bytes, offset + 12, 0x4211)
        putU16(bytes, offset + 14, 0xD001)
        putU16(bytes, offset + 16, 0xE00E)
        putLiteralLoad(bytes, 0x34, 3, 0x88)
        putLiteralLoad(bytes, 0x50, 3, 0x8C)
        putU32(bytes, 0x80, 0x030036F0)
        putU32(bytes, 0x84, 0x00003DA6)
        putU32(bytes, 0x88, 0x08000000 + zeroRoot)
        putU32(bytes, 0x8C, 0x08000000 + nonZeroRoot)
        repeat(8) { index ->
            val instruction = 0x100 + index * 12
            val literal = 0x500 + index * 4
            putLiteralLoad(bytes, instruction, 3, literal)
            putU16(bytes, instruction + 2, 0x681B)
            putU16(bytes, instruction + 4, 0x7918)
            putU16(bytes, instruction + 6, 0x7959)
            putU32(bytes, literal, 0x030036F0)
        }
    }

    private fun putLiteralLoad(bytes: ByteArray, instructionOffset: Int, register: Int, literalOffset: Int) {
        val pc = (instructionOffset + 4) and -4
        putU16(bytes, instructionOffset, 0x4800 or (register shl 8) or ((literalOffset - pc) / 4))
    }

    private companion object {
        const val VERIFIED_SELECTOR_CODE_OFFSET = 0x24
    }
}
