package com.enrpau.dualscreendex.parser.dataset.encounters

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession

fun interface EncounterTableDecoder {
    fun decode(session: RomAnalysisSession, layout: Gen3EncounterTableLayout): EncounterTableOutcome
}

class Gen3EncounterCodec : EncounterTableDecoder {
    override fun decode(
        session: RomAnalysisSession,
        layout: Gen3EncounterTableLayout,
    ): EncounterTableOutcome = decode(
        session,
        layout,
        EncounterDecodeLimits(),
    )

    fun decode(
        session: RomAnalysisSession,
        layout: Gen3EncounterTableLayout,
        limits: EncounterDecodeLimits,
    ): EncounterTableOutcome = decodeWithMeter(
        session,
        layout,
        EncounterDecodeMeter(session.limits.maxProbeWorkPerDataset.toLong(), limits),
    )

    internal fun decodeWithMeter(
        session: RomAnalysisSession,
        layout: Gen3EncounterTableLayout,
        meter: EncounterDecodeMeter,
    ): EncounterTableOutcome = try {
        decodeTable(session, layout, meter)
    } catch (budget: EncounterBudgetException) {
        EncounterTableOutcome.BudgetExceeded(
            layout = layout,
            budgetKind = budget.kind,
            observed = budget.observed,
            limit = budget.limit,
            observationComplete = budget.observationComplete,
            reason = requireNotNull(budget.message),
        )
    } catch (_: ArithmeticException) {
        EncounterTableOutcome.Rejected(layout, "encounter table extent overflows checked Long arithmetic")
    } catch (_: IndexOutOfBoundsException) {
        EncounterTableOutcome.Rejected(layout, "encounter table crosses ROM bounds")
    } catch (invalid: IllegalArgumentException) {
        EncounterTableOutcome.Rejected(layout, invalid.message ?: "encounter table is malformed")
    }

    internal fun isClassicEmptyFirst(
        session: RomAnalysisSession,
        layout: Gen3EncounterTableLayout,
    ): Boolean = layout.abi == Gen3EncounterAbi.CLASSIC_24 &&
        isStructuralEmptyFirst(session, layout)

    internal fun isStructuralEmptyFirst(
        session: RomAnalysisSession,
        layout: Gen3EncounterTableLayout,
    ): Boolean {
        val offset = layout.offset.toIndexedInt() ?: return false
        if (offset.toLong() + layout.abi.headerSize > session.rom.size.toLong()) return false
        if (!validGroupMap(session.rom.u8(offset), session.rom.u8(offset + 1))) return false
        if (session.rom.u8(offset + 2) != 0 || session.rom.u8(offset + 3) != 0) return false
        return specs(layout.abi).all { spec -> session.rom.u32le(offset + spec.pointerOffset) == 0L }
    }

    /**
     * Lightweight bounded shell proof used before decoding untyped referenced roots. It proves
     * only header shape and sentinel termination; the normal decoder still owns info/slot truth.
     */
    internal fun provesSentinelShell(
        session: RomAnalysisSession,
        layout: Gen3EncounterTableLayout,
        meter: EncounterDecodeMeter,
        allowEmptyFirst: Boolean,
    ): Boolean {
        val maxHeaders = minOf(layout.maxHeaders, meter.limits.maxHeadersPerTable)
        var row = 0
        while (row < maxHeaders) {
            val header = checkedIndexedOffset(layout.offset, row, layout.abi.headerSize) ?: return false
            if (header.toLong() + 2L > session.rom.size.toLong()) return false
            if (session.rom.u8(header) == 0xFF && session.rom.u8(header + 1) == 0xFF) {
                return row >= layout.minimumHeaders
            }
            if (header.toLong() + layout.abi.headerSize > session.rom.size.toLong()) return false
            meter.work()
            val group = session.rom.u8(header)
            val map = session.rom.u8(header + 1)
            if (!validGroupMap(group, map) ||
                session.rom.u8(header + 2) != 0 || session.rom.u8(header + 3) != 0
            ) {
                return false
            }
            var populated = false
            for (spec in specs(layout.abi)) {
                val pointerField = header + spec.pointerOffset
                val raw = session.rom.u32le(pointerField)
                if (raw != 0L) {
                    populated = true
                    if (session.rom.gbaPointer(pointerField) == null) return false
                }
            }
            if (row == 0 && !populated && !allowEmptyFirst) return false
            row++
        }
        return false
    }

    private fun decodeTable(
        session: RomAnalysisSession,
        layout: Gen3EncounterTableLayout,
        meter: EncounterDecodeMeter,
    ): EncounterTableOutcome {
        val maxHeaders = minOf(layout.maxHeaders, meter.limits.maxHeadersPerTable)
        if (maxHeaders < layout.minimumHeaders) {
            return EncounterTableOutcome.BudgetExceeded(
                layout,
                EncounterBudgetKind.EXTENT,
                layout.minimumHeaders.toLong(),
                maxHeaders.toLong().coerceAtLeast(1L),
                false,
                "encounter header-row budget cannot cover the candidate minimum",
            )
        }
        val rows = ArrayList<EncounterHeaderOutcome>(minOf(maxHeaders, 64))
        var populatedMethods = 0
        var foundSentinel = false
        var rowIndex = 0
        while (rowIndex < maxHeaders) {
            val header = checkedIndexedOffset(layout.offset, rowIndex, layout.abi.headerSize)
                ?: return EncounterTableOutcome.Rejected(layout, "encounter header offset is not indexable")
            if (header.toLong() + 2L > session.rom.size.toLong()) {
                return EncounterTableOutcome.Rejected(layout, "encounter table has no in-bounds sentinel")
            }
            if (session.rom.u8(header) == 0xFF && session.rom.u8(header + 1) == 0xFF) {
                foundSentinel = true
                break
            }
            when (val extent = session.limits.checkTableExtent(
                header.toLong(),
                1,
                layout.abi.headerSize.toLong(),
                session.rom.size.toLong(),
            )) {
                is ExtentCheck.Valid -> Unit
                is ExtentCheck.Invalid -> return EncounterTableOutcome.Rejected(layout, extent.reason)
                is ExtentCheck.BudgetExceeded -> throw EncounterBudgetException(
                    EncounterBudgetKind.EXTENT,
                    extent.observedBytes,
                    extent.limitBytes,
                    true,
                    "encounter header extent budget exceeded",
                )
            }
            meter.work()
            val group = session.rom.u8(header)
            val map = session.rom.u8(header + 1)
            val reasons = mutableListOf<String>()
            if (!validGroupMap(group, map)) reasons += "encounter header has invalid group/map bytes"
            if (session.rom.u8(header + 2) != 0 || session.rom.u8(header + 3) != 0) {
                reasons += "encounter header reserved bytes are not zero"
            }
            val methods = mutableListOf<DecodedEncounterMethod>()
            specs(layout.abi).forEach { spec ->
                meter.work()
                val pointerField = header + spec.pointerOffset
                val raw = session.rom.u32le(pointerField)
                if (raw != 0L) {
                    val info = session.rom.gbaPointer(pointerField)
                    if (info == null) {
                        reasons += "${spec.label} encounter pointer is outside the ROM"
                    } else {
                        when (val decoded = decodeMethod(session, layout, spec, info, meter)) {
                            is MethodDecode.Decoded -> methods += decoded.method
                            is MethodDecode.Malformed -> reasons += decoded.reason
                        }
                    }
                }
            }
            val row = when {
                reasons.isNotEmpty() -> EncounterHeaderOutcome.Malformed(rowIndex, group, map, reasons)
                methods.isEmpty() -> EncounterHeaderOutcome.StructuralEmpty(rowIndex, group, map)
                else -> EncounterHeaderOutcome.Decoded(rowIndex, group, map, methods).also {
                    populatedMethods = Math.addExact(populatedMethods, methods.size)
                }
            }
            if (rowIndex == 0 && row is EncounterHeaderOutcome.StructuralEmpty &&
                layout.abi == Gen3EncounterAbi.STANDARD_20
            ) {
                return EncounterTableOutcome.Rejected(
                    layout,
                    "Standard20 encounter tables require a populated first header",
                )
            }
            meter.retain(rowRetainedBytes(row))
            rows += row
            rowIndex++
        }
        if (!foundSentinel) {
            return EncounterTableOutcome.Rejected(
                layout,
                "encounter table does not terminate with a sentinel within $maxHeaders headers",
            )
        }
        if (rows.size < layout.minimumHeaders) {
            return EncounterTableOutcome.Rejected(
                layout,
                "encounter table has ${rows.size} header(s), below minimum ${layout.minimumHeaders}",
            )
        }
        if (populatedMethods < layout.minimumPopulatedMethods) {
            val malformedSummary = rows
                .filterIsInstance<EncounterHeaderOutcome.Malformed>()
                .flatMap { it.reasons }
                .distinct()
                .sorted()
                .joinToString("; ")
            return EncounterTableOutcome.Rejected(
                layout,
                "encounter table has $populatedMethods populated method(s), below minimum " +
                    layout.minimumPopulatedMethods +
                    malformedSummary.takeIf { it.isNotBlank() }?.let { "; malformed rows: $it" }.orEmpty(),
            )
        }
        val tableBytes = Math.addExact(
            Math.multiplyExact(rows.size.toLong(), layout.abi.headerSize.toLong()),
            SENTINEL_BYTES,
        )
        when (val extent = session.limits.checkTableExtent(
            layout.offset,
            1,
            tableBytes,
            session.rom.size.toLong(),
        )) {
            is ExtentCheck.Valid -> Unit
            is ExtentCheck.Invalid -> return EncounterTableOutcome.Rejected(layout, extent.reason)
            is ExtentCheck.BudgetExceeded -> throw EncounterBudgetException(
                EncounterBudgetKind.EXTENT,
                extent.observedBytes,
                extent.limitBytes,
                true,
                "encounter table extent budget exceeded",
            )
        }
        return EncounterTableOutcome.Decoded(layout, rows)
    }

    private fun decodeMethod(
        session: RomAnalysisSession,
        layout: Gen3EncounterTableLayout,
        spec: MethodSpec,
        info: Int,
        meter: EncounterDecodeMeter,
    ): MethodDecode {
        when (val extent = session.limits.checkTableExtent(
            info.toLong(),
            1,
            INFO_BYTES,
            session.rom.size.toLong(),
        )) {
            is ExtentCheck.Valid -> Unit
            is ExtentCheck.Invalid -> return MethodDecode.Malformed("${spec.label} encounter info: ${extent.reason}")
            is ExtentCheck.BudgetExceeded -> throw EncounterBudgetException(
                EncounterBudgetKind.EXTENT,
                extent.observedBytes,
                extent.limitBytes,
                true,
                "encounter info extent budget exceeded",
            )
        }
        val rate = session.rom.u8(info)
        if (spec.hidden && rate !in 0..1) {
            return MethodDecode.Malformed("hidden encounter environment byte is not land/water")
        }
        if (!spec.hidden && rate !in 0..100) {
            return MethodDecode.Malformed("${spec.label} encounter rate is outside 0..100")
        }
        if ((1..3).any { session.rom.u8(info + it) != 0 }) {
            return MethodDecode.Malformed("${spec.label} encounter info reserved bytes are not zero")
        }
        val slots = session.rom.gbaPointer(info + 4)
            ?: return MethodDecode.Malformed("${spec.label} encounter slot pointer is outside the ROM")
        when (val extent = session.limits.checkTableExtent(
            slots.toLong(),
            spec.slotCount.toLong(),
            SLOT_BYTES,
            session.rom.size.toLong(),
        )) {
            is ExtentCheck.Valid -> Unit
            is ExtentCheck.Invalid -> return MethodDecode.Malformed("${spec.label} encounter slots: ${extent.reason}")
            is ExtentCheck.BudgetExceeded -> throw EncounterBudgetException(
                EncounterBudgetKind.EXTENT,
                extent.observedBytes,
                extent.limitBytes,
                true,
                "encounter slot extent budget exceeded",
            )
        }
        val decodedSlots = ArrayList<DecodedEncounterSlot>(spec.slotCount)
        repeat(spec.slotCount) { slotIndex ->
            meter.work()
            val entry = Math.addExact(slots, Math.multiplyExact(slotIndex, SLOT_BYTES.toInt()))
            val minimum = session.rom.u8(entry)
            val maximum = session.rom.u8(entry + 1)
            val species = session.rom.u16le(entry + 2)
            if (minimum !in 0..100 || maximum !in 0..100 || species !in 0 until layout.speciesCount) {
                return MethodDecode.Malformed(
                    "${spec.label} encounter slot $slotIndex has invalid level/species fields",
                )
            }
            decodedSlots += DecodedEncounterSlot(
                speciesId = species,
                minimumLevel = minimum,
                maximumLevel = maximum,
                weight = spec.weights[slotIndex],
            )
        }
        return MethodDecode.Decoded(
            DecodedEncounterMethod(
                methodId = spec.methodId,
                label = spec.label,
                encounterRate = rate,
                environment = if (spec.hidden) {
                    if (rate == 1) EncounterEnvironment.WATER else EncounterEnvironment.LAND
                } else {
                    null
                },
                windows = setOf(EncounterTimeWindow.ANY),
                slots = decodedSlots,
            ),
        )
    }

    private fun rowRetainedBytes(row: EncounterHeaderOutcome): Long = when (row) {
        is EncounterHeaderOutcome.Decoded -> Math.addExact(
            RETAINED_HEADER_BYTES,
            row.methods.sumOf { method ->
                Math.addExact(
                    RETAINED_METHOD_BYTES,
                    Math.multiplyExact(method.slots.size.toLong(), RETAINED_SLOT_BYTES),
                )
            },
        )
        is EncounterHeaderOutcome.Malformed,
        is EncounterHeaderOutcome.StructuralEmpty,
        -> RETAINED_HEADER_BYTES
    }

    private fun specs(abi: Gen3EncounterAbi): List<MethodSpec> = when (abi) {
        Gen3EncounterAbi.STANDARD_20 -> STANDARD_SPECS
        Gen3EncounterAbi.CLASSIC_24 -> CLASSIC_SPECS
    }

    private fun checkedIndexedOffset(base: Long, index: Int, stride: Int): Int? = try {
        Math.addExact(base, Math.multiplyExact(index.toLong(), stride.toLong())).toIndexedInt()
    } catch (_: ArithmeticException) {
        null
    }

    private fun Long.toIndexedInt(): Int? = takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    private fun validGroupMap(group: Int, map: Int): Boolean = group in 0..63 && map in 0..254

    private sealed interface MethodDecode {
        data class Decoded(val method: DecodedEncounterMethod) : MethodDecode
        data class Malformed(val reason: String) : MethodDecode
    }

    private data class MethodSpec(
        val pointerOffset: Int,
        val methodId: Int,
        val label: String,
        val slotCount: Int,
        val weights: List<Int>,
        val hidden: Boolean = false,
    )

    private companion object {
        const val INFO_BYTES = 8L
        const val SLOT_BYTES = 4L
        const val SENTINEL_BYTES = 2L
        const val RETAINED_HEADER_BYTES = 48L
        const val RETAINED_METHOD_BYTES = 64L
        const val RETAINED_SLOT_BYTES = 32L
        val GRASS_WEIGHTS = listOf(20, 20, 10, 10, 10, 10, 5, 5, 4, 4, 1, 1)
        val FIVE_SLOT_WEIGHTS = listOf(60, 30, 5, 4, 1)
        val FISHING_WEIGHTS = listOf(70, 30, 60, 20, 20, 40, 40, 15, 4, 1)
        val STANDARD_SPECS = listOf(
            MethodSpec(4, 1, "grass", 12, GRASS_WEIGHTS),
            MethodSpec(8, 2, "water", 5, FIVE_SLOT_WEIGHTS),
            MethodSpec(12, 3, "rock smash", 5, FIVE_SLOT_WEIGHTS),
            MethodSpec(16, 4, "fishing", 10, FISHING_WEIGHTS),
        )
        val CLASSIC_SPECS = listOf(
            MethodSpec(4, 1, "grass", 12, GRASS_WEIGHTS),
            MethodSpec(8, 2, "water", 5, FIVE_SLOT_WEIGHTS),
            MethodSpec(12, 3, "rock smash", 5, FIVE_SLOT_WEIGHTS),
            MethodSpec(16, 8, "hidden", 3, listOf(60, 30, 10), hidden = true),
            MethodSpec(20, 4, "fishing", 10, FISHING_WEIGHTS),
        )
    }
}

internal class EncounterDecodeMeter(
    private val maxWork: Long,
    val limits: EncounterDecodeLimits,
) {
    private var work = 0L
    private var retained = 0L
    private var emptyShells = 0L

    init {
        require(maxWork > 0)
    }

    fun work() {
        work = Math.addExact(work, 1L)
        if (work > maxWork) {
            throw EncounterBudgetException(
                EncounterBudgetKind.PROBE_WORK,
                work,
                maxWork,
                false,
                "encounter aggregate probe-work budget exceeded",
            )
        }
    }

    fun retain(amount: Long) {
        retained = Math.addExact(retained, amount)
        if (retained > limits.maxRetainedBytesPerResolution) {
            throw EncounterBudgetException(
                EncounterBudgetKind.RETAINED_OUTPUT,
                retained,
                limits.maxRetainedBytesPerResolution,
                false,
                "encounter aggregate retained-output budget exceeded",
            )
        }
    }

    fun emptyFirstShell() {
        emptyShells = Math.addExact(emptyShells, 1L)
        if (emptyShells > limits.maxEmptyFirstShellWalks.toLong()) {
            throw EncounterBudgetException(
                EncounterBudgetKind.EMPTY_FIRST_SHELLS,
                emptyShells,
                limits.maxEmptyFirstShellWalks.toLong(),
                false,
                "encounter empty-first Classic24 shell-walk budget exceeded",
            )
        }
    }
}

internal class EncounterBudgetException(
    val kind: EncounterBudgetKind,
    val observed: Long,
    val limit: Long,
    val observationComplete: Boolean,
    message: String,
) : IllegalArgumentException(message)
