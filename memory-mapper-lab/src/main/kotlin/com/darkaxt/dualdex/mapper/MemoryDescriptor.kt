package com.darkaxt.dualdex.mapper

data class MemoryDescriptor(
    val id: String,
    val label: String,
    val baseAddress: Long,
    val size: Int,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) { "memory descriptor id is invalid" }
        require(label.isNotBlank()) { "memory descriptor label is required" }
        require(baseAddress in 0..0xFFFF_FFFFL) { "memory descriptor address is outside the 32-bit core bus" }
        require(size in 1..MAX_REGION_BYTES) { "memory descriptor size is outside the bounded mapper limit" }
        require(baseAddress + size - 1 <= 0xFFFF_FFFFL) { "memory descriptor exceeds the 32-bit core bus" }
    }

    companion object {
        const val MAX_REGION_BYTES = 1024 * 1024
    }
}

data class MemoryRead(
    val address: Long,
    val length: Int,
) {
    init {
        require(address in 0..0xFFFF_FFFFL) { "memory read address is outside the 32-bit core bus" }
        require(length in 1..MemoryDescriptor.MAX_REGION_BYTES) { "memory read length is outside the mapper limit" }
        require(address + length - 1 <= 0xFFFF_FFFFL) { "memory read exceeds the 32-bit core bus" }
    }
}

/** Deliberately read-only: there is no write request or write method in this module's transport contract. */
fun interface ReadOnlyMemoryTransport {
    fun read(request: MemoryRead): ByteArray
}
