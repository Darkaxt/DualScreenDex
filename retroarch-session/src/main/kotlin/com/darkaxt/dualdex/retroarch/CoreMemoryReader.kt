package com.darkaxt.dualdex.retroarch

data class CoreMemoryRegion(
    val id: String,
    val baseAddress: Long,
    val size: Int,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) { "memory region id is invalid" }
        require(baseAddress in 0..0xFFFF_FFFFL) { "memory region address is outside the 32-bit core bus" }
        require(size > 0) { "memory region must not be empty" }
        require(baseAddress + size - 1 <= 0xFFFF_FFFFL) { "memory region exceeds the 32-bit core bus" }
    }
}

sealed interface CoreMemoryReadState {
    data object Idle : CoreMemoryReadState
    data class Reading(val completedBytes: Int, val totalBytes: Int) : CoreMemoryReadState
    data class Complete(val regions: Map<String, ByteArray>) : CoreMemoryReadState
    data class Failed(val reason: String) : CoreMemoryReadState
}

private data class CoreMemoryRequest(
    val region: CoreMemoryRegion,
    val address: Long,
    val length: Int,
)

/**
 * Heartbeat-driven, read-only RetroArch core-memory transport. There is deliberately no write operation
 * and absence of a UDP reply leaves the state pending rather than cancelling it by elapsed time.
 */
class CoreMemoryReadSession(
    private val sender: (ByteArray) -> Unit,
    private val poller: () -> ByteArray?,
    private val maximumChunkBytes: Int = DEFAULT_CHUNK_BYTES,
) {
    private var regions = emptyList<CoreMemoryRegion>()
    private var requests = emptyList<CoreMemoryRequest>()
    private var buffers = emptyMap<String, ByteArray>()
    private var requestIndex = -1
    private var completedBytes = 0
    private var terminal: CoreMemoryReadState? = null

    init {
        require(maximumChunkBytes in 1..MAX_CHUNK_BYTES) { "core-memory chunk is outside the safe UDP packet limit" }
    }

    fun start(regions: List<CoreMemoryRegion>): CoreMemoryReadState {
        check(requestIndex < 0) { "core-memory reader has already started" }
        require(regions.isNotEmpty()) { "at least one core-memory region is required" }
        require(regions.map(CoreMemoryRegion::id).distinct().size == regions.size) { "core-memory region ids must be unique" }
        require(regions.sumOf { it.size.toLong() } <= MAX_TOTAL_BYTES) { "core-memory read exceeds the total byte limit" }

        this.regions = regions.toList()
        requests = regions.flatMap(::requestsFor)
        buffers = regions.associate { it.id to ByteArray(it.size) }
        requestIndex = 0
        sendCurrent()
        return state()
    }

    fun heartbeat(): CoreMemoryReadState {
        terminal?.let { return it }
        if (requestIndex < 0) return CoreMemoryReadState.Idle
        while (true) {
            val response = poller() ?: break
            val request = requests[requestIndex]
            val bytes = runCatching { parse(request, response) }.getOrElse { failure ->
                return CoreMemoryReadState.Failed(failure.message ?: failure.javaClass.simpleName).also { terminal = it }
            }
            val offset = (request.address - request.region.baseAddress).toInt()
            bytes.copyInto(requireNotNull(buffers[request.region.id]), offset)
            completedBytes += bytes.size
            requestIndex++
            if (requestIndex >= requests.size) {
                return CoreMemoryReadState.Complete(buffers.mapValues { it.value.copyOf() }).also { terminal = it }
            }
            sendCurrent()
        }
        return state()
    }

    private fun requestsFor(region: CoreMemoryRegion): List<CoreMemoryRequest> = buildList {
        var offset = 0
        while (offset < region.size) {
            val length = minOf(maximumChunkBytes, region.size - offset)
            add(CoreMemoryRequest(region, region.baseAddress + offset, length))
            offset += length
        }
    }

    private fun sendCurrent() {
        val request = requests[requestIndex]
        val command = "READ_CORE_MEMORY ${request.address.toString(16)} ${request.length}"
        sender(command.toByteArray(Charsets.US_ASCII))
    }

    private fun parse(request: CoreMemoryRequest, response: ByteArray): ByteArray {
        val parts = response.toString(Charsets.US_ASCII)
            .trim()
            .trimEnd('\u0000')
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
        require(parts.size >= 3 && parts[0] == "READ_CORE_MEMORY") { "invalid READ_CORE_MEMORY reply" }
        require(parts[1].toLongOrNull(16) == request.address) { "READ_CORE_MEMORY reply address did not match the request" }
        require(!parts[2].equals("ERROR", ignoreCase = true)) {
            "RetroArch rejected the memory read: ${parts.drop(2).joinToString(" ")}"
        }
        val payload = parts.drop(2)
        require(payload.size == request.length) {
            "short READ_CORE_MEMORY reply: expected ${request.length} bytes, received ${payload.size}"
        }
        return ByteArray(payload.size) { index ->
            val value = payload[index]
            require(value.matches(Regex("[0-9A-Fa-f]{2}"))) { "invalid memory byte in RetroArch reply" }
            value.toInt(16).toByte()
        }
    }

    private fun state(): CoreMemoryReadState = terminal
        ?: CoreMemoryReadState.Reading(completedBytes, regions.sumOf(CoreMemoryRegion::size))

    companion object {
        const val DEFAULT_CHUNK_BYTES = 512
        const val MAX_CHUNK_BYTES = 1024
        private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024
    }
}
