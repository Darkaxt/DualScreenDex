package com.enrpau.dualscreendex.parser.language

import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
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
    private val codecsByIdentity = (
        WesternPokemonTextCodecs.all + listOf(
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
    ): PokemonTextCodec? = WesternPokemonTextCodecs.forLanguage(language, generation)
        ?.takeIf { it.supports(generation, platform) }

    fun candidateCodecs(generation: Int, platform: Platform): List<PokemonTextCodec> =
        WesternPokemonTextCodecs.all.filter { it.supports(generation, platform) }

    fun candidateCodec(language: LanguageTag, platform: Platform): PokemonTextCodec? = when (platform) {
        Platform.GB, Platform.GBC -> PokemonTextCodec.gbEnglish.takeIf { language == LanguageTag.ENGLISH }
        Platform.GBA -> PokemonTextCodec.gbaEnglish.takeIf { language == LanguageTag.ENGLISH }
        Platform.UNKNOWN -> null
    }
}

fun ResolvedRomLayout.defaultTextCodec(): PokemonTextCodec? =
    languageManifest.defaultProjection()?.let { projection ->
        LanguageRegistry.codec(projection.codecId, projection.codecVersion)
            ?.takeIf { codec ->
                codec.language == projection.language &&
                    codec.supports(generation, platform)
            }
    }
