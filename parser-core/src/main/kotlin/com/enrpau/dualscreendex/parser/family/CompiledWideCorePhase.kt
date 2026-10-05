package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsRowOutcome
import com.enrpau.dualscreendex.parser.dataset.core.basestats.Gen3BaseStatsRecord
import com.enrpau.dualscreendex.parser.model.Gen3CompiledCanonicalSpeciesMetadata
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideCoreOutcome
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideMoveOutcome
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideMoveResolver

/** Keeps independently declared physical extents separate from inverse-first-match semantic coverage. */
internal fun resolveCompiledWideCorePhase(
    session: RomAnalysisSession,
    identity: IdentityRootsPhaseResult.Resolved,
    wide: Gen3CompiledWideCoreOutcome.Resolved,
): CoreDatasetsPhaseResult.Resolved {
    val canonicalCount = wide.nativeToDex.size
    val typedStats = wide.baseStats.table
    val names = wide.speciesNames
    val stats = TableLayout(typedStats.offset.toInt(), typedStats.count.toInt(), typedStats.abi.recordSize,
        format = TableRecordFormat.WIDE_STATS_64)
    val namesEvidence = compiledCoverage(names, canonicalCount, "bounded copier and canonical inverse/forward name joins")
    val statsEvidence = compiledCoverage(stats, canonicalCount, "selector-false root and six widened stat/type reads")
    val outcome = Gen3CompiledWideMoveResolver.resolve(session, wide, identity.probeCodec)
    val moves = outcome as? Gen3CompiledWideMoveOutcome.Resolved
    val missingReason = (outcome as? Gen3CompiledWideMoveOutcome.Rejected)?.reason
        ?: "complete compiled ordinary acquisition/name/move-prefix authority is absent"
    val moveNames = moves?.names
    val moveData = moves?.details?.table?.let { table ->
        TableLayout(table.offset.toInt(), table.count.toInt(), table.abi.recordSize, format = table.abi.tableRecordFormat)
    }
    val moveNamesEvidence = moveNames?.let {
        compiledCoverage(it, it.count - 1, "17-byte EOS copier joined to the proven ordinary acquisition minimum; full move bound unproven")
    } ?: compiledMissing(missingReason)
    val moveDataEvidence = moveData?.let { table ->
        val unclassified = requireNotNull(moves).details.materializedRecords.count { (id, row) -> id > 0 && row.split == null }
        compiledCoverage(table, table.count - 1, "independent scalar-prefix and signed-priority consumers; extension mechanics unproven")
            .copy(coveredRecords = table.count - 1 - unclassified, incompleteRecords = unclassified,
                reasons = listOf("all acquisition-minimum scalar rows decoded; $unclassified encoded split rows lack static category authority"))
    } ?: compiledMissing(missingReason)
    val tables = identity.tableResolution.tables.copy(
        speciesNames = names, baseStats = stats, moveNames = moveNames, moveData = moveData,
        learnsets = moves?.learnsetTable, evolutions = null, descriptions = null,
    )
    return CoreDatasetsPhaseResult.Resolved(
        candidateTables = tables, speciesCount = wide.speciesCount,
        inferredMoveCount = moves?.acquisitionMinimumMoveCount, moveCount = moves?.acquisitionMinimumMoveCount,
        speciesNames = namesEvidence, baseStats = statsEvidence, moveNames = moveNamesEvidence, moveData = moveDataEvidence,
        speciesNamesLayout = names, baseStatsLayout = stats, moveNamesLayout = moveNames, moveDataLayout = moveData,
        resolvedMoveDetails = moves?.details,
        moveDetailsTypedRejectionReason = missingReason.takeIf { moves == null },
        languageManifest = RomLanguageAuthority.resolve(session.rom, session.header, 3, identity.probeCodec,
            namesEvidence, moveNamesEvidence, names, moveNames, session.cancellation),
        resolvedBaseStats = wide.baseStats,
        compiledCanonicalSpecies = Gen3CompiledCanonicalSpeciesMetadata(names, typedStats, wide.nativeToDex),
        compiledWideMoves = moves,
    )
}

internal fun compiledWideRecords(core: CoreDatasetsPhaseResult.Resolved): Map<Int, Gen3BaseStatsRecord> {
    val canonical = core.compiledCanonicalSpecies ?: return emptyMap()
    val typed = core.resolvedBaseStats ?: return emptyMap()
    if (typed.table != canonical.baseStatsTable || core.speciesNamesLayout != canonical.speciesNames ||
        canonical.nativeToDex.keys.any { typed.rows[it] !is BaseStatsRowOutcome.Decoded }
    ) return emptyMap()
    return canonical.nativeToDex.keys.associateWith { (typed.rows[it] as BaseStatsRowOutcome.Decoded).record }
}

internal fun compiledWideLearnsetEvidence(core: CoreDatasetsPhaseResult.Resolved): ValidationEvidence {
    val moves = core.compiledWideMoves ?: return compiledMissing(core.moveDetailsTypedRejectionReason
        ?: "ordinary acquisition prerequisite is unavailable")
    val expected = requireNotNull(core.compiledCanonicalSpecies).nativeToDex.size
    return compiledCoverage(moves.learnsetTable, expected,
        "complete canonical four-byte lists, unshifted u16 levels, explicit termination and move-reference joins")
}

private fun compiledCoverage(table: TableLayout, covered: Int, reason: String) = ValidationEvidence(
    compatible = true, validRecords = covered, totalRecords = table.count, confidence = 1.0,
    reasons = listOf(reason), offset = table.offset, recordSize = table.recordSize,
    elementSize = table.elementSize, format = table.format,
    coveredRecords = covered, expectedRecords = covered, incompleteRecords = 0,
)

private fun compiledMissing(reason: String) = ValidationEvidence(false, 0, 0, 0.0, listOf(reason))
