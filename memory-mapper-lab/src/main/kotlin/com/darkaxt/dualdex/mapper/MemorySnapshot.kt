package com.darkaxt.dualdex.mapper

enum class MapperLabel {
    OVERWORLD,
    BATTLE_START,
    MOVE_SELECTED,
    MOVE_EXECUTED,
    TARGET_CHANGED,
    OPPONENT_SWITCHED,
    BATTLE_END,
    CUSTOM,
}

data class MemoryRegionSnapshot(
    val descriptor: MemoryDescriptor,
    val bytes: ByteArray,
    val sha256: String,
)

data class MemorySnapshot(
    val id: String,
    val label: MapperLabel,
    val customLabel: String?,
    val capturedAtEpochMs: Long,
    val coreIdentity: String,
    val contentIdentity: String,
    val regions: List<MemoryRegionSnapshot>,
)

data class MapperSessionRecord(
    val id: String,
    val coreIdentity: String,
    val contentIdentity: String,
    val descriptors: List<MemoryDescriptor>,
    val snapshots: List<MemorySnapshot>,
)
