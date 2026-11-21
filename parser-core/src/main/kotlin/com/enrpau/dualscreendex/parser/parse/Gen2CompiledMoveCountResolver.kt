package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/**
 * Gen II move-count authority from the compiled Metronome random-move loop.
 *
 * `call BattleRandom; cp NUM_ATTACKS + 1; jr nc, .loop; push af; ld de, 1; ld hl, Excepts; call IsInArray;
 * pop bc; jr c, .loop`. Both relative jumps must return to the same random call. Some 254-move engines
 * write the equivalent `cp $FF; jr z, .loop`, which also rejects only 255 (0 is in the exception list).
 */
internal object Gen2CompiledMoveCountResolver {
    fun resolve(rom: RomImage, cancellation: ParserCancellationToken = ParserCancellationToken.NONE): Int? {
        val counts = linkedSetOf<Int>()
        for (offset in 0..rom.size - LOOP.size) {
            if (offset % 4096 == 0) cancellation.throwIfCancellationRequested()
            if (rom.u8(offset) != 0xCD || !LOOP.indices.all { LOOP[it] < 0 || rom.u8(offset + it) == LOOP[it] }) continue
            val bound = rom.u8(offset + 4)
            val count = when (rom.u8(offset + 5)) {
                JR_NC -> bound - 1
                JR_Z -> if (bound == 0xFF) 254 else continue
                else -> continue
            }
            if (count in 1..254) counts += count
        }
        return counts.singleOrNull()
    }

    private const val JR_NC = 0x30
    private const val JR_Z = 0x28
    private val LOOP = intArrayOf(
        0xCD, -1, -1, 0xFE, -1, -1, 0xF9,
        0xF5, 0x11, 0x01, 0x00, 0x21, -1, -1, 0xCD, -1, -1, 0xC1, 0x38, 0xEC,
    )
}
