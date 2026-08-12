package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class RecoveredGen3DescriptionPointer(
    val rowIndex: Int,
    val originalOffset: Int,
    val recoveredOffset: Int,
    val text: String,
)

/**
 * Recovers the narrow source anomaly where a description pointer lands on the terminator byte
 * immediately before its actual text. Recovery is permitted only inside the next independently
 * referenced description boundary, and competing candidates leave the row unresolved.
 */
internal object Gen3DescriptionPointerRecovery {
    fun recover(
        rom: RomImage,
        table: TableLayout,
        codec: PokemonTextCodec = PokemonTextCodec.gbaEnglish,
    ): Map<Int, RecoveredGen3DescriptionPointer> {
        if (table.count <= 0 || table.recordSize <= 0) return emptyMap()
        val pointerFields = table.pointerOffsets.ifEmpty {
            if (table.recordSize >= 36) listOf(16, 20) else listOf(16)
        }
        val pointersByRow = (0 until table.count).associateWith { row ->
            pointerFields.mapNotNull { field ->
                val cell = table.offset.toLong() + row.toLong() * table.recordSize + field
                cell.takeIf { it in 0..(rom.size - 4).toLong() }
                    ?.toInt()
                    ?.let(rom::gbaPointer)
            }
        }
        val boundaries = pointersByRow.values.flatten().distinct().sorted()
        return buildMap {
            pointersByRow.forEach { (row, pointers) ->
                val candidates = pointers.mapNotNull { pointer ->
                    if (rom.u8(pointer) != codec.terminator) return@mapNotNull null
                    val recoveredOffset = pointer + 1
                    val boundary = boundaries.firstOrNull { it > pointer } ?: return@mapNotNull null
                    decodeNaturalBeforeBoundary(rom, recoveredOffset, boundary, codec)?.let { text ->
                        RecoveredGen3DescriptionPointer(row, pointer, recoveredOffset, text)
                    }
                }.distinctBy { it.recoveredOffset to it.text }
                if (candidates.size == 1) put(row, candidates.single())
            }
        }
    }

    private fun decodeNaturalBeforeBoundary(
        rom: RomImage,
        offset: Int,
        boundary: Int,
        codec: PokemonTextCodec,
    ): String? {
        if (offset !in 0 until boundary || boundary > rom.size) return null
        val terminator = (offset until boundary).firstOrNull { rom.u8(it) == codec.terminator } ?: return null
        val decoded = codec.decodeDetailed(rom.slice(offset, terminator - offset + 1))
        val text = decoded.text
        val letterCount = text.count(Char::isLetter)
        return text.takeIf {
            decoded.terminated &&
                decoded.validRatio >= MINIMUM_VALID_RATIO &&
                text.length >= MINIMUM_TEXT_LENGTH &&
                letterCount * 2 >= text.length
        }
    }

    private const val MINIMUM_VALID_RATIO = 0.85
    private const val MINIMUM_TEXT_LENGTH = 8
}
