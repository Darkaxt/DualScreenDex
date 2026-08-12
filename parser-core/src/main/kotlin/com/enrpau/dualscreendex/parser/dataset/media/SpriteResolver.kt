package com.enrpau.dualscreendex.parser.dataset.media

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

/** Resolves only caller-proven roots; it never scans for sprite or palette tables. */
class SpriteResolver(
    private val codec: SpriteTableDecoder = SpriteCodec(),
) {
    fun resolve(
        session: RomAnalysisSession,
        semanticDomain: SpriteSemanticDomain,
        exactLayouts: Collection<SpriteTableLayout> = emptyList(),
        directCompiledConsumerLayouts: Collection<SpriteTableLayout> = emptyList(),
        publishedLayouts: Collection<SpriteTableLayout> = emptyList(),
        compiledReferenceLayouts: Collection<SpriteTableLayout> = emptyList(),
        inheritedLayouts: Collection<SpriteTableLayout> = emptyList(),
        structuralLayouts: Collection<SpriteTableLayout> = emptyList(),
        allowStructuralAnchors: Boolean = false,
    ): DatasetResolution<ResolvedSpriteLayout> {
        val roots = linkedSetOf<Long>()
        val candidates = mutableListOf<DatasetCandidate<ResolvedSpriteLayout>>()
        val rejections = linkedSetOf<String>()
        var observed = 0
        var work = 0L
        fun process(
            layouts: Collection<SpriteTableLayout>,
            source: CandidateSource,
            references: ((Long) -> GbaTargetReferenceEvidence?)? = null,
        ): DatasetResolution.BudgetExceeded<ResolvedSpriteLayout>? {
            for (layout in layouts) {
                observed++
                if (roots.add(layout.tableOffset) && roots.size > session.limits.maxProbeRootsPerDataset) {
                    return budget(
                        BudgetKind.PROBE_ROOTS,
                        roots.size.toLong(),
                        session.limits.maxProbeRootsPerDataset.toLong(),
                        false,
                        "sprite probe-root budget exceeded",
                    )
                }
                work++
                if (work > session.limits.maxProbeWorkPerDataset.toLong()) {
                    return budget(
                        BudgetKind.PROBE_WORK,
                        work,
                        session.limits.maxProbeWorkPerDataset.toLong(),
                        false,
                        "sprite layout-attempt budget exceeded",
                    )
                }
                if (layout.count != semanticDomain.tableRowCount) {
                    rejections += "sprite candidate cardinality ${layout.count} does not match " +
                        "semantic domain ${semanticDomain.tableRowCount}"
                    continue
                }
                when (val decoded = codec.decode(session, layout)) {
                    is SpriteTableOutcome.Rejected -> rejections += decoded.reason
                    is SpriteTableOutcome.BudgetExceeded -> return budget(
                        decoded.budgetKind.toResolutionBudget(),
                        decoded.observed,
                        decoded.limit,
                        true,
                        decoded.reason,
                    )
                    is SpriteTableOutcome.Decoded -> {
                        val resolved = ResolvedSpriteLayout(decoded.layout, decoded.rows, semanticDomain)
                        val structural = resolved.rows.count { it !is SpriteRowOutcome.Malformed }
                        if (structural == 0) {
                            rejections += "sprite candidate has no structurally classifiable rows"
                            continue
                        }
                        if (candidates.size == session.limits.maxCandidatesPerDataset) {
                            return budget(
                                BudgetKind.CANDIDATES,
                                candidates.size + 1L,
                                session.limits.maxCandidatesPerDataset.toLong(),
                                false,
                                "sprite candidate budget exceeded",
                            )
                        }
                        candidates += candidate(session, resolved, source, references?.invoke(layout.tableOffset))
                    }
                }
            }
            return null
        }

        fun selectCurrent(structuralAllowed: Boolean = false): DatasetResolution<ResolvedSpriteLayout> =
            CandidateSelector.select(
                session = session,
                kind = DatasetKind.SPRITES,
                candidates = candidates.asSequence(),
                structuralAnchorPolicy = if (structuralAllowed) {
                    StructuralAnchorPolicy.allow(DatasetKind.SPRITES)
                } else {
                    StructuralAnchorPolicy.denyAll()
                },
            )

        if (exactLayouts.isNotEmpty() && session.exactProfileIdentity != null) {
            process(exactLayouts, CandidateSource.EXACT_PROFILE)?.let { return it }
            if (candidates.isNotEmpty()) return selectCurrent()
            return DatasetResolution.Unavailable(
                DatasetKind.SPRITES,
                observed,
                rejections.ifEmpty { listOf("matching exact sprite layout did not pass its explicit codec") },
            )
        }

        process(directCompiledConsumerLayouts, CandidateSource.DIRECT_COMPILED_CONSUMER)?.let { return it }
        if (candidates.isNotEmpty()) return selectCurrent()

        val referenceIndex = if (publishedLayouts.isNotEmpty() || compiledReferenceLayouts.isNotEmpty()) {
            session.gbaReferenceIndex
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
        val referenceFor: (Long) -> GbaTargetReferenceEvidence? = { root ->
            root.toIndexedInt()?.let { referenceIndex?.target(it) }
        }
        process(publishedLayouts, CandidateSource.PUBLISHED_HEADER, referenceFor)?.let { return it }
        process(compiledReferenceLayouts, CandidateSource.COMPILED_REFERENCE, referenceFor)?.let { return it }
        process(inheritedLayouts, CandidateSource.INHERITED_FAMILY_LAYOUT)?.let { return it }
        process(structuralLayouts, CandidateSource.STRUCTURAL_ANCHOR)?.let { return it }

        if (observed == 0) {
            return DatasetResolution.Unavailable(DatasetKind.SPRITES, 0, "no sprite candidates were proposed")
        }
        if (candidates.isEmpty()) {
            return DatasetResolution.Unavailable(
                DatasetKind.SPRITES,
                observed,
                rejections.ifEmpty { listOf("no sprite candidate passed its explicit codec") },
            )
        }
        return selectCurrent(allowStructuralAnchors)
    }

    private fun candidate(
        session: RomAnalysisSession,
        layout: ResolvedSpriteLayout,
        source: CandidateSource,
        reference: GbaTargetReferenceEvidence?,
    ): DatasetCandidate<ResolvedSpriteLayout> {
        val active = layout.semanticDomain.activeRowIndices
        val activeDecoded = active.count { layout.rows[it] is SpriteRowOutcome.Decoded }
        val structural = layout.rows.count { it !is SpriteRowOutcome.Malformed }
        val malformed = layout.rows.count { it is SpriteRowOutcome.Malformed }
        val ambiguous = layout.rows.count { it is SpriteRowOutcome.AmbiguousSources }
        val activeMissing = active.size - activeDecoded
        val placeholder = layout.rows.count { it is SpriteRowOutcome.StandardPlaceholder }
        val reasons = buildList {
            if (activeMissing > 0) {
                add(CandidateReason(CandidateReasonKind.ANOMALY, "$activeMissing active sprite row(s) lack decoded ROM pixels"))
            }
            if (malformed > 0) {
                add(CandidateReason(CandidateReasonKind.ANOMALY, "$malformed sprite row(s) are malformed"))
            }
            if (ambiguous > 0) {
                add(CandidateReason(CandidateReasonKind.ANOMALY, "$ambiguous Gen 1 sprite row(s) have multiple valid banks"))
            }
            if (placeholder > 0) {
                add(CandidateReason(CandidateReasonKind.INFORMATION, "$placeholder explicitly identified standard placeholder row(s)"))
            }
        }
        return DatasetCandidate(
            identity = CandidateIdentity(layout.layoutIdentity.value),
            kind = DatasetKind.SPRITES,
            layout = layout,
            source = source,
            strength = CandidateStrength(
                semanticCoverage = EvidenceCoverage(activeDecoded, active.size),
                structuralCoverage = EvidenceCoverage(structural, layout.rows.size),
                compiledReferenceCount = reference?.count ?: 0,
                datasetQuality = activeDecoded,
            ),
            diagnosticOffset = layout.table.tableOffset.toIndexedInt(),
            diagnosticLabel = layout.layoutIdentity.value,
            eligibility = if (source == CandidateSource.EXACT_PROFILE) {
                session.exactProfileEligibility()
            } else {
                CandidateEligibility.validated(source)
            },
            provenance = provenance(session, reference, reasons),
        )
    }

    private fun provenance(
        session: RomAnalysisSession,
        reference: GbaTargetReferenceEvidence?,
        reasons: Collection<CandidateReason>,
    ): CandidateProvenance {
        val sites = when {
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
        val allReasons = buildList {
            addAll(reasons)
            reference?.siteEvidenceUnavailableReason?.let {
                add(CandidateReason(CandidateReasonKind.ANOMALY, it))
            }
            sites.overflowReason?.let { add(CandidateReason(CandidateReasonKind.ANOMALY, it)) }
        }
        return CandidateProvenance(
            reasons = allReasons,
            validatorReviewRecommended = allReasons.any { it.kind != CandidateReasonKind.INFORMATION },
            compiledReferenceSites = sites,
        )
    }

    private fun budget(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedSpriteLayout> = DatasetResolution.BudgetExceeded(
        DatasetKind.SPRITES,
        kind,
        observed,
        limit,
        complete,
        reason,
    )

    private fun SpriteBudgetKind.toResolutionBudget(): BudgetKind = when (this) {
        SpriteBudgetKind.DECODE_WORK -> BudgetKind.PROBE_WORK
        SpriteBudgetKind.TABLE_EXTENT,
        SpriteBudgetKind.TABLE_ROWS,
        SpriteBudgetKind.COMPRESSED_INPUT,
        SpriteBudgetKind.DECODE_OUTPUT,
        SpriteBudgetKind.RETAINED_OUTPUT,
        -> BudgetKind.EXTENT
    }

    private fun Long.toIndexedInt(): Int? = takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

}
