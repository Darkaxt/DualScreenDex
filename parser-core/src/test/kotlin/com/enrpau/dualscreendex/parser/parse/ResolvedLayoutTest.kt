package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ResolvedLayoutTest {
    @Test
    fun probePreservesRelocatedTableLayoutUsedByValidation() {
        val chartOffset = 300
        val chart = byteArrayOf(
            0, 5, 5,
            0, 8, 5,
            10, 10, 5,
            10, 11, 5,
            10, 12, 20,
            10, 15, 20,
            10, 6, 20,
            10, 5, 5,
            10, 16, 5,
            10, 8, 20,
            11, 10, 20,
            11, 11, 5,
            0xFF.toByte(), 0xFF.toByte(), 0,
        )
        val bytes = ByteArray(512) { 0x7F }
        chart.copyInto(bytes, chartOffset)

        val probe = FamilyParsers.all.single { it.family == EngineFamily.EMERALD }
            .probe(RomImage(bytes), RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))

        val layout = assertNotNull(probe.resolvedLayout).let { probe.resolvedLayout!! }
        assertEquals(3, layout.generation)
        assertEquals(chartOffset, layout.tables.typeChart?.offset)
        assertEquals(3, layout.tables.typeChart?.recordSize)
        assertEquals(true, layout.tables.typeChart?.variableLength)
    }
}
