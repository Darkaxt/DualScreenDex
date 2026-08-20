package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.validate.TableValidators

/** Resolves a Gen I legacy type chart from its compiled battle consumer. */
internal object Gen1CompiledTypeChartResolver {
    fun resolve(rom: RomImage): TableLayout? {
        val candidates = buildSet {
            var offset = 0
            while (offset + MIN_CONSUMER_BYTES <= rom.size) {
                if (isConsumerAt(rom, offset)) {
                    val bank = offset / BANK_BYTES
                    val address = rom.u16le(offset + 1)
                    if (bank > 0 && address in SWITCHABLE_ADDRESS_RANGE) {
                        rom.gbBankAddress(bank, address)?.let { root ->
                            val evidence = TableValidators.typeChart(
                                rom = rom,
                                offset = root,
                                generation = 1,
                                maximumType = MAX_TYPE_ID,
                            )
                            if (evidence.compatible) add(root)
                        }
                    }
                }
                offset++
            }
        }
        val root = candidates.singleOrNull() ?: return null
        return TableLayout(root, 0, RECORD_BYTES, variableLength = true)
    }

    private fun isConsumerAt(rom: RomImage, offset: Int): Boolean {
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 3) != LOAD_A_INCREMENT_HL ||
            rom.u8(offset + 4) != COMPARE_IMMEDIATE ||
            rom.u8(offset + 5) != TERMINATOR ||
            rom.u8(offset + 6) !in ZERO_BRANCHES
        ) {
            return false
        }
        val start = offset + 7
        val endExclusive = minOf(rom.size, offset + CONSUMER_WINDOW_BYTES)
        return containsByte(rom, start, endExclusive, COMPARE_B) &&
            containsDefenderComparison(rom, start, endExclusive)
    }

    private fun containsByte(rom: RomImage, start: Int, endExclusive: Int, value: Int): Boolean {
        var offset = start
        while (offset < endExclusive) {
            if (rom.u8(offset) == value) return true
            offset++
        }
        return false
    }

    private fun containsDefenderComparison(rom: RomImage, start: Int, endExclusive: Int): Boolean {
        var offset = start
        while (offset + 1 < endExclusive) {
            if (rom.u8(offset) == LOAD_A_HL && rom.u8(offset + 1) in DEFENDER_COMPARISONS) return true
            offset++
        }
        return false
    }

    private val SWITCHABLE_ADDRESS_RANGE = 0x4000..0x7FFF
    private val ZERO_BRANCHES = setOf(0x28, 0xCA)
    private val DEFENDER_COMPARISONS = setOf(0xB9, 0xBA, 0xBB)
    private const val BANK_BYTES = 0x4000
    private const val RECORD_BYTES = 3
    private const val MAX_TYPE_ID = 63
    private const val CONSUMER_WINDOW_BYTES = 32
    private const val MIN_CONSUMER_BYTES = 16
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_A_INCREMENT_HL = 0x2A
    private const val COMPARE_IMMEDIATE = 0xFE
    private const val TERMINATOR = 0xFF
    private const val COMPARE_B = 0xB8
    private const val LOAD_A_HL = 0x7E
}
