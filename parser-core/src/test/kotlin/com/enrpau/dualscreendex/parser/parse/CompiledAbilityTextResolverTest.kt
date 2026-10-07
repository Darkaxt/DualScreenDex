package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameCodec
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameResolver
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameRowOutcome
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableOutcome
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilitySemanticDomain
import com.enrpau.dualscreendex.parser.dataset.abilities.abilitySession
import com.enrpau.dualscreendex.parser.dataset.abilities.putGbaPointer
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.validate.Gen3BaseStatAbilitySlots
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.*
import org.junit.Test

class CompiledAbilityTextResolverTest {
    @Test fun relocatedGetterAndPairedAccessorsOwnNamesDespiteCoherentDecoy() {
        for (relocation in listOf(0, 0x400)) {
            val fixture = CompiledAbilityTextFixture(relocation)
            val session = abilitySession(fixture.bytes, useDefaultReferenceIndex = true)
            assertEquals(14, compiledAbilityNameStride(session, fixture.falseNames.offset.toInt()))
            val decoy = AbilityNameCodec().decode(session, fixture.falseNames,
                AbilitySemanticDomain(setOf(1, 2, 4, 7, 12))) as AbilityNameTableOutcome.Decoded
            assertEquals(13, decoy.resolved.baseRowCount)
            assertTrue(decoy.resolved.unresolvedActiveAbilityIds.isEmpty())
            val outcome = resolve(fixture)
            assertEquals("Resolved", outcome.javaClass.simpleName)
            val binding = property(outcome, "Binding")
            val names = property(binding, "Names")
            val descriptions = property(binding, "Descriptions")
            val slots = property(binding, "SpeciesSlots")
            assertEquals(fixture.names.offset, (property(names, "Offset") as Number).toLong())
            assertEquals(20L, (property(names, "Count") as Number).toLong())
            assertEquals(17, property(names, "NameWidth"))
            assertEquals(fixture.descriptions.offset, (property(descriptions, "Offset") as Number).toLong())
            assertEquals(20L, (property(descriptions, "Count") as Number).toLong())
            assertEquals(36, property(slots, "RecordStride"))
            assertEquals(24, property(slots, "FieldOffset"))
            assertEquals(2, property(slots, "ElementSize"))
            assertEquals(3, property(slots, "SlotCount"))
            assertEquals(listOf(1, 2), readSlots(slots, fixture, 1))
            assertEquals(listOf(4), readSlots(slots, fixture, 2))
            assertEquals(listOf(7, 12), readSlots(slots, fixture, 3))
            assertTrue(readSlots(slots, fixture, fixture.core.count).isEmpty())
            assertTrue(Gen3BaseStatAbilitySlots.read(RomImage(fixture.bytes), fixture.core.offset, 36).isEmpty())
        }
    }

    @Test fun cachedHiddenAndFirstNonzeroFallbacksRetainOnlyTheProvedFields() {
        val fixture = CompiledAbilityTextFixture(cachedFallback = true)
        assertEquals("Resolved", resolve(fixture).javaClass.simpleName)
        assertEquals(3, fixture.getterLoads.size)
        fixture.half(fixture.getterLoads[1], 0x8848)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun oneConflictingConsumerRejectsOtherwiseCompleteOwnership() {
        val fixture = CompiledAbilityTextFixture()
        fixture.addNameConsumer(width = 14)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun twoCompleteOwnedContractsAreAmbiguousAndRespectRootAndCandidateCaps() {
        val fixture = competingFixture()
        assertEquals("Ambiguous", resolve(fixture).javaClass.simpleName)
        for (limits in listOf(ResolutionLimits(maxProbeRootsPerDataset = 1),
            ResolutionLimits(maxCandidatesPerDataset = 1))) {
            assertEquals("BudgetExceeded", invoke(abilitySession(fixture.bytes,
                useDefaultReferenceIndex = true, limits = limits), fixture.core).javaClass.simpleName)
        }
    }

    @Test fun truncatedOrNonNativeGetterPathsAreNotOwnershipProof() {
        for (operation in listOf(0xFFFF, 0x2005)) {
            val fixture = CompiledAbilityTextFixture()
            fixture.half(fixture.getterLoad, operation)
            assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
        }
    }

    @Test fun provedSlotsRejectATruncatedCoreEvenWhenTheAbilityFieldStillFits() {
        val fixture = CompiledAbilityTextFixture()
        val slots = property(property(resolve(fixture), "Binding"), "SpeciesSlots")
        val short = RomImage(fixture.bytes.copyOf(fixture.core.offset + fixture.core.count * fixture.core.recordSize - 1))
        val values = slots.javaClass.getMethod("read", RomImage::class.java, Int::class.javaPrimitiveType!!)
            .invoke(slots, short, 3) as List<*>
        assertTrue(values.isEmpty())
    }

    @Test fun accessorCannotOverwriteTheSavedReturnOrWriteStackAndProgramCounters() {
        val outcomes = listOf(0x9001, 0x4685, 0x4687).map { operation ->
            val fixture = CompiledAbilityTextFixture()
            fixture.half(fixture.argumentSetupSites.first(), operation)
            resolve(fixture).javaClass.simpleName
        }
        assertEquals(List(3) { "Unavailable" }, outcomes)
    }

    @Test fun accessorCallsClobberTheLiveLinkRegisterEvenWhenTheSavedReturnWasPoppedElsewhere() {
        val fixture = CompiledAbilityTextFixture()
        fixture.half(fixture.accessorReturnSites.first(), 0x4770)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun accessorMayStoreArgumentsInsideItsAllocatedLocals() {
        assertEquals("Resolved", resolve(CompiledAbilityTextFixture(localArguments = true)).javaClass.simpleName)
    }

    @Test fun signedGreaterThanCannotTreatANegativeMachineValueAsUnsignedPositive() {
        val fixture = CompiledAbilityTextFixture(signedComparisonTrap = true)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun emptyRegisterListsAreNotNativeStackEvidence() {
        val outcomes = listOf(0xB400, 0xBC00).map { operation ->
            val fixture = CompiledAbilityTextFixture()
            fixture.half(fixture.argumentSetupSites.first(), operation)
            resolve(fixture).javaClass.simpleName
        } + resolve(CompiledAbilityTextFixture(emptyGetterPop = true)).javaClass.simpleName
        assertEquals(List(3) { "Unavailable" }, outcomes)
    }

    @Test fun unalignedSummaryPointerCellsCannotProveTypedInputs() {
        val fixture = CompiledAbilityTextFixture()
        for (site in fixture.summaryRootLoadSites) {
            val op = RomImage(fixture.bytes).u16le(site)
            val literal = ((site + 4) and -4) + (op and 255) * 4
            fixture.bytes[literal] = 1
        }
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun fieldOffsetComesFromGetterNotRecordSizeProfile() {
        val fixture = CompiledAbilityTextFixture(fieldOffset = 22)
        val outcome = resolve(fixture)
        assertEquals("Resolved", outcome.javaClass.simpleName)
        val slots = property(property(outcome, "Binding"), "SpeciesSlots")
        assertEquals(22, property(slots, "FieldOffset"))
        assertEquals(listOf(7, 12), readSlots(slots, fixture, 3))
    }

    @Test fun wrongGetterFieldWidthIsNotPromoted() {
        val fixture = CompiledAbilityTextFixture()
        fixture.half(fixture.getterLoad, 0x7808)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun getterMustMatchSelectedCoreRootAndStride() {
        val fixture = CompiledAbilityTextFixture()
        for (core in listOf(fixture.core.copy(offset = fixture.core.offset + 0x200), fixture.core.copy(recordSize = 40))) {
            assertEquals("Unavailable", resolve(fixture, core).javaClass.simpleName)
        }
    }

    @Test fun nearbyPublishedCoreCannotMasqueradeAsAnotherFieldOffset() {
        val fixture = CompiledAbilityTextFixture()
        val shifted = fixture.core.copy(offset = fixture.core.offset + 2)
        putGbaPointer(fixture.bytes, 0x1BC, shifted.offset)
        assertEquals("Unavailable", resolve(fixture, shifted).javaClass.simpleName)
    }

    @Test fun everyReachableGetterPathMustTerminate() {
        val fixture = CompiledAbilityTextFixture(getterCycle = true)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun nativeIdBeyondOwnedNameBoundaryIsNotPublished() {
        val fixture = CompiledAbilityTextFixture()
        fixture.half(fixture.core.offset + fixture.core.recordSize + fixture.fieldOffset, 20)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun completePerRootReferenceAndTargetBudgetsAreRequired() {
        val fixture = CompiledAbilityTextFixture()
        fixture.addNameConsumer()
        for (limits in listOf(ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
            ResolutionLimits(maxDistinctGbaReferenceTargets = 1))) {
            val session = abilitySession(fixture.bytes, useDefaultReferenceIndex = true, limits = limits)
            assertEquals("BudgetExceeded", invoke(session, fixture.core).javaClass.simpleName)
        }
    }

    @Test fun compiledInlineNamesRequireNativeTerminationInsideTheirCell() {
        val fixture = CompiledAbilityTextFixture()
        val binding = property(resolve(fixture), "Binding")
        val layout = property(binding, "Names") as AbilityNameTableLayout
        val start = layout.offset.toInt() + 12 * layout.stride
        fixture.bytes.fill(0xBB.toByte(), start, start + layout.nameWidth)
        val session = abilitySession(fixture.bytes, useDefaultReferenceIndex = true)
        val decoded = AbilityNameCodec().decode(session, layout, AbilitySemanticDomain(setOf(1, 2, 4, 7, 12)))
            as AbilityNameTableOutcome.Decoded
        assertTrue(decoded.resolved.rows[12] is AbilityNameRowOutcome.Malformed)
        assertTrue(12 in decoded.resolved.unresolvedActiveAbilityIds)
    }

    @Test fun compiledDirectArrayDoesNotReinterpretAnInteriorTokenAsAnAliasBoundary() {
        val fixture = CompiledAbilityTextFixture()
        val layout = property(property(resolve(fixture), "Binding"), "Names") as AbilityNameTableLayout
        val start = layout.offset.toInt() + 14 * layout.stride
        fixture.bytes.fill(0xFF.toByte(), start, start + layout.nameWidth)
        fixture.bytes[start] = 0xAE.toByte()
        val decoded = AbilityNameCodec().decode(abilitySession(fixture.bytes), layout,
            AbilitySemanticDomain(setOf(1, 2, 4, 7, 12))) as AbilityNameTableOutcome.Decoded
        assertEquals(20, decoded.resolved.baseRowCount)
        assertTrue(decoded.resolved.aliasLabels.isEmpty())
        assertTrue(19 in decoded.resolved.decodedDirectAbilityIds())
    }

    @Test fun selectedTypedNativeArrayCannotBeExtendedAcrossItsOwnedBoundary() {
        val fixture = CompiledAbilityTextFixture()
        val selected = property(property(resolve(fixture), "Binding"), "Names") as AbilityNameTableLayout
        val result = AbilityNameResolver().resolve(abilitySession(fixture.bytes),
            AbilitySemanticDomain(setOf(20)), selectedLayout = selected)
        assertEquals("Unavailable", result.javaClass.simpleName)
    }

    @Test fun rootLoadCannotClobberTheNameIndex() {
        val fixture = CompiledAbilityTextFixture()
        val low = fixture.bytes[fixture.nameRootLoad].toInt() and 0xFF
        fixture.half(fixture.nameRootLoad, 0x4900 or low)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun descriptionIndexMustMatchFourBytePointers() {
        val fixture = CompiledAbilityTextFixture()
        fixture.half(fixture.descriptionIndexShift, 0x0064)
        assertEquals("Unavailable", resolve(fixture).javaClass.simpleName)
    }

    @Test fun cancellationIsPropagated() {
        val fixture = CompiledAbilityTextFixture()
        val session = abilitySession(fixture.bytes, useDefaultReferenceIndex = true,
            cancellation = ParserCancellationToken { throw ParserCancellationException() })
        assertThrows(ParserCancellationException::class.java) { invoke(session, fixture.core) }
    }

    @Test fun workAndOwnedExtentsHaveExplicitBudgets() {
        val fixture = CompiledAbilityTextFixture()
        for (limits in listOf(ResolutionLimits(maxProbeWorkPerDataset = 1), ResolutionLimits(maxDatasetExtentBytes = 8))) {
            val session = abilitySession(fixture.bytes, useDefaultReferenceIndex = true, limits = limits)
            assertEquals("BudgetExceeded", invoke(session, fixture.core).javaClass.simpleName)
        }
    }

    private fun competingFixture(): CompiledAbilityTextFixture {
        val first = CompiledAbilityTextFixture()
        val second = CompiledAbilityTextFixture(relocation = 0x1000, coreOffset = first.core.offset)
        for (offset in 0x2000 until first.bytes.size) {
            if (second.bytes[offset] != 0xFF.toByte()) first.bytes[offset] = second.bytes[offset]
        }
        putGbaPointer(first.bytes, 0x1AC, first.core.offset)
        putGbaPointer(first.bytes, 0x1B0, second.names.offset.toInt())
        putGbaPointer(first.bytes, 0x1B4, second.descriptions.offset.toInt())
        return first
    }

    private fun resolve(fixture: CompiledAbilityTextFixture, core: TableLayout = fixture.core): Any =
        invoke(abilitySession(fixture.bytes, useDefaultReferenceIndex = true), core)

    // Reflection makes missing implementation an executable assertion, not a compiler/setup failure.
    private fun invoke(session: RomAnalysisSession, core: TableLayout): Any {
        val type = try { Class.forName("com.enrpau.dualscreendex.parser.parse.CompiledAbilityTextResolver") }
        catch (missing: ClassNotFoundException) { throw AssertionError("compiled native ability-text resolver is absent", missing) }
        return try { requireNotNull(type.getMethod("resolve", RomAnalysisSession::class.java, TableLayout::class.java).invoke(null, session, core)) }
        catch (failure: InvocationTargetException) { throw failure.targetException }
    }

    private fun property(value: Any, name: String): Any = requireNotNull(value.javaClass.getMethod("get$name").invoke(value))

    private fun readSlots(slots: Any, fixture: CompiledAbilityTextFixture, id: Int): List<*> =
        slots.javaClass.getMethod("read", RomImage::class.java, Int::class.javaPrimitiveType!!)
            .invoke(slots, RomImage(fixture.bytes), id) as List<*>
}
