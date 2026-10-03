package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2CompactTypeNameResolverTest {
    @Test fun resolvesRelativeByteTypeNamesThroughCompleteConsumer() {
        val fixture = fixture(pointers = false)
        val layout = resolve(fixture.bytes)
        assertEquals(fixture.layout, layout)
        assertRoles(fixture.bytes, requireNotNull(layout))
    }

    @Test fun resolvesBankLocalWordPointersThroughIndependentConsumer() {
        val fixture = fixture(pointers = true)
        val layout = resolve(fixture.bytes)
        assertEquals(fixture.layout, layout)
        assertRoles(fixture.bytes, requireNotNull(layout))
    }

    @Test fun interpretsRelativeOffsetsFromEachCellNotTheTableRoot() {
        val fixture = fixture(pointers = false)
        val decoded = CompiledTypeNameResolver.decode(RomImage(fixture.bytes), 2, fixture.layout, codec)
        assertEquals("Fighting", requireNotNull(decoded)[1]?.name)
        assertEquals("???", decoded[18]?.name)
    }

    @Test fun preservesRomNativeIdsInsteadOfRetailTypeNumbering() {
        val names = NAMES.toMutableList().also { it[0] = "Dark"; it[16] = "Normal" }
        val fixture = fixture(pointers = false, names = names)
        val decoded = CompiledTypeNameResolver.decode(
            RomImage(fixture.bytes), 2, requireNotNull(resolve(fixture.bytes)), codec,
        )
        assertEquals(TypeSemanticRole.DARK, requireNotNull(decoded)[0]?.semanticRole)
        assertEquals(TypeSemanticRole.NORMAL, decoded[16]?.semanticRole)
    }

    @Test fun rejectsIncompleteConsumerAndUnprovenRestartCopy() {
        for (pointers in listOf(false, true)) {
            val fixture = fixture(pointers)
            assertNull(resolve(fixture.bytes.copyOf().also { it[CONSUMER + 9] = 0 }))
            assertNull(resolve(fixture.bytes.copyOf().also { it[COPY + 7] = 0 }))
            assertNull(resolve(fixture.bytes.copyOf().also { it.word(CONSUMER + if (pointers) 15 else 13, 0x8000) }))
        }
    }

    @Test fun rejectsIncompleteSemanticDomainAndDuplicateRoles() {
        assertNull(resolve(fixture(false, NAMES.dropLast(1) + "Unknown").bytes))
        assertNull(resolve(fixture(true, NAMES.dropLast(1) + "Normal").bytes))
        val fixture = fixture(false)
        assertNull(CompiledTypeNameResolver.decode(RomImage(fixture.bytes), 1, fixture.layout, codec))
        assertNull(CompiledTypeNameResolver.decode(RomImage(fixture.bytes), 2, fixture.layout.copy(count = 18), codec))
        assertNull(CompiledTypeNameResolver.decode(RomImage(fixture.bytes), 2, fixture.layout.copy(variableLength = true), codec))
    }

    @Test fun rejectsRelativeOffsetsIntoIndexCellsAndBankCrossingTables() {
        val fixture = fixture(false)
        assertNull(resolve(fixture.bytes.copyOf().also { it[TABLE] = 0 }))
        assertNull(resolve(fixture.bytes.copyOf().also { it.word(CONSUMER + 4, 0x7FFA) }))
    }

    @Test fun rejectsNamesTerminatingBeyondTheCompiledCopyWidth() {
        val names = listOf("       Normal") + NAMES.drop(1)
        assertNull(resolve(fixture(false, names).bytes))
        assertNull(resolve(fixture(true, names).bytes))
    }

    @Test fun rejectsCompetingCompleteRoots() {
        val first = fixture(false)
        val second = fixture(true, consumer = CONSUMER + 0x1000, table = TABLE + 0x1000)
        second.bytes.copyInto(first.bytes, CONSUMER + 0x1000, CONSUMER + 0x1000, 0x8000)
        assertNull(resolve(first.bytes))
    }

    private fun assertRoles(bytes: ByteArray, layout: TableLayout) {
        val decoded = requireNotNull(CompiledTypeNameResolver.decode(RomImage(bytes), 2, layout, codec))
        assertEquals(19, decoded.size)
        assertEquals(TypeSemanticRole.entries.toSet(), decoded.values.map { it.semanticRole }.toSet())
        assertEquals(TypeSemanticRole.FIRE, decoded[9]?.semanticRole)
        assertEquals(TypeSemanticRole.GRASS, decoded[11]?.semanticRole)
        assertEquals(TypeSemanticRole.FAIRY, decoded[17]?.semanticRole)
    }

    private fun resolve(bytes: ByteArray): TableLayout? = CompiledTypeNameResolver.resolve(
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "CRYSTAL")), 2, codec,
    )

    private fun fixture(
        pointers: Boolean,
        names: List<String> = NAMES,
        consumer: Int = CONSUMER,
        table: Int = TABLE,
    ): Fixture {
        val bytes = ByteArray(0x8000)
        bytes.put(0x20, "c3 00 01")
        bytes.put(COPY, "2a 12 13 0b 79 b0 20 f8 c9")
        if (pointers) {
            bytes.put(consumer, "fa 00 c2 21 00 42 5f 16 00 19 19 2a 66 6f 11 00 c3 01 0d 00 e7 c9")
        } else {
            bytes.put(consumer, "fa 00 c2 21 00 42 5f 16 00 19 5e 19 11 00 c3 01 0d 00 e7 c9")
        }
        bytes.word(consumer + 4, table)
        val width = if (pointers) 2 else 1
        var cursor = table + names.size * width
        names.forEachIndexed { id, name ->
            val cell = table + id * width
            if (pointers) bytes.word(cell, cursor) else bytes[cell] = (cursor - cell).toByte()
            val encoded = name.map { char ->
                when (char) {
                    '?' -> 0x9E
                    ' ' -> 0x7F
                    in 'A'..'Z' -> 0x80 + (char - 'A')
                    in 'a'..'z' -> 0xA0 + (char - 'a')
                    else -> error("fixture glyph $char")
                }.toByte()
            }.toByteArray() + 0x53.toByte()
            encoded.copyInto(bytes, cursor)
            cursor += encoded.size
        }
        return Fixture(bytes, TableLayout(
            table, names.size, width, bank = 1, valuesArePointers = pointers,
            format = TableRecordFormat.GEN2_COMPACT_TYPE_NAMES,
        ))
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private data class Fixture(val bytes: ByteArray, val layout: TableLayout)

    private companion object {
        const val CONSUMER = 0x4100
        const val TABLE = 0x4200
        const val COPY = 0x100
        val codec = Gen2PlainNameCodec.english53
        val NAMES = listOf(
            "Normal", "Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel",
            "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark", "Fairy", "???",
        )
    }
}
