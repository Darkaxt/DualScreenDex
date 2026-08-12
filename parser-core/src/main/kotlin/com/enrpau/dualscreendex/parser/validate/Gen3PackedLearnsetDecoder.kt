package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage

internal data class Gen3PackedLearnsetRecord(
    val level: Int,
    val moveId: Int,
)

internal enum class Gen3PackedLearnsetDisposition {
    CLEAN,
    RECOVERED_SHORT_TAIL,
    QUARANTINED,
}

internal data class Gen3PackedLearnsetDecodeResult(
    val records: List<Gen3PackedLearnsetRecord>,
    val disposition: Gen3PackedLearnsetDisposition,
    val discardedTailWords: Int = 0,
) {
    val usable: Boolean get() = disposition != Gen3PackedLearnsetDisposition.QUARANTINED
}

/**
 * Decodes the two-byte Gen III level-up format once for both validation and materialization.
 *
 * Level zero is a real initial/relearnable acquisition in several ROM engines. Move zero is
 * always NONE, so it cannot become a catalog entry. A small invalid suffix immediately before
 * the terminator is retained as row-level anomaly evidence while the valid prefix stays usable.
 * Longer or unterminated malformed data is quarantined in full rather than guessed through.
 */
internal object Gen3PackedLearnsetDecoder {
    fun adjacentPointerBoundaries(offsets: List<Int?>): Map<Int, Int?> {
        val targets = offsets.filterNotNull().distinct().sorted()
        return targets.mapIndexed { index, target -> target to targets.getOrNull(index + 1) }.toMap()
    }

    fun decode(
        rom: RomImage,
        offset: Int,
        moveCount: Int,
        moveBits: Int,
        endExclusive: Int? = null,
    ): Gen3PackedLearnsetDecodeResult {
        if (offset !in 0 until rom.size || moveCount <= 1 || moveBits !in 1..15) return quarantined()
        if (endExclusive != null && (endExclusive !in (offset + 2)..rom.size || (endExclusive - offset) % 2 != 0)) {
            return quarantined()
        }
        val moveMask = (1 shl moveBits) - 1
        val records = mutableListOf<Gen3PackedLearnsetRecord>()
        var cursor = offset
        try {
            repeat(MAX_ENTRIES) {
                if (cursor == endExclusive) {
                    return if (records.isNotEmpty()) recovered(records, 0) else quarantined()
                }
                if (endExclusive != null && cursor + 2 > endExclusive) return quarantined()
                val packed = rom.u16le(cursor)
                if (packed == TERMINATOR) {
                    return Gen3PackedLearnsetDecodeResult(records, Gen3PackedLearnsetDisposition.CLEAN)
                }
                val move = packed and moveMask
                val level = packed ushr moveBits
                if (move !in 1 until moveCount || level !in 0..100) {
                    val tailWords = shortTailLengthToTerminator(rom, cursor, endExclusive)
                        ?: boundedInvalidTailWords(cursor, endExclusive)
                    return if (tailWords != null && records.isNotEmpty()) {
                        recovered(records, tailWords)
                    } else {
                        quarantined()
                    }
                }
                records += Gen3PackedLearnsetRecord(level, move)
                cursor += 2
            }
        } catch (_: RomBoundsException) {
            return quarantined()
        }
        return quarantined()
    }

    private fun shortTailLengthToTerminator(
        rom: RomImage,
        firstInvalid: Int,
        endExclusive: Int?,
    ): Int? = try {
        for (skipped in 0..MAX_RECOVERABLE_TAIL_WORDS) {
            val cursor = firstInvalid + skipped * 2
            if (endExclusive != null && cursor + 2 > endExclusive) break
            if (rom.u16le(cursor) == TERMINATOR) return skipped
        }
        null
    } catch (_: RomBoundsException) {
        null
    }

    private fun boundedInvalidTailWords(firstInvalid: Int, endExclusive: Int?): Int? {
        val boundary = endExclusive ?: return null
        val remaining = boundary - firstInvalid
        if (remaining <= 0 || remaining % 2 != 0) return null
        return (remaining / 2).takeIf { it <= MAX_RECOVERABLE_TAIL_WORDS }
    }

    private fun recovered(
        records: List<Gen3PackedLearnsetRecord>,
        discardedTailWords: Int,
    ) = Gen3PackedLearnsetDecodeResult(
        records = records,
        disposition = Gen3PackedLearnsetDisposition.RECOVERED_SHORT_TAIL,
        discardedTailWords = discardedTailWords,
    )

    private fun quarantined() = Gen3PackedLearnsetDecodeResult(
        records = emptyList(),
        disposition = Gen3PackedLearnsetDisposition.QUARANTINED,
    )

    private const val MAX_ENTRIES = 128
    private const val MAX_RECOVERABLE_TAIL_WORDS = 4
    private const val TERMINATOR = 0xFFFF
}
