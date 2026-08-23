package com.enrpau.dualscreendex.companion.knowledge

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveSnapshot
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

object SaveKnowledgeMapper {
    fun merge(previous: KnowledgeLedger, catalog: ParsedCatalog, snapshot: SaveSnapshot): KnowledgeLedger {
        require(snapshot.romIdentity.equals(catalog.romSha256, ignoreCase = true)) {
            "SaveRAM snapshot belongs to another ROM catalog"
        }
        val speciesByDex = catalog.speciesById.values
            .mapNotNull { species -> species.dexNumber.value?.let { dex -> dex to species.id } }
            .groupBy({ it.first }, { it.second })
        val seen = snapshot.seenDexNumbers.flatMap { speciesByDex[it].orEmpty() }.toSet()
        val caughtFromFlags = snapshot.caughtDexNumbers.flatMap { speciesByDex[it].orEmpty() }.toSet()
        val partyKeys = snapshot.party.mapTo(mutableSetOf(), OwnedIndividual::stableLocation)
        val owned = snapshot.allIndividuals.mapNotNull { individual ->
            if (individual.speciesId !in catalog.speciesById) return@mapNotNull null
            OwnedPokemon(
                stableKey = individual.stableLocation,
                speciesId = individual.speciesId,
                generation = snapshot.saveGeneration,
                level = individual.level ?: 0,
                ivs = individual.ivs.orEmpty(),
                dvs = individual.dvs.orEmpty(),
                captureBallId = individual.captureBallId,
                isEgg = individual.isEgg,
                party = individual.stableLocation in partyKeys,
            )
        }
        val caught = caughtFromFlags + owned.filterNot(OwnedPokemon::isEgg).map(OwnedPokemon::speciesId)
        return previous.copy(
            seenSpecies = previous.seenSpecies + previous.caughtSpecies + seen + caught,
            caughtSpecies = previous.caughtSpecies + caught,
            owned = owned,
            teamSpecies = owned.filter { it.party && !it.isEgg }.mapTo(linkedSetOf(), OwnedPokemon::speciesId),
            trainerCardUnlocked = previous.trainerCardUnlocked || owned.any(OwnedPokemon::party),
            currentAreaBaseId = snapshot.currentArea?.baseId,
            visitedAreaBaseIds = previous.visitedAreaBaseIds + listOfNotNull(snapshot.currentArea?.baseId),
        )
    }
}
