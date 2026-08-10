package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.validate.TableValidators

/** Resolves referenced CFRU/DPE data tables without assuming which published pointer slot a fork retained. */
object Gen3DynamicTableResolver {
    fun resolve(
        rom: RomImage,
        inherited: ProfileTables,
        speciesCount: Int,
        moveCount: Int,
    ): ProfileTables {
        val statsValid = inherited.baseStats?.let {
            TableValidators.baseStats(rom, it.offset, speciesCount, it.recordSize, generation = 3).compatible
        } == true
        val standardMovesValid = inherited.moveData?.let {
            TableValidators.moveData(rom, it.offset, moveCount, it.recordSize, generation = 3).compatible
        } == true
        if (statsValid && standardMovesValid) return inherited

        val references = referencedTargets(rom, speciesCount, moveCount)
        val candidates = buildSet {
            addAll(references.keys)
            PUBLISHED_POINTER_SLOTS.mapNotNullTo(this) { slot ->
                if (slot + 4 <= rom.size) rom.gbaPointer(slot) else null
            }
            inherited.baseStats?.offset?.let(::add)
            inherited.moveData?.offset?.let(::add)
        }

        val stats = if (statsValid) inherited.baseStats else candidates.asSequence()
            .filter { it % 4 == 0 && it.toLong() + speciesCount.toLong() * 28 <= rom.size }
            .mapNotNull { offset ->
                if (!plausibleStatsSample(rom, offset, speciesCount)) return@mapNotNull null
                val evidence = TableValidators.baseStats(rom, offset, speciesCount, 28, generation = 3)
                if (evidence.compatible) evidence to (references[offset] ?: 0) else null
            }
            .sortedWith(candidateOrder)
            .firstOrNull()
            ?.first
            ?.toLayout()

        val moves = if (standardMovesValid) inherited.moveData else candidates.asSequence()
            .filter { it % 4 == 0 && it.toLong() + moveCount.toLong() * 16 <= rom.size }
            .mapNotNull { offset ->
                if (!plausibleMoveSample(rom, offset, moveCount)) return@mapNotNull null
                val evidence = TableValidators.cfruMoveData(rom, offset, moveCount)
                if (evidence.compatible) evidence to (references[offset] ?: 0) else null
            }
            .sortedWith(candidateOrder)
            .firstOrNull()
            ?.first
            ?.toLayout(TableRecordFormat.CFRU_MOVE_16)

        return inherited.copy(
            baseStats = stats ?: inherited.baseStats,
            moveData = moves ?: inherited.moveData,
        )
    }

    private fun referencedTargets(rom: RomImage, speciesCount: Int, moveCount: Int): Map<Int, Int> {
        val maximumSpan = maxOf(speciesCount * 28, moveCount * 16)
        val maximumTarget = rom.size - maximumSpan
        if (maximumTarget < 0) return emptyMap()
        val counts = HashMap<Int, Int>()
        var offset = 0
        while (offset <= rom.size - 4) {
            val target = rom.gbaPointer(offset)
            if (target != null && target % 4 == 0 && target <= maximumTarget) {
                counts[target] = (counts[target] ?: 0) + 1
            }
            offset += 4
        }
        return counts.filterValues { it >= 2 }
    }

    private fun plausibleStatsSample(rom: RomImage, offset: Int, count: Int): Boolean {
        val sample = minOf(count, 96)
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

    private fun plausibleMoveSample(rom: RomImage, offset: Int, count: Int): Boolean {
        val sample = minOf(count, 96)
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
        return plausible == sample - 1 && populated * 5 >= (sample - 1) * 4
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

    private val PUBLISHED_POINTER_SLOTS = listOf(0x128, 0x144, 0x148, 0x1BC, 0x1C0, 0x1CC)
}
