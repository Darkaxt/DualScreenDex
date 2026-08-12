package com.enrpau.dualscreendex.parser.dataset.encounters

import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

enum class Gen3EncounterAbi(val headerSize: Int) {
    STANDARD_20(20),
    CLASSIC_24(24),
}

enum class EncounterEnvironment { LAND, WATER }

enum class EncounterTimeWindow { ANY, MORNING, DAY, NIGHT }

enum class EncounterBudgetKind { EXTENT, PROBE_WORK, RETAINED_OUTPUT, EMPTY_FIRST_SHELLS }

data class EncounterDecodeLimits(
    val maxHeadersPerTable: Int = 1_024,
    val maxRetainedBytesPerResolution: Long = 64L * 1_024 * 1_024,
    val maxNegativeProbeCacheEntries: Int = 4_096,
    val maxEmptyFirstShellWalks: Int = 16_384,
) {
    init {
        require(maxHeadersPerTable > 0) { "encounter header-row budget must be positive" }
        require(maxRetainedBytesPerResolution > 0) { "encounter retained-output budget must be positive" }
        require(maxNegativeProbeCacheEntries > 0) { "encounter negative-probe cache budget must be positive" }
        require(maxEmptyFirstShellWalks > 0) { "encounter empty-first shell budget must be positive" }
    }
}

/** Immutable physical interpretation of one sentinel-terminated Gen III encounter table. */
class Gen3EncounterTableLayout(
    val offset: Long,
    val abi: Gen3EncounterAbi,
    val speciesCount: Int,
    val minimumHeaders: Int = 3,
    val minimumPopulatedMethods: Int = 3,
    val maxHeaders: Int = 1_024,
) {
    val identity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "encounter:gen3:${offset.toString(16)}:${abi.name.lowercase()}:" +
            "s$speciesCount:min$minimumHeaders:p$minimumPopulatedMethods:max$maxHeaders",
    )

    init {
        require(offset >= 0) { "encounter table offset must not be negative" }
        require(speciesCount > 0) { "encounter species count must be positive" }
        require(minimumHeaders > 0) { "minimum encounter header count must be positive" }
        require(minimumPopulatedMethods > 0) { "minimum populated-method count must be positive" }
        require(maxHeaders >= minimumHeaders) { "maximum encounter headers must cover the minimum" }
    }

    override fun equals(other: Any?): Boolean = other is Gen3EncounterTableLayout &&
        offset == other.offset && abi == other.abi && speciesCount == other.speciesCount &&
        minimumHeaders == other.minimumHeaders && minimumPopulatedMethods == other.minimumPopulatedMethods &&
        maxHeaders == other.maxHeaders

    override fun hashCode(): Int = identity.hashCode()

    override fun toString(): String = "Gen3EncounterTableLayout(identity=$identity)"
}

data class DecodedEncounterSlot(
    val speciesId: Int,
    val minimumLevel: Int,
    val maximumLevel: Int,
    val weight: Int,
)

class DecodedEncounterMethod(
    val methodId: Int,
    val label: String,
    val encounterRate: Int,
    val environment: EncounterEnvironment?,
    windows: Collection<EncounterTimeWindow>,
    slots: Collection<DecodedEncounterSlot>,
) {
    val windows: Set<EncounterTimeWindow> = immutableSet(windows)
    val slots: List<DecodedEncounterSlot> = immutableList(slots)

    init {
        require(methodId > 0) { "encounter method ID must be positive" }
        require(label.isNotBlank()) { "encounter method label must not be blank" }
        require(encounterRate in 0..100) { "encounter rate/environment byte must be within 0..100" }
        require(this.windows.isNotEmpty()) { "encounter method must publish at least one time window" }
        require(this.slots.isNotEmpty()) { "encounter method must publish at least one slot" }
        require(this.slots.all { it.weight >= 0 }) { "encounter slot weights must not be negative" }
    }

    override fun equals(other: Any?): Boolean = other is DecodedEncounterMethod &&
        methodId == other.methodId && label == other.label && encounterRate == other.encounterRate &&
        environment == other.environment && windows == other.windows && slots == other.slots

    override fun hashCode(): Int {
        var result = methodId
        result = 31 * result + label.hashCode()
        result = 31 * result + encounterRate
        result = 31 * result + (environment?.hashCode() ?: 0)
        result = 31 * result + windows.hashCode()
        result = 31 * result + slots.hashCode()
        return result
    }
}

sealed interface EncounterHeaderOutcome {
    val rowIndex: Int
    val mapGroup: Int
    val mapNumber: Int

    class Decoded(
        override val rowIndex: Int,
        override val mapGroup: Int,
        override val mapNumber: Int,
        methods: Collection<DecodedEncounterMethod>,
    ) : EncounterHeaderOutcome {
        val methods: List<DecodedEncounterMethod> = immutableList(methods)

        init {
            require(rowIndex >= 0)
            require(mapGroup in 0..63 && mapNumber in 0..254)
            require(this.methods.isNotEmpty())
        }

        override fun equals(other: Any?): Boolean = other is Decoded &&
            rowIndex == other.rowIndex && mapGroup == other.mapGroup && mapNumber == other.mapNumber &&
            methods == other.methods

        override fun hashCode(): Int = 31 * (31 * (31 * rowIndex + mapGroup) + mapNumber) + methods.hashCode()
    }

    data class StructuralEmpty(
        override val rowIndex: Int,
        override val mapGroup: Int,
        override val mapNumber: Int,
    ) : EncounterHeaderOutcome {
        init {
            require(rowIndex >= 0)
            require(mapGroup in 0..63 && mapNumber in 0..254)
        }
    }

    class Malformed(
        override val rowIndex: Int,
        override val mapGroup: Int,
        override val mapNumber: Int,
        reasons: Collection<String>,
    ) : EncounterHeaderOutcome {
        val reasons: List<String> = immutableList(reasons.distinct().sorted())

        init {
            require(rowIndex >= 0)
            require(mapGroup in 0..255 && mapNumber in 0..255)
            require(this.reasons.isNotEmpty())
        }

        override fun equals(other: Any?): Boolean = other is Malformed &&
            rowIndex == other.rowIndex && mapGroup == other.mapGroup && mapNumber == other.mapNumber &&
            reasons == other.reasons

        override fun hashCode(): Int = 31 * (31 * (31 * rowIndex + mapGroup) + mapNumber) + reasons.hashCode()
    }
}

class ResolvedEncounterLayout(
    val table: Gen3EncounterTableLayout,
    rows: Collection<EncounterHeaderOutcome>,
) : ImmutableDatasetLayout<ResolvedEncounterLayout> {
    val rows: List<EncounterHeaderOutcome> = immutableList(rows)
    override val layoutIdentity: CandidateLayoutIdentity = table.identity

    init {
        require(this.rows.isNotEmpty()) { "resolved encounter layout must contain header rows" }
        require(this.rows.map(EncounterHeaderOutcome::rowIndex) == this.rows.indices.toList()) {
            "resolved encounter header rows must be complete and index ordered"
        }
        require(this.rows.size <= table.maxHeaders)
    }

    override fun immutableSnapshot(): ResolvedEncounterLayout = this

    override fun equals(other: Any?): Boolean = other is ResolvedEncounterLayout &&
        table == other.table && rows == other.rows

    override fun hashCode(): Int = 31 * table.hashCode() + rows.hashCode()
}

sealed interface EncounterTableOutcome {
    val layout: Gen3EncounterTableLayout

    class Decoded(
        override val layout: Gen3EncounterTableLayout,
        rows: Collection<EncounterHeaderOutcome>,
    ) : EncounterTableOutcome {
        val rows: List<EncounterHeaderOutcome> = immutableList(rows)
    }

    data class Rejected(
        override val layout: Gen3EncounterTableLayout,
        val reason: String,
    ) : EncounterTableOutcome {
        init {
            require(reason.isNotBlank())
        }
    }

    data class BudgetExceeded(
        override val layout: Gen3EncounterTableLayout,
        val budgetKind: EncounterBudgetKind,
        val observed: Long,
        val limit: Long,
        val observationComplete: Boolean,
        val reason: String,
    ) : EncounterTableOutcome {
        init {
            require(observed >= 0 && limit > 0 && reason.isNotBlank())
        }
    }
}

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())

private fun <T> immutableSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
