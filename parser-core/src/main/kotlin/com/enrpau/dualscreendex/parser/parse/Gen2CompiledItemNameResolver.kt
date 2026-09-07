package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.Gen2ItemNameAuthority

/** One original ROM0 nomination, followed only by selected literal compiled dependencies. */
internal class Gen2CompiledItemNameResolver(private val session: RomAnalysisSession) {
    private var cached: Gen2ItemNameAuthority? = null
    private var work = 0
    private val code = mutableSetOf<Int>()
    private val windows = mutableListOf<IntRange>()
    private val values = mutableMapOf<String, Int>()
    private val rom get() = session.rom
    private fun v(name: String) = values.getValue(name)

    @Synchronized fun original(): Gen2ItemNameAuthority {
        session.cancellation.throwIfCancellationRequested()
        cached?.let { return it }
        // Invoked incomplete/cancelled discoveries are terminal. A later payload read cannot retry them.
        cached = Gen2ItemNameAuthority.Unavailable()
        try {
            val candidates = mutableListOf<Int>()
            val end = minOf(rom.size, BANK)
            for (at in 0..end - 10) {
                tick()
                if (rom.u8(at) == 0xe5 && rom.u8(at + 1) == 0xc5 && rom.u8(at + 2) == 0xfa &&
                    rom.u8(at + 5) == 0xfe && rom.u8(at + 7) == 0x30) {
                    candidates += at
                    check(candidates.size <= minOf(32, session.limits.maxProbeRootsPerDataset))
                }
            }
            check(candidates.size == 1)
            cached = prove(candidates.single())
        } catch (_: InvalidContract) {
            // No family, payload, hash, historical-reference or alternate-consumer retry.
        }
        session.cancellation.throwIfCancellationRequested()
        return requireNotNull(cached)
    }

    private fun prove(wrapper: Int): Gen2ItemNameAuthority.Available {
        var p = cursor(wrapper)
        p.match("E5 C5 FA @object FE %threshold 30 ~generatedBranch EA @index 3E %selector EA @selectorSlot CD @getName 18 ~copied")
        check(p.pc == v("generatedBranch")); p.match("CD @generated")
        check(p.pc == v("copied")); p.match("11 @destination C1 E1 C9")
        p = cursor(home(v("getName")))
        p.match("F0 %hram F5 E5 C5 D5 FA @selectorSlot FE %monsterSelector 20 ~ordinaryBranch")
        check(v("selector") != v("monsterSelector") && v("ordinaryBranch") in p.pc until p.start + WINDOW)
        p.pc = v("ordinaryBranch") // The unselected monster-name arm supplies no naming authority.
        p.match("FA @selectorSlot 3D 5F 16 00 21 @directory 19 19 19 2A D7 2A 66 6F FA @index 3D CD @nth 11 @destination 01 @copyBytes CD @copier 7B EA @returnLow 7A EA @returnHigh D1 C1 E1 F1 D7 C9")
        check(v("copyBytes") in 1..64)
        cursor(home(v("nth"))).match("A7 C8 C5 47 0E %terminator 2A B9 20 FC 05 20 F9 C1 C9")
        cursor(home(v("copier"))).match("04 0C 18 03 2A 12 13 0D 20 FA 05 20 F7 C9")
        p = cursor(home(v("generated")))
        p.match("E5 D5 C5 FA @object F5 FE %split F5 38 ~tmBranch 21 @hmPrefix 01 @hmBytes 18 ~prefixJoin")
        check(p.pc == v("tmBranch")); p.match("21 @tmPrefix 01 @tmBytes")
        check(p.pc == v("prefixJoin"))
        p.match("11 @destination CD @copier D5 FA @object 4F 21 @helperAddress 3E %helperBank")
        val rst = p.byte(); check(rst and 0xc7 == 0xc7)
        val farRst = rst and 0x38
        check(farRst != 0x10)
        p.match("D1 F1 79 38 ~notHM D6 %hmSubtract")
        check(p.pc == v("notHM"))
        p.match("06 %digits D6 0A 38 03 04 18 F9 C6 0A F5 78 12 13 F1 06 %digits 80 12 13 3E %terminator 12 F1 EA @object C1 D1 E1 C9")
        val helper = pointer(v("helperBank"), v("helperAddress"))
        p = cursor(helper)
        p.match("79 FE %skipFirst 38 ~skipDone FE %skipSecond 38 ~skipOne 3D")
        check(p.pc == v("skipOne")); p.match("3D"); check(p.pc == v("skipDone"))
        p.match("D6 %numberSubtract 3C 4F C9")
        check(v("threshold") in 1 until v("skipFirst") && v("skipFirst") < v("skipSecond") && v("skipSecond") < v("split"))
        check(v("numberSubtract") == v("threshold") && v("hmSubtract") == v("split") - v("threshold") - 2)
        check(v("digits") + 9 <= 255)
        // A local returned-leaf contract, not a global MBC/RAM allocation proof.
        cursor(0x10).match("E0 %hram EA @mapper C9")
        check(v("hram") in 0x80..0xfe && v("mapper") in 0x2000 until 0x4000)
        cursor(farRst).match("C3 @farCall")
        p = cursor(home(v("farCall")))
        val scratch = when (p.byte()) {
            0xea -> {
                val address = p.word(); check(address in 0xc000 until 0xe000)
                p.match("F0 %hram F5 FA"); check(p.word() == address); address
            }
            0xe0 -> {
                val address = p.byte(); check(address in 0x80..0xfe && address != v("hram"))
                p.match("F0 %hram F5 F0"); check(p.byte() == address); 0xff00 + address
            }
            else -> invalid()
        }
        p.match("D7 CD @callHL 78 EA @savedB 79 EA @savedC C1 78 D7 FA @savedB 47 FA @savedC 4F C9")
        cursor(home(v("callHL"))).match("E9")
        // FarCall saves returned B/C before POP BC restores the prior bank in B, then reloads B/C.
        // The formatter's saved AF is popped afterwards, so its HM classification carry survives.
        val outputEnd = v("destination") + maxOf(v("copyBytes"), v("tmBytes") + 3, v("hmBytes") + 3)
        check(v("destination") in 0xc000 until 0xe000 && outputEnd <= 0xe000 && outputEnd - v("destination") <= 128)
        val slots = listOf("object", "index", "selectorSlot", "returnLow", "returnHigh", "savedB", "savedC").map(::v)
        check(slots.distinct().size == slots.size && slots.all { it in 0xc000 until 0xe000 && it !in v("destination") until outputEnd })
        check(scratch !in slots && scratch !in v("destination") until outputEnd)
        check(v("selector") in 1..255)
        val entry = home(v("directory") + 3 * (v("selector") - 1))
        // The selected three-byte directory is data, not an additional routine/stub window.
        val bank = readCode(home(entry))
        val address = readCode(home(entry + 1)) or (readCode(home(entry + 2)) shl 8)
        val root = pointer(bank, address)
        val bankEnd = minOf(rom.size, (root / BANK + 1) * BANK)
        check(root !in code)
        for (kind in listOf("tm", "hm")) {
            val start = home(v(kind + "Prefix")); val count = v(kind + "Bytes")
            check(count in 1..32 && start.toLong() + count <= minOf(BANK, rom.size))
            check((start until start + count).none { it in code })
        }
        return Gen2ItemNameAuthority.Available(root, bankEnd, v("copyBytes"), v("threshold"), v("split"),
            v("skipFirst"), v("skipSecond"), v("numberSubtract"), v("hmSubtract"), v("digits"), v("terminator"),
            v("tmPrefix"), v("tmBytes"), v("hmPrefix"), v("hmBytes"), code)
    }

    private fun tick() {
        session.cancellation.throwIfCancellationRequested()
        check(++work <= session.limits.maxProbeWorkPerDataset)
    }
    private fun home(at: Int): Int { check(at in 0 until minOf(BANK, rom.size)); return at }
    private fun pointer(bank: Int, address: Int): Int {
        check(bank in 0..255 && address in 0 until 2 * BANK && (address < BANK || bank > 0))
        val at = if (address < BANK) address else bank * BANK + address - BANK
        check(at in 0 until rom.size); return at
    }
    private fun cursor(at: Int): Cursor {
        check(at in 0 until rom.size)
        if (windows.none { at in it }) {
            check(windows.size < 10)
            windows += at until minOf(at + WINDOW, (at / BANK + 1) * BANK, rom.size)
        }
        return Cursor(at)
    }
    private fun readCode(at: Int): Int {
        tick()
        code += at
        check(code.size.toLong() <= minOf(4096L, session.limits.maxDatasetExtentBytes))
        return rom.u8(at)
    }
    private inner class Cursor(val start: Int) {
        var pc = start
        fun byte(): Int {
            check(pc in start until start + WINDOW && pc / BANK == start / BANK && pc < rom.size)
            return readCode(pc++)
        }
        fun word(): Int = byte() or (byte() shl 8)
        fun match(pattern: String) {
            for (token in pattern.split(' ')) {
                val value = when (token.first()) {
                    '@' -> word()
                    '~' -> { val delta = byte().toByte().toInt(); pc + delta }
                    else -> byte()
                }
                if (token.first() in "@%~") {
                    val key = token.drop(1)
                    check(values[key] == null || values[key] == value)
                    values[key] = value
                } else check(value == token.toInt(16))
            }
        }
    }
    private class InvalidContract : RuntimeException()
    private fun check(value: Boolean) { if (!value) invalid() }
    private fun invalid(): Nothing = throw InvalidContract()
    private companion object { const val BANK = 0x4000; const val WINDOW = 512 }
}
