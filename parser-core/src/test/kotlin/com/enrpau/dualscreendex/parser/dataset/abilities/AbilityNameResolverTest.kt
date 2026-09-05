package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AbilityNameResolverTest {
    @Test
    fun cancellationPrecedesEmptyAndUnsupportedExactResolution() {
        listOf(null, TableLayout(0x100, 4, 13, variableLength = true)).forEach { exact ->
            val failure = ParserCancellationException()
            val session = abilitySession(
                ByteArray(0x1000),
                exactTableOverride = exact,
                cancellation = ParserCancellationToken { throw failure },
            )

            assertSame(failure, assertThrows(ParserCancellationException::class.java) {
                AbilityNameResolver().resolve(session, AbilitySemanticDomain(setOf(1, 2)))
            })
        }
    }

    @Test
    fun cancellationStopsDirectAndInheritedProposalsBeforeTheNextDecode() {
        listOf(true, false).forEach { direct ->
            val failure = ParserCancellationException()
            var cancelled = false
            var decodes = 0
            val session = abilitySession(
                ByteArray(0x1000),
                cancellation = ParserCancellationToken { if (cancelled) throw failure },
            )
            val resolver = AbilityNameResolver(AbilityNameTableDecoder { _, layout, _ ->
                decodes++
                cancelled = true
                AbilityNameTableOutcome.Rejected(layout, "synthetic malformed name table")
            })
            val layouts = listOf(AbilityNameTableLayout(0x100, 4, 13), AbilityNameTableLayout(0x200, 4, 13))

            assertSame(failure, assertThrows(ParserCancellationException::class.java) {
                resolver.resolve(
                    session,
                    AbilitySemanticDomain(setOf(1, 2)),
                    directCompiledConsumerLayouts = if (direct) layouts else emptyList(),
                    inheritedLayouts = if (direct) emptyList() else layouts,
                )
            })
            assertEquals(1, decodes)
        }
    }

    @Test
    fun selectedLayoutExtendsThroughEveryValidRowRequiredByTheSemanticDomain() {
        val selected = AbilityNameTableLayout(0x100, 4, 13)
        val complete = AbilityNameTableLayout(0x100, 6, 13)
        val bytes = ByteArray(0x100 + 6 * 13)
        putAbilityNames(bytes, complete, ordinaryAbilityNames(5))

        val result = AbilityNameResolver().resolve(
            session = abilitySession(bytes),
            semanticDomain = AbilitySemanticDomain(setOf(1, 5)),
            selectedLayout = selected,
        ) as DatasetResolution.Resolved<ResolvedAbilityNameLayout>

        assertEquals(complete, result.candidate.layout.table)
        assertEquals(5, result.candidate.layout.baseAbilityCount)
    }

    @Test
    fun invalidReferencedSuffixPreservesTheValidatedBaseCatalogAsPartial() {
        val selected = AbilityNameTableLayout(0x100, 4, 13)
        val complete = AbilityNameTableLayout(0x100, 6, 13)
        val bytes = ByteArray(0x100 + 6 * 13)
        putAbilityNames(bytes, complete, ordinaryAbilityNames(5))
        bytes.fill(0, 0x100 + 4 * 13, 0x100 + 5 * 13)

        val result = AbilityNameResolver().resolve(
            session = abilitySession(bytes),
            semanticDomain = AbilitySemanticDomain(setOf(1, 5)),
            selectedLayout = selected,
        ) as DatasetResolution.Partial<ResolvedAbilityNameLayout>

        assertEquals(selected, result.candidate.layout.table)
        assertEquals(3, result.candidate.layout.baseAbilityCount)
        assertTrue(result.reasons.any { it.contains("semantic coverage is 1/2") })
    }

    @Test
    fun selectedDeclaredCatalogResolvesWhenValidatedBaseStatsReferenceNoAbilities() {
        val selected = AbilityNameTableLayout(0x100, 4, 13)
        val bytes = ByteArray(0x100 + 4 * 13)
        putAbilityNames(bytes, selected, ordinaryAbilityNames(3))

        val result = AbilityNameResolver().resolve(
            session = abilitySession(bytes),
            semanticDomain = AbilitySemanticDomain(emptySet()),
            selectedLayout = selected,
        ) as DatasetResolution.Resolved<ResolvedAbilityNameLayout>

        assertEquals(selected, result.candidate.layout.table)
        assertEquals(3, result.candidate.layout.baseAbilityCount)
    }

    @Test
    fun exactProfileFastPathDoesNotBuildTheReferenceIndex() {
        val layout = AbilityNameTableLayout(0x100, 78, 13)
        val bytes = ByteArray(0x100 + 78 * 13)
        putAbilityNames(bytes, layout, ordinaryAbilityNames(77))
        var indexBuilds = 0

        val result = AbilityNameResolver().resolve(
            session = abilitySession(bytes, exactLayout = layout, onReferenceIndexBuild = { indexBuilds++ }),
            semanticDomain = AbilitySemanticDomain(setOf(1, 77)),
            compiledLayouts = listOf(AbilityNameTableLayout(0x200, 78, 13)),
        ) as DatasetResolution.Resolved<ResolvedAbilityNameLayout>

        assertEquals(layout, result.candidate.layout.table)
        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun unsupportedExactAbilityStorageFailsClosedInsteadOfBorrowingInheritedLayout() {
        val inherited = AbilityNameTableLayout(0x100, 4, 13)
        val bytes = ByteArray(0x1000)
        putAbilityNames(bytes, inherited, ordinaryAbilityNames(3))

        val result = AbilityNameResolver().resolve(
            session = abilitySession(
                bytes,
                exactTableOverride = TableLayout(0x500, 4, 13, variableLength = true),
            ),
            semanticDomain = AbilitySemanticDomain(setOf(1, 3)),
            inheritedLayouts = listOf(inherited),
        )

        assertTrue(result is DatasetResolution.Unavailable)
        assertTrue(
            (result as DatasetResolution.Unavailable<ResolvedAbilityNameLayout>)
                .reason.contains("exact", ignoreCase = true),
        )
    }

    @Test
    fun malformedExactAbilityMetadataReturnsTypedUnavailableWithoutThrowing() {
        val bytes = ByteArray(0x1000)
        val valid = AbilityNameTableLayout(0x100, 4, 13)
        putAbilityNames(bytes, valid, ordinaryAbilityNames(3))
        val malformed = listOf(
            "negative offset" to TableLayout(-1, 4, 13),
            "short count" to TableLayout(0x100, 1, 13),
            "zero width" to TableLayout(0x100, 4, 0),
            "unsupported width" to TableLayout(0x100, 4, 7),
            "zero stride" to TableLayout(0x100, 4, 13, stride = 0),
            "short stride" to TableLayout(0x100, 4, 13, stride = 12),
            "variable storage" to TableLayout(0x100, 4, 13, variableLength = true),
            "bank" to TableLayout(0x100, 4, 13, bank = 1),
            "banks" to TableLayout(0x100, 4, 13, banks = listOf(1)),
            "overlapping pointer" to TableLayout(0x100, 4, 13, pointerOffsets = listOf(4), stride = 20),
            "element size" to TableLayout(0x100, 4, 13, elementSize = 1),
            "bank adjustment" to TableLayout(0x100, 4, 13, bankAdjustment = 1),
            "bank remap" to TableLayout(0x100, 4, 13, bankRemap = mapOf(1 to 2)),
            "pointer values" to TableLayout(0x100, 4, 13, valuesArePointers = true),
            "foreign format" to TableLayout(0x100, 4, 13, format = TableRecordFormat.CFRU_MOVE_16),
        )

        malformed.forEach { (label, table) ->
            val attempt = runCatching {
                AbilityNameResolver().resolve(
                    session = abilitySession(bytes, exactTableOverride = table),
                    semanticDomain = AbilitySemanticDomain(setOf(1, 3)),
                )
            }

            assertTrue("$label must not throw: ${attempt.exceptionOrNull()}", attempt.isSuccess)
            val result = attempt.getOrThrow()
            assertTrue("$label: $result", result is DatasetResolution.Unavailable)
            assertTrue(
                "$label: ${(result as DatasetResolution.Unavailable<ResolvedAbilityNameLayout>).reason}",
                result.reason.contains("exact", ignoreCase = true),
            )
        }
    }

    @Test
    fun exactEmbeddedDescriptionPointerMetadataUsesTheSupportedDirectRecordAbi() {
        val layout = AbilityNameTableLayout(0x100, 4, nameWidth = 20, stride = 28)
        val bytes = ByteArray(0x1000)
        putAbilityNames(bytes, layout, ordinaryAbilityNames(3))

        val result = AbilityNameResolver().resolve(
            session = abilitySession(
                bytes,
                exactTableOverride = TableLayout(
                    offset = 0x100,
                    count = 4,
                    recordSize = 20,
                    pointerOffsets = listOf(20),
                    stride = 28,
                ),
            ),
            semanticDomain = AbilitySemanticDomain(setOf(1, 3)),
        ) as DatasetResolution.Resolved<ResolvedAbilityNameLayout>

        assertEquals(layout, result.candidate.layout.table)
        assertEquals(CandidateSource.EXACT_PROFILE, result.candidate.source)
    }

    @Test
    fun decisiveDirectNameCandidateBypassesUnrelatedReferenceIndexOverflow() {
        val direct = AbilityNameTableLayout(0x100, 4, 13)
        val compiled = AbilityNameTableLayout(0x300, 4, 13)
        val bytes = ByteArray(0x1000)
        putAbilityNames(bytes, direct, ordinaryAbilityNames(3))
        putAbilityNames(bytes, compiled, ordinaryAbilityNames(3))
        var indexBuilds = 0

        val result = AbilityNameResolver().resolve(
            session = abilitySession(
                bytes,
                referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                    "unrelated fixture reference overflow",
                    observedTargets = 2,
                    limitTargets = 1,
                ),
                onReferenceIndexBuild = { indexBuilds++ },
            ),
            semanticDomain = AbilitySemanticDomain(setOf(1, 3)),
            directCompiledConsumerLayouts = listOf(direct),
            compiledLayouts = listOf(compiled),
        ) as DatasetResolution.Resolved<ResolvedAbilityNameLayout>

        assertEquals(direct, result.candidate.layout.table)
        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, result.candidate.source)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun equalDirectNameCandidatesStayAmbiguousWithoutBuildingTheReferenceIndex() {
        val first = AbilityNameTableLayout(0x100, 4, 13)
        val second = AbilityNameTableLayout(0x300, 4, 13)
        val compiled = AbilityNameTableLayout(0x500, 4, 13)
        val bytes = ByteArray(0x1000)
        listOf(first, second, compiled).forEach { putAbilityNames(bytes, it, ordinaryAbilityNames(3)) }
        var indexBuilds = 0

        val result = AbilityNameResolver().resolve(
            session = abilitySession(
                bytes,
                referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                    "unrelated fixture reference overflow",
                    observedTargets = 2,
                    limitTargets = 1,
                ),
                onReferenceIndexBuild = { indexBuilds++ },
            ),
            semanticDomain = AbilitySemanticDomain(setOf(1, 3)),
            directCompiledConsumerLayouts = listOf(second, first),
            compiledLayouts = listOf(compiled),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun resolvesBattleTheaterDirectAbilityRecordsWithoutFixedRetailAssumptions() {
        val layout = AbilityNameTableLayout(0x100, 311, nameWidth = 20, stride = 28)
        val bytes = ByteArray(0x100 + 311 * 28)
        putAbilityNames(bytes, layout, ordinaryAbilityNames(310))

        val result = AbilityNameResolver().resolve(
            session = abilitySession(bytes),
            semanticDomain = AbilitySemanticDomain(setOf(1, 145, 310)),
            directCompiledConsumerLayouts = listOf(layout),
        ) as DatasetResolution.Resolved<ResolvedAbilityNameLayout>

        assertEquals(310, result.candidate.layout.baseAbilityCount)
        assertEquals(20, result.candidate.layout.table.nameWidth)
        assertEquals(28, result.candidate.layout.table.stride)
        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, result.candidate.source)
    }

    @Test
    fun equalIndependentCatalogsStayAmbiguousRegardlessOfOffsetOrEnumeration() {
        val firstLayout = AbilityNameTableLayout(0x100, 78, 13)
        val secondLayout = AbilityNameTableLayout(0x800, 78, 13)
        val bytes = ByteArray(0x1000)
        putAbilityNames(bytes, firstLayout, ordinaryAbilityNames(77))
        putAbilityNames(bytes, secondLayout, ordinaryAbilityNames(77))
        val session = abilitySession(bytes)
        val domain = AbilitySemanticDomain(setOf(1, 77))

        val first = AbilityNameResolver().resolve(
            session,
            domain,
            inheritedLayouts = listOf(secondLayout, firstLayout),
        )
        val second = AbilityNameResolver().resolve(
            session,
            domain,
            inheritedLayouts = listOf(firstLayout, secondLayout),
        )

        assertTrue(first is DatasetResolution.Ambiguous)
        assertTrue(second is DatasetResolution.Ambiguous)
        assertEquals(
            setOf(firstLayout, secondLayout),
            (first as DatasetResolution.Ambiguous<ResolvedAbilityNameLayout>)
                .candidates.map { it.layout.table }.toSet(),
        )
        assertEquals(
            setOf(firstLayout, secondLayout),
            (second as DatasetResolution.Ambiguous<ResolvedAbilityNameLayout>)
                .candidates.map { it.layout.table }.toSet(),
        )
    }

    @Test
    fun separatelyReportedAliasMetadataDoesNotBreakAnEqualDirectCatalogTie() {
        val ordinary = AbilityNameTableLayout(0x100, 255, 17)
        val aliasedNames = buildList {
            add("-------")
            repeat(254) { add("BASE ABILITY") }
            add("-")
            addAll(listOf("AIR LOCK", "VITAL SPIRIT", "WHITE SMOKE"))
        }
        val aliased = AbilityNameTableLayout(0x2000, aliasedNames.size, 17)
        val bytes = ByteArray(0x4000)
        putAbilityNames(bytes, ordinary, ordinaryAbilityNames(254))
        putAbilityNames(bytes, aliased, aliasedNames)

        val result = AbilityNameResolver().resolve(
            session = abilitySession(bytes),
            semanticDomain = AbilitySemanticDomain(setOf(1, 65, 254)),
            inheritedLayouts = listOf(aliased, ordinary),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
        assertEquals(
            setOf(ordinary, aliased),
            (result as DatasetResolution.Ambiguous<ResolvedAbilityNameLayout>)
                .candidates.map { it.layout.table }.toSet(),
        )
    }

    @Test
    fun retainsCompiledInstructionSitesAsCandidateProvenance() {
        val layout = AbilityNameTableLayout(0x300, 4, 13)
        val bytes = ByteArray(0x1000)
        putAbilityNames(bytes, layout, ordinaryAbilityNames(3))
        putThumbLiteralReferences(bytes, 0x40, 0x100, layout.offset.toInt())

        val result = AbilityNameResolver().resolve(
            session = abilitySession(bytes, useDefaultReferenceIndex = true),
            semanticDomain = AbilitySemanticDomain(setOf(1, 3)),
            compiledLayouts = listOf(layout),
        ) as DatasetResolution.Resolved<ResolvedAbilityNameLayout>

        assertEquals(listOf(0x40), result.candidate.provenance.compiledReferenceSites.offsets)
        assertEquals(1, result.candidate.strength.compiledReferenceCount)
    }

    @Test
    fun rootWorkExtentAndCandidateBudgetsAreTypedOutcomes() {
        val layouts = listOf(
            AbilityNameTableLayout(0x100, 4, 13),
            AbilityNameTableLayout(0x200, 4, 13),
        )
        val bytes = ByteArray(0x1000)
        layouts.forEach { putAbilityNames(bytes, it, ordinaryAbilityNames(3)) }
        val domain = AbilitySemanticDomain(setOf(1, 3))

        val roots = AbilityNameResolver().resolve(
            abilitySession(bytes, limits = ResolutionLimits(maxProbeRootsPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded<ResolvedAbilityNameLayout>
        val work = AbilityNameResolver().resolve(
            abilitySession(bytes, limits = ResolutionLimits(maxProbeWorkPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded<ResolvedAbilityNameLayout>
        val candidates = AbilityNameResolver().resolve(
            abilitySession(bytes, limits = ResolutionLimits(maxCandidatesPerDataset = 1)),
            domain,
            inheritedLayouts = layouts,
        ) as DatasetResolution.BudgetExceeded<ResolvedAbilityNameLayout>
        val extent = AbilityNameResolver().resolve(
            abilitySession(bytes, limits = ResolutionLimits(maxDatasetExtentBytes = 16)),
            domain,
            inheritedLayouts = listOf(layouts.first()),
        ) as DatasetResolution.BudgetExceeded<ResolvedAbilityNameLayout>

        assertEquals(BudgetKind.PROBE_ROOTS, roots.budgetKind)
        assertEquals(BudgetKind.PROBE_WORK, work.budgetKind)
        assertEquals(BudgetKind.CANDIDATES, candidates.budgetKind)
        assertEquals(BudgetKind.EXTENT, extent.budgetKind)
    }
}
