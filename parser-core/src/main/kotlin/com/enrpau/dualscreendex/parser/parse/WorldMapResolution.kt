package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog

sealed interface WorldMapResolution {
    data class Resolved(val catalog: WorldMapCatalog, val reasons: List<String>) : WorldMapResolution
    data class Unavailable(val stage: String, val reason: String) : WorldMapResolution
    data class Ambiguous(val stage: String, val reason: String) : WorldMapResolution
    data class BudgetExceeded(val stage: String, val reason: String) : WorldMapResolution
}
