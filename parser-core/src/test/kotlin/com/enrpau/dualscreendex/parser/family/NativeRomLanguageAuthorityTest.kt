package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import org.junit.Assert.*
import org.junit.Test

class NativeRomLanguageAuthorityTest {
    @Test fun resolvesEveryNativeCodecFromSeparateSelectedTablesWithoutLeadingRetailControls() {
        for (codec in JapanesePokemonTextCodecs.all + KoreanGen2PokemonTextCodec.codec) {
            val fixture = nativeAuthorityFixture(codec)
            val result = fixture.resolve()
            assertEquals(codec.id, LanguageResolutionStatus.RESOLVED, result.status)
            assertEquals(codec.language, result.defaultLanguage)
            assertEquals(codec.id, result.defaultProjection()?.codecId)
            assertEquals(fixture.species, result.defaultProjection()?.localizedTables?.speciesNames)
        }
    }

    @Test fun nativeContentOutranksAContradictoryRegionalHeader() {
        val fixture = nativeAuthorityFixture(JapanesePokemonTextCodecs.gen3Later)
        assertEquals(LanguageTag.JAPANESE, fixture.resolve(header = RomHeader(Platform.GBA, "CUSTOM", "BPEE")).defaultLanguage)
    }

    @Test fun randomKanaAndRepeatedKnownNamesDoNotEstablishLanguage() {
        val codec = JapanesePokemonTextCodecs.gen1RedBlue
        val random = listOf("アイウエオ", "カキクケコ", "サシスセソ", "タチツテト", "ナニヌネノ", "ハヒフホマ")
        assertEquals(LanguageResolutionStatus.UNKNOWN, nativeAuthorityFixture(codec, random).resolve().status)
        assertEquals(LanguageResolutionStatus.UNKNOWN, nativeAuthorityFixture(codec, List(20) { "はたく" }).resolve().status)
    }

    @Test fun latinScriptCollisionsAndHeaderOnlySpeciesCannotJoinNativeMoves() {
        val fixture = nativeAuthorityFixture(JapanesePokemonTextCodecs.gen3Later)
        repeat(fixture.species.count) { index ->
            nativeEncode("ABCDE", fixture.codec).copyInto(fixture.bytes, fixture.species.offset + index * fixture.species.recordSize)
        }
        assertEquals(LanguageResolutionStatus.UNKNOWN, fixture.resolve().status)
        fixture.bytes.fill(0, fixture.species.offset, fixture.species.offset + fixture.species.count * fixture.species.recordSize)
        assertEquals(LanguageResolutionStatus.UNKNOWN, fixture.resolve().status)
    }

    @Test fun selectedVariableRootCannotResolveFromATerminatedPrefixOnly() {
        val fixture = nativeAuthorityFixture(KoreanGen2PokemonTextCodec.codec)
        val lastTerminator = fixture.bytes.indexOfLast { it == 0x50.toByte() }
        fixture.bytes[lastTerminator] = 1 // truncated lead; no next established record boundary
        assertEquals(LanguageResolutionStatus.UNKNOWN, fixture.resolve().status)
    }

    @Test fun malformedFixedRecordAndOverlappingStrideCannotSupplyNativeEvidence() {
        val fixture = nativeAuthorityFixture(JapanesePokemonTextCodecs.gen3Later)
        fixture.bytes[fixture.species.offset + 5] = 0xfd.toByte()
        assertEquals(LanguageResolutionStatus.UNKNOWN, fixture.resolve().status)
        val valid = nativeAuthorityFixture(JapanesePokemonTextCodecs.gen3Later)
        assertEquals(LanguageResolutionStatus.UNKNOWN, valid.copy(species = valid.species.copy(stride = 1)).resolve().status)
    }

    @Test fun cancelsBeforeEarlyOutcomeAndDuringNativeSampling() {
        for (at in listOf(1, 12)) {
            var checks = 0
            val token = ParserCancellationToken { if (++checks == at) throw ParserCancellationException() }
            assertThrows(ParserCancellationException::class.java) {
                nativeAuthorityFixture(KoreanGen2PokemonTextCodec.codec).resolve(cancellation = token)
            }
        }
    }
}

internal data class NativeAuthorityFixture(val bytes: ByteArray, val codec: PokemonTextCodec, val species: TableLayout, val moves: TableLayout) {
    fun resolve(header: RomHeader? = null, cancellation: ParserCancellationToken = ParserCancellationToken.NONE) = RomLanguageAuthority.resolve(
        RomImage(bytes), header ?: RomHeader(if (3 in codec.applicableGenerations) Platform.GBA else Platform.GBC, "UNMARKED"),
        codec.applicableGenerations.single(), codec, evidence(species), evidence(moves), species, moves, cancellation,
    )
    private fun evidence(layout: TableLayout) = ValidationEvidence(true, layout.count, layout.count, 1.0,
        listOf("selected synthetic table with independent geometry"), layout.offset, layout.recordSize)
}

internal fun nativeAuthorityFixture(codec: PokemonTextCodec, names: List<String>? = null): NativeAuthorityFixture {
    val generation = codec.applicableGenerations.single()
    val korean = codec.language == LanguageTag.KOREAN
    val speciesNames = if (korean) listOf("이상해씨", "이상해풀", "이상해꽃", "파이리")
        else listOf("フシギダネ", "フシギソウ", "フシギバナ", "ヒトカゲ")
    val moveNames = names ?: if (korean) listOf("연속펀치", "메가톤펀치", "고양이돈받기", "불꽃펀치", "냉동펀치", "번개펀치", "할퀴기", "가위자르기")
        else listOf("れんぞくパンチ", "メガトンパンチ", "ネコにこばん", "ほのおのパンチ", "れいとうパンチ", "かみなりパンチ", "ひっかく", "ハサミギロチン")
    val speciesWidth = if (generation == 3) 6 else if (korean) 10 else 5
    val species = TableLayout(0x100, speciesNames.size, speciesWidth)
    val moves = TableLayout(0x200, moveNames.size + if (generation == 3) 1 else 0,
        if (generation == 3) 8 else 0, variableLength = generation != 3)
    val encodedMoves = moveNames.map { nativeEncode(it, codec) }
    val bytes = ByteArray(if (generation == 3) 0x200 + moves.count * 8 else 0x200 + encodedMoves.sumOf { it.size })
    speciesNames.forEachIndexed { index, name ->
        nativeEncode(name, codec).take(speciesWidth).toByteArray().copyInto(bytes, species.offset + index * speciesWidth)
    }
    if (generation == 3) {
        bytes[0x200] = 0xae.toByte(); bytes[0x201] = 0xff.toByte()
        encodedMoves.forEachIndexed { index, encoded -> encoded.copyInto(bytes, 0x200 + (index + 1) * 8) }
    } else {
        var cursor = moves.offset
        encodedMoves.forEach { encoded -> encoded.copyInto(bytes, cursor); cursor += encoded.size }
    }
    return NativeAuthorityFixture(bytes, codec, species, moves)
}

/** Test encoder uses exact glyph tokens, never substitutions or production lexical authority. */
internal fun nativeEncode(text: String, codec: PokemonTextCodec): ByteArray {
    val glyphs = nativeGlyphs.getOrPut(codec.id) {
        buildMap {
            for (lead in 0..255) {
                val bytes = byteArrayOf(lead.toByte(), codec.terminator.toByte())
                val token = codec.decodeToken(RomImage(bytes), 0, 1)
                if (token is PokemonTextToken.Glyph && token.text.length == 1) putIfAbsent(token.text.single(), byteArrayOf(lead.toByte()))
                if (codec.language == LanguageTag.KOREAN && lead in 1..11) for (trail in 0..255) {
                    val pair = byteArrayOf(lead.toByte(), trail.toByte())
                    val decoded = codec.decodeToken(RomImage(pair), 0, 2)
                    if (decoded is PokemonTextToken.Glyph && decoded.text.length == 1) putIfAbsent(decoded.text.single(), pair)
                }
            }
            put(' ', byteArrayOf(if (3 in codec.applicableGenerations) 0 else 0x7f))
        }
    }
    return text.flatMap { requireNotNull(glyphs[it]) { "${codec.id} fixture glyph $it is unavailable" }.asList() }
        .plus(codec.terminator.toByte()).toByteArray()
}
private val nativeGlyphs = mutableMapOf<String, Map<Char, ByteArray>>()
