package com.darkaxt.dualdex.mapper

object RetroArchReadProtocol {
    const val MAX_CHUNK_BYTES = 512

    fun command(request: MemoryRead): String =
        "READ_CORE_MEMORY ${request.address.toString(16)} ${request.length}"

    fun parse(request: MemoryRead, response: String): ByteArray {
        val parts = response.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        require(parts.size >= 3 && parts[0] == "READ_CORE_MEMORY") { "invalid READ_CORE_MEMORY reply" }
        val address = parts[1].toLongOrNull(16)
        require(address == request.address) { "READ_CORE_MEMORY reply address did not match the request" }
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

    fun chunks(descriptor: MemoryDescriptor): List<MemoryRead> = buildList {
        var offset = 0
        while (offset < descriptor.size) {
            val length = minOf(MAX_CHUNK_BYTES, descriptor.size - offset)
            add(MemoryRead(descriptor.baseAddress + offset, length))
            offset += length
        }
    }
}
