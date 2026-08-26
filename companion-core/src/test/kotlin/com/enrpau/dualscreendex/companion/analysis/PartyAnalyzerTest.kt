package com.enrpau.dualscreendex.companion.analysis

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.PartyMemberDetails
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanic
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicCondition
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicConditionKind
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartyAnalyzerTest {
    @Test
    fun emptyPartyProducesNoInventedMembersOrDevelopmentWarnings() {
        val result = PartyAnalyzer.analyze(emptyList(), catalog())

        assertEquals(0, result.teamSummary.partySize)
        assertNull(result.teamSummary.minimumLevel)
        assertNull(result.teamSummary.maximumLevel)
        assertEquals(0, result.teamSummary.faintedCount)
        assertEquals(0, result.teamSummary.statusCount)
        assertNull(result.teamSummary.moveDistribution)
        assertEquals(emptyList<PartyMemberDefense>(), result.defensiveProfile!!.members)
        assertEquals(emptyList<Int>(), result.defensiveProfile.unavailableMemberSlots)
        assertEquals(emptyList<EvolutionOpportunity>(), result.development.evolutionOpportunities)
        assertEquals(emptyList<NearbyMove>(), result.development.nearbyMoves)
        assertEquals(emptyList<MoveRoleGap>(), result.development.moveRoleGaps)
    }

    @Test
    fun partialInputsWithholdOnlyCalculationsThatCannotBeProven() {
        val partial = owned(
            speciesId = 999,
            level = 8,
            moveIds = listOf(999),
            movePp = listOf(5),
        )

        val result = PartyAnalyzer.analyze(listOf(partial), catalog())

        assertEquals(1, result.teamSummary.partySize)
        assertEquals(8, result.teamSummary.minimumLevel)
        assertEquals(MoveCategoryDistribution(0, 0, 0, 1), result.teamSummary.moveDistribution)
        assertEquals(listOf(0), result.defensiveProfile!!.unavailableMemberSlots)
        assertEquals(emptyList<PartyMemberDefense>(), result.defensiveProfile.members)
        assertEquals(emptyList<MoveRoleGap>(), result.development.moveRoleGaps)
        assertEquals(
            setOf(OffensiveCoverageOutcome.NO_EFFECTIVE_KNOWN_OPTION),
            result.offensiveCoverage!!.types.mapTo(mutableSetOf()) { it.outcome },
        )
    }

    @Test
    fun faintedMembersRemainStructuralAndAreMarkedUnavailableForImmediateBattle() {
        val fainted = owned(
            speciesId = 1,
            level = 12,
            currentHp = 0,
            maximumHp = 30,
            status = 0x40,
            moveIds = listOf(10, 11),
            movePp = listOf(10, 20),
        )

        val result = PartyAnalyzer.analyze(listOf(fainted), catalog())

        assertEquals(1, result.teamSummary.partySize)
        assertEquals(1, result.teamSummary.faintedCount)
        assertEquals(1, result.teamSummary.statusCount)
        assertEquals(false, result.defensiveProfile!!.members.single().availableForImmediateBattle)
        assertEquals(listOf(2, 4), result.defensiveProfile.members.single().weaknessTypeIds)
        assertEquals(listOf(3), result.defensiveProfile.members.single().resistanceTypeIds)
    }

    @Test
    fun singleMemberAnalysisUsesLiveMovesParsedDevelopmentAndResolvedCategories() {
        val member = owned(
            speciesId = 1,
            level = 12,
            moveIds = listOf(10, 11),
            movePp = listOf(10, 20),
        )

        val result = PartyAnalyzer.analyze(listOf(member), catalog())

        assertEquals(MoveCategoryDistribution(1, 0, 1, 0), result.teamSummary.moveDistribution)
        assertEquals(
            OffensiveCoverageOutcome.SUPER_EFFECTIVE,
            result.offensiveCoverage!!.types.single { it.defendingTypeId == 3 }.outcome,
        )
        assertEquals(
            OffensiveCoverageOutcome.NEUTRAL_ONLY,
            result.offensiveCoverage.types.single { it.defendingTypeId == 1 }.outcome,
        )
        assertEquals(
            OffensiveCoverageOutcome.NO_EFFECTIVE_KNOWN_OPTION,
            result.offensiveCoverage.types.single { it.defendingTypeId == 2 }.outcome,
        )
        assertEquals(
            EvolutionOpportunity(0, 1, 2, 4, 16, false),
            result.development.evolutionOpportunities.single(),
        )
        assertEquals(NearbyMove(0, 1, 12, 14, 2), result.development.nearbyMoves.single())
        assertEquals(listOf(MoveRoleGap.SPECIAL), result.development.moveRoleGaps)
    }

    @Test
    fun sixMemberPartyUsesTheFullLevelSpanAndCountsRepeatedWeaknessesOncePerMember() {
        val party = listOf(5, 10, 15, 20, 25, 30).mapIndexed { slot, level ->
            owned(
                speciesId = 1,
                level = level,
                moveIds = listOf(10),
                movePp = listOf(10),
                stableLocation = "party:$slot",
            )
        }

        val result = PartyAnalyzer.analyze(party, catalog())

        assertEquals(6, result.teamSummary.partySize)
        assertEquals(5, result.teamSummary.minimumLevel)
        assertEquals(30, result.teamSummary.maximumLevel)
        assertEquals(6, result.teamSummary.moveDistribution!!.physical)
        assertEquals(
            listOf(RepeatedWeakness(2, 6), RepeatedWeakness(4, 6)),
            result.defensiveProfile!!.repeatedWeaknesses,
        )
    }

    @Test
    fun activeTypeChartMutationChangesCoverageWithoutChangingRomIdentity() {
        val party = listOf(owned(speciesId = 1, level = 12, moveIds = listOf(10), movePp = listOf(10)))
        val baseline = catalog()
        val mutated = baseline.copy(
            typeChart = baseline.typeChart
                .filterNot { it.attackingTypeId == 1 && it.defendingTypeId == 2 } +
                TypeMatchup(1, 2, 200),
        )

        val before = PartyAnalyzer.analyze(party, baseline)
        val after = PartyAnalyzer.analyze(party, mutated)

        assertEquals(baseline.romSha256, mutated.romSha256)
        assertEquals(
            OffensiveCoverageOutcome.NO_EFFECTIVE_KNOWN_OPTION,
            before.offensiveCoverage!!.types.single { it.defendingTypeId == 2 }.outcome,
        )
        assertEquals(
            OffensiveCoverageOutcome.SUPER_EFFECTIVE,
            after.offensiveCoverage!!.types.single { it.defendingTypeId == 2 }.outcome,
        )
    }

    @Test
    fun emptyTypeChartWithholdsCoverageInsteadOfInventingNeutralMatchups() {
        val party = listOf(owned(speciesId = 1, level = 12, moveIds = listOf(10), movePp = listOf(10)))
        val result = PartyAnalyzer.analyze(party, catalog().copy(typeChart = emptyList()))

        assertNull(result.offensiveCoverage)
        assertNull(result.defensiveProfile)
        assertEquals(MoveCategoryDistribution(1, 0, 0, 0), result.teamSummary.moveDistribution)
        assertEquals(1, result.development.evolutionOpportunities.size)
    }

    @Test
    fun provenTypedAbilityModifierChangesOnlyItsAffectedDefensiveMatchup() {
        val ability = AbilityRecord(
            id = 9,
            name = CatalogField.available("Ground Guard"),
            mechanics = CatalogField.available(
                listOf(
                    AbilityMechanic(
                        kind = AbilityMechanicKind.MULTIPLIER,
                        label = "Incoming Ground damage",
                        value = "No damage",
                        numerator = 0,
                        denominator = 1,
                        conditions = listOf(
                            AbilityMechanicCondition(
                                kind = AbilityMechanicConditionKind.ATTACKING_MOVE_TYPE,
                                value = 4,
                                label = "Ground-type move",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val withMechanic = catalog(ability = ability)
        val withoutMechanic = withMechanic.copy(
            abilitiesById = mapOf(9 to ability.copy(mechanics = CatalogField.notFound("not proven"))),
        )
        val member = owned(speciesId = 1, level = 12, abilityId = 9, moveIds = listOf(10), movePp = listOf(10))

        val applied = PartyAnalyzer.analyze(listOf(member), withMechanic).defensiveProfile!!.members.single()
        val withheld = PartyAnalyzer.analyze(listOf(member), withoutMechanic).defensiveProfile!!.members.single()

        assertEquals(listOf(2), applied.weaknessTypeIds)
        assertEquals(listOf(4), applied.immunityTypeIds)
        assertEquals(listOf(AppliedAbilityModifier(9, 4, 0, 1)), applied.abilityModifiers)
        assertEquals(listOf(2, 4), withheld.weaknessTypeIds)
        assertEquals(emptyList<Int>(), withheld.immunityTypeIds)
        assertEquals(emptyList<AppliedAbilityModifier>(), withheld.abilityModifiers)
        assertEquals(applied.resistanceTypeIds, withheld.resistanceTypeIds)
    }

    private fun catalog(ability: AbilityRecord? = null): ParsedCatalog = ParsedCatalog(
        romSha256 = "a".repeat(64),
        family = EngineFamily.EMERALD,
        platform = Platform.GBA,
        speciesById = mapOf(
            1 to species(
                id = 1,
                typeIds = listOf(1),
                evolutions = listOf(EvolutionEdge(targetSpeciesId = 2, methodId = 4, parameter = 16)),
                learnset = listOf(
                    LearnsetEntry(level = 1, moveId = 10),
                    LearnsetEntry(level = 14, moveId = 12),
                    LearnsetEntry(level = 20, moveId = 13),
                ),
            ),
            2 to species(id = 2, typeIds = listOf(1), evolutions = emptyList(), learnset = emptyList()),
        ),
        movesById = mapOf(
            10 to move(10, 1, MoveCategory.PHYSICAL, 40),
            11 to move(11, 1, MoveCategory.STATUS, 0),
            12 to move(12, 3, MoveCategory.SPECIAL, 50),
            13 to move(13, 4, MoveCategory.PHYSICAL, 60),
        ),
        typesById = mapOf(
            1 to type(1, "FIRE"),
            2 to type(2, "WATER"),
            3 to type(3, "GRASS"),
            4 to type(4, "GROUND"),
        ),
        abilitiesById = ability?.let { mapOf(it.id to it) }.orEmpty(),
        typeChart = listOf(
            TypeMatchup(1, 2, 50),
            TypeMatchup(1, 3, 200),
            TypeMatchup(2, 1, 200),
            TypeMatchup(2, 3, 50),
            TypeMatchup(3, 1, 50),
            TypeMatchup(3, 2, 200),
            TypeMatchup(4, 1, 200),
        ),
    )

    private fun species(
        id: Int,
        typeIds: List<Int>,
        evolutions: List<EvolutionEdge>,
        learnset: List<LearnsetEntry>,
    ) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(id),
        name = CatalogField.available("SPECIES $id"),
        typeIds = CatalogField.available(typeIds),
        baseStats = CatalogField.notFound("fixture"),
        sprite = CatalogField.notFound("fixture"),
        evolutionEdges = CatalogField.available(evolutions),
        learnset = CatalogField.available(learnset),
        abilityIds = CatalogField.available(listOf(9)),
    )

    private fun move(id: Int, typeId: Int, category: MoveCategory, power: Int) = MoveRecord(
        id = id,
        name = CatalogField.available("MOVE $id"),
        typeId = CatalogField.available(typeId),
        category = CatalogField.available(category),
        power = CatalogField.available(power),
        accuracy = CatalogField.available(100),
        pp = CatalogField.available(10),
    )

    private fun type(id: Int, name: String) = TypeRecord(id, CatalogField.available(name))

    private fun owned(
        speciesId: Int,
        level: Int,
        moveIds: List<Int>,
        movePp: List<Int>,
        stableLocation: String = "party:0",
        currentHp: Int = 20,
        maximumHp: Int = 30,
        status: Long = 0,
        abilityId: Int? = null,
    ) = OwnedIndividual(
        stableLocation = stableLocation,
        speciesId = speciesId,
        level = level,
        details = PartyMemberDetails(
            currentHp = currentHp,
            maximumHp = maximumHp,
            status = status,
            abilityId = abilityId,
            moveIds = List(4) { slot -> moveIds.getOrElse(slot) { 0 } },
            movePp = List(4) { slot -> movePp.getOrElse(slot) { 0 } },
        ),
    )
}
