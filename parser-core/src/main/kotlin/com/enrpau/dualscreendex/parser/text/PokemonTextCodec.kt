package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform

data class DecodedText(
    val text: String,
    val terminated: Boolean,
    val validBytes: Int,
    val contentBytes: Int,
    val validUnits: Int,
    val contentUnits: Int,
    val consumedBytes: Int,
    val glyphUnits: Int,
    val whitespaceUnits: Int,
    val substitutionUnits: Int,
    val controlUnits: Int,
    val invalidUnits: Int,
) {
    val validRatio: Double get() = if (contentUnits == 0) 0.0 else validUnits.toDouble() / contentUnits
}

internal enum class StaticLabelUse {
    GEN2_ORDINARY_ITEM_NAME,
    GEN2_DECLARED_SIGN_TEXT,
}

/** Explicit source ratifications, not an inference from a substitution's display text. */
internal enum class StaticLabelRule(
    val use: StaticLabelUse,
    val rawByte: Int,
    val text: String,
    val byteCount: Int,
) {
    WESTERN_GEN2_POKE(StaticLabelUse.GEN2_ORDINARY_ITEM_NAME, 0x54, "POKé", 1),
    JAPANESE_GEN2_NA_DAKUTEN(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x14, "ナﾞ", 1),
    JAPANESE_GEN2_NO_DAKUTEN(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x18, "ノ゛", 1),
    JAPANESE_GEN2_NI(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x1D, "に ", 1),
    JAPANESE_GEN2_TTE(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x1E, "って", 1),
    JAPANESE_GEN2_WO(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x1F, "を ", 1),
    JAPANESE_GEN2_TA(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x22, "た！", 1),
    JAPANESE_GEN2_KOUGEKI(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x23, "こうげき", 1),
    JAPANESE_GEN2_WA(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x24, "は ", 1),
    JAPANESE_GEN2_NO(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x25, "の ", 1),
    JAPANESE_GEN2_ROUTE(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x35, "ばん どうろ", 1),
    JAPANESE_GEN2_WATASHI(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x36, "わたし", 1),
    JAPANESE_GEN2_KOKO_WA(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x37, "ここは ", 1),
    JAPANESE_GEN2_GA(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x4A, "が ", 1),
    JAPANESE_GEN2_POKEMON(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x54, "ポケモン", 1),
    JAPANESE_GEN2_ELLIPSIS(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x56, "⋯⋯", 1),
    JAPANESE_GEN2_PC(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x5B, "パソコン", 1),
    JAPANESE_GEN2_TM(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x5C, "わざマシン", 1),
    JAPANESE_GEN2_TRAINER(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x5D, "トレーナー", 1),
    JAPANESE_GEN2_ROCKET(StaticLabelUse.GEN2_DECLARED_SIGN_TEXT, 0x5E, "ロケットだん", 1),
}

internal data class StaticLabelDecode(
    val decoded: DecodedText,
    val unratifiedSubstitutionUnits: Int,
)

sealed interface PokemonTextToken {
    val byteCount: Int

    data class Glyph(val text: String, override val byteCount: Int = 1) : PokemonTextToken
    data class Whitespace(val text: String = " ", override val byteCount: Int = 1) : PokemonTextToken
    data class Substitution(val text: String, override val byteCount: Int = 1) : PokemonTextToken
    data class Control(val replacement: String = " ", override val byteCount: Int = 1) : PokemonTextToken
    data class Invalid(override val byteCount: Int = 1) : PokemonTextToken
    data class Terminator(override val byteCount: Int = 1) : PokemonTextToken
}

fun interface PokemonTextTokenDecoder {
    fun decode(rom: RomImage, offset: Int, endExclusive: Int): PokemonTextToken
}

class PokemonTextCodec internal constructor(
    val id: String,
    val version: Int,
    val language: LanguageTag,
    val applicableGenerations: Set<Int>,
    val applicablePlatforms: Set<Platform>,
    val terminator: Int,
    private val tokenDecoder: PokemonTextTokenDecoder,
    staticLabelRules: Collection<StaticLabelRule>,
) {
    // Keep the original constructor, including positional/trailing-SAM callers, strict by default.
    internal constructor(
        id: String,
        version: Int,
        language: LanguageTag,
        applicableGenerations: Set<Int>,
        applicablePlatforms: Set<Platform>,
        terminator: Int,
        tokenDecoder: PokemonTextTokenDecoder,
    ) : this(id, version, language, applicableGenerations, applicablePlatforms, terminator, tokenDecoder, emptyList())

    private val staticLabelRules = java.util.Collections.unmodifiableList(ArrayList(staticLabelRules))
    val name: String get() = id

    init {
        require(id.isNotBlank()) { "codec ID must not be blank" }
        require(version > 0) { "codec version must be positive" }
        require(applicableGenerations.isNotEmpty() && applicableGenerations.all { it > 0 }) {
            "codec applicable generations must be positive"
        }
        require(applicablePlatforms.isNotEmpty() && Platform.UNKNOWN !in applicablePlatforms) {
            "codec must declare at least one known platform"
        }
        require(terminator in 0..0xFF) { "single-byte terminator must be in 0..255" }
    }

    fun supports(generation: Int, platform: Platform): Boolean =
        generation in applicableGenerations && platform in applicablePlatforms

    internal fun decodeToken(
        rom: RomImage,
        offset: Int,
        endExclusive: Int,
    ): PokemonTextToken {
        require(offset in 0 until rom.size) { "text token offset must be in 0 until ${rom.size}" }
        require(endExclusive in offset + 1..rom.size) {
            "text token end must be after offset and within ${rom.size}"
        }
        val remaining = endExclusive - offset
        val decoded = tokenDecoder.decode(rom, offset, endExclusive)
        return if (decoded.byteCount in 1..remaining) decoded else PokemonTextToken.Invalid()
    }

    internal fun isRatifiedStaticLabelSubstitution(
        rom: RomImage,
        offset: Int,
        token: PokemonTextToken.Substitution,
        use: StaticLabelUse,
    ): Boolean = staticLabelRules.any { rule ->
        rule.use == use && rule.byteCount == token.byteCount && rule.text == token.text &&
            rom.u8(offset) == rule.rawByte
    }

    fun decode(bytes: ByteArray): String = decodeDetailed(bytes).text

    fun decodeDetailed(bytes: ByteArray): DecodedText = decodeDetailed(
        rom = RomImage(bytes),
        offset = 0,
        maximumBytes = bytes.size,
        cancellation = ParserCancellationToken.NONE,
    )

    fun decodeDetailed(
        rom: RomImage,
        offset: Int,
        maximumBytes: Int,
        cancellation: ParserCancellationToken,
    ): DecodedText = decodeDetailed(rom, offset, maximumBytes, cancellation, onSubstitution = null)

    internal fun decodeStaticLabel(
        rom: RomImage,
        offset: Int,
        maximumBytes: Int,
        cancellation: ParserCancellationToken,
        use: StaticLabelUse,
    ): StaticLabelDecode {
        var unratified = 0
        val decoded = decodeDetailed(rom, offset, maximumBytes, cancellation) { start, token ->
            if (staticLabelRules.none { rule ->
                    rule.use == use && rule.byteCount == token.byteCount && rule.text == token.text &&
                        rom.u8(start) == rule.rawByte
                }) unratified++
        }
        return StaticLabelDecode(decoded, unratified)
    }

    private fun decodeDetailed(
        rom: RomImage,
        offset: Int,
        maximumBytes: Int,
        cancellation: ParserCancellationToken,
        onSubstitution: ((Int, PokemonTextToken.Substitution) -> Unit)?,
    ): DecodedText {
        require(offset in 0..rom.size) { "text offset must be in 0..${rom.size}" }
        require(maximumBytes >= 0) { "maximum text bytes must not be negative" }
        val endExclusive = minOf(rom.size.toLong(), offset.toLong() + maximumBytes.toLong()).toInt()
        val output = StringBuilder()
        var cursor = offset
        var validBytes = 0
        var contentBytes = 0
        var validUnits = 0
        var contentUnits = 0
        var glyphUnits = 0
        var whitespaceUnits = 0
        var substitutionUnits = 0
        var controlUnits = 0
        var invalidUnits = 0
        var terminated = false

        while (cursor < endExclusive) {
            cancellation.throwIfCancellationRequested()
            val token = decodeToken(rom, cursor, endExclusive)
            cursor += token.byteCount
            when (token) {
                is PokemonTextToken.Glyph -> {
                    output.append(token.text)
                    validBytes += token.byteCount
                    contentBytes += token.byteCount
                    validUnits++
                    contentUnits++
                    glyphUnits++
                }
                is PokemonTextToken.Whitespace -> {
                    output.append(token.text)
                    validBytes += token.byteCount
                    contentBytes += token.byteCount
                    validUnits++
                    contentUnits++
                    whitespaceUnits++
                }
                is PokemonTextToken.Substitution -> {
                    onSubstitution?.invoke(cursor - token.byteCount, token)
                    output.append(token.text)
                    validBytes += token.byteCount
                    contentBytes += token.byteCount
                    validUnits++
                    contentUnits++
                    substitutionUnits++
                }
                is PokemonTextToken.Control -> {
                    output.append(token.replacement)
                    validBytes += token.byteCount
                    contentBytes += token.byteCount
                    validUnits++
                    contentUnits++
                    controlUnits++
                }
                is PokemonTextToken.Invalid -> {
                    contentBytes += token.byteCount
                    contentUnits++
                    invalidUnits++
                }
                is PokemonTextToken.Terminator -> {
                    terminated = true
                    break
                }
            }
        }

        return DecodedText(
            text = output.toString().replace(WHITESPACE, " ").trim(),
            terminated = terminated,
            validBytes = validBytes,
            contentBytes = contentBytes,
            validUnits = validUnits,
            contentUnits = contentUnits,
            consumedBytes = cursor - offset,
            glyphUnits = glyphUnits,
            whitespaceUnits = whitespaceUnits,
            substitutionUnits = substitutionUnits,
            controlUnits = controlUnits,
            invalidUnits = invalidUnits,
        )
    }

    companion object {
        val gbEnglish = singleByte(
            id = "gb-english",
            language = LanguageTag.ENGLISH,
            applicableGenerations = setOf(1, 2),
            applicablePlatforms = setOf(Platform.GB, Platform.GBC),
            terminator = 0x50,
            controlValues = (0x4B..0x4F).toSet(),
        ) { value ->
            when (value) {
                in 0x80..0x99 -> ('A'.code + value - 0x80).toChar()
                in 0xA0..0xB9 -> ('a'.code + value - 0xA0).toChar()
                in 0xF6..0xFF -> ('0'.code + value - 0xF6).toChar()
                0x7F -> ' '
                in 0x4B..0x4F -> ' '
                0xE0 -> '\''
                0xE3 -> '-'
                0xE6 -> '?'
                0xE7 -> '!'
                0xE8 -> '.'
                0xE9 -> '&'
                0xEA -> 'é'
                0xF2 -> '.'
                else -> null
            }
        }

        val gbaEnglish = singleByte(
            id = "gba-english",
            language = LanguageTag.ENGLISH,
            applicableGenerations = setOf(3),
            applicablePlatforms = setOf(Platform.GBA),
            terminator = 0xFF,
            controlValues = setOf(0xFA, 0xFB, 0xFE),
        ) { value ->
            when (value) {
                0x00 -> ' '
                0x01 -> 'À'
                0x02 -> 'Á'
                0x03 -> 'Â'
                0x04 -> 'Ç'
                0x05 -> 'È'
                0x06 -> 'É'
                0x07 -> 'Ê'
                0x08 -> 'Ë'
                0x09 -> 'Ì'
                0x0B -> 'Î'
                0x0C -> 'Ï'
                0x0D -> 'Ò'
                0x0E -> 'Ó'
                0x0F -> 'Ô'
                0x10 -> 'Œ'
                0x11 -> 'Ù'
                0x12 -> 'Ú'
                0x13 -> 'Û'
                0x14 -> 'Ñ'
                0x15 -> 'ß'
                0x16 -> 'à'
                0x17 -> 'á'
                0x19 -> 'ç'
                0x1A -> 'è'
                0x1B -> 'é'
                0x1C -> 'ê'
                0x1D -> 'ë'
                0x1E -> 'ì'
                0x20 -> 'î'
                0x21 -> 'ï'
                0x22 -> 'ò'
                0x23 -> 'ó'
                0x24 -> 'ô'
                0x25 -> 'œ'
                0x26 -> 'ù'
                0x27 -> 'ú'
                0x28 -> 'û'
                0x29 -> 'ñ'
                0x2D -> '&'
                0x2E -> '+'
                0x35 -> '='
                0x36 -> ';'
                0x51 -> '¿'
                0x52 -> '¡'
                0x5B -> '%'
                0x5C -> '('
                0x5D -> ')'
                in 0xA1..0xAA -> ('0'.code + value - 0xA1).toChar()
                in 0xBB..0xD4 -> ('A'.code + value - 0xBB).toChar()
                in 0xD5..0xEE -> ('a'.code + value - 0xD5).toChar()
                0xAB -> '!'
                0xAC -> '?'
                0xAD -> '.'
                0xAE -> '-'
                0xAF -> '·'
                0xB0 -> '…'
                0xB4 -> '\''
                0xB5 -> '♂'
                0xB6 -> '♀'
                0xB7 -> '¥'
                0xB8 -> ','
                0xB9 -> '×'
                0xBA -> '/'
                0xFA, 0xFB, 0xFE -> ' '
                else -> null
            }
        }

        private fun singleByte(
            id: String,
            language: LanguageTag,
            applicableGenerations: Set<Int>,
            applicablePlatforms: Set<Platform>,
            terminator: Int,
            controlValues: Set<Int>,
            decoder: (Int) -> Char?,
        ): PokemonTextCodec = PokemonTextCodec(
            id = id,
            version = 1,
            language = language,
            applicableGenerations = applicableGenerations,
            applicablePlatforms = applicablePlatforms,
            terminator = terminator,
            tokenDecoder = PokemonTextTokenDecoder { rom, offset, _ ->
                val value = rom.u8(offset)
                when {
                    value == terminator -> PokemonTextToken.Terminator()
                    value in controlValues -> PokemonTextToken.Control()
                    else -> decoder(value)?.let { character ->
                        if (character.isWhitespace()) PokemonTextToken.Whitespace(character.toString())
                        else PokemonTextToken.Glyph(character.toString())
                    } ?: PokemonTextToken.Invalid()
                }
            },
        )

        private val WHITESPACE = Regex("\\s+")
    }
}
