package com.enrpau.dualscreendex.companion.battle

import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RarityEvaluatorTest {
    @Test
    fun mapsEveryInnateBoundaryToItsTierAndBaseStars() {
        val expectations = listOf(
            0 to InnateTier.FODDER,
            9 to InnateTier.FODDER,
            10 to InnateTier.STANDARD,
            17 to InnateTier.STANDARD,
            18 to InnateTier.TRAINED,
            23 to InnateTier.TRAINED,
            24 to InnateTier.VETERAN,
            27 to InnateTier.VETERAN,
            28 to InnateTier.ELITE,
            29 to InnateTier.ELITE,
            30 to InnateTier.ACE,
            31 to InnateTier.ACE,
        )

        expectations.forEach { (average, expectedTier) ->
            val result = RarityEvaluator.evaluate(individual(ivs = List(6) { average }), null, emptyList())
            assertEquals(expectedTier, result.innateTier)
            assertEquals(expectedTier.baseStars, result.baseStars)
            assertEquals(expectedTier.baseStars.toDouble(), result.stars)
        }
    }

    @Test
    fun normalizesFourAndFiveDvVectorsBeforeInnateTiering() {
        assertEquals(
            InnateTier.ACE,
            RarityEvaluator.evaluate(individual(generation = 1, dvs = List(4) { 15 }), null, emptyList()).innateTier,
        )
        assertEquals(
            InnateTier.STANDARD,
            RarityEvaluator.evaluate(individual(generation = 2, dvs = List(5) { 7 }), null, emptyList()).innateTier,
        )
    }

    @Test
    fun classifiesEveryRelativeBoundary() {
        val expected = mapOf(
            -4 to RelativeTier.WEAK,
            -3 to RelativeTier.WEAK,
            -2 to RelativeTier.ORDINARY,
            1 to RelativeTier.ORDINARY,
            2 to RelativeTier.COMPETENT,
            3 to RelativeTier.COMPETENT,
            4 to RelativeTier.STRONG,
            5 to RelativeTier.STRONG,
            6 to RelativeTier.MAJOR,
            7 to RelativeTier.MAJOR,
        )

        expected.forEach { (difference, tier) ->
            val level = 10 + difference
            val area = area(
                slots = listOf(
                    slot(1, level, level, 1),
                    slot(2, 10, 10, 1_000),
                ),
            )
            assertEquals(tier, RarityEvaluator.evaluate(individual(level = level), AREA_BASE_ID, listOf(area)).relativeTier)
        }
    }

    @Test
    fun usesWeightedMidpointsAcrossTheExactCandidateTable() {
        val area = area(
            slots = listOf(
                slot(speciesId = 1, minimumLevel = 8, maximumLevel = 12, weight = 80),
                slot(speciesId = 2, minimumLevel = 14, maximumLevel = 16, weight = 20),
            ),
        )

        val result = RarityEvaluator.evaluate(
            individual(speciesId = 2, level = 14, ivs = List(6) { 24 }),
            AREA_BASE_ID,
            listOf(area),
        )

        assertEquals(RelativeTier.COMPETENT, result.relativeTier)
        assertEquals(InnateTier.VETERAN, result.innateTier)
        assertEquals(3, result.baseStars)
        assertEquals(0.5, result.areaAdjustment)
        assertEquals(3.5, result.stars)
    }

    @Test
    fun clampsHalfStarAdjustmentsAtZeroAndFive() {
        val weakBaseline = area(slots = listOf(slot(1, 7, 7, 1), slot(2, 10, 10, 1_000)))
        val majorBaseline = area(slots = listOf(slot(1, 16, 16, 1), slot(2, 10, 10, 1_000)))

        assertEquals(
            0.0,
            RarityEvaluator.evaluate(individual(level = 7, ivs = List(6) { 0 }), AREA_BASE_ID, listOf(weakBaseline)).stars,
        )
        assertEquals(
            5.0,
            RarityEvaluator.evaluate(individual(level = 16, ivs = List(6) { 31 }), AREA_BASE_ID, listOf(majorBaseline)).stars,
        )
    }

    @Test
    fun acceptsMultipleCandidatesOnlyWhenTheirRelativeTiersAgree() {
        val agreeing = listOf(
            area(methodId = 1, slots = listOf(slot(1, 13, 13, 1), slot(2, 10, 10, 1_000))),
            area(methodId = 2, slots = listOf(slot(1, 13, 13, 1), slot(2, 11, 11, 1_000))),
        )
        val disagreeing = agreeing + area(methodId = 3, slots = listOf(slot(1, 13, 13, 1), slot(2, 14, 14, 1_000)))

        assertEquals(
            RelativeTier.COMPETENT,
            RarityEvaluator.evaluate(individual(level = 13), AREA_BASE_ID, agreeing).relativeTier,
        )
        val ambiguous = RarityEvaluator.evaluate(individual(level = 13), AREA_BASE_ID, disagreeing)
        assertNull(ambiguous.relativeTier)
        assertNull(ambiguous.areaAdjustment)
        assertEquals(3.0, ambiguous.stars)
    }

    @Test
    fun failsClosedForInvalidCandidateTables() {
        val invalidTables = listOf(
            area(slots = listOf(slot(1, 10, 10, null))),
            area(slots = listOf(slot(1, 10, 10, 0))),
            area(slots = listOf(slot(1, 12, 10, 100))),
            area(slots = listOf(slot(1, 10, 10, 100), slot(2, 12, 10, 20))),
        )

        invalidTables.forEach { invalid ->
            val result = RarityEvaluator.evaluate(individual(level = 10), AREA_BASE_ID, listOf(invalid))
            assertNull(result.relativeTier)
            assertNull(result.areaAdjustment)
            assertEquals(3.0, result.stars)
        }
    }

    @Test
    fun leavesAreaAdjustmentUnavailableWithoutExactCurrentAreaEvidence() {
        val candidate = area(slots = listOf(slot(1, 10, 12, 100)))
        val multipleCapableAreas = listOf(
            candidate,
            area(methodId = 2, slots = listOf(slot(1, 10, 12, 100))),
        )
        val cases = listOf(
            RarityEvaluator.evaluate(individual(level = 11), null, multipleCapableAreas),
            RarityEvaluator.evaluate(individual(level = 11), AREA_BASE_ID + 1, multipleCapableAreas),
            RarityEvaluator.evaluate(individual(speciesId = 2, level = 11), AREA_BASE_ID, listOf(candidate)),
            RarityEvaluator.evaluate(individual(level = 13), AREA_BASE_ID, listOf(candidate)),
        )

        cases.forEach { result ->
            assertNull(result.relativeTier)
            assertNull(result.areaAdjustment)
            assertEquals(3.0, result.stars)
        }
    }

    @Test
    fun reportsWhyAreaRelativeRarityCouldNotBeApplied() {
        val matchingArea = area(slots = listOf(slot(1, 10, 12, 100)))

        val unavailable = RarityEvaluator.evaluate(
            individual(level = 11),
            null,
            listOf(matchingArea, area(methodId = 2, slots = listOf(slot(1, 10, 12, 100)))),
        )
        assertEquals(AreaRarityOutcome.AREA_UNAVAILABLE, unavailable.areaOutcome)
        assertNull(unavailable.currentAreaBaseId)

        val missingArea = RarityEvaluator.evaluate(
            individual(level = 11),
            0x0202,
            listOf(matchingArea, area(methodId = 2, slots = listOf(slot(1, 10, 12, 100)))),
        )
        assertEquals(AreaRarityOutcome.AREA_NOT_IN_CATALOG, missingArea.areaOutcome)
        assertEquals(0x0202, missingArea.currentAreaBaseId)
        assertEquals(0, missingArea.matchingAreaCount)

        val missingSpecies = RarityEvaluator.evaluate(individual(speciesId = 2, level = 11), AREA_BASE_ID, listOf(matchingArea))
        assertEquals(AreaRarityOutcome.SPECIES_LEVEL_NOT_IN_AREA, missingSpecies.areaOutcome)
        assertEquals(1, missingSpecies.matchingAreaCount)
        assertEquals(0, missingSpecies.candidateAreaCount)

        val invalidWeights = RarityEvaluator.evaluate(
            individual(level = 11),
            AREA_BASE_ID,
            listOf(area(slots = listOf(slot(1, 10, 12, null)))),
        )
        assertEquals(AreaRarityOutcome.INVALID_WEIGHTS, invalidWeights.areaOutcome)
        assertEquals(1, invalidWeights.candidateAreaCount)

        val disagreeing = listOf(
            area(methodId = 1, slots = listOf(slot(1, 13, 13, 1), slot(2, 10, 10, 1_000))),
            area(methodId = 2, slots = listOf(slot(1, 13, 13, 1), slot(2, 14, 14, 1_000))),
        )
        val ambiguous = RarityEvaluator.evaluate(individual(level = 13), AREA_BASE_ID, disagreeing)
        assertEquals(AreaRarityOutcome.AMBIGUOUS_TIER, ambiguous.areaOutcome)
        assertEquals(2, ambiguous.candidateAreaCount)

        val applied = RarityEvaluator.evaluate(individual(level = 11), AREA_BASE_ID, listOf(matchingArea))
        assertEquals(AreaRarityOutcome.APPLIED, applied.areaOutcome)
        assertEquals(1, applied.matchingAreaCount)
        assertEquals(1, applied.candidateAreaCount)
    }

    @Test
    fun doesNotGuessTheCurrentAreaFromTheOpponentSpeciesAndLevel() {
        val uniqueCandidate = area(
            slots = listOf(
                slot(1, 14, 14, 1),
                slot(2, 10, 10, 1_000),
            ),
        )
        val unrelated = EncounterArea(
            id = 0x0301 * 10 + 1,
            baseAreaId = 0x0301,
            name = CatalogField.available("Other area"),
            methodId = 1,
            slots = listOf(slot(3, 5, 5, 100)),
        )

        val result = RarityEvaluator.evaluate(
            individual(speciesId = 1, level = 14),
            currentAreaBaseId = 0x0202,
            encounterAreas = listOf(uniqueCandidate, unrelated),
        )

        assertEquals(AreaRarityOutcome.AREA_NOT_IN_CATALOG, result.areaOutcome)
        assertNull(result.relativeTier)
        assertEquals(0, result.matchingAreaCount)
        assertEquals(1, result.candidateAreaCount)
    }

    @Test
    fun matchesTheCurrentAreaByExplicitBaseIdentityRatherThanEncodedRowArithmetic() {
        val expansionStyleRowId = AREA_BASE_ID * 100 + 21
        val result = RarityEvaluator.evaluate(
            individual(level = 14),
            currentAreaBaseId = AREA_BASE_ID,
            encounterAreas = listOf(
                EncounterArea(
                    id = expansionStyleRowId,
                    baseAreaId = AREA_BASE_ID,
                    name = CatalogField.available("Expansion area"),
                    methodId = 1,
                    slots = listOf(slot(1, 14, 14, 1), slot(2, 10, 10, 1_000)),
                ),
            ),
        )

        assertEquals(AreaRarityOutcome.APPLIED, result.areaOutcome)
        assertEquals(1, result.matchingAreaCount)
    }

    @Test
    fun doesNotInventStarsWhenInnateDataIsUnavailable() {
        val result = RarityEvaluator.evaluate(
            individual(level = 13, ivs = emptyList()),
            AREA_BASE_ID,
            listOf(area(slots = listOf(slot(1, 13, 13, 1), slot(2, 10, 10, 1_000)))),
        )

        assertEquals(RelativeTier.COMPETENT, result.relativeTier)
        assertNull(result.innateTier)
        assertNull(result.baseStars)
        assertEquals(0.5, result.areaAdjustment)
        assertNull(result.stars)
    }

    private fun individual(
        speciesId: Int = 1,
        generation: Int = 3,
        level: Int = 13,
        ivs: List<Int> = List(6) { 24 },
        dvs: List<Int> = emptyList(),
    ) = OwnedPokemon("battle", speciesId, generation, level, ivs = ivs, dvs = dvs)

    private fun area(
        methodId: Int = 1,
        slots: List<EncounterSlot>,
    ) = EncounterArea(
        id = AREA_BASE_ID * 10 + methodId,
        baseAreaId = AREA_BASE_ID,
        name = CatalogField.available("Test area"),
        methodId = methodId,
        slots = slots,
    )

    private fun slot(
        speciesId: Int,
        minimumLevel: Int,
        maximumLevel: Int,
        weight: Int?,
    ) = EncounterSlot(speciesId, minimumLevel, maximumLevel, weight)

    private companion object {
        const val AREA_BASE_ID = 0x0203
    }
}
