package com.enrpau.dualscreendex.parser.resolution

import java.util.Collections
import java.util.PriorityQueue

enum class CandidateReasonKind {
    INFORMATION,
    RECOVERY,
    ANOMALY,
}

data class CandidateReason(
    val kind: CandidateReasonKind,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "candidate provenance reason must not be blank" }
    }
}

/** A deterministic, non-truncating set of compiled instruction sites. */
class CompiledReferenceSites private constructor(
    offsets: List<Int>,
    val observedSites: Long,
    val limitSites: Int,
    val overflowReason: String?,
) {
    val offsets: List<Int> = Collections.unmodifiableList(offsets.toList())
    val budgetExceeded: Boolean get() = overflowReason != null

    override fun equals(other: Any?): Boolean = other is CompiledReferenceSites &&
        offsets == other.offsets &&
        observedSites == other.observedSites &&
        limitSites == other.limitSites &&
        overflowReason == other.overflowReason

    override fun hashCode(): Int {
        var result = offsets.hashCode()
        result = 31 * result + observedSites.hashCode()
        result = 31 * result + limitSites
        result = 31 * result + (overflowReason?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "CompiledReferenceSites(" +
        "offsets=$offsets, observedSites=$observedSites, limitSites=$limitSites, " +
        "overflowReason=$overflowReason)"

    companion object {
        private const val DEFAULT_MAX_SITES = 16
        private val EMPTY = CompiledReferenceSites(
            offsets = emptyList(),
            observedSites = 0L,
            limitSites = DEFAULT_MAX_SITES,
            overflowReason = null,
        )

        fun empty(): CompiledReferenceSites = EMPTY

        /** Preserves bounded upstream evidence when the concrete sites were discarded on overflow. */
        fun overflowed(
            observedSites: Long,
            limitSites: Int,
            reason: String,
        ): CompiledReferenceSites {
            require(limitSites > 0) { "compiled reference site budget must be positive" }
            require(observedSites > limitSites.toLong()) {
                "compiled reference site overflow must observe more sites than its limit"
            }
            require(reason.isNotBlank()) { "compiled reference site overflow reason must not be blank" }
            return CompiledReferenceSites(
                offsets = emptyList(),
                observedSites = observedSites,
                limitSites = limitSites,
                overflowReason = reason,
            )
        }

        fun of(offsets: Iterable<Int>, maxSites: Int): CompiledReferenceSites =
            of(offsets.asSequence(), maxSites)

        fun of(offsets: Sequence<Int>, maxSites: Int): CompiledReferenceSites {
            require(maxSites > 0) { "compiled reference site budget must be positive" }
            val distinctSites = linkedSetOf<Int>()
            val iterator = offsets.iterator()
            while (iterator.hasNext()) {
                val site = iterator.next()
                require(site >= 0) { "compiled reference sites must not be negative" }
                if (distinctSites.add(site) && distinctSites.size > maxSites) {
                    return CompiledReferenceSites(
                        offsets = emptyList(),
                        observedSites = distinctSites.size.toLong(),
                        limitSites = maxSites,
                        overflowReason = "compiled reference site budget exceeded " +
                            "(${distinctSites.size} > $maxSites)",
                    )
                }
            }
            val normalized = distinctSites.sorted()
            return if (normalized.isEmpty() && maxSites == DEFAULT_MAX_SITES) {
                EMPTY
            } else {
                CompiledReferenceSites(
                    offsets = normalized,
                    observedSites = normalized.size.toLong(),
                    limitSites = maxSites,
                    overflowReason = null,
                )
            }
        }

        internal fun merge(
            values: Collection<CompiledReferenceSites>,
            maxSites: Int,
        ): CompiledReferenceSites {
            require(maxSites > 0) { "compiled reference site budget must be positive" }
            values.singleOrNull()
                ?.takeIf { it.limitSites == maxSites }
                ?.let { return it }
            val sourceOverflows = values.filter { it.budgetExceeded }
            val known = aggregateKnownSites(values.filterNot { it.budgetExceeded }, maxSites)
            if (sourceOverflows.isNotEmpty()) {
                val sourceObservedLowerBound = sourceOverflows.maxOf { it.observedSites }
                val observedLowerBound = maxOf(sourceObservedLowerBound, known.observedDistinct)
                val sourceReports = sourceOverflows
                    .map { evidence ->
                        "observed-at-least=${evidence.observedSites}, " +
                            "source-limit=${evidence.limitSites}, reason=${evidence.overflowReason}"
                    }
                    .distinct()
                    .sorted()
                return CompiledReferenceSites(
                    offsets = emptyList(),
                    observedSites = observedLowerBound,
                    limitSites = maxSites,
                    overflowReason = "compiled reference sites unavailable during merge: " +
                        "source evidence was previously discarded; observed at least " +
                        "$observedLowerBound distinct site(s) (source lower bound " +
                        "$sourceObservedLowerBound, known complete sites ${known.observedDistinct}); " +
                        "merge limit=$maxSites; source reports=$sourceReports",
                )
            }
            if (known.observedDistinct > maxSites.toLong()) {
                return CompiledReferenceSites(
                    offsets = emptyList(),
                    observedSites = known.observedDistinct,
                    limitSites = maxSites,
                    overflowReason = "compiled reference site budget exceeded while merging evidence " +
                        "(observed ${known.observedDistinct} distinct site(s), limit $maxSites)",
                )
            }
            return CompiledReferenceSites(
                offsets = known.retainedSites,
                observedSites = known.observedDistinct,
                limitSites = maxSites,
                overflowReason = null,
            )
        }

        /** Exact union count with at most one cursor per already-bounded source and [maxSites] retained sites. */
        private fun aggregateKnownSites(
            values: List<CompiledReferenceSites>,
            maxSites: Int,
        ): KnownSiteAggregate {
            val queue = PriorityQueue<SiteCursor>(
                compareBy<SiteCursor>({ it.site }, { it.sourceIndex }, { it.offsetIndex }),
            )
            values.forEachIndexed { sourceIndex, value ->
                value.offsets.firstOrNull()?.let { first ->
                    queue += SiteCursor(sourceIndex, offsetIndex = 0, site = first)
                }
            }
            val retained = ArrayList<Int>(minOf(maxSites, DEFAULT_MAX_SITES))
            var observedDistinct = 0L
            var previous: Int? = null
            while (queue.isNotEmpty()) {
                val cursor = queue.remove()
                if (previous != cursor.site) {
                    observedDistinct++
                    previous = cursor.site
                    if (observedDistinct <= maxSites.toLong()) {
                        retained += cursor.site
                    } else if (observedDistinct == maxSites.toLong() + 1L) {
                        retained.clear()
                    }
                }
                val nextOffsetIndex = cursor.offsetIndex + 1
                values[cursor.sourceIndex].offsets.getOrNull(nextOffsetIndex)?.let { nextSite ->
                    queue += SiteCursor(cursor.sourceIndex, nextOffsetIndex, nextSite)
                }
            }
            return KnownSiteAggregate(
                observedDistinct = observedDistinct,
                retainedSites = retained,
            )
        }

        private data class SiteCursor(
            val sourceIndex: Int,
            val offsetIndex: Int,
            val site: Int,
        )

        private data class KnownSiteAggregate(
            val observedDistinct: Long,
            val retainedSites: List<Int>,
        )
    }
}

/** Typed review and diagnostic evidence carried with a candidate without affecting its rank. */
class CandidateProvenance(
    reasons: Collection<CandidateReason> = emptyList(),
    val validatorReviewRecommended: Boolean = false,
    val compiledReferenceSites: CompiledReferenceSites = CompiledReferenceSites.empty(),
) {
    val reasons: List<CandidateReason> = Collections.unmodifiableList(reasons.toList())

    override fun equals(other: Any?): Boolean = other is CandidateProvenance &&
        reasons == other.reasons &&
        validatorReviewRecommended == other.validatorReviewRecommended &&
        compiledReferenceSites == other.compiledReferenceSites

    override fun hashCode(): Int {
        var result = reasons.hashCode()
        result = 31 * result + validatorReviewRecommended.hashCode()
        result = 31 * result + compiledReferenceSites.hashCode()
        return result
    }

    override fun toString(): String = "CandidateProvenance(" +
        "reasons=$reasons, validatorReviewRecommended=$validatorReviewRecommended, " +
        "compiledReferenceSites=$compiledReferenceSites)"

    companion object {
        internal fun merge(
            values: Collection<CandidateProvenance>,
            maxReferenceSites: Int,
        ): CandidateProvenance {
            val mergedReasons = values
                .flatMap { it.reasons }
                .distinct()
                .sortedWith(compareBy<CandidateReason>({ it.kind.ordinal }, { it.message }))
            return CandidateProvenance(
                reasons = mergedReasons,
                validatorReviewRecommended = values.any { it.validatorReviewRecommended },
                compiledReferenceSites = CompiledReferenceSites.merge(
                    values.map { it.compiledReferenceSites },
                    maxReferenceSites,
                ),
            )
        }
    }
}
