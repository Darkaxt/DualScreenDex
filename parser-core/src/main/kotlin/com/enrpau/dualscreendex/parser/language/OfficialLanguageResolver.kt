package com.enrpau.dualscreendex.parser.language

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class OfficialLanguageCandidate(
    val language: LanguageTag,
    val evidence: LanguageEvidence,
)

/** Generates official locale candidates without treating regional metadata as final language authority. */
internal object OfficialLanguageResolver {
    fun headerCandidate(header: RomHeader, generation: Int): OfficialLanguageCandidate? {
        val language = if (
            generation == 1 &&
            header.platform in GB_PLATFORMS &&
            header.gbDestinationCode == JAPANESE_DESTINATION_CODE
        ) {
            LanguageTag.JAPANESE
        } else {
            val marker = when {
                generation == 3 && header.platform == Platform.GBA -> header.gameCode
                    ?.takeIf { code -> code.length == 4 && code.take(3) in GBA_PRODUCT_PREFIXES }
                    ?.last()
                generation == 2 && header.platform == Platform.GBC -> header.gbManufacturerCode
                    ?.takeIf { code -> code.length == 4 && code.take(3) in GEN2_PRODUCT_PREFIXES }
                    ?.last()
                generation == 1 && header.platform == Platform.GBC -> header.gbManufacturerCode
                    ?.takeIf { code -> code.length == 4 && code.take(3) in GEN1_PRODUCT_PREFIXES }
                    ?.last()
                else -> null
            } ?: return null
            LANGUAGE_BY_MARKER[marker.uppercaseChar()] ?: return null
        }
        if (language == LanguageTag.KOREAN && header.gbManufacturerCode !in KOREAN_GEN2_PRODUCT_CODES) {
            return null
        }
        if (LanguageRegistry.candidateCodecs(generation, header.platform).none { it.language == language }) {
            return null
        }
        return OfficialLanguageCandidate(
            language = language,
            evidence = LanguageEvidence(
                kind = LanguageEvidenceKind.HEADER_REGION_HINT,
                summary = "recognized header region metadata seeds a ${language.value} candidate",
                confidence = HEADER_HINT_CONFIDENCE,
            ),
        )
    }

    fun preferredProbeCodec(
        rom: RomImage,
        header: RomHeader,
        generation: Int,
        family: EngineFamily? = null,
        cancellation: ParserCancellationToken,
    ): PokemonTextCodec {
        val language = headerCandidate(header, generation)?.language
            ?: redBlueMenuLanguage(rom, header, generation, cancellation)
            ?: LanguageTag.ENGLISH
        return requireNotNull(
            LanguageRegistry.candidateCodec(language, generation, header.platform, family),
        ) {
            "${header.platform} generation $generation has no registered ${language.value} text codec candidate"
        }
    }

    /** Header/menu metadata orders trials only; each codec must establish its own table authority. */
    fun probeCodecs(
        rom: RomImage, header: RomHeader, generation: Int, family: EngineFamily,
        cancellation: ParserCancellationToken,
    ): List<PokemonTextCodec> {
        cancellation.throwIfCancellationRequested()
        return (listOf(preferredProbeCodec(rom, header, generation, family, cancellation)) +
            LanguageRegistry.candidateCodecs(generation, header.platform)).distinctBy { it.id }
    }

    private fun redBlueMenuLanguage(
        rom: RomImage,
        header: RomHeader,
        generation: Int,
        cancellation: ParserCancellationToken,
    ): LanguageTag? {
        if (generation != 1 || header.platform !in GB_PLATFORMS ||
            RED_BLUE_TITLES.none { header.title.uppercase().startsWith(it) }
        ) return null
        return RED_BLUE_MENU_LABELS.entries.filter { (_, labels) ->
            labels.all { label -> rom.contains(encodeGb(label), cancellation) }
        }.singleOrNull()?.key
    }

    private fun RomImage.contains(pattern: ByteArray, cancellation: ParserCancellationToken): Boolean {
        var found = false
        visitMatches(
            pattern = pattern,
            onCheck = cancellation::throwIfCancellationRequested,
        ) {
            found = true
            false
        }
        return found
    }

    private fun encodeGb(value: String): ByteArray = ByteArray(value.length) { index ->
        when (val character = value[index]) {
            ' ' -> 0x7F
            else -> 0x80 + character.code - 'A'.code
        }.toByte()
    }

    private const val HEADER_HINT_CONFIDENCE = 60
    private const val JAPANESE_DESTINATION_CODE = 0
    private val GB_PLATFORMS = setOf(Platform.GB, Platform.GBC)
    private val GBA_PRODUCT_PREFIXES = setOf("AXV", "AXP", "BPR", "BPG", "BPE")
    private val GEN2_PRODUCT_PREFIXES = setOf("AAU", "AAX", "BXT", "BYT")
    private val KOREAN_GEN2_PRODUCT_CODES = setOf("AAUK", "AAXK")
    private val GEN1_PRODUCT_PREFIXES = setOf("APS")
    private val LANGUAGE_BY_MARKER = mapOf(
        'E' to LanguageTag.ENGLISH,
        'F' to LanguageTag.FRENCH,
        'D' to LanguageTag.GERMAN,
        'I' to LanguageTag.ITALIAN,
        'S' to LanguageTag.SPANISH,
        'J' to LanguageTag.JAPANESE,
        'K' to LanguageTag.KOREAN,
    )
    private val RED_BLUE_TITLES = setOf("POKEMON RED", "POKEMON BLUE")
    private val RED_BLUE_MENU_LABELS = mapOf(
        LanguageTag.ENGLISH to listOf("NEW GAME", "OPTION"),
        LanguageTag.FRENCH to listOf("NOUVEAU JEU", "OPTIONS"),
        LanguageTag.GERMAN to listOf("NEUES SPIEL", "OPTION"),
        LanguageTag.ITALIAN to listOf("NUOVO GIOCO", "OPZIONI"),
        LanguageTag.SPANISH to listOf("JUEGO NUEVO", "OPCIONES"),
    )
}
