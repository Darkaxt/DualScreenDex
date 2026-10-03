package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Gen2CompactCoreMetadata
import com.enrpau.dualscreendex.parser.model.Gen2CompactSpeciesSlot
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2CompactCoreResolverTest {
    @Test fun bindsZeroBasedNamesStatsAndCanonicalDomain() {
        val core = requireNotNull(resolve(fixture(false)))
        assertEquals(289, core.metadata.slots.size)
        assertEquals(TableLayout(NAMES, 292, 10, bank = 2), core.tables.speciesNames)
        assertEquals(TableRecordFormat.GEN2_COMPACT_BASE_STATS, core.tables.baseStats?.format)
        assertEquals(34, core.tables.baseStats?.recordSize)
        assertEquals(291, core.tables.baseStats?.count)
        assertEquals(289, core.namesEvidence.coveredRecords)
        assertEquals(289, core.statsEvidence.expectedRecords)
    }

    @Test fun bindsOneBasedNamesAndOddStrideWithoutClassicPrefixInterpretation() {
        val core = requireNotNull(resolve(fixture(true)))
        assertEquals(254, core.metadata.slots.size)
        assertEquals(256, core.tables.speciesNames?.count)
        assertEquals(35, core.tables.baseStats?.recordSize)
        assertEquals(255, core.tables.baseStats?.count)
    }

    @Test fun derivesRelocatedTablesAndCopiedStatDestination() {
        val bytes = fixture(false)
        bytes.copyInto(bytes, BASE + 0x600, BASE, BASE + 291 * 34)
        bytes.copyInto(bytes, NAMES + 0x1000, NAMES, NAMES + 292 * 10)
        bytes.word(BASE_GETTER + 17, BASE + 0x600)
        bytes.word(NAME_GETTER + 18, 0x5200)
        bytes.word(BASE_GETTER + 21, 0xC700)
        bytes.word(CALCULATOR + 6, 0xC6FF)
        val core = requireNotNull(resolve(bytes))
        assertEquals(BASE + 0x600, core.tables.baseStats?.offset)
        assertEquals(NAMES + 0x1000, core.tables.speciesNames?.offset)
    }

    @Test fun rejectsBrokenCopyMultiplyRestorationAndNameArithmetic() {
        for (oneBased in listOf(false, true)) {
            val bytes = fixture(oneBased)
            listOf(COPY + 3, MULTIPLY + 4, RESTORE + 5,
                NAME_GETTER + if (oneBased) 20 else 15,
                BASE_GETTER + if (oneBased) 20 else 19,
                BASE_GETTER + if (oneBased) 32 else 31).forEach { offset ->
                assertNull("broken compact consumer $offset", resolve(bytes.copyOf().also {
                    it[offset] = (it[offset].toInt() xor 1).toByte()
                }))
            }
        }
    }

    @Test fun rejectsMismatchedFieldReaderAndIncompleteSixStatLoop() {
        for (oneBased in listOf(false, true)) {
            val bytes = fixture(oneBased)
            val fieldOperand = if (oneBased) 7 else 6
            assertNull(resolve(bytes.copyOf().also { it.word(CALCULATOR + fieldOperand, 0xC500) }))
            assertNull(resolve(bytes.copyOf().also { it[STAT_LOOP + 16] = 7 }))
            assertNull(resolve(bytes.copyOf().also { it[STAT_LOOP + 18] = 0xEE.toByte() }))
            assertNull(resolve(bytes.copyOf().also { it[STAT_LOOP + 19] = 0 }))
        }
    }

    @Test fun rejectsMismatchedFormInputAndUnvalidatedCanonicalNamesOrStats() {
        val one = fixture(true)
        assertNull(resolve(one.copyOf().also { it.word(NAME_GETTER + 7, 0xC201) }))
        for (oneBased in listOf(false, true)) {
            val bytes = fixture(oneBased)
            assertNull(resolve(bytes.copyOf().also { it[NAMES + 10] = 0x50 }))
            assertNull(resolve(bytes.copyOf().also { it[BASE] = 0 }))
            assertNull(resolve(bytes.copyOf().also { it[BASE + 6] = 19 }))
            assertNull(resolve(bytes.copyOf().also { it[TYPE_COPY] = 0 }))
        }
    }

    @Test fun rejectsTruncatedBankCrossingAndCompetingCoreRoots() {
        val original = fixture(false)
        assertNull(resolve(original.copyOf().also { it.word(BASE_GETTER + 17, 0x7FF0) }))
        assertNull(resolve(original.copyOf().also { it.word(NAME_GETTER + 18, 0x7FF0) }))
        assertNull(resolve(original.copyOf(NAMES + 291 * 10)))
        val competing = original.copyOf()
        competing.copyInto(competing, NAME_GETTER + 0x100 - 13, NAME_GETTER - 13, NAME_GETTER + 40)
        competing.copyInto(competing, NAMES + 0x1000, NAMES, NAMES + 292 * 10)
        competing.word(NAME_GETTER + 0x100 + 18, 0x5200)
        assertNull(resolve(competing))
    }

    @Test fun materializesProvenIndicesStatsFirstFieldsAndNoGuessedDexNumbers() {
        val bytes = fixture(false)
        val names = TableLayout(NAMES, 3, 10, bank = 2)
        val stats = TableLayout(BASE, 2, 34, bank = 1, format = TableRecordFormat.GEN2_COMPACT_BASE_STATS)
        bytes[BASE + 34] = 80
        val metadata = Gen2CompactCoreMetadata(listOf(
            Gen2CompactSpeciesSlot(1, 2, 1), Gen2CompactSpeciesSlot(2, 1, 0),
        ))
        val layout = ResolvedRomLayout(EngineFamily.CRYSTAL, 2, Platform.GBC, 2, null,
            ProfileTables(speciesNames = names, baseStats = stats),
            languageManifest = language(names), gen2CompactCore = metadata)
        val species = RecordMaterializers.species(RomImage(bytes), layout)
        assertEquals(setOf(1, 2), species.keys)
        assertEquals("Other", species[1]?.name?.value)
        assertEquals("Unit", species[2]?.name?.value)
        assertEquals(80, species[1]?.baseStats?.value?.hp)
        assertEquals(40, species[1]?.baseStats?.value?.speed)
        assertEquals(50, species[1]?.baseStats?.value?.specialAttack)
        assertEquals(listOf(9, 9), species[1]?.typeIds?.value)
        assertNull(species[1]?.dexNumber?.value)
    }

    @Test fun rejectsCompactMaterializationWithoutCoherentIndexAuthority() {
        val bytes = fixture(false)
        val names = TableLayout(NAMES, 3, 10)
        val stats = TableLayout(BASE, 2, 34, format = TableRecordFormat.GEN2_COMPACT_BASE_STATS)
        val layout = ResolvedRomLayout(EngineFamily.CRYSTAL, 2, Platform.GBC, 2, null,
            ProfileTables(speciesNames = names, baseStats = stats), languageManifest = language(names))
        assertEquals(emptyMap<Int, Any>(), RecordMaterializers.species(RomImage(bytes), layout))
        val metadata = Gen2CompactCoreMetadata(listOf(Gen2CompactSpeciesSlot(1, 4, 0)))
        assertEquals(emptyMap<Int, Any>(), RecordMaterializers.species(RomImage(bytes), layout.copy(gen2CompactCore = metadata)))
    }

    private fun language(names: TableLayout): RomLanguageManifest = RomLanguageManifest(
        LanguageTag.ENGLISH, listOf(RomLanguageProjection(LanguageTag.ENGLISH,
            Gen2PlainNameCodec.english53.id, 1, LocalizedTableLayout(speciesNames = names),
            emptyList(), LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED,
    )

    private fun resolve(bytes: ByteArray): Gen2CompactCoreResolution? = Gen2CompactCoreResolver.resolve(
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "CRYSTAL")),
    )

    private fun fixture(oneBased: Boolean): ByteArray = ByteArray(0x10000).also { bytes ->
        bytes.put(0x08, "e0 87 ea 00 20 c9")
        bytes.put(0x18, "c3 00 06")
        bytes.put(0x20, "c3 00 07")
        bytes.put(0x40, "c1 d1 e1 c9")
        bytes.put(0x80, "3c fe 02 3d c9")
        bytes.put(0x100, "e6 20 cb 37 1f c9")
        bytes.put(0x180, "c5 4f 87 79 38 02 cb b8 a8 28 07 fe 20 30 03 a8 e6 1f a7 79 c1 c9")
        bytes.put(0x200, "21 00 10 d5 7c 2f 57 7d 2f 5f cd 20 02 30 03 3f d1 c9 19 cb 3c cb 1d 11 23 01 19 44 4d 37 d1 c9")
        bytes.put(0x220, "0c 28 13 0d 78 e6 3f 47 2a a7 28 0d b9 2a 20 f8 cd 80 01 20 f3 c9 06 00 0d 78 cd 00 01 47 0d 37 c9")
        bytes.put(0x300, "21 ff 0f cd 80 03 d8 01 00 f0 09 cb 3c cb 1d 2b 24 44 4d c9")
        bytes.put(0x340, "21 ff 11 cd 80 03 d8 01 00 ee 18 be")
        bytes.put(0x380, "78 e6 3f 28 23 fe 01 28 1f 47 23 2a a7 28 19 b9 20 f8 3e 3f a6 28 f3 fe 20 20 06 cb 68 28 eb 23 c9 2a cb 6f b8 20 e4 c9 06 00 37 c9")
        bytes.put(STACK, "e8 fd d5 e5 f8 08 56 5f f0 87 32 7b 5e 36 04 2b 36 80 2b 72 2b 73 e1 d1 c3 08 00")
        bytes.put(RESTORE, "f5 e5 f8 04 7e cf e1 f1 33 c9")
        bytes.put(COPY, "cd 00 04 04 0c 18 03 2a 12 13 0d 20 fa 05 20 f7 c9")
        bytes.put(MULTIPLY, "a7 c8 c5 1f 30 01 09 cb 21 cb 10 a7 20 f5 c1 c9")
        bytes.put(TYPE_COPY, "2a 12 13 0b 79 b0 20 f8 c9")
        if (oneBased) {
            bytes.put(NAME_GETTER, "e5 c5 fa 10 c1 4f fa 00 c2 47 cd 40 03 50 59 c1 62 6b 29 29 19 29 11 00 42 19 11 00 c8 d5 01 0a 00 3e 02 cd 00 05 62 6b 36 53 d1 e1 c9")
            bytes.put(BASE_GETTER, "e5 d5 c5 fa 02 c1 4f fa 00 c2 47 cd 00 03 0b 3e 23 21 00 42 df 11 00 c5 01 23 00 3e 01 cd 00 05 c3 40 00")
            bytes.put(CALCULATOR, "e5 d5 c5 78 57 e5 21 ff c4 06 00 09 7e 5f e1 c1 d1 e1 c9")
        } else {
            bytes.put(NAME_GETTER - 13, "e5 21 10 c1 fa 03 c1 22 fa 00 c2 77 e1")
            bytes.put(NAME_GETTER, "e5 21 10 c1 2a 5f 7e cd 00 01 57 62 6b 29 29 19 29 11 00 42 19 11 00 c8 d5 01 0a 00 3e 02 cd 00 05 62 6b 36 53 d1 e1 c9")
            bytes.put(BASE_GETTER, "e5 d5 c5 fa 02 c1 4f fa 00 c2 47 cd 00 02 3e 22 21 00 42 df 11 00 c5 01 22 00 3e 01 cd 00 05 c3 40 00")
            bytes.put(CALCULATOR, "e5 d5 c5 50 e5 21 ff c4 06 00 09 5e e1 c1 d1 e1 c9")
        }
        bytes.put(STAT_LOOP, "0e 00 0c cd 00 43 f0 a4 12 13 f0 a5 12 13 79 fe 06 20 ef c9")
        val stride = if (oneBased) 35 else 34
        val baseSlots = if (oneBased) 255 else 291
        repeat(baseSlots) { row -> bytes.put(BASE + row * stride, "0a 14 1e 28 32 3c 09 09") }
        val nameSlots = if (oneBased) 256 else 292
        repeat(nameSlots) { row -> bytes.name(NAMES + row * 10, if (row == 2) "Other" else "Unit") }
        bytes.put(TYPE_GETTER, "fa 10 c1 21 80 46 5f 16 00 19 5e 19 11 00 c9 01 0d 00 e7 c9")
        val types = listOf("Normal", "Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel",
            "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark", "Fairy", "???")
        var cursor = TYPE_TABLE + types.size
        types.forEachIndexed { index, name ->
            val cell = TYPE_TABLE + index
            bytes[cell] = (cursor - cell).toByte()
            cursor += bytes.name(cursor, name)
        }
    }

    private fun ByteArray.name(offset: Int, name: String): Int {
        name.forEachIndexed { index, char -> this[offset + index] = when (char) {
            in 'A'..'Z' -> 0x80 + (char - 'A')
            in 'a'..'z' -> 0xA0 + (char - 'a')
            '?' -> 0x9E
            else -> error("fixture glyph $char")
        }.toByte() }
        this[offset + name.length] = 0x53
        return name.length + 1
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val STACK = 0x400
        const val RESTORE = 0x480
        const val COPY = 0x500
        const val MULTIPLY = 0x600
        const val TYPE_COPY = 0x700
        const val NAME_GETTER = 0x900
        const val BASE_GETTER = 0x980
        const val BASE = 0x4200
        const val NAMES = 0x8200
        const val STAT_LOOP = 0xC200
        const val CALCULATOR = 0xC300
        const val TYPE_GETTER = 0xC600
        const val TYPE_TABLE = 0xC680
    }
}
