package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Bounded compiled script-buffer string consumers. Width is proved by index arithmetic, not locale.
 * The paired x6/x8 ABI is present in the verified native RS/Emerald/FRLG consumers. Other ABIs
 * continue through existing discovery. No profile offsets or literal-name search are used here.
 */
internal object Gen3CompiledNameGeometryResolver {
    data class Result(val speciesNames: TableLayout? = null, val moveNames: TableLayout? = null, val ambiguous: Boolean = false)

    fun resolve(rom: RomImage, codec: PokemonTextCodec, cancellation: ParserCancellationToken = ParserCancellationToken.NONE): Result {
        cancellation.throwIfCancellationRequested()
        val species = linkedSetOf<Int>()
        val moves = linkedSetOf<Int>()
        val end = minOf(rom.size, MAXIMUM_CODE_BYTES)
        for (offset in 0..end - 60 step 2) {
            cancellation.throwIfCancellationRequested()
            if (!matches(rom, offset, PREFIX)) continue
            val body = offset + PREFIX.size * 2
            if (body + SPECIES.size * 2 <= end && matches(rom, body, SPECIES)) {
                literalRoot(rom, body + 18)?.let(species::add)
            }
            if (matches(rom, body, MOVES)) literalRoot(rom, body + 14)?.let(moves::add)
        }
        if (species.isEmpty() || moves.isEmpty()) return Result()
        val pairs = buildSet {
            for (s in species) for (m in moves) {
                cancellation.throwIfCancellationRequested()
                val distance = m.toLong() - s
                if (distance <= 0 || distance % 6 != 0L || distance / 6 !in 1..MAXIMUM_RECORDS) continue
                val count = (distance / 6).toInt()
                if (fixedCount(rom, s, 6, codec, cancellation, count) != count) continue
                val moveCount = fixedCount(rom, m, 8, codec, cancellation, MAXIMUM_RECORDS)
                if (moveCount <= 0) continue
                add(Result(TableLayout(s, count, 6), TableLayout(m, moveCount, 8)))
            }
        }
        return pairs.singleOrNull() ?: Result(ambiguous = pairs.size > 1)
    }

    private fun fixedCount(rom: RomImage, root: Int, width: Int, codec: PokemonTextCodec,
        cancellation: ParserCancellationToken, maximum: Int): Int {
        var count = 0
        while (count < maximum && root.toLong() + (count + 1L) * width <= rom.size) {
            cancellation.throwIfCancellationRequested()
            val text = codec.decodeDetailed(rom, root + count * width, width, cancellation)
            if (!text.terminated || text.invalidUnits != 0 || text.text.isBlank() || text.controlUnits != 0) break
            count++
        }
        return count
    }

    private fun literalRoot(rom: RomImage, instruction: Int): Int? {
        val opcode = rom.u16le(instruction)
        val slot = ((instruction + 4) and -4) + (opcode and 255) * 4
        if (slot.toLong() + 4 > rom.size) return null
        return rom.gbaPointer(slot)
    }

    private fun matches(rom: RomImage, offset: Int, pattern: IntArray): Boolean {
        if (offset < 0 || offset.toLong() + pattern.size * 2 > rom.size) return false
        return pattern.indices.all { index ->
            val opcode = rom.u16le(offset + index * 2)
            when (val expected = pattern[index]) {
                BL_HIGH -> opcode and 0xf800 == 0xf000
                BL_LOW -> opcode and 0xf800 == 0xf800
                LITERAL_R0 -> opcode and 0xff00 == 0x4800
                LITERAL_R1 -> opcode and 0xff00 == 0x4900
                LITERAL_R2 -> opcode and 0xff00 == 0x4a00
                else -> opcode == expected
            }
        }
    }

    private const val MAXIMUM_CODE_BYTES = 0x100000
    private const val MAXIMUM_RECORDS = 2048
    private const val BL_HIGH = -1
    private const val BL_LOW = -2
    private const val LITERAL_R0 = -3
    private const val LITERAL_R1 = -4
    private const val LITERAL_R2 = -5
    private val PREFIX = intArrayOf(0xb510, 0x6881, 0x780c, 0x3101, 0x6081, BL_HIGH, BL_LOW,
        0x0400, 0x0c00, BL_HIGH, BL_LOW)
    private val SPECIES = intArrayOf(0x0400, 0x0c00, LITERAL_R1, 0x00a4, 0x1864, 0x6822,
        0x0041, 0x1809, 0x0049, LITERAL_R0, 0x1809, 0x1c10, BL_HIGH, BL_LOW,
        0x2000, 0xbc10, 0xbc02, 0x4708)
    private val MOVES = intArrayOf(0x1c01, 0x0409, LITERAL_R0, 0x00a4, 0x1824, 0x6820,
        0x0b49, LITERAL_R2, 0x1889, BL_HIGH, BL_LOW, 0x2000, 0xbc10, 0xbc02, 0x4708)
}
