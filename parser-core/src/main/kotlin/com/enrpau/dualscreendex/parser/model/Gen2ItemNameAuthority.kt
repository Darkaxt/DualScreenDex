package com.enrpau.dualscreendex.parser.model

/** Immutable original compiled mechanics; not an item domain or live-engine state claim. */
sealed interface Gen2ItemNameAuthority {
    data class Unavailable(val reason: String = "original GenII item consumer unavailable") : Gen2ItemNameAuthority

    class Available internal constructor(
        val root: Int,
        val bankEnd: Int,
        val copyBytes: Int,
        val machineThreshold: Int,
        val machineSplit: Int,
        val skipFirst: Int,
        val skipSecond: Int,
        val numberSubtract: Int,
        val hmSubtract: Int,
        val digitOrigin: Int,
        val terminator: Int,
        val tmPrefix: Int,
        val tmPrefixBytes: Int,
        val hmPrefix: Int,
        val hmPrefixBytes: Int,
        codeOffsets: Set<Int>,
    ) : Gen2ItemNameAuthority {
        val codeOffsets: Set<Int> = java.util.Collections.unmodifiableSet(codeOffsets.toSet())
    }
}
