package com.darkaxt.dualdex

import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import java.util.ArrayDeque

private const val MAX_FRAME_BYTES = 4 * 1024 * 1024
private const val MAX_FRAME_REGIONS = 16
private const val MAX_FRAME_FAULTS = 64
private const val MAX_SCENARIO_BYTES = 64 * 1024 * 1024
private const val MAX_SCENARIO_FRAMES = 256
private const val MAX_SYSTEM_ID_CHARS = 128
private const val MAX_GAME_BASENAME_CHARS = 1_024
private const val MAX_DIRECTORY_CHARS = 2_048

private fun String.isWireSafe(maximumChars: Int, allowEmpty: Boolean = false): Boolean =
    (allowEmpty || isNotEmpty()) && length <= maximumChars && all { it.code in 0x20..0x7E }

internal class RawLiveMemoryRegion(
    val baseAddress: Long,
    bytes: ByteArray,
) {
    private val contents = bytes.copyOf()
    val size: Int = contents.size
    val endAddressExclusive: Long = baseAddress + size

    init {
        require(baseAddress in 0..0xFFFF_FFFFL) { "raw memory region address is outside the 32-bit core bus" }
        require(contents.isNotEmpty()) { "raw memory region must not be empty" }
        require(contents.size <= MAX_FRAME_BYTES) { "raw memory region exceeds the frame byte limit" }
        require(endAddressExclusive <= 0x1_0000_0000L) { "raw memory region exceeds the 32-bit core bus" }
    }

    fun copyTo(address: Long, destination: ByteArray, destinationOffset: Int, length: Int) {
        contents.copyInto(
            destination = destination,
            destinationOffset = destinationOffset,
            startIndex = (address - baseAddress).toInt(),
            endIndex = (address - baseAddress).toInt() + length,
        )
    }
}

internal enum class RawLiveMemoryReadFaultKind {
    UNREADABLE,
    PARTIAL,
    MALFORMED,
}

internal sealed interface RawLiveMemoryReadResult {
    data object Unavailable : RawLiveMemoryReadResult
    data object Malformed : RawLiveMemoryReadResult
    data class Data(val bytes: ByteArray) : RawLiveMemoryReadResult
}

internal data class RawLiveMemoryReadFault(
    val baseAddress: Long,
    val size: Int,
    val kind: RawLiveMemoryReadFaultKind,
) {
    private val endAddressExclusive = baseAddress + size

    init {
        require(baseAddress in 0..0xFFFF_FFFFL) { "raw memory fault address is outside the 32-bit core bus" }
        require(size > 0) { "raw memory fault must not be empty" }
        require(endAddressExclusive <= 0x1_0000_0000L) { "raw memory fault exceeds the 32-bit core bus" }
    }

    fun intersects(address: Long, length: Int): Boolean =
        address < endAddressExclusive && baseAddress < address + length
}

internal class RawLiveMemoryFrame(
    val id: String,
    regions: List<RawLiveMemoryRegion>,
    readFaults: List<RawLiveMemoryReadFault> = emptyList(),
) {
    private val regions = regions.also {
        require(it.size <= MAX_FRAME_REGIONS) { "raw memory frame exceeds the region limit" }
    }.sortedBy(RawLiveMemoryRegion::baseAddress)
    private val readFaults = readFaults.also {
        require(it.size <= MAX_FRAME_FAULTS) { "raw memory frame exceeds the fault limit" }
    }.toList()
    val retainedBytes: Long = this.regions.sumOf { it.size.toLong() }

    init {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) { "raw memory frame id is invalid" }
        require(retainedBytes <= MAX_FRAME_BYTES) { "raw memory frame exceeds the byte limit" }
        this.regions.zipWithNext().forEach { (first, second) ->
            require(first.endAddressExclusive <= second.baseAddress) { "raw memory regions overlap" }
        }
    }

    fun read(address: Long, length: Int): RawLiveMemoryReadResult {
        val fault = readFaults.firstOrNull { it.intersects(address, length) }
        if (fault?.kind == RawLiveMemoryReadFaultKind.UNREADABLE) return RawLiveMemoryReadResult.Unavailable
        if (fault?.kind == RawLiveMemoryReadFaultKind.MALFORMED) return RawLiveMemoryReadResult.Malformed

        val data = readAvailable(address, length) ?: return RawLiveMemoryReadResult.Unavailable
        return if (fault?.kind == RawLiveMemoryReadFaultKind.PARTIAL) {
            RawLiveMemoryReadResult.Data(data.copyOf(maxOf(0, data.size - 1)))
        } else {
            RawLiveMemoryReadResult.Data(data)
        }
    }

    private fun readAvailable(address: Long, length: Int): ByteArray? {
        val result = ByteArray(length)
        var cursor = address
        var destinationOffset = 0
        val endAddressExclusive = address + length
        while (cursor < endAddressExclusive) {
            val region = regions.firstOrNull { cursor >= it.baseAddress && cursor < it.endAddressExclusive }
                ?: return null
            val copyLength = minOf(region.endAddressExclusive, endAddressExclusive).minus(cursor).toInt()
            region.copyTo(cursor, result, destinationOffset, copyLength)
            cursor += copyLength
            destinationOffset += copyLength
        }
        return result
    }
}

internal class RawLiveMemoryScenario(
    val id: String,
    val systemId: String,
    val gameBasename: String,
    crc32: String?,
    frames: List<RawLiveMemoryFrame>,
    val savefileDirectory: String = "/qa/saves",
    val savestateDirectory: String = "/qa/states",
    val systemDirectory: String = "/qa/system",
) {
    val crc32: String? = crc32?.uppercase()
    val frames = frames.toList()

    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) { "raw memory scenario id is invalid" }
        require(systemId.isNotBlank() && systemId.isWireSafe(MAX_SYSTEM_ID_CHARS) && ',' !in systemId) {
            "raw memory system id is invalid"
        }
        require(gameBasename.isNotBlank() && gameBasename.isWireSafe(MAX_GAME_BASENAME_CHARS)) {
            "raw memory game basename is invalid"
        }
        require(savefileDirectory.isWireSafe(MAX_DIRECTORY_CHARS, allowEmpty = true)) {
            "raw memory savefile directory is invalid"
        }
        require(savestateDirectory.isWireSafe(MAX_DIRECTORY_CHARS, allowEmpty = true)) {
            "raw memory savestate directory is invalid"
        }
        require(systemDirectory.isWireSafe(MAX_DIRECTORY_CHARS, allowEmpty = true)) {
            "raw memory system directory is invalid"
        }
        require(this.crc32 == null || this.crc32.matches(Regex("[0-9A-F]{8}"))) { "raw memory CRC32 is invalid" }
        require(this.frames.isNotEmpty()) { "raw memory scenario must contain at least one frame" }
        require(this.frames.size <= MAX_SCENARIO_FRAMES) { "raw memory scenario exceeds the frame limit" }
        require(this.frames.sumOf(RawLiveMemoryFrame::retainedBytes) <= MAX_SCENARIO_BYTES) {
            "raw memory scenario exceeds the byte limit"
        }
        require(this.frames.map(RawLiveMemoryFrame::id).distinct().size == this.frames.size) {
            "raw memory frame ids must be unique"
        }
    }
}

internal data class RawLiveMemorySimulatorSnapshot(
    val scenarioId: String,
    val frameId: String,
    val frameIndex: Int,
    val frameCount: Int,
    val paused: Boolean,
)

internal class RawLiveMemorySimulator(
    initialScenario: RawLiveMemoryScenario,
    private val frameDurationNanos: Long = DEFAULT_FRAME_DURATION_NANOS,
    private val monotonicNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val lock = Any()
    private val endpoints = mutableSetOf<Endpoint>()
    private var scenario = initialScenario
    private var frameIndex = 0
    private var paused = true
    private var frameAnchorNanos = monotonicNanos()
    private var generation = 0L
    private var closed = false

    init {
        require(frameDurationNanos > 0) { "raw memory frame duration must be positive" }
    }

    fun transportFactory(): () -> NetworkCommandTransport = { openTransport() }

    fun openTransport(): NetworkCommandTransport = synchronized(lock) {
        check(!closed) { "raw memory simulator is closed" }
        Endpoint().also(endpoints::add)
    }

    fun snapshot(): RawLiveMemorySimulatorSnapshot = synchronized(lock) {
        check(!closed) { "raw memory simulator is closed" }
        refreshPlayback()
        snapshotLocked()
    }

    fun pause(): RawLiveMemorySimulatorSnapshot = synchronized(lock) {
        check(!closed) { "raw memory simulator is closed" }
        refreshPlayback()
        paused = true
        frameAnchorNanos = monotonicNanos()
        snapshotLocked()
    }

    fun play(): RawLiveMemorySimulatorSnapshot = synchronized(lock) {
        check(!closed) { "raw memory simulator is closed" }
        refreshPlayback()
        if (paused) {
            paused = false
            frameAnchorNanos = monotonicNanos()
        }
        snapshotLocked()
    }

    fun step(): RawLiveMemorySimulatorSnapshot = synchronized(lock) {
        check(!closed) { "raw memory simulator is closed" }
        refreshPlayback()
        paused = true
        frameIndex = (frameIndex + 1) % scenario.frames.size
        frameAnchorNanos = monotonicNanos()
        snapshotLocked()
    }

    fun selectScenario(replacement: RawLiveMemoryScenario): RawLiveMemorySimulatorSnapshot = synchronized(lock) {
        check(!closed) { "raw memory simulator is closed" }
        scenario = replacement
        frameIndex = 0
        paused = true
        frameAnchorNanos = monotonicNanos()
        generation++
        endpoints.forEach { endpoint ->
            endpoint.replies.clear()
            endpoint.replies.add(QueuedReply(generation, CONTENTLESS_PACKET.copyOf()))
        }
        snapshotLocked()
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        endpoints.forEach { endpoint ->
            endpoint.closed = true
            endpoint.replies.clear()
        }
        endpoints.clear()
    }

    private fun send(endpoint: Endpoint, payload: ByteArray) = synchronized(lock) {
        requireOpen(endpoint)
        check(endpoint.replies.size < MAX_PENDING_REPLIES) { "network command reply queue is full" }
        val command = payload.toString(Charsets.US_ASCII).trim()
        val response = when {
            command == "GET_STATUS" -> {
                refreshPlayback()
                statusPacket()
            }
            command == "VERSION" -> VERSION_PACKET.copyOf()
            command.startsWith("GET_CONFIG_PARAM ") -> configPacket(command)
            command.startsWith("READ_CORE_MEMORY ") -> {
                refreshPlayback()
                if (endpoint.openedGeneration == generation) {
                    memoryPacket(command)
                } else {
                    memoryErrorPacket(command.substringAfter("READ_CORE_MEMORY ").substringBefore(' '))
                }
            }
            else -> return@synchronized
        }
        check(response.size <= MAX_PACKET_BYTES) { "network command reply exceeds the packet limit" }
        endpoint.replies.add(QueuedReply(generation, response))
    }

    private fun poll(endpoint: Endpoint): ByteArray? = synchronized(lock) {
        requireOpen(endpoint)
        var reply = endpoint.replies.pollFirst()
        while (reply != null && reply.generation != generation) {
            reply = endpoint.replies.pollFirst()
        }
        reply?.payload?.copyOf()
    }

    private fun close(endpoint: Endpoint) = synchronized(lock) {
        if (endpoint.closed) return@synchronized
        endpoint.closed = true
        endpoint.replies.clear()
        endpoints.remove(endpoint)
    }

    private fun requireOpen(endpoint: Endpoint) {
        check(!closed && !endpoint.closed && endpoint in endpoints) { "network command transport is closed" }
    }

    private fun refreshPlayback() {
        if (paused) return
        val now = monotonicNanos()
        val elapsed = now - frameAnchorNanos
        if (elapsed < frameDurationNanos) return
        val elapsedFrames = elapsed / frameDurationNanos
        frameIndex = ((frameIndex.toLong() + elapsedFrames % scenario.frames.size) % scenario.frames.size).toInt()
        frameAnchorNanos += elapsedFrames * frameDurationNanos
    }

    private fun snapshotLocked(): RawLiveMemorySimulatorSnapshot = RawLiveMemorySimulatorSnapshot(
        scenarioId = scenario.id,
        frameId = scenario.frames[frameIndex].id,
        frameIndex = frameIndex,
        frameCount = scenario.frames.size,
        paused = paused,
    )

    private fun statusPacket(): ByteArray = buildString {
        append(if (paused) "GET_STATUS PAUSED " else "GET_STATUS PLAYING ")
        append(scenario.systemId)
        append(',')
        append(scenario.gameBasename)
        scenario.crc32?.let {
            append(",crc32=")
            append(it)
        }
    }.toByteArray(Charsets.US_ASCII)

    private fun configPacket(command: String): ByteArray {
        val parameter = command.substringAfter("GET_CONFIG_PARAM ").substringBefore(' ')
        val value = when (parameter) {
            "savefile_directory" -> scenario.savefileDirectory
            "savestate_directory" -> scenario.savestateDirectory
            "system_directory" -> scenario.systemDirectory
            else -> ""
        }
        return "GET_CONFIG_PARAM $parameter $value".trimEnd().toByteArray(Charsets.US_ASCII)
    }

    private fun memoryPacket(command: String): ByteArray {
        val fields = command.split(Regex("\\s+"))
        val addressToken = fields.getOrNull(1).orEmpty()
        val address = addressToken.toLongOrNull(16)
        val length = fields.getOrNull(2)?.toIntOrNull()
        if (fields.size != 3 || address == null || address !in 0..0xFFFF_FFFFL || length == null || length !in 1..MAX_READ_BYTES) {
            return memoryErrorPacket(addressToken)
        }
        if (address + length > 0x1_0000_0000L) return memoryErrorPacket(addressToken)

        return when (val result = scenario.frames[frameIndex].read(address, length)) {
            RawLiveMemoryReadResult.Unavailable -> memoryErrorPacket(addressToken)
            RawLiveMemoryReadResult.Malformed -> "READ_CORE_MEMORY $addressToken ZZ".toByteArray(Charsets.US_ASCII)
            is RawLiveMemoryReadResult.Data -> buildString {
                append("READ_CORE_MEMORY ")
                append(addressToken)
                result.bytes.forEach { byte ->
                    append(' ')
                    append(HEX[(byte.toInt() ushr 4) and 0x0F])
                    append(HEX[byte.toInt() and 0x0F])
                }
            }.toByteArray(Charsets.US_ASCII)
        }
    }

    private fun memoryErrorPacket(addressToken: String): ByteArray =
        "READ_CORE_MEMORY $addressToken ERROR".toByteArray(Charsets.US_ASCII)

    private inner class Endpoint : NetworkCommandTransport {
        val openedGeneration = generation
        val replies = ArrayDeque<QueuedReply>()
        var closed = false

        override fun send(payload: ByteArray) = this@RawLiveMemorySimulator.send(this, payload)

        override fun poll(): ByteArray? = this@RawLiveMemorySimulator.poll(this)

        override fun close() = this@RawLiveMemorySimulator.close(this)
    }

    private data class QueuedReply(
        val generation: Long,
        val payload: ByteArray,
    )

    private companion object {
        const val DEFAULT_FRAME_DURATION_NANOS = 1_000_000_000L
        const val MAX_READ_BYTES = 1024
        const val MAX_PENDING_REPLIES = 1024
        const val MAX_PACKET_BYTES = 4096
        val CONTENTLESS_PACKET = "GET_STATUS CONTENTLESS".toByteArray(Charsets.US_ASCII)
        val VERSION_PACKET = "1.0.0-dualdex-qa".toByteArray(Charsets.US_ASCII)
        val HEX = "0123456789ABCDEF".toCharArray()
    }
}
