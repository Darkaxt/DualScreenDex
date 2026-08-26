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
                challenge.metric.isBlank() || challenge.target <= 0
            ) return@mapNotNull null
            val category = runCatching { ChallengeCategory.valueOf(challenge.category) }.getOrNull()
                ?: return@mapNotNull null
            ChallengeDefinition(
                key = challenge.key,
                title = challenge.title,
                description = challenge.description,
                category = category,
                requiredCapabilities = challenge.requiredCapabilities.toSet(),
                organicSafe = challenge.organicSafe,
                predicate = ChallengePredicate.CountAtLeast(challenge.metric, challenge.target),
                sourceInspiration = challenge.sourceInspiration,
            )
        }.distinctBy { it.key }
    }

    private data class StoredCatalog(
        val schema: Int = 0,
        val challenges: List<StoredChallenge> = emptyList(),
    )

    private data class StoredChallenge(
        val key: String = "",
        val title: String = "",
        val description: String = "",
        val category: String = "",
        val requiredCapabilities: List<String> = emptyList(),
        val organicSafe: Boolean = false,
        val operator: String = "",
        val metric: String = "",
        val target: Long = 0,
        val sourceInspiration: String = "",
    )
}

