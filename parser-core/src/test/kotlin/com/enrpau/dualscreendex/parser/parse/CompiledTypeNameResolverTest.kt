package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndexFactory
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompiledTypeNameResolverTest {
    @Test
    fun resolvesGen2BankLocalPointerTableFromItsCompiledConsumer() {
        val bytes = ByteArray(0xC000)
        val table = 0x9000
        val labels = listOf(
            "NORMAL", "FIGHTING", "FLYING", "POISON", "GROUND", "ROCK", "BIRD", "BUG", "GHOST",
            "STEEL", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "NORMAL",
            "NORMAL", "???", "FIRE", "WATER", "GRASS", "ELECTRIC", "PSYCHIC", "ICE", "DRAGON", "DARK",
        )
        labels.forEachIndexed { id, label ->
            val target = 0x9200 + id * 12
            writeU16(bytes, table + id * 2, 0x4000 + target - 0x8000)
            encodeGb(bytes, target, label)
        }
        putGbTypeNameConsumer(bytes, 0x8200, table)
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "TYPE TEST"))

        val resolved = requireNotNull(
            CompiledTypeNameResolver.resolve(session, 2, WesternPokemonTextCodecs.gen2English),
        )
        val decoded = CompiledTypeNameResolver.decode(
            session.rom,
            generation = 2,
            layout = resolved,
            codec = WesternPokemonTextCodecs.gen2English,
        )

        assertEquals(table, resolved.offset)
        assertEquals(2, resolved.bank)
        assertEquals("FIRE", decoded?.get(20)?.name)
        assertEquals(TypeSemanticRole.FIRE, decoded?.get(20)?.semanticRole)
        assertEquals(TypeSemanticRole.DARK, decoded?.get(27)?.semanticRole)
        assertNull(decoded?.get(6))
    }

    @Test
    fun acceptsOfficialSpanishGen2MysteryTypeLabel() {
        val bytes = ByteArray(0xC000)
        val table = 0x9000
        val labels = listOf(
            "NORMAL", "LUCHA", "VOLADOR", "VENENO", "TIERRA", "ROCA", "", "BICHO", "FANTASMA",
            "ACERO", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "NORMAL",
            "NORMAL", "¿¿??", "FUEGO", "AGUA", "PLANTA", "ELÉCTRIC", "PSÍQUICO", "HIELO", "DRAGÓN", "SINIEST.",
        )
        labels.forEachIndexed { id, label ->
            val target = 0x9200 + id * 12
            writeU16(bytes, table + id * 2, 0x4000 + target - 0x8000)
            encodeSpanishGb(bytes, target, label)
        }
        val layout = TableLayout(
            offset = table,
            count = 28,
            recordSize = 2,
            bank = 2,
            valuesArePointers = true,
        )

        val decoded = CompiledTypeNameResolver.decode(
            RomImage(bytes),
            generation = 2,
            layout = layout,
            codec = WesternPokemonTextCodecs.gen2Spanish,
        )

        assertEquals("¿¿??", decoded?.get(19)?.name)
        assertEquals(TypeSemanticRole.MYSTERY, decoded?.get(19)?.semanticRole)
        assertEquals(TypeSemanticRole.DARK, decoded?.get(27)?.semanticRole)
    }

    @Test
    fun mapsGen3SemanticsFromLocalizedLabelsRatherThanNumericPositions() {
        val bytes = ByteArray(0x2000)
        val table = 0x400
        val labels = frenchGen3Labels().toMutableList().also {
            val fire = it[10]
            it[10] = it[11]
            it[11] = fire
        }
        labels.forEachIndexed { id, label -> encodeGba(bytes, table + id * 7, 7, label) }
        val session = gbaSession(bytes, mapOf(table to 2))

        val resolved = requireNotNull(
            CompiledTypeNameResolver.resolve(session, 3, WesternPokemonTextCodecs.gen3French),
        )
        val decoded = CompiledTypeNameResolver.decode(
            session.rom,
            generation = 3,
            layout = resolved,
            codec = WesternPokemonTextCodecs.gen3French,
        )

        assertEquals(TypeSemanticRole.WATER, decoded?.get(10)?.semanticRole)
        assertEquals(TypeSemanticRole.FIRE, decoded?.get(11)?.semanticRole)
        assertEquals("EAU", decoded?.get(10)?.name)
    }

    @Test
    fun rejectsWrongLanguageContaminationAndCompetingCompiledTables() {
        val bytes = ByteArray(0x2000)
        val first = 0x400
        val second = 0x800
        frenchGen3Labels().forEachIndexed { id, label ->
            encodeGba(bytes, first + id * 7, 7, label)
            encodeGba(bytes, second + id * 7, 7, label)
        }

        assertNull(
            CompiledTypeNameResolver.resolve(
                gbaSession(bytes, mapOf(first to 1)),
                3,
                WesternPokemonTextCodecs.gen3English,
            ),
        )
        assertNull(
            CompiledTypeNameResolver.resolve(
                gbaSession(bytes, mapOf(first to 1, second to 1)),
                3,
                WesternPokemonTextCodecs.gen3French,
            ),
        )
    }

    private fun gbaSession(bytes: ByteArray, targets: Map<Int, Int>) = RomAnalysisSession(
        rom = RomImage(bytes),
        header = RomHeader(Platform.GBA, "TYPE TEST"),
        gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ ->
            GbaReferenceIndex.countsOnlyForTesting(targets)
        },
    )

    private fun frenchGen3Labels() = listOf(
        "NORMAL", "COMBAT", "VOL", "POISON", "SOL", "ROCHE", "INSECT", "SPECTR", "ACIER",
        "???", "FEU", "EAU", "PLANTE", "ELECTR", "PSY", "GLACE", "DRAGON", "TENEBR",
    )

    private fun putGbTypeNameConsumer(bytes: ByteArray, offset: Int, table: Int) {
        val bankBase = offset / 0x4000 * 0x4000
        val address = 0x4000 + table - bankBase
        bytes[offset] = 0x87.toByte()
        bytes[offset + 1] = 0x21
        writeU16(bytes, offset + 2, address)
        byteArrayOf(0x5F, 0x16, 0, 0x19, 0x2A, 0x5F, 0x56).copyInto(bytes, offset + 4)
    }

    private fun encodeGb(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, character ->
            target[offset + index] = when (character) {
                in 'A'..'Z' -> (0x80 + character.code - 'A'.code).toByte()
                '?' -> 0xE6.toByte()
                else -> error("unsupported GB fixture character $character")
            }
        }
        target[offset + value.length] = 0x50
    }

    private fun encodeSpanishGb(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, character ->
            target[offset + index] = when (character) {
                in 'A'..'Z' -> (0x80 + character.code - 'A'.code).toByte()
                'É' -> 0xC7.toByte()
                'Í' -> 0xC9.toByte()
                'Ó' -> 0xCC.toByte()
                '¿' -> 0xE4.toByte()
                '?' -> 0xE6.toByte()
                '.' -> 0xE8.toByte()
                else -> error("unsupported Spanish GB fixture character $character")
            }
        }
        target[offset + value.length] = 0x50
    }

    private fun encodeGba(target: ByteArray, offset: Int, width: Int, value: String) {
        target.fill(0, offset, offset + width)
        value.forEachIndexed { index, character ->
            target[offset + index] = when (character) {
                in 'A'..'Z' -> (0xBB + character.code - 'A'.code).toByte()
                '?' -> 0xAC.toByte()
                else -> error("unsupported GBA fixture character $character")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }
}
