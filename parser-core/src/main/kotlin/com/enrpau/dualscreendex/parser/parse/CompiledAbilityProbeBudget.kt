package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.resolution.BudgetKind

internal class CompiledAbilityProbeBudget(private val session: RomAnalysisSession) {
    private var work = 0L
    private var roots = 0L

    fun step() {
        session.cancellation.throwIfCancellationRequested()
        work++
        check(BudgetKind.PROBE_WORK, work, session.limits.maxProbeWorkPerDataset.toLong())
    }

    fun root() {
        roots++
        check(BudgetKind.PROBE_ROOTS, roots, session.limits.maxProbeRootsPerDataset.toLong())
    }

    fun extent(bytes: Long) = check(BudgetKind.EXTENT, bytes, session.limits.maxDatasetExtentBytes)

    private fun check(kind: BudgetKind, observed: Long, limit: Long) {
        if (observed > limit) throw CompiledAbilityBudgetExceeded(kind, observed, limit)
    }
}

internal class CompiledAbilityBudgetExceeded(val kind: BudgetKind, val observed: Long, val limit: Long) :
    RuntimeException("compiled ability $kind budget exceeded: $observed/$limit")
