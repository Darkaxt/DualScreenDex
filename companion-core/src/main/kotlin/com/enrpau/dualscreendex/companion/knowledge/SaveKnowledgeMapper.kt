package com.enrpau.dualscreendex.companion.knowledge

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

    private val GEN3_FAMILIES = setOf(
        EngineFamily.RUBY_SAPPHIRE,
        EngineFamily.EMERALD,
        EngineFamily.FIRERED_LEAFGREEN,
    )
}
