package com.enrpau.dualscreendex.parser.dataset.types

import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

enum class TypeChartAbi {
    LEGACY_TRIPLETS,
    DENSE_U32_Q412,
    DENSE_U16_Q412_WITH_INVERSE,
}

/** Immutable physical interpretation of one Gen III type-effectiveness table. */
class TypeChartTableLayout(
    val offset: Long,
    val abi: TypeChartAbi,
    val typeCount: Int? = null,
) : ImmutableDatasetLayout<TypeChartTableLayout> {
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "type-chart:${offset.toString(16)}:${abi.name.lowercase()}:${typeCount ?: "variable"}",
    )

    override fun immutableSnapshot(): TypeChartTableLayout = this

    override fun equals(other: Any?): Boolean = other is TypeChartTableLayout &&
        offset == other.offset && abi == other.abi && typeCount == other.typeCount

    override fun hashCode(): Int = 31 * (31 * offset.hashCode() + abi.hashCode()) + (typeCount ?: 0)

    override fun toString(): String =
        "TypeChartTableLayout(offset=$offset, abi=$abi, typeCount=$typeCount)"
}

data class TypeChartMatchup(
    val attackingTypeId: Int,
    val defendingTypeId: Int,
    val effectivenessPercent: Int,
)

/** Byte-preserving row evidence; dense tables publish one row per matrix cell. */
data class TypeChartRow(
    val rowIndex: Int,
    val matchup: TypeChartMatchup,
    val encodedMultiplier: Long,
)

class ResolvedTypeChartLayout(
    val table: TypeChartTableLayout,
    rows: Collection<TypeChartRow>,
) : ImmutableDatasetLayout<ResolvedTypeChartLayout> {
    val rows: List<TypeChartRow> = immutableList(rows)
    val matchups: List<TypeChartMatchup> = immutableList(this.rows.map(TypeChartRow::matchup))
    override val layoutIdentity: CandidateLayoutIdentity = table.layoutIdentity

    init {
        require(this.rows.map(TypeChartRow::rowIndex) == this.rows.indices.toList()) {
            "type-chart rows must be complete and index ordered"
        }
    }

    override fun immutableSnapshot(): ResolvedTypeChartLayout = this

    /** Explicit dataset-to-catalog projection boundary. */
    fun catalogMatchups(): List<TypeMatchup> = immutableList(
        rows.asSequence()
            .filterNot { row ->
                table.abi != TypeChartAbi.LEGACY_TRIPLETS && row.encodedMultiplier == Q412_NEUTRAL
            }
            .map { row ->
                TypeMatchup(
                    attackingTypeId = row.matchup.attackingTypeId,
                    defendingTypeId = row.matchup.defendingTypeId,
                    multiplierPercent = row.matchup.effectivenessPercent,
                )
            }
            .toList(),
    )

    override fun equals(other: Any?): Boolean = other is ResolvedTypeChartLayout &&
        table == other.table && rows == other.rows

    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()

    override fun toString(): String = "ResolvedTypeChartLayout(table=$table, rows=$rows)"

    private companion object {
        const val Q412_NEUTRAL = 4096L
    }
}

sealed interface TypeChartTableOutcome {
    val layout: TypeChartTableLayout

    class Decoded(
        override val layout: TypeChartTableLayout,
        rows: Collection<TypeChartRow>,
    ) : TypeChartTableOutcome {
        val rows: List<TypeChartRow> = immutableList(rows)
        val matchups: List<TypeChartMatchup> = immutableList(this.rows.map(TypeChartRow::matchup))

        init {
            require(this.rows.map(TypeChartRow::rowIndex) == this.rows.indices.toList()) {
                "decoded type-chart rows must be complete and index ordered"
            }
        }
    }

    data class Rejected(
        override val layout: TypeChartTableLayout,
        val reason: String,
    ) : TypeChartTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: TypeChartTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : TypeChartTableOutcome
}

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
