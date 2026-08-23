package com.darkaxt.dualdex.mapper

import java.security.MessageDigest
import java.util.UUID

sealed interface CaptureResult {
    data object Disabled : CaptureResult
    data class Captured(val snapshot: MemorySnapshot) : CaptureResult
    data class Failed(val reason: String) : CaptureResult
}

class MemoryMapperLab(
    private val transport: ReadOnlyMemoryTransport,
    private val descriptors: List<MemoryDescriptor>,
    private val coreIdentity: String = "UNKNOWN_CORE",
    private val contentIdentity: String = "UNKNOWN_CONTENT",
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val sessionId = idFactory()
    private val history = mutableListOf<MemorySnapshot>()
    private var retainedRawBytes = 0L

    var enabled: Boolean = false
        private set

    init {
        require(descriptors.isNotEmpty()) { "at least one bounded memory descriptor is required" }
        require(descriptors.map { it.id }.distinct().size == descriptors.size) { "memory descriptor ids must be unique" }
        require(descriptors.sumOf { it.size.toLong() } <= MAX_CAPTURE_BYTES) { "mapper capture exceeds the total byte limit" }
    }

    fun enable(privacyAcknowledged: Boolean) {
        check(privacyAcknowledged) { "the mapper privacy warning must be acknowledged before enabling reads" }
        enabled = true
    }

    fun disable() {
        enabled = false
    }

    fun capture(label: MapperLabel, customLabel: String? = null): CaptureResult {
        if (!enabled) return CaptureResult.Disabled
        if (label == MapperLabel.CUSTOM && customLabel.isNullOrBlank()) {
            return CaptureResult.Failed("a custom mapper label is required")
        }
        return runCatching {
            val regions = descriptors.map { descriptor ->
                val bytes = transport.read(MemoryRead(descriptor.baseAddress, descriptor.size))
                require(bytes.size == descriptor.size) {
                    "short read for ${descriptor.id}: expected ${descriptor.size} bytes, received ${bytes.size}"
                }
                val immutable = bytes.copyOf()
                MemoryRegionSnapshot(descriptor, immutable, sha256(immutable))
            }
            MemorySnapshot(
                id = idFactory(),
                label = label,
                customLabel = customLabel?.trim()?.takeIf(String::isNotEmpty),
                capturedAtEpochMs = clock(),
                coreIdentity = coreIdentity,
                contentIdentity = contentIdentity,
                regions = regions,
            ).also(::retain)
        }.fold(CaptureResult::Captured) { CaptureResult.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun record(label: MapperLabel, regions: Map<String, ByteArray>, customLabel: String? = null): CaptureResult {
        if (!enabled) return CaptureResult.Disabled
        if (label == MapperLabel.CUSTOM && customLabel.isNullOrBlank()) {
            return CaptureResult.Failed("a custom mapper label is required")
        }
        return runCatching {
            val snapshots = descriptors.map { descriptor ->
                val bytes = requireNotNull(regions[descriptor.id]) { "memory region ${descriptor.id} is missing" }
                require(bytes.size == descriptor.size) { "memory region ${descriptor.id} has the wrong size" }
                val immutable = bytes.copyOf()
                MemoryRegionSnapshot(descriptor, immutable, sha256(immutable))
            }
            MemorySnapshot(
                id = idFactory(), label = label, customLabel = customLabel?.trim()?.takeIf(String::isNotEmpty),
                capturedAtEpochMs = clock(), coreIdentity = coreIdentity, contentIdentity = contentIdentity,
                regions = snapshots,
            ).also(::retain)
        }.fold(CaptureResult::Captured) { CaptureResult.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun snapshots(): List<MemorySnapshot> = history.toList()

    fun record(): MapperSessionRecord = MapperSessionRecord(
        id = sessionId,
        coreIdentity = coreIdentity,
        contentIdentity = contentIdentity,
        descriptors = descriptors,
        snapshots = snapshots(),
    )

    private fun retain(snapshot: MemorySnapshot) {
        history += snapshot
        retainedRawBytes += snapshot.rawByteCount()
        while (history.size > MAX_SNAPSHOT_COUNT || retainedRawBytes > MAX_HISTORY_BYTES) {
            retainedRawBytes -= history.removeAt(0).rawByteCount()
        }
    }

    private fun MemorySnapshot.rawByteCount(): Long = regions.sumOf { it.bytes.size.toLong() }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_CAPTURE_BYTES = 2L * 1024 * 1024
        const val MAX_SNAPSHOT_COUNT = 32
        const val MAX_HISTORY_BYTES = 16L * 1024 * 1024
    }
}
