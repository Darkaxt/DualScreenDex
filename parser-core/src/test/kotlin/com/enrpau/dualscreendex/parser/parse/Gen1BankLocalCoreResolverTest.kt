package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.catalog.SpeciesIndexResolution
import com.enrpau.dualscreendex.parser.catalog.SpeciesIndexResolver
import com.enrpau.dualscreendex.parser.language.resolvedEnglishLayout
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.family.EngineFamilyDefinitions
import com.enrpau.dualscreendex.parser.family.FamilyProbeState
import com.enrpau.dualscreendex.parser.family.IdentityRootsPhaseResult
import com.enrpau.dualscreendex.parser.family.IdentityRootsStrategy
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen1BankLocalCoreResolverTest {
    @Test
    fun resolvesBankLocalFixedNamesFromAVerifiedFarCall() {
        val result = Gen1CompiledNameResolver.resolve(RomImage(fixture()), 190)

        assertNotNull(result)
        assertEquals(0x9000, result?.offset)
        assertEquals(190, result?.count)
        assertEquals(10, result?.recordSize)
    }

    @Test
    fun resolvesBankLocalBaseStatsWithAnAnchoredStrideAndCopyLength() {
        val result = Gen1CompiledBaseResolver.resolve(RomImage(fixture()), 151)

        assertNotNull(result)
        assertEquals(0xD000, result?.offset)
        assertEquals(151, result?.count)
        assertEquals(35, result?.recordSize)
    }

    @Test
    fun resolvesPointerMoveNamesOnlyWhenTheirPointersProveTheLinearOrder() {
        val result = Gen1CompiledMoveResolver.resolve(RomImage(fixture()))

        assertNotNull(result)
        assertEquals(0x110C8, result?.moveNames?.offset)
        assertEquals(100, result?.moveNames?.count)
        assertEquals(true, result?.moveNames?.variableLength)
        assertEquals(0x14000, result?.moveData?.offset)
    }

    @Test
    fun bankLocalBaseLayoutDoesNotInheritSpriteBanksFromAnotherRecordAbi() {
        val session = RomAnalysisSession(
            RomImage(fixture()),
            RomHeader(Platform.GBC, "POKEMON RED"),
        )
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.RED_BLUE)
        val identity = IdentityRootsStrategy()
            .execute(session, definition, FamilyProbeState.empty())
            .identityRoots as IdentityRootsPhaseResult.Resolved
        val tables = identity.tableResolution.tables

        assertEquals(0xD000, tables.baseStats?.offset)
        assertEquals(35, tables.baseStats?.recordSize)
        assertEquals(0x9000, tables.speciesNames?.offset)
        assertNull(tables.sprites)
    }

    @Test
    fun existingInlineBaseAuthorityPreservesItsValidatedSpriteMetadata() {
        val bytes = fixture()
        bytes.fill(0, 0x450, 0x450 + 15)
        write(bytes, 0x500,
            0xF0, 0xB8, 0xF5, 0x3E, 0x03, 0xE0, 0xB8,
            0xEA, 0x00, 0x20, 0xC5, 0xD5, 0xE5)
        write(bytes, 0x520,
            0x3D, 0x01, 0x23, 0x00, 0x21, 0x00, 0x50,
            0xCD, 0x00, 0x02, 0x11, 0x40, 0xD0,
            0x01, 0x23, 0x00, 0xCD, 0x20, 0x02)
        write(bytes, 0x540,
            0xE1, 0xD1, 0xC1, 0xF1, 0xE0, 0xB8,
            0xEA, 0x00, 0x20, 0xC9)
        val session = RomAnalysisSession(
            RomImage(bytes),
            RomHeader(Platform.GBC, "POKEMON RED"),
        )
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.RED_BLUE)
        val identity = IdentityRootsStrategy()
            .execute(session, definition, FamilyProbeState.empty())
            .identityRoots as IdentityRootsPhaseResult.Resolved
        val tables = identity.tableResolution.tables

        assertEquals(35, tables.baseStats?.recordSize)
        assertNotNull(tables.sprites)
        assertEquals(0xD000, tables.sprites?.offset)
        assertEquals(35, tables.sprites?.recordSize)
    }

    @Test
    fun rejectsAFarCallWithoutAProvenBankRestore() {
        val bytes = fixture()
        bytes[0x260 + 10] = 0x00

        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 190))
        assertNull(Gen1CompiledMoveResolver.resolve(RomImage(bytes)))
    }

    @Test
    fun rejectsBankStoresOutsideTheMbcRegisterWindow() {
        val bytes = fixture()
        putWord(bytes, 0x240 + 3, 0xC000)
        putWord(bytes, 0x260 + 15, 0xC000)

        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 190))
        assertNull(Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151))
    }

    @Test
    fun rejectsAnUnprovenRestartCopyRoutine() {
        val bytes = fixture()
        bytes[0x220 + 1] = 0x00

        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 190))
        assertNull(Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151))
    }

    @Test
    fun rejectsDisagreeingNameStrideAndCopyLength() {
        val bytes = fixture()
        putWord(bytes, 0x8100 + 18, 9)

        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 190))
    }

    @Test
    fun rejectsDisagreeingBaseStrideAndCopyLength() {
        val bytes = fixture()
        putWord(bytes, 0xC110 + 11, 34)

        assertNull(Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151))
    }

    @Test
    fun rejectsAnUnprovenRepeatedAddRoutine() {
        val bytes = fixture()
        bytes[0x200 + 2] = 0x19

        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 190))
        assertNull(Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151))
    }

    @Test
    fun rejectsAFalseDexOrderDespitePlausibleStatBytes() {
        val bytes = fixture()
        bytes[0xD000 + 35] = 1

        assertNull(Gen1CompiledBaseResolver.resolve(RomImage(bytes), 151))
    }

    @Test
    fun rejectsNamesThatCrossTheSourceBank() {
        val bytes = fixture()
        putWord(bytes, 0x8100 + 1, 0x7FF0)

        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 190))
    }

    @Test
    fun rejectsReorderedOrAliasedMovePointers() {
        val bytes = fixture()
        putWord(bytes, 0x11000 + 2, 0x50C8)

        assertNull(Gen1CompiledMoveResolver.resolve(RomImage(bytes)))
    }

    @Test
    fun rejectsAMoveNameReaderNotLinkedToItsHomeSelector() {
        val bytes = fixture()
        putWord(bytes, 0x10100 + 4, 0xD030)

        assertNull(Gen1CompiledMoveResolver.resolve(RomImage(bytes)))
    }

    @Test
    fun rejectsAmbiguousBankLocalNameTables() {
        val bytes = fixture().copyOf(0x1C000)
        write(bytes, 0x480, 0x21, 0x00, 0x41, 0x06, 0x06, 0xCF)
        bytes.copyInto(bytes, 0x18100, 0x8100, 0x8100 + 26)
        bytes.copyInto(bytes, 0x19000, 0x9000, 0x9000 + 1900)

        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 190))
    }

    @Test
    fun resolvesCompiledSpeciesIndexBeforeContentPermutationDecoys() {
        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(indexFixture()), indexLayout())

        assertTrue(result is SpeciesIndexResolution.Resolved)
        assertEquals(112, result.values[1])
        assertEquals(1, result.values[112])
        assertEquals(0, result.values[181])
        assertEquals(151, result.values.values.count { it > 0 })
    }

    @Test
    fun compiledSpeciesIndexMaterializesCorrectStatsAndWithholdsAlternateForms() {
        val records = RecordMaterializers.species(RomImage(indexFixture()), indexLayout())

        assertEquals(112, records[1]?.dexNumber?.value)
        assertEquals(105, records[1]?.baseStats?.value?.hp)
        assertEquals(1, records[112]?.dexNumber?.value)
        assertEquals(45, records[112]?.baseStats?.value?.hp)
        assertNull(records[181]?.baseStats?.value)
        assertNull(records[181]?.typeIds?.value)
    }

    @Test
    fun rejectsCompiledSpeciesIndexWithoutBankRestoration() {
        val bytes = indexFixture()
        bytes[0x700 + 12] = 0
        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), indexLayout())

        assertTrue(result is SpeciesIndexResolution.Unavailable)
        assertTrue(result.values.values.all { it == 0 })
        assertTrue(RecordMaterializers.species(RomImage(bytes), indexLayout()).values.all { it.baseStats.value == null })
    }

    @Test
    fun rejectsCompiledSpeciesIndexWithoutProvenAlternateExclusion() {
        val bytes = indexFixture()
        bytes[0x2A0 + 6] = 0
        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), indexLayout())

        assertTrue(result is SpeciesIndexResolution.Unavailable)
        assertTrue(result.values.values.all { it == 0 })
    }

    @Test
    fun rejectsCompiledSpeciesIndexCrossingItsSourceBank() {
        val bytes = indexFixture()
        putWord(bytes, 0x4100 + 7, 0x7FF0)
        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), indexLayout())

        assertTrue(result is SpeciesIndexResolution.Unavailable)
        assertTrue(result.values.values.all { it == 0 })
    }

    private fun indexLayout() = resolvedEnglishLayout(
        EngineFamily.RED_BLUE,
        generation = 1,
        platform = Platform.GBC,
        speciesCount = 190,
        moveCount = 100,
        tables = ProfileTables(
            speciesNames = TableLayout(0x9000, 190, 10),
            baseStats = TableLayout(0xD000, 151, 35),
        ),
    )

    private fun indexFixture(): ByteArray = fixture().also { bytes ->
        putWord(bytes, 0x450 + 9, 0x40E0)
        write(bytes, 0xC0E0,
            0xC5, 0xD5, 0xE5, 0xFA, 0x10, 0xD0, 0xF5,
            0xFA, 0x40, 0xD0, 0xEA, 0x10, 0xD0)
        write(bytes, 0x700,
            0xF0, 0xB8, 0xF5, 0x3E, 0x01, 0xCD, 0x40, 0x02,
            0xCD, 0x00, 0x41, 0xF1, 0xCD, 0x40, 0x02, 0xC9)
        write(bytes, 0x4100,
            0xC5, 0xE5, 0xFA, 0x10, 0xD0, 0x3D, 0x21, 0x00, 0x50,
            0x06, 0x00, 0x4F, 0x09, 0x7E, 0xEA, 0x10, 0xD0, 0xE1, 0xC1, 0xC9)
        repeat(151) { bytes[0x5000 + it] = (it + 1).toByte() }
        bytes[0x5000] = 112
        bytes[0x5000 + 111] = 1
        bytes[0x5000 + 180] = 112
        write(bytes, 0xC0F3,
            0xFA, 0x40, 0xD0, 0x21, 0x00, 0x67, 0xCD, 0xA0, 0x02,
            0x30, 0x0C, 0x78, 0x21, 0x00, 0x68, 0x01, 0x23, 0x00,
            0xCD, 0x00, 0x02, 0x18, 0x10)
        write(bytes, 0xC10A, 0xCD, 0x00, 0x07, 0xFA, 0x10, 0xD0)
        write(bytes, 0x2A0,
            0x11, 0x01, 0x00, 0x06, 0x00, 0x4F, 0x7E, 0xFE, 0xFF,
            0x28, 0x07, 0xB9, 0x28, 0x06, 0x04, 0x19, 0x18, 0xF4,
            0xA7, 0xC9, 0x37, 0xC9)
        write(bytes, 0xE700, 181, 0xFF)
        write(bytes, 0xE800, 0, 200, 200, 200, 200, 200, 4, 4)
        bytes[0xD000 + 1] = 45
        bytes[0xD000 + 111 * 35 + 1] = 105
    }

    private fun fixture(): ByteArray = ByteArray(0x18000).also { bytes ->
        write(bytes, 0x08, 0xC3, 0x60, 0x02)
        write(bytes, 0x20, 0xC3, 0x20, 0x02)
        write(bytes, 0x200, 0xA7, 0xC8, 0x09, 0x3D, 0x20, 0xFC, 0xC9)
        write(bytes, 0x220, 0x2A, 0x12, 0x13, 0x0B, 0x79, 0xB0, 0x20, 0xF8, 0xC9)
        write(bytes, 0x240, 0xE0, 0xB8, 0xEA, 0x00, 0x20, 0xC9)
        write(bytes, 0x250, 0xE9)
        write(bytes, 0x260, 0xF0, 0xB8, 0xF5, 0x78, 0xCD, 0x40, 0x02,
            0xCD, 0x50, 0x02, 0xC1, 0x78, 0xE0, 0xB8, 0xEA, 0x00, 0x20, 0xC9)
        write(bytes, 0x400, 0x21, 0x00, 0x41, 0x06, 0x02, 0xCF)
        write(bytes, 0x8100, 0x21, 0x00, 0x50, 0x01, 0x0A, 0x00,
            0xFA, 0x10, 0xD0, 0x3D, 0xCD, 0x00, 0x02, 0x11, 0x80, 0xC8,
            0xD5, 0x01, 0x0A, 0x00, 0xE7, 0x3E, 0x50, 0x12, 0xD1, 0xC9)
        repeat(190) { index ->
            repeat(10) { bytes[0x9000 + index * 10 + it] = 0x50 }
            write(bytes, 0x9000 + index * 10, 0x80, 0x81)
        }
        write(bytes, 0x450, 0xF0, 0xB8, 0xF5, 0x3E, 0x03, 0xCD, 0x40, 0x02,
            0xCD, 0x00, 0x41, 0xF1, 0xC3, 0x40, 0x02)
        write(bytes, 0xC110, 0x3D, 0x01, 0x23, 0x00, 0x21, 0x00, 0x50,
            0xCD, 0x00, 0x02, 0x01, 0x23, 0x00, 0x11, 0x40, 0xD0, 0xE7)
        repeat(151) { index ->
            write(bytes, 0xD000 + index * 35, index + 1, 60, 60, 60, 60, 60, 4, 4)
        }
        write(bytes, 0x380, 0xFA, 0x10, 0xD0, 0xEA, 0x20, 0xD0,
            0xCD, 0x20, 0x04, 0x11, 0x80, 0xC8, 0xC9)
        write(bytes, 0x420, 0xE5, 0xC5, 0xD5, 0x21, 0x00, 0x41,
            0x06, 0x04, 0xCF, 0xD1, 0xC1, 0xE1, 0xC9)
        write(bytes, 0x10100, 0xFA, 0x20, 0xD0, 0xEA, 0x10, 0xD0,
            0x3D, 0x21, 0x00, 0x50, 0x16, 0x00, 0x5F, 0x19, 0x19,
            0x2A, 0x56, 0x5F, 0x21, 0x80, 0xC8, 0xC3, 0x80, 0x02)
        write(bytes, 0x280, 0x1A, 0x13, 0x22, 0xFE, 0x50, 0x20, 0xF9, 0xC9)
        repeat(100) { index ->
            putWord(bytes, 0x11000 + index * 2, 0x50C8 + index * 6)
            write(bytes, 0x110C8 + index * 6, 0x8C, 0x8E, 0x95, 0x84, 0xF6 + index % 10, 0x50)
            write(bytes, 0x14000 + index * 6, 0, 1, index + 1, index % 28, 100, 15)
        }
        write(bytes, 0x600, 0x3D, 0x21, 0x00, 0x40, 0x01, 0x06, 0x00,
            0xCD, 0x00, 0x02, 0x11, 0x00, 0xD2, 0x3E, 0x05, 0xCD, 0x00, 0x03)
    }

    private fun write(bytes: ByteArray, offset: Int, vararg values: Int) {
        values.forEachIndexed { index, value -> bytes[offset + index] = value.toByte() }
    }

    private fun putWord(bytes: ByteArray, offset: Int, value: Int) {
        write(bytes, offset, value and 0xFF, value ushr 8)
    }
}
