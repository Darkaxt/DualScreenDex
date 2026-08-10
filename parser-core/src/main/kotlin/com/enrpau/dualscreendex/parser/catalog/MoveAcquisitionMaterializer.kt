package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomCapability

data class MoveAcquisitionMaterialization(
    val acquisitionsBySpecies: Map<Int, List<MoveAcquisition>>,
    val evidence: Map<MoveAcquisitionMethod, CapabilityEvidence>,
)

object MoveAcquisitionMaterializer {
    private const val EGG_SPECIES_OFFSET = 20_000

    fun materialize(rom: RomImage, layout: ResolvedRomLayout): MoveAcquisitionMaterialization {
        if (layout.pokeemeraldExpansion != null) return expansionAcquisitions(rom, layout)
        val bySpecies = linkedMapOf<Int, MutableList<MoveAcquisition>>()
        val evidence = linkedMapOf<MoveAcquisitionMethod, CapabilityEvidence>()
        val gbaReferences = if (layout.generation == 3) referencedTargets(rom) else emptyMap()
        val legacy = when (layout.generation) {
            1 -> LegacyAcquisitions(machine = embeddedGenOneMachines(rom, layout))
            2 -> embeddedGenTwoAcquisitions(rom, layout)
            else -> LegacyAcquisitions()
        }

        val machines = if (layout.generation <= 2) {
            legacy.machine
        } else {
            indirectEngineBitfieldPair(
                rom, layout, gbaReferences, CFRU_MACHINE_MOVES_SLOT, CFRU_MACHINE_FLAGS_SLOT,
                MoveAcquisitionMethod.MACHINE,
            ) ?: runtimeReferencedMachinePair(rom, layout, gbaReferences)
                ?: referencedBitfieldPair(rom, layout, 58, MoveAcquisitionMethod.MACHINE, gbaReferences)
        }
        merge(bySpecies, machines?.acquisitions.orEmpty())
        evidence[MoveAcquisitionMethod.MACHINE] = evidence(
            RomCapability.MACHINE_MOVES,
            machines,
            "validated ROM machine-move list and species compatibility flags",
            "machine-move list and compatibility flags were not jointly resolved",
        )

        val eggs = when (layout.generation) {
            2 -> genTwoEggMoves(rom, layout)
            3 -> eggMoves(rom, layout)
            else -> null
        }
        merge(bySpecies, eggs?.acquisitions.orEmpty())
        evidence[MoveAcquisitionMethod.EGG] = evidence(
            RomCapability.EGG_MOVES,
            eggs,
            "validated sentinel-delimited ROM egg-move list",
            if (layout.generation == 1) "breeding is not part of this engine" else "egg-move table was not resolved",
            if (layout.generation == 1) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
        )

        val tutors = when (layout.generation) {
            2 -> legacy.tutor
            3 -> referencedTutorPair(rom, layout, gbaReferences, machines?.sourceOffset)
            else -> null
        }
        val tutorNotApplicable = layout.generation == 1 || layout.family == EngineFamily.GOLD_SILVER ||
            layout.family == EngineFamily.RUBY_SAPPHIRE
        merge(bySpecies, tutors?.acquisitions.orEmpty())
        evidence[MoveAcquisitionMethod.TUTOR] = evidence(
            RomCapability.TUTOR_MOVES,
            tutors,
            "validated referenced ROM tutor-move list and species compatibility flags",
            if (tutorNotApplicable) "a separate tutor-compatibility table is not part of this engine" else "tutor move list and compatibility flags were not jointly resolved",
            if (tutorNotApplicable) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
        )

        return MoveAcquisitionMaterialization(
            acquisitionsBySpecies = bySpecies.mapValues { (_, values) -> values.distinct() },
            evidence = evidence,
        )
    }

    private fun expansionAcquisitions(rom: RomImage, layout: ResolvedRomLayout): MoveAcquisitionMaterialization {
        val expansion = requireNotNull(layout.pokeemeraldExpansion)
        val species = layout.tables.baseStats ?: return MoveAcquisitionMaterialization(emptyMap(), emptyMap())
        val moveCount = layout.moveCount ?: return MoveAcquisitionMaterialization(emptyMap(), emptyMap())
        val stride = species.stride ?: expansion.speciesRecordSize
        val bySpecies = linkedMapOf<Int, MutableList<MoveAcquisition>>()

        fun read(fieldOffset: Int, method: MoveAcquisitionMethod): Pair<Int, Int> {
            var populatedSpecies = 0
            var links = 0
            repeat(species.count) { speciesId ->
                val pointer = rom.gbaPointer(species.offset + speciesId * stride + fieldOffset) ?: return@repeat
                val seen = mutableSetOf<Int>()
                val moves = buildList {
                    var cursor = pointer
                    repeat(1024) {
                        val move = rom.u16le(cursor)
                        if (move == 0xFFFF) return@buildList
                        if (move !in 1 until moveCount) return@buildList
                        if (seen.add(move)) {
                            add(MoveAcquisition(move, method))
                        }
                        cursor += 2
                    }
                }
                if (moves.isNotEmpty()) {
                    populatedSpecies++
                    links += moves.size
                    bySpecies.getOrPut(speciesId) { mutableListOf() }.addAll(moves)
                }
            }
            return populatedSpecies to links
        }

        val (teachableSpecies, teachableLinks) = read(expansion.teachablePointerOffset, MoveAcquisitionMethod.MACHINE)
        val (eggSpecies, eggLinks) = read(expansion.eggMovePointerOffset, MoveAcquisitionMethod.EGG)
        val evidence = linkedMapOf<MoveAcquisitionMethod, CapabilityEvidence>()
        evidence[MoveAcquisitionMethod.MACHINE] = CapabilityEvidence(
            capability = RomCapability.MACHINE_MOVES,
            compatible = teachableSpecies > 0,
            confidence = if (teachableSpecies > 0) 1.0 else 0.0,
            offset = species.offset + expansion.teachablePointerOffset,
            count = teachableLinks,
            reasons = listOf("decoded integrated expansion teachable-move lists; machine and tutor provenance is combined"),
            status = if (teachableSpecies > 0) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
        )
        evidence[MoveAcquisitionMethod.EGG] = CapabilityEvidence(
            capability = RomCapability.EGG_MOVES,
            compatible = eggSpecies > 0,
            confidence = if (eggSpecies > 0) 1.0 else 0.0,
            offset = species.offset + expansion.eggMovePointerOffset,
            count = eggLinks,
            reasons = listOf("decoded integrated expansion egg-move lists"),
            status = if (eggSpecies > 0) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
        )
        evidence[MoveAcquisitionMethod.TUTOR] = CapabilityEvidence(
            capability = RomCapability.TUTOR_MOVES,
            compatible = false,
            confidence = 1.0,
            offset = species.offset + expansion.teachablePointerOffset,
            reasons = listOf("the expansion species record combines machine and tutor compatibility into one teachable list"),
            status = CapabilityStatus.NOT_APPLICABLE,
        )
        return MoveAcquisitionMaterialization(
            acquisitionsBySpecies = bySpecies.mapValues { (_, values) -> values.distinct() },
            evidence = evidence,
        )
    }

    private fun embeddedGenOneMachines(rom: RomImage, layout: ResolvedRomLayout): Candidate? {
        val stats = layout.tables.baseStats ?: return null
        val moveCount = layout.moveCount ?: return null
        val machineCount = 55
        val flagOffset = 20
        val flagBytes = 7
        if (stats.recordSize < flagOffset + flagBytes) return null

        val lists = mutableListOf<Pair<List<Int>, Int>>()
        var offset = 0
        while (offset + machineCount <= rom.size) {
            val first = rom.u8(offset)
            if (first !in 1 until moveCount) {
                offset++
                continue
            }
            val moves = ArrayList<Int>(machineCount)
            var valid = true
            repeat(machineCount) { index ->
                val move = rom.u8(offset + index)
                if (move !in 1 until moveCount || move in moves) valid = false else moves += move
            }
            val before = if (offset > 0) rom.u8(offset - 1) else 0
            val after = if (offset + machineCount < rom.size) rom.u8(offset + machineCount) else 0
            if (valid && before !in 1 until moveCount && after !in 1 until moveCount) lists += moves to offset
            offset++
        }
        val distinct = lists.distinctBy { it.first }
        if (distinct.size != 1) return null
        val (moves, sourceOffset) = distinct.single()
        return embeddedFlagCandidate(rom, stats, flagOffset, moves, 0, MoveAcquisitionMethod.MACHINE, sourceOffset)
    }

    private fun embeddedGenTwoAcquisitions(rom: RomImage, layout: ResolvedRomLayout): LegacyAcquisitions {
        val stats = layout.tables.baseStats ?: return LegacyAcquisitions()
        val moveCount = layout.moveCount ?: return LegacyAcquisitions()
        val tutorCount = if (layout.family == EngineFamily.CRYSTAL) GEN_TWO_TUTOR_COUNT else 0
        val totalCount = GEN_TWO_MACHINE_COUNT + tutorCount
        if (stats.recordSize < GEN_TWO_FLAG_OFFSET + GEN_TWO_FLAG_BYTES) return LegacyAcquisitions()
        val candidates = mutableListOf<Pair<Int, List<Int>>>()
        var offset = 0
        while (offset + totalCount < rom.size) {
            if (rom.u8(offset + totalCount) == 0) {
                val moves = List(totalCount) { rom.u8(offset + it) }
                if (moves.all { it in 1 until moveCount } && moves.distinct().size == totalCount) {
                    candidates += offset to moves
                }
            }
            offset++
        }
        val distinct = candidates.distinctBy { it.second }
        if (distinct.size != 1) return LegacyAcquisitions()
        val (sourceOffset, moves) = distinct.single()
        return LegacyAcquisitions(
            machine = embeddedFlagCandidate(
                rom, stats, GEN_TWO_FLAG_OFFSET, moves.take(GEN_TWO_MACHINE_COUNT), 0,
                MoveAcquisitionMethod.MACHINE, sourceOffset,
            ),
            tutor = if (tutorCount > 0) embeddedFlagCandidate(
                rom, stats, GEN_TWO_FLAG_OFFSET, moves.drop(GEN_TWO_MACHINE_COUNT), GEN_TWO_MACHINE_COUNT,
                MoveAcquisitionMethod.TUTOR, sourceOffset,
            ) else null,
        )
    }

    private fun embeddedFlagCandidate(
        rom: RomImage,
        stats: com.enrpau.dualscreendex.parser.model.TableLayout,
        flagOffset: Int,
        moves: List<Int>,
        firstBit: Int,
        method: MoveAcquisitionMethod,
        sourceOffset: Int,
    ): Candidate? {
        val acquisitions = linkedMapOf<Int, List<MoveAcquisition>>()
        repeat(stats.count) { index ->
            val row = stats.offset + index * stats.recordSize + flagOffset
            if (row + (firstBit + moves.size + 7) / 8 > rom.size) return null
            acquisitions[index + 1] = moves.mapIndexedNotNull { indexInList, moveId ->
                val bit = firstBit + indexInList
                val enabled = rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0
                MoveAcquisition(moveId, method, indexInList + 1).takeIf { enabled }
            }
        }
        return Candidate(sourceOffset, 1.0, acquisitions)
    }

    private fun genTwoEggMoves(rom: RomImage, layout: ResolvedRomLayout): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        val tableBytes = speciesCount * 2
        val candidates = mutableListOf<Candidate>()
        var start = 0x4000
        while (start + tableBytes <= rom.size) {
            val bank = start / 0x4000
            val bankEnd = minOf(rom.size, (bank + 1) * 0x4000)
            if (start + tableBytes > bankEnd) {
                start = bankEnd
                continue
            }
            val sampleCount = minOf(4, speciesCount)
            val sampleValid = (0 until sampleCount).all { index ->
                rom.u16le(start + index * 2) in 0x4000..0x7FFF
            }
            if (!sampleValid) {
                start++
                continue
            }
            var valid = true
            var links = 0
            var nonEmpty = 0
            var targetsAfterTable = 0
            val targets = mutableSetOf<Int>()
            val acquisitions = linkedMapOf<Int, List<MoveAcquisition>>()
            repeat(speciesCount) { species ->
                if (!valid) return@repeat
                val pointer = rom.u16le(start + species * 2)
                val target = rom.gbBankAddress(bank, pointer)
                if (target == null || target >= bankEnd) {
                    valid = false
                    return@repeat
                }
                targets += target
                if (target >= start + tableBytes) targetsAfterTable++
                val moves = mutableListOf<MoveAcquisition>()
                var cursor = target
                while (cursor < bankEnd && moves.size <= MAX_EGG_MOVES_PER_SPECIES) {
                    val value = rom.u8(cursor++)
                    if (value == 0xFF) break
                    if (value !in 1..moveCount || moves.any { it.moveId == value }) {
                        valid = false
                        break
                    }
                    moves += MoveAcquisition(value, MoveAcquisitionMethod.EGG)
                }
                if (!valid || cursor > bankEnd || rom.u8(cursor - 1) != 0xFF) {
                    valid = false
                    return@repeat
                }
                if (moves.isNotEmpty()) nonEmpty++
                links += moves.size
                acquisitions[species + 1] = moves
            }
            val afterRatio = targetsAfterTable.toDouble() / speciesCount
            if (valid && targets.size >= maxOf(2, speciesCount / 10) &&
                nonEmpty >= maxOf(2, speciesCount / 8) && links >= maxOf(2, speciesCount / 2) && afterRatio >= 0.75
            ) {
                candidates += Candidate(start, minOf(1.0, links.toDouble() / speciesCount), acquisitions)
            }
            start++
        }
        return candidates.maxByOrNull { it.confidence }?.takeIf { best ->
            candidates.count { best.confidence - it.confidence < 0.05 } == 1
        }
    }

    private fun eggMoves(rom: RomImage, layout: ResolvedRomLayout): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        val candidates = mutableListOf<Candidate>()
        var start = 0
        while (start + 2 <= rom.size) {
            val marker = rom.u16le(start)
            if (marker !in (EGG_SPECIES_OFFSET + 1)..(EGG_SPECIES_OFFSET + speciesCount)) {
                start += 2
                continue
            }
            var cursor = start
            var currentSpecies = 0
            var acceptCurrentGroup = false
            var groups = 0
            var moves = 0
            var terminated = false
            var valid = true
            val seenSpecies = mutableSetOf<Int>()
            val acquisitions = linkedMapOf<Int, MutableList<MoveAcquisition>>()
            var records = 0
            while (records < 8192 && valid && !terminated) {
                if (cursor + 2 > rom.size) {
                    valid = false
                    break
                }
                val value = rom.u16le(cursor)
                cursor += 2
                when {
                    value == 0xFFFF -> terminated = true
                    value in (EGG_SPECIES_OFFSET + 1)..(EGG_SPECIES_OFFSET + speciesCount) -> {
                        val species = value - EGG_SPECIES_OFFSET
                        currentSpecies = species
                        acceptCurrentGroup = seenSpecies.add(species)
                        if (acceptCurrentGroup) {
                            groups++
                            acquisitions.getOrPut(species) { mutableListOf() }
                        }
                    }
                    currentSpecies > 0 && value in 1 until moveCount -> {
                        if (acceptCurrentGroup) {
                            acquisitions.getValue(currentSpecies) += MoveAcquisition(value, MoveAcquisitionMethod.EGG)
                            moves++
                        }
                    }
                    else -> valid = false
                }
                records++
            }
            val minimumGroups = maxOf(2, speciesCount / 10)
            if (valid && terminated && groups >= minimumGroups && moves >= groups) {
                val candidate = Candidate(
                    start,
                    minOf(1.0, groups.toDouble() / maxOf(2, speciesCount / 4)),
                    acquisitions.mapValues { it.value.toList() },
                )
                if (groups >= maxOf(20, speciesCount / 3)) return candidate
                candidates += candidate
            }
            start += 2
        }
        return candidates.maxWithOrNull(compareBy<Candidate> { it.acquisitions.values.sumOf(List<MoveAcquisition>::size) }.thenBy { it.confidence })
    }

    private fun referencedTutorPair(
        rom: RomImage,
        layout: ResolvedRomLayout,
        references: Map<Int, List<Int>>,
        machineOffset: Int?,
    ): Candidate? {
        pointerIndexedTutorPair(rom, layout, references)?.let { return it }
        indirectEngineBitfieldPair(
            rom, layout, references, CFRU_TUTOR_MOVES_SLOT, CFRU_TUTOR_FLAGS_SLOT,
            MoveAcquisitionMethod.TUTOR,
        )?.let { return it }
        runtimeReferencedTutorPair(rom, layout, references)?.let { return it }
        adjacentReferencedTutorPair(rom, layout, references)?.let { return it }
        val candidates = (GEN_THREE_TUTOR_PREFIX.size..MAX_STANDARD_TUTOR_COUNT).mapNotNull { count ->
            referencedBitfieldPair(
                rom,
                layout,
                count,
                MoveAcquisitionMethod.TUTOR,
                references,
                requiredPrefix = GEN_THREE_TUTOR_PREFIX,
            )
        }.filterNot { it.sourceOffset == machineOffset }
        return candidates.maxWithOrNull(
            compareBy<Candidate> { it.confidence }
                .thenBy { candidate -> candidate.acquisitions.values.flatten().mapNotNull { it.sourceId }.maxOrNull() ?: 0 },
        )
    }

    private fun runtimeReferencedTutorPair(
        rom: RomImage,
        layout: ResolvedRomLayout,
        references: Map<Int, List<Int>>,
    ): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        val moveLists = references.mapNotNull { (offset, moveReferences) ->
            val moves = decodeSentinelTutorMoves(rom, offset, moveCount) ?: return@mapNotNull null
            Triple(offset, moves, moveReferences)
        }
        val flags = references.mapNotNull { (offset, flagReferences) ->
            if (flagReferences.none { corroboratesTutorBitfieldIndexing(rom, it) }) return@mapNotNull null
            offset to flagReferences
        }
        val pairs = moveLists.flatMap { (moveListOffset, moves, moveReferences) ->
            flags.mapNotNull { (flagsOffset, flagReferences) ->
                val proximity = referenceProximity(moveReferences, flagReferences)
                if (proximity > NEAR_TUTOR_RUNTIME_REFERENCE_DISTANCE) return@mapNotNull null
                val validation = validateRuntimeBitfield(rom, flagsOffset, speciesCount, moves.size)
                    ?: return@mapNotNull null
                RuntimeTutorPair(moveListOffset, moves, flagsOffset, validation, proximity)
            }
        }.distinctBy { it.moveListOffset to it.flagsOffset }
        val best = pairs.minWithOrNull(
            compareBy<RuntimeTutorPair> { it.proximity }
                .thenByDescending { it.validation.confidence }
                .thenByDescending { it.moves.size },
        ) ?: return null
        if (pairs.any { it !== best && it.proximity == best.proximity && it.validation.confidence == best.validation.confidence }) {
            return null
        }
        val acquisitions = (0 until speciesCount).associate { species ->
            val row = best.flagsOffset + species * best.validation.rowBytes
            species to best.moves.mapIndexedNotNull { bit, moveId ->
                val enabled = rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0
                MoveAcquisition(moveId, MoveAcquisitionMethod.TUTOR, bit + 1).takeIf { enabled }
            }
        }
        return Candidate(best.moveListOffset, best.validation.confidence, acquisitions)
    }

    private fun decodeSentinelTutorMoves(rom: RomImage, offset: Int, moveCount: Int): List<Int>? {
        val moves = mutableListOf<Int>()
        repeat(MAX_RUNTIME_TUTOR_COUNT + 1) { index ->
            if (offset + index * 2 + 2 > rom.size) return null
            val move = rom.u16le(offset + index * 2)
            if (move == 0xFFFF) {
                return moves.takeIf { it.size in MIN_TUTOR_COUNT..MAX_RUNTIME_TUTOR_COUNT && it.distinct().size == it.size }
            }
            if (move !in 1 until moveCount || move in moves) return null
            moves += move
        }
        return null
    }

    private fun corroboratesTutorBitfieldIndexing(rom: RomImage, reference: Int): Boolean {
        val start = maxOf(0, reference - TUTOR_BITFIELD_CODE_WINDOW) and 1.inv()
        val end = minOf(rom.size - 2, reference + 8)
        val instructions = generateSequence(start) { previous ->
            (previous + 2).takeIf { it <= end }
        }.map(rom::u16le).toList()
        val shiftsSpeciesHigh = instructions.any { instruction ->
            instruction and 0xF800 == 0 && (instruction ushr 6) and 0x1F == 16
        }
        val shiftsSpeciesDown = instructions.any { instruction ->
            instruction and 0xF800 == 0x0800 && (instruction ushr 6) and 0x1F == 14
        }
        val shiftsMask = instructions.any { it and 0xFFC0 == 0x4080 }
        val loadsWord = instructions.any { it and 0xF800 == 0x6800 }
        val masksWord = instructions.any { it and 0xFFC0 == 0x4000 }
        return shiftsSpeciesHigh && shiftsSpeciesDown && shiftsMask && loadsWord && masksWord
    }

    private fun runtimeReferencedMachinePair(
        rom: RomImage,
        layout: ResolvedRomLayout,
        references: Map<Int, List<Int>>,
    ): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        val machineCount = GEN_THREE_TM_PREFIX.size + 8
        val flagCandidates = references.mapNotNull { (target, targetReferences) ->
            val codeReferences = targetReferences.count { reference ->
                corroboratesSplitWordBitfieldIndexing(rom, reference)
            }
            if (codeReferences < 2) return@mapNotNull null
            validateRuntimeBitfield(rom, target, speciesCount, machineCount)?.let { validation ->
                Triple(target, validation, codeReferences)
            }
        }
        if (flagCandidates.size != 1) return null
        val (flagsOffset, validation, _) = flagCandidates.single()

        val moveLists = references.keys.mapNotNull { moveListOffset ->
            if (moveListOffset + machineCount * 2 > rom.size) return@mapNotNull null
            val moves = List(machineCount) { rom.u16le(moveListOffset + it * 2) }
            if (moves.any { it !in 1 until moveCount } || moves.distinct().size != moves.size ||
                !hasExactMoveListBoundary(rom, moveListOffset, moves, moveCount)
            ) return@mapNotNull null
            val prefixSimilarity = GEN_THREE_TM_PREFIX.indices.count { index ->
                moves[index] == GEN_THREE_TM_PREFIX[index]
            }.toDouble() / GEN_THREE_TM_PREFIX.size
            if (prefixSimilarity < MIN_MACHINE_RUNTIME_PREFIX_SIMILARITY) return@mapNotNull null
            moveListOffset to moves
        }.distinctBy { it.second }
        if (moveLists.size != 1) return null
        val (moveListOffset, moves) = moveLists.single()
        val acquisitions = (0 until speciesCount).associate { species ->
            val row = flagsOffset + species * validation.rowBytes
            species to moves.mapIndexedNotNull { bit, moveId ->
                val enabled = rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0
                MoveAcquisition(moveId, MoveAcquisitionMethod.MACHINE, bit + 1).takeIf { enabled }
            }
        }
        return Candidate(moveListOffset, validation.confidence, acquisitions)
    }

    private fun corroboratesSplitWordBitfieldIndexing(rom: RomImage, reference: Int): Boolean {
        val start = maxOf(0, reference - MACHINE_BITFIELD_CODE_WINDOW) and 1.inv()
        val end = minOf(rom.size - 2, reference + 8)
        val instructions = generateSequence(start) { previous ->
            (previous + 2).takeIf { it <= end }
        }.map(rom::u16le).toList()
        val shiftsByThree = instructions.any { instruction ->
            instruction and 0xF800 == 0 && (instruction ushr 6) and 0x1F == 3
        }
        val loadsWord = instructions.any { it and 0xF800 == 0x6800 }
        val masksWord = instructions.any { it and 0xFFC0 == 0x4000 }
        val comparesWordBoundary = instructions.any { it and 0xF800 == 0x2800 && it and 0xFF == 31 }
        val subtractsWordBoundary = instructions.any { it and 0xF800 == 0x3800 && it and 0xFF == 32 }
        return shiftsByThree && loadsWord && masksWord && comparesWordBoundary && subtractsWordBoundary
    }

    private fun validateRuntimeBitfield(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        bitCount: Int,
    ): BitfieldValidation? {
        val rowBytes = (bitCount + 7) / 8
        if (offset < 0 || offset.toLong() + speciesCount.toLong() * rowBytes > rom.size) return null
        val usedBits = BooleanArray(bitCount)
        var nonEmpty = 0
        repeat(speciesCount) { species ->
            val row = offset + species * rowBytes
            var any = false
            repeat(bitCount) { bit ->
                if (rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0) {
                    usedBits[bit] = true
                    any = true
                }
            }
            if (any) nonEmpty++
        }
        val speciesRatio = nonEmpty.toDouble() / speciesCount
        val bitRatio = usedBits.count { it }.toDouble() / bitCount
        if (speciesRatio !in 0.15..1.0 || bitRatio < 0.75) return null
        return BitfieldValidation((speciesRatio + bitRatio) / 2.0, rowBytes)
    }

    private fun adjacentReferencedTutorPair(
        rom: RomImage,
        layout: ResolvedRomLayout,
        references: Map<Int, List<Int>>,
    ): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        val candidates = mutableListOf<AdjacentTutorCandidate>()
        for (count in MIN_TUTOR_COUNT..MAX_STANDARD_TUTOR_COUNT) {
            references.forEach { (moveListOffset, moveReferences) ->
                if (moveReferences.size < 2 || moveListOffset + count * 2 > rom.size) return@forEach
                val moves = List(count) { rom.u16le(moveListOffset + it * 2) }
                if (moves.any { it !in 1 until moveCount } || moves.distinct().size != moves.size ||
                    !hasExactMoveListBoundary(rom, moveListOffset, moves, moveCount)
                ) return@forEach
                val afterList = moveListOffset + count * 2
                val possibleFlagOffsets = linkedSetOf(afterList, (afterList + 3) and 3.inv())
                for (capacity in count + 1..MAX_STANDARD_TUTOR_COUNT) {
                    val paddedEnd = moveListOffset + capacity * 2
                    if ((afterList until paddedEnd).any { rom.u8(it) != 0 }) break
                    possibleFlagOffsets += paddedEnd
                    possibleFlagOffsets += (paddedEnd + 3) and 3.inv()
                }
                possibleFlagOffsets.forEach { flagsOffset ->
                    val flagReferences = references[flagsOffset] ?: return@forEach
                    val validation = validateBitfield(rom, flagsOffset, speciesCount, count) ?: return@forEach
                    if ((0 until validation.rowBytes).any { rom.u8(flagsOffset + it) != 0 }) return@forEach
                    val confidence = minOf(
                        1.0,
                        validation.confidence * 0.8 + 0.1 +
                            minOf(0.05, moveReferences.size * 0.02) +
                            minOf(0.05, flagReferences.size * 0.02),
                    )
                    candidates += AdjacentTutorCandidate(
                        moveListOffset, flagsOffset, validation.rowBytes, moves, confidence,
                        moveReferences.size + flagReferences.size,
                    )
                }
            }
        }
        val distinct = candidates.distinctBy { it.moves to it.flagsOffset }
        val best = distinct.maxWithOrNull(
            compareBy<AdjacentTutorCandidate> { it.confidence }
                .thenBy { it.referenceCount }
                .thenBy { it.moves.size },
        ) ?: return null
        if (distinct.any { it !== best && best.confidence - it.confidence < 0.05 && it.referenceCount >= best.referenceCount }) {
            return null
        }
        val acquisitions = (0 until speciesCount).associate { species ->
            val row = best.flagsOffset + species * best.rowBytes
            species to best.moves.mapIndexedNotNull { bit, moveId ->
                val enabled = rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0
                MoveAcquisition(moveId, MoveAcquisitionMethod.TUTOR, bit + 1).takeIf { enabled }
            }
        }
        return Candidate(best.moveListOffset, best.confidence, acquisitions)
    }

    private fun indirectEngineBitfieldPair(
        rom: RomImage,
        layout: ResolvedRomLayout,
        references: Map<Int, List<Int>>,
        movePointerSlot: Int,
        flagsPointerSlot: Int,
        method: MoveAcquisitionMethod,
        requireMachinePrefix: Boolean = false,
    ): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        if (movePointerSlot + 4 > rom.size || flagsPointerSlot + 4 > rom.size) return null
        val moveListOffset = rom.gbaPointer(movePointerSlot) ?: return null
        val flagsOffset = rom.gbaPointer(flagsPointerSlot) ?: return null
        val inferred = inferBitfieldLayouts(rom, flagsOffset, speciesCount)
            .filter { candidate ->
                if (moveListOffset + candidate.bitCount * 2 > rom.size) return@filter false
                val candidateMoves = List(candidate.bitCount) { rom.u16le(moveListOffset + it * 2) }
                candidateMoves.all { it in 1 until moveCount } &&
                    (method != MoveAcquisitionMethod.TUTOR || candidateMoves.distinct().size == candidateMoves.size) &&
                    (!requireMachinePrefix || GEN_THREE_TM_PREFIX.indices.count { index ->
                        candidateMoves.getOrNull(index) == GEN_THREE_TM_PREFIX[index]
                    }.toDouble() / GEN_THREE_TM_PREFIX.size >= MIN_MACHINE_PREFIX_SIMILARITY)
            }
            .maxWithOrNull(compareBy<InferredBitfieldLayout> { it.bitCount }.thenBy { it.confidence })
            ?: return null
        val moves = List(inferred.bitCount) { rom.u16le(moveListOffset + it * 2) }
        val acquisitions = (0 until speciesCount).associate { species ->
            val row = flagsOffset + species * inferred.rowBytes
            species to moves.mapIndexedNotNull { bit, moveId ->
                val enabled = rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0
                MoveAcquisition(moveId, method, bit + 1).takeIf { enabled }
            }
        }
        return Candidate(moveListOffset, inferred.confidence, acquisitions)
    }

    private fun inferBitfieldLayouts(rom: RomImage, offset: Int, speciesCount: Int): List<InferredBitfieldLayout> {
        val candidates = mutableListOf<InferredBitfieldLayout>()
        for (rowBytes in 1..MAX_INDIRECT_BITFIELD_ROW_BYTES) {
            if (offset.toLong() + speciesCount.toLong() * rowBytes > rom.size) continue
            if ((0 until rowBytes).any { rom.u8(offset + it) != 0 }) continue
            val used = BooleanArray(rowBytes * 8)
            var nonEmpty = 0
            repeat(speciesCount) { species ->
                val row = offset + species * rowBytes
                var any = false
                repeat(rowBytes * 8) { bit ->
                    if (rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0) {
                        used[bit] = true
                        any = true
                    }
                }
                if (any) nonEmpty++
            }
            val bitCount = used.indexOfLast { it } + 1
            if (bitCount <= (rowBytes - 4) * 8) continue
            val speciesRatio = nonEmpty.toDouble() / speciesCount
            val bitRatio = used.count { it }.toDouble() / bitCount.coerceAtLeast(1)
            if (bitCount >= MIN_INDIRECT_MOVE_COUNT && speciesRatio >= 0.15 && bitRatio >= 0.75) {
                candidates += InferredBitfieldLayout(
                    bitCount,
                    rowBytes,
                    minOf(1.0, (speciesRatio + bitRatio) / 2.0 * 0.9 + 0.1),
                )
            }
        }
        return candidates
    }

    private fun pointerIndexedTutorPair(
        rom: RomImage,
        layout: ResolvedRomLayout,
        references: Map<Int, List<Int>>,
    ): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        val tables = references.keys.asSequence()
            .filter { it % 4 == 0 }
            .mapNotNull { decodeIndexedTutorTable(rom, it, speciesCount) }
            .toList()
        val pairs = mutableListOf<IndexedTutorPair>()
        tables.forEach { table ->
            references.keys.forEach { moveListOffset ->
                if (moveListOffset + table.tutorCount * 2 > rom.size) return@forEach
                val moves = List(table.tutorCount) { rom.u16le(moveListOffset + it * 2) }
                if (moves.any { it !in 1 until moveCount } || moves.distinct().size != moves.size ||
                    !hasExactMoveListBoundary(rom, moveListOffset, moves, moveCount)
                ) return@forEach
                val proximity = referenceProximity(references.getValue(table.offset), references.getValue(moveListOffset))
                val confidence = minOf(1.0, table.confidence + when {
                    proximity <= 64 -> 0.15
                    proximity <= 512 -> 0.10
                    proximity <= 4096 -> 0.05
                    else -> 0.0
                })
                pairs += IndexedTutorPair(table, moveListOffset, moves, confidence, proximity)
            }
        }
        val distinct = pairs.distinctBy { it.table.offset to it.moves }
        val selected = distinct.maxWithOrNull(
            compareBy<IndexedTutorPair> { it.confidence }
                .thenByDescending { it.proximity }
                .thenBy { it.table.nonEmptySpecies },
        )?.takeIf { best ->
            distinct.count { best.confidence - it.confidence < 0.05 && it.proximity <= best.proximity + 64 } == 1
        } ?: return null
        val acquisitions = selected.table.indexesBySpecies.mapValues { (_, indexes) ->
            indexes.map { index ->
                MoveAcquisition(selected.moves[index], MoveAcquisitionMethod.TUTOR, index + 1)
            }
        }
        return Candidate(selected.moveListOffset, selected.confidence, acquisitions)
    }

    private fun decodeIndexedTutorTable(rom: RomImage, offset: Int, speciesCount: Int): IndexedTutorTable? {
        val minimumCount = maxOf(4, (speciesCount * MIN_TUTOR_SPECIES_COVERAGE).toInt())
        return (speciesCount downTo minimumCount).firstNotNullOfOrNull { tableSpeciesCount ->
            decodeExactIndexedTutorTable(rom, offset, tableSpeciesCount)?.let { table ->
                table.copy(confidence = table.confidence * tableSpeciesCount.toDouble() / speciesCount)
            }
        }
    }

    private fun decodeExactIndexedTutorTable(rom: RomImage, offset: Int, speciesCount: Int): IndexedTutorTable? {
        val tableBytes = speciesCount * 4
        if (offset < 0 || offset.toLong() + tableBytes > rom.size) return null
        val sample = minOf(4, speciesCount)
        if ((0 until sample).any { rom.gbaPointer(offset + it * 4) == null }) return null
        val indexesBySpecies = linkedMapOf<Int, List<Int>>()
        val usedIndexes = mutableSetOf<Int>()
        val targets = mutableSetOf<Int>()
        var nonEmptySpecies = 0
        repeat(speciesCount) { species ->
            val pointerOffset = offset + species * 4
            if (rom.u32le(pointerOffset) == 0L) {
                indexesBySpecies[species] = emptyList()
                return@repeat
            }
            val target = rom.gbaPointer(pointerOffset) ?: return null
            targets += target
            val indexes = mutableListOf<Int>()
            var cursor = target
            while (cursor < rom.size && indexes.size <= MAX_TUTOR_MOVES_PER_SPECIES) {
                val value = rom.u8(cursor++)
                if (value == 0xFF) break
                if (value >= MAX_TUTOR_COUNT || value in indexes) return null
                indexes += value
                usedIndexes += value
            }
            if (cursor > rom.size || rom.u8(cursor - 1) != 0xFF) return null
            if (indexes.isNotEmpty()) nonEmptySpecies++
            indexesBySpecies[species] = indexes
        }
        val tutorCount = (usedIndexes.maxOrNull() ?: return null) + 1
        val nonEmptyRatio = nonEmptySpecies.toDouble() / speciesCount
        val usedRatio = usedIndexes.size.toDouble() / tutorCount
        val targetRatio = targets.size.toDouble() / speciesCount
        if (tutorCount !in MIN_TUTOR_COUNT..MAX_TUTOR_COUNT || nonEmptyRatio < 0.15 || usedRatio < 0.75 || targetRatio < 0.05) {
            return null
        }
        return IndexedTutorTable(
            offset = offset,
            tutorCount = tutorCount,
            indexesBySpecies = indexesBySpecies,
            nonEmptySpecies = nonEmptySpecies,
            confidence = (nonEmptyRatio + usedRatio + minOf(1.0, targetRatio * 4)) / 3.0 * 0.85,
        )
    }

    private fun referenceProximity(first: List<Int>, second: List<Int>): Int =
        first.minOf { firstOffset -> second.minOf { secondOffset -> kotlin.math.abs(firstOffset - secondOffset) } }

    private fun referencedBitfieldPair(
        rom: RomImage,
        layout: ResolvedRomLayout,
        itemCount: Int,
        method: MoveAcquisitionMethod,
        references: Map<Int, List<Int>>,
        requiredPrefix: List<Int>? = null,
    ): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        val candidates = mutableListOf<BitfieldPairCandidate>()
        val moveLists = references.keys.mapNotNull { moveListOffset ->
            if (moveListOffset + itemCount * 2 > rom.size) return@mapNotNull null
            val moves = List(itemCount) { rom.u16le(moveListOffset + it * 2) }
            if (moves.any { it !in 1 until moveCount } || moves.distinct().size != moves.size ||
                requiredPrefix?.let { prefix ->
                    prefix.indices.count { moves.getOrNull(it) == prefix[it] }.toDouble() / prefix.size <
                        MIN_TUTOR_PREFIX_SIMILARITY
                } == true ||
                !hasExactMoveListBoundary(rom, moveListOffset, moves, moveCount)
            ) null else moveListOffset to moves
        }.toMap()
        val multiplicities = moveLists.values.groupingBy { it }.eachCount()
        moveLists.forEach { (moveListOffset, moves) ->
            val moveRefs = references.getValue(moveListOffset)
            val nearbyTargets = moveRefs.flatMap { reference ->
                buildList {
                    var nearby = maxOf(0, reference - NEAR_REFERENCE_LITERAL_WINDOW)
                    val end = minOf(rom.size - 4, reference + NEAR_REFERENCE_LITERAL_WINDOW)
                    while (nearby <= end) {
                        rom.gbaPointer(nearby)?.let(::add)
                        nearby += 4
                    }
                }
            }.distinct()
            val corroboratedTargets = moveRefs.flatMap { reference ->
                buildList {
                    var nearby = maxOf(0, reference - CORROBORATED_REFERENCE_LITERAL_WINDOW)
                    val end = minOf(rom.size - 4, reference + CORROBORATED_REFERENCE_LITERAL_WINDOW)
                    while (nearby <= end) {
                        rom.gbaPointer(nearby)?.takeIf { startsWithEmptyBitfieldRow(rom, it, itemCount) }?.let(::add)
                        nearby += 4
                    }
                }
            }.distinct()
            val independentTargets = if (method == MoveAcquisitionMethod.MACHINE) {
                references.keys.filter { target ->
                    target != moveListOffset && target % 4 == 0 && startsWithEmptyBitfieldRow(rom, target, itemCount)
                }
            } else {
                emptyList()
            }
            val adjacentTargets = if (method == MoveAcquisitionMethod.TUTOR) {
                val afterList = moveListOffset + itemCount * 2
                listOf(afterList, (afterList + 3) and 3.inv()).distinct()
            } else {
                emptyList()
            }
            (nearbyTargets + corroboratedTargets + independentTargets + adjacentTargets).distinct()
                .filter { it != moveListOffset }.forEach { flagsOffset ->
                val validation = validateBitfield(rom, flagsOffset, speciesCount, itemCount) ?: return@forEach
                val duplicateCorroboration = if (multiplicities.getValue(moves) > 1) 0.05 else 0.0
                val codeCorroboration = if (
                    method == MoveAcquisitionMethod.MACHINE && corroboratesStandardMachineIndexing(rom, moveRefs, itemCount)
                ) 0.15 else 0.0
                candidates += BitfieldPairCandidate(
                    moveListOffset = moveListOffset,
                    moves = moves,
                    flagsOffset = flagsOffset,
                    rowBytes = validation.rowBytes,
                    confidence = minOf(1.0, validation.confidence * 0.85 + duplicateCorroboration + codeCorroboration),
                )
            }
        }
        val distinct = candidates.distinctBy { it.moves to it.flagsOffset }
        val best = distinct.maxByOrNull { it.confidence } ?: return null
        val selected = distinct.filter { best.confidence - it.confidence < 0.05 }
        if (selected.map { it.moves }.distinct().size != 1) return null
        val acquisitions = (0 until speciesCount).associate { species ->
            species to selected.flatMap { variant ->
                val row = variant.flagsOffset + species * variant.rowBytes
                variant.moves.mapIndexedNotNull { bit, moveId ->
                    val enabled = rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0
                    MoveAcquisition(moveId, method, bit + 1).takeIf { enabled }
                }
            }.distinct()
        }
        return Candidate(best.moveListOffset, best.confidence, acquisitions)
    }

    private fun corroboratesStandardMachineIndexing(rom: RomImage, references: List<Int>, itemCount: Int): Boolean {
        val reservedHmSlots = 8
        val tmCount = itemCount - reservedHmSlots
        if (tmCount <= 0 || tmCount > 0xFF) return false
        return references.any { reference ->
            val start = maxOf(0, reference - MACHINE_CODE_WINDOW)
            val end = minOf(rom.size - 2, reference + MACHINE_CODE_WINDOW)
            var foundTmOffset = false
            var foundHmBound = false
            var offset = start and 1.inv()
            while (offset <= end) {
                val instruction = rom.u16le(offset)
                if (instruction and 0xF800 == 0x3000 && instruction and 0xFF == tmCount) foundTmOffset = true
                if (instruction and 0xF800 == 0x2800) {
                    val activeHmCount = (instruction and 0xFF) + 1
                    if (activeHmCount in MIN_ACTIVE_HMS..reservedHmSlots) foundHmBound = true
                }
                offset += 2
            }
            foundTmOffset && foundHmBound
        }
    }

    private fun referencedTargets(rom: RomImage): Map<Int, List<Int>> {
        val output = linkedMapOf<Int, MutableList<Int>>()
        var offset = 0
        while (offset + 4 <= rom.size) {
            rom.gbaPointer(offset)?.let { target -> output.getOrPut(target) { mutableListOf() } += offset }
            offset += 4
        }
        return output
    }

    private fun validateBitfield(rom: RomImage, offset: Int, speciesCount: Int, bitCount: Int): BitfieldValidation? {
        val packedBytes = (bitCount + 7) / 8
        val rowSizes = listOf(packedBytes, (packedBytes + 1) and 1.inv(), (packedBytes + 3) and 3.inv()).distinct()
        return rowSizes.mapNotNull { rowBytes ->
            validateBitfield(rom, offset, speciesCount, bitCount, rowBytes)
        }.maxWithOrNull(compareBy<BitfieldValidation> { it.confidence }.thenBy { -it.rowBytes })
    }

    private fun validateBitfield(
        rom: RomImage,
        offset: Int,
        speciesCount: Int,
        bitCount: Int,
        rowBytes: Int,
    ): BitfieldValidation? {
        if (offset < 0 || offset.toLong() + speciesCount.toLong() * rowBytes > rom.size) return null
        val usedBits = BooleanArray(bitCount)
        var nonEmpty = 0
        repeat(speciesCount) { species ->
            val row = offset + species * rowBytes
            var any = false
            repeat(bitCount) { bit ->
                if (rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0) {
                    usedBits[bit] = true
                    any = true
                }
            }
            if (any) nonEmpty++
            repeat(rowBytes * 8 - bitCount) { extra ->
                val bit = bitCount + extra
                if (rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0) return null
            }
        }
        val speciesRatio = nonEmpty.toDouble() / speciesCount
        val bitRatio = usedBits.count { it }.toDouble() / bitCount
        if (speciesRatio !in 0.15..1.0 || bitRatio < 0.75) return null
        return BitfieldValidation((speciesRatio + bitRatio) / 2.0, rowBytes)
    }

    private fun startsWithEmptyBitfieldRow(rom: RomImage, offset: Int, bitCount: Int): Boolean {
        val rowBytes = ((bitCount + 31) / 32) * 4
        if (offset < 0 || offset + rowBytes > rom.size) return false
        return (0 until rowBytes).all { rom.u8(offset + it) == 0 }
    }

    private fun hasExactMoveListBoundary(rom: RomImage, offset: Int, moves: List<Int>, moveCount: Int): Boolean {
        val after = offset + moves.size * 2
        if (after + 2 > rom.size) return true
        val next = rom.u16le(after)
        return next !in 1 until moveCount || next in moves
    }

    private fun merge(
        target: MutableMap<Int, MutableList<MoveAcquisition>>,
        source: Map<Int, List<MoveAcquisition>>,
    ) = source.forEach { (species, values) -> target.getOrPut(species) { mutableListOf() }.addAll(values) }

    private fun evidence(
        capability: RomCapability,
        candidate: Candidate?,
        availableReason: String,
        unavailableReason: String,
        unavailableStatus: CapabilityStatus = CapabilityStatus.NOT_FOUND,
    ) = CapabilityEvidence(
        capability = capability,
        compatible = candidate != null,
        confidence = candidate?.confidence ?: 0.0,
        offset = candidate?.sourceOffset,
        count = candidate?.acquisitions?.values?.sumOf { it.size },
        reasons = listOf(if (candidate != null) availableReason else unavailableReason),
        status = if (candidate != null) CapabilityStatus.AVAILABLE else unavailableStatus,
    )

    private data class Candidate(
        val sourceOffset: Int,
        val confidence: Double,
        val acquisitions: Map<Int, List<MoveAcquisition>>,
    )

    private data class BitfieldPairCandidate(
        val moveListOffset: Int,
        val moves: List<Int>,
        val flagsOffset: Int,
        val rowBytes: Int,
        val confidence: Double,
    )

    private data class AdjacentTutorCandidate(
        val moveListOffset: Int,
        val flagsOffset: Int,
        val rowBytes: Int,
        val moves: List<Int>,
        val confidence: Double,
        val referenceCount: Int,
    )

    private data class RuntimeTutorPair(
        val moveListOffset: Int,
        val moves: List<Int>,
        val flagsOffset: Int,
        val validation: BitfieldValidation,
        val proximity: Int,
    )

    private data class BitfieldValidation(
        val confidence: Double,
        val rowBytes: Int,
    )

    private data class IndexedTutorTable(
        val offset: Int,
        val tutorCount: Int,
        val indexesBySpecies: Map<Int, List<Int>>,
        val nonEmptySpecies: Int,
        val confidence: Double,
    )

    private data class IndexedTutorPair(
        val table: IndexedTutorTable,
        val moveListOffset: Int,
        val moves: List<Int>,
        val confidence: Double,
        val proximity: Int,
    )

    private data class InferredBitfieldLayout(
        val bitCount: Int,
        val rowBytes: Int,
        val confidence: Double,
    )

    private data class LegacyAcquisitions(
        val machine: Candidate? = null,
        val tutor: Candidate? = null,
    )

    private const val NEAR_REFERENCE_LITERAL_WINDOW = 32
    private const val CORROBORATED_REFERENCE_LITERAL_WINDOW = 96
    private const val MACHINE_CODE_WINDOW = 64
    private const val MACHINE_BITFIELD_CODE_WINDOW = 80
    private const val TUTOR_BITFIELD_CODE_WINDOW = 48
    private const val NEAR_TUTOR_RUNTIME_REFERENCE_DISTANCE = 64
    private const val MIN_ACTIVE_HMS = 6
    private const val MIN_TUTOR_COUNT = 4
    private const val MAX_TUTOR_COUNT = 128
    private const val MAX_STANDARD_TUTOR_COUNT = 40
    private const val MAX_RUNTIME_TUTOR_COUNT = 32
    private const val MAX_TUTOR_MOVES_PER_SPECIES = 128
    private const val MIN_TUTOR_SPECIES_COVERAGE = 0.8
    private const val CFRU_MACHINE_FLAGS_SLOT = 0x043C68
    private const val CFRU_MACHINE_MOVES_SLOT = 0x125A8C
    private const val CFRU_TUTOR_MOVES_SLOT = 0x120BE4
    private const val CFRU_TUTOR_FLAGS_SLOT = 0x120C30
    private const val MAX_INDIRECT_BITFIELD_ROW_BYTES = 32
    private const val MIN_INDIRECT_MOVE_COUNT = 4
    private const val MIN_MACHINE_PREFIX_SIMILARITY = 0.8
    private const val MIN_MACHINE_RUNTIME_PREFIX_SIMILARITY = 0.65
    private const val MIN_TUTOR_PREFIX_SIMILARITY = 0.8
    private val GEN_THREE_TM_PREFIX = listOf(
        264, 337, 352, 347, 46, 92, 258, 339, 331, 237, 241, 269, 58, 59, 63, 113, 182,
        240, 202, 219, 218, 76, 231, 85, 87, 89, 216, 91, 94, 247, 280, 104, 115, 351, 53,
        188, 201, 126, 317, 332, 259, 263, 290, 156, 213, 168, 211, 285, 289, 315,
    )
    private val GEN_THREE_TUTOR_PREFIX = listOf(
        5, 14, 25, 34, 38, 68, 69, 102, 118, 135, 138, 86, 153, 157, 164,
    )
    private const val GEN_TWO_MACHINE_COUNT = 57
    private const val GEN_TWO_TUTOR_COUNT = 3
    private const val GEN_TWO_FLAG_OFFSET = 24
    private const val GEN_TWO_FLAG_BYTES = 8
    private const val MAX_EGG_MOVES_PER_SPECIES = 32
}
