package com.darkaxt.dualdex.mapper

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

/** A non-blocking, heartbeat-driven reader. Lack of a reply keeps the capture pending; it never times out. */
class HeartbeatMemoryReader(
    private val sender: RawCommandSender,
    private val poller: RawCommandPoller,
    private val descriptors: List<MemoryDescriptor>,
) {
    private val requests = descriptors.flatMap { descriptor ->
        RetroArchReadProtocol.chunks(descriptor).map { descriptor to it }
    }
    private val buffers = descriptors.associate { it.id to ByteArray(it.size) }
    private var requestIndex = -1
    private var completedBytes = 0
    private var terminal: HeartbeatReadState? = null

    init {
        require(descriptors.isNotEmpty()) { "at least one memory descriptor is required" }
        require(descriptors.sumOf { it.size.toLong() } <= MemoryMapperLab.MAX_CAPTURE_BYTES) {
            "mapper capture exceeds the total byte limit"
        }
    }

    fun start(): HeartbeatReadState {
        check(requestIndex < 0) { "memory reader has already started" }
        requestIndex = 0
        sendCurrent()
        return state()
    }

    fun heartbeat(): HeartbeatReadState {
        terminal?.let { return it }
        if (requestIndex < 0) return HeartbeatReadState.Idle
        while (true) {
            val payload = poller.poll() ?: break
            val (descriptor, request) = requests[requestIndex]
            val bytes = runCatching {
                RetroArchReadProtocol.parse(request, payload.toString(Charsets.US_ASCII).trim().trimEnd('\u0000'))
            }.getOrElse { failure ->
                return HeartbeatReadState.Failed(failure.message ?: failure.javaClass.simpleName).also { terminal = it }
            }
            val offset = (request.address - descriptor.baseAddress).toInt()
            bytes.copyInto(requireNotNull(buffers[descriptor.id]), offset)
            completedBytes += bytes.size
            requestIndex++
            if (requestIndex >= requests.size) {
                return HeartbeatReadState.Complete(buffers.mapValues { it.value.copyOf() }).also { terminal = it }
            }
            sendCurrent()
        }
        return state()
    }

    private fun sendCurrent() {
        val request = requests[requestIndex].second
        sender.send(RetroArchReadProtocol.command(request).toByteArray(Charsets.US_ASCII))
    }

    private fun state(): HeartbeatReadState = terminal
        ?: HeartbeatReadState.Reading(completedBytes, descriptors.sumOf(MemoryDescriptor::size))
}
