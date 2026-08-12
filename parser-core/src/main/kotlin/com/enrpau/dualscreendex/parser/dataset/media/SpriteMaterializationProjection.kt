package com.enrpau.dualscreendex.parser.dataset.media

import java.util.Collections

/**
 * Pure projection over codec-owned row outcomes. It cannot read ROM bytes, discover roots, infer an
 * ABI, or choose among ambiguous sources.
 */
object SpriteMaterializationProjection {
    fun materialize(layout: ResolvedSpriteLayout): Map<Int, DecodedSpriteFrame> {
        val active = layout.semanticDomain.activeRowIndices.toHashSet()
        val frames = linkedMapOf<Int, DecodedSpriteFrame>()
        layout.rows.forEach { row ->
            if (row.rowIndex in active && row is SpriteRowOutcome.Decoded) {
                frames[row.rowIndex] = row.frame
            }
        }
        return Collections.unmodifiableMap(frames)
    }
}
