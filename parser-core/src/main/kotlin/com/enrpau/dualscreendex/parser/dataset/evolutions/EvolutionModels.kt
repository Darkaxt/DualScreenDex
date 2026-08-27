package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

/** Immutable physical shape of one fixed-width Gen III evolution array. */
class EvolutionTableLayout(
    val offset: Long,
    val count: Long,
    val slotsPerSpecies: Int,
    val recordSize: Int,
) : ImmutableDatasetLayout<EvolutionTableLayout> {
    val rowStride: Long get() = Math.multiplyExact(slotsPerSpecies.toLong(), recordSize.toLong())
    val endExclusive: Long get() = Math.addExact(offset, Math.multiplyExact(count, rowStride))
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "evolutions:${offset.toString(16)}:$count:$slotsPerSpecies:$recordSize",
    )

    fun rowOffset(rowIndex: Int): Int {
        require(rowIndex >= 0) { "evolution row index must not be negative" }
        val result = Math.addExact(offset, Math.multiplyExact(rowIndex.toLong(), rowStride))
        require(result in 0..Int.MAX_VALUE.toLong()) { "evolution row offset does not fit Int" }
        return result.toInt()
    }

    override fun immutableSnapshot(): EvolutionTableLayout = this

    override fun equals(other: Any?): Boolean = other is EvolutionTableLayout &&
        offset == other.offset && count == other.count &&
        slotsPerSpecies == other.slotsPerSpecies && recordSize == other.recordSize

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + count.hashCode()
        result = 31 * result + slotsPerSpecies
        result = 31 * result + recordSize
        return result
    }

    override fun toString(): String = "EvolutionTableLayout(" +
        "offset=$offset, count=$count, slotsPerSpecies=$slotsPerSpecies, recordSize=$recordSize)"
}

/** Immutable byte-preserving edge shared by validation and future materialization adapters. */
class EvolutionEdgeValue(
    val targetSpeciesId: Int,
    val methodId: Int,
    val parameter: Int,
    val conditionValue: Int?,
    raw: ByteArray,
) {
    private val rawSnapshot: ByteArray = raw.copyOf()
    val raw: ByteArray get() = rawSnapshot.copyOf()

    override fun equals(other: Any?): Boolean = other is EvolutionEdgeValue &&
        targetSpeciesId == other.targetSpeciesId && methodId == other.methodId &&
        parameter == other.parameter && conditionValue == other.conditionValue &&
        rawSnapshot.contentEquals(other.rawSnapshot)

    override fun hashCode(): Int {
        var result = targetSpeciesId
        result = 31 * result + methodId
        result = 31 * result + parameter
        result = 31 * result + (conditionValue ?: 0)
        result = 31 * result + rawSnapshot.contentHashCode()
        return result
    }

    override fun toString(): String = "EvolutionEdgeValue(" +
        "targetSpeciesId=$targetSpeciesId, methodId=$methodId, parameter=$parameter, " +
        "conditionValue=$conditionValue, raw=${rawSnapshot.contentToString()})"
}

sealed interface EvolutionRowOutcome {
    val rowIndex: Int

    class Decoded(
        override val rowIndex: Int,
        edges: Collection<EvolutionEdgeValue>,
    ) : EvolutionRowOutcome {
        val edges: List<EvolutionEdgeValue> = immutableList(edges)

        init {
            require(this.edges.isNotEmpty()) { "decoded evolution row requires at least one active edge" }
        }

        override fun equals(other: Any?): Boolean = other is Decoded &&
            rowIndex == other.rowIndex && edges == other.edges

        override fun hashCode(): Int = 31 * rowIndex + edges.hashCode()
        override fun toString(): String = "Decoded(rowIndex=$rowIndex, edges=$edges)"
    }

    data class StructuralEmpty(override val rowIndex: Int) : EvolutionRowOutcome

    class Malformed(
        override val rowIndex: Int,
        edges: Collection<EvolutionEdgeValue>,
        reasons: Collection<String>,
    ) : EvolutionRowOutcome {
        val edges: List<EvolutionEdgeValue> = immutableList(edges)
        val reasons: List<String> = immutableList(reasons)

        init {
            require(this.reasons.isNotEmpty()) { "malformed evolution row requires a reason" }
            require(this.reasons.all { it.isNotBlank() }) { "malformed reasons must not be blank" }
        }

        override fun equals(other: Any?): Boolean = other is Malformed &&
            rowIndex == other.rowIndex && edges == other.edges && reasons == other.reasons

        override fun hashCode(): Int = 31 * (31 * rowIndex + edges.hashCode()) + reasons.hashCode()
        override fun toString(): String = "Malformed(rowIndex=$rowIndex, edges=$edges, reasons=$reasons)"
    }
}

class ResolvedEvolutionLayout(
    val table: EvolutionTableLayout,
    rows: Collection<EvolutionRowOutcome>,
) : ImmutableDatasetLayout<ResolvedEvolutionLayout> {
    val rows: List<EvolutionRowOutcome> = immutableList(rows)
    override val layoutIdentity: CandidateLayoutIdentity = table.layoutIdentity

    init {
        require(table.count in 1..Int.MAX_VALUE.toLong()) { "resolved evolution count must fit Int" }
        require(this.rows.size == table.count.toInt()) { "row evidence must match evolution count" }
        require(this.rows.map { it.rowIndex } == this.rows.indices.toList()) {
            "row evidence must be complete and index ordered"
        }
    }

    override fun immutableSnapshot(): ResolvedEvolutionLayout = this

    /** Pure catalog projection. Physical row zero is always the structural species-none sentinel. */
    fun catalogEvolutions(): Map<Int, List<EvolutionEdge>> = Collections.unmodifiableMap(
        rows.mapNotNull { row ->
            when (row) {
                is EvolutionRowOutcome.Decoded -> row.rowIndex to Collections.unmodifiableList(
                    row.edges.map(EvolutionEdgeValue::toCatalogEdge),
                )
                is EvolutionRowOutcome.StructuralEmpty -> row.rowIndex to emptyList()
                is EvolutionRowOutcome.Malformed -> null
            }
        }.toMap(),
    )

    override fun equals(other: Any?): Boolean = other is ResolvedEvolutionLayout &&
        table == other.table && rows == other.rows
    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()
    override fun toString(): String = "ResolvedEvolutionLayout(table=$table, rows=$rows)"
}

private fun EvolutionEdgeValue.toCatalogEdge(): EvolutionEdge = EvolutionEdge(
    targetSpeciesId = targetSpeciesId,
    methodId = methodId,
    parameter = parameter,
    raw = raw,
    conditionValue = conditionValue,
)

sealed interface EvolutionTableOutcome {
    val layout: EvolutionTableLayout

    class Decoded(
        override val layout: EvolutionTableLayout,
        rows: Collection<EvolutionRowOutcome>,
    ) : EvolutionTableOutcome {
        val rows: List<EvolutionRowOutcome> = immutableList(rows)

        init {
            require(layout.count in 1..Int.MAX_VALUE.toLong()) {
                "decoded evolution count must fit indexed row outcomes"
            }
            require(this.rows.size == layout.count.toInt()) {
                "decoded row evidence must match evolution species count"
            }
            require(this.rows.map { it.rowIndex } == this.rows.indices.toList()) {
                "decoded row evidence must be complete and index ordered"
            }
        }

        val activeEdges: Int get() = rows.sumOf { row ->
            when (row) {
                is EvolutionRowOutcome.Decoded -> row.edges.size
                is EvolutionRowOutcome.Malformed -> row.edges.size
                is EvolutionRowOutcome.StructuralEmpty -> 0
            }
        }

        override fun equals(other: Any?): Boolean = other is Decoded &&
            layout == other.layout && rows == other.rows

        override fun hashCode(): Int = 31 * layout.hashCode() + rows.hashCode()
        override fun toString(): String = "Decoded(layout=$layout, rows=$rows)"
    }

    data class Rejected(override val layout: EvolutionTableLayout, val reason: String) : EvolutionTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: EvolutionTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : EvolutionTableOutcome
}

/** Explicit adapter boundary for the inseparable Gen I/II evolution + learnset stream. */
data class Gen12CombinedStreamLayout(
    val pointerTableOffset: Long,
    val count: Int,
    val tableBank: Int,
    val generation: Int,
    val moveCount: Int,
)

data class Gen12CombinedRowCharacterization(
    val rowIndex: Int,
    val evolutions: EvolutionRowOutcome,
    val learnsetEntries: Int,
    val learnsetValid: Boolean,
)

sealed interface Gen12CombinedStreamOutcome {
    class Decoded(
        val layout: Gen12CombinedStreamLayout,
        rows: Collection<Gen12CombinedRowCharacterization>,
    ) : Gen12CombinedStreamOutcome {
        val rows: List<Gen12CombinedRowCharacterization> = immutableList(rows)

        override fun equals(other: Any?): Boolean = other is Decoded &&
            layout == other.layout && rows == other.rows

        override fun hashCode(): Int = 31 * layout.hashCode() + rows.hashCode()
        override fun toString(): String = "Decoded(layout=$layout, rows=$rows)"
    }

    data class Rejected(val layout: Gen12CombinedStreamLayout, val reason: String) : Gen12CombinedStreamOutcome

    data class ExtentBudgetExceeded(
        val layout: Gen12CombinedStreamLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : Gen12CombinedStreamOutcome
}

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
