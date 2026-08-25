package com.enrpau.dualscreendex.companion.knowledge

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveSnapshot
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform

object SaveKnowledgeMapper {
    fun pokedexFlagNumbersBySpeciesId(catalog: ParsedCatalog): Map<Int, Int> {
        val retailRegionalDex = catalog.platform == Platform.GBA &&
            catalog.family in GEN3_FAMILIES &&
            catalog.speciesById[1]?.dexNumber?.value?.let { it != 1 } == true
        return catalog.speciesById.values.mapNotNull { species ->
            val flagNumber = if (retailRegionalDex) {
                when (species.id) {
                    in 1..251 -> species.id
                    in 277..411 -> species.id - 25
                    else -> null
                }
            } else {
                species.dexNumber.value
            }
            flagNumber?.takeIf { it > 0 }?.let { species.id to it }
        }.toMap()
    }

    fun speciesIdsForPokedexFlags(catalog: ParsedCatalog, dexNumbers: Set<Int>): Set<Int> {
        val flagNumbers = pokedexFlagNumbersBySpeciesId(catalog)
        val speciesByDex = flagNumbers.entries
            .map { (speciesId, flagNumber) -> flagNumber to speciesId }
            .groupBy({ it.first }, { it.second })
        return dexNumbers.mapNotNullTo(linkedSetOf()) { flagNumber ->
            speciesByDex[flagNumber]?.minWithOrNull(
                compareBy<Int> { catalog.speciesById[it]?.formId != 0 }
                    .thenBy { it },
            )
        }
    }

    fun merge(previous: KnowledgeLedger, catalog: ParsedCatalog, snapshot: SaveSnapshot): KnowledgeLedger {
        require(snapshot.romIdentity.equals(catalog.romSha256, ignoreCase = true)) {
            "SaveRAM snapshot belongs to another ROM catalog"
        }
        val seen = speciesIdsForPokedexFlags(catalog, snapshot.seenDexNumbers)
        val caughtFromFlags = speciesIdsForPokedexFlags(catalog, snapshot.caughtDexNumbers)
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

    private val GEN3_FAMILIES = setOf(
        EngineFamily.RUBY_SAPPHIRE,
        EngineFamily.EMERALD,
        EngineFamily.FIRERED_LEAFGREEN,
    )
}
