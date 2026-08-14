package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetResolversTest {
    @Test
    fun reconcilesReadableMoveNamesToTheValidatedMoveDataPrefix() {
        val data = ValidationEvidence(
            compatible = true,
            validRecords = 793,
            totalRecords = 793,
            confidence = 1.0,
            reasons = emptyList(),
        )

        assertEquals(793, DatasetResolvers.reconciledMoveCount(923, data))
    }

    @Test
    fun resolvesRelocatedGen3DescriptionArrayFromRecordShape() {
        val bytes = ByteArray(0x1000)
        repeat(3) { index ->
            val base = 0x200 + index * 32
            putGbaText(bytes, base, if (index == 0) "UNKNOWN" else "SEED")
            putU16(bytes, base + 12, if (index == 0) 0 else 7)
            putU16(bytes, base + 14, if (index == 0) 0 else 69)
            putU32(bytes, base + 16, 0x08000800 + index * 0x20)
            putGbaText(bytes, 0x800 + index * 0x20, "POKEMON TEXT")
        }

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 3, inherited = TableLayout(0x600, 3, 32),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
    }

    @Test
    fun resolvesCompiledReferencedGen3DescriptionArrayWithoutRetailCategorySeed() {
        val bytes = ByteArray(0x3000)
        val table = 0x400
        val count = 8
        repeat(count) { index ->
            val record = table + index * 36
            putGbaText(bytes, record, if (index == 0) "UNKNOWN" else "CUSTOM")
            putU16(bytes, record + 12, if (index == 0) 0 else 7)
            putU16(bytes, record + 14, if (index == 0) 0 else 69)
            putU32(bytes, record + 16, 0x08002000 + index * 0x20)
            putGbaText(bytes, 0x2000 + index * 0x20, "POKEMON TEXT")
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, table, table)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = count, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(count, result.validRecords)
        assertEquals(36, result.recordSize)
    }

    @Test
    fun compiledReferencedDescriptionRootBeatsLargerUnreferencedFullDecoy() {
        val bytes = ByteArray(0x5000)
        val referencedTable = 0x400
        repeat(6) { index ->
            val record = referencedTable + index * 36
            putGbaText(bytes, record, if (index == 0) "UNKNOWN" else "CUSTOM")
            putU16(bytes, record + 12, if (index == 0) 0 else 7)
            putU16(bytes, record + 14, if (index == 0) 0 else 69)
            putU32(bytes, record + 16, 0x08003000 + index * 0x20)
            putGbaText(bytes, 0x3000 + index * 0x20, "ROM DESCRIPTION")
        }
        putDescriptionTable(bytes, offset = 0x1000, count = 8, textOffset = 0x3800)
        putVerifiedDescriptionConsumer36(bytes, 0x80, 0x100, referencedTable)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 8, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(referencedTable, result.offset)
        assertEquals(6, result.totalRecords)
    }

    @Test
    fun genericReferencesDoNotGiveAPartialDescriptionRootCompiledAuthority() {
        val bytes = ByteArray(0x5000)
        val genericTable = 0x400
        repeat(6) { index ->
            val record = genericTable + index * 36
            putGbaText(bytes, record, if (index == 0) "UNKNOWN" else "CUSTOM")
            putU16(bytes, record + 12, if (index == 0) 0 else 7)
            putU16(bytes, record + 14, if (index == 0) 0 else 69)
            putU32(bytes, record + 16, 0x08003000 + index * 0x20)
            putGbaText(bytes, 0x3000 + index * 0x20, "ROM DESCRIPTION")
        }
        val structuralTable = 0x1000
        putDescriptionTable(bytes, structuralTable, count = 8, textOffset = 0x3800)
        putThumbLiteralReferences(bytes, 0x80, 0x100, genericTable, genericTable)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 8, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(structuralTable, result.offset)
        assertEquals(8, result.totalRecords)
    }

    @Test
    fun verifiedThirtyTwoByteConsumerCanAuthorizeAPartialDescriptionRoot() {
        val bytes = ByteArray(0x6000)
        val compiledTable = 0x400
        repeat(6) { index ->
            val record = compiledTable + index * 32
            putGbaText(bytes, record, if (index == 0) "UNKNOWN" else "CUSTOM")
            putU16(bytes, record + 12, if (index == 0) 0 else 7)
            putU16(bytes, record + 14, if (index == 0) 0 else 69)
            putU32(bytes, record + 16, 0x08004000 + index * 0x20)
            putGbaText(bytes, 0x4000 + index * 0x20, "ROM DESCRIPTION")
        }
        putDescriptionTable32(bytes, offset = 0x1000, count = 8, textOffset = 0x4800)
        putVerifiedDescriptionConsumer32(bytes, 0x80, 0x100, compiledTable)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 8, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(compiledTable, result.offset)
        assertEquals(6, result.totalRecords)
        assertEquals(32, result.recordSize)
    }

    @Test
    fun genericLiteralReferencesCannotOutrankAlteredEmeraldDescriptionCoverage() {
        val bytes = ByteArray(0xC000)
        val graphicsDescriptor = 0x800
        val descriptionTable = 0x2000
        val descriptionCount = 387
        putDescriptionTable32(bytes, descriptionTable, descriptionCount, 0x8000)
        putGbaText(bytes, graphicsDescriptor, "UNKNOWN")
        putU32(bytes, graphicsDescriptor + 16, 0x0800B800)
        putGbaText(bytes, 0xB800, "GRAPHICS TEXT")
        putThumbLiteralReferences(
            bytes,
            instructionOffset = 0x100,
            literalOffset = 0x180,
            firstTarget = graphicsDescriptor,
            secondTarget = graphicsDescriptor,
        )

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 474, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(descriptionTable, result.offset)
        assertEquals(descriptionCount, result.validRecords)
        assertEquals(descriptionCount, result.totalRecords)
        assertEquals(32, result.recordSize)
    }

    @Test
    fun oneRowGenericReferenceCannotSelfDefineExpandedDescriptionAvailability() {
        val bytes = ByteArray(0x5000)
        val descriptor = 0x800
        putGbaText(bytes, descriptor, "UNKNOWN")
        putU32(bytes, descriptor + 16, 0x08004000)
        putGbaText(bytes, 0x4000, "ACCIDENTAL TEXT")
        putThumbLiteralReferences(bytes, 0x100, 0x180, descriptor, descriptor)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 474, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertFalse(result.compatible)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "semantic domain" in it })
    }

    @Test
    fun structuralDescriptionAnchorsStopBeforeRetainingPastSessionCandidateLimit() {
        val bytes = ByteArray(0x6000)
        val count = 8
        putDescriptionTable32(bytes, offset = 0x400, count = count, textOffset = 0x3000)
        putDescriptionTable32(bytes, offset = 0x1000, count = count, textOffset = 0x4000)
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, ""),
            limits = ResolutionLimits(maxCandidatesPerDataset = 1),
        )

        val result = DatasetResolvers.gen3Descriptions(
            session = session,
            speciesCount = count,
            inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertFalse(result.compatible)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "candidate budget exceeded (2 > 1)" in it })
    }

    @Test
    fun compiledReferencedFullDescriptionRootBeatsCompleteUnreferencedInheritedDecoy() {
        val bytes = ByteArray(0x3000)
        val inheritedTable = 0x400
        val compiledTable = 0x800
        val count = 10
        putDescriptionTable(bytes, inheritedTable, count, 0x1800)
        putDescriptionTable(bytes, compiledTable, count, 0x2200)
        putVerifiedDescriptionConsumer36(bytes, 0x80, 0x100, compiledTable)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = count,
            inherited = TableLayout(inheritedTable, count, 36, pointerOffsets = listOf(16, 20)),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(compiledTable, result.offset)
    }

    @Test
    fun returnsReviewEvidenceWhenCompiledDescriptionCandidateBudgetIsExceeded() {
        val bytes = ByteArray(0x28000)
        val speciesCount = 3
        repeat(130) { index ->
            val table = 0x8000 + index * 0x100
            val text = 0x20000 + index * 0x60
            repeat(speciesCount) { row ->
                val record = table + row * 36
                putGbaText(bytes, record, if (row == 0) "UNKNOWN" else "CUSTOM")
                putU16(bytes, record + 12, if (row == 0) 0 else 7)
                putU16(bytes, record + 14, if (row == 0) 0 else 69)
                putU32(bytes, record + 16, 0x08000000 + text + row * 0x20)
                putU32(bytes, record + 20, 0x08000000 + text + row * 0x20)
                putGbaText(bytes, text + row * 0x20, "ROM DESCRIPTION")
            }
            val instruction = 0x1000 + index * 0x10
            putThumbLiteralReferences(bytes, instruction, instruction + 8, table, table)
        }

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertFalse(result.compatible)
        assertEquals(
            listOf(
                "Gen 3 Pokédex description candidate budget exceeded (256); " +
                    "automatic resolution requires review",
            ),
            result.reasons,
        )
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun resolvesPartialPokedexDescriptionArrayForExpandedSpeciesCatalog() {
        val bytes = ByteArray(0x1000)
        repeat(4) { index ->
            val base = 0x200 + index * 32
            putGbaText(bytes, base, if (index == 0) "UNKNOWN" else "SEED")
            putU16(bytes, base + 12, if (index == 0) 0 else 7)
            putU16(bytes, base + 14, if (index == 0) 0 else 69)
            putU32(bytes, base + 16, 0x08000800 + index * 0x20)
            putGbaText(bytes, 0x800 + index * 0x20, "POKEMON TEXT")
        }
        repeat(6) { index ->
            val base = 0x400 + index * 32
            putGbaText(bytes, base, if (index == 1) "SEED" else "OTHER")
            putU16(bytes, base + 12, 7)
            putU16(bytes, base + 14, 69)
            putU32(bytes, base + 16, 0x08000900 + index * 0x10)
            putGbaText(bytes, 0x900 + index * 0x10, "DECOY TEXT")
        }

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 8, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(4, result.totalRecords)
        assertEquals(0x200, result.offset)
    }

    @Test
    fun trimsAdjacentDescriptionDataAfterAHighConfidencePrefixWithAnInternalSourceGap() {
        val bytes = ByteArray(0x5000)
        val tableOffset = 0x200
        val physicalCount = 100
        val requestedCount = 104
        putDescriptionTable(bytes, tableOffset, physicalCount, textOffset = 0x3000)
        putU32(bytes, tableOffset + 50 * 36 + 16, 0)
        putU32(bytes, tableOffset + 50 * 36 + 20, 0)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = requestedCount,
            inherited = TableLayout(
                tableOffset,
                requestedCount,
                36,
                pointerOffsets = listOf(16, 20),
            ),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(tableOffset, result.offset)
        assertEquals(99, result.validRecords)
        assertEquals(physicalCount, result.totalRecords)
        assertTrue(result.reasons.any { "adjacent non-description data" in it })
    }

    @Test
    fun recoversAUniqueDescriptionPointerThatLandsOneByteBeforeBoundedNaturalText() {
        val bytes = ByteArray(0x5000)
        val tableOffset = 0x200
        val textOffset = 0x3000
        val count = 12
        val recoveredIndex = 5
        putDescriptionTable(bytes, tableOffset, count, textOffset)
        val badTarget = textOffset + recoveredIndex * 0x20
        bytes[badTarget] = 0xFF.toByte()
        putGbaText(bytes, badTarget + 1, "RECOVERED TEXT")

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = count,
            inherited = TableLayout(tableOffset, count, 36, pointerOffsets = listOf(16, 20)),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(count, result.validRecords)
        assertEquals(count, result.totalRecords)
        assertEquals(count, result.coveredRecords)
        assertEquals(count, result.expectedRecords)
        assertEquals(0, result.incompleteRecords)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "off-by-one description pointer" in it })
    }

    @Test
    fun doesNotRecoverDescriptionTextThatCrossesTheNextReferencedBoundary() {
        val bytes = ByteArray(0x5000)
        val tableOffset = 0x200
        val textOffset = 0x3000
        val count = 12
        val malformedIndex = 5
        putDescriptionTable(bytes, tableOffset, count, textOffset)
        val badTarget = textOffset + malformedIndex * 0x20
        bytes[badTarget] = 0xFF.toByte()
        (1 until 0x20).forEach { bytes[badTarget + it] = 0 }

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = count,
            inherited = TableLayout(tableOffset, count, 36, pointerOffsets = listOf(16, 20)),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertEquals(count - 1, result.validRecords)
        assertFalse(result.reasons.any { "off-by-one description pointer" in it })
    }

    @Test
    fun doesNotGuessBetweenTwoBoundedOffByOneDescriptionAlternatives() {
        val bytes = ByteArray(0x5000)
        val tableOffset = 0x200
        val textOffset = 0x3000
        val count = 12
        val malformedIndex = 5
        putDescriptionTable(bytes, tableOffset, count, textOffset)
        val firstTarget = textOffset + malformedIndex * 0x20
        val secondTarget = firstTarget + 0x10
        putU32(bytes, tableOffset + malformedIndex * 36 + 16, 0x08000000 + firstTarget)
        putU32(bytes, tableOffset + malformedIndex * 36 + 20, 0x08000000 + secondTarget)
        bytes[firstTarget] = 0xFF.toByte()
        putGbaText(bytes, firstTarget + 1, "FIRST")
        bytes[secondTarget] = 0xFF.toByte()
        putGbaText(bytes, secondTarget + 1, "SECOND")

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = count,
            inherited = TableLayout(tableOffset, count, 36, pointerOffsets = listOf(16, 20)),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertEquals(count - 1, result.validRecords)
        assertFalse(result.reasons.any { "off-by-one description pointer" in it })
    }

    @Test
    fun prefersLargerRelocatedDescriptionArrayOverValidInheritedPrefix() {
        val bytes = ByteArray(0x2000)
        putDescriptionTable(bytes, offset = 0x200, count = 4, textOffset = 0x1000)
        putDescriptionTable(bytes, offset = 0x500, count = 7, textOffset = 0x1400, firstCategory = "GLITCH")
        putVerifiedDescriptionConsumer36(bytes, 0x80, 0x100, 0x500)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = 9,
            inherited = TableLayout(0x200, 4, 36, pointerOffsets = listOf(16, 20)),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(0x500, result.offset)
        assertEquals(7, result.validRecords)
        assertEquals(7, result.totalRecords)
    }

    @Test
    fun unreferencedLargerDescriptionDecoyDoesNotBeatValidInheritedPrefix() {
        val bytes = ByteArray(0x2000)
        putDescriptionTable(bytes, offset = 0x200, count = 4, textOffset = 0x1000)
        putDescriptionTable(bytes, offset = 0x500, count = 7, textOffset = 0x1400)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = 9,
            inherited = TableLayout(0x200, 4, 36, pointerOffsets = listOf(16, 20)),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(4, result.totalRecords)
    }

    @Test
    fun equallyReferencedRelocatedDescriptionRootsAreAmbiguous() {
        val bytes = ByteArray(0x2400)
        putDescriptionTable(bytes, offset = 0x500, count = 7, textOffset = 0x1400)
        putDescriptionTable(bytes, offset = 0x800, count = 7, textOffset = 0x1A00)
        putThumbLiteralReferences(bytes, 0x80, 0x100, 0x500, 0x500)
        putThumbLiteralReferences(bytes, 0x84, 0x108, 0x800, 0x800)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = 9,
            inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertFalse(result.compatible)
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "conflicting" in it })
    }

    @Test
    fun equallyReferencedIndependentInheritedAndCompiledDescriptionRootsAreAmbiguous() {
        val bytes = ByteArray(0x3000)
        val inheritedTable = 0x400
        val compiledTable = 0x800
        val count = 10
        putDescriptionTable(bytes, inheritedTable, count, 0x1800)
        putDescriptionTable(bytes, compiledTable, count, 0x2200)
        putThumbLiteralReferences(bytes, 0x80, 0x100, inheritedTable, inheritedTable)
        putThumbLiteralReferences(bytes, 0x90, 0x108, compiledTable, compiledTable)

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes),
            speciesCount = count,
            inherited = TableLayout(inheritedTable, count, 36, pointerOffsets = listOf(16, 20)),
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertFalse(result.compatible)
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "conflicting" in it })
    }

    @Test
    fun resolvesThirtySixByteDescriptionArrayWithSparseRowBeforeShiftedSeed() {
        val bytes = ByteArray(0x2000)
        val availableRecords = 12
        val sparseIndex = 3
        val seedIndex = 8
        repeat(availableRecords) { index ->
            if (index == sparseIndex) return@repeat
            val base = 0x200 + index * 36
            val category = when (index) {
                0 -> "UNKNOWN"
                seedIndex -> "SEED"
                else -> "CUSTOM"
            }
            putGbaText(bytes, base, category)
            putU16(bytes, base + 12, if (index == 0) 0 else 7)
            putU16(bytes, base + 14, if (index == 0) 0 else 69)
            putU32(bytes, base + 16, 0x08001000 + index * 0x20)
            putGbaText(bytes, 0x1000 + index * 0x20, "POKEMON TEXT")
        }

        val result = DatasetResolvers.gen3Descriptions(
            RomImage(bytes), speciesCount = 16, inherited = null,
            codec = PokemonTextCodec.gbaEnglish,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(36, result.recordSize)
        assertEquals(availableRecords, result.totalRecords)
        assertEquals(availableRecords - 1, result.validRecords)
    }

    @Test
    fun resolvesExpandedEightSlotEvolutionArray() {
        val bytes = ByteArray(0x1000)
        val stride = 8 * 8
        putU16(bytes, 0x200 + stride, 4)
        putU16(bytes, 0x200 + stride + 2, 16)
        putU16(bytes, 0x200 + stride + 4, 2)
        putU16(bytes, 0x200 + stride * 2, 4)
        putU16(bytes, 0x200 + stride * 2 + 2, 32)
        putU16(bytes, 0x200 + stride * 2 + 4, 3)

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = 4, inherited = null,
        )

        assertTrue(result.toString(), result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(stride, result.recordSize)
    }

    @Test
    fun resolvesCfruThirtyTwoSlotEvolutionArray() {
        val bytes = ByteArray(0x2000)
        val stride = 32 * 8
        putU16(bytes, 0x200 + stride, 4)
        putU16(bytes, 0x200 + stride + 2, 21)
        putU16(bytes, 0x200 + stride + 4, 2)
        putU16(bytes, 0x200 + stride * 2, 4)
        putU16(bytes, 0x200 + stride * 2 + 2, 32)
        putU16(bytes, 0x200 + stride * 2 + 4, 3)

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = 4, inherited = null,
        )

        assertTrue(result.toString(), result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(stride, result.recordSize)
    }

    @Test
    fun resolvesReferencedNineSlotEvolutionTableWithoutCanonicalSpeciesAnchors() {
        val bytes = ByteArray(0x2000)
        val speciesCount = 10
        val table = 0x400
        val stride = 9 * 8
        val end = table + speciesCount * stride
        putThumbLiteralReferences(bytes, 0x80, 0x100, table, end)
        listOf(2 to 3, 5 to 6, 8 to 9).forEach { (species, target) ->
            val edge = table + species * stride + 8
            putU16(bytes, edge, 4)
            putU16(bytes, edge + 2, 16 + species)
            putU16(bytes, edge + 4, target)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(stride, result.recordSize)
        assertEquals(8, result.elementSize)
        assertEquals(speciesCount, result.validRecords)
    }

    @Test
    fun resolvesCompiledReferencedTenBySixEvolutionTable() {
        val bytes = ByteArray(0x3000)
        val speciesCount = 10
        val table = 0x1000
        val stride = 10 * 6
        putThumbLiteralReferences(
            bytes, 0x80, 0x100, table, table + speciesCount * stride,
        )
        listOf(2 to 3, 5 to 6, 8 to 9).forEach { (species, target) ->
            val edge = table + species * stride + 6
            putU16(bytes, edge, 4)
            putU16(bytes, edge + 2, 16 + species)
            putU16(bytes, edge + 4, target)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(stride, result.recordSize)
        assertEquals(6, result.elementSize)
        assertEquals(speciesCount, result.validRecords)
    }

    @Test
    fun resolvesCompiledReferencedEvolutionTableContainingOnlyReservedTransformationMethods() {
        val bytes = ByteArray(0x3000)
        val speciesCount = 10
        val table = 0x1000
        val stride = 10 * 8
        putThumbLiteralReferences(
            bytes, 0x80, 0x100, table, table + speciesCount * stride,
        )
        listOf(0xFFFF, 0xFFFE, 0xFFFD).forEachIndexed { slot, method ->
            val edge = table + 3 * stride + slot * 8
            putU16(bytes, edge, method)
            putU16(bytes, edge + 2, 100 + slot)
            putU16(bytes, edge + 4, 4 + slot)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(stride, result.recordSize)
        assertEquals(8, result.elementSize)
        assertEquals(speciesCount, result.validRecords)
    }

    @Test
    fun prefersEightByteNineSlotEvolutionRecordsOverSixByteTwelveSlotStrideAlias() {
        val bytes = ByteArray(0x2000)
        val speciesCount = 10
        val table = 0x400
        val stride = 9 * 8
        putThumbLiteralReferences(
            bytes, 0x80, 0x100, table, table + speciesCount * stride,
        )
        repeat(4) { index ->
            val species = 2 + index * 2
            val edge = table + species * stride + 8
            putU16(bytes, edge, 4)
            putU16(bytes, edge + 2, 20 + species)
            putU16(bytes, edge + 4, species + 1)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(stride, result.recordSize)
        assertEquals(8, result.elementSize)
    }

    @Test
    fun preservesInheritedOfficialFiveByEightEvolutionLayout() {
        val bytes = ByteArray(0x1000)
        val table = 0x200
        val stride = 5 * 8
        putU16(bytes, table + stride, 4)
        putU16(bytes, table + stride + 2, 16)
        putU16(bytes, table + stride + 4, 2)

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = 4,
            inherited = TableLayout(table, 4, stride, elementSize = 8),
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(stride, result.recordSize)
        assertEquals(8, result.elementSize)
    }

    @Test
    fun prefersCompleteReferencedEvolutionTableOverPartialInheritedLayout() {
        val bytes = ByteArray(0x3000)
        val speciesCount = 10
        val inheritedTable = 0x400
        val inheritedStride = 5 * 8
        putU16(bytes, inheritedTable + inheritedStride, 4)
        putU16(bytes, inheritedTable + inheritedStride + 2, 16)
        putU16(bytes, inheritedTable + inheritedStride + 4, 2)
        repeat(3) { index -> putU16(bytes, inheritedTable + (7 + index) * inheritedStride, 1) }

        val referencedTable = 0x1000
        val referencedStride = 9 * 8
        putThumbLiteralReferences(
            bytes, 0x80, 0x100, referencedTable,
            referencedTable + speciesCount * referencedStride,
        )
        listOf(2 to 3, 5 to 6).forEach { (species, target) ->
            val edge = referencedTable + species * referencedStride + 8
            putU16(bytes, edge, 4)
            putU16(bytes, edge + 2, 16 + species)
            putU16(bytes, edge + 4, target)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount,
            inherited = TableLayout(inheritedTable, speciesCount, inheritedStride, elementSize = 8),
        )

        assertTrue(result.compatible)
        assertEquals(referencedTable, result.offset)
        assertEquals(speciesCount, result.validRecords)
    }

    @Test
    fun resolvesEvolutionTableWhoseReferencedBoundaryIsRomEof() {
        val speciesCount = 10
        val table = 0x1000
        val stride = 9 * 8
        val bytes = ByteArray(table + speciesCount * stride)
        putThumbLiteralReferences(bytes, 0x80, 0x100, table, bytes.size)
        listOf(2 to 3, 5 to 6).forEach { (species, target) ->
            val edge = table + species * stride + 8
            putU16(bytes, edge, 4)
            putU16(bytes, edge + 2, 16 + species)
            putU16(bytes, edge + 4, target)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertTrue(result.toString(), result.compatible)
        assertEquals(table, result.offset)
        assertEquals(stride, result.recordSize)
    }

    @Test
    fun rejectsEquallyStrongReferencedEvolutionRootsAsAmbiguous() {
        val bytes = ByteArray(0x3000)
        val speciesCount = 10
        val stride = 9 * 8
        listOf(0x400, 0x1000).forEachIndexed { index, table ->
            putThumbLiteralReferences(
                bytes,
                instructionOffset = 0x80 + index * 4,
                literalOffset = 0x100 + index * 8,
                firstTarget = table,
                secondTarget = table + speciesCount * stride,
            )
            listOf(2 to 3, 5 to 6).forEach { (species, target) ->
                val edge = table + species * stride + 8
                putU16(bytes, edge, 4)
                putU16(bytes, edge + 2, 16 + species)
                putU16(bytes, edge + 4, target)
            }
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.any { "conflicting" in it })
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun ignoresPlainPointerDataWhenSelectingCompiledEvolutionRootAndBoundaryReferences() {
        val bytes = ByteArray(0x3000)
        val speciesCount = 10
        val stride = 9 * 8
        val pointerShapedTable = 0x400
        val compiledTable = 0x1000
        putU32(bytes, 0x100, 0x08000000 + pointerShapedTable)
        putU32(bytes, 0x104, 0x08000000 + pointerShapedTable + speciesCount * stride)
        putThumbLiteralReferences(
            bytes, instructionOffset = 0x180, literalOffset = 0x200,
            firstTarget = compiledTable, secondTarget = compiledTable + speciesCount * stride,
        )
        listOf(pointerShapedTable, compiledTable).forEach { table ->
            listOf(2 to 3, 5 to 6).forEach { (species, target) ->
                val edge = table + species * stride + 8
                putU16(bytes, edge, 4)
                putU16(bytes, edge + 2, 16 + species)
                putU16(bytes, edge + 4, target)
            }
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(compiledTable, result.offset)
    }

    @Test
    fun recognizesThumbEvolutionLiteralReferencesAcrossTheArchitecturalRange() {
        val bytes = ByteArray(0x3000)
        val speciesCount = 10
        val table = 0x1000
        val stride = 9 * 8
        putThumbLiteralReferences(
            bytes, instructionOffset = 0x80, literalOffset = 0x400,
            firstTarget = table, secondTarget = table + speciesCount * stride,
        )
        listOf(2 to 3, 5 to 6).forEach { (species, target) ->
            val edge = table + species * stride + 8
            putU16(bytes, edge, 4)
            putU16(bytes, edge + 2, 16 + species)
            putU16(bytes, edge + 4, target)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
    }

    @Test
    fun returnsReviewEvidenceWhenReferencedEvolutionCandidateBudgetIsExceeded() {
        val bytes = ByteArray(0x60000)
        val speciesCount = 3
        val firstTable = 0x4000
        repeat(300) { index ->
            val table = firstTable + index * 0x400
            val reference = 0x1000 + index * 12
            putThumbLiteralReferences(
                bytes, reference, reference + 4, table, table + speciesCount * 6,
            )
            putU16(bytes, table + 6, 4)
            putU16(bytes, table + 8, 16)
            putU16(bytes, table + 10, 2)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.any { "candidate budget exceeded (256)" in it })
    }

    @Test
    fun returnsReviewEvidenceRatherThanDiscardingManyStructurallyCleanEmptyExtents() {
        val bytes = ByteArray(0x20000)
        val speciesCount = 10
        val firstDecoy = 0x4000
        repeat(300) { index ->
            val table = firstDecoy + index * 0x80
            val reference = 0x1000 + index * 12
            putThumbLiteralReferences(
                bytes, reference, reference + 4, table, table + speciesCount * 6,
            )
        }
        val realTable = 0x18000
        val stride = 9 * 8
        putThumbLiteralReferences(
            bytes, 0x2000, 0x2004, realTable, realTable + speciesCount * stride,
        )
        listOf(2 to 3, 5 to 6).forEach { (species, target) ->
            val edge = realTable + species * stride + 8
            putU16(bytes, edge, 4)
            putU16(bytes, edge + 2, 16 + species)
            putU16(bytes, edge + 4, target)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.any { "budget exceeded" in it })
    }

    @Test
    fun resolvesLargeSparseEvolutionTableWhenTheOnlyEdgeFallsBetweenUniformSamples() {
        val speciesCount = 100
        val table = 0x1000
        val stride = 9 * 8
        val bytes = ByteArray(0x4000)
        putThumbLiteralReferences(
            bytes, 0x80, 0x100, table, table + speciesCount * stride,
        )
        val soleEdge = table + 19 * 8
        putU16(bytes, soleEdge, 4)
        putU16(bytes, soleEdge + 2, 16)
        putU16(bytes, soleEdge + 4, 3)

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(8, result.elementSize)
    }

    @Test
    fun returnsReviewEvidenceWhenInactiveEvolutionPrefilterShapeBudgetIsExceeded() {
        val bytes = ByteArray(0x450000)
        val speciesCount = 3
        val firstTable = 0x20000
        repeat(4_200) { index ->
            val table = firstTable + index * 0x400
            val reference = 0x1000 + index * 12
            putThumbLiteralReferences(
                bytes, reference, reference + 4, table, table + speciesCount * 6,
            )
            putU16(bytes, table + 6, 999)
            putU16(bytes, table + 12, 999)
        }

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes), speciesCount = speciesCount, inherited = null,
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.any { "prefilter shape budget exceeded (4096)" in it })
    }

    @Test
    fun rejectsEvolutionExtentOverflowWithoutReadingTheRom() {
        val result = DatasetResolvers.gen3Evolutions(
            RomImage(ByteArray(64)), speciesCount = Int.MAX_VALUE, inherited = null,
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun compiledReferencedEvolutionRootBeatsCompleteUnreferencedInheritedRoot() {
        val bytes = ByteArray(0x5000)
        val speciesCount = 10
        val inheritedTable = 0x400
        val compiledTable = 0x1800
        val stride = 5 * 8
        repeat(speciesCount) { species ->
            if (species > 0) {
                val inheritedRecord = inheritedTable + species * stride
                putU16(bytes, inheritedRecord, 4)
                putU16(bytes, inheritedRecord + 2, species + 1)
                putU16(bytes, inheritedRecord + 4, if (species + 1 < speciesCount) species + 1 else 1)

                val compiledRecord = compiledTable + species * stride
                putU16(bytes, compiledRecord, 4)
                putU16(bytes, compiledRecord + 2, species + 10)
                putU16(bytes, compiledRecord + 4, if (species + 1 < speciesCount) species + 1 else 1)
            }
        }
        putThumbLiteralReferences(
            bytes,
            0x80,
            0x100,
            compiledTable,
            compiledTable + speciesCount * stride,
        )

        val result = DatasetResolvers.gen3Evolutions(
            RomImage(bytes),
            speciesCount = speciesCount,
            inherited = TableLayout(inheritedTable, speciesCount, stride, elementSize = 8),
        )

        assertTrue(result.compatible)
        assertEquals(compiledTable, result.offset)
    }

    @Test
    fun rejectsLearnsetExtentOverflowWithoutScanningOrAllocating() {
        val result = DatasetResolvers.gen3Learnsets(
            RomImage(ByteArray(64)), speciesCount = Int.MAX_VALUE, moveCount = 800, inherited = null,
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun resolvesRelocatedPackedLearnsetPointerTable() {
        val bytes = ByteArray(0x1000)
        // A valid pointer may legally target the final ROM byte, but it cannot be dereferenced as
        // a u16 learnset marker. It must not terminate the scan before the real table is reached.
        putU32(bytes, 0, 0x08000FFF)
        repeat(3) { index ->
            val target = if (index == 0) 0x800 else 0x800 + (index - 1) * 0x20
            putU32(bytes, 0x200 + index * 4, 0x08000000 + target)
            putU16(bytes, target, (1 shl 9) or (10 + index))
            putU16(bytes, target + 2, 0xFFFF)
        }
        // Species NONE conventionally reuses the first real species learnset.
        putU32(bytes, 0x204, 0x08000800)
        putThumbLiteralReferences(bytes, 0x80, 0x100, 0x200, 0x200)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 50, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
    }

    @Test
    fun sameRootAllZeroPackedAliasYieldsToPositiveLevelWideAbi() {
        val bytes = ByteArray(0x1800)
        val table = 0x400
        val speciesCount = 3
        repeat(speciesCount) { species ->
            val target = if (species < 2) 0x1000 else 0x1040
            putU32(bytes, table + species * 4, 0x08000000 + target)
            listOf(33, 1, 45, 3, 73, 7).forEachIndexed { index, word ->
                putU16(bytes, target + index * 2, word)
            }
            putU16(bytes, target + 12, 0xFFFF)
        }

        val result = DatasetResolvers.gen3LearnsetResolution(
            RomImage(bytes),
            speciesCount = speciesCount,
            moveCount = 755,
            inherited = null,
            referenceIndex = GbaCompiledReferenceIndex(mapOf(table to 12)),
        )

        assertTrue(result.evidence.compatible)
        assertFalse(result.evidence.ambiguous)
        assertEquals(1, result.tables.size)
        assertEquals(table, result.tables.single().table.offset)
        assertEquals(TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16, result.tables.single().table.format)
        assertEquals(4, result.tables.single().table.elementSize)
    }

    @Test
    fun sameRootPackedViewWithANullDomainRowRemainsAmbiguous() {
        val bytes = ByteArray(0x2000)
        val table = 0x400
        val speciesCount = 10
        repeat(speciesCount - 1) { species ->
            val target = 0x1000 + species * 0x20
            putU32(bytes, table + species * 4, 0x08000000 + target)
            listOf(33, 1, 45, 3, 73, 7).forEachIndexed { index, word ->
                putU16(bytes, target + index * 2, word)
            }
            putU16(bytes, target + 12, 0xFFFF)
        }

        assertFalse(
            DatasetResolvers.hasOnlyZeroPackedLevels(
                RomImage(bytes),
                pointerTableOffset = table,
                speciesCount = speciesCount,
                moveCount = 755,
            ),
        )
    }

    @Test
    fun resolvesCompiledReferencedLevelByteMoveHalfwordLearnsetTable() {
        val bytes = ByteArray(0x3000)
        val table = 0x400
        val count = 10
        repeat(count) { species ->
            val target = 0x1800 + species * 0x20
            putU32(bytes, table + species * 4, 0x08000000 + target)
            if (species > 0) {
                bytes[target] = 1
                putU16(bytes, target + 1, 700 + species)
                bytes[target + 3] = (5 + species).toByte()
                putU16(bytes, target + 4, 600 + species)
                bytes[target + 6] = 0xFE.toByte()
            } else {
                bytes[target] = 0xFE.toByte()
            }
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, table, table)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = count, moveCount = 800, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(count, result.validRecords)
        assertEquals(3, result.elementSize)
    }

    @Test
    fun compiledReferencedLevelByteMoveHalfwordRootBeatsCompleteUnreferencedInheritedPackedDecoy() {
        val bytes = ByteArray(0x4000)
        val inheritedTable = 0x400
        val compiledTable = 0x800
        val count = 10
        repeat(count) { species ->
            val inheritedTarget = 0x1800 + species * 0x20
            putU32(bytes, inheritedTable + species * 4, 0x08000000 + inheritedTarget)
            putU16(bytes, inheritedTarget, (1 shl 10) or (10 + species))
            putU16(bytes, inheritedTarget + 2, 0xFFFF)

            val compiledTarget = 0x2800 + species * 0x20
            putU32(bytes, compiledTable + species * 4, 0x08000000 + compiledTarget)
            bytes[compiledTarget] = 1
            putU16(bytes, compiledTarget + 1, 700 + species)
            bytes[compiledTarget + 3] = 0xFE.toByte()
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, compiledTable, compiledTable)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes),
            speciesCount = count,
            moveCount = 800,
            inherited = TableLayout(inheritedTable, count, 4, elementSize = 2),
        )

        assertTrue(result.compatible)
        assertEquals(compiledTable, result.offset)
        assertEquals(3, result.elementSize)
    }

    @Test
    fun strongerCompiledPackedRootBeatsAWeakerCompiledLevelMoveRootAcrossEncodings() {
        val bytes = ByteArray(0x5000)
        val packedTable = 0x400
        val levelMoveTable = 0x800
        val count = 10
        repeat(count) { species ->
            val packedTarget = 0x1800 + species * 0x20
            putU32(bytes, packedTable + species * 4, 0x08000000 + packedTarget)
            putU16(bytes, packedTarget, (1 shl 10) or (10 + species))
            putU16(bytes, packedTarget + 2, 0xFFFF)

            val levelMoveTarget = 0x3000 + species * 0x20
            putU32(bytes, levelMoveTable + species * 4, 0x08000000 + levelMoveTarget)
            bytes[levelMoveTarget] = 1
            putU16(bytes, levelMoveTarget + 1, 700 + species)
            bytes[levelMoveTarget + 3] = 0xFE.toByte()
        }
        putU32(bytes, packedTable, 0x08000000 + 0x1820)
        putThumbLiteralReferences(bytes, 0x80, 0x100, packedTable, packedTable)
        putThumbLiteralReferences(bytes, 0x90, 0x108, levelMoveTable, 0x1200)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = count, moveCount = 800, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(packedTable, result.offset)
        assertEquals(2, result.elementSize)
    }

    @Test
    fun compiledReferencedPackedRootBeatsCompleteUnreferencedInheritedPackedDecoy() {
        val bytes = ByteArray(0x4000)
        val inheritedTable = 0x400
        val compiledTable = 0x800
        val count = 10
        listOf(inheritedTable to 0x1800, compiledTable to 0x2800).forEach { (table, data) ->
            repeat(count) { species ->
                val target = data + species * 0x20
                putU32(bytes, table + species * 4, 0x08000000 + target)
                putU16(bytes, target, (1 shl 10) or (10 + species))
                putU16(bytes, target + 2, 0xFFFF)
            }
            putU32(bytes, table, 0x08000000 + data + 0x20)
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, compiledTable, compiledTable)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes),
            speciesCount = count,
            moveCount = 800,
            inherited = TableLayout(inheritedTable, count, 4, elementSize = 2),
        )

        assertTrue(result.compatible)
        assertEquals(compiledTable, result.offset)
        assertEquals(2, result.elementSize)
    }

    @Test
    fun equallyReferencedIndependentInheritedAndCompiledPackedLearnsetRootsAreAmbiguous() {
        val bytes = ByteArray(0x4000)
        val inheritedTable = 0x400
        val compiledTable = 0x800
        val count = 10
        listOf(inheritedTable to 0x1800, compiledTable to 0x2800).forEach { (table, data) ->
            repeat(count) { species ->
                val target = data + species * 0x20
                putU32(bytes, table + species * 4, 0x08000000 + target)
                putU16(bytes, target, (1 shl 10) or (10 + species))
                putU16(bytes, target + 2, 0xFFFF)
            }
            putU32(bytes, table, 0x08000000 + data + 0x20)
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, inheritedTable, inheritedTable)
        putThumbLiteralReferences(bytes, 0x90, 0x108, compiledTable, compiledTable)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes),
            speciesCount = count,
            moveCount = 800,
            inherited = TableLayout(inheritedTable, count, 4, elementSize = 2),
        )

        assertFalse(result.compatible)
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "conflicting" in it })
    }

    @Test
    fun rejectsAdjacentEqualRootsWithoutCompiledSelectorEvidence() {
        val bytes = ByteArray(0x4000)
        val count = 10
        val primaryTable = 0x400
        val alternateTable = primaryTable + count * 4
        listOf(primaryTable to 0x1800, alternateTable to 0x2800).forEachIndexed { ruleset, (table, data) ->
            repeat(count) { species ->
                val target = data + species * 0x20
                putU32(bytes, table + species * 4, 0x08000000 + target)
                putU16(bytes, target, ((1 + ruleset) shl 10) or (10 + species))
                putU16(bytes, target + 2, 0xFFFF)
            }
            putU32(bytes, table, 0x08000000 + data + 0x20)
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, primaryTable, primaryTable)
        putThumbLiteralReferences(bytes, 0x90, 0x108, alternateTable, alternateTable)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = count, moveCount = 800, inherited = null,
        )

        assertFalse(result.compatible)
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun keepsReferencedInheritedAndAdjacentPackedRootsForReviewWithoutSelectorEvidence() {
        val bytes = ByteArray(0x4000)
        val count = 10
        val firstTable = 0x400
        val inheritedTable = firstTable + count * 4
        listOf(firstTable to 0x1800, inheritedTable to 0x2800).forEachIndexed { ruleset, (table, data) ->
            repeat(count) { species ->
                val target = data + species * 0x20
                putU32(bytes, table + species * 4, 0x08000000 + target)
                putU16(bytes, target, ((1 + ruleset) shl 10) or (10 + species))
                putU16(bytes, target + 2, 0xFFFF)
            }
            putU32(bytes, table, 0x08000000 + data + 0x20)
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, firstTable, firstTable)
        putThumbLiteralReferences(bytes, 0x90, 0x108, inheritedTable, inheritedTable)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes),
            speciesCount = count,
            moveCount = 800,
            inherited = TableLayout(inheritedTable, count, 4, elementSize = 2),
        )

        assertFalse(result.compatible)
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun ignoresAnUnreferencedFullLevelByteMoveHalfwordLearnsetDecoy() {
        val bytes = ByteArray(0x3000)
        val expectedTable = 0x400
        val decoyTable = 0x800
        val count = 10
        listOf(expectedTable to 0x1800, decoyTable to 0x2200).forEach { (table, data) ->
            repeat(count) { species ->
                val target = data + species * 0x20
                putU32(bytes, table + species * 4, 0x08000000 + target)
                bytes[target] = 1
                putU16(bytes, target + 1, 700 + species)
                bytes[target + 3] = 0xFE.toByte()
            }
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, expectedTable, expectedTable)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = count, moveCount = 800, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(expectedTable, result.offset)
        assertEquals(3, result.elementSize)
    }

    @Test
    fun rejectsEquallyStrongCompiledLevelByteMoveHalfwordLearnsetRootsAsAmbiguous() {
        val bytes = ByteArray(0x3000)
        val firstTable = 0x400
        val secondTable = 0x800
        val count = 10
        listOf(firstTable to 0x1800, secondTable to 0x2200).forEach { (table, data) ->
            repeat(count) { species ->
                val target = data + species * 0x20
                putU32(bytes, table + species * 4, 0x08000000 + target)
                bytes[target] = 1
                putU16(bytes, target + 1, 700 + species)
                bytes[target + 3] = 0xFE.toByte()
            }
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, firstTable, firstTable)
        putThumbLiteralReferences(bytes, 0x90, 0x108, secondTable, secondTable)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = count, moveCount = 800, inherited = null,
        )

        assertFalse(result.compatible)
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "conflicting" in it })
    }

    @Test
    fun rejectsALevelByteMoveHalfwordListWithoutATerminatorBeforeTheNextPointer() {
        val bytes = ByteArray(0x2400)
        val table = 0x400
        val count = 10
        val data = 0x1800
        repeat(count) { species ->
            val target = if (species == 0) data else data + 3 + (species - 1) * 0x20
            putU32(bytes, table + species * 4, 0x08000000 + target)
            if (species == 0) {
                bytes[target] = 1
                putU16(bytes, target + 1, 700)
            } else {
                bytes[target] = 0xFE.toByte()
            }
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, table, table)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = count, moveCount = 800, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(count - 1, result.validRecords)
        assertEquals(3, result.elementSize)
    }

    @Test
    fun returnsReviewEvidenceWhenCompiledLevelByteMoveHalfwordCandidateBudgetIsExceeded() {
        val bytes = ByteArray(0x10000)
        val count = 3
        val sharedEmptyLearnset = 0xF000
        bytes[sharedEmptyLearnset] = 0xFE.toByte()
        repeat(257) { index ->
            val table = 0x4000 + index * 0x20
            repeat(count) { species ->
                putU32(bytes, table + species * 4, 0x08000000 + sharedEmptyLearnset)
            }
            val instruction = 0x100 + index * 12
            putThumbLiteralReferences(bytes, instruction, instruction + 4, table, table)
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = count, moveCount = 800, inherited = null,
        )

        assertFalse(result.compatible)
        assertFalse(result.ambiguous)
        assertTrue(result.reviewRecommended)
        assertTrue(result.reasons.any { "candidate budget exceeded" in it })
    }

    @Test
    fun completeRelocatedPackedLearnsetTableSupersedesCompatibleInheritedInteriorOffset() {
        val bytes = ByteArray(0x1000)
        val table = 0x200
        val speciesCount = 10
        repeat(speciesCount) { index ->
            val target = if (index == 0) 0x800 else 0x800 + (index - 1) * 0x20
            putU32(bytes, table + index * 4, 0x08000000 + target)
            putU16(bytes, target, (1 shl 9) or (10 + index))
            putU16(bytes, target + 2, 0xFFFF)
        }
        // Species NONE reuses species 1, making the actual table root structurally discoverable.
        putU32(bytes, table + 4, 0x08000800)
        putThumbLiteralReferences(bytes, 0x80, 0x100, table, table)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes),
            speciesCount = speciesCount,
            moveCount = 50,
            inherited = TableLayout(table + 4, speciesCount, 4),
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(speciesCount, result.validRecords)
    }

    @Test
    fun deterministicallySelectsTheLowestCompletePackedRulesetWithoutAnInheritedOffset() {
        val bytes = ByteArray(0x1400)
        listOf(0x200 to 0x800, 0x400 to 0xA00).forEach { (table, learnsets) ->
            repeat(3) { index ->
                val target = learnsets + index * 0x20
                putU32(bytes, table + index * 4, 0x08000000 + target)
                putU16(bytes, target, (1 shl 9) or (10 + index))
                putU16(bytes, target + 2, 0xFFFF)
            }
            putU32(bytes, table + 4, 0x08000000 + learnsets)
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, 0x200, 0x200)
        putThumbLiteralReferences(bytes, 0x90, 0x108, 0x400, 0x600)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 50, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
    }

    @Test
    fun keepsNineBitPackedLearnsetsWhenMoveCatalogContainsExactly512Moves() {
        val bytes = ByteArray(0x1000)
        repeat(3) { index ->
            val target = 0x800 + index * 0x20
            putU32(bytes, 0x200 + index * 4, 0x08000000 + target)
            if (index == 0) {
                putU16(bytes, target, 0xFFFF)
            } else {
                putU16(bytes, target, (1 shl 9) or (489 + index))
                putU16(bytes, target + 2, 0xFFFF)
            }
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, 0x200, 0x200)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 512, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(2, result.elementSize)
    }

    @Test
    fun resolvesCfruExpandedLearnsetPointerTable() {
        val bytes = ByteArray(0x1000)
        repeat(3) { index ->
            val target = 0x800 + index * 0x20
            putU32(bytes, 0x200 + index * 4, 0x08000000 + target)
            if (index == 0) {
                putU16(bytes, target, 0)
                bytes[target + 2] = 0xFF.toByte()
            } else {
                putU16(bytes, target, 600 + index)
                bytes[target + 2] = (10 + index).toByte()
                putU16(bytes, target + 3, 0)
                bytes[target + 5] = 0xFF.toByte()
            }
        }
        putThumbLiteralReferences(bytes, 0x80, 0x100, 0x200, 0x200)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 800, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(3, result.elementSize)
    }

    @Test
    fun resolvesReferencedExpandedLearnsetTableWithoutAnEmptySpeciesZeroRecord() {
        val bytes = ByteArray(0x1000)
        putThumbLiteralReferences(bytes, 0x80, 0x100, 0x200, 0x200)
        repeat(3) { index ->
            val target = 0x800 + index * 0x20
            putU32(bytes, 0x200 + index * 4, 0x08000000 + target)
            putU16(bytes, target, 600 + index)
            bytes[target + 2] = (1 + index * 6).toByte()
            putU16(bytes, target + 3, 0)
            bytes[target + 5] = 0xFF.toByte()
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 3, moveCount = 800, inherited = null,
        )

        assertTrue(result.compatible)
        assertEquals(0x200, result.offset)
        assertEquals(3, result.elementSize)
    }

    @Test
    fun resolvesReferencedWideLearnsetsWithSparseNullAndNonemptyFirstEntry() {
        val bytes = ByteArray(0x2400)
        val weaklyReferencedDecoy = 0x200
        val expectedTable = 0x400
        val decoyData = 0x1000
        val expectedData = 0x1800

        repeat(10) { index ->
            if (index < 3) {
                val decoyTarget = decoyData + index * 0x20
                putU32(bytes, weaklyReferencedDecoy + index * 4, 0x08000000 + decoyTarget)
                putU16(bytes, decoyTarget, 600 + index)
                putU16(bytes, decoyTarget + 2, index)
                putU16(bytes, decoyTarget + 4, 0xFFFF)
            }

            if (index == 6) return@repeat
            // Species zero may reuse the first real species instead of pointing at an empty list.
            val target = expectedData + (if (index == 0) 1 else index) * 0x20
            putU32(bytes, expectedTable + index * 4, 0x08000000 + target)
            putU16(bytes, target, 700 + index)
            putU16(bytes, target + 2, if (index == 0) 0 else index)
            putU16(bytes, target + 4, 0xFFFF)
        }
        // Compiled code commonly exposes the table through multiple literal-pool references.
        putU32(bytes, 0x100, 0x08000000 + expectedTable)
        putU32(bytes, 0x104, 0x08000000 + expectedTable)
        // Broad coverage selects the real table over a tiny pure structural decoy.
        putU32(bytes, 0x108, 0x08000000 + weaklyReferencedDecoy)

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 10, moveCount = 800, inherited = null,
            referenceIndex = GbaCompiledReferenceIndex(
                mapOf(expectedTable to 2, weaklyReferencedDecoy to 1),
            ),
        )

        assertTrue(result.compatible)
        assertEquals(expectedTable, result.offset)
        assertEquals(9, result.validRecords)
        assertEquals(4, result.elementSize)
    }

    @Test
    fun prefersPopulatedImperfectWideRootOverTinyPureReferencedDecoy() {
        val bytes = ByteArray(0xC000)
        val speciesCount = 1300
        val tinyPureTable = 0x1000
        val populatedTable = 0x3000
        val tinyPureData = 0x6000
        val populatedData = 0x7000
        val malformedData = 0xA000
        putU32(bytes, 0x100, 0x08000000 + tinyPureTable)
        putU32(bytes, 0x104, 0x08000000 + populatedTable)

        repeat(3) { index ->
            val target = tinyPureData + index * 8
            putU32(bytes, tinyPureTable + index * 4, 0x08000000 + target)
            putU16(bytes, target, 700 + index)
            putU16(bytes, target + 2, index)
            putU16(bytes, target + 4, 0xFFFF)
        }
        repeat(1290) { index ->
            val target = populatedData + index * 8
            putU32(bytes, populatedTable + index * 4, 0x08000000 + target)
            putU16(bytes, target, 700 + index % 50)
            putU16(bytes, target + 2, index % 101)
            putU16(bytes, target + 4, 0xFFFF)
        }
        putU16(bytes, malformedData, 801)
        putU16(bytes, malformedData + 2, 1)
        putU16(bytes, malformedData + 4, 0xFFFF)
        repeat(10) { index ->
            putU32(bytes, populatedTable + (1290 + index) * 4, 0x08000000 + malformedData)
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = speciesCount, moveCount = 800, inherited = null,
            referenceIndex = GbaCompiledReferenceIndex(mapOf(tinyPureTable to 1, populatedTable to 1)),
        )

        assertTrue(result.compatible)
        assertEquals(populatedTable, result.offset)
        assertEquals(1290, result.validRecords)
        assertEquals(1290.0 / speciesCount, result.confidence, 0.0)
        assertEquals(4, result.elementSize)
    }

    @Test
    fun resolvesUnambiguousSingleReferencedPartialWideLearnsetTableWithoutEarlyEntries() {
        val bytes = ByteArray(0x1800)
        val table = 0x200
        putU32(bytes, 0x100, 0x08000000 + table)
        listOf(4, 6, 8, 9).forEachIndexed { entry, species ->
            val target = 0x800 + entry * 0x20
            putU32(bytes, table + species * 4, 0x08000000 + target)
            putU16(bytes, target, 700 + entry)
            putU16(bytes, target + 2, species)
            putU16(bytes, target + 4, 0xFFFF)
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 10, moveCount = 800, inherited = null,
            referenceIndex = GbaCompiledReferenceIndex(mapOf(table to 1)),
        )

        assertTrue(result.compatible)
        assertEquals(table, result.offset)
        assertEquals(4, result.validRecords)
        assertEquals(0.4, result.confidence, 0.0)
        assertEquals(4, result.elementSize)
    }

    @Test
    fun rejectsEquallyStrongReferencedWideLearnsetRootsAsAmbiguous() {
        val bytes = ByteArray(0x2800)
        val firstTable = 0x200
        val secondTable = 0x400
        putU32(bytes, 0x100, 0x08000000 + firstTable)
        putU32(bytes, 0x104, 0x08000000 + secondTable)
        listOf(firstTable to 0x1000, secondTable to 0x1800).forEach { (table, data) ->
            repeat(4) { index ->
                val target = data + index * 0x20
                putU32(bytes, table + index * 4, 0x08000000 + target)
                putU16(bytes, target, 700 + index)
                putU16(bytes, target + 2, index)
                putU16(bytes, target + 4, 0xFFFF)
            }
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 10, moveCount = 800, inherited = null,
            referenceIndex = GbaCompiledReferenceIndex(mapOf(firstTable to 1, secondTable to 1)),
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.any { "conflicting" in it })
        assertTrue(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun returnsReviewEvidenceWhenPointerRichWideCandidateBudgetIsExceeded() {
        val bytes = ByteArray(0x20000)
        val candidateCount = 300
        val firstTable = 0x2000
        val tableStride = 0x40
        val sharedLearnset = 0x1F000
        putU16(bytes, sharedLearnset, 700)
        putU16(bytes, sharedLearnset + 2, 1)
        putU16(bytes, sharedLearnset + 4, 0xFFFF)
        repeat(candidateCount) { index ->
            val table = firstTable + index * tableStride
            putU32(bytes, index * 4, 0x08000000 + table)
            putU32(bytes, table, 0x08000000 + sharedLearnset)
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 10, moveCount = 800, inherited = null,
            referenceIndex = GbaCompiledReferenceIndex(
                (0 until candidateCount).associate { index -> firstTable + index * tableStride to 1 },
            ),
        )

        assertFalse(result.compatible)
        assertTrue(result.reasons.any { "candidate budget" in it })
        assertTrue(result.reasons.any { "review" in it })
        assertFalse(result.ambiguous)
        assertTrue(result.reviewRecommended)
    }

    @Test
    fun pointerShapedDecoysDoNotExhaustWideCandidateBudgetBeforeRealRoot() {
        val bytes = ByteArray(0x24000)
        val decoyCount = 300
        val firstDecoyTable = 0x2000
        val tableStride = 0x40
        val invalidLearnsetData = 0x1E000
        repeat(decoyCount) { index ->
            val table = firstDecoyTable + index * tableStride
            putU32(bytes, index * 4, 0x08000000 + table)
            putU32(bytes, table, 0x08000000 + invalidLearnsetData)
        }

        val realTable = 0x10000
        putU32(bytes, decoyCount * 4, 0x08000000 + realTable)
        listOf(4, 6, 8, 9).forEachIndexed { entry, species ->
            val target = 0x18000 + entry * 0x20
            putU32(bytes, realTable + species * 4, 0x08000000 + target)
            putU16(bytes, target, 700 + entry)
            putU16(bytes, target + 2, species)
            putU16(bytes, target + 4, 0xFFFF)
        }

        val result = DatasetResolvers.gen3Learnsets(
            RomImage(bytes), speciesCount = 10, moveCount = 800, inherited = null,
            referenceIndex = GbaCompiledReferenceIndex(
                buildMap {
                    repeat(decoyCount) { index -> put(firstDecoyTable + index * tableStride, 1) }
                    put(realTable, 1)
                },
            ),
        )

        assertTrue(result.compatible)
        assertEquals(realTable, result.offset)
        assertEquals(4, result.validRecords)
    }

    private fun putGbaText(bytes: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                ' ' -> 0
                in 'A'..'Z' -> 0xBB + (character - 'A')
                else -> error("unsupported fixture character $character")
            }.toByte()
        }
        bytes[offset + value.length] = 0xFF.toByte()
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putDescriptionTable(
        bytes: ByteArray,
        offset: Int,
        count: Int,
        textOffset: Int,
        firstCategory: String = "UNKNOWN",
    ) {
        repeat(count) { index ->
            val base = offset + index * 36
            putGbaText(bytes, base, if (index == 0) firstCategory else if (index == 1) "SEED" else "ENTRY")
            putU16(bytes, base + 12, if (index == 0) 0 else 7)
            putU16(bytes, base + 14, if (index == 0) 0 else 69)
            putU32(bytes, base + 16, 0x08000000 + textOffset + index * 0x20)
            putU32(bytes, base + 20, 0x08000000 + textOffset + index * 0x20)
            putGbaText(bytes, textOffset + index * 0x20, "POKEMON TEXT")
        }
    }

    private fun putDescriptionTable32(
        bytes: ByteArray,
        offset: Int,
        count: Int,
        textOffset: Int,
    ) {
        repeat(count) { index ->
            val base = offset + index * 32
            putGbaText(bytes, base, if (index == 0) "UNKNOWN" else if (index == 1) "SEED" else "ENTRY")
            putU16(bytes, base + 12, if (index == 0) 0 else 7)
            putU16(bytes, base + 14, if (index == 0) 0 else 69)
            putU32(bytes, base + 16, 0x08000000 + textOffset + index * 0x20)
            putGbaText(bytes, textOffset + index * 0x20, "POKEMON TEXT")
        }
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putThumbLiteralReferences(
        bytes: ByteArray,
        instructionOffset: Int,
        literalOffset: Int,
        firstTarget: Int,
        secondTarget: Int,
    ) {
        val firstPc = (instructionOffset + 4) and -4
        val secondPc = (instructionOffset + 6) and -4
        putU16(bytes, instructionOffset, 0x4800 or ((literalOffset - firstPc) / 4))
        putU16(bytes, instructionOffset + 2, 0x4800 or ((literalOffset + 4 - secondPc) / 4))
        putU32(bytes, literalOffset, 0x08000000 + firstTarget)
        putU32(bytes, literalOffset + 4, 0x08000000 + secondTarget)
    }

    private fun putVerifiedDescriptionConsumer36(
        bytes: ByteArray,
        instructionOffset: Int,
        literalOffset: Int,
        target: Int,
    ) {
        putU16(bytes, instructionOffset - 6, 0x00C8) // lsls r0, r1, #3
        putU16(bytes, instructionOffset - 4, 0x1840) // adds r0, r0, r1
        putU16(bytes, instructionOffset - 2, 0x0080) // lsls r0, r0, #2
        val pc = (instructionOffset + 4) and -4
        putU16(bytes, instructionOffset, 0x4900 or ((literalOffset - pc) / 4))
        putU16(bytes, instructionOffset + 2, 0x1840) // adds r0, r0, r1
        putU32(bytes, literalOffset, 0x08000000 + target)
    }

    private fun putVerifiedDescriptionConsumer32(
        bytes: ByteArray,
        instructionOffset: Int,
        literalOffset: Int,
        target: Int,
    ) {
        val pc = (instructionOffset + 4) and -4
        putU16(bytes, instructionOffset, 0x4800 or ((literalOffset - pc) / 4))
        putU16(bytes, instructionOffset + 2, 0x0149) // lsls r1, r1, #5
        putU16(bytes, instructionOffset + 4, 0x1809) // adds r1, r1, r0
        putU16(bytes, instructionOffset + 6, 0x4770) // bx lr
        putU32(bytes, literalOffset, 0x08000000 + target)
    }
}
