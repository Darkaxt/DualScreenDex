package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.battle.BattleCapability
import com.darkaxt.dualdex.battle.BattleMemorySample
import com.darkaxt.dualdex.battle.BattleMonSnapshot
import com.darkaxt.dualdex.battle.BattleTarget
import com.darkaxt.dualdex.battle.CapabilityState
import com.darkaxt.dualdex.battle.ResolvedBattleLayout
import com.darkaxt.dualdex.battle.TargetMode
import com.enrpau.dualscreendex.companion.battle.AppliedDamageCondition
import com.enrpau.dualscreendex.companion.battle.CriticalRule
import com.enrpau.dualscreendex.companion.battle.DamageForecast
import com.enrpau.dualscreendex.companion.battle.DamageForecastConfidence
import com.enrpau.dualscreendex.companion.battle.DamageFormulaEvidence
import com.enrpau.dualscreendex.companion.battle.InclusiveRange
import com.enrpau.dualscreendex.companion.battle.SemanticProof
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageForecastAssemblerTest {
    @Test
    fun `double battle uses live command owner and selected target`() {
        val sample = sample(owner = 2, target = 1)
        val assembled = DamageForecastAssembler.input(sample, catalog(), KnowledgeMode.DISCOVERED, formula())
        val forecast = DamageForecastMemoizer().forecast(assembled)

        assertEquals(2, assembled?.attacker?.battlerIndex)
        assertEquals(3, assembled?.target?.battlerIndex)
        assertEquals(10, assembled?.move?.id)
        assertEquals(InclusiveRange(35, 42), (forecast as DamageForecast.Available).damage)
    }

    @Test
    fun `manual target change changes immutable input and forecast`() {
        val first = DamageForecastAssembler.input(sample(owner = 2, target = 0), catalog(), KnowledgeMode.DISCOVERED, formula())
        val second = DamageForecastAssembler.input(sample(owner = 2, target = 1), catalog(), KnowledgeMode.DISCOVERED, formula())

        assertNotEquals(first, second)
        assertNotEquals(
            (DamageForecastMemoizer().forecast(first) as DamageForecast.Available).damage,
            (DamageForecastMemoizer().forecast(second) as DamageForecast.Available).damage,
        )
    }

    @Test
    fun `memoizer reuses result for irrelevant polling samples`() {
        val memoizer = DamageForecastMemoizer()
        val input = DamageForecastAssembler.input(sample(owner = 2, target = 1), catalog(), KnowledgeMode.DISCOVERED, formula())

        val first = memoizer.forecast(input)
        val cpuAfterFirst = memoizer.calculationCpuNanos
        val second = memoizer.forecast(input)

        assertSame(first, second)
        assertEquals(1, memoizer.recomputationCount)
        assertTrue(cpuAfterFirst > 0)
        assertEquals(cpuAfterFirst, memoizer.calculationCpuNanos)
        assertEquals(1, memoizer.retainedInputCount)
    }

    @Test
    fun `late stable attacker and target changes replace one forecast without stale flicker`() {
        val memoizer = DamageForecastMemoizer()
        val firstInput = DamageForecastAssembler.input(sample(owner = 2, target = 0), catalog(), KnowledgeMode.DISCOVERED, formula())
        val changedInput = DamageForecastAssembler.input(sample(owner = 2, target = 1), catalog(), KnowledgeMode.DISCOVERED, formula())

        val first = memoizer.forecast(firstInput)
        val changed = memoizer.forecast(changedInput)
        val stable = memoizer.forecast(changedInput)

        assertNotEquals(first, changed)
        assertSame(changed, stable)
        assertEquals(2, memoizer.recomputationCount)
        assertEquals(1, memoizer.retainedInputCount)
    }

    @Test
    fun `unknown formula or unresolved item semantics fail closed`() {
        assertTrue(
            DamageForecastMemoizer().forecast(
                DamageForecastAssembler.input(sample(owner = 2, target = 1), catalog(), KnowledgeMode.ORGANIC, null),
            ) is DamageForecast.Absent,
        )
        val held = sample(owner = 2, target = 1).let { value ->
            value.copy(
                battlers = value.battlers.map { if (it.battlerIndex == 2) it.copy(heldItemId = 7) else it },
            )
        }
        assertTrue(
            DamageForecastMemoizer().forecast(
                DamageForecastAssembler.input(held, catalog(), KnowledgeMode.DISCOVERED, formula()),
            ) is DamageForecast.Absent,
        )
    }

    @Test
    fun `proven weather type semantics remain bounded`() {
        val base = catalog()
        val fireCatalog = base.copy(
            movesById = mapOf(
                10 to base.movesById.getValue(10).copy(typeId = CatalogField.available(10)),
            ),
            typesById = mapOf(
                10 to TypeRecord(
                    id = 10,
                    name = CatalogField.available("Feu"),
                    semanticRole = CatalogField.available(TypeSemanticRole.FIRE),
                ),
            ),
        )

        val assembled = requireNotNull(
            DamageForecastAssembler.input(
                sample(owner = 2, target = 1),
                fireCatalog,
                KnowledgeMode.DISCOVERED,
                formula(),
            ),
        )

        assertTrue(assembled.unboundedUnknowns.isEmpty())
        assertEquals(AppliedDamageCondition.WEATHER, assembled.boundedAlternatives.single().kind)
    }

    @Test
    fun `unproven weather type semantics fail closed`() {
        val base = catalog()
        val customTypeCatalog = base.copy(
            movesById = mapOf(
                10 to base.movesById.getValue(10).copy(typeId = CatalogField.available(18)),
            ),
            typesById = mapOf(
                18 to TypeRecord(18, CatalogField.available("Custom")),
            ),
        )

        val assembled = requireNotNull(
            DamageForecastAssembler.input(
                sample(owner = 2, target = 1),
                customTypeCatalog,
                KnowledgeMode.DISCOVERED,
                formula(),
            ),
        )

        assertEquals(
            listOf("Weather interaction for this move's type is unresolved."),
            assembled.unboundedUnknowns,
        )
        assertTrue(DamageForecastMemoizer().forecast(assembled) is DamageForecast.Absent)
    }

    @Test
    fun `native fire and water display strings do not change proven weather forecasts`() {
        listOf(
            TypeSemanticRole.FIRE to listOf("ほのお", "화염"),
            TypeSemanticRole.WATER to listOf("みず", "물"),
        ).forEach { (role, names) ->
            val withoutDisplayName = requireNotNull(
                DamageForecastAssembler.input(
                    sample(owner = 2, target = 1),
                    nativeTypeCatalog(31, CatalogField.notApplicable("stored in language overlay"), role),
                    KnowledgeMode.DISCOVERED,
                    formula(),
                ),
            )
            val reference = DamageForecastMemoizer().forecast(withoutDisplayName) as DamageForecast.Available
            assertTrue(withoutDisplayName.unboundedUnknowns.isEmpty())
            val weather = withoutDisplayName.boundedAlternatives.single()
            assertEquals(AppliedDamageCondition.WEATHER, weather.kind)
            assertEquals(1, weather.minimumNumerator)
            assertEquals(3, weather.maximumNumerator)
            assertEquals(2, weather.denominator)
            assertEquals(DamageForecastConfidence.BOUNDED, reference.confidence)
            assertEquals(200, reference.effectivenessPercent)

            names.forEach { name ->
                val native = requireNotNull(
                    DamageForecastAssembler.input(
                        sample(owner = 2, target = 1),
                        nativeTypeCatalog(31, CatalogField.available(name), role),
                        KnowledgeMode.DISCOVERED,
                        formula(),
                    ),
                )
                assertEquals(name, withoutDisplayName, native)
                assertEquals(name, reference, DamageForecastMemoizer().forecast(native))
            }
        }
    }

    @Test
    fun `native type names and familiar numeric ids cannot substitute for unresolved semantics`() {
        listOf(0, 31).forEach { typeId ->
            listOf("ほのお", "화염", "みず", "물", "？？？", "???").forEach { name ->
                val assembled = requireNotNull(
                    DamageForecastAssembler.input(
                        sample(owner = 2, target = 1),
                        nativeTypeCatalog(typeId, CatalogField.available(name), role = null),
                        KnowledgeMode.DISCOVERED,
                        formula(),
                    ),
                )

                assertEquals(typeId, assembled.move.typeId)
                assertEquals(200, assembled.effectivenessPercent)
                assertTrue(assembled.boundedAlternatives.isEmpty())
                assertEquals(
                    listOf("Weather interaction for this move's type is unresolved."),
                    assembled.unboundedUnknowns,
                )
                assertTrue("typeId=$typeId name=$name", DamageForecastMemoizer().forecast(assembled) is DamageForecast.Absent)
            }
        }
    }

    private fun nativeTypeCatalog(typeId: Int, name: CatalogField<String>, role: TypeSemanticRole?): ParsedCatalog {
        val base = catalog()
        return base.copy(
            movesById = mapOf(10 to base.movesById.getValue(10).copy(typeId = CatalogField.available(typeId))),
            typesById = base.typesById + (typeId to TypeRecord(
                id = typeId,
                name = name,
                semanticRole = role?.let { CatalogField.available(it) } ?: CatalogField.notFound("unresolved fixture role"),
            )),
            typeChart = listOf(TypeMatchup(typeId, 0, 200)),
        )
    }

    private fun sample(owner: Int, target: Int): BattleMemorySample {
        val battlers = listOf(
            mon(0, 0, species = 1, attack = 60, defense = 60),
            mon(1, 1, species = 2, attack = 50, defense = 50),
            mon(2, 2, species = 1, attack = 120, defense = 80),
            mon(3, 3, species = 2, attack = 50, defense = 100),
        )
        return BattleMemorySample(
            layout = ResolvedBattleLayout(0, 0, 0, 0, 0, 0, 4),
            battlers = battlers,
            opponents = listOf(battlers[1], battlers[3]),
            selectedMoveId = 10,
            target = BattleTarget(target, TargetMode.AUTOMATIC),
            capabilities = mapOf(BattleCapability.SELECTED_MOVE to CapabilityState.AVAILABLE),
            commandOwnerBattlerIndex = owner,
        )
    }

    private fun mon(index: Int, position: Int, species: Int, attack: Int, defense: Int) = BattleMonSnapshot(
        battlerIndex = index,
        position = position,
        speciesId = species,
        level = 50,
        hp = 100,
        maxHp = 100,
        ivs = listOf(10, 10, 10, 10, 10, 10),
        moves = listOf(10),
        pp = listOf(35),
        typeIds = listOf(0),
        abilityId = 0,
        personality = index.toLong(),
        attack = attack,
        defense = defense,
        speed = 70,
        specialAttack = attack,
        specialDefense = defense,
        status = 0,
    )

    private fun catalog() = ParsedCatalog(
        romSha256 = "a".repeat(64),
        family = EngineFamily.EMERALD,
        platform = Platform.GBA,
        speciesById = mapOf(
            1 to species(1),
            2 to species(2),
        ),
        movesById = mapOf(
            10 to MoveRecord(
                id = 10,
                name = CatalogField.available("Tackle"),
                typeId = CatalogField.available(0),
                category = CatalogField.available(MoveCategory.PHYSICAL),
                power = CatalogField.available(50),
                accuracy = CatalogField.available(100),
                pp = CatalogField.available(35),
                effectId = CatalogField.available(0),
            ),
        ),
        typesById = mapOf(
            0 to TypeRecord(
                id = 0,
                name = CatalogField.available("Normal"),
                semanticRole = CatalogField.available(TypeSemanticRole.NORMAL),
            ),
        ),
        typeChart = listOf(TypeMatchup(0, 0, 100)),
    )

    private fun species(id: Int) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(id),
        name = CatalogField.available("Species $id"),
        typeIds = CatalogField.available(listOf(0)),
        baseStats = CatalogField.notFound("not needed"),
        sprite = CatalogField.notFound("not needed"),
    )

    private fun formula() = DamageFormulaEvidence(
        key = "decoded-standard",
        proof = SemanticProof.CONTROL_VALIDATED,
        randomNumerators = 85..100,
        randomDenominator = 100,
        criticalRule = CriticalRule.DAMAGE_MULTIPLIER,
        criticalNumerator = 2,
        criticalDenominator = 1,
    )
}
