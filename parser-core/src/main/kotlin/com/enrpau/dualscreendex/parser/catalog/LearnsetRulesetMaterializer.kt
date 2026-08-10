package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetEncoding
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout

object LearnsetRulesetMaterializer {
    fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        primaryEntries: Map<Int, List<LearnsetEntry>>,
    ): List<LearnsetRuleset> {
        val primaryOffset = layout.tables.learnsets?.offset ?: return emptyList()
        val primary = Candidate(primaryOffset, 1.0, primaryEntries)
        if (layout.generation != 3) return label(listOf(primary), primaryOffset)

        val speciesCount = layout.speciesCount ?: return label(listOf(primary), primaryOffset)
        val moveCount = layout.moveCount ?: return label(listOf(primary), primaryOffset)
        if (speciesCount <= 0 || moveCount <= 1) return label(listOf(primary), primaryOffset)

        val offsets = pointerRunWindows(rom, speciesCount).toMutableSet()
        offsets += primaryOffset
        val candidates = offsets.mapNotNull { offset ->
            if (offset == primaryOffset) primary else readCandidate(rom, layout, offset, speciesCount, moveCount)
        }.sortedBy { it.offset }

        val distinct = mutableListOf<Candidate>()
        candidates.forEach { candidate ->
            val existing = distinct.indexOfFirst { it.entries == candidate.entries }
            if (existing < 0) {
                distinct += candidate
            } else if (candidate.offset == primaryOffset) {
                distinct[existing] = candidate
            }
        }
        if (distinct.none { it.offset == primaryOffset }) distinct += primary
        return label(distinct.sortedBy { it.offset }, primaryOffset)
    }

    private fun pointerRunWindows(rom: RomImage, speciesCount: Int): Set<Int> {
        val tableBytes = speciesCount * 4
        val output = linkedSetOf<Int>()
        var cursor = 0
        while (cursor + 4 <= rom.size) {
            if (rom.gbaPointer(cursor) == null) {
                cursor += 4
                continue
            }
            val start = cursor
            while (cursor + 4 <= rom.size && rom.gbaPointer(cursor) != null) cursor += 4
            val length = cursor - start
            if (length >= tableBytes) {
                var window = start
                while (window + tableBytes <= cursor) {
                    output += window
                    window += tableBytes
                }
            }
        }
        return output
    }

    private fun readCandidate(
        rom: RomImage,
        layout: ResolvedRomLayout,
        offset: Int,
        speciesCount: Int,
        moveCount: Int,
    ): Candidate? {
        if (offset < 0 || offset.toLong() + speciesCount.toLong() * 4 > rom.size) return null
        val entries = linkedMapOf<Int, List<LearnsetEntry>>()
        var valid = 0
        repeat(speciesCount) { speciesId ->
            val target = runCatching { rom.gbaPointer(offset + speciesId * 4) }.getOrNull()
            val decoded = target?.let { readLearnset(rom, it, layout.tables.learnsets?.elementSize, moveCount) }
            if (decoded != null) {
                entries[speciesId] = decoded
                valid++
            }
        }
        val confidence = valid.toDouble() / speciesCount
        return if (confidence >= 0.9 && entries.values.sumOf { it.size } >= speciesCount) {
            Candidate(offset, confidence, entries)
        } else {
            null
        }
    }

    private fun readLearnset(rom: RomImage, offset: Int, elementSize: Int?, moveCount: Int): List<LearnsetEntry>? =
        runCatching {
            val output = mutableListOf<LearnsetEntry>()
            var cursor = offset
            var previousLevel = 0
            repeat(128) {
                if (elementSize == 3) {
                    val move = rom.u16le(cursor)
                    val level = rom.u8(cursor + 2)
                    if (move == 0 && level == 0xFF) return output
                    require(move in 1 until moveCount && level in 0..100 && level >= previousLevel)
                    output += LearnsetEntry(level, move)
                    previousLevel = level
                    cursor += 3
                } else {
                    val packed = rom.u16le(cursor)
                    if (packed == 0xFFFF) return output
                    val moveBits = Gen3LearnsetEncoding.packedMoveBits(moveCount)
                    val move = packed and ((1 shl moveBits) - 1)
                    val level = packed ushr moveBits
                    require(move in 1 until moveCount && level in 0..100 && level >= previousLevel)
                    output += LearnsetEntry(level, move)
                    previousLevel = level
                    cursor += 2
                }
            }
            null
        }.getOrNull()

    private fun label(candidates: List<Candidate>, primaryOffset: Int): List<LearnsetRuleset> {
        val byEntryCount = candidates.sortedWith(compareBy<Candidate> { it.entries.values.sumOf(List<LearnsetEntry>::size) }.thenBy { it.offset })
        val labels = if (candidates.size == 1) {
            mapOf(candidates.single().offset to "Default")
        } else {
            byEntryCount.mapIndexed { index, candidate ->
                candidate.offset to if (index == 0) "Base" else "Expanded $index"
            }.toMap()
        }
        return candidates.map { candidate ->
            LearnsetRuleset(
                id = "ruleset-${candidate.offset.toString(16).padStart(8, '0')}",
                label = labels.getValue(candidate.offset),
                sourceOffset = candidate.offset,
                confidence = candidate.confidence,
                entriesBySpecies = candidate.entries,
                primary = candidate.offset == primaryOffset,
            )
        }
    }

    private data class Candidate(
        val offset: Int,
        val confidence: Double,
        val entries: Map<Int, List<LearnsetEntry>>,
    )
}
