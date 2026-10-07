package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.dataset.descriptions.DecodedDescriptionPage
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionRecoveryProvenance
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionRowOutcome
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.resolvedLanguageManifest
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCategoryMaterializerTest {
    @Test
    fun relocatedSelectedOrdinaryAndExpandedCellsAreNativeCategories() {
        for (root in listOf(0x100, 0x400)) for (size in listOf(32, 36)) {
            val fixture = fixture(root, size)
            assertEquals(mapOf(1 to "SEED"), SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout))
        }
    }

    @Test
    fun plausibleDecodedRowDoesNotAuthorizeInvalidCategoryTokens() {
        for (token in listOf(0x08, 0xFC, 0xFD, 0xFE)) {
            val fixture = fixture()
            fixture.bytes[fixture.cell + 1] = token.toByte()
            assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout).isEmpty())
        }
    }

    @Test
    fun blankUnterminatedAndDisagreeingCategoriesRemainUnavailable() {
        val fixture = fixture()
        fixture.bytes[fixture.cell] = 0xFF.toByte()
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout).isEmpty())
        repeat(14) { fixture.bytes[fixture.cell + it] = 0xBB.toByte() }
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout).isEmpty())
        fixture.bytes[fixture.cell + 1] = 0xFF.toByte()
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout).isEmpty())
    }

    @Test
    fun categoryCannotUseMalformedRowsOrUnselectedPhysicalTables() {
        val fixture = fixture()
        val table = fixture.layout.resolvedDatasets.descriptions!!.table
        val malformed = ResolvedDescriptionLayout(table, listOf(DescriptionRowOutcome.StructuralEmpty(0),
            DescriptionRowOutcome.Malformed(1, listOf("prose malformed"))))
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout.copy(
            resolvedDatasets = ResolvedDatasetLayouts(descriptions = malformed))).isEmpty())
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout.copy(
            tables = ProfileTables(descriptions = TableLayout(0x300, 2, 36, pointerOffsets = listOf(20))))).isEmpty())
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout.copy(generation = 2)).isEmpty())
    }

    @Test
    fun extentAndWorkExhaustionCannotPublishPartialCategoryMap() {
        val fixture = fixture()
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes.copyOf(fixture.cell + 2)), fixture.layout).isEmpty())
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout,
            limits = ResolutionLimits(maxProbeWorkPerDataset = 1)).isEmpty())
    }

    @Test
    fun cancellationPropagates() {
        val fixture = fixture()
        assertThrows(ParserCancellationException::class.java) {
            SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout,
                cancellation = ParserCancellationToken { throw ParserCancellationException() })
        }
    }

    @Test
    fun japaneseSixByteZeroTerminatedCategoryRemainsStrict() {
        val fixture = fixture(size = 28)
        byteArrayOf(1, 2, 0).copyInto(fixture.bytes, fixture.cell)
        val table = DescriptionTableLayout(0x100, 2, 28, listOf(12))
        val row = DescriptionRowOutcome.Decoded(1, "あい", 7, 69, listOf(
            DecodedDescriptionPage("Prose", DescriptionRecoveryProvenance.Direct(0x800))))
        val layout = fixture.layout.copy(tables = ProfileTables(descriptions = TableLayout(0x100, 2, 28, pointerOffsets = listOf(12))),
            languageManifest = resolvedLanguageManifest(com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later,
                language = com.enrpau.dualscreendex.parser.language.LanguageTag.JAPANESE),
            resolvedDatasets = ResolvedDatasetLayouts(descriptions = ResolvedDescriptionLayout(table,
                listOf(DescriptionRowOutcome.StructuralEmpty(0), row))))
        assertEquals(mapOf(1 to "あい"), SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), layout))
        fixture.bytes[fixture.cell + 2] = 0xFF.toByte()
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), layout).isEmpty())
        fixture.bytes[fixture.cell + 1] = 0xFC.toByte()
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), layout).isEmpty())
    }

    @Test
    fun malformedCategoryDoesNotReadBeyondCellOrAcceptProseTokens() {
        val fixture = fixture(size = 32)
        repeat(12) { fixture.bytes[fixture.cell + it] = 0xBB.toByte() }
        fixture.bytes[fixture.cell + 12] = 0xFF.toByte()
        assertTrue(SpeciesCategoryMaterializer.materialize(RomImage(fixture.bytes), fixture.layout).isEmpty())
    }

    private data class Fixture(val bytes: ByteArray, val layout: ResolvedRomLayout, val cell: Int)

    private fun fixture(root: Int = 0x100, size: Int = 36): Fixture {
        val bytes = ByteArray(0x1000)
        val cell = root + size
        "SEED".forEachIndexed { index, c -> bytes[cell + index] = (0xBB + c.code - 'A'.code).toByte() }
        bytes[cell + 4] = 0xFF.toByte()
        val pointers = if (size == 36) listOf(20) else listOf(16)
        val table = DescriptionTableLayout(root.toLong(), 2, size, pointers)
        val row = DescriptionRowOutcome.Decoded(1, "SEED", 7, 69, listOf(
            DecodedDescriptionPage("Independent prose.", DescriptionRecoveryProvenance.Direct(0x800))))
        val layout = ResolvedRomLayout(EngineFamily.EMERALD, 3, Platform.GBA, 2, 0,
            ProfileTables(descriptions = TableLayout(root, 2, size, pointerOffsets = pointers)),
            resolvedDatasets = ResolvedDatasetLayouts(descriptions = ResolvedDescriptionLayout(table,
                listOf(DescriptionRowOutcome.StructuralEmpty(0), row))),
            languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish))
        return Fixture(bytes, layout, cell)
    }
}
