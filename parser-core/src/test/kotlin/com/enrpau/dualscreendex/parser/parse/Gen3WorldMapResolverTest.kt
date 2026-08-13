package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3WorldMapResolverTest {
    @Test
    fun unrelatedTruncatedTargetDoesNotVetoCompleteRequiredAssetTargets() {
        val completeTarget = 0x100
        val unrelatedTruncatedTarget = 0x200
        val references = GbaReferenceIndex.fromTargets(
            mapOf(
                completeTarget to GbaTargetReferenceEvidence(
                    count = 1,
                    instructionSites = listOf(0x20),
                    observedSites = 1,
                    limitSites = 16,
                    overflowReason = null,
                ),
                unrelatedTruncatedTarget to GbaTargetReferenceEvidence(
                    count = 17,
                    instructionSites = emptyList(),
                    observedSites = 17,
                    limitSites = 16,
                    overflowReason = "candidate-local site budget exceeded",
                ),
            ),
            limitTargets = 32,
        )

        assertTrue(Gen3WorldMapResolver.requiredReferenceSitesComplete(references, setOf(completeTarget)))
        assertFalse(
            Gen3WorldMapResolver.requiredReferenceSitesComplete(
                references,
                setOf(completeTarget, unrelatedTruncatedTarget),
            ),
        )
    }
}
