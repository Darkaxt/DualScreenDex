package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform

/** Versioned codecs for the official Japanese Gen I-III text dialects. */
object JapanesePokemonTextCodecs {
    val gen1RedBlue by lazy {
        gbCodec(
            id = "gb-gen1-ja-red-blue",
            generation = 1,
            glyphs = GEN1_RED_BLUE_GLYPHS,
            substitutions = GEN1_RED_BLUE_SUBSTITUTIONS,
            controls = GEN1_CONTROLS,
        )
    }
    val gen1Yellow by lazy {
        gbCodec(
            id = "gb-gen1-ja-yellow",
            generation = 1,
            glyphs = GEN1_YELLOW_GLYPHS,
            substitutions = GEN1_YELLOW_SUBSTITUTIONS,
            controls = GEN1_YELLOW_CONTROLS,
        )
    }
    val gen2 by lazy {
        gbCodec(
            id = "gb-gen2-ja",
            generation = 2,
            glyphs = GEN2_GLYPHS,
            substitutions = GEN2_SUBSTITUTIONS,
            controls = GEN2_CONTROLS,
        )
    }
    val gen3RubySapphire by lazy {
        gbaCodec(
            id = "gba-gen3-ja-ruby-sapphire",
            glyphs = GEN3_GLYPHS + RUBY_SAPPHIRE_DUPLICATE_ARROWS,
            twoByteControls = GBA_COMMON_TWO_BYTE_CONTROLS,
            extendedControlParameters = GBA_RUBY_SAPPHIRE_EXTENDED_CONTROL_PARAMETERS,
        )
    }
    val gen3Later by lazy {
        gbaCodec(
            id = "gba-gen3-ja-emerald-frlg",
            glyphs = GEN3_GLYPHS,
            twoByteControls = GBA_LATER_TWO_BYTE_CONTROLS,
            extendedControlParameters = GBA_LATER_EXTENDED_CONTROL_PARAMETERS,
        )
    }

    val all: List<PokemonTextCodec> by lazy {
        listOf(gen1RedBlue, gen1Yellow, gen2, gen3RubySapphire, gen3Later)
    }

    fun forGeneration(generation: Int, family: EngineFamily? = null): PokemonTextCodec? = when (generation) {
        1 -> when (family) {
            EngineFamily.RED_BLUE -> gen1RedBlue
            EngineFamily.YELLOW -> gen1Yellow
            else -> null
        }
        2 -> gen2
        3 -> when (family) {
            EngineFamily.RUBY_SAPPHIRE -> gen3RubySapphire
            EngineFamily.EMERALD,
            EngineFamily.FIRERED_LEAFGREEN,
            -> gen3Later
            else -> null
        }
        else -> null
    }

    private fun gbaCodec(
        id: String,
        glyphs: Map<Int, String>,
        twoByteControls: Set<Int>,
        extendedControlParameters: Map<Int, Int>,
    ): PokemonTextCodec = PokemonTextCodec(
        id = id,
        version = 1,
        language = LanguageTag.JAPANESE,
        applicableGenerations = setOf(3),
        applicablePlatforms = setOf(Platform.GBA),
        terminator = GBA_TERMINATOR,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
            decodeGen3Token(
                rom = rom,
                offset = offset,
                endExclusive = endExclusive,
                glyphs = glyphs,
                twoByteControls = twoByteControls,
                extendedControlParameters = extendedControlParameters,
            )
        },
    )

    private fun gbCodec(
        id: String,
        generation: Int,
        glyphs: Map<Int, String>,
        substitutions: Map<Int, String>,
        controls: Set<Int>,
    ): PokemonTextCodec = PokemonTextCodec(
        id = id,
        version = 1,
        language = LanguageTag.JAPANESE,
        applicableGenerations = setOf(generation),
        applicablePlatforms = setOf(Platform.GB, Platform.GBC),
        terminator = GB_TERMINATOR,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, _ ->
            val value = rom.u8(offset)
            when {
                value == GB_TERMINATOR -> PokemonTextToken.Terminator()
                value == GB_WHITESPACE -> PokemonTextToken.Whitespace()
                value in substitutions -> PokemonTextToken.Substitution(requireNotNull(substitutions[value]))
                value in controls -> PokemonTextToken.Control()
                value in glyphs -> PokemonTextToken.Glyph(requireNotNull(glyphs[value]))
                else -> PokemonTextToken.Invalid()
            }
        },
    )

    private fun decodeGen3Token(
        rom: RomImage,
        offset: Int,
        endExclusive: Int,
        glyphs: Map<Int, String>,
        twoByteControls: Set<Int>,
        extendedControlParameters: Map<Int, Int>,
    ): PokemonTextToken {
        val value = rom.u8(offset)
        return when {
            value == GBA_TERMINATOR -> PokemonTextToken.Terminator()
            value == GBA_WHITESPACE -> PokemonTextToken.Whitespace()
            value in glyphs -> PokemonTextToken.Glyph(requireNotNull(glyphs[value]))
            value in GBA_SINGLE_BYTE_CONTROLS -> PokemonTextToken.Control()
            value in twoByteControls -> fixedWidthControl(offset, endExclusive, byteCount = 2)
            value == GBA_EXTENDED_CONTROL -> extendedControl(
                rom = rom,
                offset = offset,
                endExclusive = endExclusive,
                parameterCounts = extendedControlParameters,
            )
            else -> PokemonTextToken.Invalid()
        }
    }

    private fun fixedWidthControl(
        offset: Int,
        endExclusive: Int,
        byteCount: Int,
    ): PokemonTextToken {
        val remainingBytes = endExclusive - offset
        return if (byteCount <= remainingBytes) {
            PokemonTextToken.Control(byteCount = byteCount)
        } else {
            PokemonTextToken.Invalid(byteCount = remainingBytes)
        }
    }

    private fun extendedControl(
        rom: RomImage,
        offset: Int,
        endExclusive: Int,
        parameterCounts: Map<Int, Int>,
    ): PokemonTextToken {
        if (offset + 1 >= endExclusive) return PokemonTextToken.Invalid()
        val parameterByteCount = parameterCounts[rom.u8(offset + 1)]
            ?: return PokemonTextToken.Invalid(byteCount = 2)
        return fixedWidthControl(offset, endExclusive, byteCount = parameterByteCount + 2)
    }

    private fun glyphRange(start: Int, value: String): Map<Int, String> =
        value.mapIndexed { index, character -> start + index to character.toString() }.toMap()

    private const val GB_TERMINATOR = 0x50
    private const val GB_WHITESPACE = 0x7F
    private const val GBA_TERMINATOR = 0xFF
    private const val GBA_WHITESPACE = 0x00
    private const val GBA_EXTENDED_CONTROL = 0xFC

    private val COMMON_GB_GLYPHS =
        glyphRange(0x05, "ガギグゲゴザジズゼゾダヂヅデド") +
            glyphRange(0x19, "バビブボ") +
            glyphRange(0x26, "がぎぐげござじずぜぞだぢづでど") +
            glyphRange(0x3A, "ばびぶべぼ") +
            glyphRange(0x40, "パピプポ") +
            glyphRange(0x44, "ぱぴぷぺぽ") +
            glyphRange(0x80, "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフ") +
            mapOf(0x9C to "ホ") +
            glyphRange(0x9D, "マミムメモヤユヨラ") +
            glyphRange(0xA6, "ルレロワヲンッャュョィ") +
            glyphRange(0xB1, "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんっゃゅょ") +
            glyphRange(0xE3, "ーﾟﾞ？！。") +
            glyphRange(0xE9, "ァゥェ")

    private val GEN1_RED_BLUE_GLYPHS = COMMON_GB_GLYPHS +
        glyphRange(0x70, "「」『』・…ぁぇぉ┌─┐│└┘") +
        mapOf(
            0xEC to "▷",
            0xED to "▶",
            0xEE to "▼",
            0xEF to "♂",
            0xF0 to "円",
            0xF1 to "×",
            0xF2 to "⠄",
            0xF3 to "/",
            0xF4 to ",",
            0xF5 to "♀",
        ) + glyphRange(0xF6, "0123456789")

    private val GEN1_YELLOW_GLYPHS = GEN1_RED_BLUE_GLYPHS +
        mapOf(
            0x74 to "·",
            0x75 to "⋯",
            0xE4 to "゜",
            0xE5 to "゛",
            0xF2 to "．",
            0xF3 to "／",
            0xF4 to "ォ",
        ) + glyphRange(0xF6, "０１２３４５６７８９")

    private val GEN1_RED_BLUE_SUBSTITUTIONS = mapOf(
        0x54 to "ポケモン",
        0x56 to "……",
        0x5B to "パソコン",
        0x5C to "わざマシン",
        0x5D to "トレーナー",
        0x5E to "ロケットだん",
    )
    private val GEN1_YELLOW_SUBSTITUTIONS = GEN1_RED_BLUE_SUBSTITUTIONS + mapOf(
        0x4A to "が ",
        0x56 to "⋯⋯",
    )

    private val GEN1_CONTROLS = setOf(
        0x49, 0x4B, 0x4C, 0x4E, 0x4F,
        0x51, 0x52, 0x53, 0x55, 0x57, 0x58, 0x59, 0x5A, 0x5F,
    )
    private val GEN1_YELLOW_CONTROLS = GEN1_CONTROLS + 0x00

    private val GEN2_GLYPHS = COMMON_GB_GLYPHS + mapOf(
        0x6E to "ぃ",
        0x6F to "ぅ",
        0x70 to "「",
        0x71 to "」",
        0x72 to "『",
        0x73 to "』",
        0x74 to "・",
        0x75 to "⋯",
        0x76 to "ぁ",
        0x77 to "ぇ",
        0x78 to "ぉ",
        0xEF to "♂",
        0xF5 to "♀",
        0xF0 to "円",
        0xF2 to "．",
        0xF3 to "／",
        0xF4 to "ォ",
    ) + glyphRange(0xF6, "０１２３４５６７８９")

    private val GEN2_SUBSTITUTIONS = mapOf(
        0x14 to "ナﾞ",
        0x18 to "ノ゛",
        0x1D to "に ",
        0x1E to "って",
        0x1F to "を ",
        0x22 to "た！",
        0x23 to "こうげき",
        0x24 to "は ",
        0x25 to "の ",
        0x35 to "ばん どうろ",
        0x36 to "わたし",
        0x37 to "ここは ",
        0x4A to "が ",
        0x54 to "ポケモン",
        0x56 to "⋯⋯",
        0x5B to "パソコン",
        0x5C to "わざマシン",
        0x5D to "トレーナー",
        0x5E to "ロケットだん",
    )

    private val GEN2_CONTROLS = setOf(
        0x00, 0x16, 0x38, 0x39, 0x3F, 0x49, 0x4B, 0x4C, 0x4E, 0x4F,
        0x51, 0x52, 0x53, 0x55, 0x57, 0x58, 0x59, 0x5A, 0x5F,
    )

    private val GEN3_GLYPHS =
        glyphRange(0x01, "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんぁぃぅぇぉゃゅょがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽっ") +
            glyphRange(0x51, "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンァィゥェォャュョガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポッ") +
            glyphRange(0xA1, "0123456789") +
            mapOf(
                0xAB to "！",
                0xAC to "？",
                0xAD to "。",
                0xAE to "ー",
                0xAF to "·",
                0xB0 to "⋯",
                0xB1 to "“",
                0xB2 to "”",
                0xB3 to "‘",
                0xB4 to "’",
                0xB5 to "♂",
                0xB6 to "♀",
                0xB7 to "¥",
                0xB8 to ",",
                0xB9 to "×",
                0xBA to "/",
            ) +
            glyphRange(0xBB, "ABCDEFGHIJKLMNOPQRSTUVWXYZ") +
            glyphRange(0xD5, "abcdefghijklmnopqrstuvwxyz") +
            mapOf(
                0xEF to "▶",
                0xF0 to ":",
                0xF1 to "Ä",
                0xF2 to "Ö",
                0xF3 to "Ü",
                0xF4 to "ä",
                0xF5 to "ö",
                0xF6 to "ü",
            )

    private val RUBY_SAPPHIRE_DUPLICATE_ARROWS = mapOf(
        0xF7 to "↑",
        0xF8 to "↓",
        0xF9 to "←",
    )
    private val GBA_SINGLE_BYTE_CONTROLS = setOf(0xFA, 0xFB, 0xFE)
    private val GBA_COMMON_TWO_BYTE_CONTROLS = setOf(0xFD)
    private val GBA_LATER_TWO_BYTE_CONTROLS =
        GBA_COMMON_TWO_BYTE_CONTROLS + RUBY_SAPPHIRE_DUPLICATE_ARROWS.keys
    private val GBA_COMMON_EXTENDED_CONTROL_PARAMETERS = mapOf(
        0x00 to 0,
        0x01 to 1,
        0x02 to 1,
        0x03 to 1,
        0x04 to 3,
        0x05 to 1,
        0x06 to 1,
        0x07 to 0,
        0x08 to 1,
        0x09 to 0,
        0x0A to 0,
        0x0B to 2,
        0x0C to 1,
        0x0D to 1,
        0x0E to 1,
        0x0F to 0,
        0x10 to 2,
        0x11 to 1,
        0x12 to 1,
        0x13 to 1,
        0x14 to 1,
        0x15 to 0,
        0x16 to 0,
    )
    private val GBA_RUBY_SAPPHIRE_EXTENDED_CONTROL_PARAMETERS =
        GBA_COMMON_EXTENDED_CONTROL_PARAMETERS
    private val GBA_LATER_EXTENDED_CONTROL_PARAMETERS =
        GBA_COMMON_EXTENDED_CONTROL_PARAMETERS + mapOf(
            0x17 to 0,
            0x18 to 0,
        )
}
