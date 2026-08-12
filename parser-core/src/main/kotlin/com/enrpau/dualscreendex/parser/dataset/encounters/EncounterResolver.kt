package com.enrpau.dualscreendex.parser.dataset.encounters

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
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
import com.enrpau.dualscreendex.parser.resolution.StructuralAnchorPolicy
import com.enrpau.dualscreendex.parser.resolution.exactProfileEligibility
import java.util.LinkedHashMap

/** Bounded Gen III encounter resolution. Gen I/II remain in their characterized legacy decoder. */
class EncounterResolver(
    private val codec: Gen3EncounterCodec = Gen3EncounterCodec(),
) {
    fun resolve(
        session: RomAnalysisSession,
        speciesCount: Int? = null,
        exactLayouts: Collection<Gen3EncounterTableLayout> = emptyList(),
        directCompiledConsumerLayouts: Collection<Gen3EncounterTableLayout> = emptyList(),
        publishedLayouts: Collection<Gen3EncounterTableLayout> = emptyList(),
        compiledReferenceLayouts: Collection<Gen3EncounterTableLayout> = emptyList(),
        compiledReferenceRoots: Collection<Number> = emptyList(),
        inheritedLayouts: Collection<Gen3EncounterTableLayout> = emptyList(),
        structuralLayouts: Collection<Gen3EncounterTableLayout> = emptyList(),
        allowStructuralAnchors: Boolean = false,
        decodeLimits: EncounterDecodeLimits = EncounterDecodeLimits(),
    ): DatasetResolution<ResolvedEncounterLayout> {
        val meter = EncounterDecodeMeter(session.limits.maxProbeWorkPerDataset.toLong(), decodeLimits)
        val candidates = mutableListOf<DatasetCandidate<ResolvedEncounterLayout>>()
        val candidateIdentities = linkedSetOf<CandidateIdentity>()
        val distinctRoots = linkedSetOf<Long>()
        val rejections = RejectionLedger()
        val negativeCache = NegativeProbeCache(decodeLimits.maxNegativeProbeCacheEntries)
        var observed = 0

        fun chargeSubmission(): DatasetResolution.BudgetExceeded<ResolvedEncounterLayout>? = try {
            meter.work()
            null
        } catch (exhausted: EncounterBudgetException) {
            exhausted.toResolution()
        }

        listOf(
            exactLayouts,
            directCompiledConsumerLayouts,
            publishedLayouts,
            compiledReferenceLayouts,
            inheritedLayouts,
            structuralLayouts,
        ).forEach { layouts ->
            layouts.forEach { _ -> chargeSubmission()?.let { return it } }
        }
        compiledReferenceRoots.forEach { _ -> chargeSubmission()?.let { return it } }

        if (compiledReferenceRoots.isNotEmpty() && (speciesCount == null || speciesCount <= 0)) {
            return unavailable(
                0,
                "referenced encounter-root discovery requires an explicit positive species count",
            )
        }

        fun registerRoot(offset: Long): DatasetResolution.BudgetExceeded<ResolvedEncounterLayout>? {
            if (offset < 0 || offset > Int.MAX_VALUE.toLong()) {
                rejections.record("encounter root $offset is not an indexable ROM offset")
                return null
            }
            if (distinctRoots.add(offset) && distinctRoots.size > session.limits.maxProbeRootsPerDataset) {
                return budget(
                    BudgetKind.PROBE_ROOTS,
                    distinctRoots.size.toLong(),
                    session.limits.maxProbeRootsPerDataset.toLong(),
                    false,
                    "encounter probe-root budget exceeded",
                )
            }
            return null
        }

        fun process(
            layouts: Iterable<Gen3EncounterTableLayout>,
            source: CandidateSource,
            referenceIndex: GbaReferenceIndex?,
            requireShellProof: Boolean = false,
        ): DatasetResolution.BudgetExceeded<ResolvedEncounterLayout>? {
            layouts.forEach { layout ->
                observed++
                registerRoot(layout.offset)?.let { return it }
                if (layout.offset !in 0..Int.MAX_VALUE.toLong()) return@forEach
                val reference = referenceIndex
                    ?.target(layout.offset.toInt())
                    ?.takeIf { evidence -> evidence.count > 0 }
                val requiresReference = source == CandidateSource.PUBLISHED_HEADER ||
                    source == CandidateSource.COMPILED_REFERENCE ||
                    (layout.abi == Gen3EncounterAbi.CLASSIC_24 &&
                        source != CandidateSource.DIRECT_COMPILED_CONSUMER)
                if (requiresReference && reference == null) {
                    rejections.record(
                        "${layout.abi.name} encounter root lacks required compiled-reference authority",
                    )
                    return@forEach
                }
                reference?.takeIf(GbaTargetReferenceEvidence::siteBudgetExceeded)?.let { overflow ->
                    return budget(
                        BudgetKind.REFERENCE_SITES,
                        overflow.observedSites.toLong(),
                        overflow.limitSites.toLong(),
                        false,
                        requireNotNull(overflow.overflowReason),
                    )
                }
                reference?.takeIf(GbaTargetReferenceEvidence::siteEvidenceAvailable)?.let { evidence ->
                    val observedSites = evidence.instructionSites.distinct().size
                    if (observedSites > session.limits.maxCompiledReferenceSitesPerCandidate) {
                        return budget(
                            BudgetKind.REFERENCE_SITES,
                            observedSites.toLong(),
                            session.limits.maxCompiledReferenceSitesPerCandidate.toLong(),
                            false,
                            "encounter compiled-reference site budget exceeded for this analysis session",
                        )
                    }
                }
                val cacheKey = layout.identity.value
                if (negativeCache.containsKey(cacheKey)) return@forEach
                try {
                    val classicEmptyFirst = codec.isClassicEmptyFirst(session, layout)
                    if (requireShellProof && layout.abi == Gen3EncounterAbi.STANDARD_20 &&
                        codec.isStructuralEmptyFirst(session, layout)
                    ) {
                        negativeCache[cacheKey] = Unit
                        rejections.record("Standard20 encounter root begins with an unproven empty header")
                        return@forEach
                    }
                    if (classicEmptyFirst) meter.emptyFirstShell()
                    if (classicEmptyFirst && !codec.provesSentinelShell(
                            session,
                            layout,
                            meter,
                            allowEmptyFirst = classicEmptyFirst,
                        )
                    ) {
                        negativeCache[cacheKey] = Unit
                        rejections.record("encounter root does not form a bounded sentinel header shell")
                        return@forEach
                    }
                } catch (exhausted: EncounterBudgetException) {
                    return exhausted.toResolution()
                }
                when (val decoded = codec.decodeWithMeter(session, layout, meter)) {
                    is EncounterTableOutcome.Rejected -> {
                        negativeCache[cacheKey] = Unit
                        rejections.record(decoded.reason)
                    }
                    is EncounterTableOutcome.BudgetExceeded -> return decoded.toResolution()
                    is EncounterTableOutcome.Decoded -> {
                        val resolved = ResolvedEncounterLayout(decoded.layout, decoded.rows)
                        val identity = CandidateIdentity(resolved.layoutIdentity.value)
                        if (!candidateIdentities.add(identity)) return@forEach
                        if (candidates.size == session.limits.maxCandidatesPerDataset) {
                            return budget(
                                BudgetKind.CANDIDATES,
                                candidates.size + 1L,
                                session.limits.maxCandidatesPerDataset.toLong(),
                                false,
                                "encounter candidate budget exceeded",
                            )
                        }
                        candidates += candidate(session, resolved, source, reference)
                    }
                }
            }
            return null
        }

        fun selectCurrent(structuralAllowed: Boolean = false): DatasetResolution<ResolvedEncounterLayout> {
            sameRootAbiAmbiguity(
                candidates.filter { candidate ->
                    structuralAllowed || candidate.source != CandidateSource.STRUCTURAL_ANCHOR
                },
            )?.let { return it }
            return CandidateSelector.select(
                session = session,
                kind = DatasetKind.AREA_ENCOUNTERS,
                candidates = candidates.asSequence(),
                structuralAnchorPolicy = if (structuralAllowed) {
                    StructuralAnchorPolicy.allow(DatasetKind.AREA_ENCOUNTERS)
                } else {
                    StructuralAnchorPolicy.denyAll()
                },
            )
        }

        if (exactLayouts.isNotEmpty()) {
            if (session.exactProfileIdentity == null) {
                return unavailable(0, "encounter exact layouts require a matching exact-profile identity")
            }
            val exactNeedsReferences = exactLayouts.any { it.abi == Gen3EncounterAbi.CLASSIC_24 }
            val exactReferenceIndex = if (exactNeedsReferences) {
                session.gbaReferenceIndex
                    ?: return unavailable(observed, "Classic24 exact layouts require a GBA analysis session")
            } else {
                null
            }
            exactReferenceIndex?.overflowReason?.let { reason ->
                return budget(
                    BudgetKind.REFERENCE_TARGETS,
                    exactReferenceIndex.observedTargets.toLong(),
                    exactReferenceIndex.limitTargets.toLong(),
                    false,
                    reason,
                )
            }
            process(exactLayouts, CandidateSource.EXACT_PROFILE, exactReferenceIndex)?.let { return it }
            if (candidates.isNotEmpty()) return selectCurrent()
            return unavailable(
                observed,
                rejections.reasons("matching exact encounter layouts did not pass their explicit codecs"),
            )
        }

        process(directCompiledConsumerLayouts, CandidateSource.DIRECT_COMPILED_CONSUMER, null)?.let { return it }
        if (candidates.isNotEmpty()) return selectCurrent()

        val requiresIndex = publishedLayouts.isNotEmpty() || compiledReferenceLayouts.isNotEmpty() ||
            compiledReferenceRoots.isNotEmpty() ||
            inheritedLayouts.any { it.abi == Gen3EncounterAbi.CLASSIC_24 } ||
            structuralLayouts.any { it.abi == Gen3EncounterAbi.CLASSIC_24 }
        val referenceIndex = if (requiresIndex) {
            session.gbaReferenceIndex
                ?: return unavailable(observed, "Gen III encounter references require a GBA analysis session")
        } else {
            null
        }
        referenceIndex?.overflowReason?.let { reason ->
            return budget(
                BudgetKind.REFERENCE_TARGETS,
                referenceIndex.observedTargets.toLong(),
                referenceIndex.limitTargets.toLong(),
                false,
                reason,
            )
        }

        process(publishedLayouts, CandidateSource.PUBLISHED_HEADER, referenceIndex)?.let { return it }
        process(compiledReferenceLayouts, CandidateSource.COMPILED_REFERENCE, referenceIndex)?.let { return it }

        val rootLayouts = sequence {
            compiledReferenceRoots.forEach { raw ->
                val root = integralOffset(raw)
                if (root == null) {
                    rejections.record("encounter root must be an integral indexable offset")
                } else {
                    // Classic first makes the explicit empty-shell cap independently observable.
                    yield(Gen3EncounterTableLayout(root, Gen3EncounterAbi.CLASSIC_24, requireNotNull(speciesCount)))
                    yield(Gen3EncounterTableLayout(root, Gen3EncounterAbi.STANDARD_20, requireNotNull(speciesCount)))
                }
            }
        }.asIterable()
        process(
            rootLayouts,
            CandidateSource.COMPILED_REFERENCE,
            referenceIndex,
            requireShellProof = true,
        )?.let { return it }

        process(inheritedLayouts, CandidateSource.INHERITED_FAMILY_LAYOUT, referenceIndex)?.let { return it }
        process(structuralLayouts, CandidateSource.STRUCTURAL_ANCHOR, referenceIndex)?.let { return it }

        if (candidates.isEmpty()) {
            return unavailable(
                observed,
                rejections.reasons("no encounter candidate passed its explicit codec"),
            )
        }
        return selectCurrent(allowStructuralAnchors)
    }

    private fun candidate(
        session: RomAnalysisSession,
        layout: ResolvedEncounterLayout,
        source: CandidateSource,
        reference: GbaTargetReferenceEvidence?,
    ): DatasetCandidate<ResolvedEncounterLayout> {
        val structural = layout.rows.count { it !is EncounterHeaderOutcome.Malformed }
        val decodedMethods = layout.rows
            .filterIsInstance<EncounterHeaderOutcome.Decoded>()
            .sumOf { it.methods.size }
        val malformed = layout.rows.filterIsInstance<EncounterHeaderOutcome.Malformed>()
        val sites = compiledSites(session, reference)
        val reasons = buildList {
            if (malformed.isNotEmpty()) {
                add(
                    CandidateReason(
                        CandidateReasonKind.ANOMALY,
                        "${malformed.size} encounter header row(s) are malformed",
                    ),
                )
            }
            reference?.siteEvidenceUnavailableReason?.let { reason ->
                add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
            }
            if (layout.table.abi == Gen3EncounterAbi.CLASSIC_24 &&
                layout.rows.first() is EncounterHeaderOutcome.StructuralEmpty
            ) {
                add(
                    CandidateReason(
                        CandidateReasonKind.INFORMATION,
                        "bounded empty-first Classic24 sentinel shell was proven",
                    ),
                )
            }
        }
        return DatasetCandidate(
            identity = CandidateIdentity(layout.layoutIdentity.value),
            kind = DatasetKind.AREA_ENCOUNTERS,
            layout = layout,
            source = source,
            strength = CandidateStrength(
                semanticCoverage = EvidenceCoverage(structural, layout.rows.size),
                structuralCoverage = EvidenceCoverage(structural, layout.rows.size),
                compiledReferenceCount = reference?.count ?: 0,
                datasetQuality = decodedMethods,
            ),
            diagnosticOffset = layout.table.offset.toInt(),
            diagnosticLabel = layout.layoutIdentity.value,
            eligibility = if (source == CandidateSource.EXACT_PROFILE) {
                session.exactProfileEligibility()
            } else {
                CandidateEligibility.validated(source)
            },
            provenance = CandidateProvenance(
                reasons = reasons,
                validatorReviewRecommended = malformed.isNotEmpty() ||
                    reference?.siteEvidenceUnavailableReason != null,
                compiledReferenceSites = sites,
            ),
        )
    }

    private fun sameRootAbiAmbiguity(
        candidates: List<DatasetCandidate<ResolvedEncounterLayout>>,
    ): DatasetResolution.Ambiguous<ResolvedEncounterLayout>? {
        val ambiguousRoots = candidates
            .groupBy { it.layout.table.offset }
            .filterValues { sameRoot -> sameRoot.map { it.layout.table.abi }.distinct().size > 1 }
            .keys
        if (ambiguousRoots.isEmpty()) return null
        val conflicts = candidates
            .filter { it.layout.table.offset in ambiguousRoots }
            .sortedWith(compareBy({ it.diagnosticOffset }, { it.layout.table.abi.ordinal }, { it.identity.value }))
        return DatasetResolution.Ambiguous(DatasetKind.AREA_ENCOUNTERS, conflicts)
    }

    private fun compiledSites(
        session: RomAnalysisSession,
        reference: GbaTargetReferenceEvidence?,
    ): CompiledReferenceSites = when {
        reference == null -> CompiledReferenceSites.empty()
        reference.siteBudgetExceeded -> CompiledReferenceSites.overflowed(
            reference.observedSites.toLong(),
            reference.limitSites,
            requireNotNull(reference.overflowReason),
        )
        reference.siteEvidenceAvailable -> CompiledReferenceSites.of(
            reference.instructionSites,
            session.limits.maxCompiledReferenceSitesPerCandidate,
        )
        else -> CompiledReferenceSites.empty()
    }

    private fun EncounterTableOutcome.BudgetExceeded.toResolution():
        DatasetResolution.BudgetExceeded<ResolvedEncounterLayout> = budget(
        budgetKind = when (budgetKind) {
            EncounterBudgetKind.PROBE_WORK,
            EncounterBudgetKind.EMPTY_FIRST_SHELLS,
            -> BudgetKind.PROBE_WORK
            EncounterBudgetKind.EXTENT,
            EncounterBudgetKind.RETAINED_OUTPUT,
            -> BudgetKind.EXTENT
        },
        observed = observed,
        limit = limit,
        complete = observationComplete,
        reason = reason,
    )

    private fun EncounterBudgetException.toResolution():
        DatasetResolution.BudgetExceeded<ResolvedEncounterLayout> = budget(
        budgetKind = when (kind) {
            EncounterBudgetKind.PROBE_WORK,
            EncounterBudgetKind.EMPTY_FIRST_SHELLS,
            -> BudgetKind.PROBE_WORK
            EncounterBudgetKind.EXTENT,
            EncounterBudgetKind.RETAINED_OUTPUT,
            -> BudgetKind.EXTENT
        },
        observed = observed,
        limit = limit,
        complete = observationComplete,
        reason = requireNotNull(message),
    )

    private fun integralOffset(value: Number): Long? = when (value) {
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }?.takeIf { it in 0..Int.MAX_VALUE.toLong() }

    private fun unavailable(
        observed: Int,
        reasons: Collection<String>,
    ): DatasetResolution.Unavailable<ResolvedEncounterLayout> = DatasetResolution.Unavailable(
        DatasetKind.AREA_ENCOUNTERS,
        observed,
        reasons,
    )

    private fun unavailable(
        observed: Int,
        reason: String,
    ): DatasetResolution.Unavailable<ResolvedEncounterLayout> = unavailable(observed, listOf(reason))

    private fun budget(
        budgetKind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedEncounterLayout> = DatasetResolution.BudgetExceeded(
        DatasetKind.AREA_ENCOUNTERS,
        budgetKind,
        observed,
        limit,
        complete,
        reason,
    )

    private class NegativeProbeCache(private val maximumEntries: Int) :
        LinkedHashMap<String, Unit>(maximumEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > maximumEntries
    }

    private class RejectionLedger(
        private val maximumReasons: Int = MAX_REJECTION_REASONS,
    ) {
        private val retained = linkedSetOf<String>()
        private var omitted = 0L

        init {
            require(maximumReasons > 0)
        }

        fun record(reason: String) {
            require(reason.isNotBlank())
            if (reason in retained) return
            if (retained.size < maximumReasons) {
                retained += reason
            } else {
                omitted = Math.addExact(omitted, 1L)
            }
        }

        fun reasons(fallback: String): List<String> = buildList {
            if (retained.isEmpty()) add(fallback) else addAll(retained)
            if (omitted > 0) add("$omitted additional rejection reason(s) omitted by diagnostic budget")
        }
    }

    private companion object {
        const val MAX_REJECTION_REASONS = 64
    }
}
