package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchStatusTest {
    @Test
    fun parsesContentlessAndPlayingResponses() {
        assertEquals(RetroArchStatus.Contentless, RetroArchStatusParser.parse("GET_STATUS CONTENTLESS"))
        assertEquals(
            RetroArchStatus.Running(
                paused = false,
                systemId = "Nintendo - Game Boy Advance",
                gameBasename = "Pokemon - Emerald Version (USA, Europe)",
                crc32 = "1F1C08FB",
            ),
            RetroArchStatusParser.parse(
                "GET_STATUS PLAYING Nintendo - Game Boy Advance,Pokemon - Emerald Version (USA, Europe),crc32=1f1c08fb",
            ),
        )
    }

    @Test
    fun preservesCommasInsideTheGameBasename() {
        val parsed = RetroArchStatusParser.parse(
            "GET_STATUS PAUSED Nintendo - Game Boy / Color,Pokemon Red, Randomizer,crc32=00000000",
        )

        assertEquals(
            RetroArchStatus.Running(true, "Nintendo - Game Boy / Color", "Pokemon Red, Randomizer", null),
            parsed,
        )
    }

    @Test
    fun parsesCurrentRetroArchResponseWithoutAnInventedCrcField() {
        assertEquals(
            RetroArchStatus.Running(
                paused = true,
                systemId = "game_boy_advance",
                gameBasename = "Pokemon - Modern Emerald Version v3.5 (USA, Europe)",
                crc32 = null,
            ),
            RetroArchStatusParser.parse(
                "GET_STATUS PAUSED game_boy_advance,Pokemon - Modern Emerald Version v3.5 (USA, Europe)",
            ),
        )
    }

    @Test
    fun malformedResponsesAreDataRatherThanExceptions() {
        assertTrue(RetroArchStatusParser.parse("GET_STATUS PLAYING broken") is RetroArchStatus.Malformed)
    }
}
