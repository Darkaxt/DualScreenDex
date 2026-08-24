package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import kotlin.math.abs

data class Gen3PokedexSnapshot(
    val ownedOffset: Int?,
    val seenDexNumbers: Set<Int>,
    val caughtDexNumbers: Set<Int>,
)

object Gen3PokedexCodec {
    fun decode(
        saveBlock2: ByteArray?,
        context: SaveParseContext,
        party: List<OwnedIndividual>?,
    ): SaveSectionResult<Gen3PokedexSnapshot> = runCatching {
        val bytes = requireNotNull(saveBlock2) { "SaveBlock2 was unavailable" }
        require(context.speciesById.isNotEmpty()) { "a parsed ROM species index is required" }
        val flagBytes = (context.internalSpeciesCount + 7) / 8
        require(DEFAULT_OWNED_OFFSET + flagBytes * 2 <= bytes.size) {
            "ROM species count does not fit the Gen III Pokédex save block"
        }
        val decodedParty = requireNotNull(party) { "validated party evidence is required for Pokédex layout resolution" }
        if (decodedParty.isEmpty()) {
            return@runCatching SaveSectionResult.available(
                Gen3PokedexSnapshot(
                    ownedOffset = null,
                    seenDexNumbers = emptySet(),
                    caughtDexNumbers = emptySet(),
                ),
            )
        }
        val ownedDexNumbers = decodedParty.mapNotNullTo(mutableSetOf()) { individual ->
            context.speciesById[individual.speciesId]?.pokedexFlagNumber
        }
        val maximumOffset = minOf(MAX_OWNED_OFFSET, bytes.size - flagBytes * 2)
        val best = (DEFAULT_OWNED_OFFSET..maximumOffset step POKEDEX_ALIGNMENT).map { ownedOffset ->
            val caught = decodeFlags(bytes, ownedOffset, flagBytes, context.maximumDexNumber)
            val rawSeen = decodeFlags(bytes, ownedOffset + flagBytes, flagBytes, context.maximumDexNumber)
            val coveredOwned = ownedDexNumbers.count { it in caught }
            val missingOwned = ownedDexNumbers.size - coveredOwned
            val consistentFlags = caught.all { it in rawSeen }
            val headerOffset = ownedOffset - POKEDEX_OWNED_FIELD_OFFSET
            val headerConfidence = pokedexHeaderConfidence(bytes, headerOffset)
            val evidence = if (caught.isNotEmpty() || rawSeen.isNotEmpty()) 1 else 0
            val distanceFromDefault = abs(ownedOffset - DEFAULT_OWNED_OFFSET) / POKEDEX_ALIGNMENT
            val score = coveredOwned * 10_000 - missingOwned * 10_000 +
                (if (consistentFlags) 1_000 else -1_000) + headerConfidence * 100 + evidence * 10 - distanceFromDefault
            PokedexAttempt(ownedOffset, rawSeen + caught, caught, score)
        }.maxWithOrNull(compareBy<PokedexAttempt> { it.score }.thenBy { -it.ownedOffset })
            ?: error("no aligned Gen III Pokédex layout fit SaveBlock2")
        SaveSectionResult.available(
            Gen3PokedexSnapshot(
                ownedOffset = best.ownedOffset,
                seenDexNumbers = best.seen,
                caughtDexNumbers = best.caught,
            ),
        )
    }.getOrElse { error ->
        SaveSectionResult.unavailable(error.message ?: "Gen III Pokédex flags were invalid")
    }

    private fun pokedexHeaderConfidence(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 >= bytes.size) return Int.MIN_VALUE / 100
        val order = bytes[offset].toInt() and 0xFF
        val mode = bytes[offset + 1].toInt() and 0xFF
        val nationalMagic = bytes[offset + 2].toInt() and 0xFF
        return (if (order in 0..3) 1 else -4) +
            (if (mode in 0..1) 1 else -4) +
            when (nationalMagic) {
                NATIONAL_DEX_MAGIC -> 4
                0 -> 2
                else -> -8
            }
    }

    private fun decodeFlags(
        bytes: ByteArray,
        offset: Int,
        byteCount: Int,
        maximumDexNumber: Int,
    ): Set<Int> = buildSet {
        for (dex in 1..maximumDexNumber) {
            val index = dex - 1
            if (index / 8 < byteCount && bytes[offset + index / 8].toInt() and (1 shl (index % 8)) != 0) {
                add(dex)
            }
        }
    }

    private data class PokedexAttempt(
        val ownedOffset: Int,
        val seen: Set<Int>,
        val caught: Set<Int>,
        val score: Int,
    )

    private const val DEFAULT_OWNED_OFFSET = 0x28
    private const val MAX_OWNED_OFFSET = 0x200
    private const val POKEDEX_OWNED_FIELD_OFFSET = 0x10
    private const val POKEDEX_ALIGNMENT = 4
    private const val NATIONAL_DEX_MAGIC = 0xDA
}
