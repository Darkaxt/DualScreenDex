package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.catalog.RuntimeMemoryEvidence
import com.enrpau.dualscreendex.parser.io.RomImage

/**
 * Recognizes the source-defined Gen III Main ABI from independent references to both the
 * structure base and its final aligned word. Absolute RAM addresses are evidence, never a profile.
 */
object Gen3RuntimeMemoryLayoutResolver {
    fun resolve(rom: RomImage): CatalogGen3RuntimeMemoryLayout? {
        val analysis = analyze(rom)
        val best = analysis.scores.values.maxWithOrNull(compareBy<ReferenceScore> { it.base }.thenBy { it.tail })
            ?: return null
        val mainBase = analysis.scores.filterValues { it == best }.keys.singleOrNull() ?: return null
        val liveTargetOffset = MULTI_USE_PLAYER_CURSOR_OFFSET_FROM_MAIN.takeIf {
            analysis.references.getOrDefault(mainBase + it, 0) >= MIN_LIVE_TARGET_REFERENCES
        }
        return CatalogGen3RuntimeMemoryLayout(
            mainStructSize = MAIN_STRUCT_SIZE,
            inBattleByteOffset = IN_BATTLE_BYTE_OFFSET,
            inBattleMask = IN_BATTLE_MASK,
            saveBlock1MapGroupOffset = SAVE_MAP_GROUP_OFFSET,
            saveBlock1MapNumberOffset = SAVE_MAP_NUMBER_OFFSET,
            multiUsePlayerCursorOffsetFromMain = liveTargetOffset,
            multiUsePlayerCursorEvidence = liveTargetOffset?.let { RuntimeMemoryEvidence.SOURCE_PROVEN_UNTESTED },
        )
    }

    private fun analyze(rom: RomImage): ReferenceAnalysis {
        val references = linkedMapOf<Long, Int>()
        var offset = 0
        while (offset <= rom.size - 4) {
            val value = rom.u32le(offset)
            if (value in IWRAM_START..IWRAM_END) {
                references[value] = references.getOrDefault(value, 0) + 1
            }
            offset += 4
        }
        val scores = references.filter { (base, count) ->
            count >= MIN_MAIN_BASE_REFERENCES &&
                references.getOrDefault(base + MAIN_TAIL_WORD_OFFSET, 0) >= MIN_MAIN_TAIL_REFERENCES &&
                base + MAIN_STRUCT_SIZE <= IWRAM_END + 1
        }.mapValues { (base, count) -> ReferenceScore(count, references.getValue(base + MAIN_TAIL_WORD_OFFSET)) }
        return ReferenceAnalysis(references, scores)
    }

    private const val IWRAM_START = 0x03000000L
    private const val IWRAM_END = 0x03007FFFL
    private const val MAIN_STRUCT_SIZE = 0x43C
    private const val MAIN_TAIL_WORD_OFFSET = 0x438
    private const val IN_BATTLE_BYTE_OFFSET = 0x439
    private const val IN_BATTLE_MASK = 0x02
    private const val SAVE_MAP_GROUP_OFFSET = 4
    private const val SAVE_MAP_NUMBER_OFFSET = 5
    private const val MIN_MAIN_BASE_REFERENCES = 32
    private const val MIN_MAIN_TAIL_REFERENCES = 3
    private const val MULTI_USE_PLAYER_CURSOR_OFFSET_FROM_MAIN = 0xE04
    private const val MIN_LIVE_TARGET_REFERENCES = 4

    private data class ReferenceScore(val base: Int, val tail: Int)
    private data class ReferenceAnalysis(
        val references: Map<Long, Int>,
        val scores: Map<Long, ReferenceScore>,
    )
}
