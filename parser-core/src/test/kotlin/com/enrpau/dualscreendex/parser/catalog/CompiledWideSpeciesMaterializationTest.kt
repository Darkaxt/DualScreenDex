package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsCodec
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableLayout
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableOutcome
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Gen3CompiledCanonicalSpeciesMetadata
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideCoreFixture
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideCoreOutcome
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideCoreResolver
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompiledWideSpeciesMaterializationTest {
    @Test
    fun materializesOnlyCanonicalNativeIdsFromTheTypedWideSnapshot() {
        val fixture = Gen3CompiledWideCoreFixture()
        val layout = layout(fixture)
        val materialized = RecordMaterializers.speciesWithIndexResolution(RomImage(fixture.bytes), layout)

        assertEquals(setOf(1, 2, 4), materialized.records.keys)
        assertEquals(mapOf(1 to 44, 2 to 7, 4 to 9), materialized.indexResolution.values)
        assertEquals("NATIVE1", materialized.records.getValue(1).name.value)
        materialized.records.values.forEach { row ->
            assertEquals(345, row.baseStats.value?.hp)
            assertEquals(512, row.baseStats.value?.specialAttack)
            assertEquals(listOf(12, 3), row.typeIds.value)
            assertEquals(listOf(260, 65, 34), row.abilityIds.value)
            assertEquals(4, row.growthRate.value)
            assertTrue(row.navigable)
        }
        assertEquals(44, materialized.records.getValue(1).dexNumber.value)
    }

    @Test
    fun doesNotRereadLegacyStatAbilityOrGrowthOffsetsAfterTypedAdmission() {
        val fixture = Gen3CompiledWideCoreFixture()
        val layout = layout(fixture)
        fixture.bytes.fill(0, fixture.defaultStatsRoot, fixture.defaultStatsRoot + 6 * 64)
        val row = RecordMaterializers.species(RomImage(fixture.bytes), layout).getValue(1)

        assertEquals(345, row.baseStats.value?.hp)
        assertEquals(listOf(260, 65, 34), row.abilityIds.value)
        assertEquals(4, row.growthRate.value)
    }

    @Test
    fun rejectsMissingTypedRowsInsteadOfReturningToRetailByteDecoding() {
        val fixture = Gen3CompiledWideCoreFixture()
        makeLegacyBytesPlausible(fixture)
        val layout = layout(fixture).copy(resolvedDatasets = ResolvedDatasetLayouts())
        assertTrue(RecordMaterializers.species(RomImage(fixture.bytes), layout).isEmpty())
    }

    @Test
    fun rejectsAStatRootOrNameExtentThatNoLongerMatchesTheCanonicalContract() {
        val fixture = Gen3CompiledWideCoreFixture()
        makeLegacyBytesPlausible(fixture)
        val original = layout(fixture)
        val wrongStats = original.copy(tables = original.tables.copy(
            baseStats = original.tables.baseStats!!.copy(offset = fixture.defaultStatsRoot + 64),
        ))
        val wrongNames = original.copy(tables = original.tables.copy(
            speciesNames = original.tables.speciesNames!!.copy(count = 6),
        ))
        assertTrue(RecordMaterializers.species(RomImage(fixture.bytes), wrongStats).isEmpty())
        assertTrue(RecordMaterializers.species(RomImage(fixture.bytes), wrongNames).isEmpty())
    }

    @Test
    fun unknownLanguagePreservesTypedNumericCanonicalSpeciesWithoutInventingNames() {
        val fixture = Gen3CompiledWideCoreFixture()
        val rows = RecordMaterializers.species(RomImage(fixture.bytes), layout(fixture).copy(
            languageManifest = RomLanguageManifest.UNKNOWN,
        ))
        assertEquals(setOf(1, 2, 4), rows.keys)
        rows.values.forEach { row ->
            assertEquals(CapabilityStatus.NOT_FOUND, row.name.status)
            assertEquals(345, row.baseStats.value?.hp)
        }
    }

    @Test
    fun typedStatSnapshotsParticipateInDatasetEqualityAndRemainImmutable() {
        val fixture = Gen3CompiledWideCoreFixture()
        val layout = layout(fixture)
        assertNotEquals(ResolvedDatasetLayouts(), layout.resolvedDatasets)
        assertEquals(layout.resolvedDatasets, ResolvedDatasetLayouts(baseStats = layout.resolvedDatasets.baseStats))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun canonicalMetadataDoesNotExposeAMutableIndex() {
        val metadata = layout(Gen3CompiledWideCoreFixture()).compiledCanonicalSpecies!!
        (metadata.nativeToDex as MutableMap).clear()
    }

    @Test
    fun rejectsLostCanonicalMetadataAndDoesNotFabricateDescriptionIndices() {
        val fixture = Gen3CompiledWideCoreFixture()
        makeLegacyBytesPlausible(fixture)
        val original = layout(fixture)
        val markerOnly = original.copy(compiledCanonicalSpecies = null)
        val typedOnly = markerOnly.copy(tables = markerOnly.tables.copy(
            baseStats = markerOnly.tables.baseStats!!.copy(format = TableRecordFormat.STANDARD),
        ))
        for (broken in listOf(markerOnly, typedOnly)) {
            assertTrue(RecordMaterializers.species(RomImage(fixture.bytes), broken).isEmpty())
            assertTrue(SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), broken)
                is SpeciesIndexResolution.Unavailable)
        }
        assertTrue(SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), original).descriptionRows.isEmpty())
    }

    @Test
    fun unavailableEncodedMoveCategoryDoesNotHideOtherProvenScalarFields() {
        val fixture = Gen3CompiledWideCoreFixture()
        val original = layout(fixture)
        val root = 0x2a00
        fixture.putU16(root + 20, 1)
        fixture.bytes[root + 22] = 60
        fixture.bytes[root + 24] = 90
        fixture.bytes[root + 25] = 10
        fixture.bytes[root + 36] = 3
        val table = MoveDetailsTableLayout(root.toLong(), 2, MoveDetailsAbi.ALIGNED_BYTE_TARGET_MOVE_20)
        val decoded = MoveDetailsCodec().decode(fixture.session(), table) as MoveDetailsTableOutcome.Decoded
        val withMove = original.copy(
            tables = original.tables.copy(moveNames = original.tables.speciesNames!!.copy(count = 2)),
            resolvedDatasets = ResolvedDatasetLayouts(moveDetails = ResolvedMoveDetailsLayout(table, decoded.rows)),
        )
        val move = RecordMaterializers.moves(RomImage(fixture.bytes), withMove).getValue(1)
        assertEquals(CapabilityStatus.NOT_FOUND, move.category.status)
        assertEquals(60, move.power.value)
        assertEquals(0, move.typeId.value)
    }

    private fun makeLegacyBytesPlausible(fixture: Gen3CompiledWideCoreFixture) {
        for (native in listOf(1, 2, 4)) {
            repeat(6) { field -> fixture.putU16(fixture.defaultStatsRoot + native * 64 + field * 2, 260) }
        }
    }

    private fun layout(fixture: Gen3CompiledWideCoreFixture): ResolvedRomLayout {
        val core = Gen3CompiledWideCoreResolver.resolve(fixture.session(), PokemonTextCodec.gbaEnglish)
        assertTrue(core is Gen3CompiledWideCoreOutcome.Resolved)
        core as Gen3CompiledWideCoreOutcome.Resolved
        val table = core.baseStats.table
        val names = core.speciesNames
        val codec = PokemonTextCodec.gbaEnglish
        return ResolvedRomLayout(
            EngineFamily.EMERALD, 3, Platform.GBA, core.speciesCount, 0,
            ProfileTables(speciesNames = names, baseStats = TableLayout(
                table.offset.toInt(), table.count.toInt(), table.abi.recordSize, format = TableRecordFormat.WIDE_STATS_64,
            )),
            resolvedDatasets = ResolvedDatasetLayouts(baseStats = core.baseStats),
            compiledCanonicalSpecies = Gen3CompiledCanonicalSpeciesMetadata(names, table, core.nativeToDex),
            languageManifest = RomLanguageManifest(
                LanguageTag.ENGLISH,
                listOf(RomLanguageProjection(
                    LanguageTag.ENGLISH, codec.id, codec.version, LocalizedTableLayout(speciesNames = names),
                    emptyList(), LanguageResolutionStatus.RESOLVED,
                )),
                LanguageResolutionStatus.RESOLVED,
            ),
        )
    }
}
