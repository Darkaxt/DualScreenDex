package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSpeciesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3PokedexCodecTest {
    @Test
    fun emptyPartyDoesNotPromoteNearbySaveBlockBytesToPokedexFlags() {
        val context = SaveParseContext(
            romIdentity = "a".repeat(64),
            speciesById = (1..462).associateWith { SaveSpeciesContext(it, it, null) },
        )
        val bytes = ByteArray(0xF2C)
        val flagBytes = (context.internalSpeciesCount + 7) / 8
        setFlag(bytes, 0x38 + flagBytes, 433)
        setFlag(bytes, 0x38 + flagBytes, 434)

        val result = Gen3PokedexCodec.decode(bytes, context, emptyList())

        assertNull(result.value?.ownedOffset)
        assertEquals(emptySet<Int>(), result.value?.seenDexNumbers)
        assertEquals(emptySet<Int>(), result.value?.caughtDexNumbers)
    }

    @Test
    fun resolvesExpandedAlignedLayoutUsingOwnedPartyAndHeaderEvidence() {
        val context = SaveParseContext(
            romIdentity = "b".repeat(64),
            speciesById = (1..462).associateWith { speciesId ->
                SaveSpeciesContext(
                    speciesId = speciesId,
                    dexNumber = when (speciesId) {
                        in 1..251 -> speciesId
                        277 -> 252
                        280 -> 255
                        286 -> 261
                        else -> null
                    },
                    growthRate = null,
                )
            },
        )
        val bytes = ByteArray(0xF2C)
        val flagBytes = (context.internalSpeciesCount + 7) / 8
        bytes[0x2C - 0x10] = 0
        bytes[0x2C - 0x10 + 1] = 0
        bytes[0x2C - 0x10 + 2] = 0xDA.toByte()
        setFlag(bytes, 0x2C, 252)
        setFlag(bytes, 0x2C, 261)
        listOf(252, 255, 261).forEach { setFlag(bytes, 0x2C + flagBytes, it) }

        val result = Gen3PokedexCodec.decode(
            saveBlock2 = bytes,
            context = context,
            party = listOf(
                OwnedIndividual("party-0", speciesId = 277),
                OwnedIndividual("party-1", speciesId = 286),
            ),
        )

        assertEquals(0x2C, result.value?.ownedOffset)
        assertEquals(setOf(252, 261), result.value?.caughtDexNumbers)
        assertEquals(setOf(252, 255, 261), result.value?.seenDexNumbers)
    }

    @Test
    fun resolvesOfficialEmeraldDefaultLayoutFromExplicitFlags() {
        val context = SaveParseContext(
            romIdentity = "d".repeat(64),
            speciesById = (1..411).associateWith { speciesId ->
                SaveSpeciesContext(
                    speciesId = speciesId,
                    dexNumber = when (speciesId) {
                        in 1..251 -> speciesId + 202
                        in 277..411 -> speciesId - 276
                        else -> null
                    },
                    growthRate = null,
                    pokedexFlagNumber = when (speciesId) {
                        in 1..251 -> speciesId
                        in 277..411 -> speciesId - 25
                        else -> null
                    },
                )
            },
        )
        val bytes = ByteArray(0xF2C)
        val flagBytes = (context.internalSpeciesCount + 7) / 8
        (1..8).forEach { setFlag(bytes, 0x28, it) }
        (1..15).forEach { setFlag(bytes, 0x28 + flagBytes, it) }

        val result = Gen3PokedexCodec.decode(
            saveBlock2 = bytes,
            context = context,
            party = listOf(OwnedIndividual("party-0", speciesId = 1)),
        )

        assertEquals(0x28, result.value?.ownedOffset)
        assertEquals((1..8).toSet(), result.value?.caughtDexNumbers)
        assertEquals((1..15).toSet(), result.value?.seenDexNumbers)
    }

    @Test
    fun incompleteBlockIsUnavailableInsteadOfInventingEmptyFlags() {
        val context = SaveParseContext(
            romIdentity = "c".repeat(64),
            speciesById = (1..386).associateWith { SaveSpeciesContext(it, it, null) },
        )

        val result = Gen3PokedexCodec.decode(ByteArray(32), context, emptyList())

        assertNull(result.value)
        assertEquals(1, result.reasons.size)
    }

    private fun setFlag(bytes: ByteArray, offset: Int, dexNumber: Int) {
        val index = dexNumber - 1
        bytes[offset + index / 8] = (bytes[offset + index / 8].toInt() or (1 shl (index % 8))).toByte()
    }
}
