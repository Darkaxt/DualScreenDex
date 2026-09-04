package com.enrpau.dualscreendex.parser.language

import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageRegistryTest {
    @Test
    fun registersJapaneseGenerationCodecsAndKoreanGenerationTwoCodec() {
        assertNull(LanguageRegistry.candidateCodec(LanguageTag.JAPANESE, 1, Platform.GB))
        assertEquals(
            "gb-gen1-ja-red-blue",
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                1,
                Platform.GB,
                EngineFamily.RED_BLUE,
            )?.id,
        )
        assertEquals(
            "gb-gen1-ja-yellow",
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                1,
                Platform.GB,
                EngineFamily.YELLOW,
            )?.id,
        )
        assertEquals(
            "gb-gen2-ja",
            LanguageRegistry.candidateCodec(LanguageTag.JAPANESE, 2, Platform.GBC)?.id,
        )
        assertNull(LanguageRegistry.candidateCodec(LanguageTag.JAPANESE, 3, Platform.GBA))
        assertEquals(
            "gba-gen3-ja-ruby-sapphire",
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                3,
                Platform.GBA,
                EngineFamily.RUBY_SAPPHIRE,
            )?.id,
        )
        assertEquals(
            "gba-gen3-ja-emerald-frlg",
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                3,
                Platform.GBA,
                EngineFamily.EMERALD,
            )?.id,
        )
        assertEquals(
            "gba-gen3-ja-emerald-frlg",
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                3,
                Platform.GBA,
                EngineFamily.FIRERED_LEAFGREEN,
            )?.id,
        )
        assertEquals(
            "gb-gen2-ko",
            LanguageRegistry.candidateCodec(LanguageTag.KOREAN, 2, Platform.GBC)?.id,
        )
        assertNull(LanguageRegistry.candidateCodec(LanguageTag.KOREAN, 1, Platform.GB))
        assertNull(LanguageRegistry.candidateCodec(LanguageTag.KOREAN, 3, Platform.GBA))
    }

    @Test
    fun resolvesEveryRegisteredOfficialCodecByItsExactVersionedIdentity() {
        val codecs = listOf(
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                1,
                Platform.GB,
                EngineFamily.RED_BLUE,
            ),
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                1,
                Platform.GB,
                EngineFamily.YELLOW,
            ),
            LanguageRegistry.candidateCodec(LanguageTag.JAPANESE, 2, Platform.GBC),
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                3,
                Platform.GBA,
                EngineFamily.RUBY_SAPPHIRE,
            ),
            LanguageRegistry.candidateCodec(
                LanguageTag.JAPANESE,
                3,
                Platform.GBA,
                EngineFamily.EMERALD,
            ),
            LanguageRegistry.candidateCodec(LanguageTag.KOREAN, 2, Platform.GBC),
        ).filterNotNull()

        assertEquals(6, codecs.size)
        for (codec in codecs) {
            assertEquals(codec, LanguageRegistry.codec(codec.id, codec.version))
        }
    }

    @Test
    fun returnsAllApplicableOfficialCandidatesWithoutCrossGenerationReuse() {
        assertEquals(
            7,
            LanguageRegistry.candidateCodecs(1, Platform.GB).size,
        )
        assertEquals(
            7,
            LanguageRegistry.candidateCodecs(2, Platform.GBC).size,
        )
        assertEquals(
            7,
            LanguageRegistry.candidateCodecs(3, Platform.GBA).size,
        )
    }
}
