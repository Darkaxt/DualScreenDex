package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetSelectorEvidence

/**
 * Recognizes one deliberately narrow Thumb selector shape. This is not a control-flow interpreter:
 * it requires a literal SaveBlock pointer, a literal byte offset, one one-hot mask, a direct
 * conditional split, and one already-validated learnset root referenced in each bounded arm.
 */
object Gen3LearnsetSelectorExtractor {
    fun extract(rom: RomImage, validatedTableOffsets: Set<Int>): Gen3LearnsetSelectorEvidence? {
        if (validatedTableOffsets.size < 2) return null
        val qualificationCache = object : LinkedHashMap<Long, Boolean>(MAX_QUALIFICATION_CACHE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>?): Boolean =
                size > MAX_QUALIFICATION_CACHE
        }
        val qualifiedGlobals = linkedSetOf<Long>()
        var retainedMatch: Gen3LearnsetSelectorEvidence? = null
        var globalLoad = 0
        while (globalLoad <= rom.size - 8) {
            val globalLiteral = literalValue(rom, globalLoad)
            if (globalLiteral !in EWRAM_START..IWRAM_END) {
                globalLoad += 2
                continue
            }
            val globalRegister = literalDestination(rom.u16le(globalLoad)) ?: run {
                globalLoad += 2
                continue
            }
            val pointerLoad = rom.u16le(globalLoad + 2)
            if (!isWordLoadAtZero(pointerLoad, globalRegister)) {
                globalLoad += 2
                continue
            }
            val pointerRegister = pointerLoad and 0x7
            val offsetLoad = globalLoad + 4
            val fieldOffset = literalValue(rom, offsetLoad)
            val offsetRegister = literalDestination(rom.u16le(offsetLoad))
            if (fieldOffset !in 0 until MAX_SAVE_BLOCK1_BYTES || offsetRegister == null) {
                globalLoad += 2
                continue
            }
            val byteLoad = rom.u16le(globalLoad + 6)
            if (!isRegisterByteLoad(byteLoad, pointerRegister, offsetRegister)) {
                globalLoad += 2
                continue
            }
            val valueRegister = byteLoad and 0x7
            val mask = findMask(rom, globalLoad, valueRegister) ?: run {
                globalLoad += 2
                continue
            }
            val branch = findTestBranch(rom, globalLoad + 8, valueRegister, mask.first) ?: run {
                globalLoad += 2
                continue
            }
            val split = splitArms(rom, branch.first, branch.second) ?: run {
                globalLoad += 2
                continue
            }
            val zeroRoots = referencedValidatedRoots(
                rom,
                split.first,
                minOf(split.first + MAX_ARM_SCAN_BYTES, split.second, rom.size),
                validatedTableOffsets,
            )
            val nonZeroRoots = referencedValidatedRoots(
                rom,
                split.second,
                minOf(split.second + MAX_ARM_SCAN_BYTES, rom.size),
                validatedTableOffsets,
            )
            if (zeroRoots.size == 1 && nonZeroRoots.size == 1 && zeroRoots.single() != nonZeroRoots.single()) {
                val qualified = qualificationCache[globalLiteral] ?: isSaveBlock1Global(rom, globalLiteral).also {
                    qualificationCache[globalLiteral] = it
                }
                if (!qualified) {
                    globalLoad += 2
                    continue
                }
                if (globalLiteral !in qualifiedGlobals && qualifiedGlobals.size == MAX_GLOBAL_ANCHORS) return null
                qualifiedGlobals += globalLiteral
                val candidate = Gen3LearnsetSelectorEvidence(
                    saveBlock1ByteOffset = fieldOffset.toInt(),
                    mask = mask.second,
                    zeroTableOffset = zeroRoots.single(),
                    nonZeroTableOffset = nonZeroRoots.single(),
                    codeOffset = globalLoad,
                )
                val retained = retainedMatch
                if (retained != null && retained != candidate) return null
                retainedMatch = candidate
            }
            globalLoad += 2
        }
        return retainedMatch
    }

    private fun isSaveBlock1Global(rom: RomImage, candidateGlobal: Long): Boolean {
        val anchor = SaveBlockAnchor()
        forEachRamGlobalConsumer(rom) { global, groupLoads, numberLoads ->
            if (global != candidateGlobal) return@forEachRamGlobalConsumer true
            anchor.loads++
            anchor.locationGroupLoads += groupLoads
            anchor.locationNumberLoads += numberLoads
            !(anchor.loads >= MIN_GLOBAL_LOADS &&
                anchor.locationGroupLoads >= MIN_LOCATION_LOADS &&
                anchor.locationNumberLoads >= MIN_LOCATION_LOADS)
        }
        return anchor.loads >= MIN_GLOBAL_LOADS &&
            anchor.locationGroupLoads >= MIN_LOCATION_LOADS &&
            anchor.locationNumberLoads >= MIN_LOCATION_LOADS
    }

    private fun forEachRamGlobalConsumer(
        rom: RomImage,
        consumer: (global: Long, groupLoads: Int, numberLoads: Int) -> Boolean,
    ): Boolean {
        var instructionOffset = 0
        while (instructionOffset <= rom.size - 8) {
            val global = literalValue(rom, instructionOffset)
            if (global !in EWRAM_START..IWRAM_END) {
                instructionOffset += 2
                continue
            }
            val literalRegister = literalDestination(rom.u16le(instructionOffset)) ?: run {
                instructionOffset += 2
                continue
            }
            var pointerLoadOffset = instructionOffset + 2
            var pointerRegister: Int? = null
            while (pointerLoadOffset <= minOf(instructionOffset + MAX_POINTER_LOAD_SCAN_BYTES, rom.size - 2)) {
                val instruction = rom.u16le(pointerLoadOffset)
                if (isWordLoadAtZero(instruction, literalRegister)) {
                    pointerRegister = instruction and 0x7
                    break
                }
                pointerLoadOffset += 2
            }
            if (pointerRegister == null) {
                instructionOffset += 2
                continue
            }
            var groupLoads = 0
            var numberLoads = 0
            var accessOffset = pointerLoadOffset + 2
            while (accessOffset <= minOf(pointerLoadOffset + MAX_LOCATION_ACCESS_SCAN_BYTES, rom.size - 2)) {
                val instruction = rom.u16le(accessOffset)
                if (instruction and 0xF800 == 0x7800 && (instruction ushr 3) and 0x7 == pointerRegister) {
                    when ((instruction ushr 6) and 0x1F) {
                        SAVE_LOCATION_GROUP_OFFSET -> groupLoads++
                        SAVE_LOCATION_NUMBER_OFFSET -> numberLoads++
                    }
                }
                accessOffset += 2
            }
            if (!consumer(global, groupLoads, numberLoads)) return false
            instructionOffset += 2
        }
        return true
    }

    private fun findMask(rom: RomImage, globalLoad: Int, valueRegister: Int): Pair<Int, Int>? {
        val start = maxOf(0, globalLoad - MAX_MASK_LOOKBACK_BYTES)
        var offset = globalLoad - 2
        while (offset >= start) {
            val instruction = rom.u16le(offset)
            if (instruction and 0xF800 == 0x2000) {
                val register = (instruction ushr 8) and 0x7
                val value = instruction and 0xFF
                if (value in 1..0x80 && value and (value - 1) == 0 && register != valueRegister) {
                    return register to value
                }
            }
            offset -= 2
        }
        return null
    }

    private fun findTestBranch(
        rom: RomImage,
        start: Int,
        valueRegister: Int,
        maskRegister: Int,
    ): Pair<Int, Int>? {
        val end = minOf(start + MAX_TEST_SCAN_BYTES, rom.size - 4)
        var offset = start
        while (offset <= end) {
            val instruction = rom.u16le(offset)
            if (instruction and 0xFFC0 == 0x4200) {
                val left = instruction and 0x7
                val right = (instruction ushr 3) and 0x7
                if (setOf(left, right) == setOf(valueRegister, maskRegister)) {
                    val branch = rom.u16le(offset + 2)
                    val condition = (branch ushr 8) and 0xF
                    if (branch and 0xF000 == 0xD000 && condition in 0..1) return (offset + 2) to condition
                }
            }
            offset += 2
        }
        return null
    }

    private fun splitArms(rom: RomImage, conditionalBranch: Int, condition: Int): Pair<Int, Int>? {
        val branchTarget = conditionalTarget(rom.u16le(conditionalBranch), conditionalBranch)
        val jumpOffset = conditionalBranch + 2
        if (jumpOffset > rom.size - 2) return null
        val jump = rom.u16le(jumpOffset)
        if (jump and 0xF800 != 0xE000) return null
        val jumpTarget = unconditionalTarget(jump, jumpOffset)
        if (branchTarget !in 0 until rom.size || jumpTarget !in 0 until rom.size) return null
        return if (condition == CONDITION_EQ) branchTarget to jumpTarget else jumpTarget to branchTarget
    }

    private fun referencedValidatedRoots(
        rom: RomImage,
        start: Int,
        endExclusive: Int,
        validatedTableOffsets: Set<Int>,
    ): Set<Int> = buildSet {
        var offset = start
        while (offset + 2 <= endExclusive) {
            val raw = literalValue(rom, offset)
            val target = (raw - GBA_ROM_BASE).takeIf { it in 0..rom.size.toLong() }?.toInt()
            if (target in validatedTableOffsets) add(requireNotNull(target))
            offset += 2
        }
    }

    private fun literalDestination(instruction: Int): Int? =
        if (instruction and 0xF800 == 0x4800) (instruction ushr 8) and 0x7 else null

    private fun literalValue(rom: RomImage, instructionOffset: Int): Long {
        if (instructionOffset !in 0..rom.size - 2) return -1
        val instruction = rom.u16le(instructionOffset)
        if (literalDestination(instruction) == null) return -1
        val pc = (instructionOffset + 4) and -4
        val literalOffset = pc + (instruction and 0xFF) * 4
        return if (literalOffset >= 0 && literalOffset.toLong() + 4 <= rom.size.toLong()) {
            rom.u32le(literalOffset)
        } else {
            -1
        }
    }

    private fun isWordLoadAtZero(instruction: Int, baseRegister: Int): Boolean =
        instruction and 0xF800 == 0x6800 &&
            (instruction ushr 6) and 0x1F == 0 &&
            (instruction ushr 3) and 0x7 == baseRegister

    private fun isRegisterByteLoad(instruction: Int, baseRegister: Int, offsetRegister: Int): Boolean =
        instruction and 0xFE00 == 0x5C00 &&
            (instruction ushr 3) and 0x7 == baseRegister &&
            (instruction ushr 6) and 0x7 == offsetRegister

    private fun conditionalTarget(instruction: Int, offset: Int): Int {
        val immediate = (instruction and 0xFF).toByte().toInt() shl 1
        return offset + 4 + immediate
    }

    private fun unconditionalTarget(instruction: Int, offset: Int): Int {
        var immediate = (instruction and 0x7FF) shl 1
        if (immediate and 0x800 != 0) immediate = immediate or -0x1000
        return offset + 4 + immediate
    }

    private const val GBA_ROM_BASE = 0x08000000L
    private const val EWRAM_START = 0x02000000L
    private const val IWRAM_END = 0x03FFFFFFL
    private const val MAX_SAVE_BLOCK1_BYTES = 0x4000
    private const val MAX_GLOBAL_ANCHORS = 256
    private const val MAX_QUALIFICATION_CACHE = 256
    private const val MAX_POINTER_LOAD_SCAN_BYTES = 10
    private const val MAX_LOCATION_ACCESS_SCAN_BYTES = 12
    private const val MIN_GLOBAL_LOADS = 8
    private const val MIN_LOCATION_LOADS = 4
    private const val SAVE_LOCATION_GROUP_OFFSET = 4
    private const val SAVE_LOCATION_NUMBER_OFFSET = 5
    private const val MAX_MASK_LOOKBACK_BYTES = 16
    private const val MAX_TEST_SCAN_BYTES = 32
    private const val MAX_ARM_SCAN_BYTES = 128
    private const val CONDITION_EQ = 0

    private data class SaveBlockAnchor(
        var loads: Int = 0,
        var locationGroupLoads: Int = 0,
        var locationNumberLoads: Int = 0,
    )
}
