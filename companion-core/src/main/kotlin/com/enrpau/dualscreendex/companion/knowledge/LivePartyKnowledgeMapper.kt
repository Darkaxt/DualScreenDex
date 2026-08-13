package com.enrpau.dualscreendex.companion.knowledge

import com.darkaxt.dualdex.save.OwnedIndividual
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

object LivePartyKnowledgeMapper {
    fun merge(
        previous: KnowledgeLedger,
        catalog: ParsedCatalog,
        party: List<OwnedIndividual>,
        generation: Int,
    ): KnowledgeLedger {
        val liveOwned = party.mapNotNull { individual ->
            if (individual.speciesId !in catalog.speciesById) return@mapNotNull null
            OwnedPokemon(
                stableKey = individual.stableLocation,
                speciesId = individual.speciesId,
                generation = generation,
                level = individual.level ?: 0,
                ivs = individual.ivs.orEmpty(),
                dvs = individual.dvs.orEmpty(),
                captureBallId = individual.captureBallId,
                isEgg = individual.isEgg,
                party = true,
            )
        }
        val liveSpecies = liveOwned.filterNot(OwnedPokemon::isEgg).mapTo(linkedSetOf(), OwnedPokemon::speciesId)
        val caught = previous.caughtSpecies + liveSpecies
        return previous.copy(
            seenSpecies = previous.seenSpecies + caught,
            caughtSpecies = caught,
            owned = previous.owned.filterNot(OwnedPokemon::party) + liveOwned,
            teamSpecies = liveSpecies,
        )
    }
}
