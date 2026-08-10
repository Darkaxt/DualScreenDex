package com.darkaxt.dualdex.mapper

import java.util.Base64

data class MapperExportBundle(
    val schema: Int,
    val sessionId: String,
    val coreIdentity: String,
    val contentIdentity: String,
    val descriptors: List<MemoryDescriptor>,
    val snapshots: List<ExportedMemorySnapshot>,
    val diffs: List<ExportedSnapshotDiff>,
    val containsRawMemory: Boolean,
)

data class ExportedMemorySnapshot(
    val id: String,
    val label: MapperLabel,
    val customLabel: String?,
    val capturedAtEpochMs: Long,
    val regions: List<ExportedMemoryRegion>,
)

data class ExportedMemoryRegion(
    val descriptorId: String,
    val baseAddress: Long,
    val size: Int,
    val sha256: String,
    val base64Bytes: String?,
)

data class ExportedSnapshotDiff(
    val beforeSnapshotId: String,
    val afterSnapshotId: String,
    val changedBytes: Int,
    val ranges: List<ExportedChangedRange>,
    val omittedRanges: Int,
)

data class ExportedChangedRange(
    val descriptorId: String,
    val address: Long,
    val offset: Int,
    val length: Int,
    val beforeBase64: String?,
    val afterBase64: String?,
)

object MapperExport {
    fun create(
        session: MapperSessionRecord,
        includeRaw: Boolean,
        privacyAcknowledged: Boolean,
    ): MapperExportBundle {
        require(!includeRaw || privacyAcknowledged) { "raw memory export requires explicit privacy confirmation" }
        return MapperExportBundle(
            schema = 1,
            sessionId = session.id,
            coreIdentity = session.coreIdentity,
            contentIdentity = session.contentIdentity,
            descriptors = session.descriptors,
            snapshots = session.snapshots.map { snapshot ->
                ExportedMemorySnapshot(
                    id = snapshot.id,
                    label = snapshot.label,
                    customLabel = snapshot.customLabel,
                    capturedAtEpochMs = snapshot.capturedAtEpochMs,
                    regions = snapshot.regions.map { region ->
                        ExportedMemoryRegion(
                            descriptorId = region.descriptor.id,
                            baseAddress = region.descriptor.baseAddress,
                            size = region.bytes.size,
                            sha256 = region.sha256,
                            base64Bytes = region.bytes.takeIf { includeRaw }?.let(Base64.getEncoder()::encodeToString),
                        )
                    },
                )
            },
            diffs = session.snapshots.zipWithNext().map { (before, after) ->
                val diff = SnapshotDiff.between(before, after)
                ExportedSnapshotDiff(
                    beforeSnapshotId = diff.beforeSnapshotId,
                    afterSnapshotId = diff.afterSnapshotId,
                    changedBytes = diff.changedBytes,
                    ranges = diff.ranges.map { range ->
                        ExportedChangedRange(
                            descriptorId = range.descriptorId,
                            address = range.address,
                            offset = range.offset,
                            length = range.after.size,
                            beforeBase64 = range.before.takeIf { includeRaw }?.let(Base64.getEncoder()::encodeToString),
                            afterBase64 = range.after.takeIf { includeRaw }?.let(Base64.getEncoder()::encodeToString),
                        )
                    },
                    omittedRanges = diff.omittedRanges,
                )
            },
            containsRawMemory = includeRaw,
        )
    }
}
