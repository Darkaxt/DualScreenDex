package com.enrpau.dualscreendex.parser.dataset.acquisition

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage

class MoveAcquisitionCodec {
    fun decode(
        session: RomAnalysisSession,
        layout: AcquisitionTableLayout,
        domain: AcquisitionSemanticDomain,
    ): AcquisitionTableOutcome = decode(
        session,
        layout,
        domain,
        AcquisitionResolutionLedger(session.limits),
    )

    internal fun decode(
        session: RomAnalysisSession,
        layout: AcquisitionTableLayout,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
    ): AcquisitionTableOutcome = try {
        validateCardinality(layout, domain)
        validateMethod(layout)
        val retention = ledger.retentionReservation()
        // Prove row metadata fits before any cardinality-sized allocation.
        retention.reserve(layout.speciesCount)
        val rows = when (val abi = layout.abi) {
            is AcquisitionAbi.Gen3EggSentinelU16 -> {
                requireMethod(layout, AcquisitionMethod.EGG)
                decodeGen3Egg(session.rom, layout, abi, domain, ledger, retention)
            }
            is AcquisitionAbi.GbBankedEggPointersU8 -> {
                requireMethod(layout, AcquisitionMethod.EGG)
                decodeGbEgg(session.rom, layout, abi, domain, ledger, retention)
            }
            is AcquisitionAbi.EmbeddedU8MoveListBitfield -> {
                rejectEggBitfield(layout)
                decodeEmbedded(session.rom, layout, abi, domain, ledger, retention)
            }
            is AcquisitionAbi.Gen3U16MoveListBitfield -> {
                rejectEggBitfield(layout)
                decodeGen3Bitfield(session.rom, layout, abi, domain, ledger, retention)
            }
            is AcquisitionAbi.GbaRecordPointerMoveListsU16 -> {
                rejectTutorRecordLists(layout)
                decodeRecordPointerLists(session.rom, layout, abi, domain, ledger, retention)
            }
            is AcquisitionAbi.GbaPointerIndexedTutorU8 -> {
                requireMethod(layout, AcquisitionMethod.TUTOR)
                decodeIndexedTutor(session.rom, layout, abi, domain, ledger, retention)
            }
        }
        retention.commit()
        val resolved = ResolvedAcquisitionLayout(layout, rows)
        AcquisitionTableOutcome.Decoded(layout, resolved)
    } catch (failure: DecodeAbort) {
        when (failure) {
            is DecodeAbort.Rejected -> AcquisitionTableOutcome.Rejected(layout, failure.reason)
            is DecodeAbort.ExtentBudget -> AcquisitionTableOutcome.ExtentBudgetExceeded(
                layout,
                failure.observed,
                failure.limit,
                failure.reason,
            )
            is DecodeAbort.WorkBudget -> AcquisitionTableOutcome.WorkBudgetExceeded(
                layout,
                failure.observed,
                failure.limit,
                failure.reason,
            )
        }
    }

    private fun validateCardinality(
        layout: AcquisitionTableLayout,
        domain: AcquisitionSemanticDomain,
    ) {
        if (layout.speciesCount !in 1..Int.MAX_VALUE.toLong()) {
            reject("acquisition species cardinality cannot be represented by indexed row outcomes")
        }
        val first = layout.abi.speciesIdBase.toLong()
        val endExclusive = checkedAdd(first, layout.speciesCount, "acquisition species ID range overflows Long")
        if (first !in 0..Int.MAX_VALUE.toLong() || endExclusive > Int.MAX_VALUE.toLong() + 1L) {
            reject("acquisition species ID range cannot be represented by Int IDs")
        }
        if (domain.speciesIds.any { it.toLong() !in first until endExclusive }) {
            reject("acquisition layout does not cover the independently established species domain")
        }
    }

    private fun decodeGen3Egg(
        rom: RomImage,
        layout: AcquisitionTableLayout,
        abi: AcquisitionAbi.Gen3EggSentinelU16,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
    ): List<AcquisitionRowOutcome> {
        val states = linkedMapOf<Int, EggRowState>()
        if (layout.speciesCount > 45_534L) {
            reject("Gen III egg species markers cannot encode the declared species cardinality")
        }
        val seenSpecies = mutableSetOf<Int>()
        var currentSpecies: Int? = null
        var currentAccepted = false
        var cursor = abi.offset
        var records = 0
        var terminated = false
        while (records < abi.maxRecords) {
            ledger.extent(rom, cursor, 1, 2)
            ledger.work(1)
            requireReadable(rom, cursor, 2)
            val value = rom.u16le(cursor.toInt())
            cursor = checkedAdd(cursor, 2L, "Gen III egg cursor overflows Long")
            records++
            when {
                value == 0xFFFF -> {
                    terminated = true
                    break
                }
                value in 20_001..(20_000 + layout.speciesCount.toInt()) -> {
                    val species = value - 20_000
                    currentSpecies = species
                    currentAccepted = seenSpecies.add(species)
                    if (currentAccepted) states[species] = EggRowState()
                }
                currentSpecies == null -> reject("Gen III egg move appears before the first species marker")
                !domain.containsMove(value) -> states.getValue(requireNotNull(currentSpecies)).malformed +=
                    "egg move $value is outside the independently established move domain"
                currentAccepted -> {
                    val state = states.getValue(requireNotNull(currentSpecies))
                    if (state.links.any { it.moveId == value }) {
                        state.malformed += "egg move $value is duplicated within one species group"
                    } else {
                        retention.reserve(1)
                        state.links += AcquisitionLink(value, AcquisitionMethod.EGG)
                    }
                }
            }
        }
        if (!terminated) reject("Gen III egg table has no terminator within its declared record cap")
        return speciesRows(layout, states) { species, state ->
            when {
                state == null || state.links.isEmpty() && state.malformed.isEmpty() ->
                    AcquisitionRowOutcome.StructuralEmpty(species)
                state.malformed.isNotEmpty() -> AcquisitionRowOutcome.Malformed(species, state.malformed)
                else -> AcquisitionRowOutcome.Decoded(species, state.links)
            }
        }
    }

    private fun decodeGbEgg(
        rom: RomImage,
        layout: AcquisitionTableLayout,
        abi: AcquisitionAbi.GbBankedEggPointersU8,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
    ): List<AcquisitionRowOutcome> {
        val count = layout.speciesCount.toInt()
        val bankStart = checkedMultiply(abi.bank.toLong(), 0x4000L, "GB egg bank start overflows Long")
        val bankEnd = minOf(rom.size.toLong(), checkedAdd(bankStart, 0x4000L, "GB egg bank end overflows Long"))
        val pointerTableBytes = checkedMultiply(layout.speciesCount, 2L, "GB egg pointer table length overflows Long")
        val pointerTableEnd = checkedAdd(
            abi.pointerTableOffset,
            pointerTableBytes,
            "GB egg pointer table end overflows Long",
        )
        if (abi.pointerTableOffset < bankStart || pointerTableEnd > bankEnd) {
            reject("GB egg pointer table is not wholly inside its declared bank")
        }
        ledger.extent(rom, abi.pointerTableOffset, layout.speciesCount, 2)
        return List(count) { rowIndex ->
            ledger.work(1)
            val species = abi.speciesIdBase + rowIndex
            val pointerOffset = checkedAdd(
                abi.pointerTableOffset,
                checkedMultiply(rowIndex.toLong(), 2L, "GB egg pointer offset overflows Long"),
                "GB egg pointer offset overflows Long",
            ).toInt()
            val pointer = rom.u16le(pointerOffset)
            val target = rom.gbBankAddress(abi.bank, pointer)
            if (target == null || target.toLong() !in bankStart until bankEnd) {
                AcquisitionRowOutcome.Malformed(species, listOf("GB egg pointer is outside its declared bank"))
            } else {
                decodeU8TerminatedMoves(
                    rom,
                    target.toLong(),
                    abi.maxMovesPerSpecies,
                    species,
                    layout.method,
                    domain,
                    ledger,
                    retention,
                    endExclusive = bankEnd,
                )
            }
        }
    }

    private fun decodeEmbedded(
        rom: RomImage,
        layout: AcquisitionTableLayout,
        abi: AcquisitionAbi.EmbeddedU8MoveListBitfield,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
    ): List<AcquisitionRowOutcome> {
        ledger.extent(rom, abi.moveListOffset, abi.itemCount.toLong(), 1)
        ledger.extent(rom, abi.statsOffset, layout.speciesCount, abi.statsRecordSize.toLong())
        ledger.work(abi.itemCount.toLong())
        val moves = List(abi.itemCount) { index -> rom.u8(abi.moveListOffset.toInt() + index) }
        validateMoveList(moves, domain)
        return List(layout.speciesCount.toInt()) { rowIndex ->
            ledger.work(abi.flagBytes.toLong())
            val species = abi.speciesIdBase + rowIndex
            val row = checkedAdd(
                abi.statsOffset,
                checkedMultiply(rowIndex.toLong(), abi.statsRecordSize.toLong(), "stats row offset overflows Long"),
                "stats row offset overflows Long",
            )
            decodeBitfieldRow(
                rom,
                checkedAdd(row, abi.flagOffset.toLong(), "embedded flag offset overflows Long"),
                abi.flagBytes,
                abi.firstBit,
                moves,
                species,
                layout.method,
                retention,
                validateOutsideBits = false,
            )
        }
    }

    private fun decodeGen3Bitfield(
        rom: RomImage,
        layout: AcquisitionTableLayout,
        abi: AcquisitionAbi.Gen3U16MoveListBitfield,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
    ): List<AcquisitionRowOutcome> {
        ledger.extent(rom, abi.moveListOffset, abi.itemCount.toLong(), 2)
        ledger.extent(rom, abi.compatibilityOffset, layout.speciesCount, abi.rowBytes.toLong())
        ledger.work(abi.itemCount.toLong())
        val moves = List(abi.itemCount) { index -> rom.u16le(abi.moveListOffset.toInt() + index * 2) }
        validateMoveList(moves, domain)
        return List(layout.speciesCount.toInt()) { rowIndex ->
            ledger.work(abi.rowBytes.toLong())
            val species = abi.speciesIdBase + rowIndex
            val row = checkedAdd(
                abi.compatibilityOffset,
                checkedMultiply(rowIndex.toLong(), abi.rowBytes.toLong(), "compatibility row offset overflows Long"),
                "compatibility row offset overflows Long",
            )
            decodeBitfieldRow(rom, row, abi.rowBytes, 0, moves, species, layout.method, retention)
        }
    }

    private fun decodeIndexedTutor(
        rom: RomImage,
        layout: AcquisitionTableLayout,
        abi: AcquisitionAbi.GbaPointerIndexedTutorU8,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
    ): List<AcquisitionRowOutcome> {
        ledger.extent(rom, abi.pointerTableOffset, layout.speciesCount, 4)
        ledger.extent(rom, abi.moveListOffset, abi.tutorCount.toLong(), 2)
        ledger.work(abi.tutorCount.toLong())
        val moves = List(abi.tutorCount) { index -> rom.u16le(abi.moveListOffset.toInt() + index * 2) }
        validateMoveList(moves, domain)
        return List(layout.speciesCount.toInt()) { rowIndex ->
            ledger.work(1)
            val species = abi.speciesIdBase + rowIndex
            val pointerOffset = abi.pointerTableOffset.toInt() + rowIndex * 4
            if (rom.u32le(pointerOffset) == 0L) {
                AcquisitionRowOutcome.StructuralEmpty(species)
            } else {
                val target = rom.gbaPointer(pointerOffset)
                if (target == null) {
                    AcquisitionRowOutcome.Malformed(species, listOf("tutor index pointer is not a ROM pointer"))
                } else {
                    decodeTutorIndexes(rom, target.toLong(), abi, moves, species, ledger, retention)
                }
            }
        }
    }

    private fun decodeRecordPointerLists(
        rom: RomImage,
        layout: AcquisitionTableLayout,
        abi: AcquisitionAbi.GbaRecordPointerMoveListsU16,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
    ): List<AcquisitionRowOutcome> {
        ledger.extent(rom, abi.recordTableOffset, layout.speciesCount, abi.recordStride.toLong())
        return List(layout.speciesCount.toInt()) { rowIndex ->
            ledger.work(1)
            val species = abi.speciesIdBase + rowIndex
            val record = checkedAdd(
                abi.recordTableOffset,
                checkedMultiply(rowIndex.toLong(), abi.recordStride.toLong(), "species record offset overflows Long"),
                "species record offset overflows Long",
            )
            val pointerOffset = checkedAdd(
                record,
                abi.pointerFieldOffset.toLong(),
                "species acquisition pointer offset overflows Long",
            ).toInt()
            if (rom.u32le(pointerOffset) == 0L) {
                AcquisitionRowOutcome.StructuralEmpty(species)
            } else {
                val target = rom.gbaPointer(pointerOffset)
                if (target == null) {
                    AcquisitionRowOutcome.Malformed(species, listOf("species acquisition field is not a ROM pointer"))
                } else {
                    decodeU16TerminatedMoves(
                        rom,
                        target.toLong(),
                        abi.maxMovesPerSpecies,
                        species,
                        layout.method,
                        domain,
                        ledger,
                        retention,
                        provenance = if (layout.method == AcquisitionMethod.MACHINE) {
                            AcquisitionProvenance.COMBINED_MACHINE_TUTOR
                        } else {
                            AcquisitionProvenance.EGG
                        },
                    )
                }
            }
        }
    }

    private fun decodeTutorIndexes(
        rom: RomImage,
        offset: Long,
        abi: AcquisitionAbi.GbaPointerIndexedTutorU8,
        moves: List<Int>,
        species: Int,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
    ): AcquisitionRowOutcome {
        val indexes = mutableListOf<Int>()
        var cursor = offset
        var terminated = false
        while (indexes.size <= abi.maxIndexesPerSpecies) {
            ledger.extent(rom, cursor, 1, 1)
            ledger.work(1)
            requireReadable(rom, cursor, 1)
            val value = rom.u8(cursor.toInt())
            cursor = checkedAdd(cursor, 1L, "tutor index cursor overflows Long")
            if (value == 0xFF) {
                terminated = true
                break
            }
            indexes += value
        }
        if (!terminated) {
            return AcquisitionRowOutcome.Malformed(species, listOf("tutor index row exceeds its declared cap"))
        }
        val reasons = buildList {
            indexes.filter { it !in moves.indices }.distinct().forEach { add("tutor index $it is outside the move list") }
            indexes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add("tutor index $it is duplicated") }
        }
        if (reasons.isNotEmpty()) return AcquisitionRowOutcome.Malformed(species, reasons)
        if (indexes.isEmpty()) return AcquisitionRowOutcome.StructuralEmpty(species)
        retention.reserve(indexes.size.toLong())
        return AcquisitionRowOutcome.Decoded(
            species,
            indexes.map { index -> AcquisitionLink(moves[index], AcquisitionMethod.TUTOR, index + 1) },
        )
    }

    private fun decodeU8TerminatedMoves(
        rom: RomImage,
        offset: Long,
        maxMoves: Int,
        species: Int,
        method: AcquisitionMethod,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
        endExclusive: Long = rom.size.toLong(),
    ): AcquisitionRowOutcome {
        val moves = mutableListOf<Int>()
        var cursor = offset
        var terminated = false
        while (moves.size <= maxMoves) {
            if (cursor >= endExclusive) break
            ledger.extent(rom, cursor, 1, 1)
            ledger.work(1)
            requireReadable(rom, cursor, 1)
            val value = rom.u8(cursor.toInt())
            cursor = checkedAdd(cursor, 1L, "terminated acquisition cursor overflows Long")
            if (value == 0xFF) {
                terminated = true
                break
            }
            moves += value
        }
        if (!terminated) {
            return AcquisitionRowOutcome.Malformed(species, listOf("acquisition row exceeds its declared move cap"))
        }
        val reasons = buildList {
            moves.filterNot(domain::containsMove).distinct().forEach {
                add("move $it is outside the independently established move domain")
            }
            moves.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
                add("move $it is duplicated within one acquisition row")
            }
        }
        if (reasons.isNotEmpty()) return AcquisitionRowOutcome.Malformed(species, reasons)
        if (moves.isEmpty()) return AcquisitionRowOutcome.StructuralEmpty(species)
        retention.reserve(moves.size.toLong())
        return AcquisitionRowOutcome.Decoded(species, moves.map { AcquisitionLink(it, method) })
    }

    private fun decodeU16TerminatedMoves(
        rom: RomImage,
        offset: Long,
        maxMoves: Int,
        species: Int,
        method: AcquisitionMethod,
        domain: AcquisitionSemanticDomain,
        ledger: AcquisitionResolutionLedger,
        retention: AcquisitionRetentionReservation,
        provenance: AcquisitionProvenance,
    ): AcquisitionRowOutcome {
        val moves = mutableListOf<Int>()
        var cursor = offset
        var terminated = false
        while (moves.size <= maxMoves) {
            ledger.extent(rom, cursor, 1, 2)
            ledger.work(1)
            requireReadable(rom, cursor, 2)
            val value = rom.u16le(cursor.toInt())
            cursor = checkedAdd(cursor, 2L, "u16 acquisition cursor overflows Long")
            if (value == 0xFFFF) {
                terminated = true
                break
            }
            moves += value
        }
        if (!terminated) {
            return AcquisitionRowOutcome.Malformed(species, listOf("u16 acquisition row exceeds its declared move cap"))
        }
        val reasons = buildList {
            moves.filterNot(domain::containsMove).distinct().forEach {
                add("move $it is outside the independently established move domain")
            }
            moves.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
                add("move $it is duplicated within one acquisition row")
            }
        }
        if (reasons.isNotEmpty()) return AcquisitionRowOutcome.Malformed(species, reasons)
        if (moves.isEmpty()) return AcquisitionRowOutcome.StructuralEmpty(species)
        retention.reserve(moves.size.toLong())
        return AcquisitionRowOutcome.Decoded(
            species,
            moves.map { AcquisitionLink(it, method, provenance = provenance) },
        )
    }

    private fun decodeBitfieldRow(
        rom: RomImage,
        rowOffset: Long,
        rowBytes: Int,
        firstBit: Int,
        moves: List<Int>,
        species: Int,
        method: AcquisitionMethod,
        retention: AcquisitionRetentionReservation,
        validateOutsideBits: Boolean = true,
    ): AcquisitionRowOutcome {
        val lastUsedBit = firstBit + moves.size
        var staleBitCount = 0
        val staleBitPreview = ArrayList<Int>(STALE_BIT_PREVIEW_LIMIT)
        if (validateOutsideBits) {
            for (bit in 0 until rowBytes * 8) {
                if (bit in firstBit until lastUsedBit) continue
                if (bitSet(rom, rowOffset, bit)) {
                    staleBitCount++
                    if (staleBitPreview.size < STALE_BIT_PREVIEW_LIMIT) staleBitPreview += bit
                }
            }
        }
        if (staleBitCount > 0) {
            return AcquisitionRowOutcome.Malformed(
                species,
                listOf(
                    "compatibility padding/reserved bits are non-zero: observed $staleBitCount; " +
                        "first=$staleBitPreview",
                ),
            )
        }
        var enabledCount = 0L
        moves.indices.forEach { index ->
            if (bitSet(rom, rowOffset, firstBit + index)) enabledCount++
        }
        retention.reserve(enabledCount)
        val links = moves.mapIndexedNotNull { index, move ->
            AcquisitionLink(move, method, index + 1).takeIf {
                bitSet(rom, rowOffset, firstBit + index)
            }
        }
        return if (links.isEmpty()) {
            AcquisitionRowOutcome.StructuralEmpty(species)
        } else {
            AcquisitionRowOutcome.Decoded(species, links)
        }
    }

    private fun bitSet(rom: RomImage, rowOffset: Long, bit: Int): Boolean =
        rom.u8(rowOffset.toInt() + bit / 8) and (1 shl (bit % 8)) != 0

    private fun validateMoveList(moves: List<Int>, domain: AcquisitionSemanticDomain) {
        if (moves.any { !domain.containsMove(it) }) {
            reject("acquisition move list contains IDs outside the independently established move domain")
        }
        if (moves.distinct().size != moves.size) reject("acquisition move list contains duplicate move IDs")
    }

    private fun requireMethod(layout: AcquisitionTableLayout, expected: AcquisitionMethod) {
        if (layout.method != expected) reject("${layout.abi.identity} is only valid for $expected acquisition")
    }

    private fun validateMethod(layout: AcquisitionTableLayout) = when (layout.abi) {
        is AcquisitionAbi.Gen3EggSentinelU16,
        is AcquisitionAbi.GbBankedEggPointersU8,
        -> requireMethod(layout, AcquisitionMethod.EGG)
        is AcquisitionAbi.GbaPointerIndexedTutorU8 -> requireMethod(layout, AcquisitionMethod.TUTOR)
        is AcquisitionAbi.GbaRecordPointerMoveListsU16 -> rejectTutorRecordLists(layout)
        is AcquisitionAbi.EmbeddedU8MoveListBitfield,
        is AcquisitionAbi.Gen3U16MoveListBitfield,
        -> rejectEggBitfield(layout)
    }

    private fun rejectEggBitfield(layout: AcquisitionTableLayout) {
        if (layout.method == AcquisitionMethod.EGG) reject("egg acquisition does not use a compatibility bitfield ABI")
    }

    private fun rejectTutorRecordLists(layout: AcquisitionTableLayout) {
        if (layout.method == AcquisitionMethod.TUTOR) {
            reject("expansion record-pointer teachable lists combine machine and tutor provenance")
        }
    }

    private fun <T> speciesRows(
        layout: AcquisitionTableLayout,
        states: Map<Int, T>,
        transform: (Int, T?) -> AcquisitionRowOutcome,
    ): List<AcquisitionRowOutcome> = List(layout.speciesCount.toInt()) { rowIndex ->
        val species = layout.abi.speciesIdBase + rowIndex
        transform(species, states[species])
    }

    private fun requireReadable(rom: RomImage, offset: Long, length: Long) {
        if (offset < 0 || length < 0 || offset > Int.MAX_VALUE.toLong()) {
            reject("acquisition read span cannot be represented by indexed ROM offsets")
        }
        val end = checkedAdd(offset, length, "acquisition read end overflows Long")
        if (end > rom.size.toLong()) reject("acquisition read span exceeds the ROM")
    }

    private fun checkedAdd(first: Long, second: Long, message: String): Long = try {
        Math.addExact(first, second)
    } catch (_: ArithmeticException) {
        reject(message)
    }

    private fun checkedMultiply(first: Long, second: Long, message: String): Long = try {
        Math.multiplyExact(first, second)
    } catch (_: ArithmeticException) {
        reject(message)
    }

    private fun reject(message: String): Nothing = throw DecodeAbort.Rejected(message)

    private class EggRowState(
        val links: MutableList<AcquisitionLink> = mutableListOf(),
        val malformed: MutableList<String> = mutableListOf(),
    )

    private companion object {
        const val STALE_BIT_PREVIEW_LIMIT = 16
    }
}

internal class AcquisitionResolutionLedger(private val limits: ResolutionLimits) {
    private val extents = mutableSetOf<ExtentIdentity>()
    private var extentBytes = 0L
    private var work = 0L
    private var retained = 0L

    fun extent(rom: RomImage, offset: Long, count: Long, recordSize: Long) {
        when (val check = limits.checkTableExtent(offset, count, recordSize, rom.size.toLong())) {
            is ExtentCheck.Invalid -> throw DecodeAbort.Rejected(check.reason)
            is ExtentCheck.BudgetExceeded -> throw DecodeAbort.ExtentBudget(
                check.observedBytes,
                check.limitBytes,
                "acquisition table extent budget exceeded (${check.observedBytes} > ${check.limitBytes})",
            )
            is ExtentCheck.Valid -> {
                val identity = ExtentIdentity(check.extent.offset, check.extent.length)
                if (!extents.add(identity)) return
                val observed = try {
                    Math.addExact(extentBytes, check.extent.length.toLong())
                } catch (_: ArithmeticException) {
                    Long.MAX_VALUE
                }
                if (observed > limits.maxDatasetExtentBytes) {
                    throw DecodeAbort.ExtentBudget(
                        observed,
                        limits.maxDatasetExtentBytes,
                        "aggregate acquisition extent budget exceeded " +
                            "($observed > ${limits.maxDatasetExtentBytes})",
                    )
                }
                extentBytes = observed
            }
        }
    }

    fun work(amount: Long) {
        work = charge(work, amount, "acquisition probe-work")
    }

    fun retentionReservation(): AcquisitionRetentionReservation = AcquisitionRetentionReservation(this)

    internal fun reserveRetained(currentReservation: Long, amount: Long): Long {
        if (amount < 0) throw DecodeAbort.Rejected("acquisition retained-record charge must not be negative")
        val reserved = safeAdd(currentReservation, amount)
        charge(retained, reserved, "acquisition retained-record")
        return reserved
    }

    internal fun commitRetained(reservation: Long) {
        retained = charge(retained, reservation, "acquisition retained-record")
    }

    private fun charge(current: Long, amount: Long, label: String): Long {
        if (amount < 0) throw DecodeAbort.Rejected("$label charge must not be negative")
        val observed = try {
            Math.addExact(current, amount)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        val limit = limits.maxProbeWorkPerDataset.toLong()
        if (observed > limit) {
            throw DecodeAbort.WorkBudget(observed, limit, "$label budget exceeded ($observed > $limit)")
        }
        return observed
    }

    private fun safeAdd(first: Long, second: Long): Long = try {
        Math.addExact(first, second)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }

    private data class ExtentIdentity(val offset: Int, val length: Int)
}

internal class AcquisitionRetentionReservation(
    private val ledger: AcquisitionResolutionLedger,
) {
    private var retained = 0L
    private var committed = false

    fun reserve(amount: Long) {
        check(!committed) { "acquisition retention reservation is already committed" }
        retained = ledger.reserveRetained(retained, amount)
    }

    fun commit() {
        check(!committed) { "acquisition retention reservation is already committed" }
        ledger.commitRetained(retained)
        committed = true
    }
}

private sealed class DecodeAbort(val reason: String) : RuntimeException(reason) {
    class Rejected(message: String) : DecodeAbort(message)
    class ExtentBudget(val observed: Long, val limit: Long, message: String) : DecodeAbort(message)
    class WorkBudget(val observed: Long, val limit: Long, message: String) : DecodeAbort(message)
}
