package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameRowOutcome
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.ResolvedAbilityNameLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.family.abilityMechanicsCoverage
import com.enrpau.dualscreendex.parser.family.abilityMechanicsDomain
import com.enrpau.dualscreendex.parser.family.reconcileAbilityEvidence
import com.enrpau.dualscreendex.parser.family.validatedDirectAbilityIds
import com.enrpau.dualscreendex.parser.family.validatedAbilityCoverageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyParsersAbilityResolutionTest {
    @Test
    fun provesAUniqueFixedAbilityNameStrideFromEveryCompiledRootConsumer() {
        val root = 0x400
        val bytes = ByteArray(0x600)
        listOf(0x104, 0x124).forEach { rootLoad ->
            writeU16(bytes, rootLoad - 4, 0x200D) // mov r0, #13
            writeU16(bytes, rootLoad - 2, 0x4341) // mul r1, r0
            val literal = rootLoad + 0x3C
            val pc = (rootLoad + 4) and -4
            writeU16(bytes, rootLoad, 0x4800 or ((literal - pc) / 4)) // ldr r0, =root
            writeU16(bytes, rootLoad + 2, 0x180C) // add r4, r1, r0
            writeU32(bytes, literal, 0x08000000 + root)
        }
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "ABILITY TEST"))

        assertEquals(13, compiledAbilityNameStride(session, root))
        assertEquals(mapOf(root to 13), compiledAbilityNameCandidates(session))

        writeU16(bytes, 0x120, 0x200C)
        val inconsistent = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "ABILITY TEST"))
        assertEquals(null, compiledAbilityNameStride(inconsistent, root))
        assertTrue(compiledAbilityNameCandidates(inconsistent).isEmpty())
    }

    @Test
    fun abilityCoveragePromotesOnlyAnExactlyValidatedPhysicalBaseStatExtent() {
        val bytes = ByteArray(760 * 28)
        repeat(760) { row ->
            val offset = row * 28
            bytes[offset] = 45
            bytes[offset + 1] = 49
            bytes[offset + 2] = 49
            bytes[offset + 3] = 45
            bytes[offset + 4] = 65
            bytes[offset + 5] = 65
            bytes[offset + 6] = 12
            bytes[offset + 7] = 3
            bytes[offset + 19] = 3
            bytes[offset + 20] = 1
            bytes[offset + 21] = 1
            bytes[offset + 25] = 0
        }
        bytes[700 * 28 + 22] = 203.toByte()
        bytes[759 * 28 + 22] = 207.toByte()
        val semantic = TableLayout(0, 412, 28)
        val physical = TableLayout(0, 760, 28)

        assertEquals(physical, validatedAbilityCoverageLayout(RomImage(bytes), physical, semantic))
        assertEquals(
            semantic,
            validatedAbilityCoverageLayout(RomImage(bytes.copyOf(bytes.size - 1)), physical, semantic),
        )
    }

    @Test
    fun abilityTargetCoverageUsesTheFullValidatedBaseStatExtentBeyondTheNavigablePrefix() {
        val table = TableLayout(0, 760, 28)
        val bytes = ByteArray(table.count * table.recordSize)
        listOf(411 to 19, 700 to 203, 759 to 207).forEach { (row, abilityId) ->
            val offset = row * table.recordSize
            bytes[offset + 19] = 0
            bytes[offset + 20] = 1
            bytes[offset + 21] = 1
            bytes[offset + 22] = abilityId.toByte()
            bytes[offset + 25] = 0
        }

        assertEquals(
            setOf(19, 203, 207),
            validatedDirectAbilityIds(RomImage(bytes), table),
        )
    }

    @Test
    fun abilityMechanicsDomainRetainsMalformedActiveAndNumericOnlyIds() {
        val names = ResolvedAbilityNameLayout(
            table = AbilityNameTableLayout(0, 3, 13),
            rows = listOf(
                AbilityNameRowOutcome.StructuralSentinel(0, "-"),
                AbilityNameRowOutcome.Decoded(1, "OVERGROW"),
                AbilityNameRowOutcome.Malformed(2, listOf("invalid text")),
            ),
            baseRowCount = 3,
            aliasLabels = emptyList(),
            unresolvedActiveAbilityIds = setOf(2),
        )

        assertEquals(
            setOf(1, 2, 3),
            abilityMechanicsDomain(names, validatedNumericIds = setOf(0, 2, 3)),
        )
    }

    @Test
    fun abilityMechanicsCoverageExcludesEmptySlotsAndOutOfDomainMechanics() {
        val coverage = abilityMechanicsCoverage(
            activeAbilityIds = setOf(0, 1, 2),
            mechanicIds = listOf(2, 3),
        )

        assertEquals(setOf(1, 2), coverage.expectedIds)
        assertEquals(setOf(2), coverage.coveredIds)
        assertEquals(2, coverage.expectedRecords)
        assertEquals(1, coverage.coveredRecords)
        assertEquals(1, coverage.incompleteRecords)
        assertFalse(coverage.complete)
    }

    @Test
    fun unrelatedPublishedPointerAmbiguityCannotEraseValidatedAbilityEvidence() {
        val ability = evidence(valid = 189, total = 190, width = 13).copy(offset = 0xFC17A0)
        val unrelated = ability.copy(
            compatible = false,
            validRecords = 0,
            totalRecords = 2,
            confidence = 0.0,
            offset = null,
            ambiguous = true,
            reasons = listOf("ambiguous generic published pointer blocks"),
        )

        assertEquals(ability, reconcileAbilityEvidence(ability, unrelated))
    }

    @Test
    fun genericPublishedAmbiguityRemainsTerminalWhenAbilityEvidenceIsIncompatible() {
        val incompatible = evidence(valid = 4, total = 82, width = 13, compatible = false)
        val ambiguous = incompatible.copy(
            validRecords = 0,
            totalRecords = 2,
            confidence = 0.0,
            offset = null,
            ambiguous = true,
            reasons = listOf("ambiguous generic published pointer blocks"),
        )

        assertEquals(ambiguous, reconcileAbilityEvidence(incompatible, ambiguous))
    }

    @Test
    fun publishedAmbiguityExplicitlyTiedToTheAbilityRootStillFailsClosed() {
        val ability = evidence(valid = 189, total = 190, width = 13).copy(offset = 0xFC17A0)
        val sameRoot = ability.copy(
            compatible = false,
            validRecords = 0,
            totalRecords = 2,
            confidence = 0.0,
            ambiguous = true,
            reasons = listOf("ambiguous published ability role at the selected root"),
        )

        assertEquals(sameRoot, reconcileAbilityEvidence(ability, sameRoot))
    }

    @Test
    fun boundsCfruBaseAbilitiesAtThePostCatalogSentinel() {
        val names = buildList {
            add("-------")
            repeat(254) { add("BASE ABILITY ${it + 1}") }
            add("-")
            addAll(listOf("AIR LOCK", "VITAL SPIRIT", "WHITE SMOKE", "PURE POWER"))
        }

        assertEquals(AbilityNameBoundary(255, 4), semanticAbilityNameBoundary(names, maximumDirectAbilityId = 254))
    }

    @Test
    fun doesNotTreatTheLeadingNoneSlotAsTheCatalogBoundary() {
        val names = listOf("-------", "STENCH", "DRIZZLE", "SPEED BOOST")

        assertEquals(null, semanticAbilityNameBoundary(names, maximumDirectAbilityId = 3))
    }

    @Test
    fun keepsPostSeparatorLabelsWhenSpeciesCanReferenceTheSeparatorId() {
        val names = buildList {
            add("-------")
            repeat(254) { add("BASE ABILITY") }
            add("-")
            add("RUNTIME LABEL")
        }

        assertEquals(null, semanticAbilityNameBoundary(names, maximumDirectAbilityId = 255))
    }

    @Test
    fun rejectsCompetingPostCatalogSentinelsAsAmbiguous() {
        val names = buildList {
            add("-------")
            repeat(10) { add("BASE ABILITY") }
            add("-")
            addAll(listOf("FIRST ALIAS", "SECOND ALIAS", "THIRD ALIAS"))
            add("---")
            addAll(listOf("OTHER LABEL", "ANOTHER LABEL", "FINAL LABEL"))
        }

        assertEquals(null, semanticAbilityNameBoundary(names, maximumDirectAbilityId = 10))
    }

    @Test
    fun reportsOrdinaryAbilityCatalogAsAvailable() {
        val result = capabilityEvidence(RomCapability.ABILITIES, evidence(valid = 78, total = 78, width = 13))

        assertEquals(CapabilityStatus.AVAILABLE, result.status)
    }

    @Test
    fun reportsUnmaterializedRuntimeAliasLabelsAsPartial() {
        val result = capabilityEvidence(
            RomCapability.ABILITIES,
            evidence(valid = 255, total = 255, width = 17).copy(
                coveredRecords = 254,
                expectedRecords = 291,
                incompleteRecords = 37,
                reviewRecommended = true,
            ),
        )

        assertEquals(CapabilityStatus.PARTIAL, result.status)
        assertEquals(254, result.coveredRecords)
        assertEquals(291, result.expectedRecords)
        assertEquals(37, result.incompleteRecords)
    }

    @Test
    fun scansDynamicWidthsWhenInheritedViewIsSuperficiallyCompatible() {
        val inherited = evidence(valid = 15, total = 16, width = 13)
        val wide = evidence(valid = 148, total = 149, width = 19)
        var scanned = false

        val selected = selectAbilityNameEvidence(exact = false, inherited = inherited) {
            scanned = true
            listOf(inherited, wide)
        }

        assertTrue(scanned)
        assertEquals(19, selected.recordSize)
        assertEquals(148, selected.validRecords)
        assertEquals(149, selected.totalRecords)
    }

    @Test
    fun preservesExactProfileFastPath() {
        val inherited = evidence(valid = 77, total = 78, width = 13)
        var scanned = false

        val selected = selectAbilityNameEvidence(exact = true, inherited = inherited) {
            scanned = true
            listOf(evidence(valid = 148, total = 149, width = 19))
        }

        assertFalse(scanned)
        assertEquals(inherited, selected)
    }

    @Test
    fun scansAllThreeU16AbilitySlotsInThirtyTwoByteBattleEngineStats() {
        val bytes = ByteArray(96)
        writeU16(bytes, 22, 65)
        writeU16(bytes, 24, 260)
        writeU16(bytes, 26, 145)

        assertEquals(260, maximumDirectAbilityId(RomImage(bytes), TableLayout(0, 1, 32), 1))
    }

    @Test
    fun retainsTwoU8AbilitySlotsInTwentyEightByteOfficialStats() {
        val bytes = ByteArray(28)
        bytes[22] = 7
        bytes[23] = 9

        assertEquals(9, maximumDirectAbilityId(RomImage(bytes), TableLayout(0, 1, 28), 1))
    }

    @Test
    fun rejectsUnsupportedAbilityRecordWidthsFromMaximumIdScan() {
        listOf(24, 26, 30).forEach { recordSize ->
            val bytes = ByteArray(recordSize)
            bytes[22] = 7
            bytes[23] = 9

            assertEquals("recordSize=$recordSize", 0, maximumDirectAbilityId(RomImage(bytes), TableLayout(0, 1, recordSize), 1))
        }
    }

    @Test
    fun rejectsThirtyTwoByteAbilityRecordsWithShortStrideWithoutThrowing() {
        val bytes = ByteArray(56)
        writeU16(bytes, 22, 65)
        writeU16(bytes, 24, 31)
        writeU16(bytes, 26, 145)

        assertEquals(0, maximumDirectAbilityId(RomImage(bytes), TableLayout(0, 2, 32, stride = 24), 2))
    }

    @Test
    fun rejectsMalformedAbilityLayoutsWithoutThrowing() {
        val rom = RomImage(ByteArray(56))
        val malformed = listOf(
            TableLayout(-1, 1, 28),
            TableLayout(57, 1, 28),
            TableLayout(0, -1, 28),
            TableLayout(0, 1, 28, stride = -1),
            TableLayout(0, 3, 28),
        )

        malformed.forEach { table ->
            assertEquals(table.toString(), 0, maximumDirectAbilityId(rom, table, table.count))
        }
    }

    @Test
    fun retainsInheritedEvidenceWhenNoDynamicWidthValidates() {
        val inherited = evidence(valid = 4, total = 78, width = 13, compatible = false)

        val selected = selectAbilityNameEvidence(exact = false, inherited = inherited) {
            listOf(evidence(valid = 2, total = 10, width = 19, compatible = false))
        }

        assertEquals(inherited, selected)
    }

    private fun evidence(
        valid: Int,
        total: Int,
        width: Int,
        compatible: Boolean = true,
    ) = ValidationEvidence(
        compatible = compatible,
        validRecords = valid,
        totalRecords = total,
        confidence = valid.toDouble() / total,
        reasons = emptyList(),
        offset = 0x1000,
        recordSize = width,
    )

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
