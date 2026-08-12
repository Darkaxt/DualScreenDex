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
    )

    @Test
    fun decodesTypedLifecycleAndLocationScalarsFromBoundedReads() {
        val decoder = Gen3RuntimeMemoryDecoder(layout)

        assertEquals(true, decoder.decodeBattleActive(byteArrayOf(0x02)))
        assertEquals(false, decoder.decodeBattleActive(byteArrayOf(0x00)))
        assertEquals(0x0202, decoder.decodeArea(byteArrayOf(2, 2)))
        assertEquals(3, decoder.decodeTargetBattler(byteArrayOf(3)))
    }

    @Test
    fun rejectsMalformedOrOutOfRangeScalarReads() {
        val decoder = Gen3RuntimeMemoryDecoder(layout)

        assertNull(decoder.decodeBattleActive(byteArrayOf()))
        assertNull(decoder.decodeArea(byteArrayOf(2)))
        assertNull(decoder.decodeTargetBattler(byteArrayOf(4)))
    }
}
