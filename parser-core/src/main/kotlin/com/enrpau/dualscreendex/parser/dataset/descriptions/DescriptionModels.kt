package com.enrpau.dualscreendex.parser.dataset.descriptions

import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

/**
 * The physical identity of a Gen III Pokédex-description table.
 *
 * [pointerOffsets] deliberately participates in value equality. A 36-byte one-page layout
 * and a 36-byte two-page layout at the same root are different candidates, not aliases.
 */
class DescriptionTableLayout(
    val offset: Long,
    val count: Long,
    val recordSize: Int,
    pointerOffsets: List<Int>,
) : ImmutableDatasetLayout<DescriptionTableLayout> {
    val pointerOffsets: List<Int> = immutableCopy(pointerOffsets)
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "pokedex-descriptions:${offset.toString(16)}:$count:$recordSize:" +
            this.pointerOffsets.joinToString(","),
    )

    /** This final value type owns immutable snapshots of every collection it publishes. */
    override fun immutableSnapshot(): DescriptionTableLayout = this

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is DescriptionTableLayout &&
            offset == other.offset &&
            count == other.count &&
            recordSize == other.recordSize &&
            pointerOffsets == other.pointerOffsets &&
            layoutIdentity == other.layoutIdentity

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + count.hashCode()
        result = 31 * result + recordSize
        result = 31 * result + pointerOffsets.hashCode()
        result = 31 * result + layoutIdentity.hashCode()
        return result
    }

    override fun toString(): String =
        "DescriptionTableLayout(offset=$offset, count=$count, recordSize=$recordSize, " +
            "pointerOffsets=$pointerOffsets, layoutIdentity=$layoutIdentity)"
}

/** Selected physical table plus the immutable row-by-row codec evidence used to validate it. */
class ResolvedDescriptionLayout(
    val table: DescriptionTableLayout,
    rows: Collection<DescriptionRowOutcome>,
) : ImmutableDatasetLayout<ResolvedDescriptionLayout> {
    val rows: List<DescriptionRowOutcome> = immutableCopy(rows)
    override val layoutIdentity: CandidateLayoutIdentity = table.layoutIdentity

    init {
        require(table.count in 1..Int.MAX_VALUE.toLong()) {
            "resolved description table count must fit indexed row outcomes"
        }
        require(this.rows.size == table.count.toInt()) {
            "resolved description row outcomes must match table count"
        }
        require(this.rows.map { it.rowIndex } == this.rows.indices.toList()) {
            "resolved description row outcomes must be complete and index ordered"
        }
    }

    override fun immutableSnapshot(): ResolvedDescriptionLayout = this

    override fun equals(other: Any?): Boolean =
        this === other || other is ResolvedDescriptionLayout && table == other.table && rows == other.rows

    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()

    override fun toString(): String = "ResolvedDescriptionLayout(table=$table, rows=$rows)"
}

sealed interface DescriptionRecoveryProvenance {
    data class Direct(val pointer: Int) : DescriptionRecoveryProvenance

    data class OffByOneWithinNextReferencedBoundary(
        val originalPointer: Int,
        val recoveredPointer: Int,
        val nextReferencedBoundary: Int,
    ) : DescriptionRecoveryProvenance
}

data class DecodedDescriptionPage(
    val text: String,
    val provenance: DescriptionRecoveryProvenance,
)

sealed interface DescriptionRowOutcome {
    val rowIndex: Int

    class Decoded(
        override val rowIndex: Int,
        val category: String,
        val height: Int,
        val weight: Int,
        pages: List<DecodedDescriptionPage>,
    ) : DescriptionRowOutcome {
        val pages: List<DecodedDescriptionPage> = immutableCopy(pages)

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is Decoded &&
                rowIndex == other.rowIndex &&
                category == other.category &&
                height == other.height &&
                weight == other.weight &&
                pages == other.pages

        override fun hashCode(): Int {
            var result = rowIndex
            result = 31 * result + category.hashCode()
            result = 31 * result + height
            result = 31 * result + weight
            result = 31 * result + pages.hashCode()
            return result
        }

        override fun toString(): String =
            "Decoded(rowIndex=$rowIndex, category=$category, height=$height, weight=$weight, " +
                "pages=$pages)"
    }

    data class StructuralEmpty(
        override val rowIndex: Int,
    ) : DescriptionRowOutcome

    class Malformed(
        override val rowIndex: Int,
        reasons: List<String>,
    ) : DescriptionRowOutcome {
        val reasons: List<String> = immutableCopy(reasons)

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is Malformed && rowIndex == other.rowIndex && reasons == other.reasons

        override fun hashCode(): Int = 31 * rowIndex + reasons.hashCode()

        override fun toString(): String = "Malformed(rowIndex=$rowIndex, reasons=$reasons)"
    }
}

sealed interface DescriptionTableOutcome {
    val layout: DescriptionTableLayout

    class Decoded(
        override val layout: DescriptionTableLayout,
        rows: List<DescriptionRowOutcome>,
    ) : DescriptionTableOutcome {
        val rows: List<DescriptionRowOutcome> = immutableCopy(rows)
        val decodedRows: Int get() = rows.count { it is DescriptionRowOutcome.Decoded }
        val recoveredPages: Int get() = rows.sumOf { row ->
            (row as? DescriptionRowOutcome.Decoded)?.pages.orEmpty().count {
                it.provenance is DescriptionRecoveryProvenance.OffByOneWithinNextReferencedBoundary
            }
        }

        override fun equals(other: Any?): Boolean =
            this === other || other is Decoded && layout == other.layout && rows == other.rows

        override fun hashCode(): Int = 31 * layout.hashCode() + rows.hashCode()

        override fun toString(): String = "Decoded(layout=$layout, rows=$rows)"
    }

    data class Rejected(
        override val layout: DescriptionTableLayout,
        val reason: String,
    ) : DescriptionTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: DescriptionTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : DescriptionTableOutcome
}

private fun <T> immutableCopy(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
