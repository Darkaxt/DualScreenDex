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
        layout.pokeemeraldExpansion?.let { expansion ->
            val table = layout.tables.moveData ?: return null
            val count = layout.moveCount ?: return null
            val descriptions = buildMap {
                repeat(count - 1) { index ->
                    val id = index + 1
                    val record = table.offset + id * (table.stride ?: expansion.moveRecordSize)
                    val text = rom.gbaPointer(record + 4)?.let { decodeText(rom, it) } ?: return@repeat
                    put(id, text)
                }
            }
            val expected = count - 1
            val confidence = descriptions.size.toDouble() / expected.coerceAtLeast(1)
            return MoveDescriptionResult(table.offset, confidence, descriptions).takeIf {
                descriptions.size >= maxOf(3, (expected * 0.8).toInt())
            }
        }
        val moveCount = layout.moveCount ?: return null
        if (moveCount < 4) return null
        val pointerCount = moveCount - 1
        return candidateOffsets(rom, pointerCount)
            .mapNotNull { offset -> decodeCandidate(rom, offset, pointerCount) }
            .maxWithOrNull(compareBy<MoveDescriptionResult> { it.confidence }.thenBy { it.descriptions.size })
    }

    private fun candidateOffsets(rom: RomImage, pointerCount: Int): Sequence<Int> {
        val tableBytesLong = pointerCount.toLong() * 4L
        if (tableBytesLong > rom.size.toLong()) return emptySequence()
        val minimumPrefixBytesLong = ((pointerCount.toLong() + 1L) / 2L) * 4L
        val tableBytes = tableBytesLong.toInt()
        val minimumPrefixBytes = minimumPrefixBytesLong.toInt()
        return pointerRuns(rom)
            .asSequence()
            .filter { run ->
                run.length >= minimumPrefixBytes && run.offset.toLong() + tableBytesLong <= rom.size.toLong()
            }
            .flatMap { run ->
                if (run.length >= tableBytes) {
                    windows(run, tableBytes).asSequence()
                } else {
                    sequenceOf(run.offset)
                }
            }
            .distinct()
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

    private fun decodeText(rom: RomImage, offset: Int): String? {
        val length = minOf(256, rom.size - offset)
        val decoded = runCatching { PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(offset, length)) }.getOrNull()
            ?: return null
        val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
        return normalized.takeIf { decoded.terminated && decoded.validRatio >= 0.85 && looksLikeNaturalDescription(it) }
    }

    private data class PointerRun(val offset: Int, val length: Int)
}
