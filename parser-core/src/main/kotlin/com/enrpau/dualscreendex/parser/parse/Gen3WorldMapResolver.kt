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
        var phaseStarted = System.nanoTime()
        val functionIndex = ThumbFunctionIndex.build(session.rom, references)
        mapTrace("timing functionIndexMs=${elapsedMillis(phaseStarted)}")
        phaseStarted = System.nanoTime()
        val streams = decodeReferencedStreams(session.rom, references)
        mapTrace("timing streamsMs=${elapsedMillis(phaseStarted)}")
        phaseStarted = System.nanoTime()
        val eightBpp = eightBppCandidates(session.rom, references, functionIndex, streams)
        mapTrace("timing eightBppMs=${elapsedMillis(phaseStarted)}")
        phaseStarted = System.nanoTime()
        val tableEightBpp = tableDrivenEightBppCandidates(session.rom, references)
        mapTrace("timing tableEightBppMs=${elapsedMillis(phaseStarted)}")
        phaseStarted = System.nanoTime()
        val text = textCandidates(session.rom, references, functionIndex, streams)
        mapTrace("timing textMs=${elapsedMillis(phaseStarted)}")
        mapTrace(
            "summary streams=${streams.size} eightBpp=${eightBpp.size} " +
                "tableEightBpp=${tableEightBpp.size} text=${text.size} " +
                "referenceSites=${functionIndex.analyzedSiteCount}",
        )
        val eligibleFormats = listOf(eightBpp, tableEightBpp, text).count { it.isNotEmpty() }
        if (eligibleFormats > 1) {
            return WorldMapResolution.Ambiguous(
                "asset-loader",
                "multiple proven world-map loader formats remained eligible",
            )
        }
        val resolution = when {
            eightBpp.isNotEmpty() -> resolve8Bpp(session.rom, encounterBaseIds, references, eightBpp)
            tableEightBpp.isNotEmpty() -> resolveTableEightBpp(
                session.rom,
                encounterBaseIds,
                references,
                functionIndex,
                tableEightBpp,
            )
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

    private fun resolveTableEightBpp(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        candidates: List<TableAssetCandidate>,
    ): WorldMapResolution {
        val winner = candidates.singleOrNull()
            ?: return WorldMapResolution.Ambiguous(
                "asset-loader",
                "${candidates.size} table-driven 8bpp asset clusters remained",
            )
        if (winner.regions.map(TableRegionAsset::slot) != (0 until winner.regions.size).toList()) {
            return WorldMapResolution.Unavailable(
                "map-plane",
                "table-driven 8bpp cluster did not expose contiguous regions",
            )
        }
        val semanticCandidates = resolveTableSemanticLayouts(
            rom,
            references,
            functionIndex,
            winner.regions.size,
        )
        val sectionByBaseArea = Gen3MapLocationResolver.resolveSectionByBaseArea(
            rom,
            encounterBaseIds,
            references,
        )
        if (sectionByBaseArea.isEmpty()) {
            return WorldMapResolution.Unavailable(
                "map-header-join",
                "encounter map headers did not resolve a semantic section join",
            )
        }
        val baseAreasBySection = sectionByBaseArea.entries.groupBy({ it.value }, { it.key })
        mapTrace(
            "table-bindings semanticCandidates=${semanticCandidates.size} sections=${baseAreasBySection.size} " +
                "perRegion=${semanticCandidates.joinToString { candidate ->
                    candidate.joinToString(prefix = "[", postfix = "]") { layout ->
                        textLocations(layout, baseAreasBySection).size.toString()
                    }
                }}",
        )
        val layoutWinner = semanticCandidates.filter { candidate ->
            candidate.any { layout -> textLocations(layout, baseAreasBySection).isNotEmpty() }
        }.let { eligible ->
            val maximumBindings = eligible.maxOfOrNull { candidate ->
                candidate.sumOf { layout -> textLocations(layout, baseAreasBySection).size }
            }
            maximumBindings?.let { count ->
                eligible.filter { candidate ->
                    candidate.sumOf { layout -> textLocations(layout, baseAreasBySection).size } == count
                }.singleOrNull()
            }
        } ?: return WorldMapResolution.Unavailable(
            "map-plane",
            "table-driven semantic section planes did not resolve uniquely",
        )

        val regions = mutableListOf<WorldMapRegion>()
        val assets = linkedMapOf<String, com.enrpau.dualscreendex.parser.catalog.RgbaSprite>()
        winner.regions.zip(layoutWinner).forEach { (regionAsset, layout) ->
            val slot = regionAsset.slot
            val locations = textLocations(layout, baseAreasBySection)
            if (locations.isEmpty()) return@forEach
            val regionKey = "gen3-region-$slot"
            val assetKey = "world/$regionKey"
            val composition = regionAsset.asset.composition
            regions += WorldMapRegion(
                regionKey,
                null,
                composition.raster.width,
                composition.raster.height,
                composition.gridWidth,
                composition.gridHeight,
                assetKey,
                locations,
            )
            assets[assetKey] = composition.raster
        }
        if (regions.isEmpty()) {
            return WorldMapResolution.Unavailable(
                "encounter-binding",
                "table-driven 8bpp regions retained no encounter binding",
            )
        }
        return WorldMapResolution.Resolved(
            WorldMapCatalog(regions, assets).validate(),
            listOf(
                "validated one table-driven 8bpp loader cluster with ${winner.regions.size} regions",
                "published ${regions.size} region(s) with compiled semantic planes and encounter bindings",
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
        mapTrace(
            "text-bindings semanticCandidates=${semanticCandidates.size} sections=${baseAreasBySection.size} " +
                "perRegion=${semanticCandidates.joinToString { candidate ->
                    candidate.joinToString(prefix = "[", postfix = "]") { layout ->
                        textLocations(layout, baseAreasBySection).size.toString()
                    }
                }}",
        )
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
    ): List<WorldMapLocation> = baseAreasBySection.entries.sortedBy { it.key }.mapNotNull { (section, baseAreas) ->
        val cells = layout.primaryCellsBySection[section]
            ?: layout.secondaryCellsBySection[section]
            ?: return@mapNotNull null
        WorldMapLocation(
            key = "section-$section",
            displayName = "Map section $section",
            baseAreaIds = baseAreas.toSet(),
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

    private fun tableDrivenEightBppCandidates(
        rom: RomImage,
        references: GbaReferenceIndex,
    ): List<TableAssetCandidate> = references.targets.keys.mapNotNull { root ->
        val evidence = references.target(root) ?: return@mapNotNull null
        if (!evidence.siteEvidenceAvailable || evidence.instructionSites.size < MIN_TABLE_REFERENCE_SITES) {
            return@mapNotNull null
        }
        tableDrivenEightBppCandidateAt(rom, root)
    }.distinctBy(TableAssetCandidate::identity)

    private fun tableDrivenEightBppCandidateAt(
        rom: RomImage,
        root: Int,
    ): TableAssetCandidate? {
        if (root and 3 != 0) return null
        repeat(MIN_TABLE_REGION_COUNT) { slot ->
            val recordOffset = root.toLong() + slot.toLong() * REGION_MAP_INFO_BYTES
            if (
                recordOffset < 0 ||
                recordOffset + REGION_MAP_INFO_BYTES > rom.size.toLong() ||
                tableRecordShellAt(rom, recordOffset.toInt()) == null
            ) return null
        }
        val regions = buildList {
            repeat(MAX_TABLE_REGION_COUNT) { slot ->
                val recordOffset = root.toLong() + slot.toLong() * REGION_MAP_INFO_BYTES
                if (recordOffset < 0 || recordOffset + REGION_MAP_INFO_BYTES > rom.size.toLong()) {
                    return@buildList
                }
                val region = tableDrivenEightBppRegionAt(rom, recordOffset.toInt(), slot)
                    ?: return@buildList
                add(region)
            }
        }
        if (regions.size < MIN_TABLE_REGION_COUNT) return null
        if (regions.map { it.asset.identity }.distinct().size != regions.size) return null
        mapTrace(
            "table-8bpp root=0x${root.toString(16)} regions=${regions.size} " +
                "maps=${regions.joinToString { "0x${it.asset.mapOffset.toString(16)}" }}",
        )
        return TableAssetCandidate(root, regions)
    }

    private fun tableRecordShellAt(
        rom: RomImage,
        recordOffset: Int,
    ): TableRecordShell? {
        if (recordOffset < 0 || recordOffset.toLong() + REGION_MAP_INFO_BYTES > rom.size.toLong()) return null
        val fields = IntArray(REGION_MAP_INFO_POINTER_COUNT) { index ->
            romPointerAtOrNull(rom, recordOffset + index * 4) ?: return null
        }
        val compressed = setOf(
            fields[DEX_MAP_FIELD],
            fields[DEX_GRAPHICS_FIELD],
            fields[REGION_MAP_FIELD],
            fields[REGION_GRAPHICS_FIELD],
        )
        if (
            fields[DEX_MAP_FIELD] == fields[DEX_GRAPHICS_FIELD] ||
            fields[REGION_MAP_FIELD] == fields[REGION_GRAPHICS_FIELD] ||
            fields[DEX_PALETTE_FIELD] in compressed ||
            fields[REGION_PALETTE_FIELD] in compressed
        ) return null
        val paletteBytes = rom.u16le(recordOffset + REGION_MAP_INFO_PALETTE_SIZE_OFFSET)
        if (
            paletteBytes !in MIN_TABLE_PALETTE_BYTES..MAX_TABLE_PALETTE_BYTES ||
            paletteBytes % BYTES_PER_PALETTE_BANK != 0 ||
            rom.u16le(recordOffset + REGION_MAP_INFO_PADDING_OFFSET) != 0
        ) return null
        return TableRecordShell(fields, paletteBytes)
    }

    private fun tableDrivenEightBppRegionAt(
        rom: RomImage,
        recordOffset: Int,
        slot: Int,
    ): TableRegionAsset? {
        val shell = tableRecordShellAt(rom, recordOffset) ?: return null
        val fields = shell.fields
        val paletteBytes = shell.paletteBytes
        mapTrace(
            "table-record-shape slot=$slot record=0x${recordOffset.toString(16)} " +
                "paletteBytes=$paletteBytes",
        )

        val dexMap = decodeTableStream(rom, fields[DEX_MAP_FIELD]) ?: return null
        val dexGraphics = decodeTableStream(rom, fields[DEX_GRAPHICS_FIELD]) ?: return null
        val regionMap = decodeTableStream(rom, fields[REGION_MAP_FIELD]) ?: return null
        val regionGraphics = decodeTableStream(rom, fields[REGION_GRAPHICS_FIELD]) ?: return null
        mapTrace(
            "table-record-streams slot=$slot record=0x${recordOffset.toString(16)} " +
                "dexMap=${dexMap.decoded.size} dexGfx=${dexGraphics.decoded.size} " +
                "map=${regionMap.decoded.size} gfx=${regionGraphics.decoded.size}",
        )
        val dexPalette = readPalette(rom, fields[DEX_PALETTE_FIELD], paletteBytes / 2) ?: return null
        val regionPalette = readPalette(rom, fields[REGION_PALETTE_FIELD], paletteBytes / 2) ?: return null
        if (
            !validAffine8BppAsset(dexGraphics.decoded, dexMap.decoded, dexPalette.colors.size) ||
            regionMap.decoded.size != AFFINE_MAP_BYTES ||
            regionGraphics.decoded.isEmpty() ||
            regionGraphics.decoded.size % AFFINE_TILE_BYTES != 0 ||
            !paletteCovers8BppGraphics(regionGraphics.decoded, regionPalette.colors.size)
        ) return null

        val regionComposition = GbaWorldMapCompositor.compose(
            regionGraphics.decoded,
            regionMap.decoded,
            regionPalette.colors,
        )
        mapTrace(
            "table-record-compose slot=$slot record=0x${recordOffset.toString(16)} " +
                "dex=validated region=${compositionTrace(regionComposition)}",
        )
        if (
            regionComposition !is GbaWorldMapComposition.Resolved ||
            regionComposition.format != GbaWorldMapFormat.AFFINE_8BPP_64X64
        ) return null

        mapTrace(
            "table-8bpp-region slot=$slot record=0x${recordOffset.toString(16)} " +
                "dexMap=0x${dexMap.offset.toString(16)} dexGfx=0x${dexGraphics.offset.toString(16)} " +
                "map=0x${regionMap.offset.toString(16)} gfx=0x${regionGraphics.offset.toString(16)} " +
                "palette=0x${regionPalette.offset.toString(16)}",
        )
        return TableRegionAsset(
            slot,
            AssetCandidate(
                regionGraphics.offset,
                regionMap.offset,
                regionPalette.offset,
                regionComposition,
            ),
        )
    }

    private fun paletteCovers8BppGraphics(
        graphics: ByteArray,
        paletteColors: Int,
    ): Boolean {
        if (graphics.isEmpty() || paletteColors <= 0) return false
        val nonZero = graphics.map { it.toInt() and 0xFF }.filter { it != 0 }
        if (nonZero.toSet().size < 2) return false
        val base = nonZero.min() and -PALETTE_BANK_COLORS
        return nonZero.max() < base + paletteColors
    }

    private fun validAffine8BppAsset(
        graphics: ByteArray,
        tilemap: ByteArray,
        paletteColors: Int,
    ): Boolean {
        if (
            graphics.isEmpty() ||
            graphics.size % AFFINE_TILE_BYTES != 0 ||
            tilemap.size !in MIN_TABLE_AFFINE_MAP_BYTES..AFFINE_MAP_BYTES ||
            tilemap.size and (tilemap.size - 1) != 0 ||
            paletteColors <= 0
        ) return false
        val tileCount = graphics.size / AFFINE_TILE_BYTES
        val mapValues = tilemap.map { it.toInt() and 0xFF }
        if (mapValues.toSet().size < 2 || mapValues.max() >= tileCount) return false
        return paletteCovers8BppGraphics(graphics, paletteColors)
    }

    private fun decodeTableStream(rom: RomImage, offset: Int): CompressedStream? {
        val declared = GbaRomCompression.decodedSizeAtOrNull(rom, offset) ?: return null
        if (declared !in 1..MAX_DECOMPRESSED_ASSET_BYTES) return null
        return runCatching { CompressedStream(offset, GbaRomCompression.decodeAt(rom, offset)) }.getOrNull()
    }

    private fun romPointerAtOrNull(rom: RomImage, offset: Int): Int? {
        if (offset < 0 || offset.toLong() + 4 > rom.size.toLong()) return null
        val target = rom.u32le(offset) - GBA_ROM_BASE
        return target.takeIf { it in 0 until rom.size.toLong() }?.toInt()
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
                val mapsBySlot = referencedMaps.flatMap { map ->
                    compressedDestinationOffsets(rom, references, functionIndex, map.offset, functionStart)
                        .map { destination -> destination to map }
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
                    val graphicsBg = textGraphicsBgRole(
                        rom,
                        references,
                        functionIndex,
                        functionStart,
                        graphicsStream.offset,
                    )
                    if (graphicsBg != null && graphicsBg != TEXT_WORLD_BG) return@mapNotNull null
                    val palettes = paletteCandidatesInFunction(
                        rom,
                        references,
                        functionIndex,
                        functionStart,
                        excludedOffsets = intArrayOf(graphicsStream.offset) + referencedMaps.map(CompressedStream::offset),
                    )
                    palettes.mapNotNull { palette ->
                        val paletteDestination = textPaletteDestination(
                            rom,
                            references,
                            functionIndex,
                            functionStart,
                            palette.offset,
                        )
                        if (paletteDestination != null && paletteDestination != TEXT_WORLD_PALETTE_START) {
                            return@mapNotNull null
                        }
                        mapTrace(
                            "text-role function=0x${functionStart.toString(16)} " +
                                "gfx=0x${graphicsStream.offset.toString(16)} bg=$graphicsBg " +
                                "palette=0x${palette.offset.toString(16)} destination=$paletteDestination",
                        )
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
                        TextAssetBundle(
                            graphics = graphicsStream,
                            palette = palette,
                            branchSlot = graphicsSlot ?: paletteSlot,
                            worldRoleProven = graphicsBg == TEXT_WORLD_BG &&
                                paletteDestination == TEXT_WORLD_PALETTE_START,
                        )
                    }
                }.flatten()

                val loadedRegionAssets = slotByDestination.entries.mapNotNull { (destination, slot) ->
                    if (slot == TEXT_BACKGROUND_SLOT) return@mapNotNull null
                    val map = mapsBySlot[destination]?.distinctBy(CompressedStream::offset)?.singleOrNull()
                        ?: return@mapNotNull null
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
                                bundle.worldRoleProven &&
                                    bundle.graphics.offset == candidate.asset.graphicsOffset &&
                                    bundle.palette.offset == candidate.asset.paletteOffset
                            }
                        } ?: eligible.singleOrNull { candidate ->
                            bundles.any { bundle ->
                                bundle.branchSlot == slot &&
                                    bundle.graphics.offset == candidate.asset.graphicsOffset &&
                                    bundle.palette.offset == candidate.asset.paletteOffset
                            }
                        }
                    }
                }.sortedBy(TextRegionAsset::slot)

                val aliasedSingleRegion = loadedRegionAssets.isNotEmpty() &&
                    loadedRegionAssets.first().slot == 0 &&
                    loadedRegionAssets.map { it.asset.identity }.distinct().size == 1 &&
                    TEXT_BACKGROUND_SLOT in slotByDestination.values &&
                    bundles.any { bundle ->
                        bundle.worldRoleProven &&
                            bundle.graphics.offset == loadedRegionAssets.first().asset.graphicsOffset &&
                            bundle.palette.offset == loadedRegionAssets.first().asset.paletteOffset
                    }
                val regionAssets = if (aliasedSingleRegion) {
                    listOf(loadedRegionAssets.first())
                } else {
                    loadedRegionAssets
                }
                if (aliasedSingleRegion) {
                    mapTrace(
                        "text-loader function=0x${functionStart.toString(16)} " +
                            "collapsed ${loadedRegionAssets.size} identical region-plane loads to one region",
                    )
                }

                if (regionAssets.isNotEmpty() &&
                    regionAssets.map(TextRegionAsset::slot) == (0 until regionAssets.size).toList() &&
                    (regionAssets.size > 1 || destinations.size == TEXT_LOADER_DESTINATION_COUNT || aliasedSingleRegion)
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

    /**
     * Proves the source-family world graphics role from the actual loader call: the referenced
     * compressed stream must remain in r1 while the background argument in r0 reaches the next
     * direct call. A known non-zero background is a separate background layer, not world tiles.
     */
    private fun textGraphicsBgRole(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        functionStart: Int,
        graphicsOffset: Int,
    ): Int? = completeSites(references, graphicsOffset).orEmpty()
        .filter { functionIndex.functionStart(it) == functionStart }
        .mapNotNull { site -> graphicsBgArgumentAtNextCall(rom, functionStart, site) }
        .distinct()
        .singleOrNull()

    private fun graphicsBgArgumentAtNextCall(
        rom: RomImage,
        functionStart: Int,
        sourceSite: Int,
    ): Int? {
        if (sourceSite < functionStart || sourceSite + 2 > rom.size) return null
        val sourceInstruction = rom.u16le(sourceSite)
        if (
            sourceInstruction and THUMB_LITERAL_LOAD_MASK != THUMB_LITERAL_LOAD_OPCODE ||
            sourceInstruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK != 1
        ) {
            return null
        }
        var blockStart = sourceSite
        while (blockStart - 2 >= functionStart && !isThumbControlFlow(rom, blockStart - 2)) {
            blockStart -= 2
        }
        var background: Int? = null
        var sourceLive = false
        var cursor = blockStart
        while (cursor + 2 <= rom.size) {
            val instruction = rom.u16le(cursor)
            if (isThumbBl(rom, cursor)) {
                if (cursor <= sourceSite || !sourceLive) return null
                return background
            }
            if (cursor != blockStart && isThumbControlFlow(rom, cursor)) return null
            val destination = thumbDestinationRegister(instruction)
            if (cursor == sourceSite) sourceLive = true
            if (cursor > sourceSite && destination == 1) sourceLive = false
            if (destination == 0) {
                background = if (instruction and THUMB_MOVE_IMMEDIATE_MASK == THUMB_MOVE_IMMEDIATE_OPCODE) {
                    instruction and 0xff
                } else {
                    null
                }
            }
            cursor += 2
        }
        return null
    }

    /** The FRLG text-map palette is loaded at hardware palette entry zero. */
    private fun textPaletteDestination(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        functionStart: Int,
        paletteOffset: Int,
    ): Int? = completeSites(references, paletteOffset).orEmpty()
        .filter { functionIndex.functionStart(it) == functionStart }
        .mapNotNull { site ->
            if (!isPaletteSourceLoad(rom, site)) null else immediateArgumentsAtNextCall(rom, site)?.first
        }
        .distinct()
        .singleOrNull()

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

    private fun compressedDestinationOffsets(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        target: Int,
        functionStart: Int,
    ): List<Int> = completeSites(references, target).orEmpty().mapNotNull { site ->
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
        }.distinct().sorted()

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

    private fun resolveTableSemanticLayouts(
        rom: RomImage,
        references: GbaReferenceIndex,
        functionIndex: ThumbFunctionIndex,
        regionCount: Int,
    ): List<List<SemanticLayout>> {
        val layoutsByTarget = references.targets.keys.mapNotNull { target ->
            decodeTableSemanticLayout(rom, target)?.let { target to it }
        }
        return layoutsByTarget.flatMap { (target, _) ->
            loaderFunctions(references, functionIndex, target)
        }.distinct().mapNotNull { functionStart ->
            val members = layoutsByTarget.filter { (target, _) ->
                functionStart in loaderFunctions(references, functionIndex, target)
            }.sortedBy { it.first }
            if (members.size != regionCount) return@mapNotNull null
            if (members.zipWithNext().any { (left, right) ->
                    right.first - left.first != TABLE_SEMANTIC_LAYOUT_BYTES
                }
            ) return@mapNotNull null
            val sectionOwners = members.flatMap { (_, layout) -> layout.primaryCellsBySection.keys }
                .groupingBy { it }
                .eachCount()
            if (sectionOwners.values.any { it != 1 }) return@mapNotNull null
            mapTrace(
                "table-semantic function=0x${functionStart.toString(16)} targets=" +
                    members.sortedByDescending { it.first }
                        .joinToString { "0x${it.first.toString(16)}" },
            )
            members.sortedByDescending { it.first }.map { it.second }
        }.distinct()
    }

    private fun decodeTableSemanticLayout(
        rom: RomImage,
        offset: Int,
    ): SemanticLayout? {
        if (offset < 0 || offset.toLong() + TABLE_SEMANTIC_LAYOUT_BYTES > rom.size.toLong()) return null
        val bytes = rom.slice(offset, TABLE_SEMANTIC_LAYOUT_BYTES)
        val frequencies = bytes.asIterable().groupingBy { it.toInt() and 0xFF }.eachCount()
        val empty = frequencies.maxByOrNull { it.value }
            ?.takeIf { it.value >= TABLE_SEMANTIC_GRID_CELLS / 2 }
            ?.key ?: return null
        val active = bytes.map { it.toInt() and 0xFF }.filter { it != empty }
        if (
            active.size < MIN_SEMANTIC_CELLS ||
            active.any { it >= empty } ||
            active.toSet().size < MIN_SEMANTIC_SECTIONS
        ) return null
        val cellsBySection = linkedMapOf<Int, MutableList<WorldMapCell>>()
        bytes.forEachIndexed { index, value ->
            val section = value.toInt() and 0xFF
            if (section != empty) {
                cellsBySection.getOrPut(section, ::mutableListOf) += WorldMapCell(
                    index % TABLE_SEMANTIC_GRID_WIDTH,
                    index / TABLE_SEMANTIC_GRID_WIDTH,
                    1,
                    1,
                )
            }
        }
        return SemanticLayout(cellsBySection)
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
        val dungeonLayer = bytes.copyOfRange(SEMANTIC_GRID_CELLS, SEMANTIC_LAYOUT_BYTES)
        val primaryCellsBySection = linkedMapOf<Int, MutableList<WorldMapCell>>()
        mapLayer.forEachIndexed { index, value ->
            val section = value.toInt() and 0xff
            if (section != empty) {
                primaryCellsBySection.getOrPut(section, ::mutableListOf) +=
                    WorldMapCell(index % TEXT_GRID_WIDTH, index / TEXT_GRID_WIDTH, 1, 1)
            }
        }
        if (primaryCellsBySection.size < MIN_SEMANTIC_SECTIONS) return null
        val secondaryCellsBySection = linkedMapOf<Int, MutableList<WorldMapCell>>()
        dungeonLayer.forEachIndexed { index, value ->
            val section = value.toInt() and 0xff
            if (section != empty) {
                secondaryCellsBySection.getOrPut(section, ::mutableListOf) +=
                    WorldMapCell(index % TEXT_GRID_WIDTH, index / TEXT_GRID_WIDTH, 1, 1)
            }
        }
        return SemanticLayout(primaryCellsBySection, secondaryCellsBySection)
    }

    private fun decodeReferencedStreams(rom: RomImage, references: GbaReferenceIndex): List<CompressedStream> =
        references.targets.keys.mapNotNull { offset ->
            if (rom.u8(offset) != GBA_LZ77_HEADER) return@mapNotNull null
            val declared = GbaRomCompression.decodedSizeAtOrNull(rom, offset) ?: return@mapNotNull null
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

    private fun elapsedMillis(started: Long): Long =
        (System.nanoTime() - started) /
            NANOS_PER_MILLISECOND

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
    private data class SemanticLayout(
        val primaryCellsBySection: Map<Int, List<WorldMapCell>>,
        val secondaryCellsBySection: Map<Int, List<WorldMapCell>> = emptyMap(),
    )
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
        val worldRoleProven: Boolean,
    )
    private data class TextRegionAsset(val slot: Int, val asset: AssetCandidate)
    private data class TableRecordShell(val fields: IntArray, val paletteBytes: Int)
    private data class TableRegionAsset(val slot: Int, val asset: AssetCandidate) {
        val identity: List<Int> get() = listOf(
            slot,
            asset.graphicsOffset,
            asset.mapOffset,
            asset.paletteOffset,
        )
    }
    private data class TableAssetCandidate(
        val root: Int,
        val regions: List<TableRegionAsset>,
    ) {
        val identity: List<Int> get() = listOf(root) + regions.flatMap(TableRegionAsset::identity)
    }
    private data class TextAssetCandidate(
        val functionStart: Int,
        val regions: List<TextRegionAsset>,
    ) {
        val identity: List<Int> get() = listOf(functionStart) + regions.flatMap { region ->
            listOf(region.slot, region.asset.graphicsOffset, region.asset.mapOffset, region.asset.paletteOffset)
        }
    }

    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val MAX_DECOMPRESSED_ASSET_BYTES = 256 * 1_024
    private const val GBA_LZ77_HEADER = 0x10
    private const val GBA_ROM_BASE = 0x0800_0000L
    private const val REGION_MAP_INFO_BYTES = 28L
    private const val REGION_MAP_INFO_POINTER_COUNT = 6
    private const val REGION_MAP_INFO_PALETTE_SIZE_OFFSET = 24
    private const val REGION_MAP_INFO_PADDING_OFFSET = 26
    private const val DEX_MAP_FIELD = 0
    private const val DEX_GRAPHICS_FIELD = 1
    private const val DEX_PALETTE_FIELD = 2
    private const val REGION_MAP_FIELD = 3
    private const val REGION_GRAPHICS_FIELD = 4
    private const val REGION_PALETTE_FIELD = 5
    private const val MIN_TABLE_REGION_COUNT = 2
    private const val MAX_TABLE_REGION_COUNT = 8
    private const val MIN_TABLE_REFERENCE_SITES = 2
    private const val MIN_TABLE_PALETTE_BYTES = 2 * 16 * 2
    private const val MAX_TABLE_PALETTE_BYTES = 16 * 16 * 2
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
    private const val MIN_TABLE_AFFINE_MAP_BYTES = 1024
    private const val TILED_8BPP_MAP_BYTES = 32 * 20 * 2
    private const val AFFINE_TILE_BYTES = 64
    private const val TEXT_MAP_BYTES = 1200
    private const val TEXT_TILE_BYTES = 32
    private const val MIN_TEXT_PALETTE_BYTES = 16 * 2
    private const val MAX_TEXT_PALETTE_BYTES = 16 * 16 * 2
    private const val BYTES_PER_PALETTE_BANK = 16 * 2
    private const val PALETTE_BANK_COLORS = 16
    private const val TEXT_WORLD_BG = 0
    private const val TEXT_WORLD_PALETTE_START = 0
    private const val TEXT_BACKGROUND_SLOT = 4
    private const val TEXT_LOADER_DESTINATION_COUNT = 5
    private const val TEXT_GRID_WIDTH = 22
    private const val TEXT_GRID_HEIGHT = 15
    private const val SEMANTIC_GRID_CELLS = TEXT_GRID_WIDTH * TEXT_GRID_HEIGHT
    private const val SEMANTIC_LAYOUT_BYTES = SEMANTIC_GRID_CELLS * 2
    private const val TABLE_SEMANTIC_GRID_WIDTH = 28
    private const val TABLE_SEMANTIC_GRID_HEIGHT = 15
    private const val TABLE_SEMANTIC_GRID_CELLS =
        TABLE_SEMANTIC_GRID_WIDTH * TABLE_SEMANTIC_GRID_HEIGHT
    private const val TABLE_SEMANTIC_LAYOUT_BYTES = TABLE_SEMANTIC_GRID_CELLS
    private const val MIN_SEMANTIC_CELLS = 12
    private const val MIN_SEMANTIC_SECTIONS = 3
}
