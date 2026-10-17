package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.ordinaryAbilityNames
import com.enrpau.dualscreendex.parser.dataset.abilities.putAbilityDescriptions
import com.enrpau.dualscreendex.parser.dataset.abilities.putAbilityNames
import com.enrpau.dualscreendex.parser.dataset.abilities.putGbaPointer
import com.enrpau.dualscreendex.parser.model.TableLayout

/** Fabricated tables and hand-assembled cache flow; no external byte payloads. */
internal class CompiledCachedAbilityFixture(val relocation: Int = 0, private val runtimeBranches: Boolean = false) {
    val bytes = CompiledAbilityTextFixture(relocation, multipliedNames = true, nameWidth = 19).bytes.copyOf(0x20000)
    val core = TableLayout(0x6000 + relocation, 4, 40)
    val names = AbilityNameTableLayout(0x8007 + relocation, 617, 19)
    val descriptions = AbilityDescriptionTableLayout((names.offset + names.count * 19 + 3) and -4L, 617)
    val baseGetter = 0x3000 + relocation
    val innateGetter = 0x3100 + relocation
    val channelGetter = 0x3200 + relocation
    val objectGetter = 0x3300 + relocation
    val objectSetter = 0x3400 + relocation
    val sourceProducer = 0x3500 + relocation
    val summaryProducer = 0x3600 + relocation
    val textConsumer = 0x3800 + relocation
    val summaryGlobal = 0x02001200
    val fields = listOf(24, 26, 28, 34, 36, 38)
    val memberships = listOf(listOf(1, 2, 0, 513, 0, 615), listOf(4, 4, 4, 0, 610, 0), listOf(7, 0, 12, 14, 611, 0))
    val baseLoad: Int
    val innateLoad: Int
    val nameRootSites = mutableListOf<Int>()
    val descriptionRootSites = mutableListOf<Int>()

    init {
        putGbaPointer(bytes, 0x1BC, core.offset)
        putGbaPointer(bytes, 0x1C0, names.offset.toInt())
        putGbaPointer(bytes, 0x1C4, descriptions.offset.toInt())
        putAbilityNames(bytes, names, ordinaryAbilityNames(616))
        bytes.fill(0, (names.offset + names.count * 19).toInt(), descriptions.offset.toInt())
        putAbilityDescriptions(bytes, descriptions, listOf("No native ability.") + List(616) { "Binds a native modifier." }, 0x10000 + relocation)
        bytes.fill(0, core.offset, core.offset + core.count * core.recordSize)
        memberships.forEachIndexed { index, values ->
            val record = core.offset + (index + 1) * 40
            bytes.fill(45, record, record + 6)
            bytes[record + 6] = 1; bytes[record + 7] = 2
            fields.zip(values).forEach { (field, value) -> half(record + field, value) }
            half(record + 30, 35072); half(record + 32, 72)
        }
        baseLoad = coreGetter(baseGetter, 24)
        innateLoad = coreGetter(innateGetter, 34)
        Thumb(channelGetter).apply {
            op(0xB500)
            shift(0, 0, 16); shift(0, 0, 16, true)
            shift(1, 1, 24); shift(3, 1, 24, true)
            shift(2, 2, 24); shift(1, 2, 24, true)
            op(0x2900); val base = op(0)
            op(0x3901); shift(1, 1, 24); shift(1, 1, 24, true)
            bl(innateGetter); val done = op(0)
            branch(base, pc, 0); op(0x1C19); bl(baseGetter)
            branch(done, pc); shift(0, 0, 16); shift(0, 0, 16, true)
            op(0xBC02); op(0x4708); finish()
        }
        // The same persistent-object field is read and written as a halfword.
        Thumb(objectGetter).apply {
            op(0xB500); op(0x395F); shift(1, 1, 1); op(0x304E); add(0, 0, 1)
            op(0x8800); op(0xBC02); op(0x4708); finish()
        }
        Thumb(objectSetter).apply {
            op(0xB500); op(0x395F); shift(1, 1, 1); op(0x304E); add(0, 0, 1)
            op(0x7813); op(0x7852); shift(2, 2, 8); add(3, 3, 2); op(0x8003)
            op(0xBC01); op(0x4700); finish()
        }
        Thumb(sourceProducer).apply {
            op(0xB5F0); op(0xB081); op(0x1C05); op(0x1C0E); op(0x1C17)
            literal(0, 0x08000000 + core.offset); op(0x2400)
            val loop = pc
            op(0x1C28); op(0x1C31); op(0x1C22); bl(channelGetter); op(0x9000)
            op(0x1C38); op(0x1C21); op(0x315F); op(0xAA00); bl(objectSetter)
            op(0x3401); op(0x2C03); val again = op(0); branch(again, loop, 9)
            op(0xB001); op(0xBCF0); op(0xBC01); op(0x4700); finish()
        }
        Thumb(summaryProducer).apply {
            op(0xB570); op(0x1C06); literal(0, summaryGlobal); op(0x6805); op(0x3582); op(0x2400)
            val loop = pc
            op(0x1C21); op(0x315F); op(0x1C30); bl(objectGetter); op(0x8028); op(0x3502)
            op(0x3401); op(0x2C03); val again = op(0); branch(again, loop, 9)
            op(0xBC70); op(0xBC01); op(0x4700); finish()
        }
        Thumb(textConsumer).apply {
            op(0xB570)
            for (cacheOffset in listOf(0x82, 0x84, 0x86, 0x88)) {
                literal(1, summaryGlobal); op(0x6808); op(0x3000 or cacheOffset); op(0x8806)
                shift(5, 6, 2); add(4, 5, 6); shift(4, 4, 2); op(0x1BA4)
                nameRootSites += literal(0, 0x08000000 + names.offset.toInt()); add(1, 4, 0)
                bl(0x1100 + relocation)
                descriptionRootSites += literal(0, 0x08000000 + descriptions.offset.toInt())
                add(0, 5, 0); op(0x6801); bl(0x1100 + relocation)
            }
            op(0xBC70); op(0xBC01); op(0x4700); finish()
        }
    }

    private fun coreGetter(entry: Int, field: Int): Int = Thumb(entry).run {
        op(0xB570); shift(0, 0, 16); shift(5, 0, 16, true)
        shift(1, 1, 24); shift(6, 1, 24, true)
        val runtimeDone = if (runtimeBranches) {
            literal(2, 0x02002000); op(0x7812); op(0x2A00); val defaults = op(0)
            bl(0x1200 + relocation); op(0x2000); val done = op(0)
            branch(defaults, pc, 0); done
        } else null
        op(0x2E02); val invalid = op(0)
        literal(2, 0x08000000 + core.offset); shift(1, 6, 1)
        shift(0, 5, 2); add(0, 0, 5); shift(0, 0, 3)
        add(1, 1, 0); op(0x3200 or field); add(1, 1, 2)
        val load = op(0x8808); val done = op(0)
        branch(invalid, pc, 8); op(0x2000); branch(done, pc)
        runtimeDone?.let { branch(it, pc) }
        op(0xBC70); op(0xBC02); op(0x4708); finish(); load
    }

    fun addUnrelatedProducerLoop() {
        Thumb(baseGetter + 0x80).apply {
            op(0x2400); val loop = pc
            bl(0x1100 + relocation); op(0x8000); bl(0x1200 + relocation)
            op(0x1C04); op(0x2C03); val again = op(0); branch(again, loop, 9)
            op(0x4770); finish()
        }
    }

    fun addLiteralPoolDecoy(validReturn: Boolean = true) {
        val entry = 0x3C00 + relocation
        Thumb(entry).apply {
            op(0xB500); literal(0, 0x02004800); op(0xBC02)
            op(if (validReturn) 0x4708 else 0xDE00); finish()
        }
        // The RAM-address word at entry+8 looks like LDR r0,[PC,#0]. It
        // references the following word only when data is misread as code.
        putGbaPointer(bytes, entry + 12, names.offset.toInt())
    }

    fun half(at: Int, value: Int) {
        bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte()
    }

    private inner class Thumb(var pc: Int) {
        private val literals = mutableListOf<Triple<Int, Int, Int>>()
        fun op(value: Int): Int = pc.also { half(pc, value); pc += 2 }
        fun shift(destination: Int, source: Int, amount: Int, right: Boolean = false) =
            op((if (right) 0x0800 else 0) or (amount shl 6) or (source shl 3) or destination)
        fun add(destination: Int, left: Int, right: Int) = op(0x1800 or (right shl 6) or (left shl 3) or destination)
        fun literal(register: Int, value: Int): Int = op(0).also { literals += Triple(it, register, value) }
        fun bl(target: Int) {
            val delta = target - pc - 4
            require(delta % 2 == 0 && delta in -0x400000 until 0x400000)
            op(0xF000 or ((delta shr 12) and 2047)); op(0xF800 or ((delta shr 1) and 2047))
        }
        fun branch(site: Int, target: Int, condition: Int? = null) {
            val delta = (target - site - 4) / 2
            require(delta in (if (condition == null) -1024..1023 else -128..127))
            half(site, if (condition == null) 0xE000 or (delta and 2047) else 0xD000 or (condition shl 8) or (delta and 255))
        }
        fun finish() {
            pc = (pc + 3) and -4
            literals.forEach { (site, register, value) ->
                val distance = pc - ((site + 4) and -4)
                require(distance in 0..1020 && distance % 4 == 0)
                half(site, 0x4800 or (register shl 8) or (distance / 4))
                repeat(4) { bytes[pc + it] = (value ushr (it * 8)).toByte() }; pc += 4
            }
        }
    }
}
