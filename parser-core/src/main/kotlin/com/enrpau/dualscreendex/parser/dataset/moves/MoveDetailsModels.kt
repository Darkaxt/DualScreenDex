package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

/** Fixed-width Gen III move-detail ABIs currently proven by the parser. */
enum class MoveDetailsAbi(
    val recordSize: Int,
    val tableRecordFormat: TableRecordFormat,
) {
    /** Retail `BattleMove`: eight scalar bytes followed by a little-endian u32 flags field. */
    RETAIL_12(12, TableRecordFormat.STANDARD),

    /** Widened effect/power ABI used by the accepted CFRU/DPE-derived controls. */
    CFRU_16(16, TableRecordFormat.CFRU_MOVE_16),

    /** Widened retail fields with u16 target, byte flags/string/dance fields, and two-byte tail padding. */
    WIDENED_RETAIL_16(16, TableRecordFormat.WIDENED_RETAIL_MOVE_16),

    /** Later Battle Engine ABI with u16 target, u32 flags, split, and Z-move metadata. */
    BATTLE_ENGINE_20(20, TableRecordFormat.BATTLE_ENGINE_MOVE_20),

    /** Expansion-derived `MoveInfo` with pointer names and packed scalar fields. */
    UNIFIED_MOVE_INFO_48(48, TableRecordFormat.UNIFIED_MOVE_INFO_48),
}

enum class MoveSplit(val rawValue: Int) {
    PHYSICAL(0),
    SPECIAL(1),
    STATUS(2),
    ;

    companion object {
        fun fromRaw(value: Int): MoveSplit? = entries.singleOrNull { it.rawValue == value }
    }
}

class MoveDetailsTableLayout(
    val offset: Long,
    val count: Long,
    val abi: MoveDetailsAbi,
) : ImmutableDatasetLayout<MoveDetailsTableLayout> {
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "move-details:${offset.toString(16)}:$count:${abi.name}",
    )

    override fun immutableSnapshot(): MoveDetailsTableLayout = this

    override fun equals(other: Any?): Boolean = this === other ||
        other is MoveDetailsTableLayout && offset == other.offset && count == other.count && abi == other.abi

    override fun hashCode(): Int = 31 * (31 * offset.hashCode() + count.hashCode()) + abi.hashCode()

    override fun toString(): String =
        "MoveDetailsTableLayout(offset=$offset, count=$count, abi=$abi, layoutIdentity=$layoutIdentity)"
}

/** The independently established active move IDs against which semantic coverage is measured. */
class MoveDetailsSemanticDomain(
    val tableRowCount: Long,
    activeRowIndices: Set<Int>,
) {
    val activeRowIndices: List<Int> = immutableList(activeRowIndices.sorted())

    init {
        require(tableRowCount > 0) { "move-details semantic-domain cardinality must be positive" }
        require(activeRowIndices.all { it >= 0 && it.toLong() < tableRowCount }) {
            "active move row indices must fit the declared table cardinality"
        }
        require(this.activeRowIndices.isNotEmpty()) {
            "move-details semantic domain must identify at least one active row"
        }
    }

    val expectedRecords: Int get() = activeRowIndices.size

    override fun equals(other: Any?): Boolean = this === other ||
        other is MoveDetailsSemanticDomain &&
        tableRowCount == other.tableRowCount && activeRowIndices == other.activeRowIndices

    override fun hashCode(): Int = 31 * tableRowCount.hashCode() + activeRowIndices.hashCode()

    override fun toString(): String =
        "MoveDetailsSemanticDomain(tableRowCount=$tableRowCount, activeRowIndices=$activeRowIndices)"
}

/** Complete byte-level move details. Nullable fields are absent from that ABI, not guessed. */
data class Gen3MoveDetailsRecord(
    val effectId: Int,
    val power: Int,
    val typeId: Int,
    val accuracy: Int,
    val pp: Int,
    val secondaryEffectChance: Int,
    val targetMask: Int,
    val priority: Int,
    val flags: Long,
    val split: MoveSplit?,
    val argument: Int?,
    val zMovePower: Int?,
    val zMoveEffect: Int?,
)

data class CatalogMoveDetails(
    val typeId: Int,
    val category: MoveCategory,
    val power: Int,
    val accuracy: Int,
    val pp: Int,
    val priority: Int,
    val effectId: Int,
)

sealed interface MoveDetailsRowOutcome {
    val rowIndex: Int

    data class Decoded(
        override val rowIndex: Int,
        val record: Gen3MoveDetailsRecord,
    ) : MoveDetailsRowOutcome

    data class StructuralEmpty(
        override val rowIndex: Int,
    ) : MoveDetailsRowOutcome

    class Malformed(
        override val rowIndex: Int,
        reasons: Collection<String>,
    ) : MoveDetailsRowOutcome {
        val reasons: List<String> = immutableList(reasons.distinct().sorted())

        init {
            require(this.reasons.isNotEmpty()) { "malformed move-detail rows require a reason" }
        }

        override fun equals(other: Any?): Boolean = this === other ||
            other is Malformed && rowIndex == other.rowIndex && reasons == other.reasons

        override fun hashCode(): Int = 31 * rowIndex + reasons.hashCode()

        override fun toString(): String = "Malformed(rowIndex=$rowIndex, reasons=$reasons)"
    }
}

class ResolvedMoveDetailsLayout(
    val table: MoveDetailsTableLayout,
    rows: Collection<MoveDetailsRowOutcome>,
) : ImmutableDatasetLayout<ResolvedMoveDetailsLayout> {
    val rows: List<MoveDetailsRowOutcome> = immutableList(rows.toList())
    override val layoutIdentity: CandidateLayoutIdentity = table.layoutIdentity
    val materializedRecords: Map<Int, Gen3MoveDetailsRecord> = Collections.unmodifiableMap(
        linkedMapOf<Int, Gen3MoveDetailsRecord>().apply {
            this@ResolvedMoveDetailsLayout.rows.forEach { row ->
                if (row is MoveDetailsRowOutcome.Decoded) put(row.rowIndex, row.record)
            }
        },
    )

    /** Pure projection from typed ABI evidence to the fields exposed by the catalog API. */
    fun catalogDetails(): Map<Int, CatalogMoveDetails> = Collections.unmodifiableMap(
        materializedRecords.mapValues { (_, record) ->
            CatalogMoveDetails(
                typeId = record.typeId,
                category = if (table.abi == MoveDetailsAbi.WIDENED_RETAIL_16) {
                    when {
                        record.power == 0 -> MoveCategory.STATUS
                        record.typeId < 12 -> MoveCategory.PHYSICAL
                        record.typeId > 12 -> MoveCategory.SPECIAL
                        else -> MoveCategory.UNKNOWN
                    }
                } else {
                    when (record.split) {
                        MoveSplit.PHYSICAL -> MoveCategory.PHYSICAL
                        MoveSplit.SPECIAL -> MoveCategory.SPECIAL
                        MoveSplit.STATUS -> MoveCategory.STATUS
                        null -> when {
                            record.power == 0 -> MoveCategory.STATUS
                            record.typeId in 0..8 -> MoveCategory.PHYSICAL
                            record.typeId in 10..17 -> MoveCategory.SPECIAL
                            else -> MoveCategory.UNKNOWN
                        }
                    }
                },
                power = record.power,
                accuracy = record.accuracy,
                pp = record.pp,
                priority = record.priority,
                effectId = record.effectId,
            )
        },
    )

    init {
        require(table.count in 1..Int.MAX_VALUE.toLong()) {
            "resolved move-detail cardinality must fit indexed row outcomes"
        }
        require(this.rows.size == table.count.toInt()) {
            "resolved move-detail row outcomes must match table cardinality"
        }
        require(this.rows.map { it.rowIndex } == this.rows.indices.toList()) {
            "resolved move-detail rows must be complete and index ordered"
        }
    }

    override fun immutableSnapshot(): ResolvedMoveDetailsLayout = this

    override fun equals(other: Any?): Boolean = this === other ||
        other is ResolvedMoveDetailsLayout && table == other.table && rows == other.rows

    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()

    override fun toString(): String = "ResolvedMoveDetailsLayout(table=$table, rows=$rows)"
}

sealed interface MoveDetailsTableOutcome {
    val layout: MoveDetailsTableLayout

    class Decoded(
        override val layout: MoveDetailsTableLayout,
        rows: Collection<MoveDetailsRowOutcome>,
    ) : MoveDetailsTableOutcome {
        val rows: List<MoveDetailsRowOutcome> = immutableList(rows.toList())

        override fun equals(other: Any?): Boolean = this === other ||
            other is Decoded && layout == other.layout && rows == other.rows

        override fun hashCode(): Int = 31 * layout.hashCode() + rows.hashCode()
    }

    data class Rejected(
        override val layout: MoveDetailsTableLayout,
        val reason: String,
    ) : MoveDetailsTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: MoveDetailsTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : MoveDetailsTableOutcome
}

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
