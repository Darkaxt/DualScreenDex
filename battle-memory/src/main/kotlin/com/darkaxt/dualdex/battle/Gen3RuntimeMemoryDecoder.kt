package com.darkaxt.dualdex.battle

data class Gen3RuntimeMemoryLayout(
    val mainAddress: Long,
    val inBattleAddress: Long,
    val inBattleMask: Int,
    val saveBlock1MapGroupOffset: Int,
    val saveBlock1MapNumberOffset: Int,
    val saveBlock1PositionXOffset: Int = 0,
    val saveBlock1PositionYOffset: Int = 2,
    val multiUsePlayerCursorAddress: Long? = null,
    val playerPartyCountAddress: Long? = null,
    val playerPartyAddress: Long? = null,
    val playerPartyCapacity: Int? = if (playerPartyAddress == null) null else 6,
    val playerPartyRecordSize: Int? = if (playerPartyAddress == null) null else 100,
    val battleMonsAddress: Long? = null,
    val battleTypeFlagsAddress: Long? = null,
    val trainerBattleMask: Int? = null,
    val nonWildBattleMask: Int? = null,
    val saveBlock1PointerAddress: Long? = null,
    val saveBlock2PointerAddress: Long? = null,
    val saveBlock1Size: Int? = null,
    val saveBlock2Size: Int? = null,
) {
    init {
        require(mainAddress in IWRAM_START..IWRAM_END)
        require(inBattleAddress in IWRAM_START..IWRAM_END)
        require(inBattleMask in 1..0xFF && inBattleMask.countOneBits() == 1)
        require(saveBlock1MapGroupOffset >= 0)
        require(saveBlock1MapNumberOffset == saveBlock1MapGroupOffset + 1)
        require(saveBlock1PositionXOffset >= 0)
        require(saveBlock1PositionYOffset == saveBlock1PositionXOffset + 2)
        require(multiUsePlayerCursorAddress == null || multiUsePlayerCursorAddress in IWRAM_START..IWRAM_END)
        require((playerPartyCountAddress == null) == (playerPartyAddress == null))
        require(playerPartyCountAddress == null || playerPartyCountAddress in EWRAM_START..EWRAM_END)
        require(playerPartyAddress == null || playerPartyAddress in EWRAM_START..EWRAM_END)
        require(
            listOf(playerPartyCountAddress, playerPartyAddress, playerPartyCapacity, playerPartyRecordSize)
                .all { it == null } ||
                listOf(playerPartyCountAddress, playerPartyAddress, playerPartyCapacity, playerPartyRecordSize)
                    .all { it != null },
        ) { "party read-plan descriptor must be complete" }
        require(playerPartyCapacity == null || playerPartyCapacity > 0)
        require(playerPartyRecordSize == null || playerPartyRecordSize >= 80)
        require(
            playerPartyAddress == null ||
                playerPartyAddress + playerPartyCapacity!!.toLong() * playerPartyRecordSize!! <= EWRAM_END + 1,
        ) { "party read-plan window must fit in EWRAM" }
        require(battleMonsAddress == null || battleMonsAddress in EWRAM_START..EWRAM_END - BATTLE_WINDOW_TAIL_BYTES)
        require(
            listOf(battleTypeFlagsAddress, trainerBattleMask, nonWildBattleMask).all { it == null } ||
                listOf(battleTypeFlagsAddress, trainerBattleMask, nonWildBattleMask).all { it != null },
        ) { "battle type descriptor must be complete" }
        require(battleTypeFlagsAddress == null || battleTypeFlagsAddress in EWRAM_START..EWRAM_END - 3)
        require(trainerBattleMask == null || trainerBattleMask.countOneBits() == 1)
        require(
            listOf(saveBlock1PointerAddress, saveBlock2PointerAddress, saveBlock1Size, saveBlock2Size)
                .all { it == null } ||
                listOf(saveBlock1PointerAddress, saveBlock2PointerAddress, saveBlock1Size, saveBlock2Size)
                    .all { it != null },
        ) { "save-block pointer read-plan descriptor must be complete" }
        require(saveBlock1PointerAddress == null || saveBlock1PointerAddress in IWRAM_START..IWRAM_END - 3)
        require(saveBlock2PointerAddress == null || saveBlock2PointerAddress in IWRAM_START..IWRAM_END - 3)
        require(saveBlock1Size == null || saveBlock1Size > 0)
        require(saveBlock2Size == null || saveBlock2Size > 0)
    }

    companion object {
        private const val IWRAM_START = 0x03000000L
        private const val IWRAM_END = 0x03007FFFL
        private const val EWRAM_START = 0x02000000L
        private const val EWRAM_END = 0x0203FFFFL
        private const val BATTLE_WINDOW_TAIL_BYTES = 0x43FL
    }
}

data class Gen3MapPosition(val x: Int, val y: Int)

data class Gen3RuntimeSnapshot(
    val battleActive: Boolean?,
    val areaBaseId: Int?,
    val mapPosition: Gen3MapPosition? = null,
    val targetBattler: Int? = null,
    val encounterKind: BattleEncounterKind = BattleEncounterKind.UNKNOWN,
)

class Gen3RuntimeMemoryDecoder(private val layout: Gen3RuntimeMemoryLayout) {
    val locationWindowOffset: Int = minOf(
        layout.saveBlock1PositionXOffset,
        layout.saveBlock1PositionYOffset,
        layout.saveBlock1MapGroupOffset,
        layout.saveBlock1MapNumberOffset,
    )
    val locationWindowBytes: Int = maxOf(
        layout.saveBlock1PositionXOffset + 2,
        layout.saveBlock1PositionYOffset + 2,
        layout.saveBlock1MapGroupOffset + 1,
        layout.saveBlock1MapNumberOffset + 1,
    ) - locationWindowOffset

    fun decodeBattleActive(bytes: ByteArray?): Boolean? = bytes
        ?.singleOrNull()
        ?.let { value -> value.toInt() and layout.inBattleMask != 0 }

    fun decodeArea(bytes: ByteArray?): Int? {
        if (bytes == null) return null
        if (bytes.size == MAP_ID_BYTES) {
            val group = bytes[0].toInt() and 0xFF
            val map = bytes[1].toInt() and 0xFF
            return (group shl 8) or map
        }
        val groupIndex = layout.saveBlock1MapGroupOffset - locationWindowOffset
        val mapIndex = layout.saveBlock1MapNumberOffset - locationWindowOffset
        if (groupIndex !in bytes.indices || mapIndex !in bytes.indices) return null
        val group = bytes[groupIndex].toInt() and 0xFF
        val map = bytes[mapIndex].toInt() and 0xFF
        return (group shl 8) or map
    }

    fun decodePosition(bytes: ByteArray?): Gen3MapPosition? {
        if (bytes == null) return null
        val xIndex = layout.saveBlock1PositionXOffset - locationWindowOffset
        val yIndex = layout.saveBlock1PositionYOffset - locationWindowOffset
        if (xIndex < 0 || yIndex < 0 || xIndex + 1 !in bytes.indices || yIndex + 1 !in bytes.indices) return null
        return Gen3MapPosition(s16le(bytes, xIndex), s16le(bytes, yIndex))
            .takeIf { it.x >= 0 && it.y >= 0 }
    }

    private fun s16le(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)).toShort().toInt()

    fun decodeTargetBattler(bytes: ByteArray?): Int? = bytes
        ?.singleOrNull()
        ?.toInt()
        ?.and(0xFF)
        ?.takeIf { it in 0 until MAX_BATTLERS }

    fun decodeBattleEncounterKind(bytes: ByteArray?): BattleEncounterKind {
        val trainerMask = layout.trainerBattleMask ?: return BattleEncounterKind.UNKNOWN
        val nonWildMask = layout.nonWildBattleMask ?: return BattleEncounterKind.UNKNOWN
        if (layout.battleTypeFlagsAddress == null || bytes?.size != BATTLE_TYPE_FLAGS_BYTES) {
            return BattleEncounterKind.UNKNOWN
        }
        val flags = bytes.foldIndexed(0) { index, value, byte ->
            value or ((byte.toInt() and 0xFF) shl (index * 8))
        }
        return when {
            flags and nonWildMask != 0 -> BattleEncounterKind.UNKNOWN
            flags and trainerMask != 0 -> BattleEncounterKind.TRAINER
            else -> BattleEncounterKind.WILD
        }
    }

    companion object {
        const val MAP_ID_BYTES = 2
        const val BATTLE_TYPE_FLAGS_BYTES = 4
        private const val MAX_BATTLERS = 4
    }
}

fun BattleMemorySample.withLiveTargetBattler(targetBattler: Int?): BattleMemorySample {
    if (opponents.size <= 1) return this
    val opponentIndex = opponents.indexOfFirst { it.battlerIndex == targetBattler }
    val resolved = opponentIndex >= 0
    return copy(
        target = BattleTarget(
            opponentIndex = opponentIndex.takeIf { resolved } ?: 0,
            mode = if (resolved) TargetMode.AUTOMATIC else TargetMode.MANUAL_TARGET_FALLBACK,
        ),
        capabilities = capabilities + (
            BattleCapability.SELECTED_TARGET to
                if (resolved) CapabilityState.AVAILABLE else CapabilityState.NOT_FOUND
            ),
    )
}
