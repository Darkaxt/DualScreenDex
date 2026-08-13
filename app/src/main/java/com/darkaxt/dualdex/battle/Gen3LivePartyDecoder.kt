package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.gen3.Gen3PokemonCodec

internal object Gen3LivePartyDecoder {
    const val PARTY_CAPACITY = 6
    const val PARTY_BYTES = PARTY_CAPACITY * Gen3PokemonCodec.PARTY_RECORD_SIZE

    fun decode(
        countBytes: ByteArray?,
        partyBytes: ByteArray?,
        context: SaveParseContext,
    ): List<OwnedIndividual>? {
        val count = countBytes?.singleOrNull()?.toInt()?.and(0xFF) ?: return null
        if (count !in 0..PARTY_CAPACITY || partyBytes?.size != PARTY_BYTES) return null
        val decoded = (0 until count).mapNotNull { index ->
            val offset = index * Gen3PokemonCodec.PARTY_RECORD_SIZE
            Gen3PokemonCodec.decode(
                partyBytes,
                offset,
                stableLocation = "party-$index",
                context = context,
                partyLevel = partyBytes[offset + Gen3PokemonCodec.BOX_RECORD_SIZE + PARTY_LEVEL_OFFSET]
                    .toInt()
                    .and(0xFF),
            )
        }
        return decoded.takeIf { it.size == count }
    }

    private const val PARTY_LEVEL_OFFSET = 4
}
