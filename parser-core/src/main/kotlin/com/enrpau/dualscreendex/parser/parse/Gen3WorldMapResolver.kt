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

/**
 * Finds source-family ABI assets through compiled loader references, then terminates that ABI at
 * the normalized compositor/catalog boundary. It deliberately knows no ROM identity or placement.
 */
object Gen3WorldMapResolver {
    fun resolve(session: RomAnalysisSession, encounterBaseIds: Set<Int>): WorldMapResolution {
        val references = session.gbaReferenceIndex
            ?: return WorldMapResolution.Unavailable("asset-loader", "compiled GBA references are unavailable")
        references.overflowReason?.let { return WorldMapResolution.BudgetExceeded("asset-loader", it) }
        val functionIndex = ThumbFunctionIndex.build(session.rom, references)
        val streams = decodeReferencedStreams(session.rom, references)
        val eightBpp = eightBppCandidates(session.rom, references, functionIndex, streams)
        val text = textCandidates(session.rom, references, functionIndex, streams)
        mapTrace(
            "summary streams=${streams.size} eightBpp=${eightBpp.size} text=${text.size} " +
                "referenceSites=${functionIndex.analyzedSiteCount}",
        )
        if (eightBpp.isNotEmpty() && text.isNotEmpty()) {
            return WorldMapResolution.Ambiguous(
                "asset-loader",
                "multiple proven world-map loader formats remained eligible",
            )
        }
        val resolution = when {
            eightBpp.isNotEmpty() -> resolve8Bpp(session.rom, encounterBaseIds, references, eightBpp)
            text.isNotEmpty() -> resolveText(session.rom, encounterBaseIds, references, functionIndex, text)
            else -> WorldMapResolution.Unavailable(
                "asset-loader",
                "no compiled-reference tile, tilemap, and BGR555 palette cluster passed a proven loader contract",
            )
        }
        return if (resolution is WorldMapResolution.Resolved) {
            resolution.copy(
                reasons = resolution.reasons +
                    "indexed ${functionIndex.analyzedSiteCount} distinct compiled reference sites once",
            )
        } else {
            resolution
        }
    }

    private fun resolve8Bpp(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        candidates: List<AssetCandidate>,
    ): WorldMapResolution {
        val winners = authoritative(candidates)
        if (winners.size != 1) {
            return WorldMapResolution.Ambiguous(
                "asset-loader",
                "${winners.size} equally authoritative 8bpp asset clusters remained",
            )
        }
        val locations = Gen3MapLocationResolver.resolveDetailed(rom, encounterBaseIds, references)
            ?: return WorldMapResolution.Unavailable(
                "map-header-join",
                "encounter map headers and region entries did not resolve uniquely",
            )
        val winner = winners.single()
        val normalized = emeraldLocations(locations, winner.composition.gridWidth, winner.composition.gridHeight)
        if (normalized.isEmpty()) {
            return WorldMapResolution.Unavailable(
                "encounter-binding",
                "region entries retained no encounter binding",
            )
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
        return WorldMapResolution.Resolved(
            WorldMapCatalog(listOf(region), mapOf(assetKey to winner.composition.raster)).validate(),
            listOf(
                "validated one 8bpp loader asset cluster",
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
    ): WorldMapResolution {
        val winnerGroups = authoritativeText(candidates)
        if (winnerGroups.size != 1) {
            return WorldMapResolution.Ambiguous(
                "asset-loader",
                "${winnerGroups.size} equally authoritative text-map asset clusters remained",
            )
        }
        val winner = winnerGroups.single()
        if (winner.regions.isEmpty() || winner.regions.map(TextRegionAsset::slot) != (0 until winner.regions.size).toList()) {
            return WorldMapResolution.Unavailable(
                "map-plane",
                "text-map loader cluster did not expose contiguous branch-owned regions",
            )
        }
        val semanticCandidates = resolveTextSemanticLayouts(
            rom,
            references,
            functionIndex,
            winner.regions.map(TextRegionAsset::slot).toSet(),
        )
        val sectionByBaseArea = Gen3MapLocationResolver.resolveSectionByBaseArea(rom, encounterBaseIds, references)
        if (sectionByBaseArea.isEmpty()) {
            return WorldMapResolution.Unavailable(
                "map-header-join",
                "encounter map headers did not resolve a semantic section join",
            )
        }
        val baseAreasBySection = sectionByBaseArea.entries.groupBy({ it.value }, { it.key })
        val layoutWinner = when (semanticCandidates.size) {
            1 -> semanticCandidates.single()
            else -> {
                val layouts = semanticCandidates.filter { candidate ->
                    candidate.all { textLocations(it, baseAreasBySection).isNotEmpty() }
                }
                val maximumBindings = layouts.maxOfOrNull { candidate ->
                    candidate.sumOf { layout -> textLocations(layout, baseAreasBySection).size }
                }
                maximumBindings?.let { count ->
                    layouts.filter { candidate ->
                        candidate.sumOf { layout -> textLocations(layout, baseAreasBySection).size } == count
                    }.singleOrNull()
                }
            }
        } ?: return WorldMapResolution.Unavailable(
            "map-plane",
            "text-map semantic section planes did not resolve uniquely",
        )
        val regions = mutableListOf<WorldMapRegion>()
        val assets = linkedMapOf<String, com.enrpau.dualscreendex.parser.catalog.RgbaSprite>()
        winner.regions.zip(layoutWinner).forEach { (regionAsset, layout) ->
            val index = regionAsset.slot
            val normalized = textLocations(layout, baseAreasBySection)
            if (normalized.isEmpty()) {
                return WorldMapResolution.Unavailable(
                    "encounter-binding",
                    "text-map region $index retained no encounter binding",
                )
            }
            val regionKey = "gen3-region-$index"
            val assetKey = "world/$regionKey"
            regions += WorldMapRegion(
                regionKey,
                null,
                regionAsset.asset.composition.raster.width,
                regionAsset.asset.composition.raster.height,
                regionAsset.asset.composition.gridWidth,
                regionAsset.asset.composition.gridHeight,
                assetKey,
                normalized,
            )
            assets[assetKey] = regionAsset.asset.composition.raster
        }
        return WorldMapResolution.Resolved(
            WorldMapCatalog(regions, assets).validate(),
            listOf(
                "validated one text-map loader cluster with ${winner.regions.size} branch-owned regions",
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

    private fun eightBppCandidates(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        streams: List<CompressedStream>,
    ): List<AssetCandidate> {
        val maps = streams.filter {
            it.decoded.size == AFFINE_MAP_BYTES ||
                it.decoded.size == TILED_8BPP_MAP_BYTES
        }
        val graphics = streams.filter { it.decoded.isNotEmpty() && it.decoded.size % AFFINE_TILE_BYTES == 0 }
        if (mapTraceEnabled) {
            maps.filter { loaderFunctions(references, functionIndex, it.offset).isNotEmpty() }.forEach { map ->
                mapTrace(
                    "affine-map offset=0x${map.offset.toString(16)} functions=" +
                        loaderFunctions(references, functionIndex, map.offset).joinToString { "0x${it.toString(16)}" },
                )
            }
            graphics.filter {
                it.decoded.size in 10_000..20_000 &&
                    loaderFunctions(references, functionIndex, it.offset).isNotEmpty()
            }.forEach { graphic ->
                mapTrace(
                    "affine-gfx offset=0x${graphic.offset.toString(16)} size=${graphic.decoded.size} functions=" +
                        loaderFunctions(references, functionIndex, graphic.offset).joinToString { "0x${it.toString(16)}" },
                )
            }
        }
        return buildList {
            maps.forEach { map ->
                graphics.forEach { graphicsStream ->
                    if (map.offset == graphicsStream.offset) return@forEach
                    val sharedFunctions = sharedLoaderFunctions(
                        references,
                        functionIndex,
                        map.offset,
                        graphicsStream.offset,
                    )
                    if (sharedFunctions.isEmpty()) return@forEach
                    val sharedFunction = sharedFunctions.singleOrNull() ?: return@forEach
                    val unique8BppMap =
                        unique8BppMapInFunction(
                            references,
                            functionIndex,
                            maps,
                            sharedFunction,
                        ) == map.offset
                    val mapBranchArm = branchArmOwnership(
                        rom,
                        sharedFunction,
                        completeSites(references, map.offset).orEmpty()
                            .filter { functionIndex.functionStart(it) == sharedFunction },
                    )
                    val graphicsBranchArm = branchArmOwnership(
                        rom,
                        sharedFunction,
                        completeSites(references, graphicsStream.offset).orEmpty()
                            .filter { functionIndex.functionStart(it) == sharedFunction },
                    )
                    if (
                        mapBranchArm != null &&
                        graphicsBranchArm != null &&
                        graphicsBranchArm != mapBranchArm
                    ) return@forEach
                    if (mapTraceEnabled) {
                        mapTrace(
                            "affine-pair map=0x${map.offset.toString(16)} mapArm=$mapBranchArm " +
                                "gfx=0x${graphicsStream.offset.toString(16)} size=${graphicsStream.decoded.size} " +
                                "gfxArm=$graphicsBranchArm functions=${sharedFunctions.joinToString { "0x${it.toString(16)}" }}",
                        )
                    }
                    paletteCandidates(
                        rom,
                        references,
                        functionIndex,
                        intArrayOf(map.offset, graphicsStream.offset),
                    )
                        .onEach { palette ->
                            if (mapTraceEnabled) {
                                val loads = completeSites(references, palette.offset).orEmpty().mapNotNull { site ->
                                    paletteLoadByteCount(rom, site)
                                }.distinct()
                                mapTrace(
                                    "affine-cluster map=0x${map.offset.toString(16)} " +
                                        "gfx=0x${graphicsStream.offset.toString(16)} " +
                                        "palette=0x${palette.offset.toString(16)} loads=$loads",
                                )
                            }
                        }
                        .filter { palette ->
                            completeSites(references, palette.offset).orEmpty().any { site ->
                                paletteLoadContract(rom, site)
                            }
                        }
                        .forEach { palette ->
                            val result = GbaWorldMapCompositor.compose(graphicsStream.decoded, map.decoded, palette.colors)
                            mapTrace(
                                "affine-compose map=0x${map.offset.toString(16)} " +
                                    "gfx=0x${graphicsStream.offset.toString(16)} " +
                                    "palette=0x${palette.offset.toString(16)} result=${compositionTrace(result)}",
                            )
                            if (result is GbaWorldMapComposition.Resolved &&
                                result.format in setOf(
                                    GbaWorldMapFormat.AFFINE_8BPP_64X64,
                                    GbaWorldMapFormat.TILED_8BPP_32X20,
                                ) &&
                                unique8BppMap
                            ) {
                                add(
                                    AssetCandidate(
                                        graphicsStream.offset,
                                        map.offset,
                                        palette.offset,
                                        result,
                                        completeSites(references, palette.offset).orEmpty().any { site ->
                                            immediatePaletteLoadByteCount(rom, site) != null
                                        },
                                    ),
                                )
                            }
                        }
                }
            }
        }.distinctBy(AssetCandidate::identity)
    }

    private fun unique8BppMapInFunction(
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        maps: List<CompressedStream>,
        functionStart: Int,
    ): Int? = maps.filter { functionStart in loaderFunctions(references, functionIndex, it.offset) }
        .map(CompressedStream::offset)
        .distinct()
        .singleOrNull()

    private fun textCandidates(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        streams: List<CompressedStream>,
    ): List<TextAssetCandidate> {
        val maps = streams.filter { GbaWorldMapCompositor.isTextMapByteLength(it.decoded.size) }
        val graphics = streams.filter {
            it.decoded.isNotEmpty() && it.decoded.size % TEXT_TILE_BYTES == 0 &&
                !GbaWorldMapCompositor.isTextMapByteLength(it.decoded.size)
        }
        return buildList {
            functionsWithTextMaps(references, functionIndex, maps).forEach { functionStart ->
                val referencedMaps = maps.filter { functionStart in loaderFunctions(references, functionIndex, it.offset) }
                val mapsBySlot = referencedMaps.mapNotNull { map ->
                    compressedDestinationOffset(rom, references, functionIndex, map.offset, functionStart)
                        ?.let { destination -> destination to map }
                }.groupBy({ it.first }, { it.second })
                val destinations = mapsBySlot.keys.sorted()
                val baseDestination = destinations.firstOrNull() ?: return@forEach
                val slotByDestination = destinations.mapNotNull { destination ->
                    val delta = destination - baseDestination
                    if (delta >= 0 && delta % TEXT_MAP_BYTES == 0) destination to (delta / TEXT_MAP_BYTES) else null
                }.toMap()
                mapTrace(
                    "text-loader function=0x${functionStart.toString(16)} maps=${referencedMaps.size} " +
                        "destinations=${destinations.joinToString { "0x${it.toString(16)}" }} " +
                        "slots=${slotByDestination.values.sorted()}",
                )
                if (slotByDestination.size != destinations.size) return@forEach

                val bundles = graphics.mapNotNull { graphicsStream ->
                    if (functionStart !in loaderFunctions(references, functionIndex, graphicsStream.offset)) return@mapNotNull null
                    val palettes = paletteCandidatesInFunction(
                        rom,
                        references,
                        functionIndex,
                        functionStart,
                        excludedOffsets = intArrayOf(graphicsStream.offset) + referencedMaps.map(CompressedStream::offset),
                    )
                    palettes.mapNotNull { palette ->
                        val graphicsSlot = branchSelectionSlot(
                            rom,
                            references,
                            functionIndex,
                            functionStart,
                            setOf(graphicsStream.offset),
                        )
                        val paletteSlot = branchSelectionSlot(
                            rom,
                            references,
                            functionIndex,
                            functionStart,
                            setOf(palette.offset),
                        )
                        if (graphicsSlot != null && paletteSlot != null && graphicsSlot != paletteSlot) {
                            return@mapNotNull null
                        }
                        TextAssetBundle(graphicsStream, palette, graphicsSlot ?: paletteSlot)
                    }
                }.flatten()

                val regionAssets = slotByDestination.entries.mapNotNull { (destination, slot) ->
                    if (slot == TEXT_BACKGROUND_SLOT) return@mapNotNull null
                    val map = mapsBySlot[destination]?.singleOrNull() ?: return@mapNotNull null
                    val eligible = bundles.mapNotNull { bundle ->
                        if (bundle.branchSlot != null && bundle.branchSlot != slot) return@mapNotNull null
                        val result = GbaWorldMapCompositor.compose(bundle.graphics.decoded, map.decoded, bundle.palette.colors)
                        mapTrace(
                            "text-compose function=0x${functionStart.toString(16)} slot=$slot " +
                                "map=0x${map.offset.toString(16)} gfx=0x${bundle.graphics.offset.toString(16)} " +
                                "palette=0x${bundle.palette.offset.toString(16)} branch=${bundle.branchSlot} " +
                                "result=${compositionTrace(result)}",
                        )
                        if (result is GbaWorldMapComposition.Resolved && result.format == GbaWorldMapFormat.TEXT_4BPP_30X20) {
                            TextRegionAsset(
                                slot,
                                AssetCandidate(bundle.graphics.offset, map.offset, bundle.palette.offset, result),
                            )
                        } else {
                            null
                        }
                    }.distinctBy { it.asset.identity }
                    when {
                        eligible.size == 1 -> eligible.single()
                        eligible.isEmpty() -> null
                        else -> eligible.singleOrNull { candidate ->
                            bundles.any { bundle ->
                                bundle.branchSlot == slot &&
                                    bundle.graphics.offset == candidate.asset.graphicsOffset &&
                                    bundle.palette.offset == candidate.asset.paletteOffset
                            }
                        }
                    }
                }.sortedBy(TextRegionAsset::slot)

                if (regionAssets.isNotEmpty() &&
                    regionAssets.map(TextRegionAsset::slot) == (0 until regionAssets.size).toList() &&
                    (regionAssets.size > 1 || destinations.size == TEXT_LOADER_DESTINATION_COUNT)
                ) {
                    add(TextAssetCandidate(functionStart, regionAssets))
                }
            }
        }.distinctBy(TextAssetCandidate::identity)
    }

    private fun functionsWithTextMaps(
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        maps: List<CompressedStream>,
    ): Set<Int> = maps.flatMap { loaderFunctions(references, functionIndex, it.offset) }.toSet()

    private fun branchArmOwnership(
        rom: RomImage,
        functionStart: Int,
        sites: List<Int>,
    ): BranchArm? {
        val arms = sites.mapNotNull { site -> branchArmAtSite(rom, functionStart, site) }.distinct()
        return arms.singleOrNull()
    }

    private fun branchArmAtSite(
        rom: RomImage,
        functionStart: Int,
        site: Int,
    ): BranchArm? {
        var branchOffset = functionStart
        var owner: BranchArm? = null
        while (branchOffset + 2 <= site) {
            val target = branchDestination(rom, branchOffset)
            if (target != null && target > branchOffset + 2) {
                val fallthroughEnd = forwardUnconditionalBranchBefore(
                    rom,
                    branchOffset + 2,
                    target,
                )
                val join = fallthroughEnd?.let { unconditionalBranchDestination(rom, it) }
                val forwardArm = if (fallthroughEnd != null && join != null && join > target) {
                    when (site) {
                        in (branchOffset + 2) until fallthroughEnd -> BranchArm(branchOffset, false)
                        in target until join -> BranchArm(branchOffset, true)
                        else -> null
                    }
                } else {
                    null
                }
                val backwardArm = backwardJoinArmAtSite(
                    rom,
                    branchOffset,
                    target,
                    site,
                )
                val arm = forwardArm ?: backwardArm
                if (arm != null) owner = arm
            }
            branchOffset += 2
        }
        return owner
    }

    private fun backwardJoinArmAtSite(
        rom: RomImage,
        branchOffset: Int,
        target: Int,
        site: Int,
    ): BranchArm? {
        var fallthroughReturn = branchOffset + 2
        while (
            fallthroughReturn < target &&
            fallthroughReturn + 2 <= rom.size &&
            !isThumbReturnOrIndirectBranch(rom.u16le(fallthroughReturn))
        ) {
            fallthroughReturn += 2
        }
        if (fallthroughReturn >= target) return null

        val limit = minOf(rom.size, target + MAX_BRANCH_ARM_SCAN_BYTES)
        var takenEnd = target
        while (takenEnd + 2 <= limit) {
            val destination = unconditionalBranchDestination(rom, takenEnd)
            if (
                destination != null &&
                destination in (branchOffset + 2) until fallthroughReturn
            ) {
                return when (site) {
                    in (branchOffset + 2) until destination ->
                        BranchArm(branchOffset, false)

                    in target until takenEnd ->
                        BranchArm(branchOffset, true)

                    else -> null
                }
            }
            if (isThumbReturnOrIndirectBranch(rom.u16le(takenEnd))) return null
            takenEnd += 2
        }
        return null
    }

    private fun forwardUnconditionalBranchBefore(
        rom: RomImage,
        start: Int,
        end: Int,
    ): Int? {
        var cursor = start
        var owner: Int? = null
        while (cursor + 2 <= end) {
            val destination = unconditionalBranchDestination(rom, cursor)
            if (destination != null && destination > end) owner = cursor
            cursor += 2
        }
        return owner
    }

    private fun unconditionalBranchDestination(rom: RomImage, branchOffset: Int): Int? {
        if (branchOffset < 0 || branchOffset + 2 > rom.size) return null
        val instruction = rom.u16le(branchOffset)
        if (instruction and THUMB_UNCONDITIONAL_BRANCH_MASK != THUMB_UNCONDITIONAL_BRANCH_OPCODE) return null
        val encoded = instruction and THUMB_UNCONDITIONAL_BRANCH_DISPLACEMENT_MASK
        val displacement = if (encoded and THUMB_UNCONDITIONAL_BRANCH_SIGN != 0) {
            encoded or THUMB_UNCONDITIONAL_BRANCH_SIGN_EXTENSION
        } else {
            encoded
        }
        return branchOffset + 4 + (displacement shl 1)
    }

    private fun branchSelectionSlot(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        functionStart: Int,
        targets: Set<Int>,
    ): Int? {
        val sites = targets.flatMap { target ->
            completeSites(references, target).orEmpty().filter { functionIndex.functionStart(it) == functionStart }
        }
        if (sites.isEmpty()) return null
        val slots = sites.mapNotNull { site -> precedingBranchSlot(rom, functionStart, site) }.distinct()
        return slots.singleOrNull()
    }

    private fun precedingBranchSlot(rom: RomImage, functionStart: Int, site: Int): Int? {
        var cursor = functionStart
        var exact: Pair<Int, Int>? = null
        var guardedFallthrough: Pair<Int, Int>? = null
        while (cursor + 4 <= site) {
            val compare = rom.u16le(cursor)
            if (compare and THUMB_COMPARE_IMMEDIATE_MASK == THUMB_COMPARE_IMMEDIATE_OPCODE) {
                val branchOffset = cursor + 2
                val branch = branchDestination(rom, branchOffset)
                val slot = compare and 0xff
                when {
                    branch == site -> exact = cursor to slot
                    branch != null && branch > site &&
                        thumbBranchCondition(rom, branchOffset) == THUMB_CONDITION_NOT_EQUAL -> {
                        guardedFallthrough = cursor to slot
                    }
                }
            }
            cursor += 2
        }
        return (exact ?: guardedFallthrough)?.second
    }

    private fun thumbBranchCondition(rom: RomImage, branchOffset: Int): Int? {
        if (branchOffset < 0 || branchOffset + 2 > rom.size) return null
        val instruction = rom.u16le(branchOffset)
        if (instruction and THUMB_CONDITIONAL_BRANCH_MASK != THUMB_CONDITIONAL_BRANCH_OPCODE) return null
        return instruction ushr 8 and 0xf
    }

    private fun paletteCandidatesInFunction(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        functionStart: Int,
        excludedOffsets: IntArray,
    ): List<PaletteCandidate> = references.targets.keys.mapNotNull { offset ->
        if (offset in excludedOffsets || functionStart !in loaderFunctions(references, functionIndex, offset)) return@mapNotNull null
        val byteCounts = completeSites(references, offset).orEmpty()
            .filter { functionIndex.functionStart(it) == functionStart }
            .mapNotNull { paletteLoadByteCount(rom, it) }
            .filter { it in MIN_TEXT_PALETTE_BYTES..MAX_TEXT_PALETTE_BYTES && it % BYTES_PER_PALETTE_BANK == 0 }
            .distinct()
        val byteCount = byteCounts.singleOrNull() ?: return@mapNotNull null
        readPalette(rom, offset, byteCount / 2)
    }

    private fun paletteLoadByteCount(rom: RomImage, site: Int): Int? {
        val immediate = immediatePaletteLoadByteCount(rom, site)
        if (immediate != null) return immediate
        if (!isPaletteSourceLoad(rom, site)) return null
        val cpuSetBytes = cpuSetByteCountAtNextCall(rom, site) ?: return null
        return cpuSetBytes.takeIf {
            cpuSetWritableDestination(rom, site, cpuSetBytes)
        }
    }

    private fun immediatePaletteLoadByteCount(
        rom: RomImage,
        site: Int,
    ): Int? = if (isPaletteSourceLoad(rom, site)) {
        immediateArgumentsAtNextCall(rom, site)?.second
    } else {
        null
    }

    private fun isPaletteSourceLoad(rom: RomImage, site: Int): Boolean {
        if (site < 0 || site.toLong() + 2 > rom.size.toLong()) return false
        val instruction = rom.u16le(site)
        return instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE &&
            instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK == 0
    }

    private fun paletteLoadContract(rom: RomImage, site: Int): Boolean {
        val byteCount = paletteLoadByteCount(rom, site) ?: return false
        return byteCount in AFFINE_PALETTE_BYTES..MAX_AFFINE_PALETTE_BYTES && byteCount % 2 == 0
    }

    private fun cpuSetWritableDestination(
        rom: RomImage,
        sourceSite: Int,
        byteCount: Int,
    ): Boolean {
        val functionStart = ThumbFunctionIndex.functionStartByScan(rom, sourceSite) ?: return false
        var blockStart = sourceSite
        while (blockStart - 2 >= functionStart && !isThumbControlFlow(rom, blockStart - 2)) {
            blockStart -= 2
        }
        var destination: Long? = null
        var cursor = blockStart
        while (cursor <= sourceSite + MAX_PALETTE_SETUP_BYTES && cursor + 2 <= rom.size) {
            val instruction = rom.u16le(cursor)
            if (isThumbBl(rom, cursor)) {
                if (cursor <= sourceSite) return false
                val address = destination ?: return false
                return CPU_SET_DESTINATION_RANGES.any { range ->
                    address >= range.first && address + byteCount.toLong() <= range.last + 1
                }
            }
            if (cursor != blockStart && isThumbControlFlow(rom, cursor)) return false
            if (thumbDestinationRegister(instruction) == 1) {
                destination = if (
                    instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE
                ) {
                    val literal = ((cursor + 4) and -4) + (instruction and 0xff) * 4
                    if (literal + 4 <= rom.size) rom.u32le(literal) else null
                } else {
                    null
                }
            }
            cursor += 2
        }
        return false
    }

    private fun cpuSetByteCountAtNextCall(rom: RomImage, sourceSite: Int): Int? {
        val functionStart = ThumbFunctionIndex.functionStartByScan(rom, sourceSite) ?: return null
        var blockStart = sourceSite
        while (blockStart - 2 >= functionStart && !isThumbControlFlow(rom, blockStart - 2)) {
            blockStart -= 2
        }
        var control: Long? = null
        var cursor = blockStart
        while (cursor + 2 <= rom.size) {
            val instruction = rom.u16le(cursor)
            if (isThumbBl(rom, cursor)) {
                if (cursor <= sourceSite) return null
                val value = control ?: return null
                if (value and CPU_SET_RESERVED_MASK != 0L) return null
                val count = value and CPU_SET_COUNT_MASK
                if (count == 0L) return null
                val unitBytes = if (value and CPU_SET_32_BIT != 0L) 4L else 2L
                return runCatching { Math.multiplyExact(count, unitBytes).toInt() }.getOrNull()
            }
            if (cursor != blockStart && isThumbControlFlow(rom, cursor)) return null
            if (thumbDestinationRegister(instruction) == 2) {
                control = when {
                    instruction and THUMB_MOVE_IMMEDIATE_MASK == THUMB_MOVE_IMMEDIATE_OPCODE ->
                        (instruction and 0xff).toLong()
                    instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE -> {
                        val literal = ((cursor + 4) and -4) + (instruction and 0xff) * 4
                        if (literal + 4 <= rom.size) rom.u32le(literal) else null
                    }
                    else -> null
                }
            }
            cursor += 2
        }
        return null
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
    ): List<PaletteCandidate> = references.targets.keys.mapNotNull { offset ->
        if (offset in assets) return@mapNotNull null
        if (!shareCompleteLoaderFunction(references, functionIndex, *(assets + offset))) return@mapNotNull null
        val byteCounts = completeSites(references, offset).orEmpty()
            .mapNotNull { site -> paletteLoadByteCount(rom, site) }
            .filter { it in AFFINE_PALETTE_BYTES..MAX_AFFINE_PALETTE_BYTES && it % 2 == 0 }
            .distinct()
        val byteCount = byteCounts.singleOrNull() ?: return@mapNotNull null
        readPalette(rom, offset, byteCount / 2)
    }

    private fun readPalette(rom: RomImage, offset: Int, colors: Int): PaletteCandidate? {
        if (offset < 0 || offset.toLong() + colors * 2L > rom.size.toLong()) return null
        val values = ShortArray(colors)
        val distinct = linkedSetOf<Int>()
        repeat(colors) { index ->
            val value = rom.u16le(offset + index * 2) and GBA_BGR555_MASK
            values[index] = value.toShort()
            distinct += value
        }
        return PaletteCandidate(offset, values).takeIf { distinct.size >= 2 }
    }

    private fun resolveTextSemanticLayouts(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        requiredSlots: Set<Int>,
    ): List<List<SemanticLayout>> {
        val layoutsByTarget = references.targets.keys.mapNotNull { target ->
            decodeSemanticLayout(rom, target)?.let { target to it }
        }
        val groups = layoutsByTarget.flatMap { (target, _) -> loaderFunctions(references, functionIndex, target) }
            .distinct()
            .mapNotNull { functionStart ->
                val members = layoutsByTarget.mapNotNull { (target, layout) ->
                    val regionSlot = semanticRegionSlot(
                        rom,
                        references,
                        functionIndex,
                        target,
                        functionStart,
                        requiredSlots,
                    ) ?: return@mapNotNull null
                    Triple(regionSlot, target, layout)
                }
                if (members.size != requiredSlots.size ||
                    members.map { it.first }.toSet() != requiredSlots ||
                    members.map { it.second }.distinct().size != requiredSlots.size
                ) {
                    return@mapNotNull null
                }
                members.sortedBy { it.first }.map { it.third }
            }
        return groups.distinct()
    }

    private fun semanticRegionSlot(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        target: Int,
        functionStart: Int,
        requiredSlots: Set<Int>,
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
                    if (slot in requiredSlots && branchDestination(rom, cursor + 2) == site) {
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
        val distinct = candidates.distinctBy(AssetCandidate::identity)
        val immediatePaletteLoads = distinct.filter(AssetCandidate::immediatePaletteLoad)
        return immediatePaletteLoads.ifEmpty { distinct }
    }

    private fun authoritativeText(candidates: List<TextAssetCandidate>): List<TextAssetCandidate> {
        return candidates.distinctBy(TextAssetCandidate::identity)
    }

    private fun shareCompleteLoaderFunction(
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        vararg offsets: Int,
    ): Boolean = sharedLoaderFunctions(references, functionIndex, *offsets).isNotEmpty()

    private fun sharedLoaderFunctions(
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        vararg offsets: Int,
    ): Set<Int> {
        val functions = offsets.map { offset -> loaderFunctions(references, functionIndex, offset) }
        return if (functions.all(Set<Int>::isNotEmpty)) functions.reduce(Set<Int>::intersect) else emptySet()
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

    private fun compositionTrace(result: GbaWorldMapComposition): String = when (result) {
        is GbaWorldMapComposition.Resolved -> "resolved:${result.format}"
        is GbaWorldMapComposition.Rejected -> "rejected:${result.reason}"
    }

    private fun mapTrace(message: String) {
        if (mapTraceEnabled) println("world-map-trace $message")
    }

    private val mapTraceEnabled: Boolean
        get() = System.getenv("DUALDEX_MAP_TRACE") == "1"

    private fun isThumbReturnOrIndirectBranch(instruction: Int): Boolean =
        isThumbReturn(instruction) || instruction and THUMB_BX_MASK == THUMB_BX_OPCODE

    private fun isThumbReturn(instruction: Int): Boolean =
        instruction and THUMB_POP_MASK == THUMB_POP_WITH_PC || instruction == THUMB_BX_LR

    private data class BranchArm(val branchOffset: Int, val taken: Boolean)
    private data class CompressedStream(val offset: Int, val decoded: ByteArray)
    private data class PaletteCandidate(val offset: Int, val colors: ShortArray)
    private data class SemanticLayout(val cellsBySection: Map<Int, List<WorldMapCell>>)
    private data class AssetCandidate(
        val graphicsOffset: Int,
        val mapOffset: Int,
        val paletteOffset: Int,
        val composition: GbaWorldMapComposition.Resolved,
        val immediatePaletteLoad: Boolean = false,
    ) {
        val identity: Triple<Int, Int, Int> get() = Triple(graphicsOffset, mapOffset, paletteOffset)
    }
    private data class TextAssetBundle(
        val graphics: CompressedStream,
        val palette: PaletteCandidate,
        val branchSlot: Int?,
    )
    private data class TextRegionAsset(val slot: Int, val asset: AssetCandidate)
    private data class TextAssetCandidate(
        val functionStart: Int,
        val regions: List<TextRegionAsset>,
    ) {
        val identity: List<Int> get() = listOf(functionStart) + regions.flatMap { region ->
            listOf(region.slot, region.asset.graphicsOffset, region.asset.mapOffset, region.asset.paletteOffset)
        }
    }

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
    private const val THUMB_UNCONDITIONAL_BRANCH_DISPLACEMENT_MASK = 0x7FF
    private const val THUMB_UNCONDITIONAL_BRANCH_SIGN = 0x400
    private const val THUMB_UNCONDITIONAL_BRANCH_SIGN_EXTENSION = -0x800
    private const val MAX_BRANCH_ARM_SCAN_BYTES = 1_024
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
    private const val THUMB_CONDITION_NOT_EQUAL = 0x1
    private const val CPU_SET_COUNT_MASK = 0x1F_FFFFL
    private const val CPU_SET_32_BIT = 1L shl 26
    private const val CPU_SET_RESERVED_MASK = 0xFAE0_0000L
    private const val AFFINE_PALETTE_COLORS = 32
    private const val AFFINE_PALETTE_BYTES = AFFINE_PALETTE_COLORS * 2
    private const val MAX_AFFINE_PALETTE_BYTES = 256 * 2
    private const val MAX_PALETTE_SETUP_BYTES = 16
    private const val GBA_BGR555_MASK = 0x7FFF
    private val CPU_SET_DESTINATION_RANGES = arrayOf(
        0x0200_0000L..0x0203_FFFFL,
        0x0300_0000L..0x0300_7FFFL,
        0x0500_0000L..0x0500_03FFL,
    )
    private const val AFFINE_MAP_BYTES = 4096
    private const val TILED_8BPP_MAP_BYTES = 32 * 20 * 2
    private const val AFFINE_TILE_BYTES = 64
    private const val TEXT_MAP_BYTES = 1200
    private const val TEXT_TILE_BYTES = 32
    private const val MIN_TEXT_PALETTE_BYTES = 16 * 2
    private const val MAX_TEXT_PALETTE_BYTES = 16 * 16 * 2
    private const val BYTES_PER_PALETTE_BANK = 16 * 2
    private const val TEXT_BACKGROUND_SLOT = 4
    private const val TEXT_LOADER_DESTINATION_COUNT = 5
    private const val TEXT_GRID_WIDTH = 22
    private const val TEXT_GRID_HEIGHT = 15
    private const val SEMANTIC_GRID_CELLS = TEXT_GRID_WIDTH * TEXT_GRID_HEIGHT
    private const val SEMANTIC_LAYOUT_BYTES = SEMANTIC_GRID_CELLS * 2
    private const val MIN_SEMANTIC_CELLS = 12
    private const val MIN_SEMANTIC_SECTIONS = 3
}
