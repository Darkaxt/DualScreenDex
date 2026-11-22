package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.LivePokedexState
import com.darkaxt.dualdex.battle.LiveValue
import com.darkaxt.dualdex.battle.valueOrNull
import com.darkaxt.dualdex.save.OwnedIndividual

internal class Gen3LivePokedexStabilizer {
    private var confirmedOffset: Int? = null
    private var pendingCandidate: CandidateSignature? = null
    private var lastAccepted: LivePokedexState? = null

    fun reset() {
        confirmedOffset = null
        pendingCandidate = null
        lastAccepted = null
    }

    fun accept(
        candidate: LivePokedexState,
        party: LiveValue<List<OwnedIndividual>>,
    ): LivePokedexState {
        val offset = candidate.ownedFlagOffset
        val seen = candidate.seenDexNumbers.valueOrNull()
        val caught = candidate.caughtDexNumbers.valueOrNull()

        if (seen == null || caught == null || offset == null) {
            pendingCandidate = null
            return if (confirmedOffset != null || lastAccepted != null) {
                lastAccepted ?: candidate
            } else {
                candidate.also { lastAccepted = it }
            }
        }

        confirmedOffset?.let { confirmed ->
            if (offset != confirmed) return requireNotNull(lastAccepted)
            pendingCandidate = null
            lastAccepted = candidate
            return candidate
        }

        val previous = lastAccepted ?: emptyBaseline(candidate)
        val previousSeen = previous.seenDexNumbers.valueOrNull().orEmpty()
        val previousCaught = previous.caughtDexNumbers.valueOrNull().orEmpty()
        val partyCount = party.valueOrNull()?.size ?: 0
        val caughtAdditionLimit = maxOf(1, partyCount)
        val seenAdditionLimit = maxOf(2, partyCount + 1)
        val suspicious = (caught - previousCaught).size > caughtAdditionLimit ||
            (seen - previousSeen).size > seenAdditionLimit

        if (!suspicious) {
            confirmedOffset = offset
            pendingCandidate = null
            lastAccepted = candidate
            return candidate
        }

        val signature = CandidateSignature(offset, seen.toSet(), caught.toSet())
        if (pendingCandidate == signature) {
            confirmedOffset = offset
            pendingCandidate = null
            lastAccepted = candidate
            return candidate
        }

        pendingCandidate = signature
        lastAccepted = previous
        return previous
    }

    private fun emptyBaseline(candidate: LivePokedexState): LivePokedexState = candidate.copy(
        seenDexNumbers = LiveValue.Available(emptySet()),
        caughtDexNumbers = LiveValue.Available(emptySet()),
        ownedFlagOffset = null,
    )

    private data class CandidateSignature(
        val offset: Int,
        val seen: Set<Int>,
        val caught: Set<Int>,
    )
}
