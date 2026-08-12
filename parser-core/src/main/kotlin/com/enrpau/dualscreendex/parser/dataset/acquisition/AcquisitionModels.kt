package com.enrpau.dualscreendex.parser.dataset.acquisition

import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

/** Non-level-up acquisition only; no value here is selected by a LEVEL_UP save selector. */
enum class AcquisitionMethod { EGG, MACHINE, TUTOR }

/** Preserves the proven physical source without inventing a selector between combined tables. */
enum class AcquisitionProvenance { EGG, MACHINE, TUTOR, COMBINED_MACHINE_TUTOR }

data class AcquisitionLink(
    val moveId: Int,
    val method: AcquisitionMethod,
    val sourceId: Int? = null,
    val provenance: AcquisitionProvenance = method.defaultProvenance(),
) {
    init {
        require(provenance.isLegalFor(method)) {
            "acquisition provenance $provenance is not legal for method $method"
        }
    }
}

/** Independently established species and move ID domains. */
class AcquisitionSemanticDomain(
    speciesIds: Set<Int>,
    moveIds: Set<Int>,
) {
    val speciesIds: List<Int> = immutableList(speciesIds.sorted())
    val moveIds: List<Int> = immutableList(moveIds.sorted())
    private val speciesMembership = speciesIds.toHashSet()
    private val moveMembership = moveIds.toHashSet()

    init {
        require(this.speciesIds.isNotEmpty()) { "acquisition species domain must not be empty" }
        require(this.moveIds.isNotEmpty()) { "acquisition move domain must not be empty" }
        require(this.speciesIds.all { it >= 0 }) { "acquisition species IDs must not be negative" }
        require(this.moveIds.all { it > 0 }) { "acquisition move IDs must be positive" }
    }

    fun containsSpecies(id: Int): Boolean = id in speciesMembership
    fun containsMove(id: Int): Boolean = id in moveMembership

    override fun equals(other: Any?): Boolean = other is AcquisitionSemanticDomain &&
        speciesIds == other.speciesIds && moveIds == other.moveIds

    override fun hashCode(): Int = 31 * speciesIds.hashCode() + moveIds.hashCode()
}

/** Byte-level acquisition ABIs that were already characterized by the legacy parser. */
sealed interface AcquisitionAbi {
    val identity: String
    val physicalRoots: List<Long>
    val speciesIdBase: Int

    data class Gen3EggSentinelU16(
        val offset: Long,
        val maxRecords: Int = 8_192,
    ) : AcquisitionAbi {
        init {
            require(maxRecords > 0) { "egg sentinel record cap must be positive" }
        }

        override val identity: String = "g3-egg-u16:${offset.hex()}:$maxRecords"
        override val physicalRoots: List<Long> = immutableList(listOf(offset))
        override val speciesIdBase: Int = 1
    }

    data class GbBankedEggPointersU8(
        val pointerTableOffset: Long,
        val bank: Int,
        val maxMovesPerSpecies: Int = 32,
    ) : AcquisitionAbi {
        init {
            require(bank >= 0) { "GB egg pointer bank must not be negative" }
            require(maxMovesPerSpecies > 0) { "GB egg move cap must be positive" }
        }

        override val identity: String = "gb-egg-ptr:${pointerTableOffset.hex()}:$bank:$maxMovesPerSpecies"
        override val physicalRoots: List<Long> = immutableList(listOf(pointerTableOffset))
        override val speciesIdBase: Int = 1
    }

    data class EmbeddedU8MoveListBitfield(
        val moveListOffset: Long,
        val itemCount: Int,
        val statsOffset: Long,
        val statsRecordSize: Int,
        val flagOffset: Int,
        val flagBytes: Int,
        val firstBit: Int = 0,
        override val speciesIdBase: Int = 1,
    ) : AcquisitionAbi {
        init {
            require(itemCount > 0) { "embedded acquisition item count must be positive" }
            require(statsRecordSize > 0) { "embedded stats stride must be positive" }
            require(flagOffset >= 0 && flagBytes > 0 && firstBit >= 0) {
                "embedded compatibility field must be non-negative and non-empty"
            }
            require(firstBit.toLong() + itemCount.toLong() <= flagBytes.toLong() * 8L) {
                "embedded compatibility bits must fit the declared field"
            }
            require(flagOffset.toLong() + flagBytes.toLong() <= statsRecordSize.toLong()) {
                "embedded compatibility field must fit inside one stats record"
            }
            require(moveListOffset != statsOffset) {
                "embedded move-list and stats roots must be role-distinct"
            }
        }

        override val identity: String = "embedded-u8:${moveListOffset.hex()}:$itemCount:" +
            "${statsOffset.hex()}:$statsRecordSize:$flagOffset:$flagBytes:$firstBit:$speciesIdBase"
        override val physicalRoots: List<Long> = immutableList(listOf(moveListOffset, statsOffset))
    }

    data class Gen3U16MoveListBitfield(
        val moveListOffset: Long,
        val itemCount: Int,
        val compatibilityOffset: Long,
        val rowBytes: Int,
        override val speciesIdBase: Int = 0,
    ) : AcquisitionAbi {
        init {
            require(itemCount > 0) { "Gen III acquisition item count must be positive" }
            require(rowBytes > 0 && itemCount.toLong() <= rowBytes.toLong() * 8L) {
                "Gen III acquisition flags must fit the declared row"
            }
            require(moveListOffset != compatibilityOffset) {
                "Gen III move-list and compatibility roots must be role-distinct"
            }
        }

        override val identity: String = "g3-u16-bits:${moveListOffset.hex()}:$itemCount:" +
            "${compatibilityOffset.hex()}:$rowBytes:$speciesIdBase"
        override val physicalRoots: List<Long> = immutableList(listOf(moveListOffset, compatibilityOffset))
    }

    /** pokeemerald-expansion species-record pointer field used for egg or combined teachable lists. */
    data class GbaRecordPointerMoveListsU16(
        val recordTableOffset: Long,
        val recordStride: Int,
        val pointerFieldOffset: Int,
        val maxMovesPerSpecies: Int = 1_024,
        override val speciesIdBase: Int = 0,
    ) : AcquisitionAbi {
        init {
            require(recordStride > 0 && pointerFieldOffset >= 0) {
                "record-pointer acquisition fields require a positive stride and non-negative field offset"
            }
            require(pointerFieldOffset.toLong() + 4L <= recordStride.toLong()) {
                "record-pointer acquisition field must fit the species record"
            }
            require(maxMovesPerSpecies > 0) { "record-pointer acquisition move cap must be positive" }
        }

        override val identity: String = "g3-record-list:${recordTableOffset.hex()}:$recordStride:" +
            "$pointerFieldOffset:$maxMovesPerSpecies:$speciesIdBase"
        override val physicalRoots: List<Long> = immutableList(listOf(recordTableOffset))
    }

    data class GbaPointerIndexedTutorU8(
        val pointerTableOffset: Long,
        val moveListOffset: Long,
        val tutorCount: Int,
        val maxIndexesPerSpecies: Int = 128,
        override val speciesIdBase: Int = 0,
    ) : AcquisitionAbi {
        init {
            require(tutorCount > 0) { "indexed tutor count must be positive" }
            require(maxIndexesPerSpecies > 0) { "indexed tutor row cap must be positive" }
            require(pointerTableOffset != moveListOffset) {
                "indexed tutor pointer-table and move-list roots must be role-distinct"
            }
        }

        override val identity: String = "g3-tutor-index:${pointerTableOffset.hex()}:" +
            "${moveListOffset.hex()}:$tutorCount:$maxIndexesPerSpecies:$speciesIdBase"
        override val physicalRoots: List<Long> = immutableList(listOf(pointerTableOffset, moveListOffset))
    }
}

class AcquisitionTableLayout(
    val method: AcquisitionMethod,
    val speciesCount: Long,
    val abi: AcquisitionAbi,
) : ImmutableDatasetLayout<AcquisitionTableLayout> {
    init {
        require(speciesCount > 0) { "acquisition species count must be positive" }
    }

    override val layoutIdentity = CandidateLayoutIdentity(
        "acquisition:${method.name}:${speciesCount}:${abi.identity}",
    )

    override fun immutableSnapshot(): AcquisitionTableLayout = this

    override fun equals(other: Any?): Boolean = other is AcquisitionTableLayout &&
        method == other.method && speciesCount == other.speciesCount && abi == other.abi

    override fun hashCode(): Int = 31 * (31 * method.hashCode() + speciesCount.hashCode()) + abi.hashCode()
}

class AcquisitionProbe private constructor(
    val layout: AcquisitionTableLayout,
    val source: CandidateSource,
    internal val directProof: DirectAcquisitionProof?,
) {
    constructor(layout: AcquisitionTableLayout, source: CandidateSource) : this(layout, source, null)

    val directSitesByRoot: Map<Long, List<Int>> = directProof?.sitesByRoot ?: emptyMap()
    val directInstructionSites: List<Int> = directProof?.instructionSites ?: emptyList()

    init {
        require(source != CandidateSource.EXACT_PROFILE) {
            "exact profiles do not publish acquisition layout metadata"
        }
        if (source == CandidateSource.DIRECT_COMPILED_CONSUMER) {
            requireNotNull(directProof) { "direct acquisition candidates require verified proof" }
            require(directProof.matchesLayout(layout)) { "direct acquisition proof is bound to another layout" }
        } else {
            require(directProof == null) { "only direct acquisition candidates may supply verified proof" }
        }
    }

    companion object {
        fun direct(
            layout: AcquisitionTableLayout,
            proof: DirectAcquisitionProof,
        ): AcquisitionProbe = AcquisitionProbe(
            layout,
            CandidateSource.DIRECT_COMPILED_CONSUMER,
            proof,
        )
    }
}

sealed interface AcquisitionRowOutcome {
    val speciesId: Int

    class Decoded(
        override val speciesId: Int,
        links: Collection<AcquisitionLink>,
    ) : AcquisitionRowOutcome {
        val links: List<AcquisitionLink> = immutableList(links.distinct())

        init {
            require(this.links.isNotEmpty()) { "decoded acquisition rows must have at least one link" }
        }

        override fun equals(other: Any?): Boolean = other is Decoded &&
            speciesId == other.speciesId && links == other.links

        override fun hashCode(): Int = 31 * speciesId + links.hashCode()
    }

    data class StructuralEmpty(override val speciesId: Int) : AcquisitionRowOutcome

    class Malformed(
        override val speciesId: Int,
        reasons: Collection<String>,
    ) : AcquisitionRowOutcome {
        val reasons: List<String> = immutableList(reasons.distinct().sorted())

        init {
            require(this.reasons.isNotEmpty() && this.reasons.all { it.isNotBlank() }) {
                "malformed acquisition rows require non-blank reasons"
            }
        }

        override fun equals(other: Any?): Boolean = other is Malformed &&
            speciesId == other.speciesId && reasons == other.reasons

        override fun hashCode(): Int = 31 * speciesId + reasons.hashCode()
    }
}

class ResolvedAcquisitionLayout(
    val table: AcquisitionTableLayout,
    rows: Collection<AcquisitionRowOutcome>,
) : ImmutableDatasetLayout<ResolvedAcquisitionLayout> {
    val rows: List<AcquisitionRowOutcome> = immutableList(rows.toList())
    val acquisitionsBySpecies: Map<Int, List<AcquisitionLink>> = immutableMap(
        linkedMapOf<Int, List<AcquisitionLink>>().apply {
            this@ResolvedAcquisitionLayout.rows.forEach { row ->
                if (row is AcquisitionRowOutcome.Decoded) put(row.speciesId, row.links)
            }
        },
    )
    val provenanceKinds: Set<AcquisitionProvenance> = immutableSet(
        acquisitionsBySpecies.values.asSequence().flatten().map(AcquisitionLink::provenance).toSet(),
    )
    override val layoutIdentity: CandidateLayoutIdentity = table.layoutIdentity

    init {
        require(table.speciesCount in 1..Int.MAX_VALUE.toLong()) {
            "resolved acquisition cardinality must fit indexed rows"
        }
        require(this.rows.size == table.speciesCount.toInt()) {
            "resolved acquisition rows must match the declared species count"
        }
        require(this.rows.withIndex().all { (index, row) ->
            row.speciesId == table.abi.speciesIdBase + index
        }) {
            "resolved acquisition species rows must be complete and ID ordered"
        }
        val links = this.rows.asSequence()
            .filterIsInstance<AcquisitionRowOutcome.Decoded>()
            .flatMap { it.links.asSequence() }
            .toList()
        require(links.all { it.method == table.method }) {
            "resolved acquisition links must match the table method"
        }
        val expectedProvenance = if (
            table.method == AcquisitionMethod.MACHINE &&
            table.abi is AcquisitionAbi.GbaRecordPointerMoveListsU16
        ) {
            AcquisitionProvenance.COMBINED_MACHINE_TUTOR
        } else {
            table.method.defaultProvenance()
        }
        require(links.all { it.provenance == expectedProvenance }) {
            "resolved acquisition link provenance must match the proven table ABI"
        }
    }

    override fun immutableSnapshot(): ResolvedAcquisitionLayout = this

    override fun equals(other: Any?): Boolean = other is ResolvedAcquisitionLayout &&
        table == other.table && rows == other.rows

    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()
}

sealed interface AcquisitionTableOutcome {
    val layout: AcquisitionTableLayout

    data class Decoded(
        override val layout: AcquisitionTableLayout,
        val resolved: ResolvedAcquisitionLayout,
    ) : AcquisitionTableOutcome

    data class Rejected(
        override val layout: AcquisitionTableLayout,
        val reason: String,
    ) : AcquisitionTableOutcome

    data class ExtentBudgetExceeded(
        override val layout: AcquisitionTableLayout,
        val observedBytes: Long,
        val limitBytes: Long,
        val reason: String,
    ) : AcquisitionTableOutcome

    data class WorkBudgetExceeded(
        override val layout: AcquisitionTableLayout,
        val observedWork: Long,
        val limitWork: Long,
        val reason: String,
    ) : AcquisitionTableOutcome
}

class AcquisitionProjection internal constructor(
    val method: AcquisitionMethod,
    acquisitionsBySpecies: Map<Int, List<AcquisitionLink>>,
    provenanceKinds: Set<AcquisitionProvenance>,
) {
    val acquisitionsBySpecies: Map<Int, List<AcquisitionLink>> = immutableMap(
        acquisitionsBySpecies.mapValues { (_, links) -> immutableList(links) },
    )
    val provenanceKinds: Set<AcquisitionProvenance> = immutableSet(provenanceKinds)
}

object MoveAcquisitionProjection {
    fun project(layout: ResolvedAcquisitionLayout): AcquisitionProjection = AcquisitionProjection(
        layout.table.method,
        layout.acquisitionsBySpecies,
        layout.provenanceKinds,
    )
}

private fun AcquisitionMethod.defaultProvenance(): AcquisitionProvenance = when (this) {
    AcquisitionMethod.EGG -> AcquisitionProvenance.EGG
    AcquisitionMethod.MACHINE -> AcquisitionProvenance.MACHINE
    AcquisitionMethod.TUTOR -> AcquisitionProvenance.TUTOR
}

private fun AcquisitionProvenance.isLegalFor(method: AcquisitionMethod): Boolean = when (method) {
    AcquisitionMethod.EGG -> this == AcquisitionProvenance.EGG
    AcquisitionMethod.MACHINE -> this == AcquisitionProvenance.MACHINE ||
        this == AcquisitionProvenance.COMBINED_MACHINE_TUTOR
    AcquisitionMethod.TUTOR -> this == AcquisitionProvenance.TUTOR
}

internal fun AcquisitionMethod.datasetKind() = when (this) {
    AcquisitionMethod.EGG -> com.enrpau.dualscreendex.parser.resolution.DatasetKind.EGG_MOVES
    AcquisitionMethod.MACHINE -> com.enrpau.dualscreendex.parser.resolution.DatasetKind.MACHINE_MOVES
    AcquisitionMethod.TUTOR -> com.enrpau.dualscreendex.parser.resolution.DatasetKind.TUTOR_MOVES
}

private fun Long.hex(): String = if (this >= 0) toString(16) else "neg${-this}"

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())

private fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))

private fun <T> immutableSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
