package com.darkaxt.dualdex.mapper

import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.darkaxt.dualdex.retroarch.UdpNetworkCommandTransport
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.google.gson.GsonBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class MapperSnapshotView(
    val id: String,
    val label: String,
    val customLabel: String?,
    val capturedAtEpochMs: Long,
    val bytes: Int,
)

data class MapperDiffView(val changedBytes: Int, val ranges: Int, val omittedRanges: Int)

data class MapperStateView(
    val enabled: Boolean = false,
    val privacyAcknowledged: Boolean = false,
    val coreIdentity: String? = null,
    val contentIdentity: String? = null,
    val descriptors: List<MemoryDescriptor> = emptyList(),
    val captureLabel: String? = null,
    val completedBytes: Int = 0,
    val totalBytes: Int = 0,
    val snapshots: List<MapperSnapshotView> = emptyList(),
    val latestDiff: MapperDiffView? = null,
    val error: String? = null,
)

/** Optional app adapter. It owns its UDP socket, files, and worker independently of the catalog runtime. */
class MemoryMapperCoordinator(
    private val sessionStore: MapperSessionStore,
    private val retroArch: () -> RetroArchView,
    private val transportFactory: () -> NetworkCommandTransport = { UdpNetworkCommandTransport() },
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "dualdex-memory-mapper").apply { isDaemon = true }
    },
    startHeartbeat: Boolean = true,
) : AutoCloseable {
    private var state = MapperStateView()
    private var transport: NetworkCommandTransport? = null
    private var lab: MemoryMapperLab? = null
    private var active: ActiveCapture? = null

    init {
        if (startHeartbeat) scheduler.scheduleWithFixedDelay(::heartbeatSafely, 0, HEARTBEAT_MILLIS, TimeUnit.MILLISECONDS)
    }

    @Synchronized fun snapshot(): MapperStateView = state

    @Synchronized
    fun action(type: String, values: Map<String, String?>): MapperStateView {
        when (type.uppercase()) {
            "ENABLE" -> enable(values["privacyAcknowledged"].toBoolean())
            "DISABLE" -> disable()
            "CAPTURE" -> capture(values)
            "CLEAR_SESSIONS" -> {
                disable()
                sessionStore.clear()
                lab = null
                state = MapperStateView()
            }
            else -> error("unknown mapper action: $type")
        }
        return state
    }

    @Synchronized
    fun exportRaw(): ByteArray {
        check(state.privacyAcknowledged) { "enable and acknowledge the mapper before exporting raw memory" }
        val record = requireNotNull(lab) { "there is no mapper session to export" }.record()
        return GsonBuilder().serializeNulls().create()
            .toJson(MapperExport.create(record, includeRaw = true, privacyAcknowledged = true))
            .toByteArray(Charsets.UTF_8)
    }

    @Synchronized
    fun heartbeatNow() {
        val capture = active ?: return
        when (val progress = capture.reader.heartbeat()) {
            HeartbeatReadState.Idle -> Unit
            is HeartbeatReadState.Reading -> state = state.copy(
                completedBytes = progress.completedBytes, totalBytes = progress.totalBytes,
            )
            is HeartbeatReadState.Failed -> {
                active = null
                state = state.copy(captureLabel = null, completedBytes = 0, totalBytes = 0, error = progress.reason)
            }
            is HeartbeatReadState.Complete -> {
                active = null
                when (val result = requireNotNull(lab).record(capture.label, progress.regions, capture.customLabel)) {
                    CaptureResult.Disabled -> state = state.copy(captureLabel = null, error = "mapper was disabled")
                    is CaptureResult.Failed -> state = state.copy(captureLabel = null, error = result.reason)
                    is CaptureResult.Captured -> {
                        val record = requireNotNull(lab).record()
                        sessionStore.write(record)
                        state = state.copy(
                            captureLabel = null, completedBytes = progress.regions.values.sumOf(ByteArray::size),
                            totalBytes = progress.regions.values.sumOf(ByteArray::size), snapshots = snapshotViews(record),
                            latestDiff = latestDiff(record), error = null,
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        disable()
        scheduler.shutdown()
    }

    private fun enable(privacyAcknowledged: Boolean) {
        require(privacyAcknowledged) { "acknowledge the mapper privacy warning before enabling it" }
        if (state.enabled) return
        val session = retroArch()
        require(session.connection in setOf("PLAYING", "PAUSED")) { "RetroArch must be running supported content" }
        val descriptors = descriptorsFor(session.systemId)
        val candidate = transportFactory()
        val newLab = MemoryMapperLab(
            transport = ReadOnlyMemoryTransport { error("heartbeat captures use the non-blocking transport") },
            descriptors = descriptors,
            coreIdentity = session.systemId ?: "UNKNOWN_CORE",
            contentIdentity = session.contentCrc32 ?: session.gameBasename ?: "UNKNOWN_CONTENT",
        ).also { it.enable(true) }
        transport = candidate
        lab = newLab
        state = MapperStateView(
            enabled = true, privacyAcknowledged = true, coreIdentity = session.systemId,
            contentIdentity = session.contentCrc32 ?: session.gameBasename, descriptors = descriptors,
        )
    }

    private fun disable() {
        active = null
        lab?.disable()
        transport?.close()
        transport = null
        state = state.copy(enabled = false, captureLabel = null, completedBytes = 0, totalBytes = 0)
    }

    private fun capture(values: Map<String, String?>) {
        require(state.enabled) { "the memory mapper is disabled" }
        check(active == null) { "a memory capture is already in progress" }
        val label = MapperLabel.valueOf(requireNotNull(values["label"]) { "capture label is required" }.uppercase())
        val custom = values["customLabel"]?.trim()?.takeIf(String::isNotEmpty)
        require(label != MapperLabel.CUSTOM || custom != null) { "a custom mapper label is required" }
        val network = requireNotNull(transport)
        val reader = HeartbeatMemoryReader(
            RawCommandSender(network::send), RawCommandPoller(network::poll), state.descriptors,
        )
        active = ActiveCapture(label, custom, reader)
        val progress = reader.start() as HeartbeatReadState.Reading
        state = state.copy(
            captureLabel = custom ?: label.name, completedBytes = progress.completedBytes,
            totalBytes = progress.totalBytes, error = null,
        )
    }

    private fun heartbeatSafely() {
        runCatching(::heartbeatNow).onFailure { failure ->
            synchronized(this) {
                active = null
                state = state.copy(captureLabel = null, error = failure.message ?: failure.javaClass.simpleName)
            }
        }
    }

    private fun descriptorsFor(systemId: String?): List<MemoryDescriptor> {
        val normalized = systemId?.uppercase().orEmpty()
        return when {
        normalized == "GBA" || "GAME BOY ADVANCE" in normalized || "GAME_BOY_ADVANCE" in normalized -> listOf(
            MemoryDescriptor("ewram", "External work RAM", 0x0200_0000, 0x40000),
            MemoryDescriptor("iwram", "Internal work RAM", 0x0300_0000, 0x8000),
        )
        normalized in setOf("GB", "GBC") || "GAME BOY" in normalized || "GAME_BOY" in normalized -> listOf(
            MemoryDescriptor("wram", "Work RAM", 0xC000, 0x2000),
            MemoryDescriptor("hram", "High RAM", 0xFF80, 0x7F),
        )
        else -> error("the active RetroArch system is not supported by the mapper")
        }
    }

    private fun snapshotViews(record: MapperSessionRecord) = record.snapshots.map { snapshot ->
        MapperSnapshotView(snapshot.id, snapshot.label.name, snapshot.customLabel, snapshot.capturedAtEpochMs, snapshot.regions.sumOf { it.bytes.size })
    }

    private fun latestDiff(record: MapperSessionRecord): MapperDiffView? = record.snapshots.takeLast(2).takeIf { it.size == 2 }
        ?.let { SnapshotDiff.between(it[0], it[1]) }
        ?.let { MapperDiffView(it.changedBytes, it.ranges.size, it.omittedRanges) }

    private data class ActiveCapture(
        val label: MapperLabel,
        val customLabel: String?,
        val reader: HeartbeatMemoryReader,
    )

    companion object {
        // One request remains in flight at a time. A short heartbeat keeps a full GBA capture
        // practical without turning a missing reply into a cancellation timeout.
        private const val HEARTBEAT_MILLIS = 25L
    }
}
