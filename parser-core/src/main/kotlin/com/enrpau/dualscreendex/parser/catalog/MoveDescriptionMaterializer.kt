package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility
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
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        limits: ResolutionLimits = ResolutionLimits(),
    ): MoveDescriptionResult? {
        cancellation.throwIfCancellationRequested()
        val codec = layout.defaultTextCodec() ?: return null
        val budget = MoveDescriptionBudget(limits)
        return try {
            materializeBounded(rom, layout, codec, gbaReferenceIndex, cancellation, budget)
        } catch (_: MoveDescriptionBudgetExceededException) {
            null
        }
    }

    private fun materializeBounded(
        rom: RomImage,
        layout: ResolvedRomLayout,
        codec: PokemonTextCodec,
        gbaReferenceIndex: GbaReferenceIndex?,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        if (layout.generation == 2) {
            return materializeGen2(rom, codec, layout.moveCount ?: return null, cancellation, budget)
        }
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
                    checkCancellation(index, cancellation)
                    budget.recordWork()
                    val id = index + 1
                    val record = embeddedTable.offset + id * embeddedDescriptionStride
                    val text = rom.gbaPointer(record + 4)?.let { decodeText(rom, it, codec) } ?: return@repeat
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
        referencedPointerTable(rom, codec, pointerCount, gbaReferenceIndex, cancellation, budget)?.let { return it }
        return fallbackPointerTable(rom, codec, pointerCount, cancellation, budget)
    }

    private fun referencedPointerTable(
        rom: RomImage,
        codec: PokemonTextCodec,
        pointerCount: Int,
        references: GbaReferenceIndex?,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        if (references == null || references.overflowed) return null
        val tableBytes = pointerCount.toLong() * 4L
        var selected: MoveDescriptionResult? = null
        var inspected = 0
        for ((offset, evidence) in references.targets) {
            checkCancellation(inspected++, cancellation)
            if (evidence.count <= 0 || offset % 4 != 0 || offset < 0 ||
                offset.toLong() + tableBytes > rom.size.toLong()
            ) continue
            budget.recordRoot(offset)
            var pointersValid = true
            for (index in 0 until pointerCount) {
                checkCancellation(index, cancellation)
                budget.recordWork()
                if (rom.gbaPointer(offset + index * 4) == null) {
                    pointersValid = false
                    break
                }
            }
            if (!pointersValid) continue
            budget.recordCandidate()
            val candidate = decodeCandidate(
                rom,
                codec,
                offset,
                pointerCount,
                cancellation,
                budget,
                allowExplicitPlaceholders = true,
            )?.takeIf { it.descriptions.size == pointerCount } ?: continue
            if (selected != null) return null
            selected = candidate
        }
        return selected
    }

    private fun materializeGen2(
        rom: RomImage,
        codec: PokemonTextCodec,
        moveCount: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        if (moveCount < 4) return null
        val tableBytesLong = moveCount.toLong() * 2L
        if (tableBytesLong > GEN2_BANK_SIZE || tableBytesLong > rom.size.toLong()) return null
        val tableBytes = tableBytesLong.toInt()
        val referencedTables = gen2DescriptionTableConsumers(rom, cancellation, budget)
        if (referencedTables.isEmpty()) return null
        var selected: MoveDescriptionResult? = null
        for (reference in referencedTables) {
            cancellation.throwIfCancellationRequested()
            val offset = rom.gbBankAddress(reference.bank, reference.address) ?: continue
            val bankEnd = minOf(rom.size, (reference.bank + 1) * GEN2_BANK_SIZE)
            if (offset.toLong() + tableBytes > bankEnd.toLong()) continue
            var pointersValid = true
            for (index in 0 until moveCount) {
                checkCancellation(index, cancellation)
                budget.recordWork()
                if (!validGen2Pointer(rom.u16le(offset + index * 2))) {
                    pointersValid = false
                    break
                }
            }
            if (!pointersValid) continue
            budget.recordCandidate()
            val candidate = decodeGen2Candidate(
                rom,
                codec,
                offset,
                reference.bank,
                moveCount,
                cancellation,
                budget,
            ) ?: continue
            if (selected != null) return null
            selected = candidate
        }
        return selected
    }

    /** Finds source-defined `MoveDescriptions[(moveId - 1)]` consumers. */
    private fun gen2DescriptionTableConsumers(
        rom: RomImage,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): Set<Gen2TableReference> {
        val references = linkedSetOf<Gen2TableReference>()
        for (offset in 0..rom.size - GEN2_CONSUMER_BYTES) {
            if (offset % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            budget.recordScanBytes(1)
            if (rom.u8(offset) != 0x21) continue
            budget.recordMatch()
            if (rom.u8(offset + 3) != 0xFA ||
                rom.u8(offset + 6) != 0x3D ||
                rom.u8(offset + 7) != 0x4F ||
                rom.u8(offset + 8) != 0x06 || rom.u8(offset + 9) != 0 ||
                rom.u8(offset + 10) != 0x09 || rom.u8(offset + 11) != 0x09
            ) continue

            val address = rom.u16le(offset + 1)
            val reference = when {
                rom.u8(offset + 12) == 0x3E && rom.u8(offset + 14) == 0xCD -> {
                    Gen2TableReference(rom.u8(offset + 13), address)
                }
                rom.u8(offset + 12) == 0x2A &&
                    rom.u8(offset + 13) == 0x5F &&
                    rom.u8(offset + 14) == 0x56 -> {
                    Gen2TableReference(offset / GEN2_BANK_SIZE, address)
                }
                else -> null
            } ?: continue
            val root = rom.gbBankAddress(reference.bank, reference.address) ?: continue
            budget.recordRoot(root)
            references += reference
        }
        return references
    }

    private fun decodeGen2Candidate(
        rom: RomImage,
        codec: PokemonTextCodec,
        offset: Int,
        bank: Int,
        moveCount: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        val descriptions = linkedMapOf<Int, String>()
        repeat(moveCount) { index ->
            checkCancellation(index, cancellation)
            budget.recordWork()
            val target = rom.gbBankAddress(bank, rom.u16le(offset + index * 2)) ?: return@repeat
            val bankEnd = minOf(rom.size, (bank + 1) * GEN2_BANK_SIZE)
            val length = minOf(MAX_GEN2_DESCRIPTION_BYTES, bankEnd - target)
            if (length <= 0) return@repeat
            val decoded = codec.decodeDetailed(rom.slice(target, length))
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (
                decoded.terminated && decoded.validRatio >= 0.70 &&
                looksLikeNaturalDescription(normalized, codec)
            ) {
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

    private fun fallbackPointerTable(
        rom: RomImage,
        codec: PokemonTextCodec,
        pointerCount: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
    ): MoveDescriptionResult? {
        val tableBytesLong = pointerCount.toLong() * 4L
        if (tableBytesLong > rom.size.toLong()) return null
        val minimumPrefixBytes = (((pointerCount.toLong() + 1L) / 2L) * 4L).toInt()
        val tableBytes = tableBytesLong.toInt()
        var best: MoveDescriptionResult? = null
        var cursor = 0

        fun inspectCandidate(offset: Int) {
            budget.recordRoot(offset)
            budget.recordCandidate()
            val candidate = decodeCandidate(rom, codec, offset, pointerCount, cancellation, budget) ?: return
            val current = best
            if (current == null || MOVE_DESCRIPTION_ORDER.compare(candidate, current) > 0) best = candidate
        }

        while (cursor + 4 <= rom.size) {
            if (cursor % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            budget.recordScanBytes(4)
            if (rom.gbaPointer(cursor) == null) {
                cursor += 4
                continue
            }
            val runStart = cursor
            var runLength = 0
            while (cursor + 4 <= rom.size && rom.gbaPointer(cursor) != null) {
                if (cursor % RomImage.DEFAULT_SCAN_CHECK_INTERVAL_BYTES == 0) {
                    cancellation.throwIfCancellationRequested()
                }
                budget.recordScanBytes(4)
                cursor += 4
                runLength += 4
                if (runLength >= tableBytes && runLength % tableBytes == 0) {
                    inspectCandidate(runStart + runLength - tableBytes)
                }
            }
            if (runLength in minimumPrefixBytes until tableBytes) inspectCandidate(runStart)
        }
        cancellation.throwIfCancellationRequested()
        return best
    }

    private fun decodeCandidate(
        rom: RomImage,
        codec: PokemonTextCodec,
        offset: Int,
        pointerCount: Int,
        cancellation: ParserCancellationToken,
        budget: MoveDescriptionBudget,
        allowExplicitPlaceholders: Boolean = false,
    ): MoveDescriptionResult? {
        val descriptions = linkedMapOf<Int, String>()
        repeat(pointerCount) { index ->
            checkCancellation(index, cancellation)
            budget.recordWork()
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
        val naturalDescriptionCount = descriptions.values.count { looksLikeNaturalDescription(it, codec) }
        val naturalLanguageRatio = naturalDescriptionCount.toDouble() / descriptions.size.coerceAtLeast(1)
        val confidence = minOf(decodedRatio, naturalLanguageRatio)
        val minimum = maxOf(3, (pointerCount * 0.8).toInt())
        return if (descriptions.size >= minimum && naturalLanguageRatio >= 0.75) {
            MoveDescriptionResult(offset, confidence, descriptions)
        } else {
            null
        }
    }

    private fun looksLikeNaturalDescription(value: String, codec: PokemonTextCodec): Boolean =
        LanguageTextPlausibility.looksLikeNaturalDescription(
            value = value,
            language = codec.language,
            minimumLength = 12,
            minimumWords = 3,
            requireLowercase = true,
        )

    private fun isExplicitPlaceholder(value: String): Boolean = value == "-" || value == "—"

    private fun decodeText(rom: RomImage, offset: Int, codec: PokemonTextCodec): String? {
        val length = minOf(256, rom.size - offset)
        val decoded = runCatching { codec.decodeDetailed(rom.slice(offset, length)) }.getOrNull()
            ?: return null
        val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
        return normalized.takeIf {
            decoded.terminated && decoded.validRatio >= 0.85 && looksLikeNaturalDescription(it, codec)
        }
    }

    private fun checkCancellation(index: Int, cancellation: ParserCancellationToken) {
        if (index % CANCELLATION_CHECK_RECORD_INTERVAL == 0) cancellation.throwIfCancellationRequested()
    }

    private class MoveDescriptionBudget(private val limits: ResolutionLimits) {
        private val roots = linkedSetOf<Int>()
        private var matches = 0
        private var candidates = 0
        private var work = 0L
        private var scanBytes = 0L

        fun recordRoot(root: Int) {
            if (root in roots) return
            if (roots.size == limits.maxProbeRootsPerDataset) throw MoveDescriptionBudgetExceededException()
            roots += root
        }

        fun recordMatch() {
            if (matches == limits.maxProbeWorkPerDataset) throw MoveDescriptionBudgetExceededException()
            matches++
        }

        fun recordCandidate() {
            if (candidates == limits.maxCandidatesPerDataset) throw MoveDescriptionBudgetExceededException()
            candidates++
        }

        fun recordWork() {
            if (work == limits.maxProbeWorkPerDataset.toLong()) throw MoveDescriptionBudgetExceededException()
            work++
        }

        fun recordScanBytes(bytes: Int) {
            if (scanBytes > limits.maxDatasetExtentBytes - bytes) throw MoveDescriptionBudgetExceededException()
            scanBytes += bytes
        }
    }

    private class MoveDescriptionBudgetExceededException : RuntimeException()

    private data class Gen2TableReference(val bank: Int, val address: Int)

    private const val GEN2_BANK_SIZE = 0x4000
    private const val GEN2_CONSUMER_BYTES = 15
    private const val MAX_GEN2_DESCRIPTION_BYTES = 192
    private const val CANCELLATION_CHECK_RECORD_INTERVAL = 64
    private val MOVE_DESCRIPTION_ORDER =
        compareBy<MoveDescriptionResult> { it.confidence }.thenBy { it.descriptions.size }
}
