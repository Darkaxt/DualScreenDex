package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.model.HeaderlessUnifiedSpeciesMetadata
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence

/**
 * Resolves a wide level-up learnset pointer embedded in each already-proven unified species row.
 * The species root, stride, active predicate, and row count are parser authority. A field is
 * selected only when it is the unique aligned pointer field whose active rows all decode as
 * explicitly terminated `(u16 move, u16 level)` lists.
 *
 * Move IDs decoded from this relationship are existence evidence in their own right. Names and
 * details remain unavailable unless an independent move dataset covers the same ID.
 */
internal object EmbeddedLearnsetPointerResolver {
    private const val POINTER_BYTES = 4
    private const val TERMINATOR = 0xFFFF
    private const val MAX_ENTRIES_PER_SPECIES = 256

    data class Resolution(
        val evidence: ValidationEvidence,
        val resolved: ResolvedLearnsetSet,
        val table: TableLayout,
        val pointerFieldOffset: Int,
    )

    private data class Candidate(
        val fieldOffset: Int,
        val rows: List<LearnsetRowOutcome>,
    )

    fun resolve(
        session: RomAnalysisSession,
        metadata: HeaderlessUnifiedSpeciesMetadata,
        speciesCount: Int,
    ): Resolution? {
        if (speciesCount <= 1 || metadata.speciesRecordSize < POINTER_BYTES) return null
        val work = WorkLedger(session.limits.maxProbeWorkPerDataset.toLong())
        val candidates: List<Candidate> = try {
            mutableListOf<Candidate>().apply {
                for (fieldOffset in 0..metadata.speciesRecordSize - POINTER_BYTES step POINTER_BYTES) {
                    decodeCandidate(session, metadata, speciesCount, fieldOffset, work)?.let { add(it) }
                }
            }
        } catch (_: WorkBudgetExceeded) {
            return null
        }
        val selected = candidates.singleOrNull() ?: return null
        val pointerTableOffset = metadata.speciesTableOffset + selected.fieldOffset
        val typedTable = LearnsetTableLayout(
            offset = pointerTableOffset.toLong(),
            speciesCount = speciesCount,
            format = LearnsetFormat.MoveU16LevelU16,
            pointerStride = metadata.speciesRecordSize,
        )
        val resolvedTable = ResolvedSelectedLearnsetTable(
            layout = ResolvedLearnsetLayout(typedTable, selected.rows),
            confidence = 1.0,
            referenceCount = 0,
        )
        val physicalTable = TableLayout(
            offset = pointerTableOffset,
            count = speciesCount,
            recordSize = POINTER_BYTES,
            variableLength = true,
            elementSize = LearnsetFormat.MoveU16LevelU16.entrySize,
            stride = metadata.speciesRecordSize,
            format = TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16,
        )
        return Resolution(
            evidence = ValidationEvidence(
                compatible = true,
                validRecords = speciesCount,
                totalRecords = speciesCount,
                confidence = 1.0,
                reasons = listOf(
                    "decoded every active unified-species learnset pointer through one unique terminated-list ABI",
                ),
                offset = pointerTableOffset,
                recordSize = POINTER_BYTES,
                elementSize = LearnsetFormat.MoveU16LevelU16.entrySize,
                coveredRecords = speciesCount,
                expectedRecords = speciesCount,
                incompleteRecords = 0,
                format = TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16,
            ),
            resolved = ResolvedLearnsetSet(
                tables = listOf(resolvedTable),
                primaryOffset = pointerTableOffset.toLong(),
                selector = null,
            ),
            table = physicalTable,
            pointerFieldOffset = selected.fieldOffset,
        )
    }

    private fun decodeCandidate(
        session: RomAnalysisSession,
        metadata: HeaderlessUnifiedSpeciesMetadata,
        speciesCount: Int,
        fieldOffset: Int,
        work: WorkLedger,
    ): Candidate? {
        val rows = ArrayList<LearnsetRowOutcome>(speciesCount)
        var decodedRows = 0
        var entryCount = 0
        return try {
            val fallbackPointerField = metadata.speciesTableOffset + fieldOffset
            val fallbackRaw = session.rom.u32le(fallbackPointerField)
            val fallback = when {
                fallbackRaw == 0L -> null
                else -> session.rom.gbaPointer(fallbackPointerField) ?: return null
            }
            for (speciesId in 0 until speciesCount) {
                val speciesRecord = Math.addExact(
                    metadata.speciesTableOffset,
                    Math.multiplyExact(speciesId, metadata.speciesRecordSize),
                )
                val active = speciesId != 0 &&
                    session.rom.u8(speciesRecord + metadata.activePredicateOffset) != 0
                if (!active) {
                    rows += LearnsetRowOutcome.StructuralEmpty(speciesId)
                    continue
                }
                work.consume()
                val pointerField = speciesRecord + fieldOffset
                val rawPointer = session.rom.u32le(pointerField)
                val listOffset = when {
                    rawPointer == 0L -> fallback ?: return null
                    else -> session.rom.gbaPointer(pointerField) ?: return null
                }
                val entries = ArrayList<LearnsetEntryValue>()
                var terminated = false
                for (entryIndex in 0 until MAX_ENTRIES_PER_SPECIES) {
                    work.consume()
                    val entryOffset = Math.addExact(listOffset, Math.multiplyExact(entryIndex, 4))
                    val move = session.rom.u16le(entryOffset)
                    if (move == TERMINATOR) {
                        terminated = true
                        break
                    }
                    val level = session.rom.u16le(entryOffset + 2)
                    if (move == 0 || level !in 0..100) return null
                    entries += LearnsetEntryValue(level = level, moveId = move)
                }
                if (!terminated) return null
                decodedRows++
                entryCount += entries.size
                rows += LearnsetRowOutcome.Decoded(
                    rowIndex = speciesId,
                    entries = entries,
                    termination = LearnsetTermination.Explicit,
                )
            }
            if (decodedRows == 0 || entryCount == 0) null else {
                Candidate(fieldOffset, rows)
            }
        } catch (_: RomBoundsException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    private class WorkLedger(private val limit: Long) {
        private var consumed = 0L

        fun consume() {
            consumed++
            if (consumed > limit) throw WorkBudgetExceeded
        }
    }

    private object WorkBudgetExceeded : RuntimeException(null, null, false, false)
}
