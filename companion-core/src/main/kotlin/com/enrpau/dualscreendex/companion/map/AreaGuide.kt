package com.enrpau.dualscreendex.companion.map

data class AreaGuide(
    val trackedAreaBaseId: Int?,
    val areas: List<AreaGuideArea>,
)

data class AreaGuideArea(
    val baseAreaId: Int,
    val name: String,
    val overview: AreaGuideOverview,
    val encounters: List<AreaGuideEncounterGroup>,
    val placesAndServices: List<AreaGuidePoint>,
    val trainersAndPeople: List<AreaGuidePoint>,
    val items: List<AreaGuidePoint>,
    val objectives: List<AreaGuideObjective>,
)

data class AreaGuideOverview(
    val knownPointCount: Int,
    val totalPointCount: Int?,
    val collectedItemCount: Int,
    val exits: List<AreaGuideExit>,
)

data class AreaGuideExit(
    val baseAreaId: Int,
    val name: String,
)

data class AreaGuideEncounterGroup(
    val name: String?,
    val windows: List<String>,
    val species: List<AreaGuideEncounterSpecies>,
)

data class AreaGuideEncounterSpecies(
    val speciesId: Int,
    val name: String,
    val minimumLevel: Int,
    val maximumLevel: Int,
    val ratePercent: Int?,
)

enum class AreaGuidePointCategory {
    PLACE,
    SERVICE,
    AVAILABLE_ITEM,
    COLLECTED_ITEM,
    UNKNOWN,
}

enum class AreaGuidePointState {
    SILHOUETTE,
    IDENTIFIED,
    COLLECTED,
}

data class AreaGuidePoint(
    val key: String,
    val localMapKey: String,
    val baseAreaId: Int,
    val tileX: Int,
    val tileY: Int,
    val category: AreaGuidePointCategory,
    val state: AreaGuidePointState,
    val label: String?,
    val service: String?,
    val itemId: Int?,
    val destinationBaseAreaId: Int?,
)

data class AreaGuideObjective(
    val key: String,
    val title: String,
)
