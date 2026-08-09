package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
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
        listOf(61, 62, 63, 64).forEachIndexed { index, move -> putU16(bytes, tutorMoves + index * 2, move) }
        val tutorFlags = 0x980
        repeat(4) { species -> bytes[tutorFlags + species * 4] = (1 shl species).toByte() }
        putPointer(bytes, 0x80, tutorMoves)
        putPointer(bytes, 0x84, tutorFlags)

        val result = MoveAcquisitionMaterializer.materialize(
            RomImage(bytes),
            layout(3, Platform.GBA, 4, 100, TableLayout(0x100, 4, 28)),
        )

        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
        assertTrue(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
        assertEquals(62, result.acquisitionsBySpecies.getValue(1).first { it.method == MoveAcquisitionMethod.TUTOR }.moveId)
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
    ) = ResolvedRomLayout(
        family = if (generation == 1) EngineFamily.RED_BLUE else EngineFamily.EMERALD,
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

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000 + target
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}
