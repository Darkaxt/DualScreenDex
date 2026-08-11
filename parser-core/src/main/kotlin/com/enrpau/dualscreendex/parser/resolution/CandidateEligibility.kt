package com.enrpau.dualscreendex.parser.resolution

import com.enrpau.dualscreendex.parser.analysis.ExactProfileIdentity
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession

sealed interface CandidateEligibility {
    class Validated internal constructor(
        val source: CandidateSource,
        val exactProfileIdentity: ExactProfileIdentity? = null,
    ) : CandidateEligibility {
        override fun equals(other: Any?): Boolean = other is Validated &&
            source == other.source && exactProfileIdentity == other.exactProfileIdentity

        override fun hashCode(): Int = 31 * source.hashCode() + (exactProfileIdentity?.hashCode() ?: 0)

        override fun toString(): String = "Validated(" +
            "source=$source, exactProfileIdentity=$exactProfileIdentity)"
    }

    data class Ineligible(val reason: String) : CandidateEligibility {
        init {
            require(reason.isNotBlank()) { "candidate ineligibility reason must not be blank" }
        }
    }

    companion object {
        fun validated(source: CandidateSource): Validated {
            require(source != CandidateSource.EXACT_PROFILE) {
                "exact-profile eligibility must be derived from the matching ROM analysis session"
            }
            return Validated(source)
        }

        internal fun exact(identity: ExactProfileIdentity): Validated = Validated(
            source = CandidateSource.EXACT_PROFILE,
            exactProfileIdentity = identity,
        )
    }
}

fun RomAnalysisSession.exactProfileEligibility(): CandidateEligibility = exactProfileIdentity
    ?.let(CandidateEligibility::exact)
    ?: CandidateEligibility.Ineligible("analysis session has no matching exact profile identity")
