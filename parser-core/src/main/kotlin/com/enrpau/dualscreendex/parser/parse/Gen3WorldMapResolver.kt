package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.Gen3MapLocationResolver
import com.enrpau.dualscreendex.parser.catalog.Gen3RegionMapEntry
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.catalog.WorldMapCell
import com.enrpau.dualscreendex.parser.catalog.WorldMapLocation
import com.enrpau.dualscreendex.parser.catalog.WorldMapRegion
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression

sealed interface Gen3WorldMapResolution {
    data class Resolved(val catalog: WorldMapCatalog, val reasons: List<String>) : Gen3WorldMapResolution
    data class Unavailable(val reason: String) : Gen3WorldMapResolution
    data class Ambiguous(val reason: String) : Gen3WorldMapResolution
    data class BudgetExceeded(val reason: String) : Gen3WorldMapResolution
}

/**
 * Finds source-family ABI assets through compiled loader references, then terminates that ABI at
 * the normalized compositor/catalog boundary. It deliberately knows no ROM identity or placement.
 */
object Gen3WorldMapResolver {
    fun resolve(session: RomAnalysisSession, encounterBaseIds: Set<Int>): Gen3WorldMapResolution {
        val references = session.gbaReferenceIndex
            ?: return Gen3WorldMapResolution.Unavailable("compiled GBA references are unavailable")
        references.overflowReason?.let { return Gen3WorldMapResolution.BudgetExceeded(it) }
        val functionIndex = ThumbFunctionIndex.build(session.rom, references)
        val streams = decodeReferencedStreams(session.rom, references)
        val affine = affineCandidates(session.rom, references, functionIndex, streams)
        val text = textCandidates(session.rom, references, functionIndex, streams)
        if (affine.isNotEmpty() && text.isNotEmpty()) {
            return Gen3WorldMapResolution.Ambiguous("multiple proven world-map loader formats remained eligible")
        }
        val resolution = when {
            affine.isNotEmpty() -> resolveAffine(session.rom, encounterBaseIds, references, affine)
            text.isNotEmpty() -> resolveText(session.rom, encounterBaseIds, references, functionIndex, text)
            else -> Gen3WorldMapResolution.Unavailable(
                "no compiled-reference tile, tilemap, and BGR555 palette cluster passed a proven loader contract",
            )
        }
        return if (resolution is Gen3WorldMapResolution.Resolved) {
            resolution.copy(
                reasons = resolution.reasons +
                    "indexed ${functionIndex.analyzedSiteCount} distinct compiled reference sites once",
            )
        } else {
            resolution
        }
    }

    private fun resolveAffine(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        candidates: List<AssetCandidate>,
    ): Gen3WorldMapResolution {
        val winners = authoritative(candidates)
        if (winners.size != 1) {
            return Gen3WorldMapResolution.Ambiguous("${winners.size} equally authoritative affine asset clusters remained")
        }
        val locations = Gen3MapLocationResolver.resolveDetailed(rom, encounterBaseIds, references)
            ?: return Gen3WorldMapResolution.Unavailable(
                "encounter map headers and affine region entries did not resolve uniquely",
            )
        val winner = winners.single()
        val normalized = emeraldLocations(locations, winner.composition.gridWidth, winner.composition.gridHeight)
        if (normalized.isEmpty()) {
            return Gen3WorldMapResolution.Unavailable("affine region entries retained no encounter binding")
        }
        val regionKey = "gen3-region-0"
        val assetKey = "world/$regionKey"
        val region = WorldMapRegion(
            regionKey,
            null,
            winner.composition.raster.width,
            winner.composition.raster.height,
            winner.composition.gridWidth,
            winner.composition.gridHeight,
            assetKey,
            normalized,
        )
        return Gen3WorldMapResolution.Resolved(
            WorldMapCatalog(listOf(region), mapOf(assetKey to winner.composition.raster)).validate(),
            listOf(
                "validated one affine loader asset cluster",
                "resolved ${normalized.size} encounter-bound semantic locations",
            ),
        )
    }

    private fun resolveText(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        candidates: List<TextAssetCandidate>,
    ): Gen3WorldMapResolution {
        val winnerGroups = authoritativeText(candidates)
        if (winnerGroups.size != 1) {
            return Gen3WorldMapResolution.Ambiguous("${winnerGroups.size} equally authoritative text-map asset clusters remained")
        }
        val winner = winnerGroups.single()
        if (winner.maps.size != TEXT_REGION_COUNT) {
            return Gen3WorldMapResolution.Unavailable(
                "text-map loader cluster exposed ${winner.maps.size} regions instead of $TEXT_REGION_COUNT",
            )
        }
        val layouts = resolveTextSemanticLayouts(rom, references, functionIndex)
            ?: return Gen3WorldMapResolution.Unavailable("four semantic text-map section planes did not resolve uniquely")
        val sectionByBaseArea = Gen3MapLocationResolver.resolveSectionByBaseArea(rom, encounterBaseIds, references)
        if (sectionByBaseArea.isEmpty()) {
            return Gen3WorldMapResolution.Unavailable("encounter map headers did not resolve a semantic section join")
        }
        val baseAreasBySection = sectionByBaseArea.entries.groupBy({ it.value }, { it.key })
        val regions = mutableListOf<WorldMapRegion>()
        val assets = linkedMapOf<String, com.enrpau.dualscreendex.parser.catalog.RgbaSprite>()
        winner.maps.zip(layouts).forEachIndexed { index, (map, layout) ->
            val normalized = textLocations(layout, baseAreasBySection)
            if (normalized.isEmpty()) {
                return Gen3WorldMapResolution.Unavailable("text-map region $index retained no encounter binding")
            }
            val regionKey = "gen3-region-$index"
            val assetKey = "world/$regionKey"
            regions += WorldMapRegion(
                regionKey,
                null,
                map.composition.raster.width,
                map.composition.raster.height,
                map.composition.gridWidth,
                map.composition.gridHeight,
                assetKey,
                normalized,
            )
            assets[assetKey] = map.composition.raster
        }
        return Gen3WorldMapResolution.Resolved(
            WorldMapCatalog(regions, assets).validate(),
            listOf(
                "validated one shared text-map loader cluster with four independent regions",
                "joined each region to its compiled semantic section plane",
            ),
        )
    }

    private fun emeraldLocations(
        locations: com.enrpau.dualscreendex.parser.catalog.Gen3MapLocationResolution,
        gridWidth: Int,
        gridHeight: Int,
    ): List<WorldMapLocation> {
        val baseAreasBySection = locations.sectionByBaseArea.entries.groupBy({ it.value }, { it.key })
        return locations.entriesBySection.values.sortedBy(Gen3RegionMapEntry::sectionId).mapNotNull { entry ->
            val baseAreaIds = baseAreasBySection[entry.sectionId].orEmpty().toSet()
            if (baseAreaIds.isEmpty() || entry.x + entry.width > gridWidth || entry.y + entry.height > gridHeight) {
                return@mapNotNull null
            }
            WorldMapLocation(
                "section-${entry.sectionId}",
                entry.displayName,
                baseAreaIds,
                listOf(WorldMapCell(entry.x, entry.y, entry.width, entry.height)),
            )
        }
    }

    private fun textLocations(
        layout: SemanticLayout,
        baseAreasBySection: Map<Int, List<Int>>,
    ): List<WorldMapLocation> = layout.cellsBySection.entries.sortedBy { it.key }.mapNotNull { (section, cells) ->
        val baseAreaIds = baseAreasBySection[section].orEmpty().toSet()
        if (baseAreaIds.isEmpty()) return@mapNotNull null
        WorldMapLocation(
            key = "section-$section",
            displayName = "Map section $section",
            baseAreaIds = baseAreaIds,
            geometry = cells,
        )
    }

    private fun affineCandidates(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        streams: List<CompressedStream>,
    ): List<AssetCandidate> {
        val maps = streams.filter { it.decoded.size == AFFINE_MAP_BYTES }
        val graphics = streams.filter { it.decoded.isNotEmpty() && it.decoded.size % AFFINE_TILE_BYTES == 0 }
        return buildList {
            maps.forEach { map ->
                graphics.forEach { graphicsStream ->
                    if (map.offset == graphicsStream.offset ||
                        !shareCompleteLoaderFunction(references, functionIndex, map.offset, graphicsStream.offset)
                    ) {
                        return@forEach
                    }
                    paletteCandidates(
                        rom,
                        references,
                        functionIndex,
                        intArrayOf(map.offset, graphicsStream.offset),
                        AFFINE_PALETTE_COLORS,
                    )
                        .filter { palette ->
                            completeSites(references, palette.offset).orEmpty().any { site ->
                                val loadedBytes = paletteLoadByteCount(rom, site) ?: return@any false
                                loadedBytes >= AFFINE_PALETTE_COLORS * 2 && loadedBytes % 2 == 0
                            }
                        }
                        .forEach { palette ->
                            val result = GbaWorldMapCompositor.compose(graphicsStream.decoded, map.decoded, palette.colors)
                            if (result is GbaWorldMapComposition.Resolved &&
                                result.format == GbaWorldMapFormat.AFFINE_8BPP_64X64
                            ) {
                                add(AssetCandidate(graphicsStream.offset, map.offset, palette.offset, result))
                            }
                        }
                }
            }
        }.distinctBy(AssetCandidate::identity)
    }

    private fun textCandidates(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        streams: List<CompressedStream>,
    ): List<TextAssetCandidate> {
        val maps = streams.filter { it.decoded.size == TEXT_MAP_BYTES }
        val graphics = streams.filter { it.decoded.isNotEmpty() && it.decoded.size % TEXT_TILE_BYTES == 0 }
        return buildList {
            graphics.forEach { graphicsStream ->
                val functions = loaderFunctions(references, functionIndex, graphicsStream.offset)
                functions.forEach { functionStart ->
                    val referencedMaps = maps.filter { functionStart in loaderFunctions(references, functionIndex, it.offset) }
                    val palettes = paletteCandidatesInFunction(
                        rom,
                        references,
                        functionIndex,
                        functionStart,
                        excludedOffsets = intArrayOf(graphicsStream.offset) + referencedMaps.map(CompressedStream::offset),
                        colors = TEXT_PALETTE_COLORS,
                    )
                    palettes.forEach { palette ->
                        val resolvedMaps = referencedMaps.mapNotNull { map ->
                            val result = GbaWorldMapCompositor.compose(graphicsStream.decoded, map.decoded, palette.colors)
                            if (result is GbaWorldMapComposition.Resolved &&
                                result.format == GbaWorldMapFormat.TEXT_4BPP_30X20
                            ) {
                                TextMapCandidate(
                                    asset = AssetCandidate(graphicsStream.offset, map.offset, palette.offset, result),
                                    destinationOffset = compressedDestinationOffset(
                                        rom,
                                        references,
                                        functionIndex,
                                        map.offset,
                                        functionStart,
                                    ) ?: return@mapNotNull null,
                                )
                            } else {
                                null
                            }
                        }.distinctBy(TextMapCandidate::destinationOffset).sortedBy(TextMapCandidate::destinationOffset)
                        if (resolvedMaps.size == TEXT_LOADER_DESTINATION_COUNT &&
                            resolvedMaps.zipWithNext().all { (left, right) ->
                                right.destinationOffset - left.destinationOffset == TEXT_MAP_BYTES
                            }
                        ) {
                            add(
                                TextAssetCandidate(
                                    graphicsStream.offset,
                                    palette.offset,
                                    resolvedMaps.take(TEXT_REGION_COUNT).map(TextMapCandidate::asset),
                                ),
                            )
                        }
                    }
                }
            }
        }.distinctBy(TextAssetCandidate::identity)
    }

    private fun paletteCandidatesInFunction(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        functionStart: Int,
        excludedOffsets: IntArray,
        colors: Int,
    ): List<PaletteCandidate> = references.targets.keys.mapNotNull { offset ->
        if (offset in excludedOffsets || functionStart !in loaderFunctions(references, functionIndex, offset)) return@mapNotNull null
        val sites = completeSites(references, offset).orEmpty().filter { functionIndex.functionStart(it) == functionStart }
        if (sites.none { paletteLoadByteCount(rom, it) == colors * 2 }) return@mapNotNull null
        readPalette(rom, offset, colors)
    }

    private fun paletteLoadByteCount(rom: RomImage, site: Int): Int? {
        if (site < 0 || site.toLong() + 2 > rom.size.toLong()) return null
        val sourceLoad = rom.u16le(site)
        if (sourceLoad and THUMB_LITERAL_LOAD_MASK != THUMB_LITERAL_LOAD_OPCODE ||
            sourceLoad ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK != 0
        ) {
            return null
        }
        return immediateArgumentsAtNextCall(rom, site)?.second
    }

    private fun immediateArgumentsAtNextCall(rom: RomImage, sourceSite: Int): Pair<Int, Int>? {
        val functionStart = ThumbFunctionIndex.functionStartByScan(rom, sourceSite) ?: return null
        var blockStart = sourceSite
        while (blockStart - 2 >= functionStart && !isThumbControlFlow(rom, blockStart - 2)) {
            blockStart -= 2
        }
        var first: Int? = null
        var second: Int? = null
        var cursor = blockStart
        while (cursor + 2 <= rom.size) {
            val instruction = rom.u16le(cursor)
            if (isThumbBl(rom, cursor)) {
                if (cursor <= sourceSite) return null
                return first?.let { left -> second?.let { right -> left to right } }
            }
            if (cursor != blockStart && isThumbControlFlow(rom, cursor)) return null
            val destination = thumbDestinationRegister(instruction)
            val immediate = if (instruction and THUMB_MOVE_IMMEDIATE_MASK == THUMB_MOVE_IMMEDIATE_OPCODE) {
                instruction and 0xff
            } else {
                null
            }
            when (destination) {
                1 -> first = immediate
                2 -> second = immediate
            }
            cursor += 2
        }
        return null
    }

    private fun thumbDestinationRegister(instruction: Int): Int? = when {
        instruction and THUMB_MOVE_IMMEDIATE_MASK == THUMB_MOVE_IMMEDIATE_OPCODE ->
            instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK
        instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE ->
            instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK
        instruction and 0xF800 in setOf(0x0000, 0x0800, 0x1000, 0x1800) -> instruction and 0x7
        instruction and 0xF800 in setOf(0x3000, 0x3800) ->
            instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK
        instruction and 0xFC00 == 0x4000 && instruction ushr 6 and 0xF !in setOf(0x8, 0xA, 0xB) ->
            instruction and 0x7
        instruction and 0xFC00 == 0x4400 && instruction ushr 8 and 0x3 in setOf(0, 2) ->
            (instruction and 0x7) or (instruction ushr 4 and 0x8)
        instruction and 0xF800 in setOf(0x6800, 0x7800, 0x8800) -> instruction and 0x7
        instruction and 0xF800 == 0x9800 -> instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK
        else -> null
    }

    private fun compressedDestinationOffset(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        target: Int,
        functionStart: Int,
    ): Int? {
        val offsets = completeSites(references, target).orEmpty().mapNotNull { site ->
            if (functionIndex.functionStart(site) != functionStart) return@mapNotNull null
            val sourceLoad = rom.u16le(site)
            if (sourceLoad and THUMB_LITERAL_LOAD_MASK != THUMB_LITERAL_LOAD_OPCODE ||
                sourceLoad ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK != 0
            ) {
                return@mapNotNull null
            }
            var cursor = site + 2
            while (cursor <= site + MAX_DESTINATION_SETUP_BYTES && cursor + 2 <= rom.size) {
                val instruction = rom.u16le(cursor)
                if (instruction and THUMB_ADD_IMMEDIATE_MASK == THUMB_ADD_R1_IMMEDIATE) {
                    return@mapNotNull instruction and 0xff
                }
                if (instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE &&
                    instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK == 2
                ) {
                    val literal = ((cursor + 4) and -4) + (instruction and 0xff) * 4
                    if (literal + 4 <= rom.size) return@mapNotNull rom.u32le(literal).toInt()
                }
                cursor += 2
            }
            null
        }.distinct()
        return offsets.singleOrNull()
    }

    private fun paletteCandidates(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        assets: IntArray,
        colors: Int,
    ): List<PaletteCandidate> = references.targets.keys.mapNotNull { offset ->
        if (offset in assets || offset.toLong() + colors * 2L > rom.size.toLong()) return@mapNotNull null
        if (!shareCompleteLoaderFunction(references, functionIndex, *(assets + offset))) return@mapNotNull null
        readPalette(rom, offset, colors)
    }

    private fun readPalette(rom: RomImage, offset: Int, colors: Int): PaletteCandidate? {
        if (offset < 0 || offset.toLong() + colors * 2L > rom.size.toLong()) return null
        val values = ShortArray(colors)
        val distinct = linkedSetOf<Int>()
        repeat(colors) { index ->
            val value = rom.u16le(offset + index * 2)
            if (value and 0x8000 != 0) return null
            values[index] = value.toShort()
            distinct += value
        }
        return PaletteCandidate(offset, values).takeIf { distinct.size >= 2 }
    }

    private fun resolveTextSemanticLayouts(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
    ): List<SemanticLayout>? {
        val layoutsByTarget = references.targets.keys.mapNotNull { target ->
            decodeSemanticLayout(rom, target)?.let { target to it }
        }
        val groups = layoutsByTarget.flatMap { (target, _) -> loaderFunctions(references, functionIndex, target) }
            .distinct()
            .mapNotNull { functionStart ->
                val members = layoutsByTarget.mapNotNull { (target, layout) ->
                    val regionSlot = semanticRegionSlot(rom, references, functionIndex, target, functionStart)
                        ?: return@mapNotNull null
                    Triple(regionSlot, target, layout)
                }
                if (members.size != TEXT_REGION_COUNT ||
                    members.map { it.first }.toSet() != (0 until TEXT_REGION_COUNT).toSet() ||
                    members.map { it.second }.distinct().size != TEXT_REGION_COUNT
                ) {
                    return@mapNotNull null
                }
                members.sortedBy { it.first }.map { it.third }
            }
        return groups.distinct().singleOrNull()
    }

    private fun semanticRegionSlot(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        target: Int,
        functionStart: Int,
    ): Int? {
        val slots = completeSites(references, target).orEmpty().mapNotNull { site ->
            if (functionIndex.functionStart(site) != functionStart) return@mapNotNull null
            val instruction = rom.u16le(site)
            if (instruction and THUMB_LITERAL_LOAD_MASK != THUMB_LITERAL_LOAD_OPCODE ||
                instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK != 3
            ) {
                return@mapNotNull null
            }
            var cursor = functionStart
            while (cursor < site) {
                val compare = rom.u16le(cursor)
                if (compare and THUMB_COMPARE_IMMEDIATE_MASK == THUMB_COMPARE_IMMEDIATE_OPCODE) {
                    val slot = compare and 0xff
                    if (slot in 0 until TEXT_REGION_COUNT && branchDestination(rom, cursor + 2) == site) {
                        return@mapNotNull slot
                    }
                }
                cursor += 2
            }
            null
        }.distinct()
        return slots.singleOrNull()
    }

    private fun branchDestination(rom: RomImage, branchOffset: Int): Int? {
        if (branchOffset + 2 > rom.size) return null
        val instruction = rom.u16le(branchOffset)
        if (instruction and THUMB_CONDITIONAL_BRANCH_MASK != THUMB_CONDITIONAL_BRANCH_OPCODE) return null
        val displacement = (instruction and 0xff).toByte().toInt() shl 1
        return branchOffset + 4 + displacement
    }

    private fun decodeSemanticLayout(rom: RomImage, offset: Int): SemanticLayout? {
        if (offset < 0 || offset.toLong() + SEMANTIC_LAYOUT_BYTES > rom.size.toLong()) return null
        val bytes = rom.slice(offset, SEMANTIC_LAYOUT_BYTES)
        val empty = bytes.asIterable().groupingBy { it.toInt() and 0xff }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value >= SEMANTIC_GRID_CELLS }?.key ?: return null
        val mapLayer = bytes.copyOfRange(0, SEMANTIC_GRID_CELLS)
        if (mapLayer.count { (it.toInt() and 0xff) != empty } < MIN_SEMANTIC_CELLS) return null
        val cellsBySection = linkedMapOf<Int, MutableList<WorldMapCell>>()
        mapLayer.forEachIndexed { index, value ->
            val section = value.toInt() and 0xff
            if (section != empty) {
                cellsBySection.getOrPut(section, ::mutableListOf) +=
                    WorldMapCell(index % TEXT_GRID_WIDTH, index / TEXT_GRID_WIDTH, 1, 1)
            }
        }
        if (cellsBySection.size < MIN_SEMANTIC_SECTIONS) return null
        return SemanticLayout(cellsBySection)
    }

    private fun decodeReferencedStreams(rom: RomImage, references: GbaReferenceIndex): List<CompressedStream> =
        references.targets.keys.mapNotNull { offset ->
            if (offset.toLong() + 4 > rom.size.toLong() || rom.u8(offset) != GBA_LZ_HEADER) return@mapNotNull null
            val declared = rom.u24le(offset + 1)
            if (declared !in 1..MAX_DECOMPRESSED_ASSET_BYTES) return@mapNotNull null
            runCatching { CompressedStream(offset, GbaRomCompression.decodeAt(rom, offset)) }.getOrNull()
        }

    private fun authoritative(candidates: List<AssetCandidate>): List<AssetCandidate> {
        return candidates.distinctBy(AssetCandidate::identity)
    }

    private fun authoritativeText(candidates: List<TextAssetCandidate>): List<TextAssetCandidate> {
        return candidates.distinctBy(TextAssetCandidate::identity)
    }

    private fun shareCompleteLoaderFunction(
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        vararg offsets: Int,
    ): Boolean {
        val functions = offsets.map { offset -> loaderFunctions(references, functionIndex, offset) }
        return functions.all(Set<Int>::isNotEmpty) && functions.reduce(Set<Int>::intersect).isNotEmpty()
    }

    private fun completeSites(references: GbaReferenceIndex, offset: Int): List<Int>? =
        references.target(offset)?.takeIf { it.siteEvidenceAvailable && it.instructionSites.isNotEmpty() }?.instructionSites

    internal fun requiredReferenceSitesComplete(references: GbaReferenceIndex, requiredTargets: Set<Int>): Boolean =
        requiredTargets.isNotEmpty() && requiredTargets.all { completeSites(references, it) != null }

    private fun loaderFunctions(
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        target: Int,
    ): Set<Int> = completeSites(references, target).orEmpty().mapNotNull(functionIndex::functionStart).toSet()

    private fun isThumbControlFlow(rom: RomImage, offset: Int): Boolean {
        if (offset < 0 || offset + 2 > rom.size) return true
        val instruction = rom.u16le(offset)
        return isThumbReturn(instruction) || isThumbBl(rom, offset) ||
            instruction and THUMB_CONDITIONAL_BRANCH_MASK == THUMB_CONDITIONAL_BRANCH_OPCODE ||
            instruction and THUMB_UNCONDITIONAL_BRANCH_MASK == THUMB_UNCONDITIONAL_BRANCH_OPCODE ||
            instruction and THUMB_BX_MASK == THUMB_BX_OPCODE
    }

    private fun isThumbBl(rom: RomImage, offset: Int): Boolean =
        offset >= 0 && offset + 4 <= rom.size &&
            rom.u16le(offset) and THUMB_BL_FIRST_MASK == THUMB_BL_FIRST_OPCODE &&
            rom.u16le(offset + 2) and THUMB_BL_SECOND_MASK == THUMB_BL_SECOND_OPCODE

    private class ThumbFunctionIndex private constructor(
        private val functionBySite: Map<Int, Int?>,
    ) {
        val analyzedSiteCount: Int get() = functionBySite.size

        fun functionStart(site: Int): Int? = functionBySite[site]

        companion object {
            fun build(rom: RomImage, references: GbaReferenceIndex): ThumbFunctionIndex {
                val sites = references.targets.values.flatMap(GbaTargetReferenceEvidence::instructionSites).distinct()
                return ThumbFunctionIndex(sites.associateWith { functionStartByScan(rom, it) })
            }

            fun functionStartByScan(rom: RomImage, site: Int): Int? {
                if (site !in 0 until rom.size || site and 1 != 0) return null
                var cursor = site
                while (cursor >= 0) {
                    val instruction = rom.u16le(cursor)
                    if (cursor < site && isThumbReturn(instruction)) return null
                    if (instruction and THUMB_PUSH_MASK == THUMB_PUSH_WITH_LR) return cursor
                    cursor -= 2
                }
                return null
            }
        }
    }

    private fun isThumbReturn(instruction: Int): Boolean =
        instruction and THUMB_POP_MASK == THUMB_POP_WITH_PC || instruction == THUMB_BX_LR

    private data class CompressedStream(val offset: Int, val decoded: ByteArray)
    private data class PaletteCandidate(val offset: Int, val colors: ShortArray)
    private data class SemanticLayout(val cellsBySection: Map<Int, List<WorldMapCell>>)
    private data class AssetCandidate(
        val graphicsOffset: Int,
        val mapOffset: Int,
        val paletteOffset: Int,
        val composition: GbaWorldMapComposition.Resolved,
    ) {
        val identity: Triple<Int, Int, Int> get() = Triple(graphicsOffset, mapOffset, paletteOffset)
    }
    private data class TextAssetCandidate(
        val graphicsOffset: Int,
        val paletteOffset: Int,
        val maps: List<AssetCandidate>,
    ) {
        val identity: List<Int> get() = listOf(graphicsOffset, paletteOffset) + maps.map(AssetCandidate::mapOffset)
    }
    private data class TextMapCandidate(val asset: AssetCandidate, val destinationOffset: Int)

    private const val GBA_LZ_HEADER = 0x10
    private const val MAX_DECOMPRESSED_ASSET_BYTES = 256 * 1_024
    private const val THUMB_PUSH_MASK = 0xFF00
    private const val THUMB_PUSH_WITH_LR = 0xB500
    private const val THUMB_POP_MASK = 0xFF00
    private const val THUMB_POP_WITH_PC = 0xBD00
    private const val THUMB_BX_LR = 0x4770
    private const val THUMB_BX_MASK = 0xFF87
    private const val THUMB_BX_OPCODE = 0x4700
    private const val THUMB_BL_FIRST_MASK = 0xF800
    private const val THUMB_BL_FIRST_OPCODE = 0xF000
    private const val THUMB_BL_SECOND_MASK = 0xF800
    private const val THUMB_BL_SECOND_OPCODE = 0xF800
    private const val THUMB_UNCONDITIONAL_BRANCH_MASK = 0xF800
    private const val THUMB_UNCONDITIONAL_BRANCH_OPCODE = 0xE000
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
    private const val THUMB_MOVE_IMMEDIATE_MASK = 0xF800
    private const val THUMB_MOVE_IMMEDIATE_OPCODE = 0x2000
    private const val THUMB_REGISTER_SHIFT = 8
    private const val THUMB_REGISTER_MASK = 0x7
    private const val THUMB_ADD_IMMEDIATE_MASK = 0xFF00
    private const val THUMB_ADD_R1_IMMEDIATE = 0x3100
    private const val MAX_DESTINATION_SETUP_BYTES = 16
    private const val THUMB_COMPARE_IMMEDIATE_MASK = 0xF800
    private const val THUMB_COMPARE_IMMEDIATE_OPCODE = 0x2800
    private const val THUMB_CONDITIONAL_BRANCH_MASK = 0xF000
    private const val THUMB_CONDITIONAL_BRANCH_OPCODE = 0xD000
    private const val AFFINE_MAP_BYTES = 4096
    private const val AFFINE_TILE_BYTES = 64
    private const val AFFINE_PALETTE_COLORS = 32
    private const val TEXT_MAP_BYTES = 1200
    private const val TEXT_TILE_BYTES = 32
    private const val TEXT_PALETTE_COLORS = 80
    private const val TEXT_REGION_COUNT = 4
    private const val TEXT_LOADER_DESTINATION_COUNT = TEXT_REGION_COUNT + 1
    private const val TEXT_GRID_WIDTH = 22
    private const val TEXT_GRID_HEIGHT = 15
    private const val SEMANTIC_GRID_CELLS = TEXT_GRID_WIDTH * TEXT_GRID_HEIGHT
    private const val SEMANTIC_LAYOUT_BYTES = SEMANTIC_GRID_CELLS * 2
    private const val MIN_SEMANTIC_CELLS = 12
    private const val MIN_SEMANTIC_SECTIONS = 3
}
