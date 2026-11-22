package com.darkaxt.dualdex.progress

import com.google.gson.Gson

object PortableChallengeCatalog {
    private val gson = Gson()

    fun decode(bytes: ByteArray): List<ChallengeDefinition> {
        val stored = runCatching {
            gson.fromJson(bytes.toString(Charsets.UTF_8), StoredCatalog::class.java)
        }.getOrNull() ?: return emptyList()
        if (stored.schema != 1) return emptyList()
        return stored.challenges.mapNotNull { challenge ->
            if (
                challenge.key.isBlank() || challenge.title.isBlank() || challenge.description.isBlank() ||
                challenge.requiredCapabilities.isEmpty() || challenge.operator != "COUNT_AT_LEAST" ||
                challenge.metric.isBlank() || challenge.target <= 0 ||
                !validProgression(challenge.progressionGroup, challenge.progressionRank)
            ) return@mapNotNull null
            val category = runCatching { ChallengeCategory.valueOf(challenge.category) }.getOrNull()
                ?: return@mapNotNull null
            ChallengeDefinition(
                key = challenge.key,
                title = challenge.title,
                description = challenge.description,
                category = category,
                requiredCapabilities = challenge.requiredCapabilities.toSet(),
                progressionGroup = challenge.progressionGroup,
                progressionRank = challenge.progressionRank,
                organicSafe = challenge.organicSafe,
                predicate = ChallengePredicate.CountAtLeast(challenge.metric, challenge.target),
                sourceInspiration = challenge.sourceInspiration,
            )
        }.distinctBy { it.key }
    }

    fun decodeTemplates(bytes: ByteArray): List<PortableChallengeTemplate> {
        val stored = runCatching {
            gson.fromJson(bytes.toString(Charsets.UTF_8), StoredTemplateCatalog::class.java)
        }.getOrNull() ?: return emptyList()
        if (stored.schema != 1) return emptyList()
        return stored.templates.mapNotNull { template ->
            if (
                template.key.isBlank() || template.title.isBlank() || template.description.isBlank() ||
                template.portabilityTier !in 2..3 || template.requiredCapabilities.isEmpty() ||
                template.requiredCatalogRoles.isEmpty() ||
                template.requiredTemporalWindow !in SUPPORTED_TEMPORAL_WINDOWS ||
                !validProgression(template.progressionGroup, template.progressionRank) ||
                (template.portabilityTier == 3 && template.requiredAdapters.isEmpty())
            ) return@mapNotNull null
            val category = runCatching { ChallengeCategory.valueOf(template.category) }.getOrNull()
                ?: return@mapNotNull null
            val binding = runCatching { PortableChallengeBinding.valueOf(template.binding) }.getOrNull()
                ?: return@mapNotNull null
            PortableChallengeTemplate(
                key = template.key,
                title = template.title,
                description = template.description,
                category = category,
                portabilityTier = template.portabilityTier,
                requiredCapabilities = template.requiredCapabilities.toSet(),
                requiredCatalogRoles = template.requiredCatalogRoles.toSet(),
                requiredAdapters = template.requiredAdapters.toSet(),
                requiredTemporalWindow = template.requiredTemporalWindow,
                organicSafe = template.organicSafe,
                progressionGroup = template.progressionGroup,
                progressionRank = template.progressionRank,
                binding = binding,
                sourceInspiration = template.sourceInspiration,
            )
        }.distinctBy { it.key }
    }

    private data class StoredCatalog(
        val schema: Int = 0,
        val challenges: List<StoredChallenge> = emptyList(),
    )

    private data class StoredTemplateCatalog(
        val schema: Int = 0,
        val templates: List<StoredTemplate> = emptyList(),
    )

    private data class StoredTemplate(
        val key: String = "",
        val title: String = "",
        val description: String = "",
        val category: String = "",
        val portabilityTier: Int = 0,
        val requiredCapabilities: List<String> = emptyList(),
        val requiredCatalogRoles: List<String> = emptyList(),
        val requiredAdapters: List<String> = emptyList(),
        val requiredTemporalWindow: String = "",
        val organicSafe: Boolean = false,
        val progressionGroup: String? = null,
        val progressionRank: Int? = null,
        val binding: String = "",
        val sourceInspiration: String = "",
    )

    private data class StoredChallenge(
        val key: String = "",
        val title: String = "",
        val description: String = "",
        val category: String = "",
        val requiredCapabilities: List<String> = emptyList(),
        val organicSafe: Boolean = false,
        val progressionGroup: String? = null,
        val progressionRank: Int? = null,
        val operator: String = "",
        val metric: String = "",
        val target: Long = 0,
        val sourceInspiration: String = "",
    )

    private val SUPPORTED_TEMPORAL_WINDOWS = setOf(
        "PLAYTHROUGH",
        "BATTLE_EPOCH",
        "AREA_EPOCH",
        "SESSION_EPOCH",
        "GAME_SPECIFIC",
    )

    private fun validProgression(group: String?, rank: Int?): Boolean = when {
        group == null && rank == null -> true
        group.isNullOrBlank() || rank == null -> false
        else -> rank > 0
    }
}
