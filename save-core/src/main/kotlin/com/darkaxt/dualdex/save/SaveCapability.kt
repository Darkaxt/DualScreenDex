package com.darkaxt.dualdex.save

enum class SaveCapability {
    SAVE_SLOT,
    SEEN,
    CAUGHT,
    PARTY,
    BOXES,
    CURRENT_AREA,
    SPECIES,
    FORM,
    LEVEL,
    EGG,
    IVS,
    CAPTURE_BALL,
}

enum class SaveCapabilityStatus { AVAILABLE, PARTIAL, NOT_FOUND, NOT_APPLICABLE }

data class SaveCapabilityEvidence(
    val capability: SaveCapability,
    val status: SaveCapabilityStatus,
    val records: Int = 0,
    val reasons: List<String> = emptyList(),
)
