package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsCodec
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsRowOutcome
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsSemanticDomain
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableLayout
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableOutcome
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout

/** Complete native scalar/packed-tail contracts over an independently established named move domain. */
internal object Gen3CompiledPackedMoveResolver {
    fun resolve(session: RomAnalysisSession, domain: MoveDetailsSemanticDomain): ResolvedMoveDetailsLayout? {
        session.cancellation.throwIfCancellationRequested()
        val codec = MoveDetailsCodec()
        val roots = mutableMapOf<Int, Boolean>()
        var work = 0
        fun consumeWork() {
            if (++work > session.limits.maxProbeWorkPerDataset) throw WideMoveBudgetStop()
        }
        fun acceptRoot(root: Int): Boolean = roots.getOrPut(root) {
            session.cancellation.throwIfCancellationRequested()
            if (roots.size >= session.limits.maxProbeRootsPerDataset) throw WideMoveBudgetStop()
            consumeWork()
            if (root % 4 != 0 || session.limits.checkTableExtent(
                    root.toLong(), domain.tableRowCount, 20, session.rom.size.toLong(),
                ) !is ExtentCheck.Valid
            ) return@getOrPut false
            domain.activeRowIndices.take(8).all { index ->
                consumeWork()
                val row = codec.decode(session, MoveDetailsTableLayout(
                    root.toLong() + index.toLong() * 20, 1, MoveDetailsAbi.PACKED_FLAGS_MOVE_20,
                )) as? MoveDetailsTableOutcome.Decoded
                row?.rows?.singleOrNull() is MoveDetailsRowOutcome.Decoded
            }
        }
        try {
            // Scan the entire image; indexed reference-site caps do not establish consumer completeness.
            val fields = GbaAffineMoveFieldWitnesses.collectPacked(session, session.rom.size, ::acceptRoot, ::consumeWork)
            val candidates = fields.map { it.root }.distinct().filter { root ->
                REQUIRED_FIELDS.all { (offset, width, signed) ->
                    GbaMoveFieldWitness(root, 20, offset, width, signed) in fields
                }
            }
            val complete = candidates.mapNotNull { root ->
                session.cancellation.throwIfCancellationRequested()
                repeat(domain.tableRowCount.toInt()) { consumeWork() }
                val table = MoveDetailsTableLayout(root.toLong(), domain.tableRowCount, MoveDetailsAbi.PACKED_FLAGS_MOVE_20)
                val decoded = codec.decode(session, table) as? MoveDetailsTableOutcome.Decoded ?: return@mapNotNull null
                if (decoded.rows.any { it is MoveDetailsRowOutcome.Malformed } ||
                    domain.activeRowIndices.any { decoded.rows[it] !is MoveDetailsRowOutcome.Decoded }
                ) return@mapNotNull null
                ResolvedMoveDetailsLayout(table, decoded.rows)
            }
            return complete.singleOrNull()
        } catch (_: WideMoveBudgetStop) {
            return null
        }
    }

    private data class Field(val offset: Int, val width: Int, val signed: Boolean = false)
    private val REQUIRED_FIELDS = listOf(Field(0, 2), Field(2, 1), Field(3, 1), Field(4, 1), Field(5, 1),
        Field(6, 1), Field(8, 2), Field(10, 1, true), Field(11, 1), Field(12, 2), Field(14, 1),
        Field(15, 1), Field(16, 1), Field(17, 1), Field(18, 1), Field(19, 1))
}
