package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetTableLayout
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout

object LearnsetRulesetMaterializer {
    fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        primaryEntries: Map<Int, List<LearnsetEntry>>,
    ): List<LearnsetRuleset> {
        val primaryTable = layout.tables.learnsets
        val primaryOffset = primaryTable?.offset
        val primary = primaryTable?.let { Candidate(it, 1.0, primaryEntries) }
        if (layout.generation != 3) {
            return primary?.let { label(listOf(it), primaryOffset, staticPrimary = true) }.orEmpty()
        }
        if (layout.pokeemeraldExpansion == null) {
            val typed = layout.resolvedDatasets.learnsets ?: return emptyList()
            val candidates = typed.catalogRulesetCandidates().map { resolved ->
                val table = resolved.layout.table
                Candidate(
                    TableLayout(
                        offset = table.offset.toInt(),
                        count = table.speciesCount,
                        recordSize = 4,
                        variableLength = true,
                        elementSize = table.format.entrySize,
                    ),
                    resolved.confidence,
                    resolved.catalogEntries(),
                )
            }
            return label(
                distinctCandidates(candidates, layout, primaryOffset),
                primaryOffset,
                staticPrimary = candidates.size == 1,
                selectorEvidence = layout.learnsetSelector,
            )
        }

        val speciesCount = layout.speciesCount
            ?: return primary?.let { label(listOf(it), primaryOffset) }.orEmpty()
        val moveCount = layout.moveCount
            ?: return primary?.let { label(listOf(it), primaryOffset) }.orEmpty()
        if (speciesCount <= 0 || moveCount <= 1 || speciesCount.toLong() * 4 > rom.size.toLong()) return emptyList()

        val providedTables = layout.learnsetTables.ifEmpty {
            primaryTable?.let { listOf(Gen3LearnsetTableLayout(it, 1.0, 0)) }.orEmpty()
        }
        if (providedTables.isEmpty()) return emptyList()
        if (providedTables.size > MAX_RULESET_TABLES) return emptyList()
        val candidates = providedTables.mapNotNull { resolved ->
            if (primary != null && resolved.table.offset == primaryOffset) {
                primary.copy(table = resolved.table, confidence = resolved.confidence)
            } else {
                readCandidate(rom, layout, resolved, speciesCount)
            }
        }.sortedBy { it.offset }

        val distinct = distinctCandidates(candidates, layout, primaryOffset).toMutableList()
        if (primary != null && distinct.none { it.offset == primaryOffset }) distinct += primary
        return label(
            distinct.sortedBy { it.offset },
            primaryOffset,
            staticPrimary = distinct.size == 1,
            selectorEvidence = layout.learnsetSelector,
        )
    }

    private fun distinctCandidates(
        candidates: List<Candidate>,
        layout: ResolvedRomLayout,
        primaryOffset: Int?,
    ): List<Candidate> {
        val distinct = mutableListOf<Candidate>()
        candidates.sortedBy { it.offset }.forEach { candidate ->
            val existing = distinct.indexOfFirst { it.entries == candidate.entries }
            val selectorLinked = layout.learnsetSelector?.let { selector ->
                candidate.offset == selector.zeroTableOffset || candidate.offset == selector.nonZeroTableOffset
            } == true
            if (existing < 0 || selectorLinked) {
                distinct += candidate
            } else if (candidate.offset == primaryOffset) {
                distinct[existing] = candidate
            }
        }
        return distinct.sortedBy { it.offset }
    }

    private fun readCandidate(
        rom: RomImage,
        layout: ResolvedRomLayout,
        resolved: Gen3LearnsetTableLayout,
        speciesCount: Int,
    ): Candidate? {
        val table = resolved.table
        if (table.offset < 0 || table.offset.toLong() + speciesCount.toLong() * 4 > rom.size.toLong()) return null
        val candidateLayout = layout.copy(tables = layout.tables.copy(learnsets = table))
        val entries = RelationshipMaterializers.learnsets(rom, candidateLayout)
        val confidence = entries.size.toDouble() / speciesCount
        return if (confidence >= 0.9 && entries.values.sumOf { it.size } >= speciesCount) {
            Candidate(table, minOf(resolved.confidence, confidence), entries)
        } else {
            null
        }
    }

    private fun label(
        candidates: List<Candidate>,
        primaryOffset: Int?,
        staticPrimary: Boolean = candidates.size == 1,
        selectorEvidence: com.enrpau.dualscreendex.parser.model.Gen3LearnsetSelectorEvidence? = null,
    ): List<LearnsetRuleset> {
        val byEntryCount = candidates.sortedWith(compareBy<Candidate> { it.entries.values.sumOf(List<LearnsetEntry>::size) }.thenBy { it.offset })
        val labels = if (candidates.size == 1) {
            mapOf(candidates.single().offset to "Default")
        } else {
            byEntryCount.mapIndexed { index, candidate ->
                candidate.offset to if (index == 0) "Base" else "Expanded $index"
            }.toMap()
        }
        return candidates.map { candidate ->
            val selector = layoutSelector(candidates, candidate.offset, selectorEvidence)
            LearnsetRuleset(
                id = "ruleset-${candidate.offset.toString(16).padStart(8, '0')}",
                label = labels.getValue(candidate.offset),
                sourceOffset = candidate.offset,
                confidence = candidate.confidence,
                entriesBySpecies = candidate.entries,
                primary = staticPrimary && candidate.offset == primaryOffset,
                levelUpSelector = selector,
            )
        }
    }

    private fun layoutSelector(
        candidates: List<Candidate>,
        offset: Int,
        selector: com.enrpau.dualscreendex.parser.model.Gen3LearnsetSelectorEvidence?,
    ): LevelUpRulesetSelector? {
        selector ?: return null
        if (candidates.none { it.offset == selector.zeroTableOffset } ||
            candidates.none { it.offset == selector.nonZeroTableOffset }
        ) return null
        val expected = when (offset) {
            selector.zeroTableOffset -> 0
            selector.nonZeroTableOffset -> selector.mask
            else -> return null
        }
        return LevelUpRulesetSelector(selector.saveBlock1ByteOffset, selector.mask, expected)
    }

    private data class Candidate(
        val table: TableLayout,
        val confidence: Double,
        val entries: Map<Int, List<LearnsetEntry>>,
    ) {
        val offset: Int get() = table.offset
    }

    private const val MAX_RULESET_TABLES = 16
}
