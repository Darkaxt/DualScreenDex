package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext

object Gen3PokemonCodec {
    const val BOX_RECORD_SIZE = 80
    const val PARTY_RECORD_SIZE = 100

    fun decode(
        bytes: ByteArray,
        offset: Int,
        stableLocation: String,
        context: SaveParseContext,
        partyLevel: Int? = null,
    ): OwnedIndividual? = runCatching {
        require(offset >= 0 && offset + BOX_RECORD_SIZE <= bytes.size)
        val headerFlags = bytes[offset + 19].toInt() and 0xFF
        if (headerFlags and HAS_SPECIES == 0) return null
        val personality = bytes.u32le(offset)
        val otId = bytes.u32le(offset + 4)
        val key = personality xor otId
        val standardDecrypted = ByteArray(SECURE_SIZE)
        repeat(SECURE_SIZE / 4) { index ->
            val value = bytes.u32le(offset + SECURE_OFFSET + index * 4) xor key
            standardDecrypted.putU32le(index * 4, value)
        }
        val storedChecksum = bytes.u16le(offset + CHECKSUM_OFFSET)
        val standardChecksumValid = Gen3Checksums.pokemon(standardDecrypted) == storedChecksum
        val (decrypted, growth, misc) = if (standardChecksumValid) {
            val order = SUBSTRUCT_ORDERS[(personality % 24).toInt()]
            Triple(standardDecrypted, order[0] * SUBSTRUCT_SIZE, order[3] * SUBSTRUCT_SIZE)
        } else {
            if (storedChecksum != 0) return null
            Triple(
                bytes.copyOfRange(offset + SECURE_OFFSET, offset + SECURE_OFFSET + SECURE_SIZE),
                0,
                3 * SUBSTRUCT_SIZE,
            )
        }
        val speciesId = decrypted.u16le(growth)
        if (speciesId == 0 || speciesId !in context.speciesById) return null
        val experience = decrypted.u32le(growth + 4)
        val origin = decrypted.u16le(misc + 2)
        val ivWord = decrypted.u32le(misc + 4)
        val ivs = List(6) { index -> ((ivWord ushr (index * 5)) and 0x1F).toInt() }
        val isEgg = headerFlags and HEADER_EGG != 0 || ivWord and (1L shl 30) != 0L
        val ball = ((origin ushr 11) and 0xF).takeIf { it in context.captureBallIds }
        val level = partyLevel?.takeIf { it in 1..100 }
            ?: Gen3Experience.level(context.speciesById[speciesId]?.growthRate, experience)
        val speciesContext = context.speciesById.getValue(speciesId)
        val formId = when {
            speciesContext.formId > 0 -> speciesContext.formId
            speciesContext.dexNumber == UNOWN_DEX_NUMBER -> unownForm(personality)
            else -> null
        }
        OwnedIndividual(
            stableLocation = stableLocation,
            speciesId = speciesId,
            formId = formId,
            level = level,
            isEgg = isEgg,
            ivs = ivs,
            captureBallId = ball,
            experience = experience,
        )
    }.getOrNull()

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun unownForm(personality: Long): Int = (
        ((personality and 0x0300_0000) ushr 18) or
            ((personality and 0x0003_0000) ushr 12) or
            ((personality and 0x0000_0300) ushr 6) or
            (personality and 0x0000_0003)
        ).toInt() % 28

    private const val HAS_SPECIES = 0x02
    private const val HEADER_EGG = 0x04
    private const val CHECKSUM_OFFSET = 28
    private const val SECURE_OFFSET = 32
    private const val SECURE_SIZE = 48
    private const val SUBSTRUCT_SIZE = 12
    private const val UNOWN_DEX_NUMBER = 201

    // Each row maps logical Growth/Attacks/EVs/Misc to its physical encrypted block.
    private val SUBSTRUCT_ORDERS = arrayOf(
        intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3),
        intArrayOf(0, 3, 1, 2), intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 2, 1),
        intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(2, 0, 1, 3),
        intArrayOf(3, 0, 1, 2), intArrayOf(2, 0, 3, 1), intArrayOf(3, 0, 2, 1),
        intArrayOf(1, 2, 0, 3), intArrayOf(1, 3, 0, 2), intArrayOf(2, 1, 0, 3),
        intArrayOf(3, 1, 0, 2), intArrayOf(2, 3, 0, 1), intArrayOf(3, 2, 0, 1),
        intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 2, 0), intArrayOf(2, 1, 3, 0),
        intArrayOf(3, 1, 2, 0), intArrayOf(2, 3, 1, 0), intArrayOf(3, 2, 1, 0),
    )
}
