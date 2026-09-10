package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

/** Legacy menu-window title ABI used by the original Ruby/Sapphire field map. */
internal object CompiledGbaLegacyFieldMapTitle {
    class Scanner internal constructor(rom: RomImage) {
        private val reader = Reader(rom)

        fun connected(owner: Int, selectedLoader: Int): Boolean =
            connected(reader, owner, selectedLoader)
    }

    fun scanner(rom: RomImage): Scanner = Scanner(rom)

    private fun connected(r: Reader, owner: Int, selectedLoader: Int): Boolean {
        return r.ops(owner, 0xB500, 0xB081, 0x2080, 0x04C0, 0x2100, 0x8001, 0x3010, 0x8001,
            0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001,
            0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001) &&
            r.externalCall(owner + 0x2C, owner, OWNER_SIZE) &&
            r.externalCall(owner + 0x30, owner, OWNER_SIZE) &&
            r.literal(owner + 0x34, 0)?.let(::ramWord) == true &&
            r.ops(owner + 0x36, 0x2100) && loaderConnected(r, owner, selectedLoader)
    }

    private fun loaderConnected(r: Reader, owner: Int, selectedLoader: Int): Boolean {
        val entry = r.call(owner + 0x38) ?: return false
        return entry == selectedLoader ||
            (r.ops(entry, 0xB500, 0x0609, 0x0E09) && r.call(entry + 10) == selectedLoader)
    }

    internal fun loaderEntry(r: Reader, owner: Int, selectedLoader: Int): Int? {
        val entry = r.call(owner + 0x38) ?: return null
        if (entry == selectedLoader) return entry
        if (selectedLoader in entry until entry + LOADER_WRAPPER_SIZE ||
            !r.ops(entry, 0xB500, 0x0609, 0x0E09) ||
            !r.externalCall(entry + 6, entry, LOADER_WRAPPER_SIZE) ||
            r.call(entry + 10) != selectedLoader ||
            !r.ops(entry + 14, 0x0600, 0x2800, 0xD1FA, 0xBC01, 0x4700)
        ) return null
        return entry
    }

    fun declaration(
        rom: RomImage,
        owner: Int,
        selectedLoader: Int,
        cancellation: ParserCancellationToken,
    ): CompiledGbaFieldMapTitle.Declaration? {
        cancellation.throwIfCancellationRequested()
        val r = Reader(rom)
        if (!connected(r, owner, selectedLoader) || loaderEntry(r, owner, selectedLoader) == null) return null
        val stateRoot = r.literal(owner + 0x34, 0) ?: return null
        if (!r.ops(owner + 0x3C, 0x2000, 0x2100) ||
            !r.externalCall(owner + 0x40, owner, OWNER_SIZE) ||
            !r.ops(owner + 0x44, 0x2001, 0x2101) ||
            !r.externalCall(owner + 0x48, owner, OWNER_SIZE) ||
            !r.ops(owner + 0x4C, 0x2025) ||
            !r.externalCall(owner + 0x4E, owner, OWNER_SIZE) ||
            !r.ops(owner + 0x52, 0x2025) ||
            !r.externalCall(owner + 0x54, owner, OWNER_SIZE) ||
            !r.externalCall(owner + 0x58, owner, OWNER_SIZE) ||
            r.literal(owner + 0x5C, 1) != DISPLAY_CONTROL ||
            !r.ops(owner + 0x5E, 0x22F8, 0x0152, 0x1C10, 0x8008,
                0x2015, 0x2100, 0x221D, 0x2303)
        ) return null
        val frame = r.call(owner + 0x6E) ?: return null
        val source = r.literal(owner + 0x72, 0)?.let(r::romOffset) ?: return null
        if (!r.ops(owner + 0x74, 0x2116, 0x2201)) return null
        val printer = r.call(owner + 0x78) ?: return null
        if (!r.ops(owner + 0x7C, 0x2010, 0x2110, 0x221D, 0x2313) ||
            r.call(owner + 0x84) != frame ||
            !r.externalCall(owner + 0x88, owner, OWNER_SIZE)
        ) return null
        val mainCallback = r.literal(owner + 0x8C, 0)?.let(r::thumbOffset) ?: return null
        val vblankCallback = r.literal(owner + 0x92, 0)?.let(r::thumbOffset) ?: return null
        if (mainCallback == vblankCallback ||
            !r.externalCall(owner + 0x8E, owner, OWNER_SIZE) ||
            !r.externalCall(owner + 0x94, owner, OWNER_SIZE) ||
            !r.ops(owner + 0x98, 0x2001, 0x4240, 0x2100, 0x9100, 0x2210, 0x2300) ||
            !r.externalCall(owner + 0xA4, owner, OWNER_SIZE) ||
            !r.ops(owner + 0xA8, 0xB001, 0xBC01, 0x4700)
        ) return null
        val windowFlow = CompiledGbaLegacyTitleWindowFlow.resolve(rom, printer, frame, cancellation)
        val window = windowFlow?.windowRoot?.toInt() ?: 0
        val initial = CompiledGbaFieldMapTitle.Declaration(
            owner, selectedLoader, source, window, printer, frame, windowFlow)
        val ownerFlow = CompiledGbaLegacyFieldMapOwnerFlow.resolve(
            rom, initial, stateRoot, mainCallback, vblankCallback, cancellation)
        return initial.copy(ownerFlow = ownerFlow)
    }

    private fun ramWord(raw: Long): Boolean = raw and 3L == 0L &&
        (raw in 0x02000000L..0x0203FFFCL || raw in 0x03000000L..0x03007FFCL)

    private const val OWNER_SIZE = 0xC4
    private const val LOADER_WRAPPER_SIZE = 0x18
    private const val DISPLAY_CONTROL = 0x04000008L

    internal class Reader(val rom: RomImage) {
        fun range(at: Int, size: Int): Boolean = at >= 0 && at.toLong() + size <= rom.size
        fun ops(at: Int, vararg values: Int): Boolean = range(at, values.size * 2) &&
            values.indices.all { rom.u16le(at + it * 2) == values[it] }
        fun literal(site: Int, register: Int): Long? {
            if (!range(site, 2)) return null
            val op = rom.u16le(site)
            if (op and 0xFF00 != 0x4800 or (register shl 8)) return null
            val pool = ((site.toLong() + 4) and -4L) + (op and 255) * 4L
            return if (pool + 4 <= rom.size) rom.u32le(pool.toInt()) else null
        }
        fun romOffset(raw: Long): Int? = (raw - 0x08000000L)
            .takeIf { it in 0 until rom.size.toLong() }?.toInt()
        fun thumbOffset(raw: Long): Int? = if (raw and 1L == 1L) romOffset(raw - 1) else null
        fun call(site: Int): Int? {
            if (!range(site, 4)) return null
            val high = rom.u16le(site)
            val low = rom.u16le(site + 2)
            if (high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
            var delta = ((high and 0x7FF) shl 12) or ((low and 0x7FF) shl 1)
            if (delta and 0x400000 != 0) delta -= 0x800000
            return (site.toLong() + 4 + delta)
                .takeIf { it >= 0xC0 && it + 2 <= rom.size }?.toInt()
        }
        fun externalCall(site: Int, owner: Int, size: Int): Boolean =
            call(site)?.let { it !in owner until owner + size } == true
    }
}

internal class CompiledGbaLegacyTitleWindowFlow private constructor(
    val windowRoot: Long,
    val centeredOffset: Int,
    val initializerOffset: Int,
    val frameRendererOffset: Int,
) : CompiledGbaTitleWindowAuthority {
    companion object {
        fun resolve(
            rom: RomImage,
            printer: Int,
            frame: Int,
            cancellation: ParserCancellationToken,
        ): CompiledGbaLegacyTitleWindowFlow? {
            cancellation.throwIfCancellationRequested()
            val r = CompiledGbaLegacyFieldMapTitle.Reader(rom)
            if (!r.ops(printer, 0xB530, 0xB081, 0x1C05, 0x1C0B, 0x061B, 0x0E1B, 0x0612, 0x0E12))
                return null
            val windowRoot = r.literal(printer + 0x10, 0) ?: return null
            val tileOffsetRoot = r.literal(printer + 0x14, 1) ?: return null
            if (!ramWord(windowRoot) || !ramHalfWord(tileOffsetRoot) || windowRoot == tileOffsetRoot ||
                !r.ops(printer + 0x12, 0x6800) ||
                !r.ops(printer + 0x16, 0x880C, 0x9200, 0x1C29, 0x1C22)
            ) return null
            val centered = r.call(printer + 0x1E) ?: return null
            if (!r.ops(printer + 0x22, 0xB001, 0xBC30, 0xBC01, 0x4700)) return null

            if (!r.ops(frame, 0xB570, 0xB081, 0x1C04, 0x1C0D, 0x1C16, 0x0624, 0x0E24,
                    0x062D, 0x0E2D, 0x0636, 0x0E36, 0x061B, 0x0E1B) ||
                r.literal(frame + 0x1A, 0) != windowRoot ||
                !r.ops(frame + 0x1C, 0x6800, 0x9300, 0x1C21, 0x1C2A, 0x1C33)
            ) return null
            val renderer = r.call(frame + 0x26) ?: return null
            if (!r.ops(frame + 0x2A, 0xB001, 0xBC70, 0xBC01, 0x4700)) return null

            if (!r.ops(centered, 0xB510, 0xB081, 0x1C04, 0x9803, 0x0412, 0x0C12, 0x061B,
                    0x0E1B, 0x0600, 0x0E00, 0x9000, 0x1C20)) return null
            val initializer = r.call(centered + 0x18) ?: return null
            val finalizer = r.call(centered + 0x1E) ?: return null
            if (initializer == finalizer ||
                !r.ops(centered + 0x1C, 0x1C20) ||
                !r.ops(centered + 0x22, 0x0600, 0x0E00, 0xB001, 0xBC10, 0xBC02, 0x4708) ||
                !r.ops(initializer, 0xB510, 0xB081, 0x9C03, 0x0412, 0x0C12, 0x061B, 0x0E1B,
                    0x0624, 0x0E24, 0x9400) ||
                r.call(initializer + 0x14) == null ||
                !r.ops(initializer + 0x18, 0xB001, 0xBC10, 0xBC01, 0x4700)
            ) return null
            val ranges = listOf(printer to 0x32, frame to 0x38, centered to 0x30, initializer to 0x20)
            if (ranges.indices.any { i -> (i + 1 until ranges.size).any { j ->
                    overlaps(ranges[i], ranges[j])
                } } || listOf(renderer, finalizer, r.call(initializer + 0x14)!!).any { target ->
                    ranges.any { (start, size) -> target in start until start + size }
                }
            ) return null
            cancellation.throwIfCancellationRequested()
            return CompiledGbaLegacyTitleWindowFlow(windowRoot, centered, initializer, renderer)
        }

        private fun overlaps(first: Pair<Int, Int>, second: Pair<Int, Int>): Boolean =
            first.first < second.first + second.second && second.first < first.first + first.second
        private fun ramWord(raw: Long): Boolean = raw and 3L == 0L &&
            (raw in 0x02000000L..0x0203FFFCL || raw in 0x03000000L..0x03007FFCL)
        private fun ramHalfWord(raw: Long): Boolean = raw and 1L == 0L &&
            (raw in 0x02000000L..0x0203FFFEL || raw in 0x03000000L..0x03007FFEL)
    }
}

internal class CompiledGbaLegacyFieldMapOwnerFlow private constructor(
    val mainCallbackOffset: Int,
    val vblankCallbackOffset: Int,
) : CompiledGbaFieldMapOwnerAuthority {
    companion object {
        fun resolve(
            rom: RomImage,
            declaration: CompiledGbaFieldMapTitle.Declaration,
            stateRoot: Long,
            mainCallback: Int,
            vblankCallback: Int,
            cancellation: ParserCancellationToken,
        ): CompiledGbaLegacyFieldMapOwnerFlow? {
            cancellation.throwIfCancellationRequested()
            val flow = declaration.windowFlow as? CompiledGbaLegacyTitleWindowFlow ?: return null
            if (declaration.window.toLong() != flow.windowRoot || declaration.owner < 0xC0 ||
                declaration.owner and 3 != 0 || !ramWord(stateRoot)) return null
            val loaderEntry = CompiledGbaLegacyFieldMapTitle.loaderEntry(
                CompiledGbaLegacyFieldMapTitle.Reader(rom), declaration.owner, declaration.loader) ?: return null
            val ranges = buildList {
                add(declaration.owner to 0xC4)
                add(declaration.printer to 0x32)
                add(declaration.frame to 0x38)
                add(flow.centeredOffset to 0x30)
                add(flow.initializerOffset to 0x20)
                if (loaderEntry != declaration.loader) add(loaderEntry to 0x18)
            }
            val source = declaration.source
            if (source < 0xC0 || source >= rom.size || ranges.any { (start, size) ->
                    source in start until start + size
                } || listOf(declaration.loader, mainCallback, vblankCallback).any { target ->
                    ranges.any { (start, size) -> target in start until start + size }
                }
            ) return null
            cancellation.throwIfCancellationRequested()
            return CompiledGbaLegacyFieldMapOwnerFlow(mainCallback, vblankCallback)
        }

        private fun ramWord(raw: Long): Boolean = raw and 3L == 0L &&
            (raw in 0x02000000L..0x0203FFFCL || raw in 0x03000000L..0x03007FFCL)
    }
}
