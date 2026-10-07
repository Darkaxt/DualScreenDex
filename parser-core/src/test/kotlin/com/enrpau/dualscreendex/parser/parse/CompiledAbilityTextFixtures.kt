package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.ordinaryAbilityNames
import com.enrpau.dualscreendex.parser.dataset.abilities.putAbilityDescriptions
import com.enrpau.dualscreendex.parser.dataset.abilities.putAbilityNames
import com.enrpau.dualscreendex.parser.dataset.abilities.putGbaPointer
import com.enrpau.dualscreendex.parser.model.TableLayout

/** Hand-assembled relocated code and fabricated text only; no external inputs or ROM payloads. */
internal class CompiledAbilityTextFixture(
    val relocation: Int = 0,
    val fieldOffset: Int = 24,
    getterCycle: Boolean = false,
    cachedFallback: Boolean = false,
    coreOffset: Int = 0x6000 + relocation,
    signedComparisonTrap: Boolean = false,
    emptyGetterPop: Boolean = false,
    private val localArguments: Boolean = false,
) {
    val bytes = ByteArray(0x18000) { 0xFF.toByte() }
    val core = TableLayout(coreOffset, 4, 36)
    val names = AbilityNameTableLayout(0x8007 + relocation, 20, 17)
    val descriptions = AbilityDescriptionTableLayout(
        ((names.offset.toInt() + names.count.toInt() * names.stride + 3) and -4), 20,
    )
    val getter = 0x1800 + relocation
    val nameEntry = 0x2200 + relocation
    val descriptionEntry = 0x2300 + relocation
    val falseNames = AbilityNameTableLayout(0xC000 + relocation, 13, 14)
    val getterLoad: Int
    val getterLoads = mutableListOf<Int>()
    val nameRootLoad: Int
    val descriptionIndexShift: Int
    val argumentSetupSites = mutableListOf<Int>()
    val accessorReturnSites = mutableListOf<Int>()
    val summaryRootLoadSites = mutableListOf<Int>()

    init {
        putAbilityNames(bytes, names, ordinaryAbilityNames(19))
        putGbaPointer(bytes, 0x1BC, core.offset)
        putGbaPointer(bytes, 0x1C0, names.offset.toInt())
        putGbaPointer(bytes, 0x1C4, descriptions.offset.toInt())
        putAbilityNames(bytes, falseNames, ordinaryAbilityNames(12))
        putAbilityDescriptions(bytes, descriptions,
            listOf("No native ability.") + List(19) { "Binds a native modifier." }, 0x10000 + relocation)
        bytes.fill(0, names.offset.toInt() + names.count.toInt() * names.stride, descriptions.offset.toInt())
        bytes.fill(0, core.offset, core.offset + core.count * core.recordSize)
        listOf(listOf(1, 2, 0), listOf(4, 4, 4), listOf(7, 0, 12)).forEachIndexed { index, slots ->
            val record = core.offset + (index + 1) * core.recordSize
            bytes.fill(45, record, record + 6)
            bytes[record + 6] = 1; bytes[record + 7] = 2
            slots.forEachIndexed { slot, value -> half(record + fieldOffset + slot * 2, value) }
        }
        val code = Thumb(getter)
        if (cachedFallback) {
            getterLoad = cachedGetter(code)
        } else {
            code.op(0xB530)
            if (emptyGetterPop) code.op(0xBC00)
            code.shift(0, 0, 16); code.shift(5, 0, 16, right = true)
            code.shift(1, 1, 24); code.shift(4, 1, 24, right = true)
            code.op(0x2C02)
            val invalidBranch = code.op(0)
            code.literal(2, core.offset)
            code.shift(1, 4, 1)
            code.shift(0, 5, 3); code.add(0, 0, 5); code.shift(0, 0, 2)
            code.add(1, 1, 0); code.op(0x3200 or fieldOffset); code.add(1, 1, 2)
            getterLoad = code.op(0x8808)
            if (signedComparisonTrap) {
                code.literalValue(3, -1); code.op(0x2B00)
                val positive = code.op(0)
                code.op(0xFFFF)
                code.branch(positive, code.pc, 12)
            }
            val cycleBranch = if (getterCycle) { code.op(0x2800); code.op(0) } else null
            val doneBranch = code.op(0)
            val invalid = code.pc
            code.op(0x2000)
            val done = code.pc
            code.op(0xBC30); code.op(0xBC02); code.op(0x4708)
            if (cycleBranch != null) {
                val spin = code.op(0xE7FE)
                code.branch(cycleBranch, spin, 0)
            }
            code.branch(invalidBranch, invalid, 8); code.branch(doneBranch, done)
            code.finish()
        }

        val name = accessor(nameEntry)
        name.shift(1, 4, 4); name.add(1, 1, 4)
        nameRootLoad = name.literal(2, names.offset.toInt())
        name.add(1, 1, 2); name.bl(0x1100 + relocation); name.returnFromAccessor(localArguments); name.finish()
        val description = accessor(descriptionEntry)
        description.literal(1, descriptions.offset.toInt())
        descriptionIndexShift = description.shift(4, 4, 2)
        description.add(4, 4, 1); description.op(0x6821)
        description.bl(0x1100 + relocation); description.returnFromAccessor(localArguments); description.finish()
        half(0x1000 + relocation, 0x2000); half(0x1002 + relocation, 0x4770)
        half(0x1100 + relocation, 0x4770)

        // A coherent fixed-stride name consumer, but not a species-getter/paired-description owner.
        val decoy = Thumb(0x2600 + relocation)
        decoy.op(0xB510); decoy.op(0x200E); decoy.op(0x4348)
        decoy.literal(2, falseNames.offset.toInt()); decoy.add(1, 0, 2)
        decoy.returnFromAccessor(); decoy.finish()
    }

    private fun cachedGetter(code: Thumb): Int {
        code.op(0xB530)
        code.shift(0, 0, 16); code.shift(5, 0, 16, right = true)
        code.shift(1, 1, 24); code.shift(4, 1, 24, right = true)
        code.literalValue(2, 0x02002000); code.op(0x2000); code.op(0x8010)
        code.op(0x2C02)
        val invalid = code.op(0)
        val firstLoad = loadGetterSlot(code)
        fun saveAndBranch(): Int {
            code.literalValue(1, 0x02002000); code.op(0x8008); code.op(0x2800)
            return code.op(0)
        }
        val primaryDone = saveAndBranch()
        code.branch(invalid, code.pc, 8)
        code.op(0x2C01)
        val ordinaryFallback = code.op(0)
        code.op(0x2402); loadGetterSlot(code)
        val hiddenDone = saveAndBranch()
        val fallback = code.pc
        code.op(0x2400)
        val loop = code.pc
        loadGetterSlot(code)
        val loopDone = saveAndBranch()
        code.op(0x3401); code.op(0x2C02)
        val again = code.op(0)
        code.branch(again, loop, 9)
        val done = code.pc
        code.literalValue(1, 0x02002000); code.op(0x8808)
        code.op(0xBC30); code.op(0xBC02); code.op(0x4708)
        code.branch(ordinaryFallback, fallback, 9)
        listOf(primaryDone, hiddenDone, loopDone).forEach { code.branch(it, done, 1) }
        code.finish()
        return firstLoad
    }

    private fun loadGetterSlot(code: Thumb): Int {
        code.literal(2, core.offset)
        code.shift(1, 4, 1)
        code.shift(0, 5, 3); code.add(0, 0, 5); code.shift(0, 0, 2)
        code.add(1, 1, 0); code.op(0x3200 or fieldOffset); code.add(1, 1, 2)
        return code.op(0x8808).also { getterLoads += it }
    }

    fun addNameConsumer(width: Int = 17) {
        val code = Thumb(0x2800 + relocation)
        code.op(0xB510)
        if (width == 17) { code.shift(1, 0, 4); code.add(1, 1, 0) }
        else { code.op(0x2000 or width); code.op(0x4341) }
        code.literal(2, names.offset.toInt()); code.add(1, 1, 2)
        code.returnFromAccessor(); code.finish()
    }

    private fun accessor(entry: Int): Thumb = Thumb(entry).apply {
        op(0xB510)
        if (localArguments) op(0xB081)
        summaryRootLoadSites += literalValue(0, 0x02001000)
        op(0x6801); op(0x1C08); op(0x3050); op(0x8800)
        op(0x3154); op(0x7809)
        bl(getter)
        op(0x1C04); shift(4, 4, 16); shift(4, 4, 16, right = true)
        argumentSetupSites += op(0x2000)
        if (localArguments) op(0x9000)
        op(0x2102); bl(0x1000 + relocation)
    }

    fun half(offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }

    private inner class Thumb(start: Int) {
        var pc = start
        private val literals = mutableListOf<Triple<Int, Int, Int>>()
        fun op(value: Int): Int = pc.also { half(pc, value); pc += 2 }
        fun shift(destination: Int, source: Int, amount: Int, right: Boolean = false): Int =
            op((if (right) 0x0800 else 0) or (amount shl 6) or (source shl 3) or destination)
        fun add(destination: Int, left: Int, right: Int) = op(0x1800 or (right shl 6) or (left shl 3) or destination)
        fun literal(register: Int, root: Int): Int = literalValue(register, 0x08000000 + root)
        fun literalValue(register: Int, value: Int): Int = op(0).also { literals += Triple(it, register, value) }
        fun bl(target: Int) {
            val delta = target - (pc + 4)
            require(delta % 2 == 0 && delta in -0x400000 until 0x400000)
            op(0xF000 or ((delta shr 12) and 0x7FF)); op(0xF800 or ((delta shr 1) and 0x7FF))
        }
        fun branch(site: Int, target: Int, condition: Int? = null) {
            val delta = (target - site - 4) / 2
            if (condition == null) {
                require(delta in -1024..1023); half(site, 0xE000 or (delta and 0x7FF))
            } else {
                require(delta in -128..127); half(site, 0xD000 or (condition shl 8) or (delta and 0xFF))
            }
        }
        fun returnFromAccessor(restoreLocals: Boolean = false) {
            if (restoreLocals) op(0xB001)
            op(0xBC10); op(0xBC02); accessorReturnSites += op(0x4708)
        }
        fun finish() {
            pc = (pc + 3) and -4
            literals.forEach { (site, register, value) ->
                val delta = pc - ((site + 4) and -4)
                require(delta in 0..1020 && delta % 4 == 0)
                half(site, 0x4800 or (register shl 8) or (delta / 4))
                repeat(4) { index -> bytes[pc + index] = (value ushr (index * 8)).toByte() }
                pc += 4
            }
        }
    }
}
