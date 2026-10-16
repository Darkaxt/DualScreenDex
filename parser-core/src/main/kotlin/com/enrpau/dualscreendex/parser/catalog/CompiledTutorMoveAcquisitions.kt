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
    private data class Commands(val table: Int, val variables: Int, val specials: Int)
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
        var directSources = 0
        var confirmationSources = 0
        // The immediate helper and the bounded confirmation helper are separate contracts.
        // A GOTO or a nearby chooser alone is never teaching-source authority.
        rom.visitMatches(byteArrayOf(0x16), onCheck = cancellation::throwIfCancellationRequested) { at ->
            if (!tick() || at > rom.size - 10) return@visitMatches !exhausted
            val variable = rom.u16le(at + 1)
            if (variable < 0x8000 || variableAddress(commands, variable) != flow.selected)
                return@visitMatches !exhausted
            val opcode = rom.u8(at + 5)
            if (opcode != 4 && opcode != 5 || opcode == 4 && directSources >= MAX_SOURCES ||
                opcode == 5 && confirmationSources >= MAX_CONFIRMATION_SOURCES) return@visitMatches true
            val helper = pointer(at + 6, thumb = false) ?: return@visitMatches true
            val move = rom.u16le(at + 3)
            val accepted = if (opcode == 4) {
                helper <= rom.size - 7 && rom.u8(helper) == 0x25 &&
                    byteMatches(helper + 3, 0x27, 0x6A, 0x5A, 3) &&
                    pointer(commands.specials.toLong() + rom.u16le(helper + 1) * 4L, true) in menus
            } else confirmation(at, helper, move, commands, flow, menus.keys)
            if (!accepted) return@visitMatches !exhausted
            val count = layout.moveCount ?: return@visitMatches false
            if (move !in 1 until count || move >= 0x8000) { exhausted = true; return@visitMatches false }
            if (sources.putIfAbsent(move, at) == null) {
                if (opcode == 4) directSources++ else confirmationSources++
            }
            sources.size < MAX_SOURCES + MAX_CONFIRMATION_SOURCES // Total coverage remains unknown.
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

    private fun confirmation(source: Int, helper: Int, move: Int, commands: Commands, flow: Workflow, menus: Set<Int>): Boolean {
        if (source < 5 || helper > rom.size - 105 || !byteMatches(source - 5, 0x16)) return false
        val price = rom.u16le(source - 4)
        val priceAddress = variableAddress(commands, price) ?: return false
        if (priceAddress == flow.selected || rom.u16le(source - 2) >= 0x8000) return false
        // Complete command boundaries, including both rejection branches and the post-teach
        // payment. No scanning across operands, following cycles or accepting extra effects.
        val boundaries = intArrayOf(0, 3, 7, 13, 15, 20, 26, 31, 36, 42, 48, 50, 55, 56, 62, 64, 66, 69, 72, 75, 76, 81, 87, 93, 95, 100, 103, 104)
        val opcodes = intArrayOf(0x25, 0x83, 0x0F, 9, 0x21, 6, 0x26, 0x22, 6, 0x0F, 9, 5, 2, 0x0F, 9, 0x97, 0x25, 0x25, 0x25, 0x27, 0x21, 6, 0x0F, 9, 0x19, 0x25, 0x6C, 2)
        if (boundaries.indices.any { rom.u8(helper + boundaries[it]) != opcodes[it] }) return false
        if (rom.u8(helper + 4) != 1 || rom.u16le(helper + 5) != price ||
            rom.u8(helper + 14) != 5 || listOf(49, 63, 94).any { rom.u8(helper + it) != 4 } ||
            rom.u16le(helper + 18) != 0 || rom.u16le(helper + 79) != 0 ||
            rom.u8(helper + 21) != 1 || rom.u8(helper + 37) != 4 || rom.u8(helper + 82) != 1 ||
            pointer(helper + 38, false) != helper + 56 || rom.u8(helper + 65) != 1) return false
        val result = rom.u16le(helper + 16)
        val walletVariable = rom.u16le(helper + 27)
        val paymentVariable = rom.u16le(helper + 96)
        val resultAddress = variableAddress(commands, result) ?: return false
        val paymentAddress = variableAddress(commands, paymentVariable) ?: return false
        if (resultAddress == flow.selected || paymentAddress == flow.selected ||
            resultAddress == priceAddress || paymentAddress == priceAddress ||
            walletVariable !in 0x4000..0x7FFF || rom.u16le(helper + 32) != walletVariable ||
            rom.u16le(helper + 34) != price || rom.u16le(helper + 77) != result ||
            rom.u16le(helper + 98) != price) return false
        val rejection = pointer(helper + 22, false) ?: return false
        if (pointer(helper + 51, false) != rejection || rejection in helper until helper + 105 ||
            pointer(helper + 83, false) in listOf(null, helper + 56, helper + 72)) return false
        for (offset in intArrayOf(7, 42, 56, 87)) {
            if (rom.u8(helper + offset + 1) != 0 || pointer(helper + offset + 2, false) == null) return false
        }
        fun special(offset: Int) = pointer(commands.specials.toLong() + rom.u16le(helper + offset + 1) * 4L, true)
        val formatter = special(0) ?: return false
        val wallet = pointer(commands.specials.toLong() + rom.u16le(helper + 29) * 4L, true) ?: return false
        val firstClose = special(66) ?: return false
        val secondClose = special(69) ?: return false
        if (special(72) !in menus || special(100) == null || firstClose == secondClose ||
            !confirmationCommands(commands, flow.selected) || !moveNameFormatter(formatter, move, flow.selected) ||
            !matches(wallet + 2, 0x6800) || !matches(wallet + 6, 0x1840, 0x8800, 0x4770) ||
            !ram(wallet, 0, 4) || literal(wallet + 4, 1)?.let { it in 0..0xFFFF } != true) return false
        val first = windowCloser(firstClose, flow.selected) ?: return false
        val second = windowCloser(secondClose, flow.selected) ?: return false
        return first.first != second.first && first.second == second.second
    }

    private fun confirmationCommands(commands: Commands, selected: Long): Boolean {
        fun command(opcode: Int) = pointer(commands.table.toLong() + opcode * 4L, true)
        val caller = command(4) ?: return false
        val reader = bl(caller + 4) ?: return false
        val jump = command(5) ?: return false
        if (!matches(jump, 0xB510, 0x1C04) || bl(jump + 4) != reader ||
            !matches(jump + 8, 0x1C01, 0x1C20) ||
            bl(jump + 12)?.let { matches(it, 0x6081, 0x4770) } != true ||
            !matches(jump + 16, 0x2000, 0xBC10, 0xBC02, 0x4708)) return false
        val standard = command(9) ?: return false
        if (!matches(standard, 0xB500, 0x1C02, 0x6890, 0x7801, 0x3001, 0x6090, 0x0089) ||
            word(standard + 16) != 0x1809 ||
            !matches(standard + 20, 0x4281, 0xD203, 0x6809, 0x1C10) ||
            bl(standard + 28) != bl(caller + 12) || !matches(standard + 32, 0x2000, 0xBC02, 0x4708)) return false
        val standards = romLiteral(standard + 14, 0) ?: return false
        val end = romLiteral(standard + 18, 0) ?: return false
        if (standards.toLong() + 6 * 4 > end) return false
        val message = pointer(standards.toLong() + 4 * 4, false) ?: return false
        val question = pointer(standards.toLong() + 5 * 4, false) ?: return false
        if (!byteMatches(message, 0x67, 0, 0, 0, 0, 0x66, 0x6D, 3) ||
            !byteMatches(question, 0x67, 0, 0, 0, 0, 0x66, 0x6E) ||
            !byteMatches(question + 9, 3)) return false
        // These are the engine's presentation boundary, like linkedMenu's UI calls.
        // Native value producers and destinations remain separately linked below.
        val setter = command(0x16) ?: return false
        val half = bl(setter + 4) ?: return false
        val getter = bl(setter + 12) ?: return false
        val specialVar = command(0x26) ?: return false
        if (!matches(specialVar, 0xB570, 0x1C05) || bl(specialVar + 4) != half ||
            !matches(specialVar + 8, 0x0400, 0x0C00) || bl(specialVar + 12) != getter ||
            word(specialVar + 16) != 0x1C06 || word(specialVar + 20) != 0x1C28 || bl(specialVar + 22) != half ||
            !matches(specialVar + 26, 0x0400, 0x0B80, 0x1900, 0x6800) ||
            romLiteral(specialVar + 18, 4) != commands.specials ||
            bl(specialVar + 34)?.let { word(it) } != 0x4700 ||
            !matches(specialVar + 38, 0x8030, 0x2000, 0xBC70, 0xBC02, 0x4708)) return false
        val branch = command(6) ?: return false
        if (!matches(branch, 0xB530, 0x1C05, 0x68A8, 0x7804, 0x3001, 0x60A8, 0x1C28) ||
            bl(branch + 14) != reader || word(branch + 18) != 0x1C02 ||
            !matches(branch + 22, 0x0060, 0x1900, 0x78AB, 0x18C0, 0x1840, 0x7800, 0x2801, 0xD103, 0x1C28, 0x1C11) ||
            bl(branch + 42) != bl(jump + 12) || !matches(branch + 46, 0x2000, 0xBC30, 0xBC02, 0x4708)) return false
        val conditions = romLiteral(branch + 20, 1) ?: return false
        if (!byteMatches(conditions + 3, 0, 1, 0) || !byteMatches(conditions + 12, 0, 1, 1)) return false
        val compareValue = command(0x21) ?: return false
        val compareVariables = command(0x22) ?: return false
        val compare = bl(compareValue + 32) ?: return false
        if (!matches(compare, 0xB500, 0x0400, 0x0C00, 0x0409, 0x0C09, 0x4288, 0xD201,
                0x2000, 0xE004, 0x4288, 0xD001, 0x2002, 0xE000, 0x2001, 0xBC02, 0x4708) ||
            !matches(compareValue, 0xB530, 0x1C04) || bl(compareValue + 4) != half ||
            !matches(compareValue + 8, 0x0400, 0x0C00) || bl(compareValue + 12) != getter ||
            !matches(compareValue + 16, 0x8805, 0x1C20) || bl(compareValue + 20) != half ||
            !matches(compareValue + 24, 0x1C01, 0x0409, 0x0C09, 0x1C28) ||
            !matches(compareValue + 36, 0x70A0, 0x2000, 0xBC30, 0xBC02, 0x4708) ||
            !matches(compareVariables, 0xB530, 0x1C04) || bl(compareVariables + 4) != half ||
            !matches(compareVariables + 8, 0x0400, 0x0C00) || bl(compareVariables + 12) != getter ||
            !matches(compareVariables + 16, 0x1C05, 0x1C20) || bl(compareVariables + 20) != half ||
            !matches(compareVariables + 24, 0x0400, 0x0C00) || bl(compareVariables + 28) != getter ||
            !matches(compareVariables + 32, 0x882A, 0x8801, 0x1C10) || bl(compareVariables + 38) != compare ||
            !matches(compareVariables + 42, 0x70A0, 0x2000, 0xBC30, 0xBC02, 0x4708)) return false
        val load = command(0x0F) ?: return false
        if (!matches(load, 0xB530, 0x1C04, 0x68A0, 0x7805, 0x3001, 0x60A0, 0x1C20) ||
            bl(load + 14) != reader || !matches(load + 18, 0x00AD, 0x3464, 0x1964, 0x6020, 0x2000, 0xBC30, 0xBC02, 0x4708)) return false
        val number = command(0x83) ?: return false
        val valueGetter = bl(number + 18) ?: return false
        if (!matches(number, 0xB530, 0x6881, 0x780D, 0x3101, 0x6081) || bl(number + 10) != half ||
            !matches(number + 14, 0x0400, 0x0C00) ||
            !matches(valueGetter, 0xB510, 0x0400, 0x0C04, 0x1C20) || bl(valueGetter + 8) != getter ||
            !matches(valueGetter + 12, 0x2800, 0xD001, 0x8800, 0xE000, 0x1C20, 0xBC10, 0xBC02, 0x4708) ||
            !matches(number + 22, 0x1C04, 0x0424, 0x0C24, 0x1C20) || bl(number + 30) == null ||
            !matches(number + 34, 0x1C03, 0x061B, 0x0E1B) ||
            !matches(number + 42, 0x00AD, 0x182D, 0x6828, 0x1C21, 0x2200) || bl(number + 52) == null ||
            !matches(number + 56, 0x2000, 0xBC30, 0xBC02, 0x4708)) return false
        val buffers = romLiteral(number + 40, 0) ?: return false
        val output = value(buffers.toLong() + 4) ?: return false
        if (output !in 0x02000000L..0x0203FFF9L && output !in 0x03000000L..0x03007FF9L ||
            output < selected + 2 && selected < output + 6) return false
        val fade = command(0x97) ?: return false
        return matches(fade, 0xB510, 0x1C04, 0x68A1, 0x7808, 0x3101, 0x60A1, 0x2100) &&
            bl(fade + 14) != null && codePointer(literal(fade + 18, 1)) != null &&
            word(fade + 20) == 0x1C20 && bl(fade + 22)?.let { dialogueCommands(commands, reader, it) } == true &&
            matches(fade + 26, 0x2001, 0xBC10, 0xBC02, 0x4708)
    }

    private fun dialogueCommands(commands: Commands, reader: Int, setup: Int): Boolean {
        fun command(opcode: Int) = pointer(commands.table.toLong() + opcode * 4L, true)
        if (!matches(setup, 0x2202, 0x7042, 0x6041, 0x4770)) return false
        val message = command(0x67) ?: return false
        if (!matches(message, 0xB510, 0x1C04) || bl(message + 4) != reader ||
            !matches(message + 8, 0x2800, 0xD100, 0x6E60) || bl(message + 14) == null ||
            !matches(message + 18, 0x2000, 0xBC10, 0xBC02, 0x4708)) return false
        for (opcode in intArrayOf(0x66, 0x6D)) {
            val wait = command(opcode) ?: return false
            if (word(wait) != 0xB500 || bl(wait + 4) != setup ||
                !matches(wait + 8, 0x2001, 0xBC02, 0x4708)) return false
            val poll = codePointer(literal(wait + 2, 1)) ?: return false
            if (word(poll) != 0xB500 || !ram(poll + 2, 0, 4)) return false
            if (opcode == 0x66) {
                if (!matches(poll + 4, 0x7800, 0x2800, 0xD004, 0x2000, 0xE003) ||
                    !matches(poll + 20, 0x2001, 0xBC02, 0x4708)) return false
            } else if (!matches(poll + 4, 0x8DC1, 0x2001, 0x4008, 0x2800, 0xD108,
                    0x2002, 0x4008, 0x2800, 0xD104, 0x2000, 0xE003) ||
                !matches(poll + 32, 0x2001, 0xBC02, 0x4708)) return false
        }
        val question = command(0x6E) ?: return false
        if (!matches(question, 0xB500, 0x6882, 0x7813, 0x3201, 0x6082, 0x7811, 0x3201, 0x6082, 0x1C18) ||
            bl(question + 18) == null || !matches(question + 22, 0x0600, 0x0E00, 0x2801, 0xD001, 0x2000, 0xE002) ||
            bl(question + 34) == null || !matches(question + 38, 0x2001, 0xBC02, 0x4708)) return false
        val returning = command(3) ?: return false
        if (word(returning) != 0xB500 || !matches(returning + 6, 0x2000, 0xBC02, 0x4708)) return false
        val restore = bl(returning + 2) ?: return false
        if (!matches(restore, 0xB510, 0x1C04) || !matches(restore + 8, 0x60A0, 0xBC10, 0xBC01, 0x4700)) return false
        val pop = bl(restore + 4) ?: return false
        return matches(pop, 0xB500, 0x1C02, 0x7810, 0x2800, 0xD008, 0x3801, 0x7010,
            0x7811, 0x0089, 0x1C10, 0x300C, 0x1840, 0x6800, 0xE000, 0x2000, 0xBC02, 0x4708)
    }

    private fun moveNameFormatter(at: Int, move: Int, selected: Long): Boolean {
        val names = layout.tables.moveNames ?: return false
        val stride = names.stride ?: names.recordSize
        val count = layout.moveCount ?: return false
        if (stride !in 1..255 || move !in 1 until count ||
            !matches(at, 0xB500) || literal(at + 4, 1) != selected ||
            !matches(at + 6, 0x880A, 0x2100 or stride, 0x4351) ||
            literal(at + 12, 2) != 0x08000000L + names.offset ||
            word(at + 14) != 0x1889 || !matches(at + 20, 0xBC01, 0x4700) || !ram(at + 2, 0, 1)) return false
        val output = literal(at + 2, 0) ?: return false
        if (output !in 0x02000000L..(0x02040000L - stride) &&
            output !in 0x03000000L..(0x03008000L - stride) ||
            output < selected + 2 && selected < output + stride) return false
        val name = names.offset.toLong() + move.toLong() * stride
        if (name !in 0..rom.size.toLong() - stride || (0 until stride).none { rom.u8(name.toInt() + it) == 0xFF }) return false
        val copy = bl(at + 16) ?: return false
        return matches(copy, 0xB500, 0x1C03, 0xE002, 0x701A, 0x3301, 0x3101, 0x780A,
            0x1C10, 0x28FF, 0xD1F8, 0x20FF, 0x7018, 0x1C18, 0xBC02, 0x4708)
    }

    private fun windowCloser(at: Int, selected: Long): Pair<Long, Pair<Int, Int>>? {
        if (!matches(at, 0xB510) || !ram(at + 2, 4, 1) || !matches(at + 4, 0x7820, 0x2101) ||
            word(at + 12) != 0x7820 || !matches(at + 18, 0xBC10, 0xBC01, 0x4700)) return null
        val window = literal(at + 2, 4) ?: return null
        if (window in selected until selected + 2) return null
        val clear = bl(at + 8) ?: return null
        val remove = bl(at + 14) ?: return null
        return (window to (clear to remove)).takeIf { clear != remove }
    }

    private fun variableAddress(commands: Commands, variable: Int): Long? =
        if (variable in 0x8000..0xFFFF) value(commands.variables.toLong() + (variable - 0x8000) * 4L)
            ?.takeIf { it and 1L == 0L && (it in 0x02000000L..0x0203FFFEL || it in 0x03000000L..0x03007FFEL) }
        else null

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
                contracts += Commands(table, variables, specials)
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
        const val MAX_SOURCES = 10
        const val MAX_CONFIRMATION_SOURCES = 13
    }
}
