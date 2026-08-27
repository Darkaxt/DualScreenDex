package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomBoundsException

/** Sole byte interpreter for fixed Gen III evolution rows and combined Gen I/II streams. */
class EvolutionCodec {
    fun decodeGen3(
        session: RomAnalysisSession,
        layout: EvolutionTableLayout,
        maximumMethod: Int = 255,
    ): EvolutionTableOutcome {
        if (layout.count <= 0 || layout.slotsPerSpecies <= 0 || layout.recordSize !in setOf(6, 8)) {
            return EvolutionTableOutcome.Rejected(layout, "invalid Gen III evolution layout metadata")
        }
        if (maximumMethod <= 0) {
            return EvolutionTableOutcome.Rejected(layout, "maximum evolution method must be positive")
        }
        val rowStride = try {
            layout.rowStride
        } catch (_: ArithmeticException) {
            return EvolutionTableOutcome.Rejected(layout, "evolution row stride overflows Long")
        }
        val extent = when (
            val checked = session.limits.checkTableExtent(
                offset = layout.offset,
                count = layout.count,
                recordSize = rowStride,
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Valid -> checked.extent
            is ExtentCheck.Invalid -> return EvolutionTableOutcome.Rejected(layout, checked.reason)
            is ExtentCheck.BudgetExceeded -> return EvolutionTableOutcome.ExtentBudgetExceeded(
                layout,
                checked.observedBytes,
                checked.limitBytes,
                "evolution table extent ${checked.observedBytes} exceeds deterministic budget " +
                    checked.limitBytes,
            )
        }
        val rows = List(layout.count.toInt()) { rowIndex ->
            decodeGen3Row(
                session = session,
                rowIndex = rowIndex,
                rowOffset = extent.offset + rowIndex * rowStride.toInt(),
                slotsPerSpecies = layout.slotsPerSpecies,
                recordSize = layout.recordSize,
                speciesCount = layout.count.toInt(),
                maximumMethod = maximumMethod,
            )
        }
        return EvolutionTableOutcome.Decoded(layout, rows)
    }

    fun characterizeGen12Combined(
        session: RomAnalysisSession,
        layout: Gen12CombinedStreamLayout,
    ): Gen12CombinedStreamOutcome {
        if (
            layout.count <= 0 || layout.tableBank < 0 || layout.generation !in 1..2 ||
            layout.moveCount <= 0
        ) {
            return Gen12CombinedStreamOutcome.Rejected(layout, "invalid Gen I/II combined stream metadata")
        }
        when (
            val checked = session.limits.checkTableExtent(
                offset = layout.pointerTableOffset,
                count = layout.count.toLong(),
                recordSize = GB_POINTER_SIZE,
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Invalid -> return Gen12CombinedStreamOutcome.Rejected(layout, checked.reason)
            is ExtentCheck.BudgetExceeded -> return Gen12CombinedStreamOutcome.ExtentBudgetExceeded(
                layout,
                checked.observedBytes,
                checked.limitBytes,
                "combined stream pointer table exceeds deterministic extent budget",
            )
            is ExtentCheck.Valid -> Unit
        }
        val rows = List(layout.count) { rowIndex ->
            try {
                characterizeGen12Row(session, layout, rowIndex)
            } catch (error: RomBoundsException) {
                Gen12CombinedRowCharacterization(
                    rowIndex = rowIndex,
                    evolutions = EvolutionRowOutcome.Malformed(
                        rowIndex,
                        emptyList(),
                        listOf("combined relationship row exceeds the ROM extent"),
                    ),
                    learnsetEntries = 0,
                    learnsetValid = false,
                )
            }
        }
        return Gen12CombinedStreamOutcome.Decoded(layout, rows)
    }

    private fun decodeGen3Row(
        session: RomAnalysisSession,
        rowIndex: Int,
        rowOffset: Int,
        slotsPerSpecies: Int,
        recordSize: Int,
        speciesCount: Int,
        maximumMethod: Int,
    ): EvolutionRowOutcome {
        if (rowIndex == 0) return EvolutionRowOutcome.StructuralEmpty(rowIndex)
        val edges = mutableListOf<EvolutionEdgeValue>()
        val reasons = mutableListOf<String>()
        repeat(slotsPerSpecies) { slot ->
            val record = rowOffset + slot * recordSize
            val method = session.rom.u16le(record)
            if (method == 0) return@repeat // Disabled-slot payload is intentionally opaque.
            val parameter = session.rom.u16le(record + 2)
            val target = session.rom.u16le(record + 4)
            val condition = if (recordSize == 8) session.rom.u16le(record + 6) else null
            if (method == 0xFFFF && parameter == 0 && target == 0 && (condition == null || condition == 0)) {
                return@repeat
            }
            if (method !in 1..maximumMethod && method !in RESERVED_TRANSFORMATION_METHODS) {
                reasons += "slot $slot has unsupported method $method"
                return@repeat
            }
            // Battle-only transformation records can target internal form IDs that are outside
            // the independently proven navigable species domain. They are valid engine records,
            // but are not Pokédex evolution links. In-domain transformation targets remain typed.
            if (method in RESERVED_TRANSFORMATION_METHODS && target !in 1 until speciesCount &&
                target !in setOf(0, 0xFFFF)
            ) {
                return@repeat
            }
            if (target !in 1 until speciesCount) {
                reasons += "slot $slot has target $target outside 1..<${speciesCount}"
                return@repeat
            }
            edges += EvolutionEdgeValue(
                targetSpeciesId = target,
                methodId = method,
                parameter = parameter,
                conditionValue = condition,
                raw = session.rom.slice(record, recordSize),
            )
        }
        return when {
            reasons.isNotEmpty() -> EvolutionRowOutcome.Malformed(rowIndex, edges, reasons)
            edges.isEmpty() -> EvolutionRowOutcome.StructuralEmpty(rowIndex)
            else -> EvolutionRowOutcome.Decoded(rowIndex, edges)
        }
    }

    private fun characterizeGen12Row(
        session: RomAnalysisSession,
        layout: Gen12CombinedStreamLayout,
        rowIndex: Int,
    ): Gen12CombinedRowCharacterization {
        val pointerField = layout.pointerTableOffset.toInt() + rowIndex * GB_POINTER_SIZE.toInt()
        var cursor = session.rom.gbBankAddress(layout.tableBank, session.rom.u16le(pointerField))
            ?: return Gen12CombinedRowCharacterization(
                rowIndex,
                EvolutionRowOutcome.Malformed(rowIndex, emptyList(), listOf("invalid banked stream pointer")),
                learnsetEntries = 0,
                learnsetValid = false,
            )
        val edges = mutableListOf<EvolutionEdgeValue>()
        val evolutionReasons = mutableListOf<String>()
        var evolutionEntries = 0
        while (evolutionEntries < MAX_GEN12_EVOLUTIONS) {
            val method = session.rom.u8(cursor)
            if (method == 0) break
            val width = evolutionWidth(layout.generation, method)
            if (width == null) {
                evolutionReasons += "unsupported Gen ${layout.generation} evolution method $method"
                break
            }
            val target = session.rom.u8(cursor + width - 1)
            if (target !in 1..layout.count) {
                evolutionReasons += "evolution target $target outside 1..${layout.count}"
                break
            }
            edges += EvolutionEdgeValue(
                targetSpeciesId = target,
                methodId = method,
                parameter = session.rom.u8(cursor + 1),
                conditionValue = null,
                raw = session.rom.slice(cursor, width),
            )
            cursor += width
            evolutionEntries++
        }
        if (session.rom.u8(cursor) != 0) {
            evolutionReasons += "evolution list has no terminator within $MAX_GEN12_EVOLUTIONS entries"
        } else {
            cursor++
        }
        var learnsetEntries = 0
        var learnsetValid = evolutionReasons.isEmpty()
        try {
            while (learnsetEntries < MAX_GEN12_LEARNSET_ENTRIES) {
                val level = session.rom.u8(cursor)
                if (level == 0) break
                val move = session.rom.u8(cursor + 1)
                if (level !in 1..100 || move !in 1..layout.moveCount) {
                    learnsetValid = false
                    break
                }
                cursor += 2
                learnsetEntries++
            }
            if (session.rom.u8(cursor) != 0) learnsetValid = false
        } catch (_: RomBoundsException) {
            learnsetValid = false
        }
        val evolutionOutcome = when {
            evolutionReasons.isNotEmpty() -> EvolutionRowOutcome.Malformed(rowIndex, edges, evolutionReasons)
            edges.isEmpty() -> EvolutionRowOutcome.StructuralEmpty(rowIndex)
            else -> EvolutionRowOutcome.Decoded(rowIndex, edges)
        }
        return Gen12CombinedRowCharacterization(
            rowIndex,
            evolutionOutcome,
            learnsetEntries,
            learnsetValid,
        )
    }

    private fun evolutionWidth(generation: Int, method: Int): Int? = when (generation) {
        1 -> when (method) {
            1, 3 -> 3
            2 -> 4
            else -> null
        }
        2 -> when (method) {
            in 1..4 -> 3
            5 -> 4
            else -> null
        }
        else -> null
    }

    private companion object {
        const val GB_POINTER_SIZE = 2L
        const val MAX_GEN12_EVOLUTIONS = 16
        const val MAX_GEN12_LEARNSET_ENTRIES = 128
        val RESERVED_TRANSFORMATION_METHODS = 0xFFFD..0xFFFF
    }
}
