package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence

/** Current validated physical core roles, not an item root or permission from an absent original route. */
internal class Gen3SelectedCoreItemRoute private constructor(
    private val owner: RomAnalysisSession,
    val nameRoot: Int,
    val nameStride: Int,
    val statRoot: Int,
    val statStride: Int,
    val count: Int,
) {
    fun belongsTo(session: RomAnalysisSession): Boolean = owner === session

    companion object {
        fun fromValidated(
            session: RomAnalysisSession,
            names: TableLayout?,
            stats: TableLayout?,
            nameEvidence: ValidationEvidence,
            statEvidence: ValidationEvidence,
        ): Gen3SelectedCoreItemRoute? {
            session.cancellation.throwIfCancellationRequested()
            if (session.header.platform != Platform.GBA || names == null || stats == null ||
                names.count != stats.count || names.count !in 2..65536 || names.offset == stats.offset ||
                names.recordSize !in 2..64 || stats.recordSize !in 2..256) return null
            fun coherent(table: TableLayout, evidence: ValidationEvidence): Boolean =
                !table.variableLength && !table.valuesArePointers && table.pointerOffsets.isEmpty() &&
                    table.bank == null && table.banks.isEmpty() && table.bankAdjustment == 0 && table.bankRemap.isEmpty() &&
                    (table.stride == null || table.stride == table.recordSize) &&
                    table.format == TableRecordFormat.STANDARD && evidence.compatible && !evidence.ambiguous &&
                    evidence.offset == table.offset && evidence.recordSize == table.recordSize &&
                    evidence.totalRecords == table.count && evidence.validRecords in 1..table.count &&
                    (evidence.format == null || evidence.format == table.format) &&
                    session.limits.checkTableExtent(table.offset.toLong(), table.count.toLong(),
                        table.recordSize.toLong(), session.rom.size.toLong()) is ExtentCheck.Valid
            if (!coherent(names, nameEvidence) || !coherent(stats, statEvidence)) return null
            val nameEnd = names.offset.toLong() + names.count.toLong() * names.recordSize
            val statEnd = stats.offset.toLong() + stats.count.toLong() * stats.recordSize
            if (names.offset.toLong() < statEnd && stats.offset.toLong() < nameEnd) return null
            return Gen3SelectedCoreItemRoute(session, names.offset, names.recordSize,
                stats.offset, stats.recordSize, names.count)
        }
    }
}

/** Only a complete native core binding nominates the independent item route. Rejection is terminal. */
internal sealed interface Gen3SelectedCoreItemOutcome {
    data object NotNominated : Gen3SelectedCoreItemOutcome
    data class Evaluated(val authority: GbaItemNameAuthority) : Gen3SelectedCoreItemOutcome
}
