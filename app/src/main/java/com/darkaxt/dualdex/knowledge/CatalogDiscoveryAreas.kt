package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog

/** All catalog-validated areas whose geography can be revealed, including maps without wild encounters. */
internal fun ParsedCatalog.discoverableAreaBaseIds(): Set<Int> = buildSet {
    encounterAreas.forEach { area -> add(area.id / 10) }
    localMaps.maps.forEach { map -> add(map.baseAreaId) }
    worldMaps.regions.forEach { region ->
        region.locations.forEach { location -> addAll(location.baseAreaIds) }
    }
}.filterTo(linkedSetOf()) { it in 0..0xFFFF }
