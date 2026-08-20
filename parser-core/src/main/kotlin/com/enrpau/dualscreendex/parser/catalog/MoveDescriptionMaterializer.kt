package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

data class MoveDescriptionResult(
    val sourceOffset: Int,
    val confidence: Double,
    val descriptions: Map<Int, String>,
)

object MoveDescriptionMaterializer {
    fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        gbaReferenceIndex: GbaReferenceIndex? = null,
    ): MoveDescriptionResult? {
        if (layout.generation == 2) return materializeGen2(rom, layout.moveCount ?: return null)
        if (layout.generation != 3) return null
        val table = layout.tables.moveData
        val embeddedDescriptionStride = when {
            layout.pokeemeraldExpansion != null -> table?.stride ?: layout.pokeemeraldExpansion.moveRecordSize
            table?.format == TableRecordFormat.UNIFIED_MOVE_INFO_48 -> table.stride ?: return null
            else -> null
        }
        if (embeddedDescriptionStride != null) {
            val embeddedTable = table ?: return null
            val count = layout.moveCount ?: return null
            val descriptions = buildMap {
                repeat(count - 1) { index ->
                    val id = index + 1
                    val record = embeddedTable.offset + id * embeddedDescriptionStride
                    val text = rom.gbaPointer(record + 4)?.let { decodeText(rom, it) } ?: return@repeat
                    put(id, text)
                }
            }
            val expected = count - 1
            val confidence = descriptions.size.toDouble() / expected.coerceAtLeast(1)
            return MoveDescriptionResult(embeddedTable.offset, confidence, descriptions).takeIf {
                descriptions.size >= maxOf(3, (expected * 0.8).toInt())
            }
        }
        val moveCount = layout.moveCount ?: return null
        if (moveCount < 4) return null
        val pointerCount = moveCount - 1
        referencedPointerTable(rom, pointerCount, gbaReferenceIndex)?.let { return it }
        return candidateOffsets(rom, pointerCount)
            .mapNotNull { offset -> decodeCandidate(rom, offset, pointerCount) }
            .maxWithOrNull(compareBy<MoveDescriptionResult> { it.confidence }.thenBy { it.descriptions.size })
    }

    private fun referencedPointerTable(
        rom: RomImage,
        pointerCount: Int,
        references: GbaReferenceIndex?,
    ): MoveDescriptionResult? {
        if (references == null || references.overflowed) return null
        val tableBytes = pointerCount.toLong() * 4L
        val candidates = references.targets.asSequence()
            .filter { (_, evidence) -> evidence.count > 0 }
            .map { (offset, _) -> offset }
            .filter { offset ->
                offset % 4 == 0 && offset >= 0 && offset.toLong() + tableBytes <= rom.size.toLong()
            }
            .filter { offset ->
                (0 until pointerCount).all { index -> rom.gbaPointer(offset + index * 4) != null }
            }
            .mapNotNull { offset -> decodeCandidate(rom, offset, pointerCount, allowExplicitPlaceholders = true) }
            .filter { candidate -> candidate.descriptions.size == pointerCount }
            .toList()
        return candidates.singleOrNull()
    }

    private fun materializeGen2(rom: RomImage, moveCount: Int): MoveDescriptionResult? {
        if (moveCount < 4) return null
        val tableBytesLong = moveCount.toLong() * 2L
        if (tableBytesLong > GEN2_BANK_SIZE || tableBytesLong > rom.size.toLong()) return null
        val tableBytes = tableBytesLong.toInt()
        val referencedTables = gen2DescriptionTableConsumers(rom)
        val candidates = buildList {
            val bankCount = rom.size / GEN2_BANK_SIZE
            for (bank in 1 until bankCount) {
                val bankStart = bank * GEN2_BANK_SIZE
                val bankEnd = minOf(rom.size, bankStart + GEN2_BANK_SIZE)
                var offset = bankStart
                while (offset + tableBytes <= bankEnd) {
                    val address = 0x4000 + offset - bankStart
                    if (validGen2Pointer(rom.u16le(offset)) &&
                        validGen2Pointer(rom.u16le(offset + tableBytes - 2)) &&
                        (0 until moveCount).all { index -> validGen2Pointer(rom.u16le(offset + index * 2)) } &&
                        Gen2TableReference(bank, address) in referencedTables
                    ) {
                        decodeGen2Candidate(rom, offset, bank, moveCount)?.let(::add)
                    }
                    offset++
                }
            }
        }
        return candidates.singleOrNull()
    }

    /**
     * Finds the source-defined `MoveDescriptions[(moveId - 1)]` consumer.
     * Gold/Silver fetch the word through an explicit far bank; Crystal reads it
     * directly because the routine and pointer table share a bank.
     */
    private fun gen2DescriptionTableConsumers(rom: RomImage): Set<Gen2TableReference> = buildSet {
        for (offset in 0..rom.size - GEN2_CONSUMER_BYTES) {
            if (rom.u8(offset) != 0x21 ||
                rom.u8(offset + 3) != 0xFA ||
                rom.u8(offset + 6) != 0x3D ||
                rom.u8(offset + 7) != 0x4F ||
                rom.u8(offset + 8) != 0x06 || rom.u8(offset + 9) != 0 ||
                rom.u8(offset + 10) != 0x09 || rom.u8(offset + 11) != 0x09
            ) continue

            val address = rom.u16le(offset + 1)
            when {
                rom.u8(offset + 12) == 0x3E && rom.u8(offset + 14) == 0xCD -> {
                    add(Gen2TableReference(rom.u8(offset + 13), address))
                }
                rom.u8(offset + 12) == 0x2A &&
                    rom.u8(offset + 13) == 0x5F &&
                    rom.u8(offset + 14) == 0x56 -> {
                    add(Gen2TableReference(offset / GEN2_BANK_SIZE, address))
                }
            }
        }
    }

    private fun decodeGen2Candidate(
        rom: RomImage,
        offset: Int,
        bank: Int,
        moveCount: Int,
    ): MoveDescriptionResult? {
        val codec = PokemonTextCodec.gbEnglish
        val descriptions = linkedMapOf<Int, String>()
        repeat(moveCount) { index ->
            val target = rom.gbBankAddress(bank, rom.u16le(offset + index * 2)) ?: return@repeat
            val bankEnd = minOf(rom.size, (bank + 1) * GEN2_BANK_SIZE)
            val length = minOf(MAX_GEN2_DESCRIPTION_BYTES, bankEnd - target)
            if (length <= 0) return@repeat
            val decoded = codec.decodeDetailed(rom.slice(target, length))
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (decoded.terminated && decoded.validRatio >= 0.70 && looksLikeNaturalDescription(normalized)) {
                descriptions[index + 1] = normalized
            }
        }
        val minimum = maxOf(3, (moveCount * 0.8).toInt())
        if (descriptions.size < minimum) return null
        return MoveDescriptionResult(
            sourceOffset = offset,
            confidence = descriptions.size.toDouble() / moveCount,
            descriptions = descriptions,
        )
    }

    private fun validGen2Pointer(value: Int): Boolean = value in 0x4000..0x7FFF

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

    private fun decodeCandidate(
        rom: RomImage,
        offset: Int,
        pointerCount: Int,
        allowExplicitPlaceholders: Boolean = false,
    ): MoveDescriptionResult? {
        val codec = PokemonTextCodec.gbaEnglish
        val descriptions = linkedMapOf<Int, String>()
        repeat(pointerCount) { index ->
            val textOffset = runCatching { rom.gbaPointer(offset + index * 4) }.getOrNull() ?: return@repeat
            val length = minOf(192, rom.size - textOffset)
            val decoded = runCatching { codec.decodeDetailed(rom.slice(textOffset, length)) }.getOrNull() ?: return@repeat
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (
                decoded.terminated && decoded.validRatio >= 0.85 &&
                (normalized.length >= 5 || allowExplicitPlaceholders && isExplicitPlaceholder(normalized))
            ) {
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

    private fun isExplicitPlaceholder(value: String): Boolean = value == "-" || value == "—"

    private fun decodeText(rom: RomImage, offset: Int): String? {
        val length = minOf(256, rom.size - offset)
        val decoded = runCatching { PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(offset, length)) }.getOrNull()
            ?: return null
        val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
        return normalized.takeIf { decoded.terminated && decoded.validRatio >= 0.85 && looksLikeNaturalDescription(it) }
    }

    private data class PointerRun(val offset: Int, val length: Int)
    private data class Gen2TableReference(val bank: Int, val address: Int)

    private const val GEN2_BANK_SIZE = 0x4000
    private const val GEN2_CONSUMER_BYTES = 15
    private const val MAX_GEN2_DESCRIPTION_BYTES = 192
}
