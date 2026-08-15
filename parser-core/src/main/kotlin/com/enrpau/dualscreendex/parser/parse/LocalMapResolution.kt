package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog

sealed interface LocalMapResolution {
    data class Resolved(
        val catalog: LocalMapCatalog,
        val reasons: List<String>,
        val skippedMaps: Int = 0,
    ) : LocalMapResolution

    data class Unavailable(val stage: String, val reason: String) : LocalMapResolution
    data class BudgetExceeded(val stage: String, val reason: String) : LocalMapResolution
}
