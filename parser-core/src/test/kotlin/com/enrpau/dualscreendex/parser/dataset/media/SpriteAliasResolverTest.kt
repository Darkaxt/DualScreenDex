package com.enrpau.dualscreendex.parser.dataset.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteAliasResolverTest {
    @Test
    fun infersOnlyFromTheUniqueSameNameAndCanonicalDexDonor() {
        val frame = frame(1)
        val identities = listOf(
            SpriteSpeciesIdentity(1, "MR. MIME", 122),
            SpriteSpeciesIdentity(2, "  mr.   mime ", 122),
            SpriteSpeciesIdentity(3, "MR. MIME", 123),
        )

        val result = SpriteAliasResolver.resolve(identities, mapOf(1 to frame))

        val inferred = result.getValue(2) as SpriteProjection.Inferred
        assertEquals(1, inferred.donorSpeciesId)
        assertEquals(frame, inferred.frame)
        assertTrue(result.getValue(3) is SpriteProjection.Missing)
    }

    @Test
    fun preservesAnExplicitSpriteEvenWhenAnAliasDonorExists() {
        val explicit = frame(2)
        val result = SpriteAliasResolver.resolve(
            listOf(
                SpriteSpeciesIdentity(1, "UNOWN", 201),
                SpriteSpeciesIdentity(2, "UNOWN", 201),
            ),
            mapOf(1 to frame(1), 2 to explicit),
        )

        assertEquals(explicit, (result.getValue(2) as SpriteProjection.Explicit).frame)
    }

    @Test
    fun distinctEligibleDonorsStayAmbiguousInsteadOfChoosingByIdOrPixelEquality() {
        val result = SpriteAliasResolver.resolve(
            listOf(
                SpriteSpeciesIdentity(1, "FORM", 25),
                SpriteSpeciesIdentity(2, "FORM", 25),
                SpriteSpeciesIdentity(3, "FORM", 25),
            ),
            mapOf(1 to frame(1), 2 to frame(1)),
        )

        val ambiguous = result.getValue(3) as SpriteProjection.Ambiguous
        assertEquals(listOf(1, 2), ambiguous.donorSpeciesIds)
    }

    private fun frame(value: Int) = DecodedSpriteFrame(
        width = 8,
        height = 8,
        graphicsBytes = ByteArray(32) { value.toByte() },
        indexedPixels = ByteArray(64) { value.toByte() },
    )
}
