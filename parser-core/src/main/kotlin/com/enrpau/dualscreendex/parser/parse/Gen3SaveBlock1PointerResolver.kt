package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage

/** Resolves the unique RAM global whose compiled consumers load both SaveBlock1 map-location bytes. */
object Gen3SaveBlock1PointerResolver {
    fun resolve(rom: RomImage): Long? {
        val qualified = qualifiedAnchors(rom)
        val best = qualified.values.maxWithOrNull(
            compareBy<SaveBlockAnchor> { minOf(it.locationGroupLoads, it.locationNumberLoads) }
                .thenBy { it.locationGroupLoads + it.locationNumberLoads }
                .thenBy { it.loads },
        ) ?: return null
        return qualified.filterValues { it.score() == best.score() }.keys.singleOrNull()
    }

    internal fun qualifies(rom: RomImage, candidateGlobal: Long): Boolean =
        candidateGlobal in qualifiedAnchors(rom)

    private fun qualifiedAnchors(rom: RomImage): Map<Long, SaveBlockAnchor> {
        val anchors = linkedMapOf<Long, SaveBlockAnchor>()
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
            anchors.getOrPut(global, ::SaveBlockAnchor).also { anchor ->
                anchor.loads++
                anchor.locationGroupLoads += groupLoads
                anchor.locationNumberLoads += numberLoads
            }
            instructionOffset += 2
        }
        return anchors.filterValues { anchor ->
            anchor.loads >= MIN_GLOBAL_LOADS &&
                anchor.locationGroupLoads >= MIN_LOCATION_LOADS &&
                anchor.locationNumberLoads >= MIN_LOCATION_LOADS
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

    private const val EWRAM_START = 0x02000000L
    private const val IWRAM_END = 0x03FFFFFFL
    private const val MAX_POINTER_LOAD_SCAN_BYTES = 10
    private const val MAX_LOCATION_ACCESS_SCAN_BYTES = 12
    private const val MIN_GLOBAL_LOADS = 8
    private const val MIN_LOCATION_LOADS = 4
    private const val SAVE_LOCATION_GROUP_OFFSET = 4
    private const val SAVE_LOCATION_NUMBER_OFFSET = 5

    private data class SaveBlockAnchor(
        var loads: Int = 0,
        var locationGroupLoads: Int = 0,
        var locationNumberLoads: Int = 0,
    ) {
        fun score(): Triple<Int, Int, Int> = Triple(
            minOf(locationGroupLoads, locationNumberLoads),
            locationGroupLoads + locationNumberLoads,
            loads,
        )
    }
}
