package com.enrpau.dualscreendex.companion.api

data class ActiveLanguageBindingView(
    val romSha256: String,
    val contextEpoch: Int?,
    val stateVersion: Long?,
    val language: String,
    val authority: String,
    val projectionVersion: Long,
) {
    init {
        require(romSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "ROM SHA-256 is invalid" }
        require(contextEpoch == null || contextEpoch >= 0) { "context epoch must not be negative" }
        require(stateVersion == null || stateVersion >= 0) { "state version must not be negative" }
        require(language.isNotBlank()) { "active language must not be blank" }
        require(authority == "ROM_DEFAULT" || authority == "LIVE_RAM") { "language authority is invalid" }
        require(projectionVersion > 0) { "projection version must be positive" }
        require((contextEpoch == null) == (stateVersion == null)) {
            "context epoch and state version must be published together"
        }
        require(authority != "LIVE_RAM" || contextEpoch != null) {
            "live RAM language authority requires a session binding"
        }
    }
}

data class LocalizedEntityTextView(
    val name: String?,
    val description: String? = null,
)

data class LocalizedWorldLocationTextView(
    val regionKey: String,
    val locationKey: String,
    val name: String?,
)

data class CatalogLanguageOverlayView(
    val binding: ActiveLanguageBindingView,
    val species: Map<Int, LocalizedEntityTextView>,
    val moves: Map<Int, LocalizedEntityTextView>,
    val abilities: Map<Int, LocalizedEntityTextView>,
    val types: Map<Int, LocalizedEntityTextView>,
    val natures: Map<Int, LocalizedEntityTextView>,
    val items: Map<Int, LocalizedEntityTextView>,
    val areas: Map<Int, LocalizedEntityTextView>,
    val localMaps: Map<String, LocalizedEntityTextView>,
    val worldRegions: Map<String, LocalizedEntityTextView>,
    val worldLocations: List<LocalizedWorldLocationTextView>,
)
