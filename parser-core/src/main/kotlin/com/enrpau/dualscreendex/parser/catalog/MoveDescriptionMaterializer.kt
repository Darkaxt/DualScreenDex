package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

data class MoveDescriptionResult(
    val sourceOffset: Int,
    val confidence: Double,
    val descriptions: Map<Int, String>,
)

object MoveDescriptionMaterializer {
    fun materialize(rom: RomImage, layout: ResolvedRomLayout): MoveDescriptionResult? {
        if (layout.generation != 3) return null
        val pointerCount = (layout.moveCount ?: return null) - 1
        if (pointerCount < 3) return null
        return pointerRuns(rom)
            .asSequence()
            .filter { it.length >= pointerCount * 4 }
            .flatMap { run -> windows(run, pointerCount * 4).asSequence() }
            .mapNotNull { offset -> decodeCandidate(rom, offset, pointerCount) }
            .maxWithOrNull(compareBy<MoveDescriptionResult> { it.confidence }.thenBy { it.descriptions.size })
    }

    private fun pointerRuns(rom: RomImage): List<PointerRun> {
        val output = mutableListOf<PointerRun>()
        var cursor = 0
        while (cursor + 4 <= rom.size) {
            if (rom.gbaPointer(cursor) == null) {
                cursor += 4
                continue
            }
            val start = cursor
            while (cursor + 4 <= rom.size && rom.gbaPointer(cursor) != null) cursor += 4
            output += PointerRun(start, cursor - start)
        }
        return output
    }

    private fun windows(run: PointerRun, bytes: Int): List<Int> {
        if (run.length == bytes) return listOf(run.offset)
        val output = mutableListOf<Int>()
        var cursor = run.offset
        val end = run.offset + run.length
        while (cursor + bytes <= end) {
            output += cursor
            cursor += bytes
        }
        return output
    }

    private fun decodeCandidate(rom: RomImage, offset: Int, pointerCount: Int): MoveDescriptionResult? {
        val codec = PokemonTextCodec.gbaEnglish
        val descriptions = linkedMapOf<Int, String>()
        repeat(pointerCount) { index ->
            val textOffset = runCatching { rom.gbaPointer(offset + index * 4) }.getOrNull() ?: return@repeat
            val length = minOf(192, rom.size - textOffset)
            val decoded = runCatching { codec.decodeDetailed(rom.slice(textOffset, length)) }.getOrNull() ?: return@repeat
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (decoded.terminated && decoded.validRatio >= 0.85 && normalized.length >= 5) {
                descriptions[index + 1] = normalized
            }
        }
        val decodedRatio = descriptions.size.toDouble() / pointerCount
        val descriptionLike = descriptions.values.count(::looksLikeNaturalDescription)
        val naturalLanguageRatio = descriptionLike.toDouble() / descriptions.size.coerceAtLeast(1)
        val confidence = minOf(decodedRatio, naturalLanguageRatio)
        val minimum = maxOf(3, (pointerCount * 0.8).toInt())
        return if (descriptions.size >= minimum && naturalLanguageRatio >= 0.75) {
            MoveDescriptionResult(offset, confidence, descriptions)
        } else {
            null
        }
    }

    private fun looksLikeNaturalDescription(value: String): Boolean {
        val words = value.split(Regex("\\s+")).count { it.any(Char::isLetter) }
        return value.length >= 12 && words >= 3 && value.any(Char::isLowerCase)
    }

    private data class PointerRun(val offset: Int, val length: Int)
}
