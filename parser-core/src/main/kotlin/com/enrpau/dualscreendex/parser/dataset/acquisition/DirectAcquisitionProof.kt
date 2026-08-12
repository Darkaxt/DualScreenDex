package com.enrpau.dualscreendex.parser.dataset.acquisition

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import java.util.Collections

/** Session-derived proof that concrete Thumb literal-load instructions reference every ABI root. */
class DirectAcquisitionProof private constructor(
    private val romSha256: String,
    private val layoutIdentity: CandidateLayoutIdentity,
    sitesByRoot: Map<Long, List<Int>>,
) {
    internal val sitesByRoot: Map<Long, List<Int>> = immutableMap(
        sitesByRoot.mapValues { (_, sites) -> immutableList(sites) },
    )
    internal val instructionSites: List<Int> = immutableList(
        this.sitesByRoot.values.flatten().sorted(),
    )

    internal fun matches(session: RomAnalysisSession, layout: AcquisitionTableLayout): Boolean =
        romSha256 == session.rom.sha256 && layoutIdentity == layout.layoutIdentity

    internal fun matchesLayout(layout: AcquisitionTableLayout): Boolean = layoutIdentity == layout.layoutIdentity

    companion object {
        fun verify(
            session: RomAnalysisSession,
            layout: AcquisitionTableLayout,
            proposedSitesByRoot: Map<Long, Collection<Int>>,
        ): DirectAcquisitionProofResult {
            if (session.header.platform != Platform.GBA) {
                return DirectAcquisitionProofResult.Rejected("direct acquisition proof requires a GBA ROM")
            }
            val roots = layout.abi.physicalRoots
            if (roots.distinct().size != roots.size) {
                return DirectAcquisitionProofResult.Rejected("acquisition ABI physical roots are not role-distinct")
            }
            if (proposedSitesByRoot.keys != roots.toSet()) {
                return DirectAcquisitionProofResult.Rejected(
                    "direct acquisition proof must bind sites to every physical root",
                )
            }
            val observed = proposedSitesByRoot.values.fold(0L) { total, sites ->
                try {
                    Math.addExact(total, sites.size.toLong())
                } catch (_: ArithmeticException) {
                    Long.MAX_VALUE
                }
            }
            val limit = session.limits.maxCompiledReferenceSitesPerCandidate
            if (observed > limit.toLong()) {
                return DirectAcquisitionProofResult.BudgetExceeded(
                    observedSites = observed,
                    limitSites = limit,
                    reason = "direct acquisition reference-site budget exceeded ($observed > $limit)",
                )
            }

            val seenSites = mutableSetOf<Int>()
            val verified = linkedMapOf<Long, List<Int>>()
            roots.forEach { root ->
                val proposed = proposedSitesByRoot.getValue(root)
                if (proposed.isEmpty()) {
                    return DirectAcquisitionProofResult.Rejected(
                        "direct acquisition proof requires at least one instruction site per root",
                    )
                }
                val rootSites = ArrayList<Int>(proposed.size)
                proposed.forEach { site ->
                    if (!seenSites.add(site)) {
                        return DirectAcquisitionProofResult.Rejected(
                            "one instruction site cannot prove multiple acquisition roots",
                        )
                    }
                    if (thumbLiteralTarget(session, site) != root) {
                        return DirectAcquisitionProofResult.Rejected(
                            "instruction site $site does not load acquisition root $root",
                        )
                    }
                    rootSites += site
                }
                verified[root] = rootSites.sorted()
            }
            return DirectAcquisitionProofResult.Verified(
                DirectAcquisitionProof(session.rom.sha256, layout.layoutIdentity, verified),
            )
        }

        private fun thumbLiteralTarget(session: RomAnalysisSession, site: Int): Long? {
            if (site < 0 || site and 1 != 0 || site.toLong() + 2L > session.rom.size.toLong()) return null
            val instruction = session.rom.u16le(site)
            if (instruction and THUMB_LITERAL_LOAD_MASK != THUMB_LITERAL_LOAD_OPCODE) return null
            val pc = try {
                Math.addExact(site.toLong(), THUMB_PC_ADVANCE).and(-4L)
            } catch (_: ArithmeticException) {
                return null
            }
            val literalOffset = try {
                Math.addExact(pc, (instruction and 0xFF).toLong() * 4L)
            } catch (_: ArithmeticException) {
                return null
            }
            if (literalOffset < 0 || literalOffset + 4L > session.rom.size.toLong()) return null
            val raw = session.rom.u32le(literalOffset.toInt())
            val target = raw - GBA_ROM_BASE
            return target.takeIf { it in 0 until session.rom.size.toLong() }
        }

        private const val THUMB_LITERAL_LOAD_MASK = 0xF800
        private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
        private const val THUMB_PC_ADVANCE = 4L
        private const val GBA_ROM_BASE = 0x08000000L
    }
}

sealed interface DirectAcquisitionProofResult {
    data class Verified(val proof: DirectAcquisitionProof) : DirectAcquisitionProofResult
    data class Rejected(val reason: String) : DirectAcquisitionProofResult
    data class BudgetExceeded(
        val observedSites: Long,
        val limitSites: Int,
        val reason: String,
    ) : DirectAcquisitionProofResult
}

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())

private fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))
