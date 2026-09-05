package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.dataset.core.basestats.Gen3BaseStatsRecord
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AbilityNameCodecTest {
    @Test
    fun requiresATerminatorTokenForTheJapaneseNoneSentinel() {
        val (bytes, layout) = japaneseNameTable()
        bytes.fill(0, 0x100, 0x100 + 13)
        byteArrayOf(0xF7.toByte(), 0xFF.toByte()).copyInto(bytes, 0x100)

        assertTrue(AbilityNameCodec(JapanesePokemonTextCodecs.gen3Later).decode(
            abilitySession(bytes), layout, AbilitySemanticDomain(setOf(1, 2)),
        ) is AbilityNameTableOutcome.Rejected)
    }

    @Test
    fun checksSentinelPaddingAfterTheCompleteTerminatorToken() {
        val (bytes, layout) = japaneseNameTable()
        byteArrayOf(0xF7.toByte(), 0xFF.toByte(), 0xF7.toByte(), 0, 0xFF.toByte())
            .copyInto(bytes, 0x100)
        val codec = AbilityNameCodec(JapanesePokemonTextCodecs.gen3Later)
        val domain = AbilitySemanticDomain(setOf(1, 2))

        assertTrue(codec.decode(abilitySession(bytes), layout, domain) is AbilityNameTableOutcome.Decoded)
        bytes[0x105] = 0x01
        assertTrue(codec.decode(abilitySession(bytes), layout, domain) is AbilityNameTableOutcome.Rejected)
    }

    @Test
    fun rejectsNonNativeNamesWithoutDiscardingCompiledAbilityIdentities() {
        listOf(
            byteArrayOf(0xBB.toByte(), 0xBC.toByte(), 0xFF.toByte()),
            byteArrayOf(0x01, 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xFF.toByte()),
        ).forEach { name ->
            val (bytes, layout) = japaneseNameTable()
            name.copyInto(bytes, 0x100 + 13)
            val outcome = AbilityNameCodec(JapanesePokemonTextCodecs.gen3Later).decode(
                abilitySession(bytes), layout, AbilitySemanticDomain(setOf(1, 2)),
            ) as AbilityNameTableOutcome.Decoded

            assertTrue(outcome.resolved.rows[1] is AbilityNameRowOutcome.Malformed)
            assertEquals(setOf(1), outcome.resolved.unresolvedActiveAbilityIds)
            assertTrue(1 in outcome.resolved.catalogDirectAbilityIds())
            assertEquals(CapabilityStatus.NOT_FOUND, outcome.resolved.catalogAbilities().getValue(1).name.status)
            assertEquals("あい", (outcome.resolved.rows[2] as AbilityNameRowOutcome.Decoded).name)
        }
    }

    @Test
    fun distinguishesFullWidthNativeNamesFromTruncatedControls() {
        val (bytes, layout) = japaneseNameTable()
        bytes.fill(0x01, 0x100 + 13, 0x100 + 26)
        val codec = AbilityNameCodec(JapanesePokemonTextCodecs.gen3Later)
        val domain = AbilitySemanticDomain(setOf(1, 2))
        val fullWidth = codec.decode(abilitySession(bytes), layout, domain) as AbilityNameTableOutcome.Decoded
        assertEquals("あ".repeat(13), (fullWidth.resolved.rows[1] as AbilityNameRowOutcome.Decoded).name)

        bytes[0x100 + 25] = 0xF7.toByte()
        val truncated = codec.decode(abilitySession(bytes), layout, domain) as AbilityNameTableOutcome.Decoded
        assertTrue(truncated.resolved.rows[1] is AbilityNameRowOutcome.Malformed)
        assertTrue(truncated.resolved.rows[2] is AbilityNameRowOutcome.Decoded)
        assertEquals(setOf(1), truncated.resolved.unresolvedActiveAbilityIds)
    }

    private fun japaneseNameTable(): Pair<ByteArray, AbilityNameTableLayout> {
        val layout = AbilityNameTableLayout(0x100, 20, 13)
        val bytes = ByteArray(0x100 + 20 * 13)
        bytes[0x100] = 0xFF.toByte()
        for (index in 1 until 20) {
            byteArrayOf(0x01, 0x02, 0xFF.toByte()).copyInto(bytes, 0x100 + index * 13)
        }
        return bytes to layout
    }

    @Test
    fun cancelsWhileDecodingAbilityNameRows() {
        val layout = AbilityNameTableLayout(0x100, 3, 13)
        val bytes = ByteArray(0x100 + 3 * 13)
        putAbilityNames(bytes, layout, listOf("-------", "STENCH", "DRIZZLE"))
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 2) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            AbilityNameCodec().decode(
                abilitySession(bytes, cancellation = cancellation),
                layout,
                AbilitySemanticDomain(setOf(1, 2)),
            )
        }
        assertEquals(2, checks)
    }

    @Test
    fun acceptsRetailBlankAndTerminatedPlaceholderRowZeroButRejectsContamination() {
        val layout = AbilityNameTableLayout(0x100, 3, 13)
        val bytes = ByteArray(0x100 + 3 * 13)
        bytes[0x100] = 0xFF.toByte()
        putGbaText(bytes, 0x100 + 13, "STENCH", 13)
        putGbaText(bytes, 0x100 + 26, "DRIZZLE", 13)

        val decoded = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 2)),
        ) as AbilityNameTableOutcome.Decoded
        assertTrue(decoded.resolved.rows[0] is AbilityNameRowOutcome.StructuralSentinel)

        putGbaText(bytes, 0x100, "-------", 13)
        val placeholder = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 2)),
        ) as AbilityNameTableOutcome.Decoded
        assertTrue(placeholder.resolved.rows[0] is AbilityNameRowOutcome.StructuralSentinel)

        bytes[0x100 + 8] = 0xFF.toByte()
        val doubleTerminatorPadding = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 2)),
        ) as AbilityNameTableOutcome.Decoded
        assertTrue(doubleTerminatorPadding.resolved.rows[0] is AbilityNameRowOutcome.StructuralSentinel)

        bytes[0x100 + 8] = 0xBB.toByte()
        val nonPaddingTail = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 2)),
        )
        assertTrue(nonPaddingTail is AbilityNameTableOutcome.Rejected)
        bytes[0x100 + 8] = 0

        bytes.fill(0, 0x100 + 13, 0x100 + 26)
        bytes[0x100 + 13] = 0xFF.toByte()
        val rejected = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 2)),
        )
        assertTrue(rejected is AbilityNameTableOutcome.Rejected)

        putGbaText(bytes, 0x100 + 13, "STENCH", 13)
        bytes[0x100 + 1] = 0xBB.toByte()
        val contaminatedRowZero = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 2)),
        )
        assertTrue(contaminatedRowZero is AbilityNameTableOutcome.Rejected)
    }

    @Test
    fun acceptsLocalizedPunctuationAsTheStructuralNoneSentinel() {
        val layout = AbilityNameTableLayout(0x100, 3, 13)
        val bytes = ByteArray(0x100 + 3 * 13)
        bytes[0x100] = 0x5C
        bytes[0x101] = 0xAC.toByte()
        bytes[0x102] = 0x5D
        bytes[0x103] = 0xFF.toByte()
        putGbaText(bytes, 0x100 + 13, "HEDOR", 13)
        putGbaText(bytes, 0x100 + 26, "LLOVIZNA", 13)

        val decoded = AbilityNameCodec(WesternPokemonTextCodecs.gen3Spanish).decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 2)),
        ) as AbilityNameTableOutcome.Decoded

        assertTrue(decoded.resolved.rows[0] is AbilityNameRowOutcome.StructuralSentinel)
    }

    @Test
    fun trimsOnlyAnAllSentinelSuffixAfterTheLastDecodedActiveAbility() {
        val names = buildList {
            add("-------")
            repeat(85) { index -> add("ABILITY ${index + 1}") }
            add("...")
            repeat(169) { add("-------") }
        }
        val layout = AbilityNameTableLayout(0x100, names.size.toLong(), 13)
        val bytes = ByteArray(0x100 + names.size * 13)
        putAbilityNames(bytes, layout, names)

        val decoded = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 65, 85)),
        ) as AbilityNameTableOutcome.Decoded

        assertEquals(86, decoded.resolved.baseRowCount)
        assertEquals(85, decoded.resolved.baseAbilityCount)
        assertEquals((1..85).toSet(), decoded.resolved.decodedDirectAbilityIds())
        assertTrue(decoded.resolved.aliasLabels.isEmpty())
    }

    @Test
    fun doesNotTrimAnInternalSentinelHoleOrAnyActiveSuffixId() {
        val internalHole = buildList {
            add("-------")
            repeat(77) { index -> add("ABILITY ${index + 1}") }
            add("----")
            add("SKILL LINK")
            add("OTHER ABILITY")
        }
        val internalLayout = AbilityNameTableLayout(0x100, internalHole.size.toLong(), 13)
        val internalBytes = ByteArray(0x100 + internalHole.size * 13)
        putAbilityNames(internalBytes, internalLayout, internalHole)

        val internal = AbilityNameCodec().decode(
            abilitySession(internalBytes),
            internalLayout,
            AbilitySemanticDomain(setOf(19, 78, 80)),
        ) as AbilityNameTableOutcome.Decoded
        assertEquals(internalHole.size, internal.resolved.baseRowCount)
        assertTrue(78 !in internal.resolved.decodedDirectAbilityIds())
        assertTrue(79 in internal.resolved.decodedDirectAbilityIds())

        val activeSuffix = buildList {
            add("-------")
            repeat(9) { index -> add("ABILITY ${index + 1}") }
            add("-------")
        }
        val suffixLayout = AbilityNameTableLayout(0x100, activeSuffix.size.toLong(), 13)
        val suffixBytes = ByteArray(0x100 + activeSuffix.size * 13)
        putAbilityNames(suffixBytes, suffixLayout, activeSuffix)
        val suffix = AbilityNameCodec().decode(
            abilitySession(suffixBytes),
            suffixLayout,
            AbilitySemanticDomain(setOf(1, 10)),
        ) as AbilityNameTableOutcome.Decoded
        assertEquals(activeSuffix.size, suffix.resolved.baseRowCount)
    }

    @Test
    fun decodesOfficialAndExpandedCatalogCardinalitiesWithoutHardCodedCounts() {
        val shapes = listOf(
            Shape(77, 13, 13),
            Shape(254, 13, 13),
            Shape(255, 17, 17),
            Shape(310, 20, 28),
        )

        shapes.forEach { shape ->
            val layout = AbilityNameTableLayout(
                offset = 0x100,
                count = (shape.abilityCount + 1).toLong(),
                nameWidth = shape.width,
                stride = shape.stride,
            )
            val bytes = ByteArray(0x100 + shape.stride * (shape.abilityCount + 1) + 0x20)
            putAbilityNames(bytes, layout, ordinaryAbilityNames(shape.abilityCount))

            val decoded = AbilityNameCodec().decode(
                abilitySession(bytes),
                layout,
                AbilitySemanticDomain(setOf(1, shape.abilityCount)),
            ) as AbilityNameTableOutcome.Decoded

            assertEquals(shape.abilityCount, decoded.resolved.baseAbilityCount)
            assertEquals(shape.abilityCount + 1, decoded.resolved.baseRows.size)
            assertTrue(decoded.resolved.aliasLabels.isEmpty())
        }
    }

    @Test
    fun preservesAmethystAndBillsFullWideCatalogSemantics() {
        listOf(292, 327).forEach { abilityCount ->
            val layout = AbilityNameTableLayout(0x80, abilityCount.toLong() + 1, 17)
            val bytes = ByteArray(0x80 + (abilityCount + 1) * 17)
            putAbilityNames(bytes, layout, ordinaryAbilityNames(abilityCount))

            val decoded = AbilityNameCodec().decode(
                abilitySession(bytes),
                layout,
                AbilitySemanticDomain(setOf(1, abilityCount)),
            ) as AbilityNameTableOutcome.Decoded

            assertEquals(abilityCount, decoded.resolved.baseAbilityCount)
            assertTrue(decoded.resolved.aliasLabels.isEmpty())
        }
    }

    @Test
    fun separatesCfruBaseIdsFromPostSentinelSpeciesConditionedAliases() {
        val names = buildList {
            add("-------")
            repeat(254) { index -> add("BASE ${index + 1}") }
            add("-")
            addAll(listOf("AIR LOCK", "VITAL SPIRIT", "WHITE SMOKE", "PURE POWER"))
        }
        val layout = AbilityNameTableLayout(0x100, names.size.toLong(), 17)
        val bytes = ByteArray(0x100 + names.size * 17)
        putAbilityNames(bytes, layout, names)

        val decoded = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 65, 254)),
        ) as AbilityNameTableOutcome.Decoded

        assertEquals(254, decoded.resolved.baseAbilityCount)
        assertEquals(255, decoded.resolved.baseRows.size)
        assertEquals(
            listOf(
                AbilityAliasLabel(256, "AIR LOCK"),
                AbilityAliasLabel(257, "VITAL SPIRIT"),
                AbilityAliasLabel(258, "WHITE SMOKE"),
                AbilityAliasLabel(259, "PURE POWER"),
            ),
            decoded.resolved.aliasLabels,
        )
    }

    @Test
    fun keepsAnActiveStructuralSentinelAsASparseDirectIdHole() {
        val names = buildList {
            add("-------")
            repeat(254) { add("BASE ABILITY") }
            add("-")
            addAll(listOf("RUNTIME LABEL", "OTHER LABEL"))
        }
        val layout = AbilityNameTableLayout(0x100, names.size.toLong(), 17)
        val bytes = ByteArray(0x100 + names.size * 17)
        putAbilityNames(bytes, layout, names)

        val outcome = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(255)),
        )

        val decoded = outcome as AbilityNameTableOutcome.Decoded
        assertEquals(names.size, decoded.resolved.baseRowCount)
        assertTrue(255 !in decoded.resolved.decodedDirectAbilityIds())
        assertTrue(255 !in decoded.resolved.catalogDirectAbilityIds())
        assertTrue(decoded.resolved.unresolvedActiveAbilityIds.isEmpty())
    }

    @Test
    fun preservesAnIndependentlyReferencedMalformedNameAsAnUnresolvedAbilityIdentity() {
        val names = buildList {
            add("-------")
            repeat(19) { add("BASE ABILITY") }
        }
        val layout = AbilityNameTableLayout(0x100, names.size.toLong(), 17)
        val bytes = ByteArray(0x100 + names.size * 17)
        putAbilityNames(bytes, layout, names)
        bytes.fill(0, 0x100 + 7 * 17, 0x100 + 8 * 17)

        val outcome = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(7)),
        ) as AbilityNameTableOutcome.Decoded

        assertTrue(7 !in outcome.resolved.decodedDirectAbilityIds())
        assertTrue(7 in outcome.resolved.catalogDirectAbilityIds())
        assertEquals(CapabilityStatus.NOT_FOUND, outcome.resolved.catalogAbilities().getValue(7).name.status)
        assertEquals(setOf(7), outcome.resolved.unresolvedActiveAbilityIds)
    }

    @Test
    fun acceptsASparsePhysicalTableOnlyWhenEveryCompiledReferencedAbilityHasAName() {
        val layout = AbilityNameTableLayout(0x100, 20, 13)
        val bytes = ByteArray(0x100 + 20 * 13)
        putGbaText(bytes, 0x100, "-------", 13)
        listOf(2, 7, 18).forEach { abilityId ->
            putGbaText(bytes, 0x100 + abilityId * 13, "ABILITY $abilityId", 13)
        }

        val decoded = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(2, 7, 18)),
        ) as AbilityNameTableOutcome.Decoded
        assertEquals(setOf(2, 7, 18), decoded.resolved.decodedDirectAbilityIds())

        val incomplete = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(2, 7, 17, 18)),
        )
        assertTrue(incomplete is AbilityNameTableOutcome.Rejected)
    }

    @Test
    fun rejectsCompetingPostCatalogSentinelsInsteadOfChoosingByOrder() {
        val names = buildList {
            add("-------")
            repeat(10) { add("BASE ABILITY") }
            add("-")
            addAll(listOf("FIRST ALIAS", "SECOND ALIAS", "THIRD ALIAS"))
            add("---")
            addAll(listOf("OTHER LABEL", "ANOTHER LABEL", "FINAL LABEL"))
        }
        val layout = AbilityNameTableLayout(0x100, names.size.toLong(), 17)
        val bytes = ByteArray(0x100 + names.size * 17)
        putAbilityNames(bytes, layout, names)

        val outcome = AbilityNameCodec().decode(
            abilitySession(bytes),
            layout,
            AbilitySemanticDomain(setOf(1, 10)),
        )

        assertTrue(outcome is AbilityNameTableOutcome.Rejected)
    }

    @Test
    fun buildsTheActiveAbilityDomainOnlyFromDecodedBaseStats() {
        val domain = AbilitySemanticDomain.fromDecodedBaseStats(
            listOf(baseStats(7, 9), baseStats(9, 145), baseStats()),
        )

        assertEquals(listOf(7, 9, 145), domain.activeAbilityIds)
        assertEquals(145, domain.maximumDirectAbilityId)
    }

    private fun baseStats(vararg abilityIds: Int) = Gen3BaseStatsRecord(
        stats = BaseStats(45, 49, 49, 45, 65, 65),
        typeIds = listOf(12, 3),
        catchRate = 45,
        baseExperienceYield = 64,
        evYield = 0,
        heldItemIds = emptyList(),
        genderRatio = 127,
        eggCycles = 20,
        baseFriendship = 70,
        growthRate = 4,
        eggGroupIds = listOf(1, 7),
        abilityIds = abilityIds.toList(),
        safariZoneFleeRate = 0,
        bodyColor = 6,
        noFlip = false,
    )

    private data class Shape(val abilityCount: Int, val width: Int, val stride: Int)
}
