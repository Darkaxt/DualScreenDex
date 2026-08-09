package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomCapability

data class MoveAcquisitionMaterialization(
    val acquisitionsBySpecies: Map<Int, List<MoveAcquisition>>,
    val evidence: Map<MoveAcquisitionMethod, CapabilityEvidence>,
)

object MoveAcquisitionMaterializer {
    private const val EGG_SPECIES_OFFSET = 20_000

    fun materialize(rom: RomImage, layout: ResolvedRomLayout): MoveAcquisitionMaterialization {
        val bySpecies = linkedMapOf<Int, MutableList<MoveAcquisition>>()
        val evidence = linkedMapOf<MoveAcquisitionMethod, CapabilityEvidence>()
        val gbaReferences = if (layout.generation == 3) referencedTargets(rom) else emptyMap()

        val machines = if (layout.generation <= 2) {
            embeddedMachines(rom, layout)
        } else {
            referencedBitfieldPair(rom, layout, 58, MoveAcquisitionMethod.MACHINE, gbaReferences)
        }
        merge(bySpecies, machines?.acquisitions.orEmpty())
        evidence[MoveAcquisitionMethod.MACHINE] = evidence(
            RomCapability.MACHINE_MOVES,
            machines,
            "validated ROM machine-move list and species compatibility flags",
            "machine-move list and compatibility flags were not jointly resolved",
        )

        val eggs = if (layout.generation == 3) eggMoves(rom, layout) else null
        merge(bySpecies, eggs?.acquisitions.orEmpty())
        evidence[MoveAcquisitionMethod.EGG] = evidence(
            RomCapability.EGG_MOVES,
            eggs,
            "validated sentinel-delimited ROM egg-move list",
            if (layout.generation == 1) "breeding is not part of this engine" else "egg-move table was not resolved",
            if (layout.generation == 1) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
        )

        val tutors = if (layout.generation == 3) referencedTutorPair(rom, layout, gbaReferences, machines?.sourceOffset) else null
        merge(bySpecies, tutors?.acquisitions.orEmpty())
        evidence[MoveAcquisitionMethod.TUTOR] = evidence(
            RomCapability.TUTOR_MOVES,
            tutors,
            "validated referenced ROM tutor-move list and species compatibility flags",
            if (layout.generation == 1) "move tutors are not part of this engine" else "tutor move list and compatibility flags were not jointly resolved",
            if (layout.generation == 1) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
        )

        return MoveAcquisitionMaterialization(
            acquisitionsBySpecies = bySpecies.mapValues { (_, values) -> values.distinct() },
            evidence = evidence,
        )
    }

    private fun embeddedMachines(rom: RomImage, layout: ResolvedRomLayout): Candidate? {
        val stats = layout.tables.baseStats ?: return null
        val moveCount = layout.moveCount ?: return null
        val machineCount = if (layout.generation == 1) 55 else 57
        val flagOffset = if (layout.generation == 1) 20 else 24
        val flagBytes = if (layout.generation == 1) 7 else 8
        if (stats.recordSize < flagOffset + flagBytes) return null

        val lists = mutableMapOf<List<Int>, Int>()
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
            if (valid) lists.putIfAbsent(moves, offset)
            offset++
        }
        if (lists.size != 1) return null
        val (moves, sourceOffset) = lists.entries.single().let { it.key to it.value }
        val acquisitions = linkedMapOf<Int, List<MoveAcquisition>>()
        repeat(stats.count) { index ->
            val row = stats.offset + index * stats.recordSize + flagOffset
            if (row + flagBytes > rom.size) return null
            val values = moves.mapIndexedNotNull { bit, moveId ->
                val enabled = rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0
                MoveAcquisition(moveId, MoveAcquisitionMethod.MACHINE, bit + 1).takeIf { enabled }
            }
            acquisitions[index + 1] = values
        }
        return Candidate(sourceOffset, 1.0, acquisitions)
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
                        if (species in seenSpecies || (currentSpecies > 0 && acquisitions[currentSpecies].isNullOrEmpty())) {
                            valid = false
                        } else {
                            currentSpecies = species
                            seenSpecies += species
                            groups++
                        }
                    }
                    currentSpecies > 0 && value in 1 until moveCount -> {
                        acquisitions.getOrPut(currentSpecies) { mutableListOf() } +=
                            MoveAcquisition(value, MoveAcquisitionMethod.EGG)
                        moves++
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
        val candidates = (4..40).mapNotNull { count ->
            referencedBitfieldPair(rom, layout, count, MoveAcquisitionMethod.TUTOR, references)
        }.filterNot { it.sourceOffset == machineOffset }
        return candidates.maxWithOrNull(
            compareBy<Candidate> { it.confidence }
                .thenBy { candidate -> candidate.acquisitions.values.flatten().mapNotNull { it.sourceId }.maxOrNull() ?: 0 },
        )
    }

    private fun referencedBitfieldPair(
        rom: RomImage,
        layout: ResolvedRomLayout,
        itemCount: Int,
        method: MoveAcquisitionMethod,
        references: Map<Int, List<Int>>,
    ): Candidate? {
        val speciesCount = layout.speciesCount ?: return null
        val moveCount = layout.moveCount ?: return null
        val candidates = mutableListOf<Candidate>()
        references.forEach { (moveListOffset, moveRefs) ->
            if (moveListOffset + itemCount * 2 > rom.size) return@forEach
            val moves = List(itemCount) { rom.u16le(moveListOffset + it * 2) }
            if (moves.any { it !in 1 until moveCount } || moves.distinct().size != moves.size) return@forEach
            val nearbyTargets = moveRefs.flatMap { reference ->
                buildList {
                    var nearby = maxOf(0, reference - 32)
                    val end = minOf(rom.size - 4, reference + 32)
                    while (nearby <= end) {
                        rom.gbaPointer(nearby)?.let(::add)
                        nearby += 4
                    }
                }
            }.distinct()
            nearbyTargets.filter { it != moveListOffset }.forEach { flagsOffset ->
                val validation = validateBitfield(rom, flagsOffset, speciesCount, itemCount) ?: return@forEach
                val rowBytes = ((itemCount + 31) / 32) * 4
                val acquisitions = (0 until speciesCount).associate { species ->
                    val row = flagsOffset + species * rowBytes
                    species to moves.mapIndexedNotNull { bit, moveId ->
                        val enabled = rom.u8(row + bit / 8) and (1 shl (bit % 8)) != 0
                        MoveAcquisition(moveId, method, bit + 1).takeIf { enabled }
                    }
                }
                candidates += Candidate(moveListOffset, validation, acquisitions)
            }
        }
        val distinct = candidates.distinctBy { it.acquisitions }
        return distinct.singleOrNull() ?: distinct.maxByOrNull { it.confidence }?.takeIf { best ->
            distinct.count { best.confidence - it.confidence < 0.05 } == 1
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

    private fun validateBitfield(rom: RomImage, offset: Int, speciesCount: Int, bitCount: Int): Double? {
        val rowBytes = ((bitCount + 31) / 32) * 4
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
        return (speciesRatio + bitRatio) / 2.0
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
}
