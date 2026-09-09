package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.catalog.LocalMapNameDisposition
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

    @Test fun contextualHeaderKeepsItsNumericMapAndIsAccountedSeparately() {
        val fixture = contextualFixture()
        val result = Gen2LocalMapResolver.resolve(fixture.session(), Gen2CompiledMapFixture.MAP_IDS,
            EngineFamily.GOLD_SILVER, PokemonTextCodec.gbEnglish) as LocalMapResolution.Resolved
        assertEquals(listOf(0x101, 0x102, 0x103), result.catalog.maps.map { it.baseAreaId })
        assertEquals(listOf("A", "B", null), result.catalog.maps.map { it.displayName })
        assertEquals(listOf(LocalMapNameDisposition.STATIC_NAME_REQUIRED,
            LocalMapNameDisposition.STATIC_NAME_REQUIRED, LocalMapNameDisposition.CONTEXT_DEPENDENT),
            result.catalog.maps.map { it.nameDisposition })
        assertTrue(result.reasons.contains("classified 1 compiled contextual map-name identities separately; retained all 3 numeric maps"))
    }

    @Test fun unsupportedContextDeclarationDoesNotExemptAnUnnamedHeader() {
        val fixture = contextualFixture()
        fixture.bytes[0x880c] = 1
        val result = Gen2LocalMapResolver.resolve(fixture.session(), Gen2CompiledMapFixture.MAP_IDS,
            EngineFamily.GOLD_SILVER, PokemonTextCodec.gbEnglish) as LocalMapResolution.Resolved
        assertEquals(3, result.catalog.maps.size)
        assertTrue(result.catalog.maps.all { it.nameDisposition == LocalMapNameDisposition.STATIC_NAME_REQUIRED })
        assertTrue(result.reasons.contains("classified 0 compiled contextual map-name identities separately; retained all 3 numeric maps"))
    }

    private fun contextualFixture() = Gen2CompiledMapFixture().withLocalMaps().apply {
        bytes.copyInto(bytes, Gen2CompiledMapFixture.HEADERS + 18,
            Gen2CompiledMapFixture.HEADERS, Gen2CompiledMapFixture.HEADERS + 9)
        bytes[Gen2CompiledMapFixture.HEADERS + 23] = 0
        put(0x10, 0xe0, 0x80, 0xea, 0, 0x20, 0xc9)
        put(0x300, 0xa7, 0xc8, 9, 0x3d, 0x20, 0xfc, 0xc9)
        put(0x700, 0xe5, 0xd5, 0xc5, 0x11, 5, 0, 0xcd, 0x40, 2, 0x79, 0xc1, 0xd1, 0xe1, 0xc9)
        put(0x8800, 0xfa, 0, 0xc0, 0x47, 0xfa, 1, 0xc0, 0x4f, 0xcd, 0, 7, 0xfe, 0, 0xc0,
            0xfa, 4, 0xc0, 0x47, 0xfa, 5, 0xc0, 0x4f, 0xcd, 0, 7, 0xc9)
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
