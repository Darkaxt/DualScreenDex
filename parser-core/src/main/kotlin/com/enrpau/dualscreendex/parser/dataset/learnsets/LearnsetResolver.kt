package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetEncoding
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.parse.Gen3LearnsetSelectorExtractor
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
import java.util.Collections

/** Compiled SaveBlock1 branch proof binding exactly two already-validated level-up roots. */
data class SaveBlock1LearnsetSelectorDescriptor(
    val saveBlock1ByteOffset: Int,
    val mask: Int,
    val zeroTableOffset: Long,
    val nonZeroTableOffset: Long,
    val codeOffset: Int,
) {
    init {
        require(saveBlock1ByteOffset in 0 until MAX_SAVE_BLOCK1_BYTES) {
            "SaveBlock1 learnset selector byte offset is out of bounds"
        }
        require(mask in 1..0x80 && mask and (mask - 1) == 0) {
            "SaveBlock1 learnset selector mask must be one-hot"
        }
        require(zeroTableOffset >= 0 && nonZeroTableOffset >= 0) {
            "learnset selector roots must not be negative"
        }
        require(zeroTableOffset != nonZeroTableOffset) {
            "learnset selector arms must reference distinct roots"
        }
        require(codeOffset >= 0) { "learnset selector code offset must not be negative" }
    }

    private companion object {
        const val MAX_SAVE_BLOCK1_BYTES = 0x4000
    }
}

/** Extractor-issued proof tied to one immutable ROM digest and its two validated branch roots. */
class SaveBlock1LearnsetSelectorProof private constructor(
    val descriptor: SaveBlock1LearnsetSelectorDescriptor,
    private val romSha256: String,
) {
    internal fun belongsTo(session: RomAnalysisSession): Boolean = session.rom.sha256 == romSha256

    companion object {
        fun verify(
            session: RomAnalysisSession,
            descriptor: SaveBlock1LearnsetSelectorDescriptor,
            validatedTableOffsets: Set<Int>,
        ): SaveBlock1LearnsetSelectorProof? {
            if (
                descriptor.zeroTableOffset !in 0..Int.MAX_VALUE.toLong() ||
                descriptor.nonZeroTableOffset !in 0..Int.MAX_VALUE.toLong()
            ) {
                return null
            }
            val extracted = Gen3LearnsetSelectorExtractor.extract(session.rom, validatedTableOffsets)
                ?: return null
            if (
                extracted.saveBlock1ByteOffset != descriptor.saveBlock1ByteOffset ||
                extracted.mask != descriptor.mask ||
                extracted.zeroTableOffset.toLong() != descriptor.zeroTableOffset ||
                extracted.nonZeroTableOffset.toLong() != descriptor.nonZeroTableOffset ||
                extracted.codeOffset != descriptor.codeOffset
            ) {
                return null
            }
            return SaveBlock1LearnsetSelectorProof(descriptor, session.rom.sha256)
        }
    }
}

data class SelectorBoundLearnsetArm(
    val candidate: DatasetCandidate<ResolvedLearnsetLayout>,
)

class SelectorBoundLearnsetAlternatives(
    val descriptor: SaveBlock1LearnsetSelectorDescriptor,
    val zero: SelectorBoundLearnsetArm,
    val nonZero: SelectorBoundLearnsetArm,
) {
    val candidates: List<DatasetCandidate<ResolvedLearnsetLayout>> = Collections.unmodifiableList(
        listOf(zero.candidate, nonZero.candidate),
    )

    init {
        require(zero.candidate.layout.table.offset == descriptor.zeroTableOffset) {
            "zero selector arm does not match the descriptor root"
        }
        require(nonZero.candidate.layout.table.offset == descriptor.nonZeroTableOffset) {
            "non-zero selector arm does not match the descriptor root"
        }
        require(zero.candidate.layoutIdentity != nonZero.candidate.layoutIdentity) {
            "selector-bound learnset alternatives must have distinct layouts"
        }
    }

    override fun equals(other: Any?): Boolean = other is SelectorBoundLearnsetAlternatives &&
        descriptor == other.descriptor && zero == other.zero && nonZero == other.nonZero

    override fun hashCode(): Int = 31 * (31 * descriptor.hashCode() + zero.hashCode()) + nonZero.hashCode()

    override fun toString(): String =
        "SelectorBoundLearnsetAlternatives(descriptor=$descriptor, zero=$zero, nonZero=$nonZero)"
}

data class LearnsetResolution(
    val primary: DatasetResolution<ResolvedLearnsetLayout>,
    val selectorBoundAlternatives: SelectorBoundLearnsetAlternatives? = null,
)

/** Typed Gen III level-up discovery and resolution with one shared session evidence index. */
class LearnsetResolver(
    private val decoder: LearnsetTableDecoder = LearnsetCodec(),
) {
    fun resolveSelectedGen3(
        session: RomAnalysisSession,
        moveCount: Int,
        selectedTables: Collection<SelectedLearnsetTable>,
        primaryOffset: Long?,
        selectorProof: SaveBlock1LearnsetSelectorProof? = null,
    ): SelectedLearnsetResolution {
        if (moveCount <= 1 || selectedTables.isEmpty()) {
            return SelectedLearnsetResolution(null, "selected Gen III learnset layouts require a move domain")
        }
        val selected = selectedTables.toList()
        val speciesCounts = selected.map { it.table.speciesCount }.distinct()
        if (speciesCounts.size != 1 || selected.map { it.table.layoutIdentity }.distinct().size != selected.size) {
            return SelectedLearnsetResolution(null, "selected Gen III learnset layouts are inconsistent")
        }
        if (primaryOffset != null && selected.none { it.table.offset == primaryOffset }) {
            return SelectedLearnsetResolution(null, "selected primary learnset root is absent")
        }
        val selector = selectorProof?.takeIf { it.belongsTo(session) }?.descriptor
        if (selectorProof != null && selector == null) {
            return SelectedLearnsetResolution(null, "learnset selector proof belongs to a different ROM")
        }
        if (selector != null) {
            val roots = selected.mapTo(linkedSetOf()) { it.table.offset }
            if (selector.zeroTableOffset !in roots || selector.nonZeroTableOffset !in roots) {
                return SelectedLearnsetResolution(null, "learnset selector roots are not both selected")
            }
        }

        val ledger = LearnsetResolutionLedger(
            extentLimit = session.limits.maxDatasetExtentBytes,
            workLimit = session.limits.maxProbeWorkPerDataset.toLong(),
        )
        val resolved = mutableListOf<ResolvedSelectedLearnsetTable>()
        selected.forEach { selectedTable ->
            val outcome = decoder.decodeGen3(session, selectedTable.table, moveCount, ledger)
            val decoded = outcome as? LearnsetTableOutcome.Decoded
                ?: return SelectedLearnsetResolution(null, "selected learnset layout failed typed decoding")
            val decodedRows = decoded.decodedRows
            val nonNullRows = decoded.decodedRows + decoded.malformedRows
            val minimumEvidence = minOf(selectedTable.table.speciesCount, MIN_DECODED_ROWS)
            if (
                decodedRows < minimumEvidence ||
                (nonNullRows > 0 && decodedRows.toLong() * 2L <= nonNullRows.toLong()) ||
                decoded.totalEntries < minimumEvidence
            ) {
                return SelectedLearnsetResolution(null, "selected learnset layout failed typed validation")
            }
            resolved += ResolvedSelectedLearnsetTable(
                ResolvedLearnsetLayout(selectedTable.table, decoded.rows),
                selectedTable.confidence,
                selectedTable.referenceCount,
            )
        }
        return SelectedLearnsetResolution(
            ResolvedLearnsetSet(resolved, primaryOffset, selector),
            null,
        )
    }

    fun resolveGen3(
        session: RomAnalysisSession,
        expectedSpeciesCount: Int,
        moveCount: Int,
        profileLayout: LearnsetTableLayout? = null,
        publishedLayouts: Collection<LearnsetTableLayout> = emptyList(),
        structuralLayouts: Collection<LearnsetTableLayout> = emptyList(),
        selectorDescriptor: SaveBlock1LearnsetSelectorDescriptor? = null,
        selectorProof: SaveBlock1LearnsetSelectorProof? = null,
    ): LearnsetResolution {
        if (expectedSpeciesCount <= 0 || moveCount <= 1) {
            return LearnsetResolution(
                unavailable("Gen III learnset resolution requires positive species and move domains"),
            )
        }

        val verifiedProof = selectorProof?.takeIf { proof ->
            proof.belongsTo(session) && (selectorDescriptor == null || selectorDescriptor == proof.descriptor)
        }
        val exactPhysicalLayout = profileLayout?.takeIf { matchesExactPhysicalLayout(session, it) }
        if (
            exactPhysicalLayout != null &&
            !matchesExactProfileLayout(session, exactPhysicalLayout, moveCount)
        ) {
            return LearnsetResolution(
                unavailable("matching exact-profile learnset metadata has an invalid typed ABI"),
            )
        }

        val roots = linkedSetOf<Long>()
        val ledger = LearnsetResolutionLedger(
            extentLimit = session.limits.maxDatasetExtentBytes,
            workLimit = session.limits.maxProbeWorkPerDataset.toLong(),
        )
        val candidates = mutableListOf<DatasetCandidate<ResolvedLearnsetLayout>>()
        var observedCandidates = 0L

        fun consume(
            probe: Probe,
            destination: MutableList<DatasetCandidate<ResolvedLearnsetLayout>>,
        ): DatasetResolution.BudgetExceeded<ResolvedLearnsetLayout>? {
            if (probe.layout.offset !in roots) {
                if (roots.size == session.limits.maxProbeRootsPerDataset) {
                    return budgetExceeded(
                        BudgetKind.PROBE_ROOTS,
                        roots.size.toLong() + 1L,
                        session.limits.maxProbeRootsPerDataset.toLong(),
                        complete = false,
                        reason = "Gen III learnset probe-root budget exceeded " +
                            "(${roots.size + 1} > ${session.limits.maxProbeRootsPerDataset})",
                    )
                }
                roots += probe.layout.offset
            }
            when (val assessment = assess(session, expectedSpeciesCount, moveCount, probe, ledger)) {
                is Assessment.Candidate -> {
                    observedCandidates++
                    if (observedCandidates > session.limits.maxCandidatesPerDataset.toLong()) {
                        return budgetExceeded(
                            BudgetKind.CANDIDATES,
                            observedCandidates,
                            session.limits.maxCandidatesPerDataset.toLong(),
                            complete = false,
                            reason = "Gen III learnset candidate budget exceeded " +
                                "($observedCandidates > ${session.limits.maxCandidatesPerDataset})",
                        )
                    }
                    destination += assessment.value
                }
                is Assessment.ExtentBudget -> return assessment.toResolution()
                is Assessment.WorkBudget -> return assessment.toResolution()
                Assessment.Rejected -> Unit
            }
            return null
        }

        var profileCandidate: DatasetCandidate<ResolvedLearnsetLayout>? = null
        if (profileLayout != null) {
            val destination = mutableListOf<DatasetCandidate<ResolvedLearnsetLayout>>()
            val source = if (matchesExactProfileLayout(session, profileLayout, moveCount)) {
                CandidateSource.EXACT_PROFILE
            } else {
                CandidateSource.INHERITED_FAMILY_LAYOUT
            }
            consume(Probe(profileLayout, source, ReferenceEvidence.EMPTY), destination)
                ?.let { return LearnsetResolution(it) }
            profileCandidate = destination.singleOrNull()
            candidates += destination
            if (source == CandidateSource.EXACT_PROFILE && profileCandidate == null) {
                return LearnsetResolution(
                    unavailable("matching exact-profile learnset layout failed codec validation"),
                )
            }
        }

        val selectorArmCandidates = verifiedProof?.descriptor?.let { descriptor ->
            linkedMapOf(
                descriptor.zeroTableOffset to mutableListOf<DatasetCandidate<ResolvedLearnsetLayout>>(),
                descriptor.nonZeroTableOffset to mutableListOf(),
            )
        }
        profileCandidate?.let { candidate ->
            selectorArmCandidates?.get(candidate.layout.table.offset)?.add(candidate)
        }
        if (selectorArmCandidates != null) {
            val descriptor = requireNotNull(verifiedProof).descriptor
            selectorArmCandidates.forEach { (root, atRoot) ->
                supportedFormats(moveCount).forEach { format ->
                    consume(
                        Probe(
                            LearnsetTableLayout(root, expectedSpeciesCount, format),
                            CandidateSource.DIRECT_COMPILED_CONSUMER,
                            ReferenceEvidence.direct(descriptor.codeOffset),
                        ),
                        atRoot,
                    )?.let { return LearnsetResolution(it) }
                }
            }
        }

        fun selectorBinding(): LearnsetResolution? {
            val proof = verifiedProof ?: return null
            val arms = selectorArmCandidates ?: return null
            val zeroSelection = selectArm(session, arms.getValue(proof.descriptor.zeroTableOffset))
            val nonZeroSelection = selectArm(session, arms.getValue(proof.descriptor.nonZeroTableOffset))
            val zeroCandidate = zeroSelection?.candidateOrNull()
            val nonZeroCandidate = nonZeroSelection?.candidateOrNull()
            return if (zeroCandidate != null && nonZeroCandidate != null) {
                LearnsetResolution(
                    primary = requireNotNull(zeroSelection),
                    selectorBoundAlternatives = SelectorBoundLearnsetAlternatives(
                        proof.descriptor,
                        SelectorBoundLearnsetArm(zeroCandidate),
                        SelectorBoundLearnsetArm(nonZeroCandidate),
                    ),
                )
            } else {
                null
            }
        }

        val preIndexBinding = selectorBinding()
        if (preIndexBinding != null && publishedLayouts.isEmpty()) return preIndexBinding
        if (preIndexBinding == null && profileCandidate?.source == CandidateSource.EXACT_PROFILE) {
            return LearnsetResolution(
                CandidateSelector.select(
                    session,
                    DatasetKind.LEVEL_UP_LEARNSETS,
                    sequenceOf(profileCandidate),
                ),
            )
        }

        val referenceIndex = session.gbaReferenceIndex
            ?: return preIndexBinding ?: LearnsetResolution(
                unavailable("Gen III learnset resolution requires a GBA session"),
            )
        if (referenceIndex.overflowed) {
            return preIndexBinding ?: LearnsetResolution(
                budgetExceeded(
                    BudgetKind.REFERENCE_TARGETS,
                    observed = maxOf(
                        referenceIndex.observedTargets.toLong(),
                        referenceIndex.limitTargets.toLong() + 1L,
                    ),
                    limit = referenceIndex.limitTargets.toLong(),
                    complete = false,
                    reason = requireNotNull(referenceIndex.overflowReason),
                ),
            )
        }

        val genericProposals = sequence {
            publishedLayouts.forEach { layout ->
                yield(
                    Probe(
                        layout,
                        CandidateSource.PUBLISHED_HEADER,
                        referenceEvidence(referenceIndex, layout.offset),
                    ),
                )
            }
            referenceIndex.targets.keys.asSequence().sorted().forEach { root ->
                supportedFormats(moveCount).forEach { format ->
                    yield(
                        Probe(
                            LearnsetTableLayout(root.toLong(), expectedSpeciesCount, format),
                            CandidateSource.COMPILED_REFERENCE,
                            referenceEvidence(referenceIndex, root.toLong()),
                        ),
                    )
                }
            }
            structuralLayouts.forEach { layout ->
                yield(
                    Probe(
                        layout,
                        CandidateSource.STRUCTURAL_ANCHOR,
                        referenceEvidence(referenceIndex, layout.offset),
                    ),
                )
            }
        }
        val genericIterator = genericProposals.iterator()
        while (genericIterator.hasNext()) {
            val proposal = genericIterator.next()
            val destination = mutableListOf<DatasetCandidate<ResolvedLearnsetLayout>>()
            consume(proposal, destination)?.let { return LearnsetResolution(it) }
            candidates += destination
            selectorArmCandidates?.get(proposal.layout.offset)?.addAll(destination)
        }

        selectorBinding()?.let { return it }

        return LearnsetResolution(
            CandidateSelector.select(
                session,
                DatasetKind.LEVEL_UP_LEARNSETS,
                removeSameRootPackedAliases(candidates).asSequence(),
                structuralAnchorPolicy = StructuralAnchorPolicy.allow(DatasetKind.LEVEL_UP_LEARNSETS),
            ),
        )
    }

    private fun selectArm(
        session: RomAnalysisSession,
        candidates: List<DatasetCandidate<ResolvedLearnsetLayout>>,
    ): DatasetResolution<ResolvedLearnsetLayout>? = candidates.takeIf { it.isNotEmpty() }?.let {
        CandidateSelector.select(
            session,
            DatasetKind.LEVEL_UP_LEARNSETS,
            removeSameRootPackedAliases(it).asSequence(),
        )
    }

    private fun removeSameRootPackedAliases(
        candidates: List<DatasetCandidate<ResolvedLearnsetLayout>>,
    ): List<DatasetCandidate<ResolvedLearnsetLayout>> {
        val aliases = candidates
            .groupBy { candidate ->
                candidate.layout.table.offset to candidate.layout.table.speciesCount
            }
            .values
            .flatMapTo(linkedSetOf()) { atRoot ->
                val wideHasPositiveLevels = atRoot.any { candidate ->
                    candidate.layout.table.format == LearnsetFormat.MoveU16LevelU16 &&
                        candidate.layout.levelEvidence().positive > 0
                }
                if (!wideHasPositiveLevels) return@flatMapTo emptyList()
                atRoot.filter { candidate ->
                    candidate.layout.table.format is LearnsetFormat.PackedU16 &&
                        candidate.layout.levelEvidence().let { evidence ->
                            evidence.allRowsUsable && evidence.total > 0 && evidence.positive == 0
                        }
                }
            }
        return candidates.filterNot(aliases::contains)
    }

    private fun ResolvedLearnsetLayout.levelEvidence(): LevelEvidence {
        var total = 0
        var positive = 0
        rows.forEach { row ->
            val decoded = row as? LearnsetRowOutcome.Decoded
                ?: return LevelEvidence(total, positive, allRowsUsable = false)
            total += decoded.entries.size
            positive += decoded.entries.count { entry -> entry.level > 0 }
        }
        return LevelEvidence(total, positive, allRowsUsable = true)
    }

    private fun DatasetResolution<ResolvedLearnsetLayout>.candidateOrNull():
        DatasetCandidate<ResolvedLearnsetLayout>? = when (this) {
        is DatasetResolution.Resolved -> candidate
        is DatasetResolution.Partial -> candidate
        is DatasetResolution.Ambiguous,
        is DatasetResolution.BudgetExceeded,
        is DatasetResolution.Unavailable,
        -> null
    }

    private fun assess(
        session: RomAnalysisSession,
        expectedSpeciesCount: Int,
        moveCount: Int,
        probe: Probe,
        ledger: LearnsetResolutionLedger,
    ): Assessment {
        if (probe.layout.speciesCount != expectedSpeciesCount) return Assessment.Rejected
        val outcome = when (val decoded = decoder.decodeGen3(session, probe.layout, moveCount, ledger)) {
            is LearnsetTableOutcome.Decoded -> decoded
            is LearnsetTableOutcome.ExtentBudgetExceeded -> return Assessment.ExtentBudget(
                decoded.observedBytes,
                decoded.limitBytes,
                decoded.reason,
            )
            is LearnsetTableOutcome.WorkBudgetExceeded -> return Assessment.WorkBudget(
                decoded.observedWork,
                decoded.limitWork,
                decoded.reason,
            )
            is LearnsetTableOutcome.Rejected -> return Assessment.Rejected
        }
        val decodedRows = outcome.decodedRows
        val malformedRows = outcome.malformedRows
        val nonNullRows = decodedRows + malformedRows
        val minimumEvidence = minOf(expectedSpeciesCount, MIN_DECODED_ROWS)
        if (
            decodedRows < minimumEvidence ||
            (nonNullRows > 0 && decodedRows.toLong() * 2L <= nonNullRows.toLong()) ||
            outcome.totalEntries < minimumEvidence
        ) {
            return Assessment.Rejected
        }
        val structuralRows = expectedSpeciesCount - malformedRows
        val compiledSites = probe.references.compiledSites(session)
        val unavailableSites = probe.references.evidence
            .mapNotNull { it.siteEvidenceUnavailableReason }
            .distinct()
            .sorted()
        val reasons = buildList {
            add(
                CandidateReason(
                    CandidateReasonKind.INFORMATION,
                    "validated candidate source ${probe.source.name}",
                ),
            )
            if (malformedRows > 0) {
                add(
                    CandidateReason(
                        CandidateReasonKind.ANOMALY,
                        "$malformedRows learnset row(s) are malformed",
                    ),
                )
            }
            if (outcome.recoveredRows > 0) {
                add(
                    CandidateReason(
                        CandidateReasonKind.RECOVERY,
                        "${outcome.recoveredRows} packed learnset row(s) use bounded tail recovery",
                    ),
                )
            }
            unavailableSites.forEach { reason ->
                add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
            }
            compiledSites.overflowReason?.let { reason ->
                add(CandidateReason(CandidateReasonKind.ANOMALY, reason))
            }
        }
        val eligibility = if (probe.source == CandidateSource.EXACT_PROFILE) {
            session.exactProfileEligibility()
        } else {
            CandidateEligibility.validated(probe.source)
        }
        val resolved = ResolvedLearnsetLayout(probe.layout, outcome.rows)
        return Assessment.Candidate(
            DatasetCandidate(
                identity = CandidateIdentity(resolved.layoutIdentity.value),
                kind = DatasetKind.LEVEL_UP_LEARNSETS,
                layout = resolved,
                source = probe.source,
                strength = CandidateStrength(
                    semanticCoverage = EvidenceCoverage(decodedRows, expectedSpeciesCount),
                    structuralCoverage = EvidenceCoverage(structuralRows, expectedSpeciesCount),
                    compiledReferenceCount = probe.references.count,
                    datasetQuality = (decodedRows - outcome.recoveredRows).coerceAtLeast(0),
                ),
                diagnosticOffset = probe.layout.offset.toInt(),
                diagnosticLabel = resolved.layoutIdentity.value,
                eligibility = eligibility,
                provenance = CandidateProvenance(
                    reasons = reasons,
                    validatorReviewRecommended = malformedRows > 0 || outcome.recoveredRows > 0 ||
                        unavailableSites.isNotEmpty() || compiledSites.budgetExceeded,
                    compiledReferenceSites = compiledSites,
                ),
            ),
        )
    }

    private fun matchesExactProfileLayout(
        session: RomAnalysisSession,
        layout: LearnsetTableLayout,
        moveCount: Int,
    ): Boolean {
        val exact = session.exactProfileSnapshot?.tables?.learnsets ?: return false
        if (
            layout.format is LearnsetFormat.PackedU16 &&
            layout.format.moveBits != Gen3LearnsetEncoding.packedMoveBits(moveCount)
        ) {
            return false
        }
        val expectedFormat = when (layout.format) {
            is LearnsetFormat.PackedU16 -> TableRecordFormat.GEN3_PACKED_U16
            LearnsetFormat.LevelU8MoveU16 -> TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16
            LearnsetFormat.MoveU16LevelU8 -> TableRecordFormat.GEN3_MOVE_U16_LEVEL_U8
            LearnsetFormat.MoveU16LevelU16 -> TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16
        }
        val legacyPackedMetadata = layout.format is LearnsetFormat.PackedU16 &&
            exact.format == TableRecordFormat.STANDARD &&
            exact.elementSize in setOf(null, layout.format.entrySize)
        return exact.offset.toLong() == layout.offset &&
            exact.count == layout.speciesCount &&
            exact.recordSize == POINTER_BYTES &&
            exact.variableLength &&
            (legacyPackedMetadata ||
                exact.elementSize == layout.format.entrySize && exact.format == expectedFormat)
    }

    private fun matchesExactPhysicalLayout(
        session: RomAnalysisSession,
        layout: LearnsetTableLayout,
    ): Boolean {
        val exact = session.exactProfileSnapshot?.tables?.learnsets ?: return false
        return exact.offset.toLong() == layout.offset &&
            exact.count == layout.speciesCount &&
            exact.recordSize == POINTER_BYTES &&
            exact.variableLength
    }

    private fun supportedFormats(moveCount: Int): List<LearnsetFormat> = listOf(
        LearnsetFormat.PackedU16(Gen3LearnsetEncoding.packedMoveBits(moveCount)),
        LearnsetFormat.LevelU8MoveU16,
        LearnsetFormat.MoveU16LevelU8,
        LearnsetFormat.MoveU16LevelU16,
    )

    private fun referenceEvidence(
        index: GbaReferenceIndex,
        root: Long,
        directInstructionSite: Int? = null,
    ): ReferenceEvidence = ReferenceEvidence(
        evidence = root.takeIf { it in 0..Int.MAX_VALUE.toLong() }
            ?.toInt()
            ?.let(index::target)
            ?.let(::listOf)
            .orEmpty(),
        directInstructionSites = listOfNotNull(directInstructionSite),
        directConsumer = directInstructionSite != null,
    )

    private fun unavailable(reason: String): DatasetResolution.Unavailable<ResolvedLearnsetLayout> =
        DatasetResolution.Unavailable(DatasetKind.LEVEL_UP_LEARNSETS, 0, reason)

    private fun budgetExceeded(
        kind: BudgetKind,
        observed: Long,
        limit: Long,
        complete: Boolean,
        reason: String,
    ): DatasetResolution.BudgetExceeded<ResolvedLearnsetLayout> = DatasetResolution.BudgetExceeded(
        DatasetKind.LEVEL_UP_LEARNSETS,
        kind,
        observed,
        limit,
        complete,
        reason,
    )

    private data class Probe(
        val layout: LearnsetTableLayout,
        val source: CandidateSource,
        val references: ReferenceEvidence,
    )

    private data class ReferenceEvidence(
        val evidence: List<GbaTargetReferenceEvidence>,
        val directInstructionSites: List<Int> = emptyList(),
        val directConsumer: Boolean = false,
    ) {
        val count: Int = evidence.fold(if (directConsumer) 1 else 0) { total, item ->
            (total.toLong() + item.count.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        fun compiledSites(session: RomAnalysisSession): CompiledReferenceSites {
            val overflowed = evidence.filter { it.siteBudgetExceeded }
            if (overflowed.isNotEmpty()) {
                return CompiledReferenceSites.overflowed(
                    observedSites = maxOf(
                        overflowed.maxOf { it.observedSites.toLong() },
                        session.limits.maxCompiledReferenceSitesPerCandidate.toLong() + 1L,
                    ),
                    limitSites = session.limits.maxCompiledReferenceSitesPerCandidate,
                    reason = overflowed.mapNotNull { it.overflowReason }.distinct().sorted().joinToString("; "),
                )
            }
            return CompiledReferenceSites.of(
                evidence.asSequence().flatMap { it.instructionSites.asSequence() } +
                    directInstructionSites.asSequence(),
                session.limits.maxCompiledReferenceSitesPerCandidate,
            )
        }

        companion object {
            val EMPTY = ReferenceEvidence(emptyList())

            fun direct(instructionSite: Int): ReferenceEvidence = ReferenceEvidence(
                evidence = emptyList(),
                directInstructionSites = listOf(instructionSite),
                directConsumer = true,
            )
        }
    }

    private data class LevelEvidence(
        val total: Int,
        val positive: Int,
        val allRowsUsable: Boolean,
    )

    private sealed interface Assessment {
        data class Candidate(val value: DatasetCandidate<ResolvedLearnsetLayout>) : Assessment
        data class ExtentBudget(val observed: Long, val limit: Long, val reason: String) : Assessment {
            fun toResolution(): DatasetResolution.BudgetExceeded<ResolvedLearnsetLayout> =
                DatasetResolution.BudgetExceeded(
                    DatasetKind.LEVEL_UP_LEARNSETS,
                    BudgetKind.EXTENT,
                    observed,
                    limit,
                    true,
                    reason,
                )
        }
        data class WorkBudget(val observed: Long, val limit: Long, val reason: String) : Assessment {
            fun toResolution(): DatasetResolution.BudgetExceeded<ResolvedLearnsetLayout> =
                DatasetResolution.BudgetExceeded(
                    DatasetKind.LEVEL_UP_LEARNSETS,
                    BudgetKind.PROBE_WORK,
                    observed,
                    limit,
                    false,
                    reason,
                )
        }
        data object Rejected : Assessment
    }

    private companion object {
        const val POINTER_BYTES = 4
        const val MIN_DECODED_ROWS = 3
    }
}

data class SelectedLearnsetResolution(
    val resolved: ResolvedLearnsetSet?,
    val reason: String?,
)
