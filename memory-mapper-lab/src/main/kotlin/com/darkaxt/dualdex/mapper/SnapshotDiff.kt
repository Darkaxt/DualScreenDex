package com.darkaxt.dualdex.mapper

data class ChangedMemoryRange(
    val descriptorId: String,
    val address: Long,
    val offset: Int,
    val before: ByteArray,
    val after: ByteArray,
)

data class MemorySnapshotDiff(
    val beforeSnapshotId: String,
    val afterSnapshotId: String,
    val changedBytes: Int,
    val ranges: List<ChangedMemoryRange>,
    val omittedRanges: Int,
)

object SnapshotDiff {
    fun between(before: MemorySnapshot, after: MemorySnapshot, maximumRanges: Int = 256): MemorySnapshotDiff {
        require(maximumRanges > 0) { "maximum diff ranges must be positive" }
        require(before.coreIdentity == after.coreIdentity && before.contentIdentity == after.contentIdentity) {
            "snapshots belong to different core/content sessions"
        }
        val beforeRegions = before.regions.associateBy { it.descriptor.id }
        val allRanges = mutableListOf<ChangedMemoryRange>()
        var changedBytes = 0
        after.regions.forEach { afterRegion ->
            val beforeRegion = requireNotNull(beforeRegions[afterRegion.descriptor.id]) {
                "snapshot descriptor ${afterRegion.descriptor.id} is missing from the baseline"
            }
            require(beforeRegion.bytes.size == afterRegion.bytes.size) { "snapshot descriptor sizes changed" }
            var index = 0
            while (index < afterRegion.bytes.size) {
                if (beforeRegion.bytes[index] == afterRegion.bytes[index]) {
                    index++
                    continue
                }
                val start = index
                while (index < afterRegion.bytes.size && beforeRegion.bytes[index] != afterRegion.bytes[index]) index++
                changedBytes += index - start
                allRanges += ChangedMemoryRange(
                    descriptorId = afterRegion.descriptor.id,
                    address = afterRegion.descriptor.baseAddress + start,
                    offset = start,
                    before = beforeRegion.bytes.copyOfRange(start, index),
                    after = afterRegion.bytes.copyOfRange(start, index),
                )
            }
        }
        return MemorySnapshotDiff(
            beforeSnapshotId = before.id,
            afterSnapshotId = after.id,
            changedBytes = changedBytes,
            ranges = allRanges.take(maximumRanges),
            omittedRanges = (allRanges.size - maximumRanges).coerceAtLeast(0),
        )
    }
}
