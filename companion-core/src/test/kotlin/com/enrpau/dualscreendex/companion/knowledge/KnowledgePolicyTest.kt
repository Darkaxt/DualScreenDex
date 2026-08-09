package com.enrpau.dualscreendex.companion.knowledge

import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgePolicyTest {
    @Test
    fun organicListsSeenAndUnlocksCapturedDetails() {
        val ledger = KnowledgeLedger(
            seenSpecies = setOf(10),
            owned = listOf(OwnedPokemon("box-1", 25, 3, 20, ivs = List(6) { 15 })),
        )

        assertTrue(KnowledgePolicy.listSpecies(KnowledgeMode.ORGANIC, 10, ledger))
        assertFalse(KnowledgePolicy.staticDetails(KnowledgeMode.ORGANIC, 10, ledger))
        assertTrue(KnowledgePolicy.staticDetails(KnowledgeMode.ORGANIC, 25, ledger))
        assertFalse(KnowledgePolicy.listSpecies(KnowledgeMode.ORGANIC, 99, ledger))
    }
}
