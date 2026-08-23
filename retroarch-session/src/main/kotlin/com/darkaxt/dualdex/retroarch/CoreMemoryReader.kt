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
 * and absence of a UDP reply retries the same idempotent request on the next heartbeat rather than
 * cancelling it by elapsed time.
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
        var advanced = false
        while (true) {
            val response = poller() ?: break
            val request = requests[requestIndex]
            val offset = (request.address - request.region.baseAddress).toInt()
            val destination = requireNotNull(buffers[request.region.id])
            when (val result = CoreMemoryReplyParser.parse(request.address, request.length, response, destination, offset)) {
                CoreMemoryReply.Ignored -> continue
                is CoreMemoryReply.Failed -> {
                    return CoreMemoryReadState.Failed(result.reason).also { terminal = it }
                }
                CoreMemoryReply.Matched -> Unit
            }
            completedBytes += request.length
            requestIndex++
            advanced = true
            if (requestIndex >= requests.size) {
                return CoreMemoryReadState.Complete(buffers.mapValues { it.value.copyOf() }).also { terminal = it }
            }
            sendCurrent()
        }
        if (!advanced) sendCurrent()
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

    private fun state(): CoreMemoryReadState = terminal
        ?: CoreMemoryReadState.Reading(completedBytes, regions.sumOf(CoreMemoryRegion::size))

    companion object {
        const val DEFAULT_CHUNK_BYTES = 512
        const val MAX_CHUNK_BYTES = 1024
        private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024
    }
}

private sealed interface CoreMemoryReply {
    data object Ignored : CoreMemoryReply
    data object Matched : CoreMemoryReply
    data class Failed(val reason: String) : CoreMemoryReply
}

private object CoreMemoryReplyParser {
    fun parse(
        expectedAddress: Long,
        expectedLength: Int,
        response: ByteArray,
        destination: ByteArray,
        destinationOffset: Int,
    ): CoreMemoryReply {
        val cursor = AsciiReplyCursor(response)
        if (!cursor.readCommand()) return CoreMemoryReply.Ignored
        if (cursor.readHexAddress() != expectedAddress) return CoreMemoryReply.Ignored

        var received = 0
        while (true) {
            val token = cursor.readPayloadToken()
            if (token == AsciiReplyCursor.MISSING) break
            if (received == 0 && token == AsciiReplyCursor.ERROR) {
                return CoreMemoryReply.Failed("RetroArch rejected the memory read")
            }
            if (token !in 0..0xff) {
                return CoreMemoryReply.Failed("invalid memory byte in RetroArch reply")
            }
            if (received < expectedLength) destination[destinationOffset + received] = token.toByte()
            received++
        }
        if (received != expectedLength) {
            return CoreMemoryReply.Failed(
                "short READ_CORE_MEMORY reply: expected $expectedLength bytes, received $received",
            )
        }
        return CoreMemoryReply.Matched
    }
}

private class AsciiReplyCursor(private val bytes: ByteArray) {
    private var index = 0

    fun readCommand(): Boolean {
        skipSeparators()
        for (expected in COMMAND) {
            if (index >= bytes.size || bytes[index].toInt() != expected.code) return false
            index++
        }
        return index >= bytes.size || isSeparator(bytes[index])
    }

    fun readHexAddress(): Long? {
        skipSeparators()
        var value = 0L
        var digits = 0
        var valid = true
        while (index < bytes.size && !isSeparator(bytes[index])) {
            val nibble = hexNibble(bytes[index])
            if (nibble < 0 || digits >= 8) valid = false else value = (value shl 4) or nibble.toLong()
            digits++
            index++
        }
        return value.takeIf { valid && digits > 0 }
    }

    fun readPayloadToken(): Int {
        skipSeparators()
        if (index >= bytes.size) return MISSING
        var value = 0
        var digits = 0
        var validHex = true
        var errorToken = true
        while (index < bytes.size && !isSeparator(bytes[index])) {
            val byte = bytes[index]
            val nibble = hexNibble(byte)
            if (nibble < 0 || digits >= 2) validHex = false else value = (value shl 4) or nibble
            if (digits >= ERROR_TOKEN.length || !byte.equalsAsciiIgnoreCase(ERROR_TOKEN[digits])) errorToken = false
            digits++
            index++
        }
        return when {
            errorToken && digits == ERROR_TOKEN.length -> ERROR
            validHex && digits == 2 -> value
            else -> INVALID
        }
    }

    private fun skipSeparators() {
        while (index < bytes.size && isSeparator(bytes[index])) index++
    }

    private fun isSeparator(value: Byte): Boolean = (value.toInt() and 0xff) <= 0x20

    private fun hexNibble(value: Byte): Int = when (val unsigned = value.toInt() and 0xff) {
        in '0'.code..'9'.code -> unsigned - '0'.code
        in 'a'.code..'f'.code -> unsigned - 'a'.code + 10
        in 'A'.code..'F'.code -> unsigned - 'A'.code + 10
        else -> -1
    }

    private fun Byte.equalsAsciiIgnoreCase(expected: Char): Boolean {
        val unsigned = toInt() and 0xff
        return unsigned == expected.code || unsigned == expected.lowercaseChar().code
    }

    companion object {
        const val MISSING = -1
        const val INVALID = -2
        const val ERROR = -3
        private const val COMMAND = "READ_CORE_MEMORY"
        private const val ERROR_TOKEN = "ERROR"
    }
}
