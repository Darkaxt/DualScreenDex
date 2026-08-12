package com.enrpau.dualscreendex.parser.resolution

import java.util.Collections

enum class DatasetKind {
    SPECIES_NAMES,
    BASE_STATS,
    MOVE_NAMES,
    MOVE_DATA,
    MOVE_DESCRIPTIONS,
    TYPE_CHART,
    TYPE_PRESENTATION,
    EVOLUTIONS,
    LEVEL_UP_LEARNSETS,
    EGG_MOVES,
    MACHINE_MOVES,
    TUTOR_MOVES,
    SPRITES,
    POKEDEX_DESCRIPTIONS,
    ABILITIES,
    ABILITY_DESCRIPTIONS,
    ABILITY_MECHANICS,
    AREA_ENCOUNTERS,
    BALL_CATALOG,
}

enum class CandidateSource {
    EXACT_PROFILE,
    DIRECT_COMPILED_CONSUMER,
    PUBLISHED_HEADER,
    COMPILED_REFERENCE,
    INHERITED_FAMILY_LAYOUT,
    STRUCTURAL_ANCHOR,
}

enum class BudgetKind {
    CANDIDATES,
    PROBE_ROOTS,
    PROBE_WORK,
    REFERENCE_TARGETS,
    REFERENCE_SITES,
    EXTENT,
}

@JvmInline
value class CandidateIdentity(val value: String) {
    init {
        require(value.isNotBlank()) { "candidate identity must not be blank" }
        require(value.all { it.code in 0x21..0x7E }) {
            "candidate identity must contain stable printable ASCII without whitespace"
        }
    }

    override fun toString(): String = value
}

@JvmInline
value class CandidateLayoutIdentity(val value: String) {
    init {
        require(value.isNotBlank()) { "candidate layout identity must not be blank" }
        require(value.all { it.code in 0x21..0x7E }) {
            "candidate layout identity must contain stable printable ASCII without whitespace"
        }
    }

    override fun toString(): String = value
}

/**
 * Dataset layouts publish their own stable identity and provide an immutable value snapshot.
 * Implementations must not expose caller-owned mutable arrays, collections, or maps.
 */
interface ImmutableDatasetLayout<TSelf : ImmutableDatasetLayout<TSelf>> {
    val layoutIdentity: CandidateLayoutIdentity

    fun immutableSnapshot(): TSelf
}

data class EvidenceCoverage(
    val covered: Int,
    val expected: Int,
) : Comparable<EvidenceCoverage> {
    init {
        require(expected > 0) { "coverage expectation must be positive" }
        require(covered in 0..expected) { "coverage must be within 0..expected" }
    }

    override fun compareTo(other: EvidenceCoverage): Int =
        (covered.toLong() * other.expected.toLong())
            .compareTo(other.covered.toLong() * expected.toLong())
}

/** Substantive evidence only; diagnostic offsets and discovery order deliberately live elsewhere. */
data class CandidateStrength(
    val semanticCoverage: EvidenceCoverage? = null,
    val structuralCoverage: EvidenceCoverage,
    val compiledReferenceCount: Int = 0,
    val datasetQuality: Int = 0,
) {
    init {
        require(compiledReferenceCount >= 0) { "compiled reference count must not be negative" }
        require(datasetQuality >= 0) { "dataset-specific quality must not be negative" }
    }
}

class DatasetCandidate<TLayout : ImmutableDatasetLayout<TLayout>>(
    val identity: CandidateIdentity,
    val kind: DatasetKind,
    layout: TLayout,
    val source: CandidateSource,
    val strength: CandidateStrength,
    val diagnosticOffset: Int? = null,
    val diagnosticLabel: String,
    val eligibility: CandidateEligibility,
    val provenance: CandidateProvenance = CandidateProvenance(),
) {
    private val sourceLayoutIdentity: CandidateLayoutIdentity = layout.layoutIdentity
    val layout: TLayout = layout.immutableSnapshot()
    val layoutIdentity: CandidateLayoutIdentity = this.layout.layoutIdentity

    init {
        require(layoutIdentity == sourceLayoutIdentity) {
            "immutable layout snapshot changed its stable layout identity"
        }
        require(diagnosticOffset == null || diagnosticOffset >= 0) {
            "diagnostic offset must not be negative"
        }
        require(diagnosticLabel.isNotBlank()) { "candidate diagnostic label must not be blank" }
        if (eligibility is CandidateEligibility.Validated) {
            require(eligibility.source == source) {
                "validated evidence source ${eligibility.source} does not match candidate source $source"
            }
            require(
                (source == CandidateSource.EXACT_PROFILE) == (eligibility.exactProfileIdentity != null),
            ) {
                "exact-profile candidates require session-derived exact identity evidence"
            }
        }
    }

    internal fun withProvenance(value: CandidateProvenance): DatasetCandidate<TLayout> = DatasetCandidate(
        identity = identity,
        kind = kind,
        layout = layout,
        source = source,
        strength = strength,
        diagnosticOffset = diagnosticOffset,
        diagnosticLabel = diagnosticLabel,
        eligibility = eligibility,
        provenance = value,
    )

    override fun equals(other: Any?): Boolean = other is DatasetCandidate<*> &&
        identity == other.identity &&
        layoutIdentity == other.layoutIdentity &&
        kind == other.kind &&
        source == other.source &&
        strength == other.strength &&
        eligibility == other.eligibility &&
        provenance == other.provenance

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + layoutIdentity.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + strength.hashCode()
        result = 31 * result + eligibility.hashCode()
        result = 31 * result + provenance.hashCode()
        return result
    }

    override fun toString(): String = "DatasetCandidate(" +
        "identity=$identity, layoutIdentity=$layoutIdentity, kind=$kind, source=$source, " +
        "strength=$strength, eligibility=$eligibility, provenance=$provenance)"
}

sealed interface DatasetResolution<out TLayout> {
    val kind: DatasetKind

    data class Resolved<TLayout : ImmutableDatasetLayout<TLayout>>(
        override val kind: DatasetKind,
        val candidate: DatasetCandidate<TLayout>,
    ) : DatasetResolution<TLayout>

    class Partial<TLayout : ImmutableDatasetLayout<TLayout>> internal constructor(
        override val kind: DatasetKind,
        val candidate: DatasetCandidate<TLayout>,
        reasons: Collection<String>,
    ) : DatasetResolution<TLayout> {
        val reasons: List<String> = immutableStrings(reasons)

        override fun equals(other: Any?): Boolean = other is Partial<*> &&
            kind == other.kind && candidate == other.candidate && reasons == other.reasons

        override fun hashCode(): Int = 31 * (31 * kind.hashCode() + candidate.hashCode()) + reasons.hashCode()

        override fun toString(): String = "Partial(kind=$kind, candidate=$candidate, reasons=$reasons)"
    }

    class Ambiguous<TLayout : ImmutableDatasetLayout<TLayout>> internal constructor(
        override val kind: DatasetKind,
        candidates: Collection<DatasetCandidate<TLayout>>,
    ) : DatasetResolution<TLayout> {
        val candidates: List<DatasetCandidate<TLayout>> = Collections.unmodifiableList(candidates.toList())

        override fun equals(other: Any?): Boolean = other is Ambiguous<*> &&
            kind == other.kind && candidates == other.candidates

        override fun hashCode(): Int = 31 * kind.hashCode() + candidates.hashCode()

        override fun toString(): String = "Ambiguous(kind=$kind, candidates=$candidates)"
    }

    class Unavailable<TLayout>(
        override val kind: DatasetKind,
        val observedCandidates: Int,
        reasons: Collection<String>,
    ) : DatasetResolution<TLayout> {
        constructor(
            kind: DatasetKind,
            observedCandidates: Int,
            reason: String,
        ) : this(kind, observedCandidates, listOf(reason))

        val reasons: List<String> = immutableStrings(reasons)
        val reason: String get() = reasons.joinToString("; ")

        init {
            require(observedCandidates >= 0) { "observed candidate count must not be negative" }
            require(this.reasons.isNotEmpty()) { "unavailable resolution requires at least one reason" }
        }

        override fun equals(other: Any?): Boolean = other is Unavailable<*> &&
            kind == other.kind && observedCandidates == other.observedCandidates && reasons == other.reasons

        override fun hashCode(): Int = 31 * (31 * kind.hashCode() + observedCandidates) + reasons.hashCode()

        override fun toString(): String = "Unavailable(" +
            "kind=$kind, observedCandidates=$observedCandidates, reasons=$reasons)"
    }

    data class BudgetExceeded<TLayout>(
        override val kind: DatasetKind,
        val budgetKind: BudgetKind,
        val observed: Long,
        val limit: Long,
        val observationComplete: Boolean,
        val reason: String,
    ) : DatasetResolution<TLayout> {
        init {
            require(observed >= 0) { "observed budget work must not be negative" }
            require(limit > 0) { "budget limit must be positive" }
            require(reason.isNotBlank()) { "budget exhaustion reason must not be blank" }
        }
    }
}

private fun immutableStrings(values: Collection<String>): List<String> {
    require(values.all { it.isNotBlank() }) { "resolution reasons must not be blank" }
    return Collections.unmodifiableList(values.distinct().sorted())
}
