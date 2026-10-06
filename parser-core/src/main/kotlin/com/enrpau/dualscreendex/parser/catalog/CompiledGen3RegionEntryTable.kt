package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage

/** Proves the bounded `entries[section].name` consumer, including its eight-byte row stride. */
internal object CompiledGen3RegionEntryTable {
    data class Table(val root: Int, val count: Int) {
        fun contains(section: Int): Boolean = section in 0 until count
    }

    data class Discovery(val tables: List<Table>, val rejected: Boolean = false)

    fun discover(
        rom: RomImage,
        references: GbaReferenceIndex,
        cancellation: ParserCancellationToken,
        extentLimit: Long = ResolutionLimits().maxDatasetExtentBytes,
    ): Discovery {
        cancellation.throwIfCancellationRequested()
        if (references.overflowed) return Discovery(emptyList(), rejected = true)
        val tables = linkedSetOf<Table>()
        var offset = 0
        while (offset.toLong() + CONSUMER_BYTES <= rom.size.toLong()) {
            if (offset % CANCELLATION_INTERVAL_BYTES == 0) cancellation.throwIfCancellationRequested()
            val table = prove(rom, offset)
            if (table != null) {
                val site = offset + 4
                val evidence = references.target(table.root)
                val bytes = table.count.toLong() * ENTRY_BYTES
                if (evidence == null || !evidence.siteEvidenceAvailable || site !in evidence.instructionSites ||
                    bytes > extentLimit || table.root.toLong() + bytes > rom.size.toLong()
                ) return Discovery(emptyList(), rejected = true)
                tables += table
                if (tables.size > MAX_TABLES) return Discovery(emptyList(), rejected = true)
            }
            offset += 2
        }
        return Discovery(tables.toList())
    }

    private fun prove(rom: RomImage, at: Int): Table? {
        val compare = rom.u16le(at)
        val branch = rom.u16le(at + 2)
        val load = rom.u16le(at + 4)
        val shift = rom.u16le(at + 6)
        val field = rom.u16le(at + 8)
        val add = rom.u16le(at + 10)
        val indirect = rom.u16le(at + 12)
        if (compare and 0xF800 != 0x2800 || branch and 0xFF00 != 0xD800 ||
            load and 0xF800 != 0x4800 || shift and 0xFFC0 != 0x00C0 ||
            field and 0xF8FF != 0x3004 || add and 0xFE00 != 0x1800 ||
            indirect and 0xFFC0 != 0x6800
        ) return null
        val index = compare ushr 8 and 7
        val base = load ushr 8 and 7
        val product = shift and 7
        val address = add and 7
        if (base == index || base == product || shift ushr 3 and 7 != index ||
            field ushr 8 and 7 != base ||
            setOf(add ushr 3 and 7, add ushr 6 and 7) != setOf(base, product) ||
            indirect ushr 3 and 7 != address
        ) return null
        val branchTarget = at.toLong() + 6 + (branch and 255).toByte().toInt() * 2L
        if (branchTarget < at.toLong() + CONSUMER_BYTES || branchTarget + 2 > rom.size.toLong()) return null
        val literal = ((at.toLong() + 8) and -4L) + (load and 255) * 4L
        if (literal < at.toLong() + CONSUMER_BYTES || literal + 4 > rom.size.toLong()) return null
        val root = rom.gbaPointer(literal.toInt()) ?: return null
        if (root and 3 != 0) return null
        return Table(root, (compare and 255) + 1)
    }

    private const val CONSUMER_BYTES = 14L
    private const val ENTRY_BYTES = 8L
    private const val CANCELLATION_INTERVAL_BYTES = 4096
    private const val MAX_TABLES = 32
}
