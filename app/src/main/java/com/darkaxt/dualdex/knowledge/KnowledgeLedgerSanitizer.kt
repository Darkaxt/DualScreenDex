package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

object KnowledgeLedgerSanitizer {
    fun sanitize(ledger: KnowledgeLedger, catalog: ParsedCatalog): KnowledgeLedger {
        val speciesIds = catalog.speciesById.keys
        val moveIds = catalog.movesById.keys
        val areaBaseIds = catalog.discoverableAreaBaseIds()
        val poiKeys = catalog.localMaps.pois.mapTo(hashSetOf()) { it.key }
        val caught = ledger.caughtSpecies.filterTo(linkedSetOf()) { it in speciesIds }
        val observedMoves = ledger.observedMoves.entries.mapNotNull { (speciesId, observations) ->
            if (speciesId !in speciesIds) return@mapNotNull null
            val valid = observations
                .filter { it.moveId in moveIds && it.frequency > 0 }
                .sortedWith(compareByDescending<MoveObservation> { it.frequency }.thenBy { it.moveId })
            speciesId to valid
        }.filter { it.second.isNotEmpty() }.toMap()
        return ledger.copy(
            seenSpecies = ledger.seenSpecies.filterTo(linkedSetOf()) { it in speciesIds } + caught,
            caughtSpecies = caught,
            owned = ledger.owned.filter { it.speciesId in speciesIds },
            teamSpecies = ledger.teamSpecies.filterTo(linkedSetOf()) { it in speciesIds },
            currentAreaBaseId = ledger.currentAreaBaseId?.takeIf { it in areaBaseIds },
            visitedAreaBaseIds = ledger.visitedAreaBaseIds.filterTo(linkedSetOf()) { it in areaBaseIds },
            seenSpeciesByArea = ledger.seenSpeciesByArea.entries.mapNotNull { (areaId, seen) ->
                if (areaId !in areaBaseIds) return@mapNotNull null
                areaId to seen.filterTo(linkedSetOf()) { it in speciesIds }
            }.filter { it.second.isNotEmpty() }.toMap(),
            observedMoves = observedMoves,
            discoveredMatchups = ledger.discoveredMatchups.filter { (key, _) ->
                key.speciesId in speciesIds && key.moveId in moveIds
            },
            knownMoves = ledger.knownMoves.filterTo(linkedSetOf()) { it in moveIds },
            proximityRevealedPoiKeys = ledger.proximityRevealedPoiKeys.filterTo(linkedSetOf()) { it in poiKeys },
            identifiedPoiKeys = ledger.identifiedPoiKeys.filterTo(linkedSetOf()) { it in poiKeys },
            enteredPoiKeys = ledger.enteredPoiKeys.filterTo(linkedSetOf()) { it in poiKeys },
            collectedPoiKeys = ledger.collectedPoiKeys.filterTo(linkedSetOf()) { it in poiKeys },
        )
    }
}
