package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/**
 * Gen II move-count authority from the compiled Metronome random-move loop.
 *
 * `call BattleRandom; cp NUM_ATTACKS + 1; jr nc, .loop; push af; ld de, 1; ld hl, Excepts; call IsInArray;
 * pop bc; jr c, .loop`. Both relative jumps must return to the same random call. Some 254-move engines
 * write the equivalent `cp $FF; jr z, .loop`, which also rejects only 255 (0 is in the exception list).
 *
 * Only when no Metronome loop is compiled at all (engines that disable the move), the Battle Tower party
 * loader is the fallback: per mon, each of its four move bytes is compared twice against `NUM_ATTACKS + 1`
 * (`cp N; jr nc` reject, `cp N; jr c` keep), inside `ld hl, 2; add hl, bc` ... `ld hl, 36; add hl, bc`.
 */
internal object Gen2CompiledMoveCountResolver {
    fun resolve(rom: RomImage, cancellation: ParserCancellationToken = ParserCancellationToken.NONE): Int? {
        val counts = linkedSetOf<Int>()
        var metronome = false
        val towerCounts = linkedSetOf<Int>()
        for (offset in 0..rom.size - LOOP.size) {
            if (offset % 4096 == 0) cancellation.throwIfCancellationRequested()
            if (rom.u8(offset) == 0x21 && matches(rom, offset, TOWER)) {
                val bound = rom.u8(offset + 11)
                if (rom.u8(offset + 28) == bound && bound - 1 in 1..254) towerCounts += bound - 1 else towerCounts += -1
            }
            if (rom.u8(offset) != 0xCD || !matches(rom, offset, LOOP)) continue
            metronome = true
            val bound = rom.u8(offset + 4)
            val count = when (rom.u8(offset + 5)) {
                JR_NC -> bound - 1
                JR_Z -> if (bound == 0xFF) 254 else continue
                else -> continue
            }
            if (count in 1..254) counts += count
        }
        if (!metronome) return towerCounts.singleOrNull()?.takeIf { it > 0 }
        return counts.singleOrNull()
    }

    private fun matches(rom: RomImage, offset: Int, pattern: IntArray): Boolean =
        offset.toLong() + pattern.size <= rom.size &&
            pattern.indices.all { pattern[it] < 0 || rom.u8(offset + it) == pattern[it] }

    private const val JR_NC = 0x30
    private const val JR_Z = 0x28
    private val LOOP = intArrayOf(
        0xCD, -1, -1, 0xFE, -1, -1, 0xF9,
        0xF5, 0x11, 0x01, 0x00, 0x21, -1, -1, 0xCD, -1, -1, 0xC1, 0x38, 0xEC,
    )
    private val TOWER = intArrayOf(
        0x21, 0x02, 0x00, 0x09, 0x16, 0x03, 0x2A, 0xA7, 0x28, 0x06, 0xFE, -1, 0x30, 0x02, 0x18, 0x0A,
        0x2B, 0x3E, 0x01, 0x22, 0xAF, 0x22, 0x22, 0x77, 0x18, 0x0B, 0x7E, 0xFE, -1, 0x38, 0x02, 0x36, 0x00,
        0x23, 0x15, 0x20, 0xF5, 0x21, 0x24, 0x00, 0x09,
    )
}
