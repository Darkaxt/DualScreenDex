package com.enrpau.dualscreendex.parser.dataset.types

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession

/** Sole byte interpreter for the supported Gen III type-chart ABIs. */
class TypeChartCodec {
    fun decode(
        session: RomAnalysisSession,
        layout: TypeChartTableLayout,
    ): TypeChartTableOutcome = when (layout.abi) {
        TypeChartAbi.LEGACY_TRIPLETS -> decodeLegacy(session, layout)
        TypeChartAbi.DENSE_U32_Q412 -> decodeDenseU32(session, layout)
        TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE -> decodeDenseU16Pair(session, layout)
    }

    private fun decodeLegacy(
        session: RomAnalysisSession,
        layout: TypeChartTableLayout,
    ): TypeChartTableOutcome {
        if (layout.offset < 0 || layout.offset > Int.MAX_VALUE.toLong()) {
            return rejected(layout, "legacy type-chart offset is not indexable")
        }
        val rows = mutableListOf<TypeChartRow>()
        var cursor = layout.offset
        while (true) {
            val observed = try {
                Math.subtractExact(Math.addExact(cursor, LEGACY_RECORD_BYTES), layout.offset)
            } catch (_: ArithmeticException) {
                return rejected(layout, "legacy type-chart extent overflows Long")
            }
            if (observed > session.limits.maxDatasetExtentBytes) {
                return TypeChartTableOutcome.ExtentBudgetExceeded(
                    layout = layout,
                    observedBytes = observed,
                    limitBytes = session.limits.maxDatasetExtentBytes,
                    reason = "legacy type-chart extent $observed exceeds deterministic budget " +
                        session.limits.maxDatasetExtentBytes,
                )
            }
            val end = try {
                Math.addExact(cursor, LEGACY_RECORD_BYTES)
            } catch (_: ArithmeticException) {
                return rejected(layout, "legacy type-chart end overflows Long")
            }
            if (cursor < 0 || end > session.rom.size.toLong() || cursor > Int.MAX_VALUE.toLong()) {
                return rejected(layout, "legacy type chart has no complete terminator within the ROM")
            }
            val indexed = cursor.toInt()
            val attacking = session.rom.u8(indexed)
            val defending = session.rom.u8(indexed + 1)
            val encoded = session.rom.u8(indexed + 2)
            if (attacking in LEGACY_TERMINATORS && defending == attacking) {
                if (rows.size < MINIMUM_LEGACY_RECORDS) {
                    return rejected(
                        layout,
                        "legacy type chart has ${rows.size} rows; at least $MINIMUM_LEGACY_RECORDS are required",
                    )
                }
                return TypeChartTableOutcome.Decoded(layout, rows)
            }
            if (attacking in LEGACY_TERMINATORS || defending in LEGACY_TERMINATORS) {
                return rejected(layout, "legacy type chart contains a partial terminator")
            }
            if (attacking !in TYPE_ID_RANGE || defending !in TYPE_ID_RANGE) {
                return rejected(layout, "legacy type chart contains a type ID above the bounded maximum")
            }
            if (encoded !in LEGACY_MULTIPLIERS) {
                return rejected(layout, "legacy type chart contains unsupported multiplier $encoded")
            }
            rows += TypeChartRow(
                rowIndex = rows.size,
                matchup = TypeChartMatchup(attacking, defending, encoded * 10),
                encodedMultiplier = encoded.toLong(),
            )
            cursor = end
        }
    }

    private fun decodeDenseU32(
        session: RomAnalysisSession,
        layout: TypeChartTableLayout,
    ): TypeChartTableOutcome {
        if (layout.offset % U32_BYTES != 0L) {
            return rejected(layout, "dense u32 type chart is not u32-aligned")
        }
        val typeCount = validatedTypeCount(layout) ?: return rejected(
            layout,
            "dense u32 type chart requires a type count within 1..$MAX_TYPES",
        )
        val cells = square(typeCount) ?: return rejected(layout, "dense u32 cell count overflows Long")
        val extent = when (val result = checkedExtent(session, layout, cells, U32_BYTES)) {
            is CodecExtent.Valid -> result.value
            is CodecExtent.Failed -> return result.outcome
        }
        val rows = ArrayList<TypeChartRow>(cells.toInt())
        val encodedValues = ArrayList<Long>(cells.toInt())
        repeat(cells.toInt()) { cell ->
            val encoded = session.rom.u32le(extent.offset + cell * U32_BYTES.toInt())
            if (!isPlausibleQ412(encoded)) {
                return rejected(layout, "dense u32 type chart contains implausible Q4.12 value $encoded")
            }
            encodedValues += encoded
            rows += denseRow(cell, typeCount, encoded)
        }
        if (!isStructurallyPlausible(encodedValues, typeCount)) {
            return rejected(layout, "dense u32 type chart fails matrix-level decoy guards")
        }
        return TypeChartTableOutcome.Decoded(layout, rows)
    }

    private fun decodeDenseU16Pair(
        session: RomAnalysisSession,
        layout: TypeChartTableLayout,
    ): TypeChartTableOutcome {
        if (layout.offset % U16_BYTES != 0L) {
            return rejected(layout, "dense u16 type chart is not u16-aligned")
        }
        val typeCount = validatedTypeCount(layout) ?: return rejected(
            layout,
            "dense u16 type chart requires a type count within 1..$MAX_TYPES",
        )
        val cells = square(typeCount) ?: return rejected(layout, "dense u16 cell count overflows Long")
        val values = try {
            Math.multiplyExact(cells, 2L)
        } catch (_: ArithmeticException) {
            return rejected(layout, "dense u16 pair value count overflows Long")
        }
        val extent = when (val result = checkedExtent(session, layout, values, U16_BYTES)) {
            is CodecExtent.Valid -> result.value
            is CodecExtent.Failed -> return result.outcome
        }
        val matrixBytes = try {
            Math.multiplyExact(cells, U16_BYTES)
        } catch (_: ArithmeticException) {
            return rejected(layout, "dense u16 matrix size overflows Long")
        }
        val rows = ArrayList<TypeChartRow>(cells.toInt())
        val primaryValues = ArrayList<Long>(cells.toInt())
        val inverseValues = ArrayList<Long>(cells.toInt())
        repeat(cells.toInt()) { cell ->
            val primaryOffset = extent.offset + cell * U16_BYTES.toInt()
            val inverseOffset = Math.addExact(primaryOffset.toLong(), matrixBytes).toInt()
            val primary = session.rom.u16le(primaryOffset).toLong()
            val inverse = session.rom.u16le(inverseOffset).toLong()
            if (!isPlausibleQ412(primary) || !isPlausibleQ412(inverse)) {
                return rejected(layout, "dense u16 pair contains an implausible Q4.12 value")
            }
            val expectedInverse = when {
                primary < Q412_ONE -> Q412_DOUBLE
                primary > Q412_ONE -> Q412_HALF
                else -> Q412_ONE
            }
            if (inverse != expectedInverse) {
                return rejected(
                    layout,
                    "dense u16 pair violates inverse semantics at cell $cell " +
                        "($primary requires $expectedInverse, observed $inverse)",
                )
            }
            primaryValues += primary
            inverseValues += inverse
            rows += denseRow(cell, typeCount, primary)
        }
        if (
            !isStructurallyPlausible(primaryValues, typeCount) ||
            !isStructurallyPlausible(inverseValues, typeCount)
        ) {
            return rejected(layout, "dense u16 pair fails matrix-level decoy guards")
        }
        return TypeChartTableOutcome.Decoded(layout, rows)
    }

    private fun checkedExtent(
        session: RomAnalysisSession,
        layout: TypeChartTableLayout,
        count: Long,
        recordSize: Long,
    ): CodecExtent = when (
        val checked = session.limits.checkTableExtent(
            offset = layout.offset,
            count = count,
            recordSize = recordSize,
            romSize = session.rom.size.toLong(),
        )
    ) {
        is ExtentCheck.Valid -> CodecExtent.Valid(checked.extent)
        is ExtentCheck.Invalid -> CodecExtent.Failed(rejected(layout, checked.reason))
        is ExtentCheck.BudgetExceeded -> CodecExtent.Failed(
            TypeChartTableOutcome.ExtentBudgetExceeded(
                layout,
                checked.observedBytes,
                checked.limitBytes,
                "type-chart extent ${checked.observedBytes} exceeds deterministic budget " +
                    checked.limitBytes,
            ),
        )
    }

    private fun validatedTypeCount(layout: TypeChartTableLayout): Int? =
        layout.typeCount?.takeIf { it in 1..MAX_TYPES }

    private fun square(value: Int): Long? = try {
        Math.multiplyExact(value.toLong(), value.toLong())
    } catch (_: ArithmeticException) {
        null
    }

    private fun denseRow(cell: Int, typeCount: Int, encoded: Long): TypeChartRow = TypeChartRow(
        rowIndex = cell,
        matchup = TypeChartMatchup(
            attackingTypeId = cell / typeCount,
            defendingTypeId = cell % typeCount,
            effectivenessPercent = ((encoded * 100L + Q412_ONE / 2L) / Q412_ONE).toInt(),
        ),
        encodedMultiplier = encoded,
    )

    private fun isPlausibleQ412(value: Long): Boolean = value in 0L..MAX_Q412_VALUE

    private fun isStructurallyPlausible(values: List<Long>, typeCount: Int): Boolean {
        val neutral = values.count { it == Q412_ONE }
        val nonNeutral = values.size - neutral
        val common = values.count { it in COMMON_Q412_MULTIPLIERS }
        return neutral.toLong() * 5L >= values.size.toLong() * 2L &&
            nonNeutral >= typeCount &&
            values.toSet().size >= 3 &&
            common.toLong() * 10L >= values.size.toLong() * 9L
    }

    private fun rejected(layout: TypeChartTableLayout, reason: String): TypeChartTableOutcome.Rejected =
        TypeChartTableOutcome.Rejected(layout, reason)

    private sealed interface CodecExtent {
        data class Valid(val value: com.enrpau.dualscreendex.parser.analysis.CheckedRomExtent) : CodecExtent
        data class Failed(val outcome: TypeChartTableOutcome) : CodecExtent
    }

    private companion object {
        const val MAX_TYPES = 64
        const val LEGACY_RECORD_BYTES = 3L
        const val U16_BYTES = 2L
        const val U32_BYTES = 4L
        const val MINIMUM_LEGACY_RECORDS = 10
        const val Q412_ONE = 4096L
        const val Q412_HALF = 2048L
        const val Q412_DOUBLE = 8192L
        const val MAX_Q412_VALUE = 0xFFFFL
        val TYPE_ID_RANGE = 0 until MAX_TYPES
        val LEGACY_TERMINATORS = setOf(0xFE, 0xFF)
        val LEGACY_MULTIPLIERS = setOf(0, 5, 10, 20)
        val COMMON_Q412_MULTIPLIERS = setOf(
            0L,
            512L,
            819L,
            1024L,
            1365L,
            2048L,
            2730L,
            4096L,
            6144L,
            8192L,
            12288L,
            16384L,
            20480L,
            32768L,
        )
    }
}
