package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleMechanicsAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.RetailBattleMechanicsResolution
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.RetailBattleMechanicsResolver
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
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

class AbilityNameResolver(
    private val codec: AbilityNameTableDecoder = AbilityNameCodec(),
) {
    fun resolve(
        session: RomAnalysisSession,
        semanticDomain: AbilitySemanticDomain,
        selectedLayout: AbilityNameTableLayout? = null,
        directCompiledConsumerLayouts: Collection<AbilityNameTableLayout> = emptyList(),
        publishedLayouts: Collection<AbilityNameTableLayout> = emptyList(),
        compiledLayouts: Collection<AbilityNameTableLayout> = emptyList(),
        inheritedLayouts: Collection<AbilityNameTableLayout> = emptyList(),
    ): DatasetResolution<ResolvedAbilityNameLayout> {
        if (selectedLayout != null) {
            return resolveSelected(session, semanticDomain, selectedLayout)
        }
        if (session.exactProfileSnapshot?.tables?.abilities != null) {
            val exact = when (val result = exactLayout(session)) {
                is ExactLayout.Supported -> result.layout
                is ExactLayout.Unsupported -> return unavailable(
                    1,
                    "matching exact profile ability layout uses an unsupported or inconsistent ABI: " +
                        result.reason,
                )
            }
            return when (val decoded = codec.decode(session, exact, semanticDomain)) {
                is AbilityNameTableOutcome.Decoded -> CandidateSelector.select(
                    session,
                    DatasetKind.ABILITIES,
                    sequenceOf(
                        candidate(
                            session,
                            semanticDomain,
                            decoded.resolved,
                            CandidateSource.EXACT_PROFILE,
                            null,
                        ),
                    ),
                )
                is AbilityNameTableOutcome.ExtentBudgetExceeded -> extentBudget(decoded)
                is AbilityNameTableOutcome.Rejected -> unavailable(1, decoded.reason)
            }
        }

        val roots = linkedSetOf<Long>()
        var work = 0L
        var observed = 0
        val directCandidates = mutableListOf<DatasetCandidate<ResolvedAbilityNameLayout>>()
        for (layout in directCompiledConsumerLayouts) {
            val proposal = NameProposal(layout, CandidateSource.DIRECT_COMPILED_CONSUMER)
            observed++
            if (roots.add(proposal.layout.offset) && roots.size > session.limits.maxProbeRootsPerDataset) {
                return budget(
                    BudgetKind.PROBE_ROOTS,
                    roots.size.toLong(),
                    session.limits.maxProbeRootsPerDataset.toLong(),
                    false,
                    "ability-name probe-root budget exceeded",
                )
            }
            work++
            if (work > session.limits.maxProbeWorkPerDataset.toLong()) {
                return budget(
                    BudgetKind.PROBE_WORK,
                    work,
                    session.limits.maxProbeWorkPerDataset.toLong(),
                    false,
                    "ability-name probe-work budget exceeded",
                )
            }
            when (val decoded = codec.decode(session, proposal.layout, semanticDomain)) {
                is AbilityNameTableOutcome.Decoded -> {
                    if (directCandidates.size == session.limits.maxCandidatesPerDataset) {
                        return budget(
                            BudgetKind.CANDIDATES,
                            directCandidates.size + 1L,
                            session.limits.maxCandidatesPerDataset.toLong(),
                            false,
                            "ability-name candidate budget exceeded",
                        )
                    }
                    directCandidates += candidate(
                        session,
                        semanticDomain,
                        decoded.resolved,
                        proposal.source,
                        reference = null,
                    )
                }
                is AbilityNameTableOutcome.ExtentBudgetExceeded -> return extentBudget(decoded)
                is AbilityNameTableOutcome.Rejected -> Unit
            }
        }
        if (directCandidates.isNotEmpty()) {
            return CandidateSelector.select(session, DatasetKind.ABILITIES, directCandidates.asSequence())
        }

        val proposals = sequence {
            publishedLayouts.forEach { yield(NameProposal(it, CandidateSource.PUBLISHED_HEADER)) }
            compiledLayouts.forEach { yield(NameProposal(it, CandidateSource.COMPILED_REFERENCE)) }
            inheritedLayouts.forEach { yield(NameProposal(it, CandidateSource.INHERITED_FAMILY_LAYOUT)) }
        }
        val needsReferences = publishedLayouts.isNotEmpty() || compiledLayouts.isNotEmpty()
        val index = if (needsReferences) {
            session.gbaReferenceIndex ?: return unavailable(observed, "ability resolution requires a GBA analysis session")
        } else {
            null
        }
        index?.overflowReason?.let { reason ->
            return budget(
                BudgetKind.REFERENCE_TARGETS,
                index.observedTargets.toLong(),
                index.limitTargets.toLong(),
                false,
                reason,
            )
        }

        val candidates = mutableListOf<DatasetCandidate<ResolvedAbilityNameLayout>>()
        for (proposal in proposals) {
            observed++
            if (roots.add(proposal.layout.offset) && roots.size > session.limits.maxProbeRootsPerDataset) {
                return budget(
                    BudgetKind.PROBE_ROOTS,
                    roots.size.toLong(),
                    session.limits.maxProbeRootsPerDataset.toLong(),
                    false,
                    "ability-name probe-root budget exceeded",
                )
            }
            work++
            if (work > session.limits.maxProbeWorkPerDataset.toLong()) {
                return budget(
                    BudgetKind.PROBE_WORK,
                    work,
                    session.limits.maxProbeWorkPerDataset.toLong(),
                    false,
                    "ability-name probe-work budget exceeded",
                )
            }
            when (val decoded = codec.decode(session, proposal.layout, semanticDomain)) {
                is AbilityNameTableOutcome.Decoded -> {
                    if (candidates.size == session.limits.maxCandidatesPerDataset) {
                        return budget(
                            BudgetKind.CANDIDATES,
                            candidates.size + 1L,
                            session.limits.maxCandidatesPerDataset.toLong(),
                            false,
                            "ability-name candidate budget exceeded",
                        )
                    }
                    val reference = proposal.layout.offset.toSafeInt()?.let { index?.target(it) }
                    candidates += candidate(session, semanticDomain, decoded.resolved, proposal.source, reference)
                }
                is AbilityNameTableOutcome.ExtentBudgetExceeded -> return extentBudget(decoded)
                is AbilityNameTableOutcome.Rejected -> Unit
            }
        }
        if (observed == 0) return unavailable(0, "no ability-name candidates were proposed")
        return CandidateSelector.select(session, DatasetKind.ABILITIES, candidates.asSequence())
    }

    private fun resolveSelected(
        session: RomAnalysisSession,
        semanticDomain: AbilitySemanticDomain,
        selectedLayout: AbilityNameTableLayout,
    ): DatasetResolution<ResolvedAbilityNameLayout> {
        val selectedDomain = AbilitySemanticDomain(
            semanticDomain.activeAbilityIds.filterTo(linkedSetOf()) { it < selectedLayout.count },
        )
        val selected = when (val decoded = codec.decode(session, selectedLayout, selectedDomain)) {
            is AbilityNameTableOutcome.Decoded -> decoded.resolved
            is AbilityNameTableOutcome.ExtentBudgetExceeded -> return extentBudget(decoded)
            is AbilityNameTableOutcome.Rejected -> return unavailable(
                1,
                "selected ability-name layout failed its typed codec: ${decoded.reason}",
            )
        }
        val requiredCount = maxOf(
            selectedLayout.count,
            semanticDomain.maximumDirectAbilityId.toLong() + 1L,
        )
        if (requiredCount == selectedLayout.count) {
            return selectSelected(session, semanticDomain, selected)
        }
        val extendedLayout = AbilityNameTableLayout(
            offset = selectedLayout.offset,
            count = requiredCount,
            nameWidth = selectedLayout.nameWidth,
            stride = selectedLayout.stride,
            nameOffset = selectedLayout.nameOffset,
        )
        val extended = when (val decoded = codec.decode(session, extendedLayout, semanticDomain)) {
            is AbilityNameTableOutcome.Decoded -> decoded.resolved.takeIf { resolved ->
                resolved.rows.drop(selectedLayout.count.toInt()).all { it is AbilityNameRowOutcome.Decoded }
            }
            is AbilityNameTableOutcome.ExtentBudgetExceeded,
            is AbilityNameTableOutcome.Rejected,
            -> null
        }
        return selectSelected(session, semanticDomain, extended ?: selected)
    }

    private fun selectSelected(
        session: RomAnalysisSession,
        semanticDomain: AbilitySemanticDomain,
        layout: ResolvedAbilityNameLayout,
    ): DatasetResolution<ResolvedAbilityNameLayout> {
        val source = if (matchesExactProfileLayout(session, layout.table)) {
            CandidateSource.EXACT_PROFILE
        } else {
            CandidateSource.INHERITED_FAMILY_LAYOUT
        }
        return CandidateSelector.select(
            session,
            DatasetKind.ABILITIES,
            sequenceOf(candidate(session, semanticDomain, layout, source, reference = null)),
        )
    }

    private fun matchesExactProfileLayout(
        session: RomAnalysisSession,
        layout: AbilityNameTableLayout,
    ): Boolean = when (val exact = exactLayoutOrNull(session)) {
        null -> false
        else -> exact == layout
    }

    private fun exactLayoutOrNull(session: RomAnalysisSession): AbilityNameTableLayout? =
        if (session.exactProfileSnapshot?.tables?.abilities == null) {
            null
        } else {
            (exactLayout(session) as? ExactLayout.Supported)?.layout
        }

    private fun exactLayout(session: RomAnalysisSession): ExactLayout {
        val exact = requireNotNull(session.exactProfileSnapshot?.tables?.abilities)
        val stride = exact.stride ?: exact.recordSize
        val unsupported = buildList {
            if (exact.offset < 0) add("negative offset")
            if (exact.count <= 1) add("cardinality must include the none slot and at least one ability")
            if (exact.recordSize !in SUPPORTED_EXACT_NAME_WIDTHS) {
                add("fixed name width ${exact.recordSize} is outside $SUPPORTED_EXACT_NAME_WIDTHS")
            }
            if (stride <= 0 || stride < exact.recordSize) {
                add("stride $stride does not contain the ${exact.recordSize}-byte name field")
            }
            if (exact.variableLength) add("variable-length storage")
            if (exact.bank != null) add("banked storage")
            if (exact.banks.isNotEmpty()) add("multi-bank storage")
            if (exact.elementSize != null) add("element-size metadata")
            if (exact.bankAdjustment != 0) add("bank-adjusted storage")
            if (exact.bankRemap.isNotEmpty()) add("bank-remapped storage")
            if (exact.valuesArePointers) add("pointer-valued names")
            if (exact.format != TableRecordFormat.STANDARD) add("record format ${exact.format}")

            when {
                exact.pointerOffsets.size > 1 -> add("multiple embedded pointer fields")
                exact.pointerOffsets.size == 1 -> {
                    val pointerOffset = exact.pointerOffsets.single()
                    if (pointerOffset < exact.recordSize || pointerOffset.toLong() + 4L > stride.toLong()) {
                        add("embedded pointer offset $pointerOffset overlaps the name or exceeds stride $stride")
                    }
                }
            }
        }
        if (unsupported.isNotEmpty()) return ExactLayout.Unsupported(unsupported.joinToString("; "))

        return runCatching {
            AbilityNameTableLayout(
                offset = exact.offset.toLong(),
                count = exact.count.toLong(),
                nameWidth = exact.recordSize,
                stride = stride,
            )
        }.fold(
            onSuccess = ExactLayout::Supported,
            onFailure = { ExactLayout.Unsupported(it.message ?: "invalid fixed-width ability-name layout") },
        )
    }

    private fun candidate(
        session: RomAnalysisSession,
        semanticDomain: AbilitySemanticDomain,
        layout: ResolvedAbilityNameLayout,
        source: CandidateSource,
        reference: GbaTargetReferenceEvidence?,
    ): DatasetCandidate<ResolvedAbilityNameLayout> {
        val decodedIds = layout.baseRows.asSequence()
            .filterIsInstance<AbilityNameRowOutcome.Decoded>()
            .map { it.rowIndex }
            .toSet()
        val decodedBase = layout.baseRows.drop(1).count { it is AbilityNameRowOutcome.Decoded }
        val provenance = referenceProvenance(session, reference, buildList {
            if (layout.aliasLabels.isNotEmpty()) {
                add(
                    CandidateReason(
                        CandidateReasonKind.INFORMATION,
                        "${layout.aliasLabels.size} post-sentinel species-conditioned alias label(s) are " +
                            "reported separately from direct ability IDs",
                    ),
                )
            }
        }, reviewRecommended = layout.aliasLabels.isNotEmpty())
        return DatasetCandidate(
            identity = CandidateIdentity(layout.layoutIdentity.value),
            kind = DatasetKind.ABILITIES,
            layout = layout,
            source = source,
            strength = CandidateStrength(
                semanticCoverage = semanticDomain.activeAbilityIds.takeIf { it.isNotEmpty() }?.let { active ->
                    EvidenceCoverage(
                        covered = active.count(decodedIds::contains),
                        expected = active.size,
                    )
                },
                structuralCoverage = EvidenceCoverage(decodedBase, layout.baseAbilityCount),
                compiledReferenceCount = reference?.count ?: 0,
                datasetQuality = 0,
            ),
            diagnosticOffset = layout.table.offset.toSafeInt(),
            diagnosticLabel = layout.layoutIdentity.value,
            eligibility = if (source == CandidateSource.EXACT_PROFILE) {
                session.exactProfileEligibility()
            } else {
                CandidateEligibility.validated(source)
            },
            provenance = provenance,
        )
    }

    private fun extentBudget(
        outcome: AbilityNameTableOutcome.ExtentBudgetExceeded,
    ): DatasetResolution.BudgetExceeded<ResolvedAbilityNameLayout> = budget(
        BudgetKind.EXTENT,
        outcome.observedBytes,
        outcome.limitBytes,
        true,
        outcome.reason,
    )

    private fun budget(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedAbilityNameLayout> = DatasetResolution.BudgetExceeded(
        DatasetKind.ABILITIES,
        kind,
        observed,
        limit,
        complete,
        reason,
    )

    private fun unavailable(observed: Int, reason: String) =
        DatasetResolution.Unavailable<ResolvedAbilityNameLayout>(DatasetKind.ABILITIES, observed, reason)

    private data class NameProposal(val layout: AbilityNameTableLayout, val source: CandidateSource)

    private sealed interface ExactLayout {
        data class Supported(val layout: AbilityNameTableLayout) : ExactLayout
        data class Unsupported(val reason: String) : ExactLayout
    }

    private companion object {
        val SUPPORTED_EXACT_NAME_WIDTHS = 8..32
    }
}

class AbilityDescriptionResolver(
    private val codec: AbilityDescriptionTableDecoder = AbilityDescriptionCodec(),
) {
    fun resolve(
        session: RomAnalysisSession,
        abilityNames: ResolvedAbilityNameLayout,
        directCompiledConsumerLayouts: Collection<AbilityDescriptionTableLayout> = emptyList(),
        publishedLayouts: Collection<AbilityDescriptionTableLayout> = emptyList(),
        compiledLayouts: Collection<AbilityDescriptionTableLayout> = emptyList(),
        inheritedLayouts: Collection<AbilityDescriptionTableLayout> = emptyList(),
    ): DatasetResolution<ResolvedAbilityDescriptionLayout> {
        val rejectionReasons = linkedSetOf<String>()
        val roots = linkedSetOf<Long>()
        var work = 0L
        var observed = 0
        val directCandidates = mutableListOf<DatasetCandidate<ResolvedAbilityDescriptionLayout>>()
        for (layout in directCompiledConsumerLayouts) {
            val proposal = DescriptionProposal(layout, CandidateSource.DIRECT_COMPILED_CONSUMER)
            observed++
            if (roots.add(proposal.layout.offset) && roots.size > session.limits.maxProbeRootsPerDataset) {
                return descriptionBudget(
                    BudgetKind.PROBE_ROOTS,
                    roots.size.toLong(),
                    session.limits.maxProbeRootsPerDataset.toLong(),
                    false,
                    "ability-description probe-root budget exceeded",
                )
            }
            work++
            if (work > session.limits.maxProbeWorkPerDataset.toLong()) {
                return descriptionBudget(
                    BudgetKind.PROBE_WORK,
                    work,
                    session.limits.maxProbeWorkPerDataset.toLong(),
                    false,
                    "ability-description probe-work budget exceeded",
                )
            }
            if (proposal.layout.count != abilityNames.baseRowCount.toLong()) {
                rejectionReasons += "ability-description candidate cardinality ${proposal.layout.count} " +
                    "does not match resolved base ability row cardinality ${abilityNames.baseRowCount}"
                continue
            }
            when (val decoded = codec.decode(session, proposal.layout)) {
                is AbilityDescriptionTableOutcome.Decoded -> {
                    val assessed = assessDescriptions(decoded, proposal.source)
                    if (assessed == null) {
                        rejectionReasons += "ability-description candidate lacks sufficient decoded prose coverage"
                        continue
                    }
                    if (directCandidates.size == session.limits.maxCandidatesPerDataset) {
                        return descriptionBudget(
                            BudgetKind.CANDIDATES,
                            directCandidates.size + 1L,
                            session.limits.maxCandidatesPerDataset.toLong(),
                            false,
                            "ability-description candidate budget exceeded",
                        )
                    }
                    directCandidates += descriptionCandidate(
                        session,
                        assessed,
                        proposal.source,
                        reference = null,
                    )
                }
                is AbilityDescriptionTableOutcome.ExtentBudgetExceeded -> return descriptionBudget(
                    BudgetKind.EXTENT,
                    decoded.observedBytes,
                    decoded.limitBytes,
                    true,
                    decoded.reason,
                )
                is AbilityDescriptionTableOutcome.Rejected -> rejectionReasons += decoded.reason
            }
        }
        if (directCandidates.isNotEmpty()) {
            return CandidateSelector.select(
                session,
                DatasetKind.ABILITY_DESCRIPTIONS,
                directCandidates.asSequence(),
            )
        }

        val proposals = sequence {
            publishedLayouts.forEach { yield(DescriptionProposal(it, CandidateSource.PUBLISHED_HEADER)) }
            compiledLayouts.forEach { yield(DescriptionProposal(it, CandidateSource.COMPILED_REFERENCE)) }
            inheritedLayouts.forEach { yield(DescriptionProposal(it, CandidateSource.INHERITED_FAMILY_LAYOUT)) }
        }
        val needsReferences = publishedLayouts.isNotEmpty() || compiledLayouts.isNotEmpty()
        val index = if (needsReferences) {
            session.gbaReferenceIndex
                ?: return unavailableDescriptions(observed, "ability-description resolution requires a GBA session")
        } else {
            null
        }
        index?.overflowReason?.let { reason ->
            return descriptionBudget(
                BudgetKind.REFERENCE_TARGETS,
                index.observedTargets.toLong(),
                index.limitTargets.toLong(),
                false,
                reason,
            )
        }

        val candidates = mutableListOf<DatasetCandidate<ResolvedAbilityDescriptionLayout>>()
        for (proposal in proposals) {
            observed++
            if (roots.add(proposal.layout.offset) && roots.size > session.limits.maxProbeRootsPerDataset) {
                return descriptionBudget(
                    BudgetKind.PROBE_ROOTS,
                    roots.size.toLong(),
                    session.limits.maxProbeRootsPerDataset.toLong(),
                    false,
                    "ability-description probe-root budget exceeded",
                )
            }
            work++
            if (work > session.limits.maxProbeWorkPerDataset.toLong()) {
                return descriptionBudget(
                    BudgetKind.PROBE_WORK,
                    work,
                    session.limits.maxProbeWorkPerDataset.toLong(),
                    false,
                    "ability-description probe-work budget exceeded",
                )
            }
            if (proposal.layout.count != abilityNames.baseRowCount.toLong()) {
                rejectionReasons += "ability-description candidate cardinality ${proposal.layout.count} " +
                    "does not match resolved base ability row cardinality ${abilityNames.baseRowCount}"
                continue
            }
            when (val decoded = codec.decode(session, proposal.layout)) {
                is AbilityDescriptionTableOutcome.Decoded -> {
                    val assessed = assessDescriptions(decoded, proposal.source)
                    if (assessed == null) {
                        rejectionReasons += "ability-description candidate lacks sufficient decoded prose coverage"
                        continue
                    }
                    if (candidates.size == session.limits.maxCandidatesPerDataset) {
                        return descriptionBudget(
                            BudgetKind.CANDIDATES,
                            candidates.size + 1L,
                            session.limits.maxCandidatesPerDataset.toLong(),
                            false,
                            "ability-description candidate budget exceeded",
                        )
                    }
                    val reference = proposal.layout.offset.toSafeInt()?.let { index?.target(it) }
                    candidates += descriptionCandidate(session, assessed, proposal.source, reference)
                }
                is AbilityDescriptionTableOutcome.ExtentBudgetExceeded -> return descriptionBudget(
                    BudgetKind.EXTENT,
                    decoded.observedBytes,
                    decoded.limitBytes,
                    true,
                    decoded.reason,
                )
                is AbilityDescriptionTableOutcome.Rejected -> rejectionReasons += decoded.reason
            }
        }
        if (observed == 0) return unavailableDescriptions(0, "no ability-description candidates were proposed")
        if (candidates.isEmpty() && rejectionReasons.isNotEmpty()) {
            return DatasetResolution.Unavailable(
                DatasetKind.ABILITY_DESCRIPTIONS,
                observed,
                rejectionReasons,
            )
        }
        return CandidateSelector.select(session, DatasetKind.ABILITY_DESCRIPTIONS, candidates.asSequence())
    }

    private fun assessDescriptions(
        decoded: AbilityDescriptionTableOutcome.Decoded,
        source: CandidateSource,
    ): ResolvedAbilityDescriptionLayout? {
        val baseRows = decoded.rows.drop(1)
        val structurallyPresent = baseRows.count {
            it is AbilityDescriptionRowOutcome.Decoded || it is AbilityDescriptionRowOutcome.MissingProse
        }
        val decodedProse = baseRows.count { it is AbilityDescriptionRowOutcome.Decoded }
        val minimumPercent = if (source == CandidateSource.COMPILED_REFERENCE) 80L else 70L
        if (decoded.rows.firstOrNull() is AbilityDescriptionRowOutcome.Malformed || decodedProse < 2) return null
        if (decodedProse.toLong() * 100L < baseRows.size.toLong() * minimumPercent) return null
        return ResolvedAbilityDescriptionLayout(decoded.layout, decoded.rows)
    }

    private fun descriptionCandidate(
        session: RomAnalysisSession,
        layout: ResolvedAbilityDescriptionLayout,
        source: CandidateSource,
        reference: GbaTargetReferenceEvidence?,
    ): DatasetCandidate<ResolvedAbilityDescriptionLayout> {
        val baseRows = layout.rows.drop(1)
        val decoded = baseRows.count { it is AbilityDescriptionRowOutcome.Decoded }
        val structural = baseRows.count {
            it is AbilityDescriptionRowOutcome.Decoded || it is AbilityDescriptionRowOutcome.MissingProse
        }
        val missing = baseRows.count { it is AbilityDescriptionRowOutcome.MissingProse }
        val malformed = baseRows.size - structural
        val reasons = buildList {
            if (missing > 0) add(CandidateReason(CandidateReasonKind.INFORMATION, "$missing ability description(s) contain explicit missing-prose placeholders"))
            if (malformed > 0) add(CandidateReason(CandidateReasonKind.ANOMALY, "$malformed ability description row(s) are malformed"))
        }
        return DatasetCandidate(
            identity = CandidateIdentity(layout.layoutIdentity.value),
            kind = DatasetKind.ABILITY_DESCRIPTIONS,
            layout = layout,
            source = source,
            strength = CandidateStrength(
                semanticCoverage = EvidenceCoverage(decoded, baseRows.size),
                structuralCoverage = EvidenceCoverage(structural, baseRows.size),
                compiledReferenceCount = reference?.count ?: 0,
                datasetQuality = decoded,
            ),
            diagnosticOffset = layout.table.offset.toSafeInt(),
            diagnosticLabel = layout.layoutIdentity.value,
            eligibility = CandidateEligibility.validated(source),
            provenance = referenceProvenance(
                session,
                reference,
                reasons,
                reviewRecommended = missing > 0 || malformed > 0,
            ),
        )
    }

    private fun descriptionBudget(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedAbilityDescriptionLayout> = DatasetResolution.BudgetExceeded(
        DatasetKind.ABILITY_DESCRIPTIONS,
        kind,
        observed,
        limit,
        complete,
        reason,
    )

    private fun unavailableDescriptions(observed: Int, reason: String) =
        DatasetResolution.Unavailable<ResolvedAbilityDescriptionLayout>(
            DatasetKind.ABILITY_DESCRIPTIONS,
            observed,
            reason,
        )

    private data class DescriptionProposal(
        val layout: AbilityDescriptionTableLayout,
        val source: CandidateSource,
    )
}

object AbilityMechanicsResolver {
    fun resolve(
        session: RomAnalysisSession,
        moveDetails: ResolvedMoveDetailsLayout,
        abilityNames: ResolvedAbilityNameLayout,
        selectedAbi: BattleMechanicsAbi? = null,
    ): RetailBattleMechanicsResolution = RetailBattleMechanicsResolver.resolve(
        session = session,
        moveDetails = moveDetails,
        activeAbilityIds = abilityNames.decodedDirectAbilityIds(),
        selectedAbi = selectedAbi,
    )
}

private fun referenceProvenance(
    session: RomAnalysisSession,
    reference: GbaTargetReferenceEvidence?,
    reasons: Collection<CandidateReason>,
    reviewRecommended: Boolean,
): CandidateProvenance {
    val sites = when {
        reference == null -> CompiledReferenceSites.empty()
        reference.siteBudgetExceeded -> CompiledReferenceSites.overflowed(
            reference.observedSites.toLong(),
            reference.limitSites,
            requireNotNull(reference.overflowReason),
        )
        reference.siteEvidenceAvailable -> CompiledReferenceSites.of(
            reference.instructionSites.asSequence(),
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
        validatorReviewRecommended = reviewRecommended ||
            reference?.siteEvidenceUnavailableReason != null || sites.budgetExceeded,
        compiledReferenceSites = sites,
    )
}

private fun Long.toSafeInt(): Int? = takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
