package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/**
 * Synthetic tables, not extracted ROM data. Glyph vectors are independently transcribed from:
 * - scr-trees/pokegold_jpcrystalvc f2b5db1deb0b8f2009d7e9d50b3bcb05ef8a9f53/charmap.asm
 * - pret/pokeruby 63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1/charmap.txt
 * - Narishma-gb/pokegold-kr 7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4/constants/charmap
 * Native labels and five-byte indexing were separately corroborated through exact official controls.
 * The Japanese Gen II source charmap is a glyph oracle, not a native type-name table oracle.
 */
internal object CompiledNativeTypeNameFixtures {
    data class Label(
        val role: TypeSemanticRole,
        val text: String,
        val gbHex: String,
        val gbaHex: String = "",
    ) {
        fun encoded(generation: Int): ByteArray = if (generation == 3) {
            hex(gbaHex) + byteArrayOf(0xFF.toByte())
        } else {
            hex(gbHex) + byteArrayOf(0x50)
        }
    }

    data class Fixture(
        val bytes: ByteArray,
        val generation: Int,
        val codec: PokemonTextCodec,
        val layout: TableLayout,
        val expected: Map<Int, Label>,
    ) {
        fun session(): RomAnalysisSession = RomAnalysisSession(
            RomImage(bytes),
            RomHeader(
                when (generation) {
                    1 -> Platform.GB
                    2 -> Platform.GBC
                    else -> Platform.GBA
                },
                "SYNTHETIC TYPES",
            ),
        )

        fun replaceName(id: Int, encoded: ByteArray) {
            val offset = if (layout.valuesArePointers) GB_NAMES + id * GB_NAME_STRIDE
                else layout.offset + id * layout.recordSize
            val width = if (layout.valuesArePointers) 16 else layout.recordSize
            require(encoded.size <= width)
            bytes.fill(0xFF.toByte(), offset, offset + width)
            encoded.copyInto(bytes, offset)
        }
    }

    val japanese = listOf(
        Label(TypeSemanticRole.NORMAL, "ノーマル", "98E39DA6", "69AE6F79"),
        Label(TypeSemanticRole.FIGHTING, "かくとう", "B6B8C4B3", "06081403"),
        Label(TypeSemanticRole.FLYING, "ひこう", "CBBAB3", "1B0A03"),
        Label(TypeSemanticRole.POISON, "どく", "34B8", "4508"),
        Label(TypeSemanticRole.GROUND, "じめん", "2CD2DE", "3D222E"),
        Label(TypeSemanticRole.ROCK, "いわ", "B2DC", "022C"),
        Label(TypeSemanticRole.BUG, "むし", "D1BC", "210C"),
        Label(TypeSemanticRole.GHOST, "ゴースト", "09E38C93", "8BAE5D64"),
        Label(TypeSemanticRole.STEEL, "はがね", "CA26C8", "1A3718"),
        Label(TypeSemanticRole.MYSTERY, "？？？", "E6E6E6", "ACACAC"),
        Label(TypeSemanticRole.FIRE, "ほのお", "CEC9B5", "1E1905"),
        Label(TypeSemanticRole.WATER, "みず", "D02D", "203E"),
        Label(TypeSemanticRole.GRASS, "くさ", "B8BB", "080B"),
        Label(TypeSemanticRole.ELECTRIC, "でんき", "33DEB7", "442E07"),
        Label(TypeSemanticRole.PSYCHIC, "エスパー", "838C40E3", "545D9BAE"),
        Label(TypeSemanticRole.ICE, "こおり", "BAB5D8", "0A0528"),
        Label(TypeSemanticRole.DRAGON, "ドラゴン", "13A509AB", "95778B7E"),
        Label(TypeSemanticRole.DARK, "あく", "B1B8", "0108"),
    )

    val korean = listOf(
        Label(TypeSemanticRole.NORMAL, "노말", "028B044B"),
        Label(TypeSemanticRole.FIGHTING, "격투", "013D09C5"),
        Label(TypeSemanticRole.FLYING, "비행", "05610A80"),
        Label(TypeSemanticRole.POISON, "독", "0316"),
        Label(TypeSemanticRole.GROUND, "땅", "0375"),
        Label(TypeSemanticRole.ROCK, "바위", "04D90777"),
        Label(TypeSemanticRole.BUG, "벌레", "04FA03E9"),
        Label(TypeSemanticRole.GHOST, "고스트", "014D064A09DE"),
        Label(TypeSemanticRole.STEEL, "강철", "010D08B6"),
        Label(TypeSemanticRole.MYSTERY, "???", "0B670B670B67"),
        Label(TypeSemanticRole.FIRE, "화염", "0AAD0710"),
        Label(TypeSemanticRole.WATER, "물", "04B0"),
        Label(TypeSemanticRole.GRASS, "풀", "0A3E"),
        Label(TypeSemanticRole.ELECTRIC, "전기", "07CC01B2"),
        Label(TypeSemanticRole.PSYCHIC, "에스퍼", "0701064A0A0B"),
        Label(TypeSemanticRole.ICE, "얼음", "06F3078D"),
        Label(TypeSemanticRole.DRAGON, "드래곤", "034503D1014F"),
        Label(TypeSemanticRole.DARK, "악", "06C7"),
    )

    fun gb(codec: PokemonTextCodec, generation: Int = 2): Fixture {
        require(generation in 1..2)
        val labels = if (codec.language == LanguageTag.KOREAN) korean else japanese
        val ids = listOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 19, 20, 21, 22, 23, 24, 25, 26, 27)
        val expected = ids.zip(labels).filterNot { (_, label) ->
            generation == 1 && label.role in setOf(
                TypeSemanticRole.STEEL, TypeSemanticRole.MYSTERY, TypeSemanticRole.DARK,
            )
        }.toMap()
        val count = if (generation == 1) 27 else 28
        val fixture = Fixture(
            ByteArray(0xC000), generation, codec,
            TableLayout(GB_TABLE, count, 2, bank = 2, valuesArePointers = true), expected,
        )
        repeat(count) { id ->
            writeU16(fixture.bytes, GB_TABLE + id * 2, 0x4000 + GB_NAMES + id * GB_NAME_STRIDE - 0x8000)
            fixture.replaceName(id, (expected[id] ?: labels.first()).encoded(generation))
        }
        // ADD A; LD HL,table; LD E,A; LD D,0; ADD HL,DE; LD A,[HLI]; LD E,A; LD D,[HL].
        hex("8721").copyInto(fixture.bytes, 0x8200)
        writeU16(fixture.bytes, 0x8202, GB_TABLE - 0x4000)
        hex("5F1600192A5F56").copyInto(fixture.bytes, 0x8204)
        return fixture
    }

    fun gba(codec: PokemonTextCodec, laterRegisters: Boolean = false): Fixture {
        val fixture = Fixture(
            ByteArray(0x2000), 3, codec, TableLayout(GBA_TABLE, 18, 5),
            japanese.withIndex().associate { it.index to it.value },
        )
        putGbaTable(fixture.bytes, GBA_TABLE)
        putGbaConsumer(fixture.bytes, GBA_CONSUMER, GBA_TABLE, laterRegisters)
        return fixture
    }

    fun putGbaTable(bytes: ByteArray, root: Int) {
        japanese.forEachIndexed { id, label ->
            bytes.fill(0, root + id * 5, root + (id + 1) * 5)
            label.encoded(3).copyInto(bytes, root + id * 5)
        }
    }

    fun putGbaConsumer(bytes: ByteArray, site: Int, root: Int, laterRegisters: Boolean = false) {
        require(site % 4 == 0)
        // Synthetic equivalents of the observed Ruby and later native type*4+type consumers.
        // LSLS r1,type,#2; ADDS r1,r1,type; LDR type,[PC,literal]; ADDS r1,r1,type; BX LR.
        writeU16(bytes, site, if (laterRegisters) 0x0091 else 0x0081)
        writeU16(bytes, site + 2, if (laterRegisters) 0x1889 else 0x1809)
        writeU16(bytes, site + 4, if (laterRegisters) 0x4A06 else 0x4806)
        writeU16(bytes, site + 6, if (laterRegisters) 0x1889 else 0x1809)
        writeU16(bytes, site + 8, 0x4770)
        writeU32(bytes, site + 0x20, 0x08000000 + root)
    }

    fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }

    const val GB_TABLE = 0x9000
    const val GBA_TABLE = 0x400
    const val GBA_CONSUMER = 0x200
    private const val GB_NAMES = 0x9200
    private const val GB_NAME_STRIDE = 0x20
}
