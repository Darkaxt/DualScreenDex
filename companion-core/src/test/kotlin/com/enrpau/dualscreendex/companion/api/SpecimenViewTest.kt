package com.enrpau.dualscreendex.companion.api

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.PartyMemberDetails
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.OwnedIndividualLocation
import com.enrpau.dualscreendex.companion.model.OwnedIndividualLocationKind
import com.enrpau.dualscreendex.companion.model.ResolvedOwnedIndividual
import com.enrpau.dualscreendex.companion.model.ResolvedPokedexProjection
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecimenViewTest {
    @Test
    fun oneCanonicalSpeciesListsAllAndOnlyItsDistinctPartyAndBoxInstances() {
        val duplicateIdentity = "1111111111111111"
        val party = specimen(
            location = "party-0",
            speciesId = 25,
            identity = duplicateIdentity,
            nickname = "SPARK",
        )
        val movedDuplicate = party.copy(stableLocation = "box-0")
        val alternateForm = specimen(
            location = "box-31",
            speciesId = 26,
            identity = "2222222222222222",
            nickname = "VOLT",
        )
        val unrelated = specimen(
            location = "box-32",
            speciesId = 27,
            identity = "3333333333333333",
            nickname = "OTHER",
        )
        val snapshot = AppSnapshot(
            resolvedSaveIdentity = "save-a",
            resolvedOwnedIndividuals = listOf(
                ResolvedOwnedIndividual(party, OwnedIndividualLocation(OwnedIndividualLocationKind.PARTY, slotIndex = 0)),
                ResolvedOwnedIndividual(movedDuplicate, OwnedIndividualLocation(OwnedIndividualLocationKind.BOX, boxIndex = 0, slotIndex = 0)),
                ResolvedOwnedIndividual(alternateForm, OwnedIndividualLocation(OwnedIndividualLocationKind.BOX, boxIndex = 1, slotIndex = 1)),
                ResolvedOwnedIndividual(unrelated, OwnedIndividualLocation(OwnedIndividualLocationKind.BOX, boxIndex = 1, slotIndex = 2)),
            ),
        )

        val view = ApiViewBuilder.specimens(snapshot, catalog(), 25)

        assertEquals(listOf("SPARK", "VOLT"), view.specimens.map { it.nickname })
        assertEquals(listOf("Party · Slot 1", "Box 2 · Slot 2"), view.specimens.map { it.location.label })
        assertEquals(listOf("PARTY", "BOX"), view.specimens.map { it.location.kind })
        assertEquals(9, view.specimens.first().abilityId)
        assertEquals("Static", view.specimens.first().abilityName)
        assertEquals("Resolute", view.specimens.first().nature)
        assertEquals("Thunderbolt", view.specimens.first().moves.first().name)
        assertEquals(listOf(31, 30, 29, 28, 27, 26), view.specimens.first().ivs)
        assertEquals(4.0, view.specimens.first().rarity?.stars)
        assertTrue(view.specimens.map { it.key }.distinct().size == 2)
    }

    @Test
    fun caughtFlagWithoutDecodedOwnedRecordInventsNoSpecimenCard() {
        val snapshot = AppSnapshot(
            resolvedPokedex = ResolvedPokedexProjection(seenSpeciesIds = setOf(25), caughtSpeciesIds = setOf(25)),
            resolvedOwnedIndividuals = emptyList(),
        )

        val state = ApiViewBuilder.state(snapshot, catalog())
        val specimens = ApiViewBuilder.specimens(snapshot, catalog(), 25)

        assertEquals(0, state.speciesState.getValue(25).specimenCount)
        assertTrue(specimens.specimens.isEmpty())
    }

    @Test
    fun unavailableLocalizedNamesPreserveOwnedNumericAndMechanicalData() {
        val base = catalog()
        val unavailable = base.copy(
            speciesById = base.speciesById.mapValues { (_, species) ->
                species.copy(name = CatalogField.notFound("localized species name unavailable"))
            },
            movesById = base.movesById.mapValues { (_, move) ->
                move.copy(name = CatalogField.notFound("localized move name unavailable"))
            },
            abilitiesById = base.abilitiesById.mapValues { (_, ability) ->
                ability.copy(name = CatalogField.notFound("localized ability name unavailable"))
            },
        )
        val individual = specimen("party-0", 25, "6666666666666666", "SPARK")
        val snapshot = AppSnapshot(
            party = listOf(individual),
            resolvedOwnedIndividuals = listOf(
                ResolvedOwnedIndividual(
                    individual,
                    OwnedIndividualLocation(OwnedIndividualLocationKind.PARTY, slotIndex = 0),
                ),
            ),
        )

        val party = ApiViewBuilder.state(snapshot, unavailable).party.first()
        val owned = ApiViewBuilder.specimens(snapshot, unavailable, 25).specimens.single()

        assertEquals(25, party.speciesId)
        assertEquals(null, party.speciesName)
        assertEquals(9, party.abilityId)
        assertEquals(null, party.abilityName)
        assertEquals(85, party.moves.first().moveId)
        assertEquals(null, party.moves.first().name)
        assertEquals(12, party.moves.first().currentPp)
        assertEquals(15, party.moves.first().maximumPp)
        assertEquals(25, owned.speciesId)
        assertEquals(null, owned.speciesName)
        assertEquals(9, owned.abilityId)
        assertEquals(85, owned.moves.first().moveId)
        assertEquals(12, owned.moves.first().currentPp)
        assertEquals(15, owned.moves.first().maximumPp)
    }

    @Test
    fun stableIdentitySurvivesPartyToPcAndPcToPartyMovementWithoutDuplication() {
        val identity = "4444444444444444"
        val partyIndividual = specimen("party-0", 25, identity, "SPARK")
        val boxedIndividual = partyIndividual.copy(stableLocation = "box-62")
        fun view(individual: OwnedIndividual, location: OwnedIndividualLocation) = ApiViewBuilder.specimens(
            AppSnapshot(
                resolvedSaveIdentity = "save-a",
                resolvedOwnedIndividuals = listOf(ResolvedOwnedIndividual(individual, location)),
            ),
            catalog(),
            25,
        )

        val inParty = view(
            partyIndividual,
            OwnedIndividualLocation(OwnedIndividualLocationKind.PARTY, slotIndex = 0),
        )
        val inPc = view(
            boxedIndividual,
            OwnedIndividualLocation(OwnedIndividualLocationKind.BOX, boxIndex = 2, slotIndex = 2),
        )
        val backInParty = view(
            partyIndividual.copy(stableLocation = "party-1"),
            OwnedIndividualLocation(OwnedIndividualLocationKind.PARTY, slotIndex = 1),
        )

        assertEquals(1, inParty.specimens.size)
        assertEquals(1, inPc.specimens.size)
        assertEquals(1, backInParty.specimens.size)
        assertEquals(inParty.specimens.single().key, inPc.specimens.single().key)
        assertEquals(inPc.specimens.single().key, backInParty.specimens.single().key)
        assertEquals("Party · Slot 1", inParty.specimens.single().location.label)
        assertEquals("Box 3 · Slot 3", inPc.specimens.single().location.label)
        assertEquals("Party · Slot 2", backInParty.specimens.single().location.label)
    }

    @Test
    fun gen1AndGen2FallbackSpecimenKeysAreFixedOpaqueAndIdentityBound() {
        val resolved = ResolvedOwnedIndividual(
            specimen("box-31", 25, "5555555555555555", "SPARK").copy(individualIdentity = null),
            OwnedIndividualLocation(OwnedIndividualLocationKind.BOX, boxIndex = 13, slotIndex = 29),
        )
        val snapshot = AppSnapshot(
            resolvedSaveIdentity = "save-identity-" + "x".repeat(256),
            resolvedOwnedIndividuals = listOf(resolved),
        )
        val platforms = listOf(
            EngineFamily.RED_BLUE to Platform.GB,
            EngineFamily.GOLD_SILVER to Platform.GBC,
        )

        platforms.forEach { (family, platform) ->
            val first = ApiViewBuilder.specimens(snapshot, catalog(family, platform), 25).specimens.single().key
            val repeated = ApiViewBuilder.specimens(snapshot, catalog(family, platform), 25).specimens.single().key
            val changedSave = ApiViewBuilder.specimens(
                snapshot.copy(resolvedSaveIdentity = "another-save"),
                catalog(family, platform),
                25,
            ).specimens.single().key

            assertTrue(first.startsWith("fallback:"))
            assertEquals(73, first.length)
            assertEquals(first, repeated)
            assertTrue(first != changedSave)
        }
    }

    private fun specimen(
        location: String,
        speciesId: Int,
        identity: String,
        nickname: String,
    ) = OwnedIndividual(
        stableLocation = location,
        speciesId = speciesId,
        individualIdentity = identity,
        level = 18,
        ivs = listOf(31, 30, 29, 28, 27, 26),
        experience = 9_000,
        details = PartyMemberDetails(
            nickname = nickname,
            gender = 1,
            natureId = 3,
            heldItemId = 12,
            abilityId = 9,
            currentHp = 31,
            maximumHp = 45,
            stats = listOf(45, 28, 22, 38, 30, 26),
            moveIds = listOf(85, 0, 0, 0),
            movePp = listOf(12, 0, 0, 0),
            experienceProgress = .5,
        ),
    )

    private fun catalog(
        family: EngineFamily = EngineFamily.EMERALD,
        platform: Platform = Platform.GBA,
    ) = ParsedCatalog(
        romSha256 = "a".repeat(64),
        family = family,
        platform = platform,
        speciesById = mapOf(
            25 to species(25, 25, "PIKACHU"),
            26 to species(26, 25, "PIKACHU FORM"),
            27 to species(27, 27, "SANDSHREW"),
        ),
        movesById = mapOf(
            85 to MoveRecord(
                id = 85,
                name = CatalogField.available("Thunderbolt"),
                typeId = CatalogField.available(13),
                category = CatalogField.notFound("fixture"),
                power = CatalogField.available(90),
                accuracy = CatalogField.available(100),
                pp = CatalogField.available(15),
            ),
        ),
        typesById = mapOf(13 to TypeRecord(13, CatalogField.available("ELECTRIC"))),
        abilitiesById = mapOf(9 to AbilityRecord(9, CatalogField.available("Static"))),
        naturesById = mapOf(
            3 to NatureRecord(
                id = 3,
                name = "Resolute",
                statModifiers = listOf(1, 0, 0, -1, 0),
                positivePercent = 110,
                negativePercent = 90,
                flavorModifiers = listOf(1, -1, 0, 0, 0),
            ),
        ),
    )

    private fun species(id: Int, dex: Int, name: String) = SpeciesRecord(
        id = id,
        dexNumber = CatalogField.available(dex),
        name = CatalogField.available(name),
        typeIds = CatalogField.available(listOf(13)),
        baseStats = CatalogField.notFound("fixture"),
        sprite = CatalogField.notFound("fixture"),
    )
}
