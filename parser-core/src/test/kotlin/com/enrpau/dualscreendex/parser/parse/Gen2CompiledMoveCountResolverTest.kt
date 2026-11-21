package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2CompiledMoveCountResolverTest {
    @Test fun readsRetailAndExpandedMetronomeBounds() {
        assertEquals(251, resolve(loop("fe fc 30")))
        assertEquals(252, resolve(loop("fe fd 30")))
        assertEquals(254, resolve(loop("fe ff 30")))
    }

    @Test fun acceptsTheEquivalentRejectOnly255Form() {
        assertEquals(254, resolve(loop("fe ff 28")))
        assertNull(resolve(loop("fe fd 28")))
    }

    @Test fun requiresBothJumpsBackToTheSameRandomCall() {
        assertNull(resolve(loop("fe fc 30").also { it[OFFSET + 6] = 0xF8.toByte() }))
        assertNull(resolve(loop("fe fc 30").also { it[OFFSET + 19] = 0xED.toByte() }))
        assertNull(resolve(loop("fe fc 30").also { it[OFFSET + 9] = 2 }))
    }

    @Test fun conflictingBoundsFailClosed() {
        val bytes = loop("fe fc 30")
        loop("fe fd 30").copyInto(bytes, 0x200, OFFSET, OFFSET + 20)
        assertNull(resolve(bytes))
        loop("fe fc 30").copyInto(bytes, 0x200, OFFSET, OFFSET + 20)
        assertEquals(251, resolve(bytes))
    }

    @Test fun battleTowerBoundIsOnlyAFallbackWithoutAnyMetronomeLoop() {
        assertEquals(254, resolve(tower("ff", "ff")))
        assertEquals(251, resolve(tower("fc", "fc")))
        assertNull(resolve(tower("ff", "fc")))
        // A compiled Metronome loop stays primary, even when it disagrees.
        val both = loop("fe fd 30")
        tower("ff", "ff").copyInto(both, 0x200, 0x200, 0x240)
        assertEquals(252, resolve(both))
        // Conflicting tower loaders fail closed.
        val conflict = tower("ff", "ff")
        tower("fc", "fc").copyInto(conflict, 0x300, 0x200, 0x240)
        assertNull(resolve(conflict))
    }

    private fun tower(first: String, second: String): ByteArray = ByteArray(0x400).also { bytes ->
        ("21 02 00 09 16 03 2a a7 28 06 fe $first 30 02 18 0a 2b 3e 01 22 af 22 22 77 18 0b 7e fe $second " +
            "38 02 36 00 23 15 20 f5 21 24 00 09").split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(bytes, 0x200)
    }

    private fun resolve(bytes: ByteArray) = Gen2CompiledMoveCountResolver.resolve(RomImage(bytes))

    private fun loop(compare: String): ByteArray = ByteArray(0x400).also { bytes ->
        "cd 8d 2f $compare f9 f5 11 01 00 21 57 74 cd cf 30 c1 38 ec".split(' ')
            .map { it.toInt(16).toByte() }.toByteArray().copyInto(bytes, OFFSET)
    }

    private companion object {
        const val OFFSET = 0x100
    }
}
