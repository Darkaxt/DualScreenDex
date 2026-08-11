package com.enrpau.dualscreendex.parser.analysis

/** Deterministic work limits shared by every resolver in one ROM analysis. */
data class ResolutionLimits(
    val maxDistinctGbaReferenceTargets: Int = 32_768,
    val maxCompiledReferenceSitesPerCandidate: Int = 16,
    val maxProbeRootsPerDataset: Int = 16_384,
    val maxProbeWorkPerDataset: Int = 65_536,
    val maxCandidatesPerDataset: Int = 4_096,
    val maxDatasetExtentBytes: Long = 64L * 1_024 * 1_024,
) {
    init {
        require(maxDistinctGbaReferenceTargets > 0) {
            "GBA reference target budget must be positive"
        }
        require(maxCandidatesPerDataset > 0) {
            "dataset candidate budget must be positive"
        }
        require(maxProbeRootsPerDataset > 0) {
            "dataset probe-root budget must be positive"
        }
        require(maxProbeWorkPerDataset > 0) {
            "dataset probe-work budget must be positive"
        }
        require(maxCompiledReferenceSitesPerCandidate > 0) {
            "compiled reference site budget must be positive"
        }
        require(maxDatasetExtentBytes > 0) {
            "dataset extent budget must be positive"
        }
    }

    /**
     * Validates a fixed-width table using checked [Long] arithmetic. Indexed [Int] values are
     * published only after the complete span is proven to fit both the ROM and the work budget.
     */
    fun checkTableExtent(
        offset: Long,
        count: Long,
        recordSize: Long,
        romSize: Long,
    ): ExtentCheck {
        if (offset < 0 || count <= 0 || recordSize <= 0 || romSize < 0) {
            return ExtentCheck.Invalid(
                "table extent requires non-negative offset/ROM size and positive count/record size",
            )
        }

        val length = try {
            Math.multiplyExact(count, recordSize)
        } catch (_: ArithmeticException) {
            return ExtentCheck.Invalid("table byte length overflows Long")
        }
        if (length > maxDatasetExtentBytes) {
            return ExtentCheck.BudgetExceeded(
                observedBytes = length,
                limitBytes = maxDatasetExtentBytes,
            )
        }

        val endExclusive = try {
            Math.addExact(offset, length)
        } catch (_: ArithmeticException) {
            return ExtentCheck.Invalid("table end offset overflows Long")
        }
        if (endExclusive > romSize) {
            return ExtentCheck.Invalid(
                "table span $offset..<$endExclusive exceeds ROM size $romSize",
            )
        }
        if (offset > Int.MAX_VALUE || length > Int.MAX_VALUE || endExclusive > Int.MAX_VALUE) {
            return ExtentCheck.Invalid("validated table span cannot be represented by indexed Int offsets")
        }

        return ExtentCheck.Valid(
            CheckedRomExtent(
                offset = offset.toInt(),
                length = length.toInt(),
                endExclusive = endExclusive.toInt(),
            ),
        )
    }
}

data class CheckedRomExtent(
    val offset: Int,
    val length: Int,
    val endExclusive: Int,
)

sealed interface ExtentCheck {
    data class Valid(val extent: CheckedRomExtent) : ExtentCheck

    data class Invalid(val reason: String) : ExtentCheck

    data class BudgetExceeded(
        val observedBytes: Long,
        val limitBytes: Long,
    ) : ExtentCheck
}
