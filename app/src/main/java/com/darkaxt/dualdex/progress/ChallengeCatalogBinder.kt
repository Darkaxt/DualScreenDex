package com.darkaxt.dualdex.progress

enum class PortableChallengeBinding {
    BADGE_COUNT,
    ALL_BADGES,
    REGIONAL_COLLECTION,
    AREA_COLLECTIBLES,
    GYM_LEADER_NO_ITEMS,
    MINIGAME_RESULT,
}

data class PortableChallengeTemplate(
    val key: String,
    val title: String,
    val description: String,
    val category: ChallengeCategory,
    val portabilityTier: Int,
    val requiredCapabilities: Set<String>,
    val requiredCatalogRoles: Set<String>,
    val requiredAdapters: Set<String>,
    val requiredTemporalWindow: String,
    val organicSafe: Boolean,
    val progressionGroup: String? = null,
    val progressionRank: Int? = null,
    val binding: PortableChallengeBinding,
    val sourceInspiration: String,
)

data class AreaCollectibleBinding(
    val key: String,
    val displayName: String,
    val poiKeys: Set<String>,
    val baseAreaId: Int? = null,
) {
    init {
        require(key.isNotBlank() && displayName.isNotBlank() && poiKeys.isNotEmpty())
    }
}

data class GymLeaderBinding(val key: String, val displayName: String) {
    init {
        require(key.isNotBlank() && displayName.isNotBlank())
    }
}

data class ChallengeCatalogBindings(
    val badgeCount: Int? = null,
    val regionalSpeciesIds: Set<Int> = emptySet(),
    val areaCollectibles: List<AreaCollectibleBinding> = emptyList(),
    val gymLeaders: List<GymLeaderBinding> = emptyList(),
    val provenAdapters: Set<String> = emptySet(),
    val provenTemporalWindows: Set<String> = setOf("PLAYTHROUGH"),
) {
    init {
        require(badgeCount == null || badgeCount > 0)
        require(regionalSpeciesIds.all { it > 0 })
        require(areaCollectibles.map(AreaCollectibleBinding::key).distinct().size == areaCollectibles.size)
        require(gymLeaders.map(GymLeaderBinding::key).distinct().size == gymLeaders.size)
        require(provenAdapters.all { it.matches(ADAPTER_TOKEN) })
        require(provenTemporalWindows.all { it in TEMPORAL_WINDOWS })
    }

    val resolvedCatalogEntities: Set<String> = buildSet {
        if (badgeCount != null) add(BADGE_SEQUENCE)
        if (regionalSpeciesIds.isNotEmpty()) add(REGIONAL_POKEDEX)
        areaCollectibles.forEach { add("$AREA_COLLECTIBLES:${it.key}") }
        gymLeaders.forEach { add("$GYM_LEADER:${it.key}") }
        if (provenAdapters.any { it.startsWith("MINIGAME:") }) add("MINIGAME")
    }

    companion object {
        const val BADGE_SEQUENCE = "BADGE_SEQUENCE"
        const val REGIONAL_POKEDEX = "REGIONAL_POKEDEX"
        const val AREA_COLLECTIBLES = "AREA_COLLECTIBLES"
        const val GYM_LEADER = "GYM_LEADER"
        private val ADAPTER_TOKEN = Regex("[A-Z_]+:[a-z0-9][a-z0-9-]{0,63}")
        private val TEMPORAL_WINDOWS = setOf("PLAYTHROUGH", "BATTLE_EPOCH", "AREA_EPOCH", "SESSION_EPOCH", "GAME_SPECIFIC")
    }
}

object ChallengeCatalogBinder {
    fun bind(
        templates: List<PortableChallengeTemplate>,
        bindings: ChallengeCatalogBindings,
    ): List<ChallengeDefinition> = buildList {
        templates.forEach { template ->
            if (template.requiredTemporalWindow !in bindings.provenTemporalWindows) return@forEach
            when (template.binding) {
                PortableChallengeBinding.BADGE_COUNT -> bindings.badgeCount
                    ?.takeIf { it > 0 }
                    ?.let {
                        add(template.definition(predicate = ChallengePredicate.CountAtLeast("trainer.badges", 1)))
                    }
                PortableChallengeBinding.ALL_BADGES -> bindings.badgeCount
                    ?.takeIf { it > 0 }
                    ?.let { count ->
                        add(template.definition(predicate = ChallengePredicate.CountAtLeast("trainer.badges", count.toLong())))
                    }
                PortableChallengeBinding.REGIONAL_COLLECTION -> bindings.regionalSpeciesIds
                    .takeIf(Set<Int>::isNotEmpty)
                    ?.let { speciesIds ->
                        add(
                            template.definition(
                                predicate = ChallengePredicate.SetContainsAll(
                                    "pokedex.caughtSpeciesIds",
                                    speciesIds.map(Int::toString).toSet(),
                                ),
                            ),
                        )
                    }
                PortableChallengeBinding.AREA_COLLECTIBLES -> bindings.areaCollectibles.sortedBy(AreaCollectibleBinding::key).forEach { area ->
                    add(
                        template.definition(
                            keySuffix = area.key,
                            descriptionName = area.displayName,
                            predicate = ChallengePredicate.SetContainsAll("map.collectedPoiKeys", area.poiKeys),
                            catalogEntity = "${ChallengeCatalogBindings.AREA_COLLECTIBLES}:${area.key}",
                            knowledgeEntity = "AREA:${area.key}",
                        ),
                    )
                }
                PortableChallengeBinding.GYM_LEADER_NO_ITEMS -> bindings.gymLeaders.sortedBy(GymLeaderBinding::key).forEach { leader ->
                    add(
                        template.definition(
                            keySuffix = leader.key,
                            descriptionName = leader.displayName,
                            predicate = ChallengePredicate.All(
                                listOf(
                                    ChallengePredicate.SetContains("battle.defeatedLeaders", leader.key),
                                    ChallengePredicate.BooleanFact("battle.noItems:${leader.key}"),
                                ),
                            ),
                            catalogEntity = "${ChallengeCatalogBindings.GYM_LEADER}:${leader.key}",
                        ),
                    )
                }
                PortableChallengeBinding.MINIGAME_RESULT -> bindings.provenAdapters
                    .filter { it.startsWith("MINIGAME:") }
                    .sorted()
                    .forEach { adapter ->
                        val key = adapter.substringAfter(':')
                        add(
                            template.definition(
                                keySuffix = key,
                                descriptionName = key.replace('-', ' '),
                                predicate = ChallengePredicate.BooleanFact("adapter.$adapter.complete"),
                                adapter = adapter,
                            ),
                        )
                    }
            }
        }
    }

    private fun PortableChallengeTemplate.definition(
        predicate: ChallengePredicate,
        keySuffix: String? = null,
        descriptionName: String? = null,
        catalogEntity: String = requiredCatalogRoles.single(),
        knowledgeEntity: String? = null,
        adapter: String? = null,
    ) = ChallengeDefinition(
        key = listOfNotNull(key, keySuffix).joinToString("-"),
        title = title.replace("{name}", descriptionName.orEmpty()),
        description = description.replace("{name}", descriptionName.orEmpty()),
        category = category,
        requiredCapabilities = requiredCapabilities,
        requiredCatalogEntities = setOf(catalogEntity),
        requiredKnowledgeEntities = setOfNotNull(knowledgeEntity),
        requiredAdapters = setOfNotNull(adapter),
        progressionGroup = progressionGroup,
        progressionRank = progressionRank,
        disclosureScope = knowledgeEntity,
        organicSafe = organicSafe,
        predicate = predicate,
        sourceInspiration = sourceInspiration,
    )
}
