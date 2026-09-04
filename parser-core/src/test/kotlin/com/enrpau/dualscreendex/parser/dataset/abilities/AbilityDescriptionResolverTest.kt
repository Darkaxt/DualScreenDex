package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetKind
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AbilityDescriptionResolverTest {
    @Test
    fun cancellationAtEntryPrecedesUnavailableResolution() {
        val failure = ParserCancellationException()
        val session = abilitySession(ByteArray(0x800), cancellation = ParserCancellationToken { throw failure })

        assertSame(failure, assertThrows(ParserCancellationException::class.java) {
            AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(session, resolvedNames(3))
        })
    }

    @Test
    fun cancellationIsCheckedForEveryDirectProposalBeforeCardinalityRejection() {
        assertProposalCancellation(direct = true)
    }

    @Test
    fun cancellationIsCheckedForEveryInheritedProposalBeforeCardinalityRejection() {
        assertProposalCancellation(direct = false)
    }

    private fun assertProposalCancellation(direct: Boolean) {
        val failure = ParserCancellationException()
        var checks = 0
        var decodes = 0
        val session = abilitySession(ByteArray(0x800), cancellation = ParserCancellationToken {
            if (++checks == 3) throw failure
        })
        val resolver = AbilityDescriptionResolver(AbilityDescriptionTableDecoder { _, layout ->
            decodes++
            AbilityDescriptionTableOutcome.Rejected(layout, "fixture rejection")
        })
        val proposals = listOf(AbilityDescriptionTableLayout(0x100, 5), AbilityDescriptionTableLayout(0x200, 5))

        assertSame(failure, assertThrows(ParserCancellationException::class.java) {
            resolver.resolve(
                session,
                resolvedNames(3),
                directCompiledConsumerLayouts = if (direct) proposals else emptyList(),
                inheritedLayouts = if (direct) emptyList() else proposals,
            )
        })
        assertEquals(3, checks)
        assertEquals(0, decodes)
    }

    @Test
    fun resolvesJapaneseDescriptionsThroughExplicitDecoder() {
        val table = AbilityDescriptionTableLayout(0x100, 3)
        val bytes = ByteArray(0x1000)
        putGbaPointer(bytes, 0x100, 0x400)
        bytes[0x400] = 0xFF.toByte()
        for (index in 1..2) {
            val target = 0x400 + index * 0x40
            putGbaPointer(bytes, 0x100 + index * 4, target)
            byteArrayOf(0x01, 0x02, 0x13, 0x19, 0x0A, 0x03, 0x3A, 0x07, 0x2D, 0x1C, 0x0E, 0x39, 0xFF.toByte())
                .copyInto(bytes, target)
        }

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(JapanesePokemonTextCodecs.gen3Later))
            .resolve(abilitySession(bytes), resolvedNames(2), inheritedLayouts = listOf(table))
            as DatasetResolution.Resolved<ResolvedAbilityDescriptionLayout>

        assertEquals(2, result.candidate.strength.semanticCoverage?.covered)
        assertEquals(AbilityDescriptionRowOutcome.Decoded(2, "あいてのこうげきをふせぐ"), result.candidate.layout.rows[2])
    }

    @Test
    fun publishedAbilityDescriptionsBeatADenseCompiledMoveDescriptionDecoy() {
        val names = resolvedNames(abilityCount = 20)
        val published = AbilityDescriptionTableLayout(0x100, 21)
        val decoy = AbilityDescriptionTableLayout(0x300, 21)
        val bytes = ByteArray(0x5000)
        val partialDescriptions = MutableList<String?>(21) { index ->
            when {
                index == 0 -> "NO SPECIAL ABILITY"
                index <= 15 -> "ABILITY EFFECT NUMBER $index"
                else -> null
            }
        }
        val moveDescriptions = MutableList<String?>(21) { index ->
            if (index == 0) "NO SPECIAL ABILITY" else "MOVE DAMAGE DESCRIPTION"
        }
        putAbilityDescriptions(bytes, published, partialDescriptions, 0x1000)
        putAbilityDescriptions(bytes, decoy, moveDescriptions, 0x3000)

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(
            session = abilitySession(bytes, references = mapOf(0x100 to 2, 0x300 to 2)),
            abilityNames = names,
            publishedLayouts = listOf(published),
            compiledLayouts = listOf(decoy),
        ) as DatasetResolution.Partial<ResolvedAbilityDescriptionLayout>

        assertEquals(published, result.candidate.layout.table)
        assertEquals(CandidateSource.PUBLISHED_HEADER, result.candidate.source)
        assertEquals(15, result.candidate.strength.semanticCoverage?.covered)
        assertEquals(20, result.candidate.strength.semanticCoverage?.expected)
    }

    @Test
    fun cloverMissingProseIsReportedPartialWhileLaterIdsRemainDecoded() {
        val names = resolvedNames(abilityCount = 254)
        val table = AbilityDescriptionTableLayout(0x100, 255)
        val descriptions = MutableList<String?>(255) { index ->
            if (index == 0) "NO SPECIAL ABILITY" else "ABILITY EFFECT NUMBER $index"
        }
        descriptions[218] = "-"
        val bytes = ByteArray(0x10000)
        putAbilityDescriptions(bytes, table, descriptions, 0x1000)

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(
            session = abilitySession(bytes),
            abilityNames = names,
            inheritedLayouts = listOf(table),
        ) as DatasetResolution.Partial<ResolvedAbilityDescriptionLayout>

        assertEquals(253, result.candidate.strength.semanticCoverage?.covered)
        assertEquals(254, result.candidate.strength.semanticCoverage?.expected)
        assertEquals(AbilityDescriptionRowOutcome.MissingProse(218, "-"), result.candidate.layout.rows[218])
        assertTrue(result.candidate.layout.rows[219] is AbilityDescriptionRowOutcome.Decoded)
    }

    @Test
    fun resolvesBattleTheaterEmbeddedDescriptionsAsDirectBaseAbilityIds() {
        val names = resolvedNames(abilityCount = 310, width = 20, stride = 28)
        val table = AbilityDescriptionTableLayout(0x100, 311, recordStride = 28, pointerOffset = 20)
        val descriptions = MutableList<String?>(311) { index ->
            if (index == 0) "NO SPECIAL ABILITY" else "ABILITY EFFECT NUMBER $index"
        }
        val bytes = ByteArray(0x10000)
        putAbilityDescriptions(bytes, table, descriptions, 0x3000)

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(
            session = abilitySession(bytes),
            abilityNames = names,
            directCompiledConsumerLayouts = listOf(table),
        ) as DatasetResolution.Resolved<ResolvedAbilityDescriptionLayout>

        assertEquals(310, result.candidate.strength.semanticCoverage?.covered)
        assertEquals(310, result.candidate.strength.semanticCoverage?.expected)
        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, result.candidate.source)
    }

    @Test
    fun decisiveDirectDescriptionsBypassUnrelatedReferenceIndexOverflow() {
        val names = resolvedNames(abilityCount = 3)
        val direct = AbilityDescriptionTableLayout(0x100, 4)
        val compiled = AbilityDescriptionTableLayout(0x200, 4)
        val descriptions = listOf(
            "NO SPECIAL ABILITY",
            "FIRST ABILITY EFFECT",
            "SECOND ABILITY EFFECT",
            "THIRD ABILITY EFFECT",
        )
        val bytes = ByteArray(0x2000)
        putAbilityDescriptions(bytes, direct, descriptions, 0x800)
        putAbilityDescriptions(bytes, compiled, descriptions, 0x1000)
        var indexBuilds = 0

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(
            session = abilitySession(
                bytes,
                referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                    "unrelated fixture reference overflow",
                    observedTargets = 2,
                    limitTargets = 1,
                ),
                onReferenceIndexBuild = { indexBuilds++ },
            ),
            abilityNames = names,
            directCompiledConsumerLayouts = listOf(direct),
            compiledLayouts = listOf(compiled),
        ) as DatasetResolution.Resolved<ResolvedAbilityDescriptionLayout>

        assertEquals(direct, result.candidate.layout.table)
        assertEquals(CandidateSource.DIRECT_COMPILED_CONSUMER, result.candidate.source)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun equalDirectDescriptionCandidatesStayAmbiguousWithoutBuildingTheReferenceIndex() {
        val names = resolvedNames(abilityCount = 3)
        val first = AbilityDescriptionTableLayout(0x100, 4)
        val second = AbilityDescriptionTableLayout(0x200, 4)
        val compiled = AbilityDescriptionTableLayout(0x300, 4)
        val descriptions = listOf(
            "NO SPECIAL ABILITY",
            "FIRST ABILITY EFFECT",
            "SECOND ABILITY EFFECT",
            "THIRD ABILITY EFFECT",
        )
        val bytes = ByteArray(0x2000)
        putAbilityDescriptions(bytes, first, descriptions, 0x800)
        putAbilityDescriptions(bytes, second, descriptions, 0x1000)
        putAbilityDescriptions(bytes, compiled, descriptions, 0x1800)
        var indexBuilds = 0

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(
            session = abilitySession(
                bytes,
                referenceIndexOverride = GbaReferenceIndex.budgetExceeded(
                    "unrelated fixture reference overflow",
                    observedTargets = 2,
                    limitTargets = 1,
                ),
                onReferenceIndexBuild = { indexBuilds++ },
            ),
            abilityNames = names,
            directCompiledConsumerLayouts = listOf(second, first),
            compiledLayouts = listOf(compiled),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun equalCompiledDescriptionTablesStayAmbiguous() {
        val names = resolvedNames(abilityCount = 3)
        val first = AbilityDescriptionTableLayout(0x100, 4)
        val second = AbilityDescriptionTableLayout(0x200, 4)
        val bytes = ByteArray(0x2000)
        val descriptions = listOf(
            "NO SPECIAL ABILITY",
            "FIRST ABILITY EFFECT",
            "SECOND ABILITY EFFECT",
            "THIRD ABILITY EFFECT",
        )
        putAbilityDescriptions(bytes, first, descriptions, 0x800)
        putAbilityDescriptions(bytes, second, descriptions, 0x1000)

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(
            session = abilitySession(bytes, references = mapOf(0x100 to 2, 0x200 to 2)),
            abilityNames = names,
            compiledLayouts = listOf(second, first),
        )

        assertTrue(result is DatasetResolution.Ambiguous)
        assertEquals(
            setOf(first, second),
            (result as DatasetResolution.Ambiguous<ResolvedAbilityDescriptionLayout>)
                .candidates.map { it.layout.table }.toSet(),
        )
    }

    @Test
    fun rejectsATableWhoseCoverageIsMostlyMissingProsePlaceholders() {
        val names = resolvedNames(abilityCount = 10)
        val table = AbilityDescriptionTableLayout(0x100, 11)
        val descriptions = buildList<String?> {
            add("NO SPECIAL ABILITY")
            add("FIRST ABILITY EFFECT")
            add("SECOND ABILITY EFFECT")
            repeat(8) { add("-") }
        }
        val bytes = ByteArray(0x2000)
        putAbilityDescriptions(bytes, table, descriptions, 0x800)

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(
            session = abilitySession(bytes),
            abilityNames = names,
            inheritedLayouts = listOf(table),
        )

        assertTrue(result is DatasetResolution.Unavailable)
    }

    @Test
    fun rejectsDescriptionCardinalityThatIsNotTiedToTheResolvedBaseAbilityIds() {
        val names = resolvedNames(abilityCount = 3)
        val wrongCount = AbilityDescriptionTableLayout(0x100, 5)
        val bytes = ByteArray(0x1000)

        val result = AbilityDescriptionResolver(AbilityDescriptionCodec(PokemonTextCodec.gbaEnglish)).resolve(
            session = abilitySession(bytes),
            abilityNames = names,
            inheritedLayouts = listOf(wrongCount),
        )

        assertTrue(result is DatasetResolution.Unavailable)
        assertTrue(
            (result as DatasetResolution.Unavailable<ResolvedAbilityDescriptionLayout>)
                .reason.contains("cardinality", ignoreCase = true),
        )
    }

    private fun resolvedNames(
        abilityCount: Int,
        width: Int = 13,
        stride: Int = width,
    ): ResolvedAbilityNameLayout {
        val table = AbilityNameTableLayout(0, abilityCount.toLong() + 1, width, stride)
        val rows = buildList {
            add(AbilityNameRowOutcome.StructuralSentinel(0, "-------"))
            repeat(abilityCount) { index ->
                add(AbilityNameRowOutcome.Decoded(index + 1, "ABILITY ${index + 1}"))
            }
        }
        return ResolvedAbilityNameLayout(table, rows, abilityCount + 1, emptyList())
    }
}
