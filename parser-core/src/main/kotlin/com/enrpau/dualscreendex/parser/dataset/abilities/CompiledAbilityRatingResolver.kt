package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7ControlEffect
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryWidth
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder

data class ResolvedCompiledAbilityRatings(
    val sourceOffset: Int,
    val ratingsByAbility: Map<Int, Int>,
    val consumerSites: List<Int>,
)

/**
 * Resolves a raw-ID-indexed signed AI-rating vector from its compiled Thumb consumers.
 *
 * The ability domain comes from the independently selected ability-name table. A candidate must
 * cover that complete raw-ID domain, use the source-defined signed -10..10 rating scale, and be
 * read as a signed byte through at least two complete literal-root consumers. No ROM identity,
 * symbol, address, or byte sequence participates in selection; multiple survivors fail closed.
 */
object CompiledAbilityRatingResolver {
    fun resolve(
        session: RomAnalysisSession,
        abilityIds: Set<Int>,
    ): ResolvedCompiledAbilityRatings? {
        if (abilityIds.isEmpty() || abilityIds.any { it <= 0 }) return null
        val maximumId = abilityIds.max()
        val recordCount = maximumId + 1
        val references = session.gbaReferenceIndex?.takeUnless { it.overflowed } ?: return null
        val candidates = references.targets.keys.mapNotNull { root ->
            if (root < 0 || root.toLong() + recordCount.toLong() > session.rom.size.toLong()) {
                return@mapNotNull null
            }
            val rawValues = (0 until recordCount).map { index ->
                session.rom.u8(root + index).toByte().toInt()
            }
            if (rawValues.first() != 0 || rawValues.any { it !in -10..10 }) return@mapNotNull null
            val activeValues = abilityIds.map(rawValues::get)
            if (
                activeValues.toSet().size < 6 ||
                activeValues.none { it < 0 } ||
                activeValues.none { it == 10 } ||
                activeValues.count { it != 0 } * 2 < activeValues.size
            ) return@mapNotNull null

            val sites = session.nominatedGbaReferenceSites(root)
                ?.takeIf { it.siteEvidenceAvailable }
                ?.instructionSites
                .orEmpty()
                .filter { site -> readsSignedIndexedByteInBasicBlock(session, site) }
                .distinct()
            if (sites.size < 2) return@mapNotNull null
            ResolvedCompiledAbilityRatings(
                sourceOffset = root,
                ratingsByAbility = abilityIds.sorted().associateWith(rawValues::get),
                consumerSites = sites,
            )
        }
        return candidates.singleOrNull()
    }

    private fun readsSignedIndexedByteInBasicBlock(session: RomAnalysisSession, site: Int): Boolean {
        val rootLoad = decode(session, site) as? Arm7MemoryTransfer ?: return false
        if (!rootLoad.load || rootLoad.width != Arm7MemoryWidth.WORD || rootLoad.address !is Arm7Address.PcRelative) {
            return false
        }
        val rootRegister = rootLoad.valueRegister
        var offset = site + rootLoad.size
        while (offset + 2 <= session.rom.size) {
            val instruction = decode(session, offset) ?: return false
            val transfer = instruction as? Arm7MemoryTransfer
            val address = transfer?.address as? Arm7Address.RegisterOffset
            if (
                transfer?.load == true &&
                transfer.width == Arm7MemoryWidth.BYTE &&
                transfer.signed &&
                address?.base == rootRegister &&
                address.index != null &&
                address.immediate == 0 &&
                address.add &&
                address.preIndexed &&
                !address.writeBack
            ) return true
            if (rootRegister in instruction.registersWritten) return false
            if (instruction.controlEffect != Arm7ControlEffect.Sequential) return false
            offset += instruction.size
        }
        return false
    }

    private fun decode(session: RomAnalysisSession, offset: Int) =
        (ThumbDecoder.decode(session.rom, offset) as? Arm7DecodeResult.Decoded)?.instruction
}
