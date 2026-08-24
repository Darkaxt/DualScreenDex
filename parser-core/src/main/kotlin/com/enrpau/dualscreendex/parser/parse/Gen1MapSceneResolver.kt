package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapScene
import com.enrpau.dualscreendex.parser.io.RomImage

internal object Gen1MapSceneResolver {
    data class Source(
        val baseAreaId: Int,
        val headerBank: Int,
        val header: Int,
        val blockBank: Int,
        val blocks: Int,
    )

    data class Resolution(
        val scenes: List<LocalMapScene>,
        val skippedReasons: List<String>,
    )

    fun resolve(
        rom: RomImage,
        sources: List<Source>,
        maps: List<LocalMap>,
    ): Resolution {
        val mapsById = maps.associateBy(LocalMap::baseAreaId)
        val sourcesById = sources.associateBy(Source::baseAreaId)
        val constraints = mutableListOf<LocalMapSceneConstraint>()
        val skippedReasons = mutableListOf<String>()
        for (source in sources.sortedBy(Source::baseAreaId)) {
            val sourceMap = mapsById[source.baseAreaId] ?: continue
            val flagsResult = runCatching {
                require(source.header + FIXED_HEADER_BYTES <= bankEnd(rom, source.headerBank))
                rom.u8(source.header + CONNECTION_FLAGS_OFFSET).also { require(it and CONNECTION_MASK.inv() == 0) }
            }
            val flagsFailure = flagsResult.exceptionOrNull()
            if (flagsFailure != null) {
                skippedReasons += reason(source.baseAreaId, null, flagsFailure)
                continue
            }
            val flags = flagsResult.getOrThrow()
            var record = source.header + FIXED_HEADER_BYTES
            for (direction in DIRECTIONS) {
                if (flags and direction.mask == 0) continue
                val current = record
                record += CONNECTION_BYTES
                runCatching {
                    readConstraint(
                        rom = rom,
                        record = current,
                        direction = direction.kind,
                        source = source,
                        sourceMap = sourceMap,
                        sourcesById = sourcesById,
                        mapsById = mapsById,
                    )
                }.onSuccess { constraint ->
                    constraints += constraint
                }.onFailure { failure ->
                        skippedReasons += reason(source.baseAreaId, direction.kind, failure)
                    }
            }
        }
        return Resolution(
            scenes = LocalMapSceneBuilder.build(maps, constraints),
            skippedReasons = boundedReasons(skippedReasons),
        )
    }

    private fun readConstraint(
        rom: RomImage,
        record: Int,
        direction: Kind,
        source: Source,
        sourceMap: LocalMap,
        sourcesById: Map<Int, Source>,
        mapsById: Map<Int, LocalMap>,
    ): LocalMapSceneConstraint {
        require(record + CONNECTION_BYTES <= bankEnd(rom, source.headerBank)) { "connection record is truncated" }
        val targetId = rom.u8(record)
        val target = requireNotNull(sourcesById[targetId]) { "connected map descriptor is unavailable" }
        val targetMap = requireNotNull(mapsById[targetId]) { "connected map raster is unavailable" }
        val strip = rom.gbBankAddress(target.blockBank, rom.u16le(record + STRIP_POINTER_OFFSET))
            ?: error("connection strip pointer is invalid")
        val targetBlockCount = targetMap.blockWidth * targetMap.blockHeight
        require(strip in target.blocks until target.blocks + targetBlockCount) {
            "connection strip pointer is outside target blocks"
        }
        require(rom.u16le(record + DESTINATION_POINTER_OFFSET) in WRAM_RANGE) {
            "connection destination is outside WRAM"
        }
        require(rom.u8(record + STRIP_LENGTH_OFFSET) in 1..MAX_STRIP_LENGTH) {
            "connection strip length is invalid"
        }
        require(rom.u8(record + CONNECTED_WIDTH_OFFSET) == targetMap.blockWidth) {
            "connected map width does not match target"
        }
        require(rom.u16le(record + VIEW_POINTER_OFFSET) in WRAM_RANGE) {
            "connection view pointer is outside WRAM"
        }
        val rawY = rom.u8(record + Y_ALIGNMENT_OFFSET)
        val rawX = rom.u8(record + X_ALIGNMENT_OFFSET)
        val alongEdge = when (direction) {
            Kind.NORTH, Kind.SOUTH -> {
                require(signed(rawX) % 2 == 0) { "horizontal alignment is not on the metatile grid" }
                -signed(rawX)
            }
            Kind.WEST, Kind.EAST -> {
                require(signed(rawY) % 2 == 0) { "vertical alignment is not on the metatile grid" }
                -signed(rawY)
            }
        }
        when (direction) {
            Kind.NORTH -> require(rawY == (targetMap.gridHeight - 1) and 0xff) {
                "north alignment does not match target height"
            }
            Kind.SOUTH -> require(rawY == 0) { "south alignment is not zero" }
            Kind.WEST -> require(rawX == (targetMap.gridWidth - 1) and 0xff) {
                "west alignment does not match target width"
            }
            Kind.EAST -> require(rawX == 0) { "east alignment is not zero" }
        }
        return when (direction) {
            Kind.NORTH -> LocalMapSceneConstraint(source.baseAreaId, targetId, alongEdge, -targetMap.gridHeight)
            Kind.SOUTH -> LocalMapSceneConstraint(source.baseAreaId, targetId, alongEdge, sourceMap.gridHeight)
            Kind.WEST -> LocalMapSceneConstraint(source.baseAreaId, targetId, -targetMap.gridWidth, alongEdge)
            Kind.EAST -> LocalMapSceneConstraint(source.baseAreaId, targetId, sourceMap.gridWidth, alongEdge)
        }
    }

    private fun boundedReasons(reasons: List<String>): List<String> = when {
        reasons.size <= MAX_DIAGNOSTICS -> reasons
        else -> reasons.take(MAX_DIAGNOSTICS) + "${reasons.size - MAX_DIAGNOSTICS} additional Gen I connection diagnostics omitted"
    }

    private fun reason(baseAreaId: Int, direction: Kind?, failure: Throwable): String =
        "map 0x${baseAreaId.toString(16).padStart(4, '0')}" +
            (direction?.let { " ${it.name.lowercase()} connection" } ?: " connections") +
            ": ${failure.message}"

    private fun signed(value: Int): Int = value.toByte().toInt()
    private val LocalMap.blockWidth: Int get() = gridWidth / BLOCK_METATILE_EDGE
    private val LocalMap.blockHeight: Int get() = gridHeight / BLOCK_METATILE_EDGE
    private fun bankEnd(rom: RomImage, bank: Int): Int =
        minOf(rom.size.toLong(), (bank.toLong() + 1L) * BANK_BYTES).toInt()

    private data class Direction(val mask: Int, val kind: Kind)
    private enum class Kind { NORTH, SOUTH, WEST, EAST }

    private val DIRECTIONS = listOf(
        Direction(0x08, Kind.NORTH),
        Direction(0x04, Kind.SOUTH),
        Direction(0x02, Kind.WEST),
        Direction(0x01, Kind.EAST),
    )
    private val WRAM_RANGE = 0xc000..0xdfff

    private const val BANK_BYTES = 0x4000
    private const val BLOCK_METATILE_EDGE = 2
    private const val FIXED_HEADER_BYTES = 10
    private const val CONNECTION_FLAGS_OFFSET = 9
    private const val CONNECTION_MASK = 0x0f
    private const val CONNECTION_BYTES = 11
    private const val STRIP_POINTER_OFFSET = 1
    private const val DESTINATION_POINTER_OFFSET = 3
    private const val STRIP_LENGTH_OFFSET = 5
    private const val CONNECTED_WIDTH_OFFSET = 6
    private const val Y_ALIGNMENT_OFFSET = 7
    private const val X_ALIGNMENT_OFFSET = 8
    private const val VIEW_POINTER_OFFSET = 9
    private const val MAX_STRIP_LENGTH = 255
    private const val MAX_DIAGNOSTICS = 64
}
