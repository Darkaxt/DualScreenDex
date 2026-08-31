package com.darkaxt.dualdex

import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.google.gson.Gson
import java.security.MessageDigest
import java.util.Base64

internal class RawLiveMemoryScenarioCatalog(
    scenarios: List<RawLiveMemoryScenario>,
) {
    val scenarios = scenarios.toList()
    val initialScenario: RawLiveMemoryScenario
        get() = scenarios.first()

    init {
        require(this.scenarios.isNotEmpty()) { "raw memory catalog must contain at least one scenario" }
        require(this.scenarios.size <= MAX_SCENARIOS) { "raw memory catalog exceeds the scenario limit" }
        require(this.scenarios.map(RawLiveMemoryScenario::id).distinct().size == this.scenarios.size) {
            "raw memory scenario ids must be unique"
        }
    }

    fun requireScenario(id: String): RawLiveMemoryScenario =
        scenarios.firstOrNull { it.id == id } ?: throw IllegalArgumentException("unknown raw memory scenario")

    private companion object {
        const val MAX_SCENARIOS = 32
    }
}

internal class RawLiveMemoryQaController(
    private val catalog: RawLiveMemoryScenarioCatalog,
    frameDurationNanos: Long = 1_000_000_000L,
    monotonicNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val simulator = RawLiveMemorySimulator(
        initialScenario = catalog.initialScenario,
        frameDurationNanos = frameDurationNanos,
        monotonicNanos = monotonicNanos,
    )

    fun transportFactory(): () -> NetworkCommandTransport = simulator.transportFactory()

    fun scenarioIds(): List<String> = catalog.scenarios.map(RawLiveMemoryScenario::id)

    fun snapshot(): RawLiveMemorySimulatorSnapshot = simulator.snapshot()

    fun pause(): RawLiveMemorySimulatorSnapshot = simulator.pause()

    fun play(): RawLiveMemorySimulatorSnapshot = simulator.play()

    fun step(): RawLiveMemorySimulatorSnapshot = simulator.step()

    fun selectScenario(id: String): RawLiveMemorySimulatorSnapshot =
        simulator.selectScenario(catalog.requireScenario(id))

    override fun close() = simulator.close()
}

internal object RawLiveMemoryScenarioLoader {
    fun decode(bytes: ByteArray): RawLiveMemoryScenarioCatalog {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ASSET_BYTES) { "raw memory scenario asset size is invalid" }
        val root = requireNotNull(Gson().fromJson(bytes.toString(Charsets.UTF_8), RootDto::class.java)) {
            "raw memory scenario asset is empty"
        }
        require(root.schema == SCHEMA) { "raw memory scenario schema is unsupported" }
        val regionFrames = requireNotNull(root.regionFrames) { "raw memory region frames are missing" }
        require(regionFrames.isNotEmpty() && regionFrames.size <= MAX_REGION_FRAMES) {
            "raw memory region frame count is invalid"
        }
        val sharedFrames = regionFrames.associate { frame ->
            val id = requireNotNull(frame.id)
            require(id.matches(ID_PATTERN)) { "raw memory source frame id is invalid" }
            val regions = requireNotNull(frame.regions)
            require(regions.size <= MAX_REGIONS_PER_FRAME) { "raw memory source frame exceeds the region limit" }
            id to regions.map(::decodeRegion)
        }
        require(sharedFrames.size == regionFrames.size) { "raw memory source frame ids must be unique" }

        val scenarios = requireNotNull(root.scenarios) { "raw memory scenarios are missing" }.map { scenario ->
            val frames = requireNotNull(scenario.frames).map { frame ->
                val sourceFrameId = requireNotNull(frame.sourceFrameId)
                val regions = requireNotNull(sharedFrames[sourceFrameId]) { "raw memory source frame is missing" }
                RawLiveMemoryFrame(
                    id = requireNotNull(frame.id),
                    regions = regions,
                    readFaults = frame.readFaults.orEmpty().map(::decodeFault),
                )
            }
            RawLiveMemoryScenario(
                id = requireNotNull(scenario.id),
                systemId = requireNotNull(scenario.systemId),
                gameBasename = requireNotNull(scenario.gameBasename),
                crc32 = scenario.crc32,
                frames = frames,
                savefileDirectory = scenario.savefileDirectory ?: "/qa/saves",
                savestateDirectory = scenario.savestateDirectory ?: "/qa/states",
                systemDirectory = scenario.systemDirectory ?: "/qa/system",
            )
        }
        return RawLiveMemoryScenarioCatalog(scenarios)
    }

    private fun decodeRegion(region: RegionDto): RawLiveMemoryRegion {
        val encoded = requireNotNull(region.base64Bytes)
        require(encoded.length <= MAX_ENCODED_REGION_CHARS) { "raw memory region encoding is too large" }
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("raw memory region encoding is invalid", it) }
        require(decoded.size == region.size) { "raw memory region size does not match" }
        require(decoded.sha256().equals(region.sha256, ignoreCase = true)) { "raw memory region hash does not match" }
        return RawLiveMemoryRegion(region.baseAddress, decoded)
    }

    private fun decodeFault(fault: FaultDto): RawLiveMemoryReadFault = RawLiveMemoryReadFault(
        baseAddress = fault.baseAddress,
        size = fault.size,
        kind = runCatching { RawLiveMemoryReadFaultKind.valueOf(requireNotNull(fault.kind)) }
            .getOrElse { throw IllegalArgumentException("raw memory fault kind is invalid", it) },
    )

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private data class RootDto(
        val schema: Int = 0,
        val regionFrames: List<RegionFrameDto>? = null,
        val scenarios: List<ScenarioDto>? = null,
    )

    private data class RegionFrameDto(
        val id: String? = null,
        val regions: List<RegionDto>? = null,
    )

    private data class RegionDto(
        val baseAddress: Long = -1,
        val size: Int = -1,
        val sha256: String? = null,
        val base64Bytes: String? = null,
    )

    private data class ScenarioDto(
        val id: String? = null,
        val systemId: String? = null,
        val gameBasename: String? = null,
        val crc32: String? = null,
        val savefileDirectory: String? = null,
        val savestateDirectory: String? = null,
        val systemDirectory: String? = null,
        val frames: List<FrameDto>? = null,
    )

    private data class FrameDto(
        val id: String? = null,
        val sourceFrameId: String? = null,
        val readFaults: List<FaultDto>? = null,
    )

    private data class FaultDto(
        val baseAddress: Long = -1,
        val size: Int = -1,
        val kind: String? = null,
    )

    private const val SCHEMA = 1
    private const val MAX_ASSET_BYTES = 16 * 1024 * 1024
    private const val MAX_REGION_FRAMES = 64
    private const val MAX_REGIONS_PER_FRAME = 16
    private const val MAX_ENCODED_REGION_CHARS = 6 * 1024 * 1024
    private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
}
