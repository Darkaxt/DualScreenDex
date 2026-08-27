package com.enrpau.dualscreendex.parser.analysis

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import java.util.Collections

enum class GbaSiteEvidenceStatus {
    COMPLETE,
    COUNTS_ONLY_INCOMPLETE,
    REFERENCE_SITES_INCOMPLETE,
    REFERENCE_TARGETS_INCOMPLETE,
}

class GbaTargetReferenceEvidence internal constructor(
    val count: Int,
    instructionSites: List<Int>,
    val observedSites: Int,
    val limitSites: Int,
    val overflowReason: String?,
    val siteEvidenceUnavailableReason: String? = overflowReason,
) {
    val instructionSites: List<Int> = Collections.unmodifiableList(instructionSites.toList())
    val siteBudgetExceeded: Boolean get() = overflowReason != null
    val siteEvidenceAvailable: Boolean get() = siteEvidenceUnavailableReason == null

    override fun equals(other: Any?): Boolean = other is GbaTargetReferenceEvidence &&
        count == other.count &&
        instructionSites == other.instructionSites &&
        observedSites == other.observedSites &&
        limitSites == other.limitSites &&
        overflowReason == other.overflowReason &&
        siteEvidenceUnavailableReason == other.siteEvidenceUnavailableReason

    override fun hashCode(): Int {
        var result = count
        result = 31 * result + instructionSites.hashCode()
        result = 31 * result + observedSites
        result = 31 * result + limitSites
        result = 31 * result + (overflowReason?.hashCode() ?: 0)
        result = 31 * result + (siteEvidenceUnavailableReason?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "GbaTargetReferenceEvidence(" +
        "count=$count, instructionSites=$instructionSites, observedSites=$observedSites, " +
        "limitSites=$limitSites, overflowReason=$overflowReason, " +
        "siteEvidenceUnavailableReason=$siteEvidenceUnavailableReason)"
}

/** Immutable session-owned bounded compiled GBA target-reference evidence. */
class GbaReferenceIndex private constructor(
    targets: Map<Int, GbaTargetReferenceEvidence>,
    val overflowReason: String?,
    val observedTargets: Int,
    val limitTargets: Int,
    val siteEvidenceStatus: GbaSiteEvidenceStatus,
) {
    val targets: Map<Int, GbaTargetReferenceEvidence> = Collections.unmodifiableMap(
        linkedMapOf<Int, GbaTargetReferenceEvidence>().apply {
            targets.toSortedMap().forEach { (target, evidence) -> put(target, evidence) }
        },
    )
    val counts: Map<Int, Int> = Collections.unmodifiableMap(
        linkedMapOf<Int, Int>().apply {
            this@GbaReferenceIndex.targets.forEach { (target, evidence) -> put(target, evidence.count) }
        },
    )
    val overflowed: Boolean get() = overflowReason != null
    val siteEvidenceComplete: Boolean get() = siteEvidenceStatus == GbaSiteEvidenceStatus.COMPLETE

    fun referenceCount(targetOffset: Int): Int = targets[targetOffset]?.count ?: 0

    fun target(targetOffset: Int): GbaTargetReferenceEvidence? = targets[targetOffset]

    internal fun asLegacyCounts(): GbaCompiledReferenceIndex = GbaCompiledReferenceIndex(
        counts = counts,
        overflowReason = overflowReason,
    )

    companion object {
        private const val DEFAULT_TARGET_BUDGET = 32_768
        private const val DEFAULT_SITE_BUDGET = 16

        internal fun countsOnlyForTesting(counts: Map<Int, Int>): GbaReferenceIndex {
            require(counts.keys.all { it >= 0 }) { "GBA reference targets must be non-negative" }
            require(counts.values.all { it > 0 }) { "GBA reference counts must be positive" }
            val targets = counts.mapValues { (_, count) ->
                GbaTargetReferenceEvidence(
                    count = count,
                    instructionSites = emptyList(),
                    observedSites = 0,
                    limitSites = DEFAULT_SITE_BUDGET,
                    overflowReason = null,
                    siteEvidenceUnavailableReason =
                        "compiled instruction sites unavailable in counts-only fixture index",
                )
            }
            return GbaReferenceIndex(
                targets = targets,
                overflowReason = null,
                observedTargets = targets.size,
                limitTargets = DEFAULT_TARGET_BUDGET,
                siteEvidenceStatus = GbaSiteEvidenceStatus.COUNTS_ONLY_INCOMPLETE,
            )
        }

        fun budgetExceeded(
            reason: String,
            observedTargets: Int = 0,
            limitTargets: Int = DEFAULT_TARGET_BUDGET,
        ): GbaReferenceIndex {
            require(reason.isNotBlank()) { "GBA reference overflow reason must not be blank" }
            require(observedTargets >= 0) { "observed GBA reference targets must not be negative" }
            require(limitTargets > 0) { "GBA reference target budget must be positive" }
            return GbaReferenceIndex(
                targets = emptyMap(),
                overflowReason = reason,
                observedTargets = observedTargets,
                limitTargets = limitTargets,
                siteEvidenceStatus = GbaSiteEvidenceStatus.REFERENCE_TARGETS_INCOMPLETE,
            )
        }

        internal fun fromTargets(
            targets: Map<Int, GbaTargetReferenceEvidence>,
            limitTargets: Int,
        ): GbaReferenceIndex = GbaReferenceIndex(
            targets = targets,
            overflowReason = null,
            observedTargets = targets.size,
            limitTargets = limitTargets,
            siteEvidenceStatus = if (targets.values.any { !it.siteEvidenceAvailable }) {
                GbaSiteEvidenceStatus.REFERENCE_SITES_INCOMPLETE
            } else {
                GbaSiteEvidenceStatus.COMPLETE
            },
        )
    }

    override fun equals(other: Any?): Boolean = other is GbaReferenceIndex &&
        targets == other.targets &&
        overflowReason == other.overflowReason &&
        observedTargets == other.observedTargets &&
        limitTargets == other.limitTargets &&
        siteEvidenceStatus == other.siteEvidenceStatus

    override fun hashCode(): Int {
        var result = targets.hashCode()
        result = 31 * result + (overflowReason?.hashCode() ?: 0)
        result = 31 * result + observedTargets
        result = 31 * result + limitTargets
        result = 31 * result + siteEvidenceStatus.hashCode()
        return result
    }

    override fun toString(): String = "GbaReferenceIndex(" +
        "targets=$targets, overflowReason=$overflowReason, observedTargets=$observedTargets, " +
        "limitTargets=$limitTargets, siteEvidenceStatus=$siteEvidenceStatus)"
}

fun interface GbaReferenceIndexFactory {
    fun build(rom: RomImage, limits: ResolutionLimits): GbaReferenceIndex
}

internal object DefaultGbaReferenceIndexFactory : GbaReferenceIndexFactory {
    override fun build(rom: RomImage, limits: ResolutionLimits): GbaReferenceIndex =
        SafeGbaReferenceIndexBuilder.build(rom, limits)
}

internal object SafeGbaReferenceIndexBuilder {
    fun build(
        rom: RomImage,
        limits: ResolutionLimits,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): GbaReferenceIndex {
        val targets = linkedMapOf<Int, TargetAccumulator>()
        val romSize = rom.size.toLong()
        var instructionOffset = 0L
        while (instructionOffset <= romSize - THUMB_INSTRUCTION_BYTES) {
            if (instructionOffset % CANCELLATION_CHECK_INTERVAL_BYTES == 0L) {
                cancellation.throwIfCancellationRequested()
            }
            val instruction = rom.u16le(instructionOffset.toInt())
            if (instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE) {
                val pc = checkedAdd(instructionOffset, THUMB_PC_ADVANCE)?.and(-4L)
                val literalOffset = pc?.let { checkedAdd(it, (instruction and 0xFF).toLong() * 4L) }
                if (literalOffset != null && literalOffset >= 0 && literalOffset <= romSize - 4L) {
                    val rawTarget = rom.u32le(literalOffset.toInt())
                    val targetOffset = rawTarget - GBA_ROM_BASE
                    if (targetOffset >= 0 && targetOffset < romSize) {
                        val target = targetOffset.toInt()
                        var accumulator = targets[target]
                        if (accumulator == null) {
                            if (targets.size == limits.maxDistinctGbaReferenceTargets) {
                                val observed = targets.size + 1
                                return GbaReferenceIndex.budgetExceeded(
                                    reason = "compiled GBA reference target budget exceeded " +
                                        "($observed > ${limits.maxDistinctGbaReferenceTargets})",
                                    observedTargets = observed,
                                    limitTargets = limits.maxDistinctGbaReferenceTargets,
                                )
                            }
                            accumulator = TargetAccumulator()
                            targets[target] = accumulator
                        }
                        accumulator.record(
                            instructionOffset = instructionOffset.toInt(),
                            maxSites = limits.maxCompiledReferenceSitesPerCandidate,
                        )
                    }
                }
            }
            instructionOffset = checkedAdd(instructionOffset, THUMB_INSTRUCTION_BYTES) ?: break
        }

        cancellation.throwIfCancellationRequested()
        val published = targets.mapValues { (_, accumulator) ->
            accumulator.publish(limits.maxCompiledReferenceSitesPerCandidate)
        }
        return GbaReferenceIndex.fromTargets(
            targets = published,
            limitTargets = limits.maxDistinctGbaReferenceTargets,
        )
    }

    private fun checkedAdd(left: Long, right: Long): Long? = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

    private class TargetAccumulator {
        private var count: Int = 0
        private val sites = mutableListOf<Int>()
        private var siteBudgetExceeded: Boolean = false

        fun record(instructionOffset: Int, maxSites: Int) {
            count = Math.addExact(count, 1)
            if (siteBudgetExceeded) return
            if (sites.size == maxSites) {
                sites.clear()
                siteBudgetExceeded = true
            } else {
                sites += instructionOffset
            }
        }

        fun publish(maxSites: Int): GbaTargetReferenceEvidence = GbaTargetReferenceEvidence(
            count = count,
            instructionSites = if (siteBudgetExceeded) emptyList() else sites,
            observedSites = count,
            limitSites = maxSites,
            overflowReason = if (siteBudgetExceeded) {
                "compiled reference site budget exceeded ($count > $maxSites)"
            } else {
                null
            },
        )
    }

    private const val THUMB_INSTRUCTION_BYTES = 2L
    private const val CANCELLATION_CHECK_INTERVAL_BYTES = 4_096L
    private const val THUMB_PC_ADVANCE = 4L
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
    private const val GBA_ROM_BASE = 0x08000000L
}

/** ROM-bounded concrete-site enumeration for one session-nominated reference target. */
internal object SafeGbaReferenceSiteEnumerator {
    fun enumerate(
        rom: RomImage,
        targetOffset: Int,
        expectedCount: Int,
        maxSites: Int,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): GbaTargetReferenceEvidence {
        val sites = ArrayList<Int>(minOf(expectedCount, maxSites))
        var instructionOffset = 0
        while (instructionOffset <= rom.size - 2) {
            if (instructionOffset % CANCELLATION_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            val instruction = rom.u16le(instructionOffset)
            if (instruction and 0xF800 == 0x4800) {
                val pc = (instructionOffset + 4) and -4
                val literalOffset = pc.toLong() + (instruction and 0xFF).toLong() * 4L
                if (literalOffset in 0..rom.size.toLong() - 4L) {
                    val rawTarget = rom.u32le(literalOffset.toInt())
                    if (rawTarget - 0x08000000L == targetOffset.toLong()) {
                        if (sites.size == maxSites) {
                            return GbaTargetReferenceEvidence(
                                count = expectedCount,
                                instructionSites = emptyList(),
                                observedSites = sites.size + 1,
                                limitSites = maxSites,
                                overflowReason = "nominated compiled reference site budget exceeded " +
                                    "(${sites.size + 1} > $maxSites)",
                            )
                        }
                        sites += instructionOffset
                    }
                }
            }
            instructionOffset += 2
        }
        cancellation.throwIfCancellationRequested()
        val mismatch = sites.size != expectedCount
        return GbaTargetReferenceEvidence(
            count = expectedCount,
            instructionSites = if (mismatch) emptyList() else sites,
            observedSites = sites.size,
            limitSites = maxSites,
            overflowReason = if (mismatch) {
                "nominated compiled reference count changed during the analysis session " +
                    "(${sites.size} != $expectedCount)"
            } else {
                null
            },
        )
    }

    private const val CANCELLATION_CHECK_INTERVAL_BYTES = 4_096
}
