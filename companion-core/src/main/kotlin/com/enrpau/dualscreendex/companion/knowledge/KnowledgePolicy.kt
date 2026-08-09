package com.enrpau.dualscreendex.companion.knowledge

import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.MatchupKey

object KnowledgePolicy {
    fun listSpecies(mode: KnowledgeMode, speciesId: Int, ledger: KnowledgeLedger): Boolean = when (mode) {
        KnowledgeMode.DISCOVERED, KnowledgeMode.HIDDEN -> true
        KnowledgeMode.ORGANIC -> speciesId in ledger.seenSpecies || isCaught(speciesId, ledger)
    }

    fun staticDetails(mode: KnowledgeMode, speciesId: Int, ledger: KnowledgeLedger): Boolean = when (mode) {
        KnowledgeMode.DISCOVERED -> true
        KnowledgeMode.ORGANIC -> isCaught(speciesId, ledger)
        KnowledgeMode.HIDDEN -> true
    }

    fun matchup(
        mode: KnowledgeMode,
        speciesId: Int,
        moveId: Int,
        truth: Effectiveness?,
        ledger: KnowledgeLedger,
    ): Effectiveness? = when (mode) {
        KnowledgeMode.DISCOVERED -> truth
        KnowledgeMode.ORGANIC -> if (isCaught(speciesId, ledger)) truth else ledger.discoveredMatchups[MatchupKey(speciesId, moveId)]
        KnowledgeMode.HIDDEN -> null
    }

    fun assistanceVisible(mode: KnowledgeMode): Boolean = mode != KnowledgeMode.HIDDEN

    fun isCaught(speciesId: Int, ledger: KnowledgeLedger): Boolean = ledger.owned.any { it.speciesId == speciesId }
}
