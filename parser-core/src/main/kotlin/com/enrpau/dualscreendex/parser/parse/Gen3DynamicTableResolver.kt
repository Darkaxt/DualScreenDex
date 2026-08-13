package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.validate.TableValidators

/** Resolves referenced CFRU/DPE data tables without assuming which published pointer slot a fork retained. */
object Gen3DynamicTableResolver {
    internal fun reconcileSpeciesExtent(
        rom: RomImage,
        tables: ProfileTables,
        proposedCount: Int,
        references: GbaCompiledReferenceIndex?,
        referenceSites: GbaReferenceIndex? = null,
    ): Gen3SpeciesExtentResolution {
        val names = tables.speciesNames ?: return Gen3SpeciesExtentResolution(proposedCount, tables)
        val stats = tables.baseStats ?: return Gen3SpeciesExtentResolution(proposedCount, tables)
        if (proposedCount <= MINIMUM_RECONCILED_SPECIES_COUNT ||
            names.recordSize <= 0 || stats.recordSize <= 0 || references == null
        ) {
            return Gen3SpeciesExtentResolution(proposedCount, tables)
        }
        reconcileSparseInactiveSpeciesSuffix(
            rom = rom,
            tables = tables,
            proposedCount = proposedCount,
            referenceSites = referenceSites,
        )?.let { return it }
        val mapOffset = references.counts.keys.singleOrNull { candidate ->
            credibleSpeciesToDexMap(rom, names, proposedCount, candidate)
        } ?: return Gen3SpeciesExtentResolution(proposedCount, tables)
        val values = IntArray(proposedCount - 1) { index -> rom.u16le(mapOffset + index * 2) }
        val abilityUpperBound = tables.abilities?.count?.minus(1)

        for (boundary in MINIMUM_RECONCILED_SPECIES_COUNT until proposedCount) {
            val prior = values.copyOfRange(0, boundary - 1).filterTo(hashSetOf()) { it > 0 }
            val suffix = values.copyOfRange(boundary - 1, values.size)
            if (suffix.isEmpty() || suffix.any { it <= 0 || it !in prior }) continue
            val suffixCount = proposedCount - boundary
            val validStats = (boundary until proposedCount).count { index ->
                validStatsRow(rom, stats, index, abilityUpperBound)
            }
            if (validStats * MINIMUM_INCOHERENT_SUFFIX_DENOMINATOR >=
                suffixCount * MAXIMUM_INCOHERENT_SUFFIX_VALID_NUMERATOR
            ) {
                continue
            }
            if (!namesAliasKnownNonSpeciesTable(rom, tables, names, boundary, suffixCount)) continue
            if (!TableValidators.baseStats(rom, stats.offset, boundary, stats.recordSize, generation = 3).compatible) {
                continue
            }
            return Gen3SpeciesExtentResolution(
                boundary,
                tables.copy(
                    speciesNames = names.copy(count = boundary),
                    baseStats = stats.copy(count = boundary),
                    sprites = tables.sprites?.copy(count = minOf(tables.sprites.count, boundary)),
                    evolutions = tables.evolutions?.copy(count = minOf(tables.evolutions.count, boundary)),
                    learnsets = tables.learnsets?.copy(count = minOf(tables.learnsets.count, boundary)),
                ),
            )
        }
        return Gen3SpeciesExtentResolution(proposedCount, tables)
    }

    /**
     * A decompilation engine may retain a generously sized zero tail after its active species
     * domain. The source-defined SpeciesToNationalPokedexNum consumer indexes a u16 table with
     * `(species - 1) * 2`; only that compiled address formation can authorize the mapped positive
     * prefix. Names and base stats must independently validate the same prefix and an inactive next
     * row. Ordinary literal references are not authority.
     */
    private fun reconcileSparseInactiveSpeciesSuffix(
        rom: RomImage,
        tables: ProfileTables,
        proposedCount: Int,
        referenceSites: GbaReferenceIndex?,
    ): Gen3SpeciesExtentResolution? {
        val names = tables.speciesNames ?: return null
        val stats = tables.baseStats ?: return null
        val index = referenceSites ?: return null
        val candidates = index.targets.mapNotNull { (mapOffset, evidence) ->
            if (!evidence.siteEvidenceAvailable ||
                evidence.count < MINIMUM_DIRECT_SPECIES_TO_DEX_REFERENCES ||
                evidence.instructionSites.none { site -> hasLeafSpeciesToDexLookup(rom, site) }
            ) {
                return@mapNotNull null
            }
            val boundary = sparseMappedSpeciesBoundary(rom, mapOffset, proposedCount) ?: return@mapNotNull null
            val nameEvidence = TableValidators.names(
                rom,
                names.copy(count = boundary),
                boundary,
                com.enrpau.dualscreendex.parser.text.PokemonTextCodec.gbaEnglish,
            )
            if (!nameEvidence.compatible) return@mapNotNull null
            val recordSize = TableValidators.inferBaseStatsRecordSize(
                rom,
                stats.offset,
                boundary,
                generation = 3,
            ) ?: return@mapNotNull null
            val statsEvidence = TableValidators.baseStats(
                rom,
                stats.offset,
                boundary,
                recordSize,
                generation = 3,
            )
            if (!statsEvidence.compatible ||
                !speciesDomainEndsAt(rom, names, stats, boundary, recordSize)
            ) return@mapNotNull null
            SparseSpeciesExtentCandidate(boundary, recordSize)
        }
        val selected = candidates.singleOrNull() ?: return null
        return Gen3SpeciesExtentResolution(
            selected.boundary,
            tables.copy(
                speciesNames = names.copy(count = selected.boundary),
                baseStats = stats.copy(count = selected.boundary, recordSize = selected.baseStatsRecordSize),
                sprites = tables.sprites?.copy(count = minOf(tables.sprites.count, selected.boundary)),
                evolutions = tables.evolutions?.copy(count = minOf(tables.evolutions.count, selected.boundary)),
                learnsets = tables.learnsets?.copy(count = minOf(tables.learnsets.count, selected.boundary)),
            ),
        )
    }

    private fun sparseMappedSpeciesBoundary(rom: RomImage, offset: Int, proposedCount: Int): Int? {
        val storedCount = proposedCount - 1
        if (storedCount < MINIMUM_RECONCILED_SPECIES_COUNT || offset < 0 ||
            offset.toLong() + storedCount * 2L > rom.size.toLong()
        ) {
            return null
        }
        val firstZero = (0 until storedCount).firstOrNull { index -> rom.u16le(offset + index * 2) == 0 }
            ?: return null
        val boundary = firstZero + 1
        if (boundary <= MINIMUM_RECONCILED_SPECIES_COUNT ||
            rom.u16le(offset) != 1 || rom.u16le(offset + 2) != 2 ||
            (0 until firstZero).any { index -> rom.u16le(offset + index * 2) == 0 }
        ) {
            return null
        }
        val positive = (0 until firstZero).map { index -> rom.u16le(offset + index * 2) }
        if (positive.distinct().size * 100 < positive.size * MINIMUM_MAPPED_ID_UNIQUENESS_PERCENT) return null
        return boundary
    }

    /** Names and stats independently stop at the compiled mapping's active species boundary. */
    private fun speciesDomainEndsAt(
        rom: RomImage,
        names: TableLayout,
        stats: TableLayout,
        boundary: Int,
        baseStatsRecordSize: Int,
    ): Boolean {
        val nextName = TableValidators.names(
            rom,
            names.copy(offset = names.offset + boundary * names.recordSize, count = 1),
            1,
            com.enrpau.dualscreendex.parser.text.PokemonTextCodec.gbaEnglish,
        )
        if (nextName.validRecords != 0) return false
        val nextStatsOffset = stats.offset.toLong() + boundary.toLong() * baseStatsRecordSize
        if (nextStatsOffset < 0 || nextStatsOffset + baseStatsRecordSize > rom.size.toLong()) return false
        return rom.slice(nextStatsOffset.toInt(), baseStatsRecordSize).all { it == 0.toByte() }
    }

    /** Recognizes the complete leaf function for `SpeciesToNationalPokedexNum`. */
    private fun hasLeafSpeciesToDexLookup(rom: RomImage, literalSite: Int): Boolean {
        if (literalSite < LEAF_SPECIES_TO_DEX_PREFIX_BYTES || literalSite + LEAF_SPECIES_TO_DEX_SUFFIX_BYTES > rom.size) {
            return false
        }
        val functionStart = literalSite - LEAF_SPECIES_TO_DEX_PREFIX_BYTES
        if (rom.u16le(functionStart) and 0xFF00 != 0xB500 ||
            rom.u16le(literalSite - 4) and 0xF800 != 0x2800 ||
            rom.u16le(literalSite - 2) and 0xFF00 != 0xD000
        ) {
            return false
        }
        if (literalSite !in 0..rom.size - 2) return false
        val literalLoad = rom.u16le(literalSite)
        if (literalLoad and THUMB_LITERAL_LOAD_MASK != THUMB_LITERAL_LOAD_OPCODE) return false
        val rootRegister = (literalLoad ushr 8) and 0x7
        var indexRegister: Int? = null
        var scaled = false
        var addressRegister: Int? = null
        val end = minOf(rom.size - 2, literalSite + DIRECT_SPECIES_TO_DEX_WINDOW_BYTES)
        var offset = literalSite + 2
        while (offset <= end) {
            val instruction = rom.u16le(offset)
            when {
                instruction and 0xF800 == 0x3800 && instruction and 0xFF == 1 -> {
                    indexRegister = (instruction ushr 8) and 0x7
                    scaled = false
                    addressRegister = null
                }
                indexRegister != null && instruction and 0xF800 == 0 &&
                    (instruction ushr 6) and 0x1F == 1 &&
                    (instruction ushr 3) and 0x7 == indexRegister && instruction and 0x7 == indexRegister -> {
                    scaled = true
                }
                indexRegister != null && scaled && instruction and 0xFE00 == 0x1800 -> {
                    val left = (instruction ushr 3) and 0x7
                    val right = (instruction ushr 6) and 0x7
                    if ((left == rootRegister && right == indexRegister) ||
                        (right == rootRegister && left == indexRegister)
                    ) {
                        addressRegister = instruction and 0x7
                    }
                }
                addressRegister != null && instruction and 0xF800 == 0x8800 &&
                    (instruction ushr 6) and 0x1F == 0 &&
                    (instruction ushr 3) and 0x7 == addressRegister -> {
                    return hasLeafReturnSequence(rom, offset)
                }
                instruction and 0xF000 == 0xD000 || instruction and 0xF800 == 0xE000 -> return false
            }
            offset += 2
        }
        return false
    }

    private fun hasLeafReturnSequence(rom: RomImage, loadOffset: Int): Boolean {
        val end = minOf(rom.size - 2, loadOffset + LEAF_SPECIES_TO_DEX_SUFFIX_BYTES)
        var sawZeroReturn = false
        var offset = loadOffset + 2
        while (offset <= end) {
            val instruction = rom.u16le(offset)
            if (instruction and 0xFF00 == 0x2000 && instruction and 0xFF == 0) sawZeroReturn = true
            if (sawZeroReturn && instruction and 0xFF00 == 0xBC00) {
                val next = rom.u16le(offset + 2)
                if (next and 0xFF87 == 0x4700) return true
            }
            if (instruction and 0xF800 == 0xF000 || instruction and 0xF800 == 0xF800) return false
            offset += 2
        }
        return false
    }

    fun resolve(
        rom: RomImage,
        inherited: ProfileTables,
        speciesCount: Int,
        moveCount: Int,
    ): ProfileTables = resolveWithEvidence(rom, inherited, speciesCount, moveCount).tables

    internal fun resolveWithEvidence(
        rom: RomImage,
        inherited: ProfileTables,
        speciesCount: Int,
        moveCount: Int,
    ): Gen3DynamicTableResolution {
        val inheritedStatsEvidence = inherited.baseStats?.let {
            TableValidators.baseStats(rom, it.offset, speciesCount, it.recordSize, generation = 3)
        }
        val statsValid = inheritedStatsEvidence?.compatible == true
        val standardMoveEvidence = inherited.moveData?.let {
            TableValidators.moveData(rom, it.offset, moveCount, it.recordSize, generation = 3)
        }
        val standardMovesValid = standardMoveEvidence?.compatible == true
        if (statsValid && standardMovesValid) {
            return Gen3DynamicTableResolution(inherited, inheritedStatsEvidence, standardMoveEvidence)
        }

        val referenced = referencedTargets(rom, speciesCount, moveCount)
        if (referenced.overflowEvidence != null) {
            return Gen3DynamicTableResolution(
                tables = inherited.copy(
                    baseStats = inherited.baseStats.takeIf { statsValid },
                    moveData = inherited.moveData.takeIf { standardMovesValid },
                ),
                baseStatsEvidence = inheritedStatsEvidence.takeIf { statsValid } ?: referenced.overflowEvidence,
                moveDataEvidence = standardMoveEvidence.takeIf { standardMovesValid } ?: referenced.overflowEvidence,
            )
        }
        val references = referenced.referenceCounts
        val candidates = buildSet {
            addAll(references.keys)
            PUBLISHED_POINTER_SLOTS.mapNotNullTo(this) { slot ->
                if (slot + 4 <= rom.size) rom.gbaPointer(slot) else null
            }
            inherited.baseStats?.offset?.let(::add)
            inherited.moveData?.offset?.let(::add)
        }

        val statsCandidate = if (statsValid) inheritedStatsEvidence else candidates.asSequence()
            .filter { it % 4 == 0 && it.toLong() + speciesCount.toLong() * 28 <= rom.size }
            .mapNotNull { offset ->
                if (!plausibleStatsSample(rom, offset, speciesCount)) return@mapNotNull null
                val evidence = TableValidators.baseStats(rom, offset, speciesCount, 28, generation = 3)
                if (evidence.compatible) evidence to (references[offset] ?: 0) else null
            }
            .sortedWith(candidateOrder)
            .firstOrNull()
            ?.first
        val stats = if (statsValid) inherited.baseStats else statsCandidate?.toLayout()

        val moveCandidates = if (standardMovesValid) emptyList() else candidates.asSequence()
            .filter { it % 4 == 0 }
            .flatMap { offset ->
                buildList {
                    if (offset.toLong() + moveCount.toLong() * 16 <= rom.size &&
                        plausibleCfruMoveSample(rom, offset, moveCount)
                    ) {
                        val evidence = TableValidators.cfruMoveData(rom, offset, moveCount)
                        if (evidence.compatible) {
                            add(DynamicMoveCandidate(evidence, references[offset] ?: 0, TableRecordFormat.CFRU_MOVE_16))
                        }
                    }
                    if (offset.toLong() + moveCount.toLong() * 20 <= rom.size &&
                        plausibleBattleEngineMoveSample(rom, offset, moveCount)
                    ) {
                        val evidence = TableValidators.battleEngineMoveData(rom, offset, moveCount)
                        if (evidence.compatible) {
                            add(DynamicMoveCandidate(evidence, references[offset] ?: 0, TableRecordFormat.BATTLE_ENGINE_MOVE_20))
                        }
                    }
                }.asSequence()
            }
            .sortedWith(moveCandidateOrder)
            .toList()
        val moveSelection = when {
            standardMovesValid -> MoveCandidateSelection(inherited.moveData, standardMoveEvidence)
            moveCandidates.isEmpty() -> MoveCandidateSelection(inherited.moveData, null)
            else -> selectMoveCandidate(moveCandidates)
        }

        return Gen3DynamicTableResolution(
            tables = inherited.copy(
                baseStats = stats ?: inherited.baseStats,
                moveData = moveSelection.layout,
            ),
            baseStatsEvidence = statsCandidate,
            moveDataEvidence = moveSelection.evidence,
        )
    }

    private fun selectMoveCandidate(candidates: List<DynamicMoveCandidate>): MoveCandidateSelection {
        val winner = candidates.first()
        val equallyCredible = candidates.filter {
            it.evidence.confidence == winner.evidence.confidence && it.references == winner.references
        }
        if (equallyCredible.size == 1) {
            return MoveCandidateSelection(winner.evidence.toLayout(winner.format), winner.evidence)
        }
        return MoveCandidateSelection(
            layout = null,
            evidence = ValidationEvidence(
                compatible = false,
                validRecords = 0,
                totalRecords = equallyCredible.size,
                confidence = 0.0,
                reasons = listOf(
                    "ambiguous expanded move-data roots: " + equallyCredible.joinToString { candidate ->
                        "0x${requireNotNull(candidate.evidence.offset).toString(16)}/${candidate.format}"
                    },
                ),
                ambiguous = true,
                reviewRecommended = true,
            ),
        )
    }

    private fun referencedTargets(rom: RomImage, speciesCount: Int, moveCount: Int): ReferencedTargetResolution {
        val counts = HashMap<Int, Int>()
        var qualifiedCandidates = 0
        var offset = 0
        while (offset <= rom.size - 4) {
            val target = rom.gbaPointer(offset)
            if (target != null && target % 4 == 0) {
                if (target !in counts && !plausibleDynamicTarget(rom, target, speciesCount, moveCount)) {
                    offset += 4
                    continue
                }
                if (target !in counts && counts.size >= MAX_DYNAMIC_PREFILTER_SHAPES) {
                    return ReferencedTargetResolution(
                        referenceCounts = emptyMap(),
                        overflowEvidence = ValidationEvidence(
                            compatible = false,
                            validRecords = 0,
                            totalRecords = counts.size + 1,
                            confidence = 0.0,
                            reasons = listOf(
                                "Gen 3 dynamic table prefilter shape budget exceeded " +
                                    "($MAX_DYNAMIC_PREFILTER_SHAPES); automatic resolution requires review",
                            ),
                            ambiguous = true,
                            reviewRecommended = true,
                        ),
                    )
                }
                val priorReferences = counts[target] ?: 0
                if (priorReferences == 1) {
                    if (qualifiedCandidates >= MAX_REFERENCED_DYNAMIC_CANDIDATES) {
                        return ReferencedTargetResolution(
                            referenceCounts = emptyMap(),
                            overflowEvidence = ValidationEvidence(
                                compatible = false,
                                validRecords = 0,
                                totalRecords = qualifiedCandidates + 1,
                                confidence = 0.0,
                                reasons = listOf(
                                    "Gen 3 dynamic table candidate budget exceeded " +
                                        "($MAX_REFERENCED_DYNAMIC_CANDIDATES); automatic resolution requires review",
                                ),
                                ambiguous = true,
                                reviewRecommended = true,
                            ),
                        )
                    }
                    qualifiedCandidates++
                }
                counts[target] = priorReferences + 1
            }
            offset += 4
        }
        return ReferencedTargetResolution(counts.filterValues { it >= 2 })
    }

    private fun plausibleDynamicTarget(rom: RomImage, offset: Int, speciesCount: Int, moveCount: Int): Boolean =
        (tableFits(rom, offset, speciesCount, 28) &&
            plausibleStatsSample(rom, offset, speciesCount, MAX_PREFILTER_RECORDS)) ||
            (tableFits(rom, offset, moveCount, 16) &&
                plausibleCfruMoveSample(rom, offset, moveCount, MAX_PREFILTER_RECORDS)) ||
            (tableFits(rom, offset, moveCount, 20) &&
                plausibleBattleEngineMoveSample(rom, offset, moveCount, MAX_PREFILTER_RECORDS))

    private fun tableFits(rom: RomImage, offset: Int, count: Int, recordSize: Int): Boolean =
        offset >= 0 && count > 0 && offset.toLong() + count.toLong() * recordSize <= rom.size.toLong()

    private fun validStatsRow(
        rom: RomImage,
        layout: TableLayout,
        index: Int,
        abilityUpperBound: Int?,
    ): Boolean {
        val base = layout.offset + index * layout.recordSize
        if (base < 0 || base.toLong() + layout.recordSize > rom.size.toLong()) return false
        if ((0 until 6).any { rom.u8(base + it) == 0 } ||
            rom.u8(base + 6) !in 0..31 || rom.u8(base + 7) !in 0..31
        ) {
            return false
        }
        if (layout.recordSize == 28 && abilityUpperBound != null) {
            if ((22..23).map { rom.u8(base + it) }.any { it > abilityUpperBound }) return false
        }
        return true
    }

    private fun credibleSpeciesToDexMap(
        rom: RomImage,
        names: TableLayout,
        speciesCount: Int,
        offset: Int,
    ): Boolean {
        val storedCount = speciesCount - 1
        if (storedCount < 277 || offset < 0 || offset.toLong() + storedCount * 2L > rom.size.toLong() ||
            rom.u16le(offset) != 1 || rom.u16le(offset + 2) != 2 || rom.u16le(offset + 276 * 2) != 252
        ) {
            return false
        }
        val positive = (0 until storedCount).map { rom.u16le(offset + it * 2) }.filter { it > 0 }
        if (positive.size * 10 < storedCount * 9 || positive.distinct().size * 100 < positive.size * 95) {
            return false
        }
        val namedPositive = (1 until speciesCount).count { speciesId ->
            rom.u16le(offset + (speciesId - 1) * 2) > 0 &&
                fixedNameHasContent(rom, names, speciesId)
        }
        val named = (1 until speciesCount).count { fixedNameHasContent(rom, names, it) }
        return named > 0 && namedPositive * 10 >= named * 9
    }

    private fun fixedNameHasContent(rom: RomImage, names: TableLayout, index: Int): Boolean {
        val offset = names.offset + index * names.recordSize
        if (offset < 0 || offset.toLong() + names.recordSize > rom.size.toLong()) return false
        return (0 until names.recordSize).any { byteIndex ->
            val value = rom.u8(offset + byteIndex)
            value != 0 && value != 0xFF
        }
    }

    private fun namesAliasKnownNonSpeciesTable(
        rom: RomImage,
        tables: ProfileTables,
        names: TableLayout,
        boundary: Int,
        suffixCount: Int,
    ): Boolean {
        val suffixOffset = names.offset + boundary * names.recordSize
        val suffixLength = suffixCount * names.recordSize
        if (suffixOffset < 0 || suffixOffset.toLong() + suffixLength > rom.size.toLong()) return false
        return listOfNotNull(tables.moveNames, tables.abilities).any { other ->
            other.offset >= 0 && other.offset.toLong() + suffixLength <= rom.size.toLong() &&
                (0 until suffixLength).all { index ->
                    rom.u8(suffixOffset + index) == rom.u8(other.offset + index)
                }
        }
    }

    private fun plausibleStatsSample(rom: RomImage, offset: Int, count: Int, maximumSample: Int = 96): Boolean {
        val sample = minOf(count, maximumSample)
        if (sample < 2) return false
        var plausible = 0
        for (index in 1 until sample) {
            val base = offset + index * 28
            if ((0 until 6).all { rom.u8(base + it) in 1..255 } &&
                rom.u8(base + 6) in 0..31 && rom.u8(base + 7) in 0..31
            ) plausible++
        }
        return plausible * 10 >= (sample - 1) * 9
    }

    private fun plausibleCfruMoveSample(rom: RomImage, offset: Int, count: Int, maximumSample: Int = 96): Boolean {
        val sample = minOf(count, maximumSample)
        if (sample < 4) return false
        var plausible = 0
        var populated = 0
        for (index in 1 until sample) {
            val base = offset + index * 16
            val reserved = (0 until 16).all { rom.u8(base + it) == 0 }
            if (!reserved) populated++
            val accuracy = rom.u8(base + 5)
            if (reserved || (
                    rom.u8(base + 4) in 0..31 && (accuracy in 0..100 || accuracy == 0xFF) &&
                        rom.u8(base + 6) in 0..64 && rom.u8(base + 9).toByte().toInt() in -8..7 &&
                        (12 until 16).all { rom.u8(base + it) == 0 }
                    )
            ) plausible++
        }
        return plausible * 10 >= (sample - 1) * 9 && populated * 5 >= (sample - 1) * 4
    }

    private fun plausibleBattleEngineMoveSample(rom: RomImage, offset: Int, count: Int, maximumSample: Int = 96): Boolean {
        val sample = minOf(count, maximumSample)
        if (sample < 4) return false
        var plausible = 0
        var populated = 0
        for (index in 1 until sample) {
            val base = offset + index * 20
            val reserved = (0 until 20).all { rom.u8(base + it) == 0 }
            if (!reserved) populated++
            val accuracy = rom.u8(base + 5)
            val secondaryChance = rom.u8(base + 7)
            if (reserved || (
                    rom.u16le(base + 2) <= 2048 && rom.u8(base + 4) in 0..31 &&
                        (accuracy in 0..100 || accuracy == 0xFF) && rom.u8(base + 6) in 0..64 &&
                        (secondaryChance in 0..100 || secondaryChance == 0xFF) &&
                        rom.u8(base + 10).toByte().toInt() in -8..7 && rom.u8(base + 11) == 0 &&
                        rom.u8(base + 16) in 0..2
                    )
            ) plausible++
        }
        return plausible * 10 >= (sample - 1) * 9 && populated * 5 >= (sample - 1) * 4
    }

    private fun ValidationEvidence.toLayout(format: TableRecordFormat = TableRecordFormat.STANDARD) = TableLayout(
        offset = requireNotNull(offset),
        count = totalRecords,
        recordSize = requireNotNull(recordSize),
        format = format,
    )

    private val candidateOrder = compareByDescending<Pair<ValidationEvidence, Int>> { it.first.confidence }
        .thenByDescending { it.second }
        .thenBy { requireNotNull(it.first.offset) }

    private data class DynamicMoveCandidate(
        val evidence: ValidationEvidence,
        val references: Int,
        val format: TableRecordFormat,
    )

    private data class MoveCandidateSelection(
        val layout: TableLayout?,
        val evidence: ValidationEvidence?,
    )

    private data class SparseSpeciesExtentCandidate(
        val boundary: Int,
        val baseStatsRecordSize: Int,
    )

    private data class ReferencedTargetResolution(
        val referenceCounts: Map<Int, Int>,
        val overflowEvidence: ValidationEvidence? = null,
    )

    private val moveCandidateOrder = compareByDescending<DynamicMoveCandidate> { it.evidence.confidence }
        .thenByDescending { it.references }
        .thenBy { requireNotNull(it.evidence.offset) }
        .thenBy { it.format.ordinal }

    private val PUBLISHED_POINTER_SLOTS = listOf(
        0x128, 0x144, 0x148,
        0x1AC, 0x1B0, 0x1B4, 0x1B8,
        0x1BC, 0x1C0, 0x1C4, 0x1C8, 0x1CC,
    )
    private const val MAX_PREFILTER_RECORDS = 8
    private const val MAX_DYNAMIC_PREFILTER_SHAPES = 4_096
    private const val MAX_REFERENCED_DYNAMIC_CANDIDATES = 256
    private const val MINIMUM_RECONCILED_SPECIES_COUNT = 300
    private const val MINIMUM_INCOHERENT_SUFFIX_DENOMINATOR = 4
    private const val MAXIMUM_INCOHERENT_SUFFIX_VALID_NUMERATOR = 1
    private const val MINIMUM_DIRECT_SPECIES_TO_DEX_REFERENCES = 2
    private const val MINIMUM_MAPPED_ID_UNIQUENESS_PERCENT = 95
    private const val DIRECT_SPECIES_TO_DEX_WINDOW_BYTES = 24
    private const val LEAF_SPECIES_TO_DEX_PREFIX_BYTES = 10
    private const val LEAF_SPECIES_TO_DEX_SUFFIX_BYTES = 16
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
}

internal data class Gen3SpeciesExtentResolution(
    val speciesCount: Int,
    val tables: ProfileTables,
)

internal data class Gen3DynamicTableResolution(
    val tables: ProfileTables,
    val baseStatsEvidence: ValidationEvidence?,
    val moveDataEvidence: ValidationEvidence?,
)
