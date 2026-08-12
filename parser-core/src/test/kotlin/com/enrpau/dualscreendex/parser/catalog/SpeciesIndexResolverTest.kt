package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesIndexResolverTest {
    @Test
    fun acceptsCompiledReferencedHighDistinctnessReorderedMapFromDarkViolet() {
        val speciesCount = 420
        val tableOffset = 0x1000
        val namesOffset = 0x2000
        val bytes = ByteArray(0x4000) { 0x7F }
        val liveWords = Base64.getDecoder().decode(DARK_VIOLET_SPECIES_TO_DEX_BASE64)
        liveWords.copyInto(bytes, tableOffset)
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 469,
            tables = ProfileTables(speciesNames = TableLayout(namesOffset, speciesCount, 11)),
            compiledGbaReferences = GbaCompiledReferenceIndex(mapOf(tableOffset to 2)),
        )

        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), layout)

        assertTrue(result is SpeciesIndexResolution.Resolved)
        assertEquals(1, result.values[1])
        assertEquals(28, result.values[10])
        assertEquals(252, result.values[277])
        assertEquals(411, result.values.values.max())
        assertEquals(399, result.values.values.count { it > 0 })
    }

    @Test
    fun rejectsCompiledReferencedLowDistinctnessShortPrefixCanonicalDecoy() {
        val speciesCount = 420
        val tableOffset = 0x1000
        val namesOffset = 0x2000
        val bytes = ByteArray(0x4000) { 0x7F }
        repeat(speciesCount - 1) { index ->
            val speciesId = index + 1
            val dex = when {
                speciesId <= 9 -> speciesId
                speciesId == 277 -> 252
                else -> 1 + speciesId % 8
            }
            putU16(bytes, tableOffset + index * 2, dex)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 469,
            tables = ProfileTables(speciesNames = TableLayout(namesOffset, speciesCount, 11)),
            compiledGbaReferences = GbaCompiledReferenceIndex(mapOf(tableOffset to 2)),
        )

        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), layout)

        assertTrue(result !is SpeciesIndexResolution.Resolved)
        assertEquals(0, result.values[1])
        assertEquals(0, result.values[speciesCount - 1])
    }

    @Test
    fun rejectsUnreferencedHighDistinctnessReorderedMap() {
        val speciesCount = 420
        val tableOffset = 0x1000
        val namesOffset = 0x2000
        val bytes = ByteArray(0x4000) { 0x7F }
        Base64.getDecoder().decode(DARK_VIOLET_SPECIES_TO_DEX_BASE64).copyInto(bytes, tableOffset)
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 469,
            tables = ProfileTables(speciesNames = TableLayout(namesOffset, speciesCount, 11)),
        )

        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), layout)

        assertTrue(result !is SpeciesIndexResolution.Resolved)
        assertEquals(0, result.values[1])
        assertEquals(0, result.values[speciesCount - 1])
    }


    @Test
    fun findsGenOneInternalToDexPermutationWithMissingSlots() {
        val bytes = byteArrayOf(0x7F, 0x7F, 1, 0, 3, 2, 0x7F)
        val layout = ResolvedRomLayout(
            EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 4,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(0, 4, 10),
                baseStats = TableLayout(0, 3, 28),
            ),
        )

        assertEquals(
            mapOf(1 to 1, 2 to 0, 3 to 3, 4 to 2),
            SpeciesIndexResolver.resolve(RomImage(bytes), layout),
        )
    }

    @Test
    fun findsGbaU16SpeciesToNationalDexTableWithReservedSlot() {
        val bytes = ByteArray(32) { 0x7F }
        val values = intArrayOf(1, 2, 0, 3)
        values.forEachIndexed { index, value ->
            bytes[4 + index * 2] = value.toByte()
            bytes[5 + index * 2] = (value ushr 8).toByte()
        }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 5,
            moveCount = 1,
            tables = ProfileTables(),
        )

        assertEquals(
            mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 0, 4 to 3),
            SpeciesIndexResolver.resolve(RomImage(bytes), layout),
        )
    }

    @Test
    fun distinguishesSpeciesToDexTableFromItsReverseAtGenThreeBoundary() {
        val count = 413
        val bytes = ByteArray(2200) { 0x7F }
        val speciesToDex = IntArray(count) { index ->
            val id = index + 1
            when (id) {
                in 1..251 -> id
                in 252..276 -> 185 + id
                else -> id - 25
            }
        }
        val dexToSpecies = IntArray(count) { index ->
            val id = index + 1
            when (id) {
                in 1..251 -> id
                in 252..388 -> id + 25
                else -> id - 137
            }
        }
        speciesToDex.forEachIndexed { index, value -> putU16(bytes, index * 2, value) }
        dexToSpecies.forEachIndexed { index, value -> putU16(bytes, 1000 + index * 2, value) }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = count + 1,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(252, result[277])
        assertEquals(279, result[304])
    }

    @Test
    fun findsExpandedGbaPermutationWhenTheFirstInternalSpeciesIsNotDexOne() {
        val count = 412
        val bytes = ByteArray(1800) { 0x7F }
        val mapping = IntArray(count) { it }
        for (internalId in 1..276) mapping[internalId] = internalId + 135
        for (internalId in 277..411) mapping[internalId] = internalId - 276
        mapping.forEachIndexed { index, value -> putU16(bytes, 128 + index * 2, value) }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = count,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(136, result[1])
        assertEquals(1, result[277])
        assertEquals(135, result[411])
    }

    @Test
    fun canonicalSpeciesToDexTableWinsOverAnUnrelatedCompletePermutation() {
        val speciesCount = 412
        val bytes = ByteArray(2200) { 0x7F }
        val unrelatedPermutation = IntArray(speciesCount) { index ->
            if (index == 0) 0 else ((index + 201) % (speciesCount - 1)) + 1
        }
        val speciesToDex = IntArray(speciesCount - 1) { index ->
            val internalId = index + 1
            when (internalId) {
                in 1..251 -> internalId
                in 252..276 -> internalId + 135
                else -> internalId - 25
            }
        }
        unrelatedPermutation.forEachIndexed { index, value -> putU16(bytes, 64 + index * 2, value) }
        speciesToDex.forEachIndexed { index, value -> putU16(bytes, 1100 + index * 2, value) }
        val layout = ResolvedRomLayout(
            EngineFamily.RUBY_SAPPHIRE,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(1, result[1])
        assertEquals(252, result[277])
    }

    @Test
    fun ignoresPrefixMatchedTablesThatReuseManyPositiveDexNumbers() {
        val bytes = ByteArray(64) { 0x7F }
        listOf(1, 2, 3, 4, 5, 6, 1, 2).forEachIndexed { index, value ->
            putU16(bytes, 8 + index * 2, value)
        }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 9,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[7])
        assertEquals(0, result[8])
    }

    @Test
    fun acceptsCompiledReferencedCanonicalMapWithReservedSlotsAndRepeatedFormDexNumbers() {
        val speciesCount = 300
        val tableOffset = 0x1000
        val namesOffset = 0x2000
        val bytes = ByteArray(0x3000) { 0x7F }
        val mapping = IntArray(speciesCount - 1) { index ->
            val internalId = index + 1
            when (internalId) {
                in 1..251 -> internalId
                in 252..276 -> 0
                277 -> 252
                278, 279 -> 253
                else -> internalId - 26
            }
        }
        mapping.forEachIndexed { index, value -> putU16(bytes, tableOffset + index * 2, value) }
        repeat(speciesCount) { id ->
            if (id in 252..276) {
                bytes[namesOffset + id * 11] = 0xFF.toByte()
            } else {
                putFixedGbaName(bytes, namesOffset, id, "MON")
            }
        }
        putThumbLiteralReference(bytes, instructionOffset = 0, literalOffset = 4, target = tableOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(speciesNames = TableLayout(namesOffset, speciesCount, 11)),
            compiledGbaReferences = GbaCompiledReferenceIndex(mapOf(tableOffset to 1)),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[252])
        assertEquals(252, result[277])
        assertEquals(253, result[278])
        assertEquals(253, result[279])
    }

    @Test
    fun acceptsCompiledReferencedCanonicalMapThatRepurposesSomeLegacyReservedSlots() {
        val speciesCount = 330
        val tableOffset = 0x1000
        val namesOffset = 0x2000
        val bytes = ByteArray(0x4000) { 0x7F }
        val mapping = IntArray(speciesCount - 1) { index ->
            val internalId = index + 1
            when (internalId) {
                in 1..251 -> internalId
                252 -> 300
                253 -> 76
                in 254..276 -> 0
                else -> internalId - 25
            }
        }
        mapping.forEachIndexed { index, value -> putU16(bytes, tableOffset + index * 2, value) }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putThumbLiteralReference(bytes, instructionOffset = 0, literalOffset = 4, target = tableOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(speciesNames = TableLayout(namesOffset, speciesCount, 11)),
            compiledGbaReferences = GbaCompiledReferenceIndex(mapOf(tableOffset to 1)),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(300, result[252])
        assertEquals(76, result[253])
        assertEquals(0, result[254])
        assertEquals(252, result[277])
        assertEquals(304, result[329])
    }

    @Test
    fun rejectsUnreferencedCanonicalMapWithRepeatedFormDexNumbers() {
        val speciesCount = 300
        val tableOffset = 0x1000
        val namesOffset = 0x2000
        val bytes = ByteArray(0x3000) { 0x7F }
        repeat(speciesCount - 1) { index ->
            val internalId = index + 1
            val dex = when (internalId) {
                in 1..251 -> internalId
                in 252..276 -> 0
                277 -> 252
                278, 279 -> 253
                else -> internalId - 26
            }
            putU16(bytes, tableOffset + index * 2, dex)
        }
        repeat(speciesCount) { id ->
            if (id in 252..276) bytes[namesOffset + id * 11] = 0xFF.toByte()
            else putFixedGbaName(bytes, namesOffset, id, "MON")
        }
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(speciesNames = TableLayout(namesOffset, speciesCount, 11)),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[277])
        assertEquals(0, result[279])
    }

    @Test
    fun rejectsCompiledReferencedDuplicateMapWithoutTheCanonicalBoundaryShape() {
        val speciesCount = 12
        val tableOffset = 0x100
        val namesOffset = 0x200
        val bytes = ByteArray(0x400) { 0x7F }
        listOf(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5).forEachIndexed { index, dex ->
            putU16(bytes, tableOffset + index * 2, dex)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putThumbLiteralReference(bytes, instructionOffset = 0, literalOffset = 4, target = tableOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(speciesNames = TableLayout(namesOffset, speciesCount, 11)),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[7])
        assertEquals(0, result[11])
    }

    @Test
    fun acceptsCompiledIndexedCuratedDexMapWithoutCanonicalPrefix() {
        val speciesCount = 12
        val tableOffset = 0x100
        val namesOffset = 0x200
        val bytes = ByteArray(0x400) { 0x7F }
        listOf(3, 4, 0, 1, 2, 5, 6, 2, 7, 0, 7).forEachIndexed { index, dex ->
            putU16(bytes, tableOffset + index * 2, dex)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x20, literalOffset = 0x40, target = tableOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, 8, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(3, result[1])
        assertEquals(0, result[3])
        assertEquals(1, result[4])
        assertEquals(2, result[8])
        assertEquals(7, result[11])
    }

    @Test
    fun rejectsUnreferencedCuratedDexData() {
        val speciesCount = 12
        val tableOffset = 0x100
        val namesOffset = 0x200
        val bytes = ByteArray(0x400) { 0x7F }
        listOf(3, 4, 0, 1, 2, 5, 6, 2, 7, 0, 7).forEachIndexed { index, dex ->
            putU16(bytes, tableOffset + index * 2, dex)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, 8, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[1])
        assertEquals(0, result[3])
    }

    @Test
    fun rejectsCuratedDexDataReferencedByWrongRegisterFlow() {
        val speciesCount = 12
        val tableOffset = 0x100
        val namesOffset = 0x200
        val bytes = ByteArray(0x400) { 0x7F }
        listOf(3, 4, 0, 1, 2, 5, 6, 2, 7, 0, 7).forEachIndexed { index, dex ->
            putU16(bytes, tableOffset + index * 2, dex)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x20, literalOffset = 0x40, target = tableOffset)
        putU16(bytes, 0x26, 0x1849) // adds r1, r1, r1; the table base is not used
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, 8, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[1])
        assertEquals(0, result[3])
    }

    @Test
    fun compiledCuratedDexMapWinsOverDenseIdentityDecoy() {
        val speciesCount = 12
        val curatedOffset = 0x100
        val decoyOffset = 0x180
        val namesOffset = 0x200
        val bytes = ByteArray(0x400) { 0x7F }
        listOf(3, 4, 0, 1, 2, 5, 6, 2, 7, 0, 7).forEachIndexed { index, dex ->
            putU16(bytes, curatedOffset + index * 2, dex)
        }
        repeat(speciesCount - 1) { index -> putU16(bytes, decoyOffset + index * 2, index + 1) }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x20, literalOffset = 0x40, target = curatedOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, 8, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(3, result[1])
        assertEquals(0, result[3])
        assertEquals(1, result[4])
    }

    @Test
    fun rejectsTwoEquallyStrongCompiledCuratedDexRoots() {
        val speciesCount = 12
        val firstOffset = 0x100
        val secondOffset = 0x140
        val namesOffset = 0x200
        val bytes = ByteArray(0x400) { 0x7F }
        val first = listOf(3, 4, 0, 1, 2, 5, 6, 2, 7, 0, 7)
        val second = listOf(4, 3, 0, 2, 1, 5, 6, 2, 7, 0, 7)
        first.forEachIndexed { index, dex -> putU16(bytes, firstOffset + index * 2, dex) }
        second.forEachIndexed { index, dex -> putU16(bytes, secondOffset + index * 2, dex) }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x20, literalOffset = 0x60, target = firstOffset)
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x30, literalOffset = 0x64, target = secondOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, 8, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[1])
        assertEquals(0, result[2])
    }

    @Test
    fun canonicalPrefixPermutationResolvesOtherwiseAmbiguousCompiledLookups() {
        val speciesCount = 12
        val canonicalOffset = 0x100
        val reverseOffset = 0x140
        val orderOffset = 0x180
        val namesOffset = 0x200
        val bytes = ByteArray(0x500) { 0x7F }
        val canonical = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 10)
        val reverse = listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 1, 2)
        val order = listOf(4, 5, 6, 7, 8, 9, 10, 11, 1, 2, 3)
        canonical.forEachIndexed { index, dex -> putU16(bytes, canonicalOffset + index * 2, dex) }
        reverse.forEachIndexed { index, dex -> putU16(bytes, reverseOffset + index * 2, dex) }
        order.forEachIndexed { index, dex -> putU16(bytes, orderOffset + index * 2, dex) }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x20, literalOffset = 0x60, target = canonicalOffset)
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x30, literalOffset = 0x64, target = reverseOffset)
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x40, literalOffset = 0x68, target = orderOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, speciesCount, 32),
            ),
            compiledGbaReferences = GbaCompiledReferenceIndex(
                mapOf(canonicalOffset to 1, reverseOffset to 1, orderOffset to 1),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(11, result[10])
        assertEquals(10, result[11])
    }

    @Test
    fun unreferencedCanonicalPrefixDecoyDoesNotResolveCompiledAmbiguity() {
        val speciesCount = 12
        val decoyOffset = 0x100
        val firstCompiledOffset = 0x140
        val secondCompiledOffset = 0x180
        val namesOffset = 0x200
        val bytes = ByteArray(0x500) { 0x7F }
        val decoy = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 10)
        val firstCompiled = listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 1, 2)
        val secondCompiled = listOf(4, 5, 6, 7, 8, 9, 10, 11, 1, 2, 3)
        decoy.forEachIndexed { index, dex -> putU16(bytes, decoyOffset + index * 2, dex) }
        firstCompiled.forEachIndexed { index, dex -> putU16(bytes, firstCompiledOffset + index * 2, dex) }
        secondCompiled.forEachIndexed { index, dex -> putU16(bytes, secondCompiledOffset + index * 2, dex) }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x20, literalOffset = 0x60, target = firstCompiledOffset)
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x30, literalOffset = 0x64, target = secondCompiledOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, speciesCount, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[10])
        assertEquals(0, result[11])
    }

    @Test
    fun equalCompiledCanonicalPrefixCandidatesRemainAmbiguous() {
        val speciesCount = 13
        val firstOffset = 0x100
        val secondOffset = 0x140
        val reverseOffset = 0x180
        val namesOffset = 0x200
        val bytes = ByteArray(0x500) { 0x7F }
        val first = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 10, 12)
        val second = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 11, 10)
        val reverse = listOf(4, 5, 6, 7, 8, 9, 10, 11, 12, 1, 2, 3)
        first.forEachIndexed { index, dex -> putU16(bytes, firstOffset + index * 2, dex) }
        second.forEachIndexed { index, dex -> putU16(bytes, secondOffset + index * 2, dex) }
        reverse.forEachIndexed { index, dex -> putU16(bytes, reverseOffset + index * 2, dex) }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x20, literalOffset = 0x60, target = firstOffset)
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x30, literalOffset = 0x64, target = secondOffset)
        putCompiledIndexedU16Lookup(bytes, instructionOffset = 0x40, literalOffset = 0x68, target = reverseOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, speciesCount, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[10])
        assertEquals(0, result[12])
    }

    @Test
    fun coalescesByteIdenticalCompiledSpeciesMapsBeforeResolvingExpandedGenThreeDomain() {
        val speciesCount = 474
        val firstSpeciesMapOffset = 0x400
        val aliasedSpeciesMapOffset = 0x800
        val regionalOrderOffset = 0xC00
        val namesOffset = 0x1200
        val bytes = ByteArray(0x3000) { 0x7F }
        val speciesToDex = IntArray(speciesCount - 1) { index ->
            val speciesId = index + 1
            when (speciesId) {
                in 1..251 -> speciesId
                in 277..451 -> speciesId - 25
                else -> 0
            }
        }
        val regionalOrder = IntArray(speciesCount - 1) { index ->
            if (index < 448) ((index + 251) % 448) + 1 else 472
        }
        speciesToDex.forEachIndexed { index, dex ->
            putU16(bytes, firstSpeciesMapOffset + index * 2, dex)
            putU16(bytes, aliasedSpeciesMapOffset + index * 2, dex)
        }
        regionalOrder.forEachIndexed { index, dex ->
            putU16(bytes, regionalOrderOffset + index * 2, dex)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, 0x20, 0x80, firstSpeciesMapOffset)
        putCompiledIndexedU16Lookup(bytes, 0x30, 0x84, aliasedSpeciesMapOffset)
        putCompiledIndexedU16Lookup(bytes, 0x40, 0x88, regionalOrderOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x2C00, 1, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(251, result[251])
        assertEquals(0, result[255])
        assertEquals(252, result[277])
        assertEquals(426, result[451])
        assertEquals(0, result[473])
    }

    @Test
    fun unresolvedExpandedGenThreeMapFailsClosedInsteadOfPublishingPhysicalIdsAsDexNumbers() {
        val speciesCount = 414
        val firstOffset = 0x400
        val secondOffset = 0x800
        val namesOffset = 0xC00
        val bytes = ByteArray(0x2400) { 0x7F }
        repeat(speciesCount - 1) { index ->
            putU16(bytes, firstOffset + index * 2, ((index + 100) % 400) + 1)
            putU16(bytes, secondOffset + index * 2, ((index + 200) % 400) + 1)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, 0x20, 0x80, firstOffset)
        putCompiledIndexedU16Lookup(bytes, 0x30, 0x84, secondOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x2200, 401, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[1])
        assertEquals(0, result[speciesCount - 1])
    }

    @Test
    fun stockSizedAmbiguousCompiledMapsFailClosedBeforeStructuralPermutationFallback() {
        val speciesCount = 12
        val firstOffset = 0x100
        val secondOffset = 0x140
        val permutationOffset = 0x300
        val namesOffset = 0x500
        val bytes = ByteArray(0xA00) { 0x7F }
        val first = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 1, 2)
        val second = intArrayOf(4, 5, 6, 7, 8, 9, 10, 11, 1, 2, 3)
        val structuralPermutation = intArrayOf(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 1)
        first.forEachIndexed { index, dex -> putU16(bytes, firstOffset + index * 2, dex) }
        second.forEachIndexed { index, dex -> putU16(bytes, secondOffset + index * 2, dex) }
        structuralPermutation.forEachIndexed { index, dex ->
            putU16(bytes, permutationOffset + index * 2, dex)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, 0x20, 0x80, firstOffset)
        putCompiledIndexedU16Lookup(bytes, 0x30, 0x84, secondOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x800, speciesCount, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), layout)

        assertTrue(result is SpeciesIndexResolution.Unavailable)
        result as SpeciesIndexResolution.Unavailable
        assertTrue(result.ambiguous)
        assertTrue(result.values.values.none { it > 0 })
    }

    @Test
    fun stockSizedBudgetFailureDoesNotPublishIdentityAsAUsableDexMap() {
        val fixture = compiledBudgetFixture(
            ResolutionLimits(maxProbeRootsPerDataset = 1),
        )

        val result = SpeciesIndexResolver.resolve(fixture.first, fixture.second)

        assertTrue(result is SpeciesIndexResolution.BudgetExceeded)
        assertTrue(result.values.values.none { it > 0 })
    }

    @Test
    fun compiledCompositionIdentifiesRegionalSpeciesMapAmongThreeCompletePermutations() {
        val speciesCount = 412
        val regionalOffset = 0x1000
        val nationalOffset = 0x1400
        val regionalToNationalOffset = 0x1800
        val namesOffset = 0x2000
        val bytes = ByteArray(0x4000) { 0x7F }
        val regional = deterministicPermutation(speciesCount - 1, seed = 0x13579BDF)
        val regionalToNational = deterministicPermutation(speciesCount - 1, seed = 0x2468ACE0)
        val national = IntArray(speciesCount - 1) { index ->
            regionalToNational[regional[index] - 1]
        }
        regional.forEachIndexed { index, value -> putU16(bytes, regionalOffset + index * 2, value) }
        national.forEachIndexed { index, value -> putU16(bytes, nationalOffset + index * 2, value) }
        regionalToNational.forEachIndexed { index, value ->
            putU16(bytes, regionalToNationalOffset + index * 2, value)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, 0x20, 0x80, nationalOffset)
        putCompiledIndexedU16Lookup(bytes, 0x30, 0x84, regionalOffset)
        putCompiledIndexedU16Lookup(bytes, 0x40, 0x88, regionalToNationalOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x3000, 387, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), layout)

        assertTrue(result is SpeciesIndexResolution.Resolved)
        assertEquals(regional.take(3), (1..3).map(result.values::get))
    }

    @Test
    fun compiledCompositionChargesRowsToTheSharedProbeWorkBudget() {
        val speciesCount = 12
        val regionalOffset = 0x100
        val nationalOffset = 0x140
        val regionalToNationalOffset = 0x180
        val namesOffset = 0x200
        val bytes = ByteArray(0x500) { 0x7F }
        val regional = deterministicPermutation(speciesCount - 1, seed = 0x13579BDF)
        val regionalToNational = deterministicPermutation(speciesCount - 1, seed = 0x2468ACE0)
        val national = IntArray(speciesCount - 1) { index ->
            regionalToNational[regional[index] - 1]
        }
        regional.forEachIndexed { index, value -> putU16(bytes, regionalOffset + index * 2, value) }
        national.forEachIndexed { index, value -> putU16(bytes, nationalOffset + index * 2, value) }
        regionalToNational.forEachIndexed { index, value ->
            putU16(bytes, regionalToNationalOffset + index * 2, value)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, 0x20, 0x80, nationalOffset)
        putCompiledIndexedU16Lookup(bytes, 0x30, 0x84, regionalOffset)
        putCompiledIndexedU16Lookup(bytes, 0x40, 0x88, regionalToNationalOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, speciesCount, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolveWithEvidence(
            RomImage(bytes),
            layout,
            ResolutionLimits(maxProbeWorkPerDataset = 4),
        )

        assertTrue(result is SpeciesIndexResolution.BudgetExceeded)
        result as SpeciesIndexResolution.BudgetExceeded
        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertEquals(5, result.observed)
        assertEquals(4, result.limit)
        assertTrue(result.values.values.none { it > 0 })
    }

    @Test
    fun defectiveCompositionInputCannotBePublishedAsTheSpeciesMap() {
        val speciesCount = 12
        val defectiveRegionalOffset = 0x100
        val defectiveNationalOffset = 0x140
        val regionalToNationalOffset = 0x180
        val namesOffset = 0x200
        val bytes = ByteArray(0x500) { 0x7F }
        val defectiveRegional = IntArray(speciesCount - 1) { index -> index + 1 }.also {
            it[0] = 2
        }
        val regionalToNational = deterministicPermutation(speciesCount - 1, seed = 0x2468ACE0)
        val defectiveNational = IntArray(speciesCount - 1) { index ->
            regionalToNational[defectiveRegional[index] - 1]
        }
        defectiveRegional.forEachIndexed { index, value ->
            putU16(bytes, defectiveRegionalOffset + index * 2, value)
        }
        defectiveNational.forEachIndexed { index, value ->
            putU16(bytes, defectiveNationalOffset + index * 2, value)
        }
        regionalToNational.forEachIndexed { index, value ->
            putU16(bytes, regionalToNationalOffset + index * 2, value)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, 0x20, 0x80, defectiveNationalOffset)
        putCompiledIndexedU16Lookup(bytes, 0x30, 0x84, defectiveRegionalOffset)
        putCompiledIndexedU16Lookup(bytes, 0x40, 0x88, regionalToNationalOffset)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x300, speciesCount, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), layout)

        assertTrue(result is SpeciesIndexResolution.Unavailable)
        assertTrue(result.values.values.none { it > 0 })
    }

    @Test
    fun smallNoEvidenceCompatibilityMapRemainsTypedUnavailable() {
        val speciesCount = 6
        val namesOffset = 0x100
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
            ),
        )
        val rom = RomImage(ByteArray(0x200) { 0x7F })

        val result = SpeciesIndexResolver.resolveWithEvidence(
            rom,
            layout,
        )

        assertTrue(result is SpeciesIndexResolution.Unavailable)
        assertTrue(result.values.values.none { it > 0 })
        assertEquals((0 until speciesCount).associateWith { it }, SpeciesIndexResolver.resolve(rom, layout))
        assertTrue(RecordMaterializers.species(rom, layout).isEmpty())
    }

    private fun deterministicPermutation(size: Int, seed: Int): IntArray {
        val values = IntArray(size) { it + 1 }
        var state = seed.toLong() and 0xFFFF_FFFFL
        for (index in values.lastIndex downTo 1) {
            state = (state * 1_664_525L + 1_013_904_223L) and 0xFFFF_FFFFL
            val swap = (state % (index + 1)).toInt()
            val current = values[index]
            values[index] = values[swap]
            values[swap] = current
        }
        return values
    }

    @Test
    fun ambiguousCompleteExpandedPermutationsRemainUnresolvedInsteadOfFollowingRomOrder() {
        val speciesCount = 414
        val firstOffset = 0x400
        val secondOffset = 0x800
        val bytes = ByteArray(0x1800) { 0x7F }
        val first = intArrayOf(0) + (2 until speciesCount).toList().toIntArray() + intArrayOf(1)
        val second = intArrayOf(0) + (3 until speciesCount).toList().toIntArray() + intArrayOf(1, 2)
        first.forEachIndexed { index, value -> putU16(bytes, firstOffset + index * 2, value) }
        second.forEachIndexed { index, value -> putU16(bytes, secondOffset + index * 2, value) }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(0, result[1])
        assertEquals(0, result[speciesCount - 1])
    }

    @Test
    fun arbitraryDownstreamCallCannotExcludeAnOtherwiseEqualCompiledCandidate() {
        val speciesCount = 474
        val firstSpeciesMapOffset = 0x800
        val aliasedSpeciesMapOffset = 0xC00
        val regionalOrderOffset = 0x1000
        val namesOffset = 0x1800
        val firstWrapper = 0x100
        val aliasedWrapper = 0x140
        val regionalWrapper = 0x180
        val bytes = ByteArray(0x4000) { 0x7F }
        val speciesToDex = IntArray(speciesCount - 1) { index ->
            val speciesId = index + 1
            when (speciesId) {
                in 1..251 -> speciesId
                in 277..451 -> speciesId - 25
                else -> 0
            }
        }
        val regionalOrder = IntArray(speciesCount - 1) { index ->
            if (index < 448) ((index + 251) % 448) + 1 else 472
        }
        speciesToDex.forEachIndexed { index, dex ->
            putU16(bytes, firstSpeciesMapOffset + index * 2, dex)
            putU16(bytes, aliasedSpeciesMapOffset + index * 2, dex)
        }
        regionalOrder.forEachIndexed { index, dex ->
            putU16(bytes, regionalOrderOffset + index * 2, dex)
        }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Wrapper(bytes, firstWrapper, firstSpeciesMapOffset)
        putCompiledIndexedU16Wrapper(bytes, aliasedWrapper, aliasedSpeciesMapOffset)
        putCompiledIndexedU16Wrapper(bytes, regionalWrapper, regionalOrderOffset)
        putOrdinalLookupCall(bytes, 0x240, regionalWrapper, counterRegister = 4)
        putOrdinalLookupCall(bytes, 0x260, regionalWrapper, counterRegister = 5)
        putOrdinalLookupCall(bytes, 0x280, regionalWrapper, counterRegister = 6, consumeAsDexNumber = false)
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x3800, speciesCount, 32),
            ),
        )

        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(bytes), layout)

        assertTrue(result is SpeciesIndexResolution.Unavailable)
        result as SpeciesIndexResolution.Unavailable
        assertTrue(result.ambiguous)
        assertTrue(result.values.values.none { it > 0 })
    }

    @Test
    fun prefixCandidateDiscoveryReturnsTypedBudgetEvidenceBeforeRetainingPastLimit() {
        val speciesCount = 12
        val bytes = ByteArray(0x800) { 0x7F }
        val first = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 10)
        val second = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        first.forEachIndexed { index, dex -> putU16(bytes, 0x200 + index * 2, dex) }
        second.forEachIndexed { index, dex -> putU16(bytes, 0x300 + index * 2, dex) }
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, ""),
            limits = ResolutionLimits(maxCandidatesPerDataset = 1),
        )
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(session, layout)

        assertTrue(result is SpeciesIndexResolution.BudgetExceeded)
        result as SpeciesIndexResolution.BudgetExceeded
        assertEquals(BudgetKind.CANDIDATES, result.budgetKind)
        assertEquals(2, result.observed)
        assertEquals(1, result.limit)
    }

    @Test
    fun compiledLookupTargetCollectionStopsBeforeRetainingPastRootLimit() {
        val fixture = compiledBudgetFixture(
            ResolutionLimits(maxProbeRootsPerDataset = 1),
        )

        val result = SpeciesIndexResolver.resolve(fixture.first, fixture.second)

        assertTrue(result is SpeciesIndexResolution.BudgetExceeded)
        result as SpeciesIndexResolution.BudgetExceeded
        assertEquals(BudgetKind.PROBE_ROOTS, result.budgetKind)
        assertEquals(2, result.observed)
        assertEquals(1, result.limit)
    }

    @Test
    fun compiledIntArrayCandidatesStopBeforeAllocatingPastCandidateLimit() {
        val fixture = compiledBudgetFixture(
            ResolutionLimits(maxCandidatesPerDataset = 1),
        )

        val result = SpeciesIndexResolver.resolve(fixture.first, fixture.second)

        assertTrue(result is SpeciesIndexResolution.BudgetExceeded)
        result as SpeciesIndexResolution.BudgetExceeded
        assertEquals(BudgetKind.CANDIDATES, result.budgetKind)
        assertEquals(2, result.observed)
        assertEquals(1, result.limit)
    }

    private fun compiledBudgetFixture(
        limits: ResolutionLimits,
    ): Pair<RomAnalysisSession, ResolvedRomLayout> {
        val speciesCount = 12
        val namesOffset = 0x500
        val bytes = ByteArray(0xA00) { 0x7F }
        val first = listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 1, 2)
        val second = listOf(4, 5, 6, 7, 8, 9, 10, 11, 1, 2, 3)
        first.forEachIndexed { index, dex -> putU16(bytes, 0x300 + index * 2, dex) }
        second.forEachIndexed { index, dex -> putU16(bytes, 0x380 + index * 2, dex) }
        repeat(speciesCount) { id -> putFixedGbaName(bytes, namesOffset, id, "MON") }
        putCompiledIndexedU16Lookup(bytes, 0x20, 0x80, 0x300)
        putCompiledIndexedU16Lookup(bytes, 0x30, 0x84, 0x380)
        val rom = RomImage(bytes)
        return RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, ""),
            limits = limits,
        ) to ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                descriptions = TableLayout(0x800, speciesCount, 32),
            ),
        )
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putFixedGbaName(bytes: ByteArray, tableOffset: Int, index: Int, name: String) {
        val offset = tableOffset + index * 11
        name.forEachIndexed { characterIndex, character ->
            bytes[offset + characterIndex] = (0xBB + character.code - 'A'.code).toByte()
        }
        bytes[offset + name.length] = 0xFF.toByte()
    }

    private fun putThumbLiteralReference(
        bytes: ByteArray,
        instructionOffset: Int,
        literalOffset: Int,
        target: Int,
    ) {
        val pc = (instructionOffset + 4) and -4
        val literalWord = (literalOffset - pc) / 4
        putU16(bytes, instructionOffset, 0x4800 or literalWord)
        val pointer = 0x08000000L + target
        repeat(4) { byte ->
            bytes[literalOffset + byte] = ((pointer ushr (byte * 8)) and 0xFF).toByte()
        }
    }

    private fun putCompiledIndexedU16Lookup(
        bytes: ByteArray,
        instructionOffset: Int,
        literalOffset: Int,
        target: Int,
    ) {
        putThumbLiteralReference(bytes, instructionOffset, literalOffset, target)
        putU16(bytes, instructionOffset + 2, 0x3901) // subs r1, #1
        putU16(bytes, instructionOffset + 4, 0x0049) // lsls r1, r1, #1
        putU16(bytes, instructionOffset + 6, 0x1809) // adds r1, r1, r0
        putU16(bytes, instructionOffset + 8, 0x8808) // ldrh r0, [r1]
    }

    private fun putCompiledIndexedU16Wrapper(bytes: ByteArray, wrapperOffset: Int, target: Int) {
        putU16(bytes, wrapperOffset, 0xB500) // push {lr}
        putU16(bytes, wrapperOffset + 2, 0x0400) // lsls r0, r0, #16
        putU16(bytes, wrapperOffset + 4, 0x0C01) // lsrs r1, r0, #16
        putU16(bytes, wrapperOffset + 6, 0x2900) // cmp r1, #0
        putU16(bytes, wrapperOffset + 8, 0xD008) // beq zero return
        putCompiledIndexedU16Lookup(bytes, wrapperOffset + 10, wrapperOffset + 24, target)
        putU16(bytes, wrapperOffset + 20, 0xE003) // b return
        putU16(bytes, wrapperOffset + 22, 0)
    }

    private fun putOrdinalLookupCall(
        bytes: ByteArray,
        callOffset: Int,
        wrapperOffset: Int,
        counterRegister: Int,
        consumeAsDexNumber: Boolean = true,
    ) {
        putU16(bytes, callOffset - 4, 0x3001 or (counterRegister shl 8)) // adds rN, #1
        putU16(bytes, callOffset - 2, 0x1C00 or (counterRegister shl 3)) // adds r0, rN, #0
        putThumbBl(bytes, callOffset, wrapperOffset)
        if (!consumeAsDexNumber) return
        putU16(bytes, callOffset + 4, 0x0400) // lsls r0, r0, #16
        putU16(bytes, callOffset + 6, 0x0C00) // lsrs r0, r0, #16
        putU16(bytes, callOffset + 8, 0x2101) // movs r1, #1
        putThumbBl(bytes, callOffset + 10, 0x300)
    }

    private fun putThumbBl(bytes: ByteArray, instructionOffset: Int, targetOffset: Int) {
        val displacement = targetOffset - (instructionOffset + 4)
        putU16(bytes, instructionOffset, 0xF000 or ((displacement shr 12) and 0x7FF))
        putU16(bytes, instructionOffset + 2, 0xF800 or ((displacement shr 1) and 0x7FF))
    }

    private companion object {
        // Exact 419 little-endian u16 words at Dark Violet 0x251FEE (SHA-256 074D96B3...547F7).
        const val DARK_VIOLET_SPECIES_TO_DEX_BASE64 =
            "AQACAAMABAAFAAYABwAIAAkAHAAdAB4AHwAgACEACgALAAwADQAOAA8AEAAyADMAJQAmADoAOwARABIAEwAUABUAFgA9AD4AnwCgABoAGwA/AEAAUgBTAFQAQgBDAFgAWQCtAK4AbQBuAEcASAAXABgAhgCHAEkASgBLAFoAWwBcADQANQA2AF0AXgBfAIAAgQApACoAKwBvAHAAYABhADcAOAC0ALAAsQDDAMQAlwCYAIIAgwBEAEUARgAwAHEAcgCmAKcAigCLAJEAkgCdAJ4AvwDAALUAmQCaAKMApAC8AKEAtwCoAKkAhACFAGsAbAB0AHsAxgCNAHkAuADIAFAAUQDCAK8AYwAAAGUAZgB1ACwALQAuAC8AxwCUAM0AzgDPAMkAygDLANEA0gAoADkAZABpAGoAcwB3AHoAjgCPAJAAIgAjAJMAnACiAKUAQQC2ALsAJAA8ABkAzADQAAAAAAAAAAAAAABVAE4ATwAAAEwAfQB+AH8AAABWAFcAAAAAAAAAZwBoACcAYgCbAAAAAAAAAAAAAAAAAAAAMQAAAAAA0wB8ANUA1gDXANgA2QCyALMA3ADdAN4A3wDgAOEA4gDjAJUAlgCqAOcA6AB2AOoA6wC+AMEAxQCMAHgA8QC9APMA9AD1APYA9wD4APkA+gD7AIMBhAGFAYYBhwGIAYkBigGLAYwBjQGOAY8BkAGRAZIBkwGUAZUBlgGXAZgBmQGaAZsB/AD9AP4A/wAAAQEBAgEDAQQBBQEGAQcBCAEJAQoBCwEMAQ0BDgEPARABEQESARMBIgEjASQBFAEVAR0BHgFHAasArAAbARwBQAFBASwBLQFgAVcBWAErAUQBLgFTAVQBcgFVAVYBuQC6AD4BPwFIAUkBSgEoASkBNQE2AUIBQwFrAWwBbQFLAUwBaQFqAVEBUgFNAEUBRgE3ATgBLwEzATQBiACJAGgBYwFkATsBHwEgASEBPAE9AWUBJQEmAScBbgFvAXABZwFhAWIBUAFPAXEBMAExATIBXwE5AToBWQFaAVsBXAEYARkBGgFzAXQBdQF2AXcBeAF5AXoBewF+AX8BgAF8AX0BgQGCAWYB/AD9AP4A/wAAAQEBAgEDAQ=="
    }
}
