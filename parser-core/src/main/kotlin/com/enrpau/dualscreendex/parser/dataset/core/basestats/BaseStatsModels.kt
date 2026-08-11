package com.enrpau.dualscreendex.parser.dataset.core.basestats

import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

/** The only two ordinary Gen III base-stat ABIs that this unit can interpret. */
enum class BaseStatsAbi(val recordSize: Int) {
    /** Retail `struct BaseStats`: two one-byte ability IDs at offsets 22 and 23. */
    RETAIL_28(28),

    /** Battle Engine ABI: three little-endian u16 ability IDs at offsets 22, 24, and 26. */
    BATTLE_ENGINE_32(32),
}

class BaseStatsTableLayout(
    val offset: Long,
    val count: Long,
    val abi: BaseStatsAbi,
) : ImmutableDatasetLayout<BaseStatsTableLayout> {
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "base-stats:${offset.toString(16)}:$count:${abi.name}",
    )

    override fun immutableSnapshot(): BaseStatsTableLayout = this

    override fun equals(other: Any?): Boolean = this === other ||
        other is BaseStatsTableLayout && offset == other.offset && count == other.count && abi == other.abi

    override fun hashCode(): Int = 31 * (31 * offset.hashCode() + count.hashCode()) + abi.hashCode()

    override fun toString(): String =
        "BaseStatsTableLayout(offset=$offset, count=$count, abi=$abi, layoutIdentity=$layoutIdentity)"
}

/** Independently proven row liveness. No name, root, or ROM-order heuristic is accepted here. */
class BaseStatsSemanticDomain(
    val tableRowCount: Long,
    activeRowIndices: Set<Int>,
) {
    val activeRowIndices: List<Int> = immutableCopy(activeRowIndices.sorted())

    init {
        require(tableRowCount > 0) { "base-stat semantic-domain table cardinality must be positive" }
        require(activeRowIndices.all { it >= 0 && it.toLong() < tableRowCount }) {
            "base-stat active row indices must fit the declared table cardinality"
        }
        require(this.activeRowIndices.isNotEmpty()) {
            "base-stat semantic domain must identify at least one active row"
        }
    }

    val expectedRecords: Int get() = activeRowIndices.size

    override fun equals(other: Any?): Boolean = this === other ||
        other is BaseStatsSemanticDomain &&
        tableRowCount == other.tableRowCount && activeRowIndices == other.activeRowIndices

    override fun hashCode(): Int = 31 * tableRowCount.hashCode() + activeRowIndices.hashCode()

    override fun toString(): String =
        "BaseStatsSemanticDomain(tableRowCount=$tableRowCount, activeRowIndices=$activeRowIndices)"
}

/** Complete decoded fields shared by validation and later materialization. */
class Gen3BaseStatsRecord(
    val stats: BaseStats,
    typeIds: List<Int>,
    val catchRate: Int,
    val baseExperienceYield: Int,
    val evYield: Int,
    heldItemIds: List<Int>,
    val genderRatio: Int,
    val eggCycles: Int,
    val baseFriendship: Int,
    val growthRate: Int,
    eggGroupIds: List<Int>,
    abilityIds: List<Int>,
    val safariZoneFleeRate: Int,
    val bodyColor: Int,
    val noFlip: Boolean,
) {
    val typeIds: List<Int> = immutableCopy(typeIds)
    val heldItemIds: List<Int> = immutableCopy(heldItemIds)
    val eggGroupIds: List<Int> = immutableCopy(eggGroupIds)
    val abilityIds: List<Int> = immutableCopy(abilityIds)

    override fun equals(other: Any?): Boolean = this === other ||
        other is Gen3BaseStatsRecord &&
        stats == other.stats && typeIds == other.typeIds && catchRate == other.catchRate &&
        baseExperienceYield == other.baseExperienceYield && evYield == other.evYield &&
        heldItemIds == other.heldItemIds && genderRatio == other.genderRatio &&
        eggCycles == other.eggCycles && baseFriendship == other.baseFriendship &&
        growthRate == other.growthRate && eggGroupIds == other.eggGroupIds &&
        abilityIds == other.abilityIds && safariZoneFleeRate == other.safariZoneFleeRate &&
        bodyColor == other.bodyColor && noFlip == other.noFlip

    override fun hashCode(): Int {
        var result = stats.hashCode()
        result = 31 * result + typeIds.hashCode()
        result = 31 * result + catchRate
        result = 31 * result + baseExperienceYield
        result = 31 * result + evYield
        result = 31 * result + heldItemIds.hashCode()
        result = 31 * result + genderRatio
        result = 31 * result + eggCycles
        result = 31 * result + baseFriendship
        result = 31 * result + growthRate
        result = 31 * result + eggGroupIds.hashCode()
        result = 31 * result + abilityIds.hashCode()
        result = 31 * result + safariZoneFleeRate
        result = 31 * result + bodyColor
        result = 31 * result + noFlip.hashCode()
        return result
    }

    override fun toString(): String = "Gen3BaseStatsRecord(" +
        "stats=$stats, typeIds=$typeIds, catchRate=$catchRate, " +
        "baseExperienceYield=$baseExperienceYield, evYield=$evYield, " +
        "heldItemIds=$heldItemIds, genderRatio=$genderRatio, eggCycles=$eggCycles, " +
        "baseFriendship=$baseFriendship, growthRate=$growthRate, eggGroupIds=$eggGroupIds, " +
        "abilityIds=$abilityIds, safariZoneFleeRate=$safariZoneFleeRate, " +
        "bodyColor=$bodyColor, noFlip=$noFlip)"
}

sealed interface BaseStatsRowOutcome {
    val rowIndex: Int

    data class Decoded(
        override val rowIndex: Int,
        val record: Gen3BaseStatsRecord,
    ) : BaseStatsRowOutcome

    data class StructuralEmpty(
        override val rowIndex: Int,
    ) : BaseStatsRowOutcome

    class Malformed(
        override val rowIndex: Int,
        reasons: Collection<String>,
    ) : BaseStatsRowOutcome {
        val reasons: List<String> = immutableCopy(reasons.toList())

        init {
            require(this.reasons.isNotEmpty()) { "malformed base-stat rows require a reason" }
        }

        override fun equals(other: Any?): Boolean = this === other ||
            other is Malformed && rowIndex == other.rowIndex && reasons == other.reasons

        override fun hashCode(): Int = 31 * rowIndex + reasons.hashCode()

        override fun toString(): String = "Malformed(rowIndex=$rowIndex, reasons=$reasons)"
    }
}

class ResolvedBaseStatsLayout(
    val table: BaseStatsTableLayout,
    rows: Collection<BaseStatsRowOutcome>,
) : ImmutableDatasetLayout<ResolvedBaseStatsLayout> {
    val rows: List<BaseStatsRowOutcome> = immutableCopy(rows.toList())
    override val layoutIdentity: CandidateLayoutIdentity = table.layoutIdentity

    init {
        require(table.count in 1..Int.MAX_VALUE.toLong()) {
            "resolved base-stat table cardinality must fit indexed row outcomes"
        }
        require(this.rows.size == table.count.toInt()) {
            "resolved base-stat row outcomes must match table cardinality"
        }
        require(this.rows.map { it.rowIndex } == this.rows.indices.toList()) {
            "resolved base-stat row outcomes must be complete and index ordered"
        }
    }

    override fun immutableSnapshot(): ResolvedBaseStatsLayout = this

    override fun equals(other: Any?): Boolean = this === other ||
        other is ResolvedBaseStatsLayout && table == other.table && rows == other.rows

    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()

    override fun toString(): String = "ResolvedBaseStatsLayout(table=$table, rows=$rows)"
}

sealed interface BaseStatsTableOutcome {
    val layout: BaseStatsTableLayout

    class Decoded(
        override val layout: BaseStatsTableLayout,
        rows: Collection<BaseStatsRowOutcome>,
    ) : BaseStatsTableOutcome {
        val rows: List<BaseStatsRowOutcome> = immutableCopy(rows.toList())

        override fun equals(other: Any?): Boolean = this === other ||
            other is Decoded && layout == other.layout && rows == other.rows

        override fun hashCode(): Int = 31 * layout.hashCode() + rows.hashCode()
    }

    data class Rejected(
        override val layout: BaseStatsTableLayout,
        val reason: String,
    ) : BaseStatsTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: BaseStatsTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : BaseStatsTableOutcome
}

private fun <T> immutableCopy(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
