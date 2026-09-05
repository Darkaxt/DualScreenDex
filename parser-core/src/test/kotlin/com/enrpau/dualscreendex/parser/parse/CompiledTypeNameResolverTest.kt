package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndexFactory
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.parse.CompiledNativeTypeNameFixtures.Fixture
import com.enrpau.dualscreendex.parser.parse.CompiledNativeTypeNameFixtures.Label
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test
    fun nativeSyntheticVectorsDecodeExactlyBeforeSemanticResolution() {
        nativeFixtures().forEach { fixture ->
            fixture.expected.values.forEach { label ->
                val decoded = fixture.codec.decodeDetailed(label.encoded(fixture.generation))
                assertEquals(fixture.codec.id, label.text, decoded.text)
                assertTrue(fixture.codec.id, decoded.terminated)
                assertEquals(fixture.codec.id, 0, decoded.invalidUnits)
            }
        }
    }

    @Test
    fun resolvesJapaneseRedBlueCompleteFifteenRoleDomain() = assertNativeDomain(
        CompiledNativeTypeNameFixtures.gb(JapanesePokemonTextCodecs.gen1RedBlue, 1),
    )

    @Test
    fun resolvesJapaneseYellowCompleteFifteenRoleDomain() = assertNativeDomain(
        CompiledNativeTypeNameFixtures.gb(JapanesePokemonTextCodecs.gen1Yellow, 1),
    )

    @Test
    fun resolvesJapaneseGen2SteelDarkAndFullwidthMysteryFromCompleteDomain() = assertNativeDomain(
        CompiledNativeTypeNameFixtures.gb(JapanesePokemonTextCodecs.gen2),
    )

    @Test
    fun resolvesKoreanGen2HistoricalFireAndMultibyteMysteryFromCompleteDomain() = assertNativeDomain(
        CompiledNativeTypeNameFixtures.gb(KoreanGen2PokemonTextCodec.codec),
    )

    @Test
    fun resolvesJapaneseRubySapphireFiveByteRowsFromMultiplyByFiveConsumer() = assertNativeDomain(
        CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3RubySapphire),
    )

    @Test
    fun resolvesJapaneseLaterFiveByteRowsFromAlternateRegisterConsumer() = assertNativeDomain(
        CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later, laterRegisters = true),
    )

    @Test
    fun mapsNativeSemanticsFromSwappedLabelsRatherThanTypeIds() {
        nativeFixtures().forEach { fixture ->
            val fire = fixture.expected.entries.single { it.value.role == TypeSemanticRole.FIRE }
            val water = fixture.expected.entries.single { it.value.role == TypeSemanticRole.WATER }
            fixture.replaceName(fire.key, water.value.encoded(fixture.generation))
            fixture.replaceName(water.key, fire.value.encoded(fixture.generation))

            assertNativeDomain(fixture, fixture.expected + mapOf(fire.key to water.value, water.key to fire.value))
        }
    }

    @Test
    fun trimsNativeGbTypeLabelPaddingWithoutChangingSemantics() {
        listOf(
            CompiledNativeTypeNameFixtures.gb(JapanesePokemonTextCodecs.gen1Yellow, 1),
            CompiledNativeTypeNameFixtures.gb(JapanesePokemonTextCodecs.gen2),
        ).forEach { fixture ->
            fixture.expected.filterValues {
                it.role in setOf(TypeSemanticRole.POISON, TypeSemanticRole.WATER, TypeSemanticRole.GRASS)
            }.forEach { (id, label) ->
                val encoded = label.encoded(fixture.generation)
                fixture.replaceName(id, encoded.copyOf(encoded.size - 1) + byteArrayOf(0x7F, 0x50))
            }
            assertNativeDomain(fixture)
        }
    }

    @Test
    fun rejectsNativeDomainsDecodedThroughTheWrongLanguage() {
        nativeFixtures().forEach { fixture ->
            val wrongCodec = when (fixture.generation) {
                1 -> WesternPokemonTextCodecs.gen1English
                2 -> WesternPokemonTextCodecs.gen2English
                else -> WesternPokemonTextCodecs.gen3English
            }
            assertNativeRejected(fixture, wrongCodec)
        }
    }

    @Test
    fun rejectsNativeDomainWithDuplicatedRoleAndMissingFireMeaning() {
        nativeFixtures().forEach { fixture ->
            val fire = fixture.expected.entries.single { it.value.role == TypeSemanticRole.FIRE }
            val water = fixture.expected.values.single { it.role == TypeSemanticRole.WATER }
            fixture.replaceName(fire.key, water.encoded(fixture.generation))
            assertNativeRejected(fixture)
        }
    }

    @Test
    fun rejectsValidNativeGlyphsThatDoNotProveAStandardTypeMeaning() {
        nativeFixtures().forEach { fixture ->
            val fire = fixture.expected.entries.single { it.value.role == TypeSemanticRole.FIRE }
            val custom = when {
                fixture.codec == KoreanGen2PokemonTextCodec.codec -> "010150" // 가
                fixture.generation == 3 -> "01FF" // あ
                else -> "B150" // あ
            }
            fixture.replaceName(fire.key, CompiledNativeTypeNameFixtures.hex(custom))
            assertNativeRejected(fixture)
        }
    }

    @Test
    fun rejectsNativeLabelsWithoutABoundedTerminator() {
        nativeFixtures().forEach { fixture ->
            val fire = fixture.expected.entries.single { it.value.role == TypeSemanticRole.FIRE }
            fixture.replaceName(fire.key, ByteArray(if (fixture.generation == 3) 5 else 16))
            assertNativeRejected(fixture)
        }
    }

    @Test
    fun rejectsKoreanInvalidPairWithoutTreatingItsTrailAsATerminator() {
        val fixture = CompiledNativeTypeNameFixtures.gb(KoreanGen2PokemonTextCodec.codec)
        val malformed = CompiledNativeTypeNameFixtures.hex("015050")
        val decoded = fixture.codec.decodeDetailed(malformed)
        assertEquals(3, decoded.consumedBytes)
        assertEquals(1, decoded.invalidUnits)
        assertTrue(decoded.terminated)
        fixture.replaceName(20, malformed)
        assertNativeRejected(fixture)
    }

    @Test
    fun rejectsKoreanLeadTruncatedAtTheBankBoundary() {
        val fixture = CompiledNativeTypeNameFixtures.gb(KoreanGen2PokemonTextCodec.codec)
        CompiledNativeTypeNameFixtures.writeU16(fixture.bytes, fixture.layout.offset + 20 * 2, 0x7FFF)
        fixture.bytes[0xBFFF] = 0x01
        assertNativeRejected(fixture)
    }

    @Test
    fun rejectsSevenByteGeometryForNativeFiveByteRows() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        assertNull(
            CompiledTypeNameResolver.decode(
                RomImage(fixture.bytes), 3, fixture.layout.copy(recordSize = 7), fixture.codec,
            ),
        )
    }

    @Test
    fun rejectsNativeFiveByteDomainWithOnlyAnUnrelatedLiteralLoad() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        // Keep the real LDR/root reference but remove the type*5 arithmetic.
        CompiledNativeTypeNameFixtures.writeU16(fixture.bytes, CompiledNativeTypeNameFixtures.GBA_CONSUMER, 0x46C0)
        CompiledNativeTypeNameFixtures.writeU16(fixture.bytes, CompiledNativeTypeNameFixtures.GBA_CONSUMER + 2, 0x46C0)
        assertNativeIndexingRejected(fixture)
    }

    @Test
    fun rejectsNativeFiveByteDomainWhenCompiledIndexingMultipliesByNine() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3RubySapphire)
        // type*8+type is not evidence for five-byte rows, even though the full labels decode.
        CompiledNativeTypeNameFixtures.writeU16(fixture.bytes, CompiledNativeTypeNameFixtures.GBA_CONSUMER, 0x00C1)
        assertNativeIndexingRejected(fixture)
    }

    @Test
    fun rejectsNativeFiveByteDomainWhenIndexAdditionUsesAnotherRegister() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later, laterRegisters = true)
        // LSLS r1,r2,#2 followed by ADDS r1,r1,r3 does not establish type*5.
        CompiledNativeTypeNameFixtures.writeU16(fixture.bytes, CompiledNativeTypeNameFixtures.GBA_CONSUMER + 2, 0x18C9)
        assertNativeIndexingRejected(fixture)
    }

    @Test
    fun rejectsNativeIndexingWhenTheShiftDestroysTheOriginalTypeValue() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        writeU16(fixture.bytes, CompiledNativeTypeNameFixtures.GBA_CONSUMER, 0x0089) // LSLS r1,r1,#2
        writeU16(fixture.bytes, CompiledNativeTypeNameFixtures.GBA_CONSUMER + 2, 0x1849) // ADDS r1,r1,r1
        assertNativeIndexingRejected(fixture)
    }

    @Test
    fun rejectsNativeIndexingWhenTheLiteralLoadDestroysTheProduct() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        writeU16(fixture.bytes, CompiledNativeTypeNameFixtures.GBA_CONSUMER + 4, 0x4906) // LDR r1,[PC,literal]
        writeU16(fixture.bytes, CompiledNativeTypeNameFixtures.GBA_CONSUMER + 6, 0x1849) // ADDS r1,r1,r1
        assertNativeIndexingRejected(fixture)
    }

    @Test
    fun rejectsNativeLiteralAtRomStartWithoutReadingBeforeItsBounds() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        fixture.bytes.fill(0, CompiledNativeTypeNameFixtures.GBA_CONSUMER, CompiledNativeTypeNameFixtures.GBA_CONSUMER + 0x24)
        writeU16(fixture.bytes, 0, 0x4807)
        CompiledNativeTypeNameFixtures.writeU32(fixture.bytes, 0x20, 0x08000000 + fixture.layout.offset)
        assertNativeIndexingRejected(fixture)
    }

    @Test
    fun rejectsNativeFiveByteTableTruncatedAtRomEnd() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        assertNativeRejected(fixture.copy(bytes = fixture.bytes.copyOf(fixture.layout.offset + 17 * 5 + 1)))
    }

    @Test
    fun propagatesCancellationDuringNativeDiscoveryAndDecoding() {
        val cancellation = ParserCancellationToken { throw ParserCancellationException() }
        nativeFixtures().forEach { fixture ->
            val original = fixture.session()
            val session = RomAnalysisSession(original.rom, original.header, cancellation = cancellation)
            assertThrows(ParserCancellationException::class.java) {
                CompiledTypeNameResolver.resolve(session, fixture.generation, fixture.codec)
            }
            assertThrows(ParserCancellationException::class.java) {
                CompiledTypeNameResolver.decode(session.rom, fixture.generation, fixture.layout, fixture.codec, cancellation)
            }
        }
    }

    @Test
    fun rejectsNativeFiveByteDomainWithOnlyInventedReferenceCounts() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        fixture.bytes.fill(0, CompiledNativeTypeNameFixtures.GBA_CONSUMER, CompiledNativeTypeNameFixtures.GBA_CONSUMER + 0x24)
        assertNull(
            CompiledTypeNameResolver.resolve(
                gbaSession(fixture.bytes, mapOf(fixture.layout.offset to 1)), 3, fixture.codec,
            ),
        )
    }

    @Test
    fun rejectsCompetingNativeFiveByteDomainsWithProvenConsumers() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        CompiledNativeTypeNameFixtures.putGbaTable(fixture.bytes, 0x800)
        CompiledNativeTypeNameFixtures.putGbaConsumer(fixture.bytes, 0x240, 0x800, laterRegisters = true)
        val session = fixture.session()
        assertEquals(1, session.gbaReferenceIndex?.referenceCount(fixture.layout.offset))
        assertEquals(1, session.gbaReferenceIndex?.referenceCount(0x800))
        assertNull(CompiledTypeNameResolver.resolve(session, 3, fixture.codec))
    }

    @Test
    fun unrelatedNativeTableReferenceDoesNotCompeteWithProvenFiveByteIndexing() {
        val fixture = CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later)
        CompiledNativeTypeNameFixtures.putGbaTable(fixture.bytes, 0x800)
        CompiledNativeTypeNameFixtures.putGbaConsumer(fixture.bytes, 0x240, 0x800)
        CompiledNativeTypeNameFixtures.writeU16(fixture.bytes, 0x240, 0x46C0)
        CompiledNativeTypeNameFixtures.writeU16(fixture.bytes, 0x242, 0x46C0)
        assertNativeDomain(fixture)
    }

    @Test
    fun preservesWesternSevenByteRowsWithConcreteMultiplyBySevenConsumer() {
        val bytes = ByteArray(0x2000)
        val root = 0x400
        frenchGen3Labels().forEachIndexed { id, label -> encodeGba(bytes, root + id * 7, 7, label) }
        CompiledNativeTypeNameFixtures.putGbaConsumer(bytes, 0x200, root)
        writeU16(bytes, 0x200, 0x00C1) // LSLS r1,r0,#3
        writeU16(bytes, 0x202, 0x1A09) // SUBS r1,r1,r0: type*8-type
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC TYPES"))
        val layout = requireNotNull(CompiledTypeNameResolver.resolve(session, 3, WesternPokemonTextCodecs.gen3French))
        assertEquals(root, layout.offset)
        assertEquals(7, layout.recordSize)
        val decoded = requireNotNull(CompiledTypeNameResolver.decode(session.rom, 3, layout, WesternPokemonTextCodecs.gen3French))
        assertEquals(TypeSemanticRole.FIRE, decoded.getValue(10).semanticRole)
        assertEquals(TypeSemanticRole.WATER, decoded.getValue(11).semanticRole)
    }

    private fun nativeFixtures(): List<Fixture> = listOf(
        CompiledNativeTypeNameFixtures.gb(JapanesePokemonTextCodecs.gen1RedBlue, 1),
        CompiledNativeTypeNameFixtures.gb(JapanesePokemonTextCodecs.gen1Yellow, 1),
        CompiledNativeTypeNameFixtures.gb(JapanesePokemonTextCodecs.gen2),
        CompiledNativeTypeNameFixtures.gb(KoreanGen2PokemonTextCodec.codec),
        CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3RubySapphire),
        CompiledNativeTypeNameFixtures.gba(JapanesePokemonTextCodecs.gen3Later, laterRegisters = true),
    )

    private fun assertNativeDomain(fixture: Fixture, expected: Map<Int, Label> = fixture.expected) {
        val session = fixture.session()
        val layout = CompiledTypeNameResolver.resolve(session, fixture.generation, fixture.codec)
        assertNotNull("${fixture.codec.id}: complete native domain must resolve", layout)
        requireNotNull(layout)
        assertEquals(fixture.layout.offset, layout.offset)
        assertEquals(fixture.layout.count, layout.count)
        assertEquals(fixture.layout.recordSize, layout.recordSize)
        assertEquals(fixture.layout.bank, layout.bank)
        assertEquals(fixture.layout.valuesArePointers, layout.valuesArePointers)
        if (fixture.generation == 3) assertEquals(5, layout.stride ?: layout.recordSize)
        val decoded = CompiledTypeNameResolver.decode(session.rom, fixture.generation, layout, fixture.codec)
        assertNotNull("${fixture.codec.id}: resolved table must decode again", decoded)
        requireNotNull(decoded)
        assertEquals(expected.mapValues { it.value.text }, decoded.mapValues { it.value.name })
        assertEquals(expected.mapValues { it.value.role }, decoded.mapValues { it.value.semanticRole })
    }

    private fun assertNativeRejected(fixture: Fixture, codec: PokemonTextCodec = fixture.codec) {
        val session = fixture.session()
        assertNull(codec.id, CompiledTypeNameResolver.decode(session.rom, fixture.generation, fixture.layout, codec))
        assertNull(codec.id, CompiledTypeNameResolver.resolve(session, fixture.generation, codec))
    }

    private fun assertNativeIndexingRejected(fixture: Fixture) {
        val session = fixture.session()
        assertEquals(1, session.gbaReferenceIndex?.referenceCount(fixture.layout.offset))
        assertNull(CompiledTypeNameResolver.resolve(session, fixture.generation, fixture.codec))
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
