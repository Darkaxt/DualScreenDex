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

    private fun catalog() = ParsedCatalog(
        romSha256 = "a".repeat(64),
        family = EngineFamily.EMERALD,
        platform = Platform.GBA,
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
