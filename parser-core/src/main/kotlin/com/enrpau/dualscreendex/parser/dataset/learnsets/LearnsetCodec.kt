package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.analysis.CheckedRomExtent
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

sealed interface LearnsetFormat {
    val entrySize: Int
    val stableId: String

    data class PackedU16(val moveBits: Int) : LearnsetFormat {
        init {
            require(moveBits in 1..15) { "packed learnset move width must be within 1..15 bits" }
        }

        override val entrySize: Int = 2
        override val stableId: String = "packed-u16-m$moveBits"
    }

    data object LevelU8MoveU16 : LearnsetFormat {
        override val entrySize: Int = 3
        override val stableId: String = "level-u8-move-u16"
    }

    data object MoveU16LevelU8 : LearnsetFormat {
        override val entrySize: Int = 3
        override val stableId: String = "move-u16-level-u8"
    }

    data object MoveU16LevelU16 : LearnsetFormat {
        override val entrySize: Int = 4
        override val stableId: String = "move-u16-level-u16"
    }
}

data class LearnsetTableLayout(
    val offset: Long,
    val speciesCount: Int,
    val format: LearnsetFormat,
    val pointerStride: Int = DEFAULT_POINTER_STRIDE,
) : ImmutableDatasetLayout<LearnsetTableLayout> {
    init {
        require(offset >= 0) { "learnset table offset must not be negative" }
        require(speciesCount > 0) { "learnset species count must be positive" }
        require(pointerStride >= DEFAULT_POINTER_STRIDE) { "learnset pointer stride must cover one pointer" }
    }

    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "level-up-learnsets:${offset.toString(16)}:$speciesCount:${format.stableId}:p$pointerStride",
    )

    override fun immutableSnapshot(): LearnsetTableLayout = this
}

private const val DEFAULT_POINTER_STRIDE = 4

data class LearnsetEntryValue(
    val level: Int,
    val moveId: Int,
) {
    init {
        require(level in 0..100) { "learnset level must be within 0..100" }
        require(moveId > 0) { "learnset move id must be positive" }
    }
}

sealed interface LearnsetTermination {
    data object Explicit : LearnsetTermination

    data class RecoveredAtAdjacentPointer(
        val discardedWords: Int,
    ) : LearnsetTermination {
        init {
            require(discardedWords in 0..MAX_RECOVERABLE_PACKED_TAIL_WORDS) {
                "recovered packed tail must remain within the bounded recovery window"
            }
        }
    }

    data class RecoveredBeforeExplicitTerminator(
        val discardedWords: Int,
    ) : LearnsetTermination {
        init {
            require(discardedWords in 0..MAX_RECOVERABLE_PACKED_TAIL_WORDS) {
                "recovered packed tail must remain within the bounded recovery window"
            }
        }
    }
}

sealed interface LearnsetRowOutcome {
    val rowIndex: Int

    class Decoded(
        override val rowIndex: Int,
        entries: Collection<LearnsetEntryValue>,
        val termination: LearnsetTermination,
    ) : LearnsetRowOutcome {
        val entries: List<LearnsetEntryValue> = immutableList(entries)

        override fun equals(other: Any?): Boolean = other is Decoded &&
            rowIndex == other.rowIndex && entries == other.entries && termination == other.termination

        override fun hashCode(): Int = 31 * (31 * rowIndex + entries.hashCode()) + termination.hashCode()

        override fun toString(): String =
            "Decoded(rowIndex=$rowIndex, entries=$entries, termination=$termination)"
    }

    data class StructuralEmpty(
        override val rowIndex: Int,
    ) : LearnsetRowOutcome

    class Malformed(
        override val rowIndex: Int,
        reasons: Collection<String>,
    ) : LearnsetRowOutcome {
        val reasons: List<String> = immutableList(reasons.distinct().sorted())

        init {
            require(this.reasons.isNotEmpty()) { "malformed learnset row requires a reason" }
            require(this.reasons.all { it.isNotBlank() }) { "malformed learnset reasons must not be blank" }
        }

        override fun equals(other: Any?): Boolean = other is Malformed &&
            rowIndex == other.rowIndex && reasons == other.reasons

        override fun hashCode(): Int = 31 * rowIndex + reasons.hashCode()

        override fun toString(): String = "Malformed(rowIndex=$rowIndex, reasons=$reasons)"
    }
}

class ResolvedLearnsetLayout(
    val table: LearnsetTableLayout,
    rows: Collection<LearnsetRowOutcome>,
) : ImmutableDatasetLayout<ResolvedLearnsetLayout> {
    val rows: List<LearnsetRowOutcome> = immutableList(rows)
    override val layoutIdentity: CandidateLayoutIdentity = table.layoutIdentity

    init {
        require(this.rows.size == table.speciesCount) {
            "resolved learnset rows must match the declared species count"
        }
        require(this.rows.map { it.rowIndex } == this.rows.indices.toList()) {
            "resolved learnset rows must be complete and index ordered"
        }
    }

    override fun immutableSnapshot(): ResolvedLearnsetLayout = this

    override fun equals(other: Any?): Boolean = other is ResolvedLearnsetLayout &&
        table == other.table && rows == other.rows

    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()

    override fun toString(): String = "ResolvedLearnsetLayout(table=$table, rows=$rows)"
}

/** One already-selected physical table and the authority metadata retained by the family phase. */
class SelectedLearnsetTable(
    val table: LearnsetTableLayout,
    val confidence: Double,
    val referenceCount: Int,
) {
    init {
        require(confidence in 0.0..1.0) { "learnset confidence must be within 0..1" }
        require(referenceCount >= 0) { "learnset reference count must not be negative" }
    }
}

/** Immutable decoded form of one selected level-up table. */
class ResolvedSelectedLearnsetTable(
    val layout: ResolvedLearnsetLayout,
    val confidence: Double,
    val referenceCount: Int,
) {
    init {
        require(confidence in 0.0..1.0) { "learnset confidence must be within 0..1" }
        require(referenceCount >= 0) { "learnset reference count must not be negative" }
    }

    fun catalogEntries(): Map<Int, List<LearnsetEntry>> = Collections.unmodifiableMap(
        layout.rows.mapNotNull { row ->
            when (row) {
                is LearnsetRowOutcome.Decoded -> row.rowIndex to Collections.unmodifiableList(
                    row.entries.map { entry -> LearnsetEntry(entry.level, entry.moveId) },
                )
                is LearnsetRowOutcome.StructuralEmpty -> row.rowIndex to emptyList()
                is LearnsetRowOutcome.Malformed -> null
            }
        }.toMap(),
    )

    override fun equals(other: Any?): Boolean = other is ResolvedSelectedLearnsetTable &&
        layout == other.layout && confidence == other.confidence && referenceCount == other.referenceCount

    override fun hashCode(): Int = 31 * (31 * layout.hashCode() + confidence.hashCode()) + referenceCount
}

/** Selected-layout-only typed outcome propagated to semantic and catalog consumers. */
class ResolvedLearnsetSet(
    tables: Collection<ResolvedSelectedLearnsetTable>,
    primaryOffset: Long?,
    val selector: SaveBlock1LearnsetSelectorDescriptor?,
) {
    val tables: List<ResolvedSelectedLearnsetTable> = immutableList(tables)
    val primary: ResolvedSelectedLearnsetTable? = primaryOffset?.let { offset ->
        this.tables.singleOrNull { table -> table.layout.table.offset == offset }
    }

    init {
        require(this.tables.isNotEmpty()) { "resolved learnset set requires a selected table" }
        require(this.tables.map { it.layout.table.layoutIdentity }.distinct().size == this.tables.size) {
            "resolved learnset tables must be distinct"
        }
        require(primaryOffset == null || primary != null) { "primary learnset root must be selected" }
        selector?.let { descriptor ->
            val roots = this.tables.mapTo(linkedSetOf()) { it.layout.table.offset }
            require(descriptor.zeroTableOffset in roots && descriptor.nonZeroTableOffset in roots) {
                "learnset selector roots must both be selected"
            }
        }
    }

    fun immutableSnapshot(): ResolvedLearnsetSet = this

    fun catalogPrimaryEntries(): Map<Int, List<LearnsetEntry>> = primary?.catalogEntries().orEmpty()

    fun catalogRulesetCandidates(): List<ResolvedSelectedLearnsetTable> = tables

    override fun equals(other: Any?): Boolean = other is ResolvedLearnsetSet &&
        tables == other.tables && primary == other.primary && selector == other.selector

    override fun hashCode(): Int = 31 * (31 * tables.hashCode() + (primary?.hashCode() ?: 0)) +
        (selector?.hashCode() ?: 0)
}

sealed interface LearnsetTableOutcome {
    val layout: LearnsetTableLayout

    class Decoded(
        override val layout: LearnsetTableLayout,
        rows: Collection<LearnsetRowOutcome>,
    ) : LearnsetTableOutcome {
        val rows: List<LearnsetRowOutcome> = immutableList(rows)
        val decodedRows: Int get() = rows.count { it is LearnsetRowOutcome.Decoded }
        val malformedRows: Int get() = rows.count { it is LearnsetRowOutcome.Malformed }
        val totalEntries: Int get() = rows.sumOf { (it as? LearnsetRowOutcome.Decoded)?.entries?.size ?: 0 }
        val recoveredRows: Int get() = rows.count { row ->
            (row as? LearnsetRowOutcome.Decoded)?.termination?.let { it !is LearnsetTermination.Explicit } == true
        }

        override fun equals(other: Any?): Boolean = other is Decoded &&
            layout == other.layout && rows == other.rows

        override fun hashCode(): Int = 31 * layout.hashCode() + rows.hashCode()

        override fun toString(): String = "Decoded(layout=$layout, rows=$rows)"
    }

    data class Rejected(
        override val layout: LearnsetTableLayout,
        val reason: String,
    ) : LearnsetTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: LearnsetTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : LearnsetTableOutcome

    data class WorkBudgetExceeded(
        override val layout: LearnsetTableLayout,
        val observedWork: Long,
        val limitWork: Long,
        val reason: String,
    ) : LearnsetTableOutcome
}

interface LearnsetTableDecoder {
    fun decodeGen3(
        session: RomAnalysisSession,
        layout: LearnsetTableLayout,
        moveCount: Int,
        ledger: LearnsetResolutionLedger,
    ): LearnsetTableOutcome
}

class LearnsetCodec : LearnsetTableDecoder {
    fun decodeGen3(
        session: RomAnalysisSession,
        layout: LearnsetTableLayout,
        moveCount: Int,
    ): LearnsetTableOutcome = decodeGen3(
        session,
        layout,
        moveCount,
        LearnsetResolutionLedger(
            extentLimit = session.limits.maxDatasetExtentBytes,
            workLimit = session.limits.maxProbeWorkPerDataset.toLong(),
        ),
    )

    override fun decodeGen3(
        session: RomAnalysisSession,
        layout: LearnsetTableLayout,
        moveCount: Int,
        ledger: LearnsetResolutionLedger,
    ): LearnsetTableOutcome {
        try {
            ledger.consumeWork("layout attempt")
        } catch (exhausted: LearnsetWorkBudgetStop) {
            return exhausted.toOutcome(layout)
        }
        if (moveCount <= 1) return LearnsetTableOutcome.Rejected(layout, "move count must exceed NONE")
        if (layout.format is LearnsetFormat.PackedU16 && moveCount > (1 shl layout.format.moveBits)) {
            return LearnsetTableOutcome.Rejected(
                layout,
                "packed ${layout.format.moveBits}-bit format cannot represent $moveCount move ids",
            )
        }
        val pointerSpanLength = try {
            Math.addExact(
                Math.multiplyExact((layout.speciesCount - 1).toLong(), layout.pointerStride.toLong()),
                POINTER_BYTES,
            )
        } catch (_: ArithmeticException) {
            return LearnsetTableOutcome.Rejected(layout, "learnset pointer span overflows Long")
        }
        if (pointerSpanLength > session.limits.maxDatasetExtentBytes) {
            return LearnsetTableOutcome.ExtentBudgetExceeded(
                layout = layout,
                observedBytes = pointerSpanLength,
                limitBytes = session.limits.maxDatasetExtentBytes,
                reason = "Gen III learnset pointer-table extent budget exceeded " +
                    "($pointerSpanLength > ${session.limits.maxDatasetExtentBytes})",
            )
        }
        val pointerEnd = try {
            Math.addExact(layout.offset, pointerSpanLength)
        } catch (_: ArithmeticException) {
            return LearnsetTableOutcome.Rejected(layout, "learnset pointer span end overflows Long")
        }
        if (pointerEnd > session.rom.size.toLong() ||
            layout.offset > Int.MAX_VALUE || pointerSpanLength > Int.MAX_VALUE || pointerEnd > Int.MAX_VALUE
        ) {
            return LearnsetTableOutcome.Rejected(layout, "learnset pointer span exceeds the indexed ROM extent")
        }
        val pointerExtent = CheckedRomExtent(
            offset = layout.offset.toInt(),
            length = pointerSpanLength.toInt(),
            endExclusive = pointerEnd.toInt(),
        )

        ledger.claimExtent(pointerExtent.length.toLong())?.let { exhausted ->
            return LearnsetTableOutcome.ExtentBudgetExceeded(
                layout = layout,
                observedBytes = exhausted.observed,
                limitBytes = exhausted.limit,
                reason = "aggregate Gen III learnset extent budget exceeded " +
                    "(${exhausted.observed} > ${exhausted.limit})",
            )
        }

        return try {
            val rawPointers = List(layout.speciesCount) { row ->
                ledger.consumeWork("pointer-table row")
                session.rom.u32le(pointerExtent.offset + row * layout.pointerStride)
            }
            val targets = rawPointers.map { raw -> gbaTarget(raw, session.rom.size) }
            val distinctTargets = targets.filterNotNull().distinct().sorted()
            val boundaries = distinctTargets.mapIndexed { index, target ->
                target to distinctTargets.getOrNull(index + 1)
            }.toMap()
            val rows = rawPointers.indices.map { row ->
                val raw = rawPointers[row]
                when {
                    raw == 0L -> LearnsetRowOutcome.StructuralEmpty(row)
                    targets[row] == null -> LearnsetRowOutcome.Malformed(
                        row,
                        listOf("pointer 0x${raw.toString(16)} is not a bounded GBA ROM pointer"),
                    )
                    else -> {
                        val adjacentBoundary = boundaries[targets[row]]
                        decodeRow(
                            session = session,
                            ledger = ledger,
                            rowIndex = row,
                            start = requireNotNull(targets[row]),
                            endExclusive = adjacentBoundary ?: session.rom.size,
                            hasAdjacentBoundary = adjacentBoundary != null,
                            format = layout.format,
                            moveCount = moveCount,
                        )
                    }
                }
            }
            LearnsetTableOutcome.Decoded(layout, rows)
        } catch (exhausted: LearnsetWorkBudgetStop) {
            exhausted.toOutcome(layout)
        }
    }

    private fun decodeRow(
        session: RomAnalysisSession,
        ledger: LearnsetResolutionLedger,
        rowIndex: Int,
        start: Int,
        endExclusive: Int,
        hasAdjacentBoundary: Boolean,
        format: LearnsetFormat,
        moveCount: Int,
    ): LearnsetRowOutcome = when (format) {
        is LearnsetFormat.PackedU16 -> decodePacked(
            session,
            ledger,
            rowIndex,
            start,
            endExclusive,
            hasAdjacentBoundary,
            moveCount,
            format.moveBits,
        )
        LearnsetFormat.LevelU8MoveU16 -> decodeLevelMove(
            session,
            ledger,
            rowIndex,
            start,
            endExclusive,
            moveCount,
        )
        LearnsetFormat.MoveU16LevelU8 -> decodeMoveLevel(
            session,
            ledger,
            rowIndex,
            start,
            endExclusive,
            moveCount,
        )
        LearnsetFormat.MoveU16LevelU16 -> decodeWide(
            session,
            ledger,
            rowIndex,
            start,
            endExclusive,
            moveCount,
        )
    }

    private fun decodePacked(
        session: RomAnalysisSession,
        ledger: LearnsetResolutionLedger,
        row: Int,
        start: Int,
        end: Int,
        hasAdjacentBoundary: Boolean,
        moveCount: Int,
        moveBits: Int,
    ): LearnsetRowOutcome {
        val entries = mutableListOf<LearnsetEntryValue>()
        val moveMask = (1 shl moveBits) - 1
        var cursor = start
        repeat(MAX_ENTRIES_PER_ROW) {
            if (cursor + 2 > end) return malformed(row, "packed row is not explicitly terminated")
            ledger.consumeWork("packed row entry")
            val value = session.rom.u16le(cursor)
            if (value == PACKED_OR_WIDE_TERMINATOR) {
                return decoded(row, entries, LearnsetTermination.Explicit)
            }
            val move = value and moveMask
            val level = value ushr moveBits
            if (move !in 1 until moveCount || level !in 0..100) {
                val recovery = packedTailRecovery(session, ledger, cursor, end, hasAdjacentBoundary)
                if (entries.isNotEmpty() && recovery != null) {
                    return decoded(
                        row,
                        entries,
                        recovery,
                    )
                }
                return malformed(row, "packed row contains an invalid level or move")
            }
            entries += LearnsetEntryValue(level, move)
            cursor += 2
        }
        return malformed(row, "packed row exceeds the entry budget")
    }

    private fun packedTailRecovery(
        session: RomAnalysisSession,
        ledger: LearnsetResolutionLedger,
        firstInvalid: Int,
        end: Int,
        hasAdjacentBoundary: Boolean,
    ): LearnsetTermination? {
        for (skipped in 0..MAX_RECOVERABLE_PACKED_TAIL_WORDS) {
            val cursor = firstInvalid + skipped * 2
            if (cursor == end) {
                return if (hasAdjacentBoundary) {
                    LearnsetTermination.RecoveredAtAdjacentPointer(skipped)
                } else {
                    null
                }
            }
            if (cursor + 2 > end) return null
            if (skipped > 0) ledger.consumeWork("packed recovery tail")
            if (session.rom.u16le(cursor) == PACKED_OR_WIDE_TERMINATOR) {
                return LearnsetTermination.RecoveredBeforeExplicitTerminator(skipped)
            }
        }
        return null
    }

    private fun decodeLevelMove(
        session: RomAnalysisSession,
        ledger: LearnsetResolutionLedger,
        row: Int,
        start: Int,
        end: Int,
        moveCount: Int,
    ): LearnsetRowOutcome {
        val entries = mutableListOf<LearnsetEntryValue>()
        var cursor = start
        repeat(MAX_ENTRIES_PER_ROW) {
            if (cursor >= end) return malformed(row, "level/move row is not explicitly terminated")
            ledger.consumeWork("level/move row entry")
            val level = session.rom.u8(cursor)
            if (level == LEVEL_MOVE_TERMINATOR) return decoded(row, entries, LearnsetTermination.Explicit)
            if (cursor + 3 > end) return malformed(row, "level/move entry crosses the adjacent pointer")
            val move = session.rom.u16le(cursor + 1)
            if (level !in 0..100 || move !in 1 until moveCount) {
                return malformed(row, "level/move row contains an invalid level or move")
            }
            entries += LearnsetEntryValue(level, move)
            cursor += 3
        }
        return malformed(row, "level/move row exceeds the entry budget")
    }

    private fun decodeMoveLevel(
        session: RomAnalysisSession,
        ledger: LearnsetResolutionLedger,
        row: Int,
        start: Int,
        end: Int,
        moveCount: Int,
    ): LearnsetRowOutcome {
        val entries = mutableListOf<LearnsetEntryValue>()
        var cursor = start
        repeat(MAX_ENTRIES_PER_ROW) {
            if (cursor + 3 > end) return malformed(row, "move/level row is not explicitly terminated")
            ledger.consumeWork("move/level row entry")
            val move = session.rom.u16le(cursor)
            val level = session.rom.u8(cursor + 2)
            if (move == 0 && level == MOVE_LEVEL_TERMINATOR) {
                return decoded(row, entries, LearnsetTermination.Explicit)
            }
            if (move !in 1 until moveCount || level !in 0..100) {
                return malformed(row, "move/level row contains an invalid level or move")
            }
            entries += LearnsetEntryValue(level, move)
            cursor += 3
        }
        return malformed(row, "move/level row exceeds the entry budget")
    }

    private fun decodeWide(
        session: RomAnalysisSession,
        ledger: LearnsetResolutionLedger,
        row: Int,
        start: Int,
        end: Int,
        moveCount: Int,
    ): LearnsetRowOutcome {
        val entries = mutableListOf<LearnsetEntryValue>()
        var cursor = start
        repeat(MAX_ENTRIES_PER_ROW) {
            if (cursor + 2 > end) return malformed(row, "wide row is not explicitly terminated")
            ledger.consumeWork("wide row entry")
            val move = session.rom.u16le(cursor)
            if (move == PACKED_OR_WIDE_TERMINATOR) {
                return decoded(row, entries, LearnsetTermination.Explicit)
            }
            if (cursor + 4 > end) return malformed(row, "wide entry crosses the adjacent pointer")
            val level = session.rom.u16le(cursor + 2)
            if (move !in 1 until moveCount || level !in 0..100) {
                return malformed(row, "wide row contains an invalid level or move")
            }
            entries += LearnsetEntryValue(level, move)
            cursor += 4
        }
        return malformed(row, "wide row exceeds the entry budget")
    }

    private fun decoded(
        row: Int,
        entries: Collection<LearnsetEntryValue>,
        termination: LearnsetTermination,
    ): LearnsetRowOutcome.Decoded = LearnsetRowOutcome.Decoded(row, entries, termination)

    private fun malformed(row: Int, reason: String): LearnsetRowOutcome.Malformed =
        LearnsetRowOutcome.Malformed(row, listOf(reason))

    private fun gbaTarget(raw: Long, romSize: Int): Int? =
        (raw - GBA_ROM_BASE).takeIf { raw in GBA_ROM_START..GBA_ROM_END && it in 0 until romSize.toLong() }
            ?.toInt()

    private companion object {
        const val POINTER_BYTES = 4L
        const val GBA_ROM_BASE = 0x08000000L
        const val GBA_ROM_START = 0x08000000L
        const val GBA_ROM_END = 0x09FFFFFFL
        const val PACKED_OR_WIDE_TERMINATOR = 0xFFFF
        const val LEVEL_MOVE_TERMINATOR = 0xFE
        const val MOVE_LEVEL_TERMINATOR = 0xFF
        const val MAX_ENTRIES_PER_ROW = 256
    }
}

class LearnsetResolutionLedger internal constructor(
    private val extentLimit: Long,
    private val workLimit: Long,
) {
    private var extentConsumed = 0L
    private var workConsumed = 0L

    init {
        require(extentLimit > 0) { "learnset aggregate extent limit must be positive" }
        require(workLimit > 0) { "learnset aggregate work limit must be positive" }
    }

    internal fun claimExtent(bytes: Long): LearnsetBudgetWitness? {
        require(bytes >= 0) { "learnset extent claim must not be negative" }
        val observed = try {
            Math.addExact(extentConsumed, bytes)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        if (observed > extentLimit) return LearnsetBudgetWitness(observed, extentLimit)
        extentConsumed = observed
        return null
    }

    internal fun consumeWork(activity: String) {
        require(activity.isNotBlank()) { "learnset work activity must not be blank" }
        val observed = workConsumed + 1L
        if (observed > workLimit) throw LearnsetWorkBudgetStop(observed, workLimit, activity)
        workConsumed = observed
    }
}

internal data class LearnsetBudgetWitness(val observed: Long, val limit: Long)

private class LearnsetWorkBudgetStop(
    val observed: Long,
    val limit: Long,
    val activity: String,
) : RuntimeException(null, null, false, false) {
    fun toOutcome(layout: LearnsetTableLayout): LearnsetTableOutcome.WorkBudgetExceeded =
        LearnsetTableOutcome.WorkBudgetExceeded(
            layout = layout,
            observedWork = observed,
            limitWork = limit,
            reason = "aggregate Gen III learnset work budget exceeded during $activity " +
                "($observed > $limit)",
        )
}

private const val MAX_RECOVERABLE_PACKED_TAIL_WORDS = 4

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
