package com.darkaxt.dualdex.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Gen3RuntimeMemoryDecoderTest {
    private val layout = Gen3RuntimeMemoryLayout(
        mainAddress = 0x03001574,
        inBattleAddress = 0x030019AD,
        inBattleMask = 0x02,
        saveBlock1MapGroupOffset = 4,
        saveBlock1MapNumberOffset = 5,
        battleTypeFlagsAddress = 0x020003A0,
        trainerBattleMask = 1 shl 3,
        nonWildBattleMask = 0x8FFF8B72.toInt(),
    )

    @Test
    fun decodesTypedLifecycleAndLocationScalarsFromBoundedReads() {
        val decoder = Gen3RuntimeMemoryDecoder(layout)

        assertEquals(true, decoder.decodeBattleActive(byteArrayOf(0x02)))
        assertEquals(false, decoder.decodeBattleActive(byteArrayOf(0x00)))
        assertEquals(0x0202, decoder.decodeArea(byteArrayOf(2, 2)))
        val location = byteArrayOf(12, 0, 34, 0, 2, 2)
        assertEquals(0, decoder.locationWindowOffset)
        assertEquals(6, decoder.locationWindowBytes)
        assertEquals(0x0202, decoder.decodeArea(location))
        assertEquals(Gen3MapPosition(12, 34), decoder.decodePosition(location))
        assertEquals(3, decoder.decodeTargetBattler(byteArrayOf(3)))
        assertEquals(BattleEncounterKind.WILD, decoder.decodeBattleEncounterKind(u32(1 shl 2)))
        assertEquals(BattleEncounterKind.WILD, decoder.decodeBattleEncounterKind(u32((1 shl 2) or 1)))
        assertEquals(BattleEncounterKind.TRAINER, decoder.decodeBattleEncounterKind(u32(1 shl 3)))
        assertEquals(BattleEncounterKind.UNKNOWN, decoder.decodeBattleEncounterKind(u32(1 shl 1)))
        assertEquals(BattleEncounterKind.UNKNOWN, decoder.decodeBattleEncounterKind(u32(1 shl 9)))
        assertEquals(BattleEncounterKind.UNKNOWN, decoder.decodeBattleEncounterKind(u32((1 shl 3) or (1 shl 1))))
    }

    @Test
    fun rejectsMalformedOrOutOfRangeScalarReads() {
        val decoder = Gen3RuntimeMemoryDecoder(layout)

        assertNull(decoder.decodeBattleActive(byteArrayOf()))
        assertNull(decoder.decodeArea(byteArrayOf(2)))
        assertNull(decoder.decodePosition(byteArrayOf(0, 0, 0)))
        assertNull(decoder.decodePosition(byteArrayOf(-1, -1, 0, 0, 2, 2)))
        assertNull(decoder.decodeTargetBattler(byteArrayOf(4)))
        assertEquals(BattleEncounterKind.UNKNOWN, decoder.decodeBattleEncounterKind(null))
        assertEquals(BattleEncounterKind.UNKNOWN, decoder.decodeBattleEncounterKind(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun decodesThePlayerCommandOwnerWithoutBorrowingAnExecutionTarget() {
        val decoder = Gen3RuntimeMemoryDecoder(
            layout.copy(
                battleUi = Gen3BattleUiMemoryLayout(
                    activeBattlerAddress = 0x02024064,
                    actionCursorAddress = 0x020244AC,
                    moveCursorAddress = 0x020244B0,
                ),
            ),
        )

        assertEquals(
            Gen3BattleCommandState(activeBattler = 2, moveSlot = 1),
            decoder.decodeSelectedBattleCommand(
                activeBattler = byteArrayOf(2),
                actionCursors = byteArrayOf(0, 1, 0, 1),
                moveCursors = byteArrayOf(0, 0, 1, 0),
            ),
        )
        assertNull(
            decoder.decodeSelectedBattleCommand(
                activeBattler = byteArrayOf(2),
                actionCursors = byteArrayOf(0, 1, 2, 1),
                moveCursors = byteArrayOf(0, 0, 1, 0),
            ),
        )
    }

    @Test
    fun preservesCompletePointerFirstReadPlan() {
        val complete = layout.copy(
            saveBlock1PointerAddress = 0x03005D8C,
            saveBlock2PointerAddress = 0x03005D90,
            saveBlock1Size = 0x3D88,
            saveBlock2Size = 0x0F2C,
            playerPartyCountAddress = 0x020244E9,
            playerPartyAddress = 0x020244EC,
            playerPartyCapacity = 6,
            playerPartyRecordSize = 100,
        )

        assertEquals(0x03005D8CL, complete.saveBlock1PointerAddress)
        assertEquals(0x0F2C, complete.saveBlock2Size)
        assertEquals(600, complete.playerPartyCapacity!! * complete.playerPartyRecordSize!!)
        assertThrows(IllegalArgumentException::class.java) {
            layout.copy(saveBlock1PointerAddress = 0x03005D8C)
        }
    }

    private fun u32(value: Int) = ByteArray(4) { index -> (value ushr (index * 8)).toByte() }
}
