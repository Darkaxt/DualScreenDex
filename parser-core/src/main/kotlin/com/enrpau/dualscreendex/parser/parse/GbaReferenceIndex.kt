package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader

/** Compatibility facade for isolated legacy callers; production analysis shares one session-owned index. */
@Deprecated("Use RomAnalysisSession.gbaReferenceIndex")
object GbaReferenceIndexBuilder {
    fun build(
        rom: RomImage,
        maxDistinctTargets: Int = MAX_DISTINCT_TARGETS,
    ): GbaCompiledReferenceIndex {
        return requireNotNull(
            RomAnalysisSession(
                rom = rom,
                header = RomHeader(Platform.GBA, ""),
                limits = ResolutionLimits(maxDistinctGbaReferenceTargets = maxDistinctTargets),
            ).gbaReferenceIndex,
        ).asLegacyCounts()
    }

    private const val MAX_DISTINCT_TARGETS = 32_768
}
