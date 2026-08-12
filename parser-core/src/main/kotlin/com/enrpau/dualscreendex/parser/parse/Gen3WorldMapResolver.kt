package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.Gen3MapLocationResolver
import com.enrpau.dualscreendex.parser.catalog.Gen3RegionMapEntry
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.sprite.TileRenderer

sealed interface Gen3WorldMapResolution {
    data class Resolved(
        val catalog: WorldMapCatalog,
        val reasons: List<String>,
    ) : Gen3WorldMapResolution

    data class Unavailable(val reason: String) : Gen3WorldMapResolution
    data class Ambiguous(val reason: String) : Gen3WorldMapResolution
    data class BudgetExceeded(val reason: String) : Gen3WorldMapResolution
}

/** Resolves Gen III overview art only from a section-table join and compiled asset references. */
object Gen3WorldMapResolver {
    fun resolve(
        session: RomAnalysisSession,
        encounterBaseIds: Set<Int>,
    ): Gen3WorldMapResolution {
        val locations = Gen3MapLocationResolver.resolveDetailed(session.rom, encounterBaseIds)
            ?: return Gen3WorldMapResolution.Unavailable(
                "encounter map headers and region-map entries did not resolve uniquely",
            )
        val references = session.gbaReferenceIndex
            ?: return Gen3WorldMapResolution.Unavailable("compiled GBA references are unavailable")
        if (references.overflowed) {
            return Gen3WorldMapResolution.BudgetExceeded(
                requireNotNull(references.overflowReason),
            )
        }

        val geometryWidth = locations.entriesBySection.values.maxOfOrNull { it.x + it.width } ?: 0
        val geometryHeight = locations.entriesBySection.values.maxOfOrNull { it.y + it.height } ?: 0
        if (geometryWidth <= 0 || geometryHeight <= 0) {
            return Gen3WorldMapResolution.Unavailable("region-map geometry is empty")
        }

        val compressed = decodeReferencedStreams(session.rom, references)
        val candidates = buildCandidates(
            rom = session.rom,
            references = references,
            streams = compressed,
            geometryWidth = geometryWidth,
            geometryHeight = geometryHeight,
        )
        if (candidates.isEmpty()) {
            return Gen3WorldMapResolution.Unavailable(
                "no co-referenced compressed tile, tilemap, and BGR555 palette cluster validated",
            )
        }
        val bestScore = candidates.maxOf(AssetCandidate::score)
        val structurallyEquivalent = candidates.filter { it.score == bestScore }.distinctBy(AssetCandidate::identity)
        val tightestAssetSpan = structurallyEquivalent.minOf(AssetCandidate::targetSpan)
        val winners = structurallyEquivalent.filter { it.targetSpan == tightestAssetSpan }
        if (winners.size != 1) {
            return Gen3WorldMapResolution.Ambiguous(
                "${winners.size} equally authoritative Gen III world-map asset clusters remained: " +
                    winners.joinToString { winner ->
                        "gfx=0x${winner.gfxOffset.toString(16)}/" +
                            "map=0x${winner.tilemapOffset.toString(16)}/" +
                            "pal=0x${winner.paletteOffset.toString(16)}"
                    },
            )
        }
        val winner = winners.single()
        val locationsBySection = locations.sectionByBaseArea.entries.groupBy({ it.value }, { it.key })
        val normalizedLocations = locations.entriesBySection.values
            .sortedBy(Gen3RegionMapEntry::sectionId)
            .mapNotNull { entry ->
                val baseAreaIds = locationsBySection[entry.sectionId].orEmpty().toSet()
                if (baseAreaIds.isEmpty()) return@mapNotNull null
                WorldMapLocation(
                    key = "section-${entry.sectionId}",
                    displayName = entry.displayName,
                    baseAreaIds = baseAreaIds,
                    geometry = listOf(WorldMapCell(entry.x, entry.y, entry.width, entry.height)),
                )
            }
        if (normalizedLocations.isEmpty()) {
            return Gen3WorldMapResolution.Unavailable(
                "region-map entries did not retain an encounter base-area binding",
            )
        }

        val assetKey = "world/gen3-region-0"
        val region = WorldMapRegion(
            key = "gen3-region-0",
            displayName = null,
            pixelWidth = winner.raster.width,
            pixelHeight = winner.raster.height,
            gridWidth = geometryWidth,
            gridHeight = geometryHeight,
            imageAssetKey = assetKey,
            locations = normalizedLocations,
        )
        return Gen3WorldMapResolution.Resolved(
            catalog = WorldMapCatalog(
                regions = listOf(region),
                assets = mapOf(assetKey to winner.raster),
            ),
            reasons = listOf(
                "resolved region entries for ${normalizedLocations.size} encounter-bound locations",
                "validated a compiled-reference asset cluster at gfx=0x${winner.gfxOffset.toString(16)} " +
                    "tilemap=0x${winner.tilemapOffset.toString(16)} palette=0x${winner.paletteOffset.toString(16)}",
            ),
        )
    }

    private fun decodeReferencedStreams(
        rom: RomImage,
        references: GbaReferenceIndex,
    ): List<CompressedStream> = references.targets.keys.mapNotNull { offset ->
        if (offset.toLong() + 4 > rom.size.toLong() || rom.u8(offset) != GBA_LZ_HEADER) return@mapNotNull null
        val declared = rom.u24le(offset + 1)
        if (declared !in 1..MAX_DECOMPRESSED_ASSET_BYTES) return@mapNotNull null
        runCatching { CompressedStream(offset, GbaRomCompression.decodeAt(rom, offset)) }.getOrNull()
    }

    private fun buildCandidates(
        rom: RomImage,
        references: GbaReferenceIndex,
        streams: List<CompressedStream>,
        geometryWidth: Int,
        geometryHeight: Int,
    ): List<AssetCandidate> {
        val graphics = streams.filter { it.decoded.size >= GBA_8BPP_TILE_BYTES && it.decoded.size % GBA_8BPP_TILE_BYTES == 0 }
        val tilemaps = streams.filter { it.decoded.size >= 2 && it.decoded.size % 2 == 0 }
        val compressedOffsets = streams.mapTo(hashSetOf(), CompressedStream::offset)
        return buildList {
            graphics.forEach gfxLoop@{ gfx ->
                tilemaps.forEach mapLoop@{ map ->
                    if (gfx.offset == map.offset || !coReferenced(references, gfx.offset, map.offset)) return@mapLoop
                    val layout = inferTilemapLayout(map.decoded, geometryWidth, geometryHeight) ?: return@mapLoop
                    val croppedMap = cropTilemap(map.decoded, layout) ?: return@mapLoop
                    val indexed = runCatching {
                        TileRenderer.gba8BppTilemap(gfx.decoded, croppedMap, geometryWidth, geometryHeight)
                    }.getOrNull() ?: return@mapLoop
                    val usedIndices = indexed.indices.map { it.toInt() and 0xFF }.filter { it != 0 }
                    if (usedIndices.isEmpty()) return@mapLoop
                    val paletteBase = (usedIndices.min() / GBA_PALETTE_BANK_COLORS) * GBA_PALETTE_BANK_COLORS
                    val paletteColors = usedIndices.max() - paletteBase + 1
                    references.targets.keys.forEach palette@{ paletteOffset ->
                        if (paletteOffset == gfx.offset || paletteOffset == map.offset) return@palette
                        if (paletteOffset in compressedOffsets) return@palette
                        if (!coReferenced(references, gfx.offset, map.offset, paletteOffset)) return@palette
                        val palette = readPalette(rom, paletteOffset, paletteBase, paletteColors) ?: return@palette
                        val raster = runCatching { TileRenderer.applyBgr555Palette(indexed, palette) }.getOrNull()
                            ?: return@palette
                        add(
                            AssetCandidate(
                                gfxOffset = gfx.offset,
                                tilemapOffset = map.offset,
                                paletteOffset = paletteOffset,
                                raster = raster,
                                score = layout.score,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun inferTilemapLayout(
        tilemap: ByteArray,
        geometryWidth: Int,
        geometryHeight: Int,
    ): TilemapLayout? {
        val entries = tilemap.size / 2
        return when {
            entries == 2_048 && geometryWidth <= 28 && geometryHeight <= 15 ->
                TilemapLayout(64, 32, geometryWidth, geometryHeight, 1, 2, screenBlocked = true, score = 4)
            entries == 1_024 && geometryWidth <= 28 && geometryHeight <= 15 ->
                TilemapLayout(32, 32, geometryWidth, geometryHeight, 1, 3, screenBlocked = false, score = 3)
            entries == geometryWidth * geometryHeight ->
                TilemapLayout(
                    geometryWidth,
                    geometryHeight,
                    geometryWidth,
                    geometryHeight,
                    0,
                    0,
                    screenBlocked = false,
                    score = 2,
                )
            else -> null
        }
    }

    private fun cropTilemap(tilemap: ByteArray, layout: TilemapLayout): ByteArray? {
        val outputWidth = layout.outputWidth
        val outputHeight = layout.outputHeight
        if (layout.offsetX + outputWidth > layout.width || layout.offsetY + outputHeight > layout.height) return null
        val output = ByteArray(outputWidth * outputHeight * 2)
        repeat(outputHeight) { y ->
            repeat(outputWidth) { x ->
                val sourceX = layout.offsetX + x
                val sourceY = layout.offsetY + y
                val sourceEntry = if (layout.screenBlocked) {
                    val block = (sourceY / 32) * (layout.width / 32) + sourceX / 32
                    block * 1_024 + (sourceY % 32) * 32 + sourceX % 32
                } else {
                    sourceY * layout.width + sourceX
                }
                val source = sourceEntry * 2
                val destination = (y * outputWidth + x) * 2
                output[destination] = tilemap[source]
                output[destination + 1] = tilemap[source + 1]
            }
        }
        return output
    }

    private fun readPalette(
        rom: RomImage,
        offset: Int,
        base: Int,
        requiredColors: Int,
    ): ShortArray? {
        if (requiredColors <= 1 || requiredColors > MAX_PALETTE_COLORS) return null
        val byteCount = requiredColors.toLong() * 2L
        if (offset < 0 || offset.toLong() + byteCount > rom.size.toLong()) return null
        val colors = ShortArray(base + requiredColors)
        val distinct = linkedSetOf<Int>()
        repeat(requiredColors) { index ->
            val value = rom.u16le(offset + index * 2)
            if (value and 0x8000 != 0) return null
            colors[base + index] = value.toShort()
            distinct += value
        }
        if (distinct.size < 2) return null
        return colors
    }

    private fun coReferenced(references: GbaReferenceIndex, vararg offsets: Int): Boolean {
        val sites = offsets.map { offset ->
            references.target(offset)?.instructionSites.orEmpty().takeIf { it.isNotEmpty() } ?: return false
        }
        return cartesianSiteSpan(sites, depth = 0, minimum = Int.MAX_VALUE, maximum = Int.MIN_VALUE)
    }

    private fun cartesianSiteSpan(
        sites: List<List<Int>>,
        depth: Int,
        minimum: Int,
        maximum: Int,
    ): Boolean {
        if (depth == sites.size) return maximum.toLong() - minimum.toLong() <= MAX_COMPILED_SITE_SPAN
        return sites[depth].any { site ->
            val nextMinimum = minOf(minimum, site)
            val nextMaximum = maxOf(maximum, site)
            nextMaximum.toLong() - nextMinimum.toLong() <= MAX_COMPILED_SITE_SPAN &&
                cartesianSiteSpan(sites, depth + 1, nextMinimum, nextMaximum)
        }
    }

    private data class CompressedStream(val offset: Int, val decoded: ByteArray)

    private data class TilemapLayout(
        val width: Int,
        val height: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val offsetX: Int,
        val offsetY: Int,
        val screenBlocked: Boolean,
        val score: Int,
    )

    private data class AssetCandidate(
        val gfxOffset: Int,
        val tilemapOffset: Int,
        val paletteOffset: Int,
        val raster: RgbaSprite,
        val score: Int,
    ) {
        val identity: Triple<Int, Int, Int> get() = Triple(gfxOffset, tilemapOffset, paletteOffset)
        val targetSpan: Long get() {
            val offsets = longArrayOf(gfxOffset.toLong(), tilemapOffset.toLong(), paletteOffset.toLong())
            return offsets.max() - offsets.min()
        }
    }

    private const val GBA_LZ_HEADER = 0x10
    private const val GBA_8BPP_TILE_BYTES = 64
    private const val GBA_PALETTE_BANK_COLORS = 16
    private const val MAX_PALETTE_COLORS = 256
    private const val MAX_DECOMPRESSED_ASSET_BYTES = 256 * 1_024
    private const val MAX_COMPILED_SITE_SPAN = 160L
}
