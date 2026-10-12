package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout

/** Item-backed static eligibility. A shared predicate never supplies a tutor source catalog. */
internal class CompiledItemMoveAcquisitions(
    private val rom: RomImage,
    private val layout: ResolvedRomLayout,
    private val cancellation: ParserCancellationToken,
) {
    data class Result(val data: Data? = null, val reason: String)
    data class Data(val offset: Int, val acquisitions: Map<Int, List<MoveAcquisition>>, val failedSpecies: Set<Int>)
    private data class ItemProjection(val guardField: Int, val guard: Int, val moveField: Int)
    private var work = 0
    private var exhausted = false

    fun resolve(): Result? {
        val wrappers = nominate(intArrayOf(0xB570, 0x1C04, 0x0409, 0x0C0D, 0x1C2E)) {
            word(it + 10) == 0x212D && matches(it + 24, 0x1C20, 0x2141)
        } ?: return Result(reason = "compiled teaching nomination budget exhausted")
        if (wrappers.isEmpty()) return null
        val predicates = linkedMapOf<Int, CompiledU16MoveEligibility>()
        for (entry in wrappers) {
            cancellation.throwIfCancellationRequested()
            if (word(entry + 10) != 0x212D || !matches(entry + 24, 0x1C20, 0x2141)) continue
            if (!wrapper(entry)) return Result(reason = "compiled teaching wrapper is unsupported or malformed")
            val target = bl(entry + 38) ?: continue
            val predicate = CompiledU16MoveEligibility.resolve(rom, target, cancellation)
                ?: return Result(reason = "compiled teaching predicate is unsupported or malformed")
            predicates[entry] = predicate
        }
        if (exhausted) return Result(reason = "compiled teaching code work budget exhausted")
        if (predicates.isEmpty()) return null
        val items = layout.itemNameAuthority as? GbaItemNameAuthority.Available
            ?: return Result(reason = "compiled teaching item domain is unresolved")
        val speciesCount = layout.speciesCount?.takeIf { it in 1..65536 }
            ?: return Result(reason = "compiled teaching species domain is unresolved")
        val moveCount = layout.moveCount?.takeIf { it in 2..65536 }
            ?: return Result(reason = "compiled teaching move domain is unresolved")
        if (items.count !in 1..65536 || items.stride !in 2..256 ||
            items.root < 0 || items.root.toLong() + items.count.toLong() * items.stride > rom.size)
            return Result(reason = "compiled teaching item extent is invalid")
        val callers = nominate(intArrayOf(0xB570, 0x0600, 0x0E06)) { bl(it + 70) in predicates }
            ?: return Result(reason = "compiled item teaching caller budget exhausted")
        val contracts = linkedSetOf<Pair<CompiledU16MoveEligibility, ItemProjection>>()
        for (entry in callers) {
            cancellation.throwIfCancellationRequested()
            val predicate = predicates[bl(entry + 70)] ?: continue
            if (!sourceCaller(entry)) return Result(reason = "compiled item teaching source join is malformed")
            val projection = itemProjection(bl(entry + 10) ?: return Result(reason = "missing item move getter"), items)
                ?: return Result(reason = "compiled item teaching projection is unsupported")
            contracts += predicate to projection
        }
        if (contracts.isEmpty()) return Result(reason = "compiled teaching sources are not item-bound")
        // Independent complete consumers may agree; competing roots or projections never union.
        val (predicate, projection) = contracts.first()
        if (contracts.any { (p, i) -> !predicate.sameContract(p) || i != projection })
            return Result(reason = "conflicting compiled item teaching contracts")
        if (predicate.table.toLong() + speciesCount.toLong() * 4 > rom.size)
            return Result(reason = "compiled teaching pointer extent is invalid")
        // The selector sanitizes out-of-domain inputs to item zero. It must not authorize a raw
        // out-of-domain index through the unsanitized halfword-read arm.
        if (rom.u8(items.root + projection.guardField) == projection.guard)
            return Result(reason = "compiled item zero permits an unbounded raw move read")
        val sources = linkedMapOf<Int, Int>()
        repeat(items.count) { item ->
            cancellation.throwIfCancellationRequested()
            val at = items.root + item * items.stride
            if (rom.u8(at + projection.guardField) == projection.guard) {
                val move = rom.u16le(at + projection.moveField)
                if (move != 0) {
                    if (move !in 1 until moveCount) return Result(reason = "compiled item source move is outside the selected domain")
                    sources[item] = move
                }
            }
        }
        if (sources.isEmpty()) return Result(reason = "no positive compiled item teaching sources")
        val associations = sources.size.toLong() * speciesCount
        if (associations > MAX_RETAINED_LINKS || associations > MAX_WORK.toLong() - work)
            return Result(reason = "compiled teaching association budget exhausted")
        // Reserve the worst-case retained links and membership work before materializing rows.
        // Aliased rows and duplicate item moves must not amplify a small ROM into an unbounded matrix.
        work += associations.toInt()
        val rows = linkedMapOf<Int, List<MoveAcquisition>>()
        val failed = linkedSetOf<Int>()
        val cached = mutableMapOf<Int, Set<Int>?>()
        repeat(speciesCount) { species ->
            cancellation.throwIfCancellationRequested()
            val allowed: (Int) -> Boolean = when (species) {
                predicate.rejectedSpecies -> { _ -> false }
                predicate.overriddenSpecies -> predicate::overrideAllows
                else -> {
                    val cell = predicate.table + species * 4
                    val pointer = rom.gbaPointer(cell)
                    val values = if (pointer == null || pointer and 1 != 0) null else {
                        if (pointer !in cached) cached[pointer] = readRow(pointer, moveCount)
                        cached[pointer]
                    }
                    if (values == null) {
                        failed += species
                        return@repeat
                    }
                    values::contains
                }
            }
            rows[species] = sources.mapNotNull { (item, move) ->
                // Native item ID, not a guessed TM ordinal or a guessed tutor index.
                MoveAcquisition(move, MoveAcquisitionMethod.MACHINE, item).takeIf { allowed(move) }
            }
        }
        if (exhausted) return Result(reason = "compiled teaching row work budget exhausted")
        return Result(Data(predicate.table, rows, failed), "compiled item-backed static move eligibility; source IDs are native item IDs")
    }

    private fun readRow(pointer: Int, moveCount: Int): Set<Int>? {
        val result = linkedSetOf<Int>()
        // The native counter truncates to u8: index255 may contain the terminator, never a
        // 256th move. A later terminator would turn a failed search into a wrapping loop.
        for (index in 0..255) {
            cancellation.throwIfCancellationRequested()
            val at = pointer.toLong() + index * 2L
            if (++work > MAX_WORK) { exhausted = true; return null }
            if (at > rom.size.toLong() - 2) return null
            val move = rom.u16le(at.toInt())
            if (move == 0xFFFF) return result
            if (index == 255 || move !in 1 until moveCount) return null
            result += move
        }
        return null
    }

    private fun nominate(ops: IntArray, qualifies: (Int) -> Boolean = { true }): List<Int>? {
        val pattern = ByteArray(ops.size * 2) { index -> (ops[index / 2] ushr ((index and 1) * 8)).toByte() }
        val result = mutableListOf<Int>()
        val complete = rom.visitMatches(pattern, onCheck = cancellation::throwIfCancellationRequested) { at ->
            if (at and 1 == 0 && qualifies(at)) result += at
            !exhausted && result.size <= MAX_CANDIDATES
        }
        return if (complete) result else null
    }

    private fun wrapper(at: Int): Boolean {
        if (!matches(at, 0xB570, 0x1C04, 0x0409, 0x0C0D, 0x1C2E, 0x212D) ||
            bl(at + 12) == null || bl(at + 12) != bl(at + 28) ||
            !matches(at + 16, 0x2800, 0xD001, 0x2003, 0xE018, 0x1C20, 0x2141) ||
            !matches(at + 32, 0x0400, 0x0C00, 0x1C29) || bl(at + 38) == null ||
            !matches(at + 42, 0x0600, 0x2800, 0xD101, 0x2001, 0xE00A, 0x1C20, 0x1C31) ||
            bl(at + 56) == null ||
            !matches(at + 60, 0x0600, 0x0E00, 0x2801, 0xD001, 0x2000, 0xE000, 0x2002, 0xBC70, 0xBC02, 0x4708)) return false
        return listOf(12, 28, 38, 56).all { bl(at + it) !in at until at + 80 }
    }

    private fun sourceCaller(at: Int): Boolean {
        val names = layout.tables.moveNames ?: return false
        val stride = names.stride ?: names.recordSize
        if (!matches(at, 0xB570, 0x0600, 0x0E06) || !ramLiteral(at + 6, 0, 2) || word(at + 8) != 0x8800 ||
            !matches(at + 14, 0x0400, 0x0C05) || !ramLiteral(at + 18, 4, 4) ||
            !matches(at + 20, 0x2000, 0x81E5, 0x8220, 0x2005) ||
            !matches(at + 32, 0x2109, 0x5661, 0x2064, 0x4341) || !ramLiteral(at + 40, 0, 4) ||
            word(at + 42) != 0x180C || !ramLiteral(at + 44, 1, 1) || word(at + 46) != 0x1C20 ||
            !ramLiteral(at + 52, 0, 1) || word(at + 54) != 0x2100 or stride ||
            word(at + 56) != 0x4369 || literal(at + 58, 2) != 0x08000000L + names.offset || word(at + 60) != 0x1889 ||
            !matches(at + 66, 0x1C20, 0x1C29) || !matches(at + 74, 0x0600, 0x0E00, 0x2801)) return false
        // Only the local static source/ID join is used. Opaque UI/helper descendants grant no
        // gameplay-availability or setter effects; the caller follows the ordinary Thumb ABI.
        return listOf(10, 28, 48, 62, 70).all { bl(at + it)?.let { target -> target !in at until at + 80 } == true }
    }

    private fun itemProjection(at: Int, items: GbaItemNameAuthority.Available): ItemProjection? {
        if (!matches(at, 0xB510, 0x0400, 0x0C04, 0x1C20) ||
            !matches(at + 12, 0x0600, 0x0E00) || word(at + 16) and 0xFF00 != 0x2800 ||
            !matches(at + 18, 0xD109) || literal(at + 20, 1) != 0x08000000L + items.root ||
            literalSlot(at + 20) != at + 36 || word(at + 24) != 0x1900 || word(at + 28) != 0x1840 ||
            !matches(at + 32, 0xE003, 0) || !matches(at + 40, 0x2000, 0xBC10, 0xBC02, 0x4708)) return null
        val first = shift(word(at + 22), 4, 0) ?: return null
        val last = shift(word(at + 26), 0, 0) ?: return null
        val size = (((1L shl first) + 1) shl last).takeIf { it in 2..256 }?.toInt() ?: return null
        val load = word(at + 30)
        if (size != items.stride || load < 0 || load and 0xF83F != 0x8800) return null
        val moveField = (load ushr 6 and 31) * 2
        if (moveField > size - 2) return null
        val selector = bl(at + 8) ?: return null
        if (selector in at until at + 48 || !matches(selector, 0xB510, 0x0400, 0x0C00) ||
            literal(selector + 6, 4) != 0x08000000L + items.root || literalSlot(selector + 6) != selector + 32 ||
            !matches(selector + 12, 0x0400, 0x0C00) ||
            shift(word(selector + 16), 0, 1) != first || word(selector + 18) != 0x1809 ||
            shift(word(selector + 20), 1, 1) != last || word(selector + 22) != 0x1909 ||
            !matches(selector + 26, 0xBC10, 0xBC02, 0x4708)) return null
        val byte = word(selector + 24)
        if (byte < 0 || byte and 0xF83F != 0x7808) return null
        val guardField = byte ushr 6 and 31
        if (guardField >= size) return null
        val sanitizer = bl(selector + 8) ?: return null
        if (sanitizer in selector until selector + 36 ||
            !matches(sanitizer, 0xB500, 0x0400, 0x0C01) ||
            literalSlot(sanitizer + 6) != sanitizer + 16 ||
            literal(sanitizer + 6, 0) != items.count.toLong() - 1 ||
            !matches(sanitizer + 8, 0x4281, 0xD803, 0x1C08, 0xE002) ||
            !matches(sanitizer + 20, 0x2000, 0xBC02, 0x4708)) return null
        return ItemProjection(guardField, word(at + 16) and 255, moveField)
    }

    private fun word(at: Int): Int {
        cancellation.throwIfCancellationRequested()
        if (++work > MAX_WORK) { exhausted = true; return -1 }
        return if (at >= 0 && at and 1 == 0 && at <= rom.size - 2) rom.u16le(at) else -1
    }
    private fun matches(at: Int, vararg ops: Int) = ops.indices.all { word(at + it * 2) == ops[it] }
    private fun shift(op: Int, source: Int, target: Int): Int? =
        if (op >= 0 && op and 0xF83F == (source shl 3 or target)) op ushr 6 and 31 else null
    private fun literalSlot(at: Int): Int? {
        val op = word(at)
        if (op < 0 || op and 0xF800 != 0x4800) return null
        return (((at + 4) and -4).toLong() + (op and 255) * 4L).takeIf { it in 0..rom.size.toLong() - 4 }?.toInt()
    }
    private fun literal(at: Int, register: Int): Long? =
        if (word(at) and 0xFF00 == 0x4800 or (register shl 8)) literalSlot(at)?.let(rom::u32le) else null
    private fun ramLiteral(at: Int, register: Int, alignment: Int): Boolean = literal(at, register)?.let {
        it % alignment == 0L && (it in 0x02000000L..0x0203FFFFL || it in 0x03000000L..0x03007FFFL)
    } == true
    private fun bl(at: Int): Int? {
        val high = word(at); val low = word(at + 2)
        if (high < 0 || low < 0 || high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
        val delta = (((high and 2047) shl 12) or ((low and 2047) shl 1)) shl 9 shr 9
        return (at.toLong() + 4 + delta).takeIf { it in 0..rom.size.toLong() - 2 && it and 1L == 0L }?.toInt()
    }
    private companion object {
        const val MAX_CANDIDATES = 64
        const val MAX_WORK = 1_000_000
        const val MAX_RETAINED_LINKS = 500_000
    }
}
