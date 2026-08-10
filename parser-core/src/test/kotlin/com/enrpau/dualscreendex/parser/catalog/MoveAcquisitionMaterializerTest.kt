package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveAcquisitionMaterializerTest {
    @Test
    fun decodesEmbeddedGenOneMachineFlagsUsingValidatedRomMoveList() {
        val bytes = ByteArray(1024)
        val stats = 0x100
        val moves = 0x300
        repeat(55) { bytes[moves + it] = (it + 1).toByte() }
        repeat(56) { bytes[0x200 + it] = (it + 1).toByte() } // unrelated longer unique byte run
        repeat(4) { species ->
            repeat(7) { byte -> bytes[stats + species * 28 + 20 + byte] = (0x55 xor species).toByte() }
        }
        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(1, Platform.GB, 4, 80, TableLayout(stats, 4, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertTrue(result.acquisitionsBySpecies.getValue(1).any { it.method == MoveAcquisitionMethod.MACHINE })
    }

    @Test
    fun decodesGenTwoMachinesTutorsAndBankLocalEggPointers() {
        val bytes = ByteArray(0xC000)
        val stats = 0x100
        repeat(4) { species ->
            val row = stats + species * 32 + 24
            repeat(7) { bytes[row + it] = (if (species % 2 == 0) 0x55 else 0xAA).toByte() }
            bytes[row + 7] = (if (species % 2 == 0) 0x07 else 0x02).toByte()
        }
        val machineTable = 0x300
        repeat(60) { bytes[machineTable + it] = (it + 1).toByte() }
        bytes[machineTable + 60] = 0

        val eggPointers = 0x8100
        listOf(0x4200, 0x4203, 0x4200, 0x4205).forEachIndexed { index, pointer ->
            putU16(bytes, eggPointers + index * 2, pointer)
        }
        bytes[0x8200] = 3
        bytes[0x8201] = 5
        bytes[0x8202] = 0xFF.toByte()
        bytes[0x8203] = 7
        bytes[0x8204] = 0xFF.toByte()
        bytes[0x8205] = 9
        bytes[0x8206] = 10
        bytes[0x8207] = 0xFF.toByte()

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(2, Platform.GBC, 4, 100, TableLayout(stats, 4, 32)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.EGG).compatible)
        assertEquals(listOf(3, 5), result.acquisitionsBySpecies.getValue(1).filter { it.method == MoveAcquisitionMethod.EGG }.map { it.moveId })
        assertEquals(58, result.acquisitionsBySpecies.getValue(1).first { it.method == MoveAcquisitionMethod.TUTOR }.moveId)
    }

    @Test
    fun treatsGoldAndSilverTutorsAsNotApplicableWhileDecodingMachines() {
        val bytes = ByteArray(2048)
        val stats = 0x100
        repeat(4) { species ->
            val row = stats + species * 32 + 24
            repeat(7) { bytes[row + it] = (if (species % 2 == 0) 0x55 else 0xAA).toByte() }
        }
        repeat(57) { bytes[0x500 + it] = (it + 1).toByte() }
        bytes[0x500 + 57] = 0

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(2, Platform.GBC, 4, 100, TableLayout(stats, 4, 32), EngineFamily.GOLD_SILVER),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).status)
    }

    @Test
    fun decodesGenThreeEggSentinelList() {
        val bytes = ByteArray(1024)
        var cursor = 0x200
        listOf(20_001, 3, 5, 20_002, 7, 9, 0xFFFF).forEach { value ->
            putU16(bytes, cursor, value)
            cursor += 2
        }
        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 3, 20, TableLayout(0x100, 3, 28)),
        )

        assertEquals(
            listOf(
                MoveAcquisition(3, MoveAcquisitionMethod.EGG),
                MoveAcquisition(5, MoveAcquisitionMethod.EGG),
            ),
            result.acquisitionsBySpecies.getValue(1),
        )
    }

    @Test
    fun usesTheFirstGenThreeEggGroupWhenASpeciesMarkerIsRepeated() {
        val bytes = ByteArray(1024)
        var cursor = 0x200
        listOf(20_001, 3, 5, 20_001, 7, 20_002, 9, 0xFFFF).forEach { value ->
            putU16(bytes, cursor, value)
            cursor += 2
        }

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 3, 20, TableLayout(0x100, 3, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.EGG).compatible)
        assertEquals(
            listOf(3, 5),
            result.acquisitionsBySpecies.getValue(1).filter { it.method == MoveAcquisitionMethod.EGG }.map { it.moveId },
        )
        assertEquals(
            listOf(9),
            result.acquisitionsBySpecies.getValue(2).filter { it.method == MoveAcquisitionMethod.EGG }.map { it.moveId },
        )
    }

    @Test
    fun decodesReferencedGenThreeMachineAndTutorPairs() {
        val bytes = ByteArray(4096)
        val machineMoves = 0x500
        repeat(58) { putU16(bytes, machineMoves + it * 2, it + 1) }
        val machineFlags = 0x700
        repeat(4) { species ->
            repeat(7) { bytes[machineFlags + species * 8 + it] = (if (species % 2 == 0) 0x55 else 0xAA).toByte() }
            bytes[machineFlags + species * 8 + 7] = ((0x01 shl (species % 2)) and 0x03).toByte()
        }
        putPointer(bytes, 0x40, machineMoves)
        putPointer(bytes, 0x44, machineFlags)

        val tutorMoves = 0x900
        listOf(5, 14, 25, 34, 38, 68, 69, 102, 118, 135, 138, 86, 153, 157, 164)
            .forEachIndexed { index, move -> putU16(bytes, tutorMoves + index * 2, move) }
        val tutorFlags = 0x980
        listOf(0x1111, 0x2222, 0x4444, 0x0888).forEachIndexed { species, flags ->
            putU16(bytes, tutorFlags + species * 2, flags)
        }
        putPointer(bytes, 0x80, tutorMoves)
        putPointer(bytes, 0x84, tutorFlags)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 200, TableLayout(0x100, 4, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
        assertEquals(
            listOf(14, 68, 135, 157),
            result.acquisitionsBySpecies.getValue(1).filter { it.method == MoveAcquisitionMethod.TUTOR }.map { it.moveId },
        )
    }

    @Test
    fun decodesPackedFireRedTutorFlagsAndRejectsUnrelatedReferencedMoveArrays() {
        val bytes = ByteArray(16_384)
        val tutorMoves = listOf(5, 14, 25, 34, 38, 68, 69, 102, 118, 135, 138, 86, 153, 157, 164)
        tutorMoves.forEachIndexed { index, move -> putU16(bytes, 0x900 + index * 2, move) }
        repeat(3) { species ->
            val row = 0x1200 + (species + 1) * 2
            putU16(bytes, row, when (species) {
                0 -> 0x1555
                1 -> 0x2AAA
                else -> 0x7FFF
            })
        }
        putPointer(bytes, 0x40, 0x900)
        putPointer(bytes, 0x44, 0x1200)

        repeat(40) { putU16(bytes, 0xA00 + it * 2, it + 40) }
        repeat(4) { species ->
            repeat(8) { byte -> bytes[0x1400 + species * 8 + byte] = if (species == 0) 0 else 0x55 }
        }
        putPointer(bytes, 0x80, 0xA00)
        putPointer(bytes, 0x84, 0x1400)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 200, TableLayout(0x100, 4, 28), EngineFamily.FIRERED_LEAFGREEN),
        )

        assertEquals(0x900, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).offset)
        assertEquals(
            listOf(5, 25, 38, 69, 118, 138, 153),
            result.acquisitionsBySpecies.getValue(1)
                .filter { it.method == MoveAcquisitionMethod.TUTOR }
                .map { it.moveId },
        )
    }

    @Test
    fun treatsRubyAndSapphireTutorCompatibilityAsNotApplicable() {
        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(ByteArray(2048)),
            layout(3, Platform.GBA, 4, 200, TableLayout(0x100, 4, 28), EngineFamily.RUBY_SAPPHIRE),
        )

        assertEquals(CapabilityStatus.NOT_APPLICABLE, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).status)
    }

    @Test
    fun joinsGenThreeMoveListsToIndependentlyReferencedCompatibilityTables() {
        val bytes = ByteArray(8192)
        val machineMoves = 0x900
        repeat(58) { putU16(bytes, machineMoves + it * 2, it + 1) }
        val machineFlags = 0x1200
        repeat(3) { species ->
            val row = machineFlags + (species + 1) * 8
            repeat(7) { bytes[row + it] = (if (species % 2 == 0) 0x55 else 0xAA).toByte() }
            bytes[row + 7] = (if (species % 2 == 0) 0x01 else 0x02).toByte()
        }
        putPointer(bytes, 0x40, machineMoves)
        putPointer(bytes, 0x500, machineFlags)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 100, TableLayout(0x100, 4, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertTrue(result.acquisitionsBySpecies.getValue(1).any { it.method == MoveAcquisitionMethod.MACHINE })
    }

    @Test
    fun decodesRuntimeReferencedMachineFlagsWithUsedSpeciesZeroAndStalePaddingBits() {
        val bytes = ByteArray(0x3000)
        val machineMoves = 0x900
        val officialMoves = listOf(
            264, 337, 352, 347, 46, 92, 258, 339, 331, 237, 241, 269, 58, 59, 63, 113, 182,
            240, 202, 219, 218, 76, 231, 85, 87, 89, 216, 91, 94, 247, 280, 104, 115, 351,
            53, 188, 201, 126, 317, 332, 259, 263, 290, 156, 213, 168, 211, 285, 289, 315,
            15, 19, 57, 70, 148, 249, 127, 291,
        ).toMutableList()
        listOf(2, 4, 8, 20, 27, 31, 39, 44, 46, 47, 48).forEach { officialMoves[it] = 500 + it }
        officialMoves.forEachIndexed { index, move -> putU16(bytes, machineMoves + index * 2, move) }
        putPointer(bytes, 0x100, machineMoves)

        val flags = 0x1200
        repeat(4) { species ->
            repeat(7) { byte -> bytes[flags + species * 8 + byte] = (if (species % 2 == 0) 0x55 else 0xAA).toByte() }
            bytes[flags + species * 8 + 7] = (0xFC or (species + 1)).toByte()
        }
        listOf(0x400, 0x420, 0x440, 0x460).forEach { reference ->
            putU16(bytes, reference - 10, 0x00C0) // lsls r0, r0, #3
            putU16(bytes, reference - 8, 0x6800) // ldr r0, [r0]
            putU16(bytes, reference - 6, 0x4000) // ands r0, r0
            putU16(bytes, reference - 4, 0x281F) // cmp r0, #31
            putU16(bytes, reference - 2, 0x3820) // subs r0, #32
            putPointer(bytes, reference, flags)
        }

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 800, TableLayout(0x1800, 4, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertEquals(machineMoves, result.evidence.getValue(MoveAcquisitionMethod.MACHINE).offset)
        assertTrue(result.acquisitionsBySpecies.getValue(0).any { it.method == MoveAcquisitionMethod.MACHINE })
    }

    @Test
    fun decodesNearbyRuntimeReferencedCustomizedTutorListAndFlags() {
        val bytes = ByteArray(0x3000)
        val tutorMoves = 0x1000
        repeat(32) { putU16(bytes, tutorMoves + it * 2, 400 + it) }
        putU16(bytes, tutorMoves + 64, 0xFFFF)
        putPointer(bytes, 0x420, tutorMoves)

        val flags = 0x1400
        listOf(0x55555555, 0xAAAAAAAA.toInt(), 0x0F0F0F0F, 0xF0F0F0F0.toInt())
            .forEachIndexed { species, value -> putU32(bytes, flags + species * 4, value) }
        putU16(bytes, 0x436, 0x0400) // lsls r0, r0, #16
        putU16(bytes, 0x438, 0x0B80) // lsrs r0, r0, #14 (net species * 4)
        putU16(bytes, 0x43A, 0x408A) // lsls r2, r1
        putU16(bytes, 0x43C, 0x6800) // ldr r0, [r0]
        putU16(bytes, 0x43E, 0x4010) // ands r0, r2
        putPointer(bytes, 0x440, flags)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 800, TableLayout(0x1800, 4, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
        assertEquals(tutorMoves, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).offset)
        assertEquals(
            listOf(400, 402, 404),
            result.acquisitionsBySpecies.getValue(0).filter { it.method == MoveAcquisitionMethod.TUTOR }
                .take(3).map { it.moveId },
        )
    }

    @Test
    fun prefersCodeCorroboratedMachineListsOverUnrelatedReferencedArrays() {
        val bytes = ByteArray(8192)
        repeat(58) { index ->
            putU16(bytes, 0x900 + index * 2, index + 1)
            putU16(bytes, 0xA00 + index * 2, index + 1)
            putU16(bytes, 0xB00 + index * 2, index + 2)
        }
        val flags = 0x1200
        repeat(3) { species ->
            val row = flags + (species + 1) * 8
            repeat(7) { bytes[row + it] = (if (species % 2 == 0) 0x55 else 0xAA).toByte() }
            bytes[row + 7] = (if (species % 2 == 0) 0x01 else 0x02).toByte()
        }
        putPointer(bytes, 0x40, 0x900)
        putPointer(bytes, 0x80, 0xA00)
        putPointer(bytes, 0xC0, 0xB00)
        putPointer(bytes, 0x500, flags)
        putU16(bytes, 0xE0, 0x3032) // adds r0, #50 TMs, near the 0xB00 table reference only
        putU16(bytes, 0xE2, 0x2906) // cmp r1, #6 (seven active HMs; FRLG keeps an unused eighth slot)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 100, TableLayout(0x100, 4, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertEquals(2, result.acquisitionsBySpecies.getValue(1).first { it.method == MoveAcquisitionMethod.MACHINE }.moveId)
    }

    @Test
    fun mergesEquallyValidatedMachineCompatibilityVariants() {
        val bytes = ByteArray(8192)
        repeat(58) { index -> putU16(bytes, 0x900 + index * 2, index + 1) }
        val firstFlags = 0x1200
        val secondFlags = 0x1400
        repeat(3) { species ->
            val firstRow = firstFlags + species * 8
            val secondRow = secondFlags + species * 8
            repeat(7) { byte ->
                bytes[firstRow + byte] = (if (species % 2 == 0) 0x55 else 0xAA).toByte()
                bytes[secondRow + byte] = (if (species % 2 == 0) 0xAA else 0x55).toByte()
            }
            bytes[firstRow + 7] = (if (species % 2 == 0) 0x01 else 0x02).toByte()
            bytes[secondRow + 7] = (if (species % 2 == 0) 0x02 else 0x01).toByte()
        }
        putPointer(bytes, 0x40, 0x900)
        putPointer(bytes, 0x44, firstFlags)
        putPointer(bytes, 0x48, secondFlags)
        putU16(bytes, 0x20, 0x3032)
        putU16(bytes, 0x22, 0x2907)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 3, 100, TableLayout(0x100, 3, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        val moves = result.acquisitionsBySpecies.getValue(1).filter { it.method == MoveAcquisitionMethod.MACHINE }.map { it.moveId }
        assertTrue(1 in moves)
        assertTrue(2 in moves)
    }

    @Test
    fun decodesPointerIndexedTutorLearnsets() {
        val bytes = ByteArray(8192)
        listOf(5, 14, 25, 34).forEachIndexed { index, move -> putU16(bytes, 0x900 + index * 2, move) }
        repeat(4) { species ->
            val listOffset = 0x1200 + species * 0x10
            putPointer(bytes, 0x1000 + species * 4, listOffset)
            bytes[listOffset] = species.toByte()
            bytes[listOffset + 1] = ((species + 1) % 4).toByte()
            bytes[listOffset + 2] = 0xFF.toByte()
        }
        bytes[0x1010] = 0x78
        bytes[0x1011] = 0x56
        bytes[0x1012] = 0x34
        bytes[0x1013] = 0x12
        putPointer(bytes, 0x40, 0x900)
        putPointer(bytes, 0x44, 0x1000)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 5, 100, TableLayout(0x100, 5, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
        assertEquals(listOf(14, 25), result.acquisitionsBySpecies.getValue(1).filter { it.method == MoveAcquisitionMethod.TUTOR }.map { it.moveId })
    }

    @Test
    fun decodesReferencedAdjacentTutorBitfieldWithCustomizedMoveValues() {
        val bytes = ByteArray(8192)
        val moveListOffset = 0x900
        val customMoves = listOf(
            152, 14, 12, 220, 38, 318, 140, 102, 118, 137,
            166, 86, 153, 226, 164, 223, 205, 244, 304, 261,
            125, 221, 8, 207, 214, 354, 277, 9, 7, 210,
        )
        customMoves.forEachIndexed { index, move -> putU16(bytes, moveListOffset + index * 2, move) }
        val flagsOffset = moveListOffset + customMoves.size * 2
        repeat(4) { species ->
            if (species > 0) {
                val flags = if (species % 2 == 0) 0x1555_5555 else 0x2AAA_AAAA
                repeat(4) { byte -> bytes[flagsOffset + species * 4 + byte] = (flags ushr (byte * 8)).toByte() }
            }
        }
        putPointer(bytes, 0x40, moveListOffset)
        putPointer(bytes, 0x80, moveListOffset)
        putPointer(bytes, 0xC0, flagsOffset)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 400, TableLayout(0x100, 4, 28)),
        )

        assertEquals(moveListOffset, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).offset)
        assertEquals(14, result.acquisitionsBySpecies.getValue(1).first { it.method == MoveAcquisitionMethod.TUTOR }.moveId)
    }

    @Test
    fun decodesReferencedTutorListWithZeroPaddedUnusedSlots() {
        val bytes = ByteArray(8192)
        val moveListOffset = 0x900
        val customMoves = listOf(174, 256, 276, 340, 210, 250, 29, 157, 8, 206, 9, 7, 265, 274)
        customMoves.forEachIndexed { index, move -> putU16(bytes, moveListOffset + index * 2, move) }
        val flagsOffset = moveListOffset + 30 * 2
        repeat(4) { species ->
            if (species > 0) {
                val flags = if (species % 2 == 0) 0x1555 else 0x2AAA
                putU32(bytes, flagsOffset + species * 4, flags)
            }
        }
        putPointer(bytes, 0x40, moveListOffset)
        putPointer(bytes, 0x80, moveListOffset)
        putPointer(bytes, 0xC0, flagsOffset)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 400, TableLayout(0x100, 4, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
        assertEquals(moveListOffset, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).offset)
        assertEquals(256, result.acquisitionsBySpecies.getValue(1).first { it.method == MoveAcquisitionMethod.TUTOR }.moveId)
    }

    @Test
    fun decodesValidatedIndirectCfruMachineAndTutorTables() {
        val bytes = ByteArray(0x126000)
        val machineMovesOffset = 0x2000
        val machineFlagsOffset = 0x3000
        val tutorMovesOffset = 0x4000
        val tutorFlagsOffset = 0x5000
        val officialMachinePrefix = listOf(
            264, 337, 352, 347, 46, 92, 258, 339, 331, 237, 241, 269, 58, 59, 63, 113, 182,
            240, 202, 219, 218, 76, 231, 85, 87, 89, 216, 91, 94, 247, 280, 104, 115, 351, 53,
            188, 201, 126, 317, 332, 259, 263, 290, 156, 213, 168, 211, 285, 289, 315,
        )
        val machineMoves = officialMachinePrefix + List(78) { it + 1 }
        machineMoves.forEachIndexed { index, move -> putU16(bytes, machineMovesOffset + index * 2, move) }
        repeat(4) { species ->
            repeat(16) { byte ->
                bytes[machineFlagsOffset + species * 16 + byte] = when {
                    species == 0 -> 0
                    species % 2 == 0 -> 0xAA.toByte()
                    else -> 0x55
                }
            }
        }
        repeat(96) { index -> putU16(bytes, tutorMovesOffset + index * 2, index + 1) }
        repeat(4) { species ->
            repeat(12) { byte ->
                bytes[tutorFlagsOffset + species * 12 + byte] = when {
                    species == 0 -> 0
                    species % 2 == 0 -> 0xAA.toByte()
                    else -> 0x55
                }
            }
        }
        putPointer(bytes, 0x125A8C, machineMovesOffset)
        putPointer(bytes, 0x043C68, machineFlagsOffset)
        putPointer(bytes, 0x120BE4, tutorMovesOffset)
        putPointer(bytes, 0x120C30, tutorFlagsOffset)
        putPointer(bytes, 0x100, 0x125A8C)
        putPointer(bytes, 0x104, 0x043C68)
        putPointer(bytes, 0x108, 0x120BE4)
        putPointer(bytes, 0x10C, 0x120C30)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 400, TableLayout(0x6000, 4, 28)),
        )

        assertEquals(machineMovesOffset, result.evidence.getValue(MoveAcquisitionMethod.MACHINE).offset)
        assertEquals(tutorMovesOffset, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).offset)
        assertTrue(result.acquisitionsBySpecies.getValue(1).any { it.method == MoveAcquisitionMethod.MACHINE })
        assertTrue(result.acquisitionsBySpecies.getValue(1).any { it.method == MoveAcquisitionMethod.TUTOR })
    }

    @Test
    fun decodesCfruSlotsWithoutArtificialReferencesAndWithPackedTutorRows() {
        val bytes = ByteArray(0x126000)
        val machineMovesOffset = 0x2000
        val machineFlagsOffset = 0x3000
        val tutorMovesOffset = 0x4000
        val tutorFlagsOffset = 0x5000
        val machineMoves = List(128) { it + 1 }
        val tutorMoves = listOf(5, 14, 25, 34, 38, 68, 69, 102, 118, 135, 138, 86, 153, 157, 164)
        machineMoves.forEachIndexed { index, move -> putU16(bytes, machineMovesOffset + index * 2, move) }
        tutorMoves.forEachIndexed { index, move -> putU16(bytes, tutorMovesOffset + index * 2, move) }
        repeat(4) { species ->
            repeat(16) { byte ->
                bytes[machineFlagsOffset + species * 16 + byte] = when {
                    species == 0 -> 0
                    species % 2 == 0 -> 0xAA.toByte()
                    else -> 0x55
                }
            }
            repeat(2) { byte ->
                bytes[tutorFlagsOffset + species * 2 + byte] = when {
                    species == 0 -> 0
                    species % 2 == 0 && byte == 0 -> 0xAA.toByte()
                    species % 2 == 0 -> 0x2A
                    else -> 0x55
                }
            }
        }
        putPointer(bytes, 0x125A8C, machineMovesOffset)
        putPointer(bytes, 0x043C68, machineFlagsOffset)
        putPointer(bytes, 0x120BE4, tutorMovesOffset)
        putPointer(bytes, 0x120C30, tutorFlagsOffset)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 512, TableLayout(0x6000, 4, 28)),
        )

        assertEquals(machineMovesOffset, result.evidence.getValue(MoveAcquisitionMethod.MACHINE).offset)
        assertEquals(tutorMovesOffset, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).offset)
        assertEquals(1, result.acquisitionsBySpecies.getValue(1).first { it.method == MoveAcquisitionMethod.MACHINE }.moveId)
        assertEquals(5, result.acquisitionsBySpecies.getValue(1).first { it.method == MoveAcquisitionMethod.TUTOR }.moveId)
    }

    @Test
    fun jointlyInfersIndirectMachineFlagsAndMoveListLength() {
        val bytes = ByteArray(2_000_000)
        val machineMovesOffset = 0x2000
        val machineFlagsOffset = 0x3000
        repeat(106) { putU16(bytes, machineMovesOffset + it * 2, it + 1) }
        putU16(bytes, machineMovesOffset + 106 * 2, 900)
        repeat(100) { species ->
            val row = machineFlagsOffset + species * 16
            if (species > 0) {
                repeat(106) { bit ->
                    if ((bit + species) % 3 != 0) {
                        bytes[row + bit / 8] = (bytes[row + bit / 8].toInt() or (1 shl (bit % 8))).toByte()
                    }
                }
            }
        }
        putPointer(bytes, 0x125A8C, machineMovesOffset)
        putPointer(bytes, 0x043C68, machineFlagsOffset)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 100, 512, TableLayout(0x6000, 100, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertEquals(machineMovesOffset, result.evidence.getValue(MoveAcquisitionMethod.MACHINE).offset)
        assertEquals(106, result.acquisitionsBySpecies.getValue(1).count { it.method == MoveAcquisitionMethod.MACHINE } +
            result.acquisitionsBySpecies.getValue(2).filter { it.method == MoveAcquisitionMethod.MACHINE }
                .map { it.moveId }.count { move ->
                    result.acquisitionsBySpecies.getValue(1).none { it.method == MoveAcquisitionMethod.MACHINE && it.moveId == move }
                })
    }

    @Test
    fun rejectsUnpairedMoveLists() {
        val bytes = ByteArray(2048)
        repeat(58) { putU16(bytes, 0x500 + it * 2, it + 1) }

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 100, TableLayout(0x100, 4, 28)),
        )

        assertTrue(result.acquisitionsBySpecies.isEmpty())
        assertTrue(result.evidence.values.none { it.compatible })
    }

    private fun layout(
        generation: Int,
        platform: Platform,
        speciesCount: Int,
        moveCount: Int,
        stats: TableLayout,
        family: EngineFamily = when (generation) {
            1 -> EngineFamily.RED_BLUE
            2 -> EngineFamily.CRYSTAL
            else -> EngineFamily.EMERALD
        },
    ) = ResolvedRomLayout(
        family = family,
        generation = generation,
        platform = platform,
        speciesCount = speciesCount,
        moveCount = moveCount,
        tables = ProfileTables(baseStats = stats),
    )

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000 + target
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}
