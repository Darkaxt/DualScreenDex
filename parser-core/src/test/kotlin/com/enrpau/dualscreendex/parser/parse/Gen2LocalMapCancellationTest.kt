package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2LocalMapCancellationTest {
    @Test fun completeSyntheticLocalChainReachesLandmarkNameCaller() {
        val fixture = Gen2CompiledMapFixture().withLocalMaps()
        val result = Gen2LocalMapResolver.resolve(fixture.session(), Gen2CompiledMapFixture.MAP_IDS,
            EngineFamily.GOLD_SILVER, PokemonTextCodec.gbEnglish)
        assertTrue("expected resolved local fixture, got $result", result is LocalMapResolution.Resolved)
        assertEquals(listOf("A", "B"), (result as LocalMapResolution.Resolved).catalog.maps.map { it.displayName })
    }

    @Test fun actualLocalEntrypointDoesNotSwallowLandmarkDecoderCancellation() {
        val cancelled = CancellationException("local landmark decode")
        var calls = 0
        val codec = PokemonTextCodec(
            id = "test-local-map-cancellation", version = 1, language = LanguageTag.ENGLISH,
            applicableGenerations = setOf(2), applicablePlatforms = setOf(Platform.GBC), terminator = 0x50,
            tokenDecoder = PokemonTextTokenDecoder { _, _, _ -> calls++; throw cancelled },
        )
        val fixture = Gen2CompiledMapFixture().withLocalMaps()
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            Gen2LocalMapResolver.resolve(fixture.session(), Gen2CompiledMapFixture.MAP_IDS, EngineFamily.GOLD_SILVER, codec)
        })
        assertEquals(1, calls)
    }

    @Test fun sessionCancellationReachesMapGroupSearchFromLocalEntrypoint() {
        val cancelled = CancellationException("local map-group scan")
        var checks = 0
        val token = ParserCancellationToken {
            if (Thread.currentThread().stackTrace.any { it.methodName.substringBefore('$') == "findMapGroupRoots" } && ++checks == 2) throw cancelled
        }
        val fixture = Gen2CompiledMapFixture().withLocalMaps()
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            Gen2LocalMapResolver.resolve(fixture.session(token), Gen2CompiledMapFixture.MAP_IDS,
                EngineFamily.GOLD_SILVER, PokemonTextCodec.gbEnglish)
        })
    }

    @Test fun cancellationPrecedesEmptyOrUnsupportedLocalOutcomes() {
        val cancelled = CancellationException("local entrypoint")
        val fixture = Gen2CompiledMapFixture()
        val session = fixture.session(ParserCancellationToken { throw cancelled })
        for (family in listOf(EngineFamily.GOLD_SILVER, EngineFamily.EMERALD)) {
            assertSame(cancelled, assertThrows(CancellationException::class.java) {
                Gen2LocalMapResolver.resolve(session, emptySet(), family, null)
            })
        }
    }
}
