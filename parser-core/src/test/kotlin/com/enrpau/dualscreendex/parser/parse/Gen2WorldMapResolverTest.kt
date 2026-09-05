package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
import java.util.concurrent.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen2WorldMapResolverTest {
    @Test fun requiredDynamicSpecialLandmarkFailsClosed() {
        val source = sourceBytes("DUALDEX_POKEGOLD_ROM")
        val groupPointer = u16le(source, MAP_GROUP_POINTERS + (JOHTO_GROUP - 1) * 2)
        val groupRoot = MAP_DATA_BANK * BANK_BYTES + groupPointer - BANK_BYTES
        val header = groupRoot + (JOHTO_MAP - 1) * MAP_HEADER_BYTES
        source[header + MAP_LOCATION_FIELD] = SPECIAL_LANDMARK.toByte()
        val mutated = RomImage(source)

        val result = Gen2WorldMapResolver.resolve(
            RomAnalysisSession(mutated, RomHeaderReader.read(mutated)),
            setOf(JOHTO_BASE_ID, KANTO_BASE_ID),
            PokemonTextCodec.gbEnglish,
        )

        assertTrue("expected unavailable join, got $result", result is WorldMapResolution.Unavailable)
        assertEquals("landmark-join", (result as WorldMapResolution.Unavailable).stage)
    }

    @Test fun generatedCompiledConsumersResolveNumericBindingsAndNamesWithoutHeaderIdentity() {
        val fixture = Gen2CompiledMapFixture()
        val catalog = resolved(fixture)
        assertEquals(listOf("A", "B"), catalog.regions.flatMap { it.locations }.map { it.displayName })
        assertEquals(listOf("gen2-johto", "gen2-kanto"), catalog.regions.map { it.key })
        assertEquals(listOf(setOf(0x101), setOf(0x102)), catalog.regions.flatMap { it.locations }.map { it.baseAreaIds })
        assertEquals(listOf(listOf(WorldMapCell(1, 1, 1, 1)), listOf(WorldMapCell(2, 2, 1, 1))),
            catalog.regions.flatMap { it.locations }.map { it.geometry })
        assertEquals(2, catalog.assets.size)
        assertEquals(mapOf(1 to "A", 2 to "B"), Gen2WorldMapResolver.resolveLandmarkNames(
            fixture.session(), Gen2CompiledMapFixture.MAP_IDS, PokemonTextCodec.gbEnglish))
    }

    @Test fun malformedLocalizedNamesRetainExactlyTheCodecFreeNumericCatalog() {
        val cases = listOf(
            PokemonTextCodec.gbEnglish to intArrayOf(0x80, 0, 0x50),
            JapanesePokemonTextCodecs.gen2 to intArrayOf(0x80, 0x52, 0x50),
            KoreanGen2PokemonTextCodec.codec to intArrayOf(0x01, 0x01, 0x01, 0x50, 0x50),
            KoreanGen2PokemonTextCodec.codec to IntArray(32) { if (it == 31) 1 else 0x80 },
        )
        for ((codec, name) in cases) {
            val fixture = Gen2CompiledMapFixture().apply { landmark(1, name); landmark(2, name) }
            val expected = resolved(fixture, null)
            val actual = resolved(fixture, codec)
            assertEquals(expected.regions.map { it.copy(displayName = null) },
                actual.regions.map { it.copy(displayName = null) })
            assertTrue(actual.regions.flatMap { it.locations }.all { it.displayName == null })
            assertEquals(emptyMap<Int, String>(), Gen2WorldMapResolver.resolveLandmarkNames(
                fixture.session(), Gen2CompiledMapFixture.MAP_IDS, codec))
        }
    }

    @Test fun oneMalformedNameDoesNotDestroyAnyNumericBinding() {
        val fixture = Gen2CompiledMapFixture().apply { landmark(2, intArrayOf(0, 0x50)) }
        assertEquals(2, resolved(fixture).regions.flatMap { it.locations }.size)
    }

    @Test fun ambiguousEnglishEncodingDisablesTextNotNumericBindings() {
        val fixture = Gen2CompiledMapFixture().apply {
            landmark(1, intArrayOf(0x80, 0xc0, 0x50))
            landmark(2, intArrayOf(0x81, 0xc0, 0x50))
        }
        assertTrue(resolved(fixture).regions.flatMap { it.locations }.all { it.displayName == null })
    }

    @Test fun nativeNamesAreDecodedByTheSuppliedCodecThroughActualCompiledBindings() {
        for ((codec, name, expected) in listOf(
            Triple(JapanesePokemonTextCodecs.gen2, intArrayOf(0x05, 0x06, 0x50), "ガギ"),
            Triple(KoreanGen2PokemonTextCodec.codec, intArrayOf(0x01, 0x01, 0x01, 0x02, 0x50), "가각"),
        )) {
            val fixture = Gen2CompiledMapFixture().apply { landmark(1, name); landmark(2, name) }
            assertEquals(listOf(expected, expected), resolved(fixture, codec).regions.flatMap { it.locations }.map { it.displayName })
            assertEquals(mapOf(1 to expected, 2 to expected), Gen2WorldMapResolver.resolveLandmarkNames(
                fixture.session(), Gen2CompiledMapFixture.MAP_IDS, codec))
        }
    }

    @Test fun malformedNamesNeverRelaxStructuralConsumersPointersOrCoordinates() {
        val mutations: List<(Gen2CompiledMapFixture) -> Unit> = listOf(
            { it.bytes[Gen2CompiledMapFixture.LANDMARK_CONSUMER] = 0 },
            { it.bytes[0x200] = 0 },
            { it.bytes[0x41c] = 0 },
            { it.word(Gen2CompiledMapFixture.GROUP_TABLE, 0x8000) },
            { it.word(Gen2CompiledMapFixture.LANDMARK_TABLE + 6, 0x8000) },
            { it.bytes[Gen2CompiledMapFixture.LANDMARK_TABLE + 4] = 0 },
            { it.bytes[Gen2CompiledMapFixture.LANDMARK_TABLE + 5] = 0xff.toByte() },
            { it.bytes[Gen2CompiledMapFixture.HEADERS + 5] = 0 },
        )
        for ((index, mutate) in mutations.withIndex()) {
            val fixture = Gen2CompiledMapFixture().apply { landmark(1, intArrayOf(0, 0x50)); mutate(this) }
            for (codec in listOf(null, PokemonTextCodec.gbEnglish)) {
                val result = Gen2WorldMapResolver.resolve(fixture.session(), Gen2CompiledMapFixture.MAP_IDS, codec)
                assertTrue("mutation $index: $result", result is WorldMapResolution.Unavailable)
                assertEquals("landmark-join", (result as WorldMapResolution.Unavailable).stage)
            }
        }
    }

    @Test fun conflictingNumericAuthoritiesCannotBeSelectedByReadableText() {
        val fixture = Gen2CompiledMapFixture().apply { addCompetingLandmarks(differentCoordinates = true, malformed = true) }
        val result = Gen2WorldMapResolver.resolve(fixture.session(), Gen2CompiledMapFixture.MAP_IDS, PokemonTextCodec.gbEnglish)
        assertTrue("expected ambiguous numeric binding, got $result", result is WorldMapResolution.Ambiguous)
        assertEquals(emptyMap<Int, String>(), Gen2WorldMapResolver.resolveLandmarkNames(
            fixture.session(), Gen2CompiledMapFixture.MAP_IDS, PokemonTextCodec.gbEnglish))
    }

    @Test fun equivalentNumericAuthoritiesCannotSelectTheFirstConflictingText() {
        val fixture = Gen2CompiledMapFixture().apply { addCompetingLandmarks(differentCoordinates = false) }
        assertTrue(resolved(fixture).regions.flatMap { it.locations }.all { it.displayName == null })
        assertEquals(emptyMap<Int, String>(), Gen2WorldMapResolver.resolveLandmarkNames(
            fixture.session(), Gen2CompiledMapFixture.MAP_IDS, PokemonTextCodec.gbEnglish))
    }

    @Test fun namesFromNumericallyInvalidAuthorityCannotFillUnavailableBoundNames() {
        val fixture = Gen2CompiledMapFixture().apply {
            landmark(1, intArrayOf(0, 0x50))
            addCompetingLandmarks(differentCoordinates = false)
            bytes[0x9804] = 0
        }
        assertEquals(emptyMap<Int, String>(), Gen2WorldMapResolver.resolveLandmarkNames(
            fixture.session(), Gen2CompiledMapFixture.MAP_IDS, PokemonTextCodec.gbEnglish))
    }

    @Test fun cancellationPrecedesEmptyWorldAndNameOutcomes() {
        val cancelled = CancellationException("empty entrypoint")
        val session = Gen2CompiledMapFixture().session(ParserCancellationToken { throw cancelled })
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            Gen2WorldMapResolver.resolve(session, emptySet(), null)
        })
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            Gen2WorldMapResolver.resolveLandmarkNames(session, emptySet(), PokemonTextCodec.gbEnglish)
        })
    }

    @Test fun cancellationPropagatesFromInsideNameTokenDecoding() {
        val cancelled = CancellationException("codec cancellation")
        val codec = observingCodec { throw cancelled }
        val fixture = Gen2CompiledMapFixture()
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            Gen2WorldMapResolver.resolve(fixture.session(), Gen2CompiledMapFixture.MAP_IDS, codec)
        })
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            Gen2WorldMapResolver.resolveLandmarkNames(fixture.session(), Gen2CompiledMapFixture.MAP_IDS, codec)
        })
    }

    @Test fun sessionCancellationIsCheckedBetweenLandmarkTokens() {
        var tokens = 0
        val cancelled = CancellationException("after first glyph")
        val codec = observingCodec { tokens++ }
        val session = Gen2CompiledMapFixture().session(ParserCancellationToken { if (tokens > 0) throw cancelled })
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            Gen2WorldMapResolver.resolve(session, Gen2CompiledMapFixture.MAP_IDS, codec)
        })
        assertEquals(1, tokens)
    }

    @Test fun cancellationIsObservedInsideStructuralScansAndBindingLoops() {
        // Target phases, not implementation-dependent aggregate checkpoint counts or timing.
        for (phase in listOf("findMapAuthorities", "findGraphicsLoaders", "findPaletteLoaders",
            "findMapGroupRoots", "findLandmarkAuthorities", "findDirectThresholdRegionClassifiers", "buildBindings")) {
            var checks = 0
            val cancelled = CancellationException(phase)
            val token = ParserCancellationToken {
                if (Thread.currentThread().stackTrace.any { it.methodName.substringBefore('$') == phase } && ++checks == 2) throw cancelled
            }
            assertSame(phase, cancelled, assertThrows(CancellationException::class.java) {
                Gen2WorldMapResolver.resolve(Gen2CompiledMapFixture().session(token), Gen2CompiledMapFixture.MAP_IDS, PokemonTextCodec.gbEnglish)
            })
        }
    }

    private fun resolved(fixture: Gen2CompiledMapFixture, codec: PokemonTextCodec? = PokemonTextCodec.gbEnglish): WorldMapCatalog {
        val result = Gen2WorldMapResolver.resolve(fixture.session(), Gen2CompiledMapFixture.MAP_IDS, codec)
        assertTrue("expected resolved structural fixture, got $result", result is WorldMapResolution.Resolved)
        return (result as WorldMapResolution.Resolved).catalog
    }

    private fun observingCodec(onToken: () -> Unit) = PokemonTextCodec(
        id = "test-gen2-map-cancellation", version = 1, language = LanguageTag.ENGLISH,
        applicableGenerations = setOf(2), applicablePlatforms = setOf(Platform.GBC), terminator = 0x50,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, _ ->
            onToken()
            if (rom.u8(offset) == 0x50) PokemonTextToken.Terminator() else PokemonTextToken.Glyph("A")
        },
    )

    private fun sourceBytes(env: String): ByteArray {
        val configured = System.getenv(env)
        assumeTrue("set $env to run this source-derived rejection control", !configured.isNullOrBlank())
        return Files.readAllBytes(Path.of(requireNotNull(configured)))
    }

    private fun u16le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private companion object {
        const val BANK_BYTES = 0x4000
        const val MAP_DATA_BANK = 0x25
        const val MAP_GROUP_POINTERS = 0x940ed
        const val MAP_HEADER_BYTES = 9
        const val MAP_LOCATION_FIELD = 5
        const val SPECIAL_LANDMARK = 0
        const val JOHTO_GROUP = 1
        const val JOHTO_MAP = 12
        const val JOHTO_BASE_ID = (JOHTO_GROUP shl 8) or JOHTO_MAP
        const val KANTO_BASE_ID = 843
    }
}
