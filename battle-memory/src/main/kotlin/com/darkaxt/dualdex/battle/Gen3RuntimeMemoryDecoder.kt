package com.darkaxt.dualdex.battle

data class Gen3RuntimeMemoryLayout(
    val mainStructSize: Int,
    val inBattleByteOffset: Int,
    val inBattleMask: Int,
    val saveBlock1MapGroupOffset: Int,
    val saveBlock1MapNumberOffset: Int,
    val multiUsePlayerCursorOffsetFromMain: Int? = null,
) {
    init {
        require(mainStructSize in 1..0x8000)
        require(inBattleByteOffset in 0 until mainStructSize)
        require(inBattleMask in 1..0xFF && inBattleMask.countOneBits() == 1)
        require(saveBlock1MapGroupOffset >= 0)
        require(saveBlock1MapNumberOffset == saveBlock1MapGroupOffset + 1)
        require(multiUsePlayerCursorOffsetFromMain == null || multiUsePlayerCursorOffsetFromMain >= mainStructSize)
    }
}

data class Gen3RuntimeSnapshot(
    val battleActive: Boolean?,
    val areaBaseId: Int?,
    val targetBattler: Int? = null,
)

class Gen3RuntimeMemoryDecoder(private val layout: Gen3RuntimeMemoryLayout) {
    fun decodeBattleActive(bytes: ByteArray?): Boolean? = bytes
        ?.singleOrNull()
        ?.let { value -> value.toInt() and layout.inBattleMask != 0 }

    fun decodeArea(bytes: ByteArray?): Int? {
        if (bytes?.size != MAP_ID_BYTES) return null
        val group = bytes[0].toInt() and 0xFF
        val map = bytes[1].toInt() and 0xFF
        return (group shl 8) or map
    }

    fun decodeTargetBattler(bytes: ByteArray?): Int? = bytes
        ?.singleOrNull()
        ?.toInt()
        ?.and(0xFF)
        ?.takeIf { it in 0 until MAX_BATTLERS }

    companion object {
        const val MAP_ID_BYTES = 2
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
