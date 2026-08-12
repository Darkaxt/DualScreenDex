package com.enrpau.dualscreendex.parser.dataset.types

import com.enrpau.dualscreendex.parser.analysis.ExactTableLayoutSnapshot
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateEligibility
import com.enrpau.dualscreendex.parser.resolution.CandidateIdentity
import com.enrpau.dualscreendex.parser.resolution.CandidateProvenance
import com.enrpau.dualscreendex.parser.resolution.CandidateReason
import com.enrpau.dualscreendex.parser.resolution.CandidateReasonKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSelector
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.CandidateStrength
import com.enrpau.dualscreendex.parser.resolution.CompiledReferenceSites
import com.enrpau.dualscreendex.parser.resolution.DatasetCandidate
import com.enrpau.dualscreendex.parser.resolution.DatasetKind
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.resolution.EvidenceCoverage
import com.enrpau.dualscreendex.parser.resolution.exactProfileEligibility
import kotlin.math.sqrt

/** Bounded type-chart discovery and evidence selection. */
object TypeChartResolver {
    private val codec = TypeChartCodec()

    fun resolve(
        session: RomAnalysisSession,
        activeTypeIds: Set<Int>,
        selectedLayout: TypeChartTableLayout? = null,
        directCompiledConsumerRoots: Collection<Number> = emptyList(),
        publishedRoots: Collection<Number> = emptyList(),
        compiledRoots: Collection<Number> = emptyList(),
        inheritedRoots: Collection<Number> = emptyList(),
    ): DatasetResolution<ResolvedTypeChartLayout> {
        if (activeTypeIds.isEmpty()) return unavailable(0, "active type domain must not be empty")
        if (activeTypeIds.any { it !in 0 until MAX_TYPES }) {
            return unavailable(0, "active type domain contains an ID outside 0..${MAX_TYPES - 1}")
        }
        val lowerBound = Math.addExact(activeTypeIds.max(), 1)

        when (val exactLayout = exactLayout(session, lowerBound)) {
            ExactLayout.Absent -> Unit
            is ExactLayout.Unsupported -> return unavailable(0, exactLayout.reason)
            is ExactLayout.Supported -> when (
                val exact = assess(
                    session,
                    exactLayout.layout,
                    CandidateSource.EXACT_PROFILE,
                    emptyList(),
                    activeTypeIds.size,
                )
            ) {
                is Assessment.Candidate -> {
                    val selected = CandidateSelector.select(
                        session,
                        DatasetKind.TYPE_CHART,
                        sequenceOf(exact.value),
                    )
                    if (selected is DatasetResolution.Resolved || selected is DatasetResolution.Partial) {
                        return selected
                    }
                    return unavailable(1, "matching exact type-chart evidence was not uniquely selectable")
                }
                is Assessment.ExtentBudget -> return exact.toResolution()
                is Assessment.ReferenceSitesBudget -> return exact.toResolution()
                Assessment.Rejected -> return unavailable(
                    1,
                    "matching exact type-chart layout failed byte-level validation",
                )
            }
        }

        if (selectedLayout != null) {
            return when (
                val assessment = assess(
                    session,
                    selectedLayout,
                    CandidateSource.INHERITED_FAMILY_LAYOUT,
                    emptyList(),
                    activeTypeIds.size,
                )
            ) {
                is Assessment.Candidate -> CandidateSelector.select(
                    session,
                    DatasetKind.TYPE_CHART,
                    sequenceOf(assessment.value),
                )
                is Assessment.ExtentBudget -> assessment.toResolution()
                is Assessment.ReferenceSitesBudget -> assessment.toResolution()
                Assessment.Rejected -> unavailable(1, "selected type-chart layout failed its typed codec")
            }
        }

        val roots = when (
            val normalized = normalizeRoots(
                directCompiledConsumerRoots,
                publishedRoots,
                compiledRoots,
                inheritedRoots,
            )
        ) {
            is RootNormalization.Invalid -> return unavailable(0, normalized.reason)
            is RootNormalization.Valid -> normalized.roots
        }
        if (roots.map(RootProbe::offset).distinct().size > session.limits.maxProbeRootsPerDataset) {
            return budgetExceeded(
                BudgetKind.PROBE_ROOTS,
                observed = session.limits.maxProbeRootsPerDataset.toLong() + 1L,
                limit = session.limits.maxProbeRootsPerDataset.toLong(),
                complete = false,
                reason = "type-chart probe-root budget exceeded (observed at least " +
                    "${session.limits.maxProbeRootsPerDataset + 1}, limit " +
                    "${session.limits.maxProbeRootsPerDataset})",
            )
        }

        val referenceIndex = session.gbaReferenceIndex
            ?: return unavailable(0, "type-chart discovery requires a GBA analysis session")
        referenceIndex.overflowReason?.let { reason ->
            return budgetExceeded(
                BudgetKind.REFERENCE_TARGETS,
                referenceIndex.observedTargets.toLong(),
                referenceIndex.limitTargets.toLong(),
                complete = false,
                reason = reason,
            )
        }

        val work = ProbeWorkBudget(session.limits.maxProbeWorkPerDataset.toLong())
        val discovered = mutableListOf<AssessedProbe>()
        for (root in roots) {
            if (!work.tryConsume()) return probeWorkExceeded(work)
            val legacy = TypeChartTableLayout(root.offset, TypeChartAbi.LEGACY_TRIPLETS)
            when (val assessment = assess(
                session,
                legacy,
                root.source,
                referenceEvidence(referenceIndex.target(root.offset.toInt())),
                activeTypeIds.size,
            )) {
                is Assessment.Candidate -> {
                    if (discovered.size == session.limits.maxCandidatesPerDataset) {
                        return candidateBudgetExceeded(session, discovered.size.toLong() + 1L)
                    }
                    discovered += AssessedProbe(assessment.value)
                }
                is Assessment.ExtentBudget -> return assessment.toResolution()
                is Assessment.ReferenceSitesBudget -> return assessment.toResolution()
                Assessment.Rejected -> Unit
            }

            for (dimension in lowerBound..MAX_TYPES) {
                val u32 = TypeChartTableLayout(root.offset, TypeChartAbi.DENSE_U32_Q412, dimension)
                if (!work.tryConsume()) return probeWorkExceeded(work)
                val u32End = runCatching { denseEnd(u32) }.getOrNull()
                val u32Evidence = referencedSpanEvidence(session, root, u32End)
                if (u32Evidence != null) {
                    when (val assessment = assess(
                        session,
                        u32,
                        root.source,
                        u32Evidence,
                        activeTypeIds.size,
                    )) {
                        is Assessment.Candidate -> {
                            if (discovered.size == session.limits.maxCandidatesPerDataset) {
                                return candidateBudgetExceeded(session, discovered.size.toLong() + 1L)
                            }
                            discovered += AssessedProbe(assessment.value)
                        }
                        is Assessment.ExtentBudget -> return assessment.toResolution()
                        is Assessment.ReferenceSitesBudget -> return assessment.toResolution()
                        Assessment.Rejected -> Unit
                    }
                }

                val pair = TypeChartTableLayout(
                    root.offset,
                    TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE,
                    dimension,
                )
                if (!work.tryConsume()) return probeWorkExceeded(work)
                val pairEnd = runCatching { denseEnd(pair) }.getOrNull()
                val pairEvidence = referencedSpanEvidence(session, root, pairEnd)
                if (pairEvidence != null) {
                    when (val assessment = assess(
                        session,
                        pair,
                        root.source,
                        pairEvidence,
                        activeTypeIds.size,
                    )) {
                        is Assessment.Candidate -> {
                            if (discovered.size == session.limits.maxCandidatesPerDataset) {
                                return candidateBudgetExceeded(session, discovered.size.toLong() + 1L)
                            }
                            discovered += AssessedProbe(assessment.value)
                        }
                        is Assessment.ExtentBudget -> return assessment.toResolution()
                        is Assessment.ReferenceSitesBudget -> return assessment.toResolution()
                        Assessment.Rejected -> Unit
                    }
                }
            }
        }

        val retained = prunePairInteriorsNonTransitively(discovered)
        if (retained.size > session.limits.maxCandidatesPerDataset) {
            return budgetExceeded(
                BudgetKind.CANDIDATES,
                session.limits.maxCandidatesPerDataset.toLong() + 1L,
                session.limits.maxCandidatesPerDataset.toLong(),
                complete = false,
                reason = "type-chart candidate budget exceeded (observed at least " +
                    "${session.limits.maxCandidatesPerDataset + 1}, limit " +
                    "${session.limits.maxCandidatesPerDataset})",
            )
        }
        return CandidateSelector.select(
            session,
            DatasetKind.TYPE_CHART,
            retained.asSequence().map(AssessedProbe::candidate),
        )
    }

    private fun assess(
        session: RomAnalysisSession,
        layout: TypeChartTableLayout,
        source: CandidateSource,
        references: List<GbaTargetReferenceEvidence>,
        activeTypeCount: Int,
    ): Assessment = when (val decoded = codec.decode(session, layout)) {
        is TypeChartTableOutcome.Rejected -> Assessment.Rejected
        is TypeChartTableOutcome.ExtentBudgetExceeded -> Assessment.ExtentBudget(
            decoded.observedBytes,
            decoded.limitBytes,
            decoded.reason,
        )
        is TypeChartTableOutcome.Decoded -> {
            if (sourceRequiresCompiledEvidence(source) && references.isEmpty()) return Assessment.Rejected
            referenceSiteOverflow(references)?.let { return it }
            val compiledSites = compiledSites(session, references)
            if (compiledSites.budgetExceeded) {
                return Assessment.ReferenceSitesBudget(
                    observed = compiledSites.observedSites,
                    limit = compiledSites.limitSites.toLong(),
                    reason = requireNotNull(compiledSites.overflowReason),
                )
            }
            val resolved = ResolvedTypeChartLayout(layout, decoded.rows)
            val unavailableSites = references.mapNotNull { it.siteEvidenceUnavailableReason }.distinct().sorted()
            val reasons = buildList {
                unavailableSites.forEach { reason ->
                    add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
                }
                compiledSites.overflowReason?.let { reason ->
                    add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
                }
            }
            Assessment.Candidate(
                DatasetCandidate(
                    identity = CandidateIdentity(layout.layoutIdentity.value),
                    kind = DatasetKind.TYPE_CHART,
                    layout = resolved,
                    source = source,
                    strength = CandidateStrength(
                        semanticCoverage = EvidenceCoverage(activeTypeCount, activeTypeCount),
                        structuralCoverage = EvidenceCoverage(1, 1),
                        compiledReferenceCount = references.firstOrNull()?.count ?: 0,
                        datasetQuality = when (layout.abi) {
                            TypeChartAbi.LEGACY_TRIPLETS -> 3
                            TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE -> 2
                            TypeChartAbi.DENSE_U32_Q412 -> 1
                        },
                    ),
                    diagnosticOffset = layout.offset.toInt(),
                    diagnosticLabel = layout.layoutIdentity.value,
                    eligibility = if (source == CandidateSource.EXACT_PROFILE) {
                        session.exactProfileEligibility()
                    } else {
                        CandidateEligibility.validated(source)
                    },
                    provenance = CandidateProvenance(
                        reasons = reasons,
                        validatorReviewRecommended = unavailableSites.isNotEmpty() || compiledSites.budgetExceeded,
                        compiledReferenceSites = compiledSites,
                    ),
                ),
            )
        }
    }

    private fun exactLayout(session: RomAnalysisSession, lowerBound: Int): ExactLayout {
        val snapshot = session.exactProfileSnapshot?.tables?.typeChart ?: return ExactLayout.Absent
        if (snapshot.elementSize == null && snapshot.recordSize == LEGACY_RECORD_SIZE) {
            return ExactLayout.Supported(
                TypeChartTableLayout(snapshot.offset.toLong(), TypeChartAbi.LEGACY_TRIPLETS),
            )
        }
        val abi = when (snapshot.elementSize) {
            4 -> TypeChartAbi.DENSE_U32_Q412
            2 -> TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE
            else -> return ExactLayout.Unsupported(
                "matching exact type-chart layout uses unsupported element size ${snapshot.elementSize}",
            )
        }
        val dense = exactDenseLayout(snapshot, lowerBound, abi)
            ?: return ExactLayout.Unsupported(
                "matching exact dense type-chart layout has inconsistent count, row width, or active domain",
            )
        return ExactLayout.Supported(dense)
    }

    private fun exactDenseLayout(
        snapshot: ExactTableLayoutSnapshot,
        lowerBound: Int,
        abi: TypeChartAbi,
    ): TypeChartTableLayout? {
        val cells = snapshot.count.takeIf { it > 0 } ?: return null
        val root = sqrt(cells.toDouble()).toInt()
        if (root * root != cells || root !in lowerBound..MAX_TYPES) return null
        val elementSize = requireNotNull(snapshot.elementSize)
        val expectedRowSize = try {
            Math.multiplyExact(root, elementSize)
        } catch (_: ArithmeticException) {
            return null
        }
        if (snapshot.recordSize != expectedRowSize) return null
        return TypeChartTableLayout(snapshot.offset.toLong(), abi, root)
    }

    private fun referencedSpanEvidence(
        session: RomAnalysisSession,
        root: RootProbe,
        end: Long?,
    ): List<GbaTargetReferenceEvidence>? {
        if (end == null || end !in 0..Int.MAX_VALUE.toLong()) return null
        val index = requireNotNull(session.gbaReferenceIndex)
        val startEvidence = index.target(root.offset.toInt()) ?: return null
        val endEvidence = index.target(end.toInt()) ?: return null
        return listOf(startEvidence, endEvidence)
    }

    private fun denseEnd(layout: TypeChartTableLayout): Long {
        val dimension = requireNotNull(layout.typeCount).toLong()
        val cells = Math.multiplyExact(dimension, dimension)
        val bytesPerCell = when (layout.abi) {
            TypeChartAbi.DENSE_U32_Q412,
            TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE,
            -> 4L
            TypeChartAbi.LEGACY_TRIPLETS -> error("legacy type chart has no fixed dense end")
        }
        return Math.addExact(layout.offset, Math.multiplyExact(cells, bytesPerCell))
    }

    private fun prunePairInteriorsNonTransitively(values: List<AssessedProbe>): List<AssessedProbe> {
        val groups = values
            .groupBy { it.candidate.layout.table.offset }
            .toSortedMap()
        val prunedStarts = mutableSetOf<Long>()
        val retainedPairSpans = mutableListOf<LongRange>()
        groups.forEach { (start, candidates) ->
            if (retainedPairSpans.any { span -> start > span.first && start < span.last }) {
                prunedStarts += start
            } else {
                candidates
                    .map(AssessedProbe::candidate)
                    .map { it.layout.table }
                    .filter { it.abi == TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE }
                    .map { requireNotNull(it.typeCount) }
                    .distinct()
                    .forEach { typeCount ->
                        val dimension = typeCount.toLong()
                        val pairBytes = Math.multiplyExact(Math.multiplyExact(dimension, dimension), 4L)
                        val endExclusive = Math.addExact(start, pairBytes)
                        retainedPairSpans += start..endExclusive
                    }
            }
        }
        return values.filterNot { probe ->
            probe.candidate.layout.table.offset in prunedStarts
        }
    }

    private fun normalizeRoots(
        direct: Collection<Number>,
        published: Collection<Number>,
        compiled: Collection<Number>,
        inherited: Collection<Number>,
    ): RootNormalization {
        val values = listOf(
            CandidateSource.DIRECT_COMPILED_CONSUMER to direct,
            CandidateSource.PUBLISHED_HEADER to published,
            CandidateSource.COMPILED_REFERENCE to compiled,
            CandidateSource.INHERITED_FAMILY_LAYOUT to inherited,
        )
        val roots = mutableListOf<RootProbe>()
        values.forEach { (source, sourceRoots) ->
            sourceRoots.forEach { raw ->
                val offset = when (raw) {
                    is Byte -> raw.toLong()
                    is Short -> raw.toLong()
                    is Int -> raw.toLong()
                    is Long -> raw
                    else -> return RootNormalization.Invalid("type-chart root must be an integral offset")
                }
                if (offset !in 0..Int.MAX_VALUE.toLong()) {
                    return RootNormalization.Invalid("type-chart root $offset is not an indexable ROM offset")
                }
                roots += RootProbe(offset, source)
            }
        }
        return RootNormalization.Valid(
            roots.distinct().sortedWith(compareBy<RootProbe>({ it.offset }, { it.source.ordinal })),
        )
    }

    private fun sourceRequiresCompiledEvidence(source: CandidateSource): Boolean = when (source) {
        CandidateSource.PUBLISHED_HEADER,
        CandidateSource.COMPILED_REFERENCE,
        -> true
        CandidateSource.EXACT_PROFILE,
        CandidateSource.DIRECT_COMPILED_CONSUMER,
        CandidateSource.INHERITED_FAMILY_LAYOUT,
        CandidateSource.STRUCTURAL_ANCHOR,
        -> false
    }

    private fun referenceEvidence(value: GbaTargetReferenceEvidence?): List<GbaTargetReferenceEvidence> =
        listOfNotNull(value)

    private fun referenceSiteOverflow(
        references: List<GbaTargetReferenceEvidence>,
    ): Assessment.ReferenceSitesBudget? {
        val overflowed = references.filter(GbaTargetReferenceEvidence::siteBudgetExceeded)
        if (overflowed.isEmpty()) return null
        return Assessment.ReferenceSitesBudget(
            observed = overflowed.maxOf { it.observedSites.toLong() },
            limit = overflowed.minOf { it.limitSites.toLong() },
            reason = overflowed.mapNotNull { it.overflowReason }.distinct().sorted().joinToString("; "),
        )
    }

    private fun compiledSites(
        session: RomAnalysisSession,
        references: List<GbaTargetReferenceEvidence>,
    ): CompiledReferenceSites {
        val overflowed = references.filter(GbaTargetReferenceEvidence::siteBudgetExceeded)
        if (overflowed.isNotEmpty()) {
            return CompiledReferenceSites.overflowed(
                observedSites = overflowed.maxOf { it.observedSites.toLong() },
                limitSites = session.limits.maxCompiledReferenceSitesPerCandidate,
                reason = overflowed.mapNotNull { it.overflowReason }.distinct().sorted().joinToString("; "),
            )
        }
        return CompiledReferenceSites.of(
            references.asSequence().flatMap { it.instructionSites.asSequence() },
            session.limits.maxCompiledReferenceSitesPerCandidate,
        )
    }

    private fun unavailable(observed: Int, reason: String): DatasetResolution.Unavailable<ResolvedTypeChartLayout> =
        DatasetResolution.Unavailable(DatasetKind.TYPE_CHART, observed, reason)

    private fun budgetExceeded(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedTypeChartLayout> = DatasetResolution.BudgetExceeded(
        DatasetKind.TYPE_CHART,
        kind,
        observed,
        limit,
        complete,
        reason,
    )

    private fun probeWorkExceeded(work: ProbeWorkBudget) = budgetExceeded(
        BudgetKind.PROBE_WORK,
        work.overflowWitness,
        work.limit,
        complete = false,
        reason = "type-chart probe-work budget exceeded " +
            "(${work.overflowWitness} > ${work.limit})",
    )

    private fun candidateBudgetExceeded(
        session: RomAnalysisSession,
        observed: Long,
    ) = budgetExceeded(
        BudgetKind.CANDIDATES,
        observed,
        session.limits.maxCandidatesPerDataset.toLong(),
        complete = false,
        reason = "type-chart candidate budget exceeded " +
            "($observed > ${session.limits.maxCandidatesPerDataset})",
    )

    private data class RootProbe(val offset: Long, val source: CandidateSource)
    private data class AssessedProbe(val candidate: DatasetCandidate<ResolvedTypeChartLayout>)

    private sealed interface RootNormalization {
        data class Valid(val roots: List<RootProbe>) : RootNormalization
        data class Invalid(val reason: String) : RootNormalization
    }

    private sealed interface ExactLayout {
        data object Absent : ExactLayout
        data class Supported(val layout: TypeChartTableLayout) : ExactLayout
        data class Unsupported(val reason: String) : ExactLayout
    }

    private class ProbeWorkBudget(val limit: Long) {
        private var consumed = 0L
        val overflowWitness: Long get() = consumed + 1L

        fun tryConsume(): Boolean {
            if (consumed == limit) return false
            consumed++
            return true
        }
    }

    private sealed interface Assessment {
        data class Candidate(val value: DatasetCandidate<ResolvedTypeChartLayout>) : Assessment
        data class ExtentBudget(val observed: Long, val limit: Long, val reason: String) : Assessment {
            fun toResolution(): DatasetResolution.BudgetExceeded<ResolvedTypeChartLayout> = budgetExceeded(
                BudgetKind.EXTENT,
                observed,
                limit,
                complete = true,
                reason,
            )
        }
        data class ReferenceSitesBudget(
            val observed: Long,
            val limit: Long,
            val reason: String,
        ) : Assessment {
            fun toResolution(): DatasetResolution.BudgetExceeded<ResolvedTypeChartLayout> = budgetExceeded(
                BudgetKind.REFERENCE_SITES,
                observed,
                limit,
                complete = false,
                reason,
            )
        }
        data object Rejected : Assessment
    }

    private const val MAX_TYPES = 64
    private const val LEGACY_RECORD_SIZE = 3
}
