package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.save.gen3.Gen3TextEncoding
import com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi
import java.security.MessageDigest

data class SaveSpeciesContext(
    val speciesId: Int,
    val dexNumber: Int?,
    val growthRate: Int?,
    val pokedexFlagNumber: Int? = dexNumber,
    val formId: Int = 0,
    val genderRatio: Int? = null,
    val abilityIds: List<Int> = emptyList(),
)

data class SaveParseContext(
    val romIdentity: String,
    val speciesById: Map<Int, SaveSpeciesContext>,
    val captureBallIds: Set<Int> = (1..15).toSet(),
    val levelUpRulesetSelectors: List<SaveByteSelector> = emptyList(),
    val movePpById: Map<Int, Int> = emptyMap(),
    val gen3TextEncoding: Gen3TextEncoding? = null,
    val gen3SaveRuntimeAbi: Gen3SaveRuntimeAbi? = null,
) {
    val internalSpeciesCount: Int = (speciesById.keys.maxOrNull() ?: 0) + 1
    val maximumDexNumber: Int = speciesById.values.mapNotNull { it.pokedexFlagNumber }.maxOrNull() ?: 0
}

data class SaveByteSelector(
    val rulesetId: String,
    val saveBlock1ByteOffset: Int,
    val mask: Int,
    val expectedValue: Int,
)

data class SavedArea(val mapGroup: Int, val mapNumber: Int) {
    val baseId: Int get() = (mapGroup shl 8) or mapNumber
}

data class OwnedIndividual(
    val stableLocation: String,
    val speciesId: Int,
    val individualIdentity: String? = null,
    val formId: Int? = null,
    val level: Int? = null,
    val isEgg: Boolean = false,
    val ivs: List<Int>? = null,
    val dvs: List<Int>? = null,
    val captureBallId: Int? = null,
    val experience: Long? = null,
    val details: PartyMemberDetails? = null,
) {
    init {
        require(stableLocation.isNotBlank()) { "owned individual location must not be blank" }
        require(speciesId > 0) { "owned individual species ID must be positive" }
        require(individualIdentity == null || individualIdentity.matches(Regex("[0-9a-f]{16}"))) {
            "individual identity must be a normalized 64-bit hex value"
        }
    }

    /** Digest of fields shared by Party and boxed representations; location and volatile battle fields are excluded. */
    fun validatedRecordDigest(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun text(value: Any?) {
            digest.update((value?.toString() ?: "-").toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        text(speciesId)
        text(formId)
        text(level)
        text(isEgg)
        ivs.orEmpty().forEach(::text)
        dvs.orEmpty().forEach(::text)
        text(captureBallId)
        text(experience)
        text(details?.nickname)
        text(details?.personality)
        text(details?.gender)
        text(details?.natureId)
        text(details?.heldItemId)
        text(details?.friendship)
        text(details?.abilitySlot)
        details?.moveIds.orEmpty().forEach(::text)
        details?.movePp.orEmpty().forEach(::text)
        details?.movePpBonuses.orEmpty().forEach(::text)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class SaveSnapshot(
    val romIdentity: String,
    val saveIdentity: String,
    val saveGeneration: Int,
    val saveCounter: Long,
    val currentArea: SavedArea?,
    val seenDexNumbers: Set<Int>,
    val caughtDexNumbers: Set<Int>,
    val party: List<OwnedIndividual>,
    val storedIndividuals: List<OwnedIndividual>,
    val capabilities: Map<SaveCapability, SaveCapabilityEvidence>,
    val schemaId: String = "gen3-v1",
    val detectedLevelUpRulesetId: String? = null,
    val levelUpRulesetDetectionResolved: Boolean = false,
    val levelUpRulesetDetectionFingerprint: String? = null,
    val trainer: TrainerSnapshot? = null,
    val bag: List<BagPocketSnapshot> = emptyList(),
    val eventFlagIds: Set<Int>? = null,
) {
    val allIndividuals: List<OwnedIndividual> get() = party + storedIndividuals
}

sealed interface SaveParseResult {
    data class Parsed(val snapshot: SaveSnapshot) : SaveParseResult
    data class Unsupported(val reasons: List<String>) : SaveParseResult
}
