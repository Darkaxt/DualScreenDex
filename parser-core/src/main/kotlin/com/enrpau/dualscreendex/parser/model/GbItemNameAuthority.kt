package com.enrpau.dualscreendex.parser.model

/** Frozen compiled formatting contract; valid inputs come separately from structural references. */
sealed interface GbItemNameAuthority {
    data class Unavailable(val reason: String = "original Gen I item consumer unavailable") : GbItemNameAuthority

    @ConsistentCopyVisibility
    data class Available internal constructor(
        val root: Int,
        val bankEnd: Int,
        val copyBytes: Int,
        val machineThreshold: Int,
        val machineSplit: Int,
        val lowerAdjustment: Int,
        val numberSubtract: Int,
        val digitOrigin: Int,
        val terminator: Int,
        val lowerPrefix: Int,
        val lowerPrefixBytes: Int,
        val upperPrefix: Int,
        val upperPrefixBytes: Int,
    ) : GbItemNameAuthority
}
