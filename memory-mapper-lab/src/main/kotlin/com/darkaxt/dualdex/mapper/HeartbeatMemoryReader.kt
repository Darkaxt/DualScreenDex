package com.darkaxt.dualdex.mapper

import com.darkaxt.dualdex.retroarch.CoreMemoryReadSession
import com.darkaxt.dualdex.retroarch.CoreMemoryReadState
import com.darkaxt.dualdex.retroarch.CoreMemoryRegion

fun interface RawCommandSender {
    fun send(command: ByteArray)
}

fun interface RawCommandPoller {
    fun poll(): ByteArray?
}

sealed interface HeartbeatReadState {
    data object Idle : HeartbeatReadState
    data class Reading(val completedBytes: Int, val totalBytes: Int) : HeartbeatReadState
    data class Complete(val regions: Map<String, ByteArray>) : HeartbeatReadState
    data class Failed(val reason: String) : HeartbeatReadState
}

/** Mapper compatibility adapter over the shared production-safe read-only transport. */
class HeartbeatMemoryReader(
    sender: RawCommandSender,
    poller: RawCommandPoller,
    descriptors: List<MemoryDescriptor>,
) {
    private val reader: CoreMemoryReadSession
    private val regions: List<CoreMemoryRegion>

    init {
        require(descriptors.isNotEmpty()) { "at least one memory descriptor is required" }
        require(descriptors.sumOf { it.size.toLong() } <= MemoryMapperLab.MAX_CAPTURE_BYTES) {
            "mapper capture exceeds the total byte limit"
        }
        regions = descriptors.map { CoreMemoryRegion(it.id, it.baseAddress, it.size) }
        reader = CoreMemoryReadSession(sender::send, poller::poll, RetroArchReadProtocol.MAX_CHUNK_BYTES)
    }

    fun start(): HeartbeatReadState = reader.start(regions).toMapperState()

    fun heartbeat(): HeartbeatReadState = reader.heartbeat().toMapperState()

    private fun CoreMemoryReadState.toMapperState(): HeartbeatReadState = when (this) {
        CoreMemoryReadState.Idle -> HeartbeatReadState.Idle
        is CoreMemoryReadState.Reading -> HeartbeatReadState.Reading(completedBytes, totalBytes)
        is CoreMemoryReadState.Complete -> HeartbeatReadState.Complete(regions)
        is CoreMemoryReadState.Failed -> HeartbeatReadState.Failed(reason)
    }
}
