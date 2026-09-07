package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.GbItemNameAuthority
import com.enrpau.dualscreendex.parser.model.Platform

/** One original-phase instruction proof. No payload/string nomination or materializer discovery. */
internal class Gen1CompiledItemNameResolver(private val session: RomAnalysisSession) {
    private val rom get() = session.rom
    private var frozen: GbItemNameAuthority? = null
    private var semanticBytes = 0

    @Synchronized
    fun original(): GbItemNameAuthority {
        session.cancellation.throwIfCancellationRequested()
        frozen?.let { return it }
        val result = try {
            if (session.header.platform !in setOf(Platform.GB, Platform.GBC)) invalid()
            val end = minOf(rom.size, 0x4000)
            if (end > session.limits.maxProbeWorkPerDataset) invalid()
            var observed = 0
            var candidate = -1
            for (at in 0..(end - 10)) {
                session.cancellation.throwIfCancellationRequested()
                if (rom.u8(at) == 0xe5 && rom.u8(at + 1) == 0xc5 && rom.u8(at + 2) == 0xfa &&
                    rom.u8(at + 5) == 0xfe && rom.u8(at + 7) == 0x30 && rom.u8(at + 9) == 0xea) {
                    observed++
                    candidate = at
                }
            }
            if (observed != 1 || observed > session.limits.maxCandidatesPerDataset) invalid()
            prove(candidate)
        } catch (_: InvalidContract) {
            GbItemNameAuthority.Unavailable("incomplete, competing or over-budget original Gen I naming consumer")
        }
        session.cancellation.throwIfCancellationRequested()
        frozen = result
        return result
    }

    private fun prove(wrapper: Int): GbItemNameAuthority.Available {
        val w = Cursor(wrapper)
        w.expect(0xe5, 0xc5, 0xfa); val objectId = w.word()
        w.expect(0xfe); val threshold = w.byte()
        val machineBranch = w.relative(0x30)
        w.expect(0xea); val index = w.word()
        w.expect(0x3e); val selector = w.byte()
        w.expect(0xea); val selectorRam = w.word()
        w.expect(0x3e); val bank = w.byte()
        w.expect(0xea); val bankRam = w.word()
        w.expect(0xcd); val packed = w.word()
        val finishBranch = w.relative(0x18)
        check(machineBranch == w.pc)
        w.expect(0xcd); val machine = w.word()
        check(finishBranch == w.pc)
        w.expect(0x11); val destination = w.word()
        w.expect(0xc1, 0xe1, 0xc9)
        val inputSlots = listOf(objectId, index, selectorRam, bankRam)
        check(inputSlots.distinct().size == 4 && inputSlots.all { it in 0xc000..0xdfff })
        check(selector in 2..128 && bank > 0 && threshold in 1..254)

        val p = Cursor(packed)
        p.expect(0xfa); check(p.word() == index)
        p.expect(0xea); check(p.word() == objectId)
        p.expect(0xfe, threshold, 0xd2); check(p.word() == machine)
        p.expect(0xf0); val mirror = p.byte()
        p.expect(0xf5, 0xe5, 0xc5, 0xd5, 0xfa); check(p.word() == selectorRam)
        p.expect(0x3d); val directoryBranch = p.relative(0x20)
        // Unselected monster arm is structurally bounded; never follow its CALL.
        p.expect(0xcd); p.word()
        p.expect(0x21); p.word()
        p.expect(0x19, 0x5d, 0x54); val monsterFinish = p.relative(0x18)
        check(directoryBranch == p.pc)
        p.expect(0xfa); check(p.word() == bankRam)
        val bankHelper: Int?
        val mapper: Int
        when (p.byte()) {
            0xe0 -> {
                check(p.byte() == mirror); p.expect(0xea); mapper = p.word(); bankHelper = null
            }
            0xcd -> {
                bankHelper = p.word()
                val b = Cursor(bankHelper)
                b.expect(0xe0, mirror, 0xea); mapper = b.word(); b.expect(0xc9)
            }
            else -> invalid()
        }
        check(mapper in 0x2000..0x3fff)
        p.expect(0xfa); check(p.word() == selectorRam)
        p.expect(0x3d, 0x87, 0x16, 0, 0x5f, 0x30, 1, 0x14, 0x21)
        val directory = p.word()
        p.expect(0x19, 0x2a, 0xe0); val low = p.byte()
        p.expect(0x7e, 0xe0); val high = p.byte()
        check(listOf(low, high, mirror).distinct().size == 3 && listOf(low, high, mirror).all { it in 0x80..0xfe })
        p.expect(0xf0, high, 0x67, 0xf0, low, 0x6f, 0xfa); check(p.word() == index)
        p.expect(0x47, 0x0e, 0, 0x54, 0x5d, 0x2a, 0xfe); val terminator = p.byte()
        p.expect(0x20, 0xfb, 0x0c, 0x78, 0xb9, 0x20, 0xf4, 0x62, 0x6b, 0x11)
        check(p.word() == destination)
        p.expect(0x01); val copyBytes = p.word()
        check(copyBytes in 1..64)
        p.expect(0xcd); val copier = p.word()
        check(monsterFinish == p.pc)
        p.expect(0x7b, 0xea); val returnLow = p.word()
        p.expect(0x7a, 0xea); val returnHigh = p.word()
        p.expect(0xd1, 0xc1, 0xe1, 0xf1)
        if (bankHelper == null) { p.expect(0xe0, mirror, 0xea); check(p.word() == mapper) }
        else { p.expect(0xcd); check(p.word() == bankHelper) }
        p.expect(0xc9)
        proveCopier(copier)

        val m = Cursor(machine)
        m.expect(0xe5, 0xd5, 0xc5, 0xfa); check(m.word() == objectId)
        m.expect(0xf5, 0xfe); val split = m.byte()
        val upperBranch = m.relative(0x30)
        m.expect(0xc6); val adjustment = m.byte()
        m.expect(0xea); check(m.word() == objectId)
        m.expect(0x21); val lowerPrefix = m.word()
        m.expect(0x01); val lowerBytes = m.word()
        val copyBranch = m.relative(0x18)
        check(upperBranch == m.pc)
        m.expect(0x21); val upperPrefix = m.word()
        m.expect(0x01); val upperBytes = m.word()
        check(copyBranch == m.pc)
        m.expect(0x11); check(m.word() == destination)
        m.expect(0xcd); check(m.word() == copier)
        m.expect(0xfa); check(m.word() == objectId)
        m.expect(0xd6); val subtract = m.byte()
        m.expect(0x06); val digits = m.byte()
        m.expect(0xd6, 10, 0x38, 3, 0x04, 0x18, 0xf9, 0xc6, 10, 0xf5, 0x78, 0x12, 0x13, 0xf1, 0x06, digits,
            0x80, 0x12, 0x13, 0x3e, terminator, 0x12, 0xf1, 0xea)
        check(m.word() == objectId)
        m.expect(0xc1, 0xd1, 0xe1, 0xc9)
        check(split > threshold && adjustment == split - threshold && subtract == split - 1)
        check(255 - subtract <= 99 && digits + 9 <= 255)
        check(lowerBytes in 1..61 && upperBytes in 1..61)
        check(lowerPrefix >= 0 && lowerPrefix.toLong() + lowerBytes <= minOf(rom.size, 0x4000))
        check(upperPrefix >= 0 && upperPrefix.toLong() + upperBytes <= minOf(rom.size, 0x4000))
        val outputEnd = destination + maxOf(copyBytes, lowerBytes + 3, upperBytes + 3)
        check(destination in 0xc000..0xdfff && outputEnd <= 0xe000)
        check(inputSlots.none { it in destination until outputEnd })
        check(returnLow != returnHigh && listOf(returnLow, returnHigh).all {
            it in 0xc000..0xdfff && it !in inputSlots && it !in destination until outputEnd
        })
        val pointer = Cursor(directory + (selector - 1) * 2).word()
        val root = rom.gbBankAddress(bank, pointer) ?: invalid()
        val end = minOf(rom.size.toLong(), (bank.toLong() + 1) * 0x4000).toInt()
        check(root < end)
        return GbItemNameAuthority.Available(root, end, copyBytes, threshold, split, adjustment,
            subtract, digits, terminator, lowerPrefix, lowerBytes, upperPrefix, upperBytes)
    }

    private fun proveCopier(entry: Int) {
        val c = Cursor(entry)
        when (c.byte()) {
            0x2a -> c.expect(0x12, 0x13, 0x0b, 0x79, 0xb0, 0x20, 0xf8, 0xc9)
            0x78 -> {
                // All admitted counts have B=0. This branch enters the byte loop directly;
                // its RET returns to the naming caller, not through the unused B>0 internal CALL.
                c.expect(0xa7, 0x28, 0x0c, 0x79, 0xa7, 0x28, 1, 0x04, 0xcd)
                check(c.word() == entry + 16)
                c.expect(0x05, 0x20, 0xfa, 0xc9, 0x2a, 0x12, 0x13, 0x0d, 0x20, 0xfa, 0xc9)
            }
            else -> invalid()
        }
    }

    private inner class Cursor(var pc: Int) {
        fun byte(): Int {
            session.cancellation.throwIfCancellationRequested()
            if (pc !in 0 until minOf(rom.size, 0x4000) || ++semanticBytes > minOf(16384L, session.limits.maxDatasetExtentBytes)) invalid()
            return rom.u8(pc++)
        }
        fun word(): Int = byte() or (byte() shl 8)
        fun expect(vararg values: Int) { values.forEach { check(byte() == it) } }
        fun relative(op: Int): Int { expect(op); val delta = byte().toByte().toInt(); return pc + delta }
    }
    private class InvalidContract : RuntimeException()
    private fun invalid(): Nothing = throw InvalidContract()
    private fun check(value: Boolean) { if (!value) invalid() }
}
