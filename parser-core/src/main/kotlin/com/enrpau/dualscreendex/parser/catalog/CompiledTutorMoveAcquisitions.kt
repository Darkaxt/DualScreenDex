package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout

/** Incrementally bounded script sources. Shared eligibility never authorizes tutor provenance. */
internal class CompiledTutorMoveAcquisitions(
    private val rom: RomImage,
    private val layout: ResolvedRomLayout,
    private val shared: CompiledItemMoveAcquisitions.Data,
    private val cancellation: ParserCancellationToken,
) {
    data class Data(val offset: Int, val acquisitions: Map<Int, List<MoveAcquisition>>, val failedSpecies: Set<Int>, val sourceCount: Int)
    private data class Workflow(val party: Long, val selected: Long)
    private data class Commands(val variables: Int, val specials: Int)
    private data class Menu(val initializer: Int, val callback: Int, val action: Int)
    private var work = 0
    private var exhausted = false

    fun resolve(): Data? {
        val workflows = linkedMapOf<Int, Workflow>()
        val candidates = nominate(0xB5F0, 0x0600, 0x0E06) { bl(it + 76) in shared.wrapperEntries } ?: return null
        for (at in candidates) workflows[at] = workflow(at) ?: return null
        if (workflows.isEmpty() || workflows.values.distinct().size != 1) return null
        val flow = workflows.values.first()
        val commands = commands() ?: return null
        val menus = linkedMapOf<Int, Menu>()
        val linked = nominate(0xB500, 0xB083) { at ->
            val menu = menu(at)
            if (menu != null && linkedMenu(menu, flow, workflows.keys)) {
                menus[at] = menu
                true
            } else false
        } ?: return null
        if (linked.isEmpty()) return null
        if (menus.isEmpty()) return null
        val sources = linkedMapOf<Int, Int>()
        // SETVAR u16,u16; CALL pointer. The called helper delivers the source before any
        // opaque script operation. No nearby SPECIAL search or retail tutor ordinal.
        rom.visitMatches(byteArrayOf(0x16), onCheck = cancellation::throwIfCancellationRequested) { at ->
            if (!tick() || at > rom.size - 10) return@visitMatches !exhausted
            val variable = rom.u16le(at + 1)
            if (variable < 0x8000 || value(commands.variables.toLong() + (variable - 0x8000) * 4L) != flow.selected || rom.u8(at + 5) != 4)
                return@visitMatches !exhausted
            val helper = pointer(at + 6, thumb = false) ?: return@visitMatches true
            if (helper > rom.size - 7 || rom.u8(helper) != 0x25 ||
                !byteMatches(helper + 3, 0x27, 0x6A, 0x5A, 3)) return@visitMatches true
            val special = rom.u16le(helper + 1)
            val chooser = pointer(commands.specials.toLong() + special * 4L, thumb = true)
            if (chooser !in menus) return@visitMatches true
            val move = rom.u16le(at + 3)
            val count = layout.moveCount ?: return@visitMatches false
            if (move !in 1 until count || move >= 0x8000) { exhausted = true; return@visitMatches false }
            sources.putIfAbsent(move, at)
            sources.size < MAX_SOURCES // Complete source-catalog coverage remains unknown.
        }
        if (exhausted || sources.isEmpty()) return null
        val rows = shared.acquisitions.keys.associateWith { species ->
            cancellation.throwIfCancellationRequested()
            sources.keys.mapNotNull { move ->
                MoveAcquisition(move, MoveAcquisitionMethod.TUTOR, move).takeIf { shared.allows(species, move) }
            }
        }
        return Data(sources.values.first(), rows, shared.failedSpecies, sources.size)
    }

    private fun workflow(at: Int): Workflow? {
        val names = layout.tables.moveNames ?: return null
        val stride = names.stride ?: names.recordSize
        if (!matches(at, 0xB5F0, 0x0600, 0x0E06) || !ram(at + 6, 0, 2) ||
            !matches(at + 8, 0x79C1, 0x2080, 0x4008, 0x2800) || word(at + 16) and 0xF000 != 0xD000 ||
            !matches(at + 20, 0x2109, 0x5669, 0x2064, 0x4341) || !ram(at + 28, 0, 4) ||
            !matches(at + 30, 0x180C, 0x1C2F, 0x370E) || !ram(at + 36, 1, 1) || word(at + 38) != 0x1C20 ||
            !ram(at + 44, 0, 2) || !matches(at + 46, 0x8800, 0x81E8) || !ram(at + 50, 0, 1) ||
            !matches(at + 52, 0x210E, 0x5E6A, 0x2100 or stride, 0x4351) ||
            literal(at + 60, 2) != 0x08000000L + names.offset || word(at + 62) != 0x1889 ||
            !matches(at + 68, 0x2002, 0x8078, 0x89E9, 0x1C20) ||
            !matches(at + 80, 0x0600, 0x0E00, 0x2801) ||
            listOf(40, 64, 76).any { bl(at + it)?.let { target -> target !in at until at + 86 } != true }) return null
        if (!ram(at + 18, 5, 4)) return null
        return Workflow(literal(at + 18, 5)!!, literal(at + 44, 0)!!)
    }

    private fun commands(): Commands? {
        val contracts = linkedSetOf<Commands>()
        for (setter in nominate(0xB530, 0x1C05) { matches(it + 8, 0x0400, 0x0C00) && word(it + 24) == 0x8020 } ?: return null) {
            val half = bl(setter + 4) ?: continue
            if (bl(setter + 20) != half || !matches(setter + 16, 0x1C04, 0x1C28) ||
                !matches(setter + 24, 0x8020, 0x2000, 0xBC30, 0xBC02, 0x4708) ||
                !matches(half, 0x1C03, 0x689A, 0x7810, 0x3201, 0x609A, 0x7811, 0x0209, 0x4308, 0x3201, 0x609A, 0x4770)) continue
            val getter = bl(setter + 12) ?: continue
            val variables = variables(getter) ?: continue
            val needle = encoded(0x08000001L + setter)
            val tables = mutableListOf<Int>()
            val complete = rom.visitMatches(needle, onCheck = cancellation::throwIfCancellationRequested) { cell ->
                if (cell and 3 == 0 && cell >= 0x16 * 4) tables += cell - 0x16 * 4
                tables.size <= MAX_CANDIDATES
            }
            if (!complete) return null
            for (table in tables) {
                val caller = pointer(table.toLong() + 4 * 4, true) ?: continue
                val special = pointer(table.toLong() + 0x25 * 4, true) ?: continue
                if (!callCommand(caller) || bl(special + 2) != half || !matches(special, 0xB500) ||
                    word(special + 6) != 0x0400 || !matches(special + 10, 0x0B80, 0x1840, 0x6800) ||
                    !matches(special + 20, 0x2000, 0xBC02, 0x4708) ||
                    bl(special + 16)?.let { word(it) } != 0x4700) continue
                val specials = romLiteral(special + 8, 1) ?: continue
                contracts += Commands(variables, specials)
            }
        }
        return contracts.singleOrNull().takeUnless { exhausted }
    }

    private fun variables(at: Int): Int? {
        if (!matches(at, 0xB500, 0x0400, 0x0C01, 0x1C0A) || literal(at + 8, 0) != 0x3FFFL ||
            !matches(at + 10, 0x4281, 0xD804, 0x2000, 0xE016) ||
            !matches(at + 24, 0x0408, 0x2800, 0xDA0A) || literal(at + 32, 2) != 0xFFFF8000L ||
            !matches(at + 34, 0x1889, 0x0089, 0x1809, 0x6808, 0xE009) || !ram(at + 52, 0, 4) ||
            word(at + 54) != 0x0051 || literal(at + 56, 2) == null ||
            !matches(at + 58, 0x1889, 0x6800, 0x1840, 0xBC02, 0x4708)) return null
        return romLiteral(at + 30, 0)
    }

    private fun callCommand(at: Int): Boolean {
        if (!matches(at, 0xB510, 0x1C04) || !matches(at + 8, 0x1C01, 0x1C20) ||
            !matches(at + 16, 0x2000, 0xBC10, 0xBC02, 0x4708)) return false
        val reader = bl(at + 4) ?: return false
        if (!matches(reader, 0xB570, 0x1C03, 0x6898, 0x7806, 0x3001, 0x6098, 0x7805, 0x1C42,
                0x609A, 0x7844, 0x1C51, 0x6099, 0x7850, 0x3101, 0x6099, 0x0200, 0x1900, 0x0200,
                0x1940, 0x0200, 0x1980, 0xBC70, 0xBC02, 0x4708)) return false
        val call = bl(at + 12) ?: return false
        if (!matches(call, 0xB530, 0x1C04, 0x1C0D, 0x68A1) || !matches(call + 12, 0x60A5, 0xBC30, 0xBC01, 0x4700)) return false
        val push = bl(call + 8) ?: return false
        return matches(push, 0xB500, 0x1C02, 0x1C0B, 0x7811, 0x1C48, 0x2813, 0xDC09, 0x0088,
            0x1C11, 0x310C, 0x1809, 0x600B, 0x7810, 0x3001, 0x7010, 0x2000, 0xE000, 0x2001, 0xBC02, 0x4708)
    }

    private fun menu(at: Int): Menu? {
        val regs = arrayOfNulls<Long>(13)
        val stack = mutableMapOf<Int, Long?>()
        var pc = at + 4
        repeat(64) {
            val op = word(pc)
            when {
                op < 0 -> return null
                op and 0xF800 == 0x2000 -> regs[op ushr 8 and 7] = (op and 255).toLong()
                op and 0xF800 == 0x4800 -> regs[op ushr 8 and 7] = literal(pc, op ushr 8 and 7) ?: return null
                op and 0xF800 == 0x9000 -> {
                    val offset = (op and 255) * 4
                    if (offset !in listOf(0, 4, 8)) return null
                    stack[offset] = regs[op ushr 8 and 7]
                }
                op and 0xF800 == 0x1800 -> {
                    val left = regs[op ushr 3 and 7] ?: return null
                    val right = if (op and 0x400 != 0) (op ushr 6 and 7).toLong() else regs[op ushr 6 and 7] ?: return null
                    regs[op and 7] = (if (op and 0x200 != 0) left - right else left + right) and 0xFFFFFFFFL
                }
                op and 0xFF00 == 0x4600 -> {
                    val source = op ushr 3 and 15; val dest = (op and 7) or (op ushr 4 and 8)
                    if (source >= 13 || dest >= 13) return null
                    regs[dest] = regs[source]
                }
                op and 0xF800 == 0xF000 -> {
                    val action = regs[2]?.takeIf { it in 0..255 }?.toInt() ?: return null
                    if (regs[0] != 0L || regs[1] != 0L || regs[3] != 0L || stack[0] != 4L ||
                        !matches(pc + 4, 0xB003, 0xBC01, 0x4700)) return null
                    val callback = codePointer(stack[4]) ?: return null
                    if (codePointer(stack[8]) == null) return null
                    return Menu(bl(pc) ?: return null, callback, action)
                }
                else -> return null
            }
            pc += 2
        }
        return null
    }

    private fun linkedMenu(menu: Menu, flow: Workflow, workflows: Set<Int>): Boolean {
        val at = menu.initializer
        // r2 is normalized into callee-saved r7 before the opaque UI calls. Both sides of
        // the allocation branch are explicit; only the success arm publishes action/callback.
        if (!matches(at, 0xB5F0, 0x4657, 0x464E, 0x4645, 0xB4E0, 0xB081, 0x9C09,
                0x0600, 0x0E00, 0x4680, 0x0609, 0x0E09, 0x4689, 0x0612, 0x0E17,
                0x061B, 0x0E1B, 0x9300, 0x0624, 0x0E26) || bl(at + 40) == null ||
            literal(at + 44, 0) == null || !matches(at + 46, 0x4682, 0x208E, 0x0080) ||
            literal(at + 52, 1) == null || bl(at + 54) == null ||
            !matches(at + 58, 0x1C05, 0x4651, 0x600D, 0x2D00, 0xD107, 0x980B) || bl(at + 70) == null ||
            word(at + 74) and 0xF800 != 0xE000 || literal(at + 84, 3) != flow.party ||
            !matches(at + 86, 0x210F, 0x4644, 0x4021, 0x7A1A, 0x2010, 0x4240, 0x4010,
                0x4308, 0x7218, 0x980B, 0x6018, 0x2400, 0x72DF, 0x00B2, 0x8969,
                0x2003, 0x4008, 0x4310, 0x8168, 0x980A, 0x6028, 0x606C)) return false
        val callback = menu.callback
        if (!matches(callback, 0xB570, 0x0600, 0x0E05, 0x1C2E)) return false
        val selection = bl(callback + 74) ?: return false
        if (!matches(selection, 0xB5F0, 0x4647, 0xB480, 0x1C0C, 0x0600, 0x0E05,
                0x2000, 0x5620, 0x2806, 0xD107) || bl(selection + 26) == null ||
            literal(selection + 36, 0) != flow.party ||
            word(selection + 38) != 0x7AC0 || word(selection + 40) and 0xFF00 != 0x3800 ||
            word(selection + 42) and 0xFF00 != 0x2800 || word(selection + 44) != 0xD900 ||
            word(selection + 48) != 0x0080 || !matches(selection + 52, 0x1840, 0x6800, 0x4687)) return false
        val index = menu.action - (word(selection + 40) and 255)
        if (index !in 0..(word(selection + 42) and 255)) return false
        val table = romLiteral(selection + 50, 1) ?: return false
        val actionCase = pointer(table.toLong() + index * 4L, false) ?: return false
        return matches(actionCase, 0x1C20) && bl(actionCase + 2) != null &&
            matches(actionCase + 6, 0x0600, 0x2800, 0xD100) && word(actionCase + 12) and 0xF800 == 0xE000 &&
            word(actionCase + 14) == 0x2005 && bl(actionCase + 16) != null && ram(actionCase + 20, 0, 4) &&
            matches(actionCase + 22, 0x6800, 0x300D) && bl(actionCase + 26) != null &&
            word(actionCase + 30) == 0x1C28 && bl(actionCase + 32) in workflows
    }

    private fun tick(): Boolean {
        cancellation.throwIfCancellationRequested()
        if (++work > MAX_WORK) exhausted = true
        return !exhausted
    }
    private fun word(at: Int): Int = if (tick() && at >= 0 && at and 1 == 0 && at <= rom.size - 2) rom.u16le(at) else -1
    private fun matches(at: Int, vararg ops: Int) = ops.indices.all { word(at + it * 2) == ops[it] }
    private fun byteMatches(at: Int, vararg bytes: Int) = at >= 0 && at.toLong() + bytes.size <= rom.size && bytes.indices.all { rom.u8(at + it) == bytes[it] }
    private fun value(at: Long): Long? = if (tick() && at in 0..rom.size.toLong() - 4) rom.u32le(at.toInt()) else null
    private fun pointer(at: Int, thumb: Boolean) = pointer(at.toLong(), thumb)
    private fun pointer(at: Long, thumb: Boolean): Int? {
        val raw = value(at) ?: return null
        val offset = raw - 0x08000000L - if (thumb) 1 else 0
        return offset.takeIf { it in 0..rom.size.toLong() - 2 && (!thumb || it and 1L == 0L) }?.toInt()
    }
    private fun codePointer(raw: Long?): Int? = raw?.minus(0x08000001L)?.takeIf { it in 0..rom.size.toLong() - 2 && it and 1L == 0L }?.toInt()
    private fun literal(at: Int, register: Int): Long? {
        val op = word(at)
        if (op < 0 || op and 0xFF00 != 0x4800 or (register shl 8)) return null
        return value(((at + 4) and -4).toLong() + (op and 255) * 4L)
    }
    private fun romLiteral(at: Int, register: Int): Int? = literal(at, register)?.minus(0x08000000L)?.takeIf { it in 0..rom.size.toLong() - 4 && it and 3L == 0L }?.toInt()
    private fun ram(at: Int, register: Int, alignment: Int): Boolean = literal(at, register)?.let {
        it % alignment == 0L && (it in 0x02000000L..0x0203FFFFL || it in 0x03000000L..0x03007FFFL)
    } == true
    private fun bl(at: Int): Int? {
        val hi = word(at); val lo = word(at + 2)
        if (hi < 0 || lo < 0 || hi and 0xF800 != 0xF000 || lo and 0xF800 != 0xF800) return null
        val delta = (((hi and 2047) shl 12) or ((lo and 2047) shl 1)) shl 9 shr 9
        return (at.toLong() + 4 + delta).takeIf { it in 0..rom.size.toLong() - 2 && it and 1L == 0L }?.toInt()
    }
    private fun encoded(value: Long) = ByteArray(4) { (value ushr (it * 8)).toByte() }
    private fun nominate(vararg ops: Int, qualifies: (Int) -> Boolean = { true }): List<Int>? {
        val pattern = ByteArray(ops.size * 2) { (ops[it / 2] ushr ((it and 1) * 8)).toByte() }
        val result = mutableListOf<Int>()
        val complete = rom.visitMatches(pattern, onCheck = cancellation::throwIfCancellationRequested) { at ->
            if (at and 1 == 0 && qualifies(at)) result += at
            !exhausted && result.size <= MAX_CANDIDATES
        }
        return result.takeIf { complete }
    }
    private companion object {
        const val MAX_WORK = 1_000_000
        const val MAX_CANDIDATES = 64
        const val MAX_SOURCES = 3
    }
}
