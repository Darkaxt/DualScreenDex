package com.enrpau.dualscreendex.parser.profile

import com.enrpau.dualscreendex.parser.model.EngineFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KnownProfilesTest {
    @Test
    fun recognizesKnownEmeraldHash() {
        val profile = KnownProfiles.bySha256("a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af")
        assertNotNull(profile)
        assertEquals(EngineFamily.EMERALD, profile?.family)
        assertEquals(386, profile?.dexSpeciesCount)
    }

    @Test
    fun containsAllOfficialEnglishEntries() {
        assertEquals(11, KnownProfiles.all.size)
        assertEquals(11, KnownProfiles.all.map { it.sha256 }.distinct().size)
    }
}
