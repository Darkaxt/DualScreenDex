package com.enrpau.dualscreendex.parser.language

import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import java.util.Collections

data class LanguageDescriptor(
    val tag: LanguageTag,
    val englishName: String,
    val scripts: Set<String>,
)

object LanguageRegistry {
    private val registeredDescriptors = listOf(
        LanguageDescriptor(LanguageTag.ENGLISH, "English", setOf("Latn")),
        LanguageDescriptor(LanguageTag.FRENCH, "French", setOf("Latn")),
        LanguageDescriptor(LanguageTag.GERMAN, "German", setOf("Latn")),
        LanguageDescriptor(LanguageTag.ITALIAN, "Italian", setOf("Latn")),
        LanguageDescriptor(LanguageTag.SPANISH, "Spanish", setOf("Latn")),
        LanguageDescriptor(LanguageTag.JAPANESE, "Japanese", setOf("Jpan")),
        LanguageDescriptor(LanguageTag.KOREAN, "Korean", setOf("Kore")),
    )
    private val descriptorsByTag = registeredDescriptors.associateBy(LanguageDescriptor::tag)
    private val officialCodecs = WesternPokemonTextCodecs.all +
        JapanesePokemonTextCodecs.all +
        KoreanGen2PokemonTextCodec.codec
    private val codecsByIdentity = (
        officialCodecs + listOf(
            PokemonTextCodec.gbEnglish,
            PokemonTextCodec.gbaEnglish,
        )
    ).associateBy { it.id to it.version }

    val descriptors: List<LanguageDescriptor> = Collections.unmodifiableList(registeredDescriptors)

    fun descriptor(tag: LanguageTag): LanguageDescriptor? = descriptorsByTag[tag]

    fun codec(codecId: String, codecVersion: Int): PokemonTextCodec? =
        codecsByIdentity[codecId to codecVersion]

    fun candidateCodec(
        language: LanguageTag,
        generation: Int,
        platform: Platform,
        family: EngineFamily? = null,
    ): PokemonTextCodec? {
        if (language == LanguageTag.JAPANESE) {
            return JapanesePokemonTextCodecs.forGeneration(generation, family)
                ?.takeIf { it.supports(generation, platform) }
        }
        return officialCodecs.singleOrNull {
            it.language == language && it.supports(generation, platform)
        }
    }

    fun candidateCodecs(generation: Int, platform: Platform): List<PokemonTextCodec> =
        officialCodecs.filter { it.supports(generation, platform) }

    fun candidateCodec(language: LanguageTag, platform: Platform): PokemonTextCodec? = when (platform) {
        Platform.GB, Platform.GBC -> PokemonTextCodec.gbEnglish.takeIf { language == LanguageTag.ENGLISH }
        Platform.GBA -> PokemonTextCodec.gbaEnglish.takeIf { language == LanguageTag.ENGLISH }
        Platform.UNKNOWN -> null
    }
}

fun RomLanguageManifest.defaultTextCodec(
    generation: Int,
    platform: Platform,
): PokemonTextCodec? = defaultProjection()?.let { projection ->
    LanguageRegistry.codec(projection.codecId, projection.codecVersion)
        ?.takeIf { codec ->
            codec.language == projection.language &&
                codec.supports(generation, platform)
        }
}

fun ResolvedRomLayout.defaultTextCodec(): PokemonTextCodec? =
    languageManifest.defaultTextCodec(generation, platform)
