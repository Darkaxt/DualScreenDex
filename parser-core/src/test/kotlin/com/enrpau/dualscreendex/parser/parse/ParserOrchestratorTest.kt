package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ParserOrchestratorTest {
    @Test
    fun refusesCloseRunnerUp() {
        val result = ParserOrchestrator.select(listOf(probe(EngineFamily.EMERALD, 80), probe(EngineFamily.FIRERED_LEAFGREEN, 74)))
        assertEquals(SelectionStatus.AMBIGUOUS, result.status)
    }

    @Test
    fun selectsClearValidatedWinner() {
        val result = ParserOrchestrator.select(listOf(probe(EngineFamily.EMERALD, 88), probe(EngineFamily.FIRERED_LEAFGREEN, 50)))
        assertEquals(SelectionStatus.SELECTED, result.status)
        assertEquals(EngineFamily.EMERALD, result.winner?.family)
    }

    @Test
    fun requiresTwoIndependentAnchors() {
        val weak = probe(EngineFamily.EMERALD, 100).copy(anchors = 1)
        assertEquals(SelectionStatus.UNSUPPORTED, ParserOrchestrator.select(listOf(weak)).status)
    }

    @Test
    fun colorEnhancedGenOneRomPassesYellowPlatformGate() {
        val bytes = ByteArray(0x100000)
        "POKEMON YELLOW".toByteArray().copyInto(bytes, 0x134)
        bytes[0x143] = 0x80.toByte()

        val result = ParserOrchestrator.analyze(RomImage(bytes))
        val yellow = result.probes.single { it.family == EngineFamily.YELLOW }

        assertEquals(true, yellow.hardGatePassed)
    }

    private fun probe(family: EngineFamily, score: Int) = ParserProbe(
        family = family,
        score = score,
        hardGatePassed = true,
        anchors = 3,
        scoreEvidence = emptyList(),
        capabilities = emptyList(),
    )
}
