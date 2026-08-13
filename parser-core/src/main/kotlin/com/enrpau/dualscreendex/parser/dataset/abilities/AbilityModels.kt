package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.dataset.core.basestats.Gen3BaseStatsRecord
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.AttackMechanic
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleMechanicsAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.RetailBattleMechanicsProof
import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

/** Fixed-width name field, optionally embedded in a larger direct ability record. */
class AbilityNameTableLayout(
    val offset: Long,
    val count: Long,
    val nameWidth: Int,
    val stride: Int = nameWidth,
    val nameOffset: Int = 0,
) : ImmutableDatasetLayout<AbilityNameTableLayout> {
    constructor(
        offset: Int,
        count: Int,
        nameWidth: Int,
        stride: Int = nameWidth,
        nameOffset: Int = 0,
    ) : this(offset.toLong(), count.toLong(), nameWidth, stride, nameOffset)

    constructor(
        offset: Int,
        count: Long,
        nameWidth: Int,
        stride: Int = nameWidth,
        nameOffset: Int = 0,
    ) : this(offset.toLong(), count, nameWidth, stride, nameOffset)

    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "ability-names:${offset.toString(16)}:$count:$nameWidth:$stride:$nameOffset",
    )

    init {
        require(offset >= 0) { "ability-name offset must not be negative" }
        require(count > 1) { "ability-name table must contain the none slot and at least one ability" }
        require(nameWidth > 0) { "ability-name width must be positive" }
        require(stride >= nameOffset + nameWidth) {
            "ability-name stride must contain the complete name field"
        }
        require(nameOffset >= 0) { "ability-name field offset must not be negative" }
    }

    override fun immutableSnapshot(): AbilityNameTableLayout = this

    override fun equals(other: Any?): Boolean = this === other ||
        other is AbilityNameTableLayout && offset == other.offset && count == other.count &&
        nameWidth == other.nameWidth && stride == other.stride && nameOffset == other.nameOffset

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + count.hashCode()
        result = 31 * result + nameWidth
        result = 31 * result + stride
        result = 31 * result + nameOffset
        return result
    }

    override fun toString(): String = "AbilityNameTableLayout(" +
        "offset=$offset, count=$count, nameWidth=$nameWidth, stride=$stride, nameOffset=$nameOffset)"
}

/** Ability IDs proven live by decoded base-stat records, never by species-name plausibility. */
class AbilitySemanticDomain(activeAbilityIds: Set<Int>) {
    val activeAbilityIds: List<Int> = immutable(activeAbilityIds.filter { it > 0 }.distinct().sorted())
    val maximumDirectAbilityId: Int = this.activeAbilityIds.maxOrNull() ?: 0

    override fun equals(other: Any?): Boolean =
        other is AbilitySemanticDomain && activeAbilityIds == other.activeAbilityIds

    override fun hashCode(): Int = activeAbilityIds.hashCode()

    override fun toString(): String = "AbilitySemanticDomain(activeAbilityIds=$activeAbilityIds)"

    companion object {
        fun fromDecodedBaseStats(records: Collection<Gen3BaseStatsRecord>): AbilitySemanticDomain =
            AbilitySemanticDomain(records.asSequence().flatMap { it.abilityIds.asSequence() }.toSet())
    }
}

sealed interface AbilityNameRowOutcome {
    val rowIndex: Int

    data class Decoded(
        override val rowIndex: Int,
        val name: String,
    ) : AbilityNameRowOutcome

    data class StructuralSentinel(
        override val rowIndex: Int,
        val value: String,
    ) : AbilityNameRowOutcome

    class Malformed(
        override val rowIndex: Int,
        reasons: Collection<String>,
    ) : AbilityNameRowOutcome {
        val reasons: List<String> = immutable(reasons.distinct().sorted())

        init {
            require(this.reasons.isNotEmpty()) { "malformed ability names require a reason" }
        }

        override fun equals(other: Any?): Boolean =
            other is Malformed && rowIndex == other.rowIndex && reasons == other.reasons

        override fun hashCode(): Int = 31 * rowIndex + reasons.hashCode()

        override fun toString(): String = "Malformed(rowIndex=$rowIndex, reasons=$reasons)"
    }
}

data class AbilityAliasLabel(
    val sourceRowIndex: Int,
    val label: String,
)

/** Full row evidence plus the direct-ID prefix and separately reported runtime alias labels. */
class ResolvedAbilityNameLayout(
    val table: AbilityNameTableLayout,
    rows: Collection<AbilityNameRowOutcome>,
    val baseRowCount: Int,
    aliasLabels: Collection<AbilityAliasLabel>,
) : ImmutableDatasetLayout<ResolvedAbilityNameLayout> {
    val rows: List<AbilityNameRowOutcome> = immutable(rows.toList())
    val baseRows: List<AbilityNameRowOutcome> = immutable(this.rows.take(baseRowCount))
    val aliasLabels: List<AbilityAliasLabel> = immutable(aliasLabels.toList())
    val baseAbilityCount: Int get() = baseRowCount - 1
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        table.layoutIdentity.value + ":base=$baseRowCount:aliases=" +
            this.aliasLabels.joinToString(",") { "${it.sourceRowIndex}" },
    )

    init {
        require(table.count in 2..Int.MAX_VALUE.toLong()) {
            "resolved ability-name cardinality must fit indexed row outcomes"
        }
        require(this.rows.size == table.count.toInt()) {
            "ability-name row outcomes must match table cardinality"
        }
        require(this.rows.map { it.rowIndex } == this.rows.indices.toList()) {
            "ability-name row outcomes must be complete and index ordered"
        }
        require(baseRowCount in 2..this.rows.size) {
            "direct ability prefix must contain the none slot and at least one ability"
        }
        require(this.aliasLabels.map { it.sourceRowIndex }.distinct().size == this.aliasLabels.size) {
            "ability alias-label source rows must be unique"
        }
        require(this.aliasLabels.all { it.sourceRowIndex > baseRowCount }) {
            "ability alias labels must follow the excluded post-catalog sentinel"
        }
    }

    override fun immutableSnapshot(): ResolvedAbilityNameLayout = this

    fun catalogAbilities(): Map<Int, AbilityRecord> = Collections.unmodifiableMap(
        baseRows.mapNotNull { row ->
            val name = (row as? AbilityNameRowOutcome.Decoded)?.name ?: return@mapNotNull null
            row.rowIndex to AbilityRecord(row.rowIndex, CatalogField.available(name))
        }.toMap(),
    )

    fun decodedDirectAbilityIds(): Set<Int> = Collections.unmodifiableSet(
        baseRows.filterIsInstance<AbilityNameRowOutcome.Decoded>().mapTo(linkedSetOf()) { it.rowIndex },
    )

    override fun equals(other: Any?): Boolean = this === other ||
        other is ResolvedAbilityNameLayout && table == other.table && rows == other.rows &&
        baseRowCount == other.baseRowCount && aliasLabels == other.aliasLabels

    override fun hashCode(): Int {
        var result = table.hashCode()
        result = 31 * result + rows.hashCode()
        result = 31 * result + baseRowCount
        result = 31 * result + aliasLabels.hashCode()
        return result
    }
}

sealed interface AbilityNameTableOutcome {
    val layout: AbilityNameTableLayout

    data class Decoded(
        override val layout: AbilityNameTableLayout,
        val resolved: ResolvedAbilityNameLayout,
    ) : AbilityNameTableOutcome

    data class Rejected(
        override val layout: AbilityNameTableLayout,
        val reason: String,
    ) : AbilityNameTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: AbilityNameTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : AbilityNameTableOutcome
}

/** One pointer field per base ability ID, either standalone or embedded in a direct record. */
class AbilityDescriptionTableLayout(
    val offset: Long,
    val count: Long,
    val recordStride: Int = 4,
    val pointerOffset: Int = 0,
) : ImmutableDatasetLayout<AbilityDescriptionTableLayout> {
    constructor(
        offset: Int,
        count: Int,
        recordStride: Int = 4,
        pointerOffset: Int = 0,
    ) : this(offset.toLong(), count.toLong(), recordStride, pointerOffset)

    constructor(
        offset: Int,
        count: Long,
        recordStride: Int = 4,
        pointerOffset: Int = 0,
    ) : this(offset.toLong(), count, recordStride, pointerOffset)

    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "ability-descriptions:${offset.toString(16)}:$count:$recordStride:$pointerOffset",
    )

    init {
        require(offset >= 0) { "ability-description offset must not be negative" }
        require(count > 1) { "ability-description table must include at least one base ability" }
        require(pointerOffset >= 0) { "ability-description pointer offset must not be negative" }
        require(recordStride >= pointerOffset + 4) {
            "ability-description record stride must contain the complete pointer"
        }
    }

    override fun immutableSnapshot(): AbilityDescriptionTableLayout = this

    override fun equals(other: Any?): Boolean = this === other ||
        other is AbilityDescriptionTableLayout && offset == other.offset && count == other.count &&
        recordStride == other.recordStride && pointerOffset == other.pointerOffset

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + count.hashCode()
        result = 31 * result + recordStride
        result = 31 * result + pointerOffset
        return result
    }

    override fun toString(): String = "AbilityDescriptionTableLayout(" +
        "offset=$offset, count=$count, recordStride=$recordStride, pointerOffset=$pointerOffset)"
}

sealed interface AbilityDescriptionRowOutcome {
    val rowIndex: Int

    data class Decoded(
        override val rowIndex: Int,
        val description: String,
    ) : AbilityDescriptionRowOutcome

    data class MissingProse(
        override val rowIndex: Int,
        val placeholder: String,
    ) : AbilityDescriptionRowOutcome

    class Malformed(
        override val rowIndex: Int,
        reasons: Collection<String>,
    ) : AbilityDescriptionRowOutcome {
        val reasons: List<String> = immutable(reasons.distinct().sorted())

        init {
            require(this.reasons.isNotEmpty()) { "malformed ability descriptions require a reason" }
        }

        override fun equals(other: Any?): Boolean =
            other is Malformed && rowIndex == other.rowIndex && reasons == other.reasons

        override fun hashCode(): Int = 31 * rowIndex + reasons.hashCode()

        override fun toString(): String = "Malformed(rowIndex=$rowIndex, reasons=$reasons)"
    }
}

class ResolvedAbilityDescriptionLayout(
    val table: AbilityDescriptionTableLayout,
    rows: Collection<AbilityDescriptionRowOutcome>,
) : ImmutableDatasetLayout<ResolvedAbilityDescriptionLayout> {
    val rows: List<AbilityDescriptionRowOutcome> = immutable(rows.toList())
    override val layoutIdentity: CandidateLayoutIdentity = table.layoutIdentity

    init {
        require(table.count in 2..Int.MAX_VALUE.toLong()) {
            "resolved ability-description cardinality must fit indexed rows"
        }
        require(this.rows.size == table.count.toInt()) {
            "ability-description row outcomes must match table cardinality"
        }
        require(this.rows.map { it.rowIndex } == this.rows.indices.toList()) {
            "ability-description row outcomes must be complete and index ordered"
        }
    }

    override fun immutableSnapshot(): ResolvedAbilityDescriptionLayout = this

    override fun equals(other: Any?): Boolean =
        other is ResolvedAbilityDescriptionLayout && table == other.table && rows == other.rows

    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()
}

sealed interface AbilityDescriptionTableOutcome {
    val layout: AbilityDescriptionTableLayout

    class Decoded(
        override val layout: AbilityDescriptionTableLayout,
        rows: Collection<AbilityDescriptionRowOutcome>,
    ) : AbilityDescriptionTableOutcome {
        val rows: List<AbilityDescriptionRowOutcome> = immutable(rows.toList())
    }

    data class Rejected(
        override val layout: AbilityDescriptionTableLayout,
        val reason: String,
    ) : AbilityDescriptionTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: AbilityDescriptionTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : AbilityDescriptionTableOutcome
}

/** Complete parser-selected ARM7TDMI mechanic proof propagated to catalog consumers. */
class ResolvedAbilityMechanicsLayout(
    val routineEntry: Int,
    val abi: BattleMechanicsAbi,
    mechanics: Collection<AttackMechanic>,
    proof: RetailBattleMechanicsProof,
) : ImmutableDatasetLayout<ResolvedAbilityMechanicsLayout> {
    val mechanics: List<AttackMechanic> = Collections.unmodifiableList(mechanics.map { mechanic ->
        mechanic.copy(predicates = Collections.unmodifiableSet(mechanic.predicates.toSet()))
    })
    val proof: RetailBattleMechanicsProof = proof.copy(
        decodedCallSites = Collections.unmodifiableList(proof.decodedCallSites.toList()),
        callerEvidence = Collections.unmodifiableList(proof.callerEvidence.toList()),
        moveTableReferenceSites = Collections.unmodifiableList(proof.moveTableReferenceSites.toList()),
        literalVeneerSites = Collections.unmodifiableList(proof.literalVeneerSites.toList()),
    )
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "ability-mechanics:arm7tdmi:${routineEntry.toString(16)}:${abi.record.stride}:${this.mechanics.size}",
    )

    init {
        require(routineEntry >= 0) { "ability-mechanics routine entry must not be negative" }
        require(this.mechanics.isNotEmpty()) { "ability-mechanics proof must emit at least one mechanic" }
    }

    override fun immutableSnapshot(): ResolvedAbilityMechanicsLayout = this

    override fun equals(other: Any?): Boolean = other is ResolvedAbilityMechanicsLayout &&
        routineEntry == other.routineEntry && abi == other.abi && mechanics == other.mechanics && proof == other.proof

    override fun hashCode(): Int {
        var result = routineEntry
        result = 31 * result + abi.hashCode()
        result = 31 * result + mechanics.hashCode()
        result = 31 * result + proof.hashCode()
        return result
    }
}

private fun <T> immutable(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
