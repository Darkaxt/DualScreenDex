package com.darkaxt.dualdex.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertNull(decoder.decodeTargetBattler(byteArrayOf(4)))
        assertEquals(BattleEncounterKind.UNKNOWN, decoder.decodeBattleEncounterKind(null))
        assertEquals(BattleEncounterKind.UNKNOWN, decoder.decodeBattleEncounterKind(byteArrayOf(0, 0, 0)))
    }

    private fun u32(value: Int) = ByteArray(4) { index -> (value ushr (index * 8)).toByte() }
}
