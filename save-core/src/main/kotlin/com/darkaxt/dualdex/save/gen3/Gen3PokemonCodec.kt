package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.PartyMemberDetails
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
        val (decrypted, logicalOffsets) = if (standardChecksumValid) {
            val order = SUBSTRUCT_ORDERS[(personality % 24).toInt()]
            standardDecrypted to IntArray(4) { logical -> order[logical] * SUBSTRUCT_SIZE }
        } else {
            if (storedChecksum != 0) return null
            Pair(
                bytes.copyOfRange(offset + SECURE_OFFSET, offset + SECURE_OFFSET + SECURE_SIZE),
                IntArray(4) { logical -> logical * SUBSTRUCT_SIZE },
            )
        }
        val growth = logicalOffsets[GROWTH]
        val attacks = logicalOffsets[ATTACKS]
        val misc = logicalOffsets[MISC]
        val speciesId = decrypted.u16le(growth)
        if (speciesId == 0 || speciesId !in context.speciesById) return null
        val experience = decrypted.u32le(growth + 4)
        val heldItemId = decrypted.u16le(growth + 2).takeIf { it > 0 }
        val ppBonusByte = decrypted[growth + 8].toInt() and 0xFF
        val friendship = decrypted[growth + 9].toInt() and 0xFF
        val moveIds = List(MOVE_SLOT_COUNT) { index -> decrypted.u16le(attacks + index * 2) }
        val movePp = List(MOVE_SLOT_COUNT) { index -> decrypted[attacks + 8 + index].toInt() and 0xFF }
        val movePpBonuses = List(MOVE_SLOT_COUNT) { index -> (ppBonusByte ushr (index * 2)) and 0x3 }
        if (!validMoveSlots(moveIds, movePp, movePpBonuses, context)) return null
        val origin = decrypted.u16le(misc + 2)
        val ivWord = decrypted.u32le(misc + 4)
        val ivs = List(6) { index -> ((ivWord ushr (index * 5)) and 0x1F).toInt() }
        val isEgg = headerFlags and HEADER_EGG != 0 || ivWord and (1L shl 30) != 0L
        val ball = ((origin ushr 11) and 0xF).takeIf { it in context.captureBallIds }
        val level = partyLevel?.takeIf { it in 1..100 }
            ?: Gen3Experience.level(context.speciesById[speciesId]?.growthRate, experience)
        val speciesContext = context.speciesById.getValue(speciesId)
        val abilitySlot = ((ivWord ushr 31) and 1).toInt()
        val abilityId = speciesContext.abilityIds.getOrNull(abilitySlot)
            ?: speciesContext.abilityIds.singleOrNull()
        val formId = when {
            speciesContext.formId > 0 -> speciesContext.formId
            speciesContext.dexNumber == UNOWN_DEX_NUMBER -> unownForm(personality)
            else -> null
        }
        val partyTailPresent = partyLevel != null && offset + PARTY_RECORD_SIZE <= bytes.size
        val decodedCurrentHp = if (partyTailPresent) bytes.u16le(offset + CURRENT_HP_OFFSET) else null
        val decodedMaximumHp = if (partyTailPresent) bytes.u16le(offset + MAXIMUM_HP_OFFSET) else null
        val decodedStats = if (partyTailPresent) {
            List(STAT_SLOT_COUNT) { index -> bytes.u16le(offset + MAXIMUM_HP_OFFSET + index * 2) }
        } else {
            emptyList()
        }
        val validPartyTail = decodedCurrentHp != null && decodedMaximumHp != null &&
            decodedMaximumHp > 0 && decodedCurrentHp <= decodedMaximumHp && decodedStats.all { it > 0 }
        val currentHp = decodedCurrentHp.takeIf { validPartyTail }
        val maximumHp = decodedMaximumHp.takeIf { validPartyTail }
        val stats = decodedStats.takeIf { validPartyTail }.orEmpty()
        val status = if (validPartyTail) bytes.u32le(offset + STATUS_OFFSET) else null
        val nickname = Gen3SaveTextCodec.decode(
            bytes.copyOfRange(offset + NICKNAME_OFFSET, offset + NICKNAME_OFFSET + NICKNAME_SIZE),
            context.gen3TextEncoding,
        )
        OwnedIndividual(
            stableLocation = stableLocation,
            speciesId = speciesId,
            individualIdentity = "%08x%08x".format(personality, otId),
            formId = formId,
            level = level,
            isEgg = isEgg,
            ivs = ivs,
            captureBallId = ball,
            experience = experience,
            details = PartyMemberDetails(
                nickname = nickname,
                personality = personality,
                gender = gender(speciesContext.genderRatio, personality),
                natureId = (personality % NATURE_COUNT).toInt(),
                heldItemId = heldItemId,
                friendship = friendship,
                abilitySlot = abilitySlot,
                abilityId = abilityId,
                currentHp = currentHp,
                maximumHp = maximumHp,
                status = status,
                stats = stats,
                moveIds = moveIds,
                movePp = movePp,
                movePpBonuses = movePpBonuses,
                experienceProgress = Gen3Experience.progress(speciesContext.growthRate, experience, level),
            ),
        )
    }.getOrNull()

    private fun validMoveSlots(
        moveIds: List<Int>,
        movePp: List<Int>,
        movePpBonuses: List<Int>,
        context: SaveParseContext,
    ): Boolean = moveIds.indices.all { index ->
        val moveId = moveIds[index]
        val pp = movePp[index]
        when {
            moveId == 0 -> pp == 0
            context.movePpById.isEmpty() -> true
            else -> {
                val basePp = context.movePpById[moveId] ?: return@all false
                val maximumPp = basePp + basePp * movePpBonuses[index] / 5
                pp <= maximumPp
            }
        }
    }

    private fun gender(genderRatio: Int?, personality: Long): Int? = when (genderRatio) {
        null -> null
        0 -> MALE
        0xFE -> FEMALE
        0xFF -> GENDERLESS
        in 1..0xFD -> if ((personality and 0xFF).toInt() < genderRatio) FEMALE else MALE
        else -> null
    }

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
    private const val NICKNAME_OFFSET = 8
    private const val NICKNAME_SIZE = 10
    private const val SECURE_OFFSET = 32
    private const val SECURE_SIZE = 48
    private const val SUBSTRUCT_SIZE = 12
    private const val UNOWN_DEX_NUMBER = 201
    private const val GROWTH = 0
    private const val ATTACKS = 1
    private const val MISC = 3
    private const val MOVE_SLOT_COUNT = 4
    private const val STATUS_OFFSET = 80
    private const val CURRENT_HP_OFFSET = 86
    private const val MAXIMUM_HP_OFFSET = 88
    private const val STAT_SLOT_COUNT = 6
    private const val NATURE_COUNT = 25
    private const val MALE = 0
    private const val FEMALE = 1
    private const val GENDERLESS = 2

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
