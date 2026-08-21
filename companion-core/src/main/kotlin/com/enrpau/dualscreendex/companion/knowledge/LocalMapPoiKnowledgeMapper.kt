package com.enrpau.dualscreendex.companion.knowledge

import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import kotlin.math.abs

object LocalMapPoiKnowledgeMapper {
    fun mergeEventFlags(
        previous: KnowledgeLedger,
        catalog: ParsedCatalog,
        setFlagIds: Set<Int>?,
    ): KnowledgeLedger {
        if (setFlagIds == null) return previous
        val collected = catalog.localMaps.pois.asSequence()
            .filter { it.item?.collectionFlagId in setFlagIds }
            .map { it.key }
            .toSet()
        if (collected.isEmpty()) return previous
        return previous.copy(
            identifiedPoiKeys = previous.identifiedPoiKeys + collected,
            collectedPoiKeys = previous.collectedPoiKeys + collected,
        )
    }

    fun mergeProximity(
        previous: KnowledgeLedger,
        catalog: ParsedCatalog,
        baseAreaId: Int,
        tileX: Int,
        tileY: Int,
    ): KnowledgeLedger {
        val newlyRevealed = catalog.localMaps.pois.asSequence()
            .filter { it.baseAreaId == baseAreaId }
            .filter { it.organicVisibility != LocalMapPoiOrganicVisibility.VISIBLE }
            .filter { it.key !in previous.collectedPoiKeys }
            .filter { abs(it.tileX - tileX) <= 1 && abs(it.tileY - tileY) <= 1 }
            .map { it.key }
            .toSet()
        if (newlyRevealed.isEmpty()) return previous
        return previous.copy(
            proximityRevealedPoiKeys = previous.proximityRevealedPoiKeys + newlyRevealed,
        )
    }
}
