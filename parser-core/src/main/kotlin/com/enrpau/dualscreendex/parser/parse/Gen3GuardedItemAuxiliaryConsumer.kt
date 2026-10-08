package com.enrpau.dualscreendex.parser.parse

/** Complete local byte-only disposition, not opaque helper effects or runtime enum authority. */
internal class Gen3GuardedItemAuxiliaryConsumer(
    private val word: (Int) -> Int,
    private val literalSlot: (Int) -> Int?,
    private val literalRoot: (Int) -> Int?,
    private val call: (Int) -> Int?,
    private val sanitize: (Int) -> Int?,
) {
    data class Proof(val stride: Int, val count: Int, val field: Int, val pools: Set<Int>)

    fun resolve(site: Int, root: Int): Proof? {
        if (word(site) and 0xFF00 != 0x4800 || literalRoot(site) != root) return null
        for (entry in maxOf(0, site - MAX_BYTES)..site step 2) {
            if (!matches(entry, 0xB500, 0x0400, 0x0C00)) continue
            complete(entry, site, root)?.let { return it }
        }
        return null
    }

    private fun complete(entry: Int, site: Int, root: Int): Proof? {
        if (!matches(entry + 10, 0x0400, 0x0C02) || word(entry + 16) != 0x4282 ||
            word(entry + 18) and 0xFF00 != 0xD100 || word(entry + 22) != 0x8800 ||
            !matches(entry + 28, 0x0600, 0x0E00) || word(entry + 32) and 0xFF00 != 0x2800 ||
            word(entry + 34) and 0xFF00 != 0xD800 || word(entry + 36) != 0x0080 ||
            !matches(entry + 40, 0x1840, 0x6800, 0x4687, 0)) return null
        if (branch(entry + 18) != site) return null
        val zero = branch(entry + 34) ?: return null
        val cells = (word(entry + 32) and 255) + 1
        val table = entry + 60
        val arms = table + cells * 4
        if (zero < arms || zero > site - 4 || site != zero + 4 || (zero - arms) % 4 != 0 ||
            (zero - arms) / 4 + 1 > MAX_ARMS || word(zero) != 0x2000) return null
        if (literalValue(entry + 38, 1, entry + 56) != 0x08000000L + table) return null
        val count = sanitize(call(entry + 6) ?: return null) ?: return null
        val special = literalValue(entry + 14, 0, entry + 48) ?: return null
        if (special !in 0 until count.toLong()) return null
        val address = literalValue(entry + 20, 0, entry + 52) ?: return null
        if (address and 1L != 0L || address !in 0x02000000L..0x0203FFFEL &&
            address !in 0x03000000L..0x03007FFEL) return null
        val rootPool = (site + 21) and -4
        if (literalSlot(site) != rootPool || (rootPool != site + 18 && word(site + 18) != 0) ||
            word(site + 4) != 0x1889 || word(site + 8) != 0x1809 ||
            word(site + 10) and 0xFF00 != 0x3100 ||
            !matches(site + 12, 0x7808, 0xBC02, 0x4708)) return null
        val first = shift(word(site + 2), 2, 1) ?: return null
        val last = shift(word(site + 6), 1, 1) ?: return null
        val stride = (((1L shl first) + 1) shl last).takeIf { it in 2..256 }?.toInt() ?: return null
        val field = word(site + 10) and 255
        if (field >= stride) return null
        val sanitizer = call(entry + 6) ?: return null
        val opaque = call(entry + 24) ?: return null
        if (sanitizer in entry until rootPool + 4 || opaque in entry until rootPool + 4) return null
        val returnAt = site + 14
        for (at in arms..zero step 4) {
            if (word(at) and 0xFF00 != 0x2000 || word(at + 2) and 0xF800 != 0xE000 ||
                branch(at + 2) != returnAt) return null
        }
        val pools = linkedSetOf(entry + 48, entry + 52, entry + 56, rootPool)
        for (at in table until arms step 4) {
            val value = value(at) ?: return null
            val target = value - 0x08000000L
            if (target !in arms.toLong()..zero.toLong() || (target - arms) % 4 != 0L) return null
            pools += at
        }
        return Proof(stride, count, field, pools)
    }

    private fun value(at: Int): Long? {
        val low = word(at); val high = word(at + 2)
        return if (low < 0 || high < 0) null else low.toLong() or (high.toLong() shl 16)
    }

    private fun literalValue(at: Int, register: Int, slot: Int): Long? =
        if (word(at) and 0xFF00 == (0x4800 or (register shl 8)) && literalSlot(at) == slot) value(slot) else null

    private fun matches(at: Int, vararg values: Int): Boolean = values.indices.all { word(at + it * 2) == values[it] }
    private fun shift(op: Int, source: Int, destination: Int): Int? =
        if (op >= 0 && op and 0xF83F == ((source shl 3) or destination)) (op ushr 6) and 31 else null

    private fun branch(at: Int): Int? {
        val op = word(at)
        val displacement = when {
            op < 0 -> return null
            op and 0xF800 == 0xE000 -> (op and 0x7FF) shl 21 shr 20
            op and 0xF000 == 0xD000 && ((op ushr 8) and 15) < 14 -> (op and 255) shl 24 shr 23
            else -> return null
        }
        return at + 4 + displacement
    }

    private companion object {
        const val MAX_BYTES = 3072
        const val MAX_ARMS = 64
    }
}
