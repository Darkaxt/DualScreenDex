package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.save.gen3.Gen3TextEncoding
import com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi

data class SaveSpeciesContext(
    val speciesId: Int,
    val dexNumber: Int?,
    val growthRate: Int?,
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
    val maximumDexNumber: Int = speciesById.values.mapNotNull { it.dexNumber }.maxOrNull() ?: 0
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
    val formId: Int? = null,
    val level: Int? = null,
    val isEgg: Boolean = false,
    val ivs: List<Int>? = null,
    val dvs: List<Int>? = null,
    val captureBallId: Int? = null,
    val experience: Long? = null,
    val details: PartyMemberDetails? = null,
)

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
