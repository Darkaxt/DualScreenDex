package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.PngMapAsset
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.sprite.IndexedSprite
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.enrpau.dualscreendex.parser.sprite.TileRenderer

internal object Gen1LocalMapResolver {
    fun resolve(
        session: RomAnalysisSession,
        encounterBaseIds: Set<Int>,
        family: EngineFamily,
    ): LocalMapResolution {
        val format = Gen1LocalMapFormat.forFamily(family)
            ?: return LocalMapResolution.Unavailable(
                "map-abi",
                "no canonical Gen I local-map ABI exists for $family",
            )
        val requiredMaps = encounterBaseIds.filterTo(sortedSetOf()) { it in MAP_ID_RANGE }
        if (requiredMaps.isEmpty()) {
            return LocalMapResolution.Unavailable("encounter-binding", "no encounter-bound Gen I map IDs")
        }

        val authorities = findAuthorities(session.rom, requiredMaps, format)
        if (authorities.isEmpty()) {
            return LocalMapResolution.Unavailable(
                "map-authority",
                "no complete Gen I map-header and tileset consumer chain resolved every encounter map",
            )
        }
        if (authorities.size != 1) {
            return LocalMapResolution.Unavailable(
                "map-authority",
                "${authorities.size} complete Gen I local-map authority chains remained",
            )
        }

        val authority = authorities.single()
        val mapNames = runCatching {
            Gen1WorldMapResolver.resolveNames(
                session,
                authority.descriptors.mapTo(linkedSetOf(), MapDescriptor::mapId),
            )
        }.getOrDefault(emptyMap())
        val totalPixels = authority.descriptors.sumOf(MapDescriptor::pixelCount)
        if (totalPixels > MAX_TOTAL_PIXELS) {
            return LocalMapResolution.BudgetExceeded(
                "raster-pixels",
                "resolved $totalPixels local-map pixels (limit $MAX_TOTAL_PIXELS)",
            )
        }

        val maps = mutableListOf<LocalMap>()
        val assets = linkedMapOf<String, PngMapAsset>()
        val skippedReasons = mutableListOf<String>()
        var encodedBytes = 0L
        authority.descriptors.forEach { descriptor ->
            runCatching {
                val raster = render(descriptor, authority.tilesets.getValue(descriptor.tilesetId))
                val png = PngEncoder.encode(raster)
                encodedBytes += png.size
                if (encodedBytes > MAX_ENCODED_BYTES) {
                    return LocalMapResolution.BudgetExceeded(
                        "encoded-assets",
                        "local-map PNG assets exceed $MAX_ENCODED_BYTES bytes",
                    )
                }
                maps += descriptor.toLocalMap(mapNames[descriptor.mapId])
                assets[descriptor.assetKey] = PngMapAsset(png)
            }.onFailure { failure ->
                skippedReasons += "map 0x${descriptor.mapId.toString(16).padStart(2, '0')} render: ${failure.message}"
            }
        }
        if (maps.isEmpty()) {
            return LocalMapResolution.Unavailable("render", "no Gen I local map could be rendered")
        }

        return LocalMapResolution.Resolved(
            catalog = LocalMapCatalog(maps, assets).validate(),
            reasons = listOf(
                "resolved paired compiled Gen I map-bank, map-pointer, and tileset consumers",
                "rendered ${maps.size} bounded ${format.label} maps from 32x32 ROM blocks and 2bpp tiles",
                "resolved ${mapNames.size} map names through the compiled Town Map lookup",
                "bound all ${requiredMaps.size} encounter-authoritative map IDs",
            ) + skippedReasons,
            skippedMaps = skippedReasons.size,
        )
    }

    private fun findAuthorities(
        rom: RomImage,
        requiredMaps: Set<Int>,
        format: Gen1LocalMapFormat,
    ): List<MapAuthority> {
        val mapBanks = findMapBankAuthorities(rom)
        val mapPointers = findMapPointerAuthorities(rom)
        val tilesets = findTilesetAuthorities(rom)
        return buildList {
            mapBanks.forEach { bankAuthority ->
                mapPointers.forEach { pointerAuthority ->
                    tilesets.forEach { tilesetAuthority ->
                        buildAuthority(
                            rom,
                            bankAuthority,
                            pointerAuthority,
                            tilesetAuthority,
                            requiredMaps,
                            format,
                        )?.let(::add)
                    }
                }
            }
        }.distinctBy { authority ->
            Triple(
                authority.bankAuthority.root,
                authority.pointerAuthority.root,
                authority.tilesetAuthority.root,
            )
        }
    }

    private fun findMapBankAuthorities(rom: RomImage): List<MapBankAuthority> = buildList {
        val end = minOf(BANK_BYTES, rom.size)
        var offset = 0
        while (offset + MAP_BANK_CONSUMER_BYTES <= end) {
            parseMapBankAuthorityAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { it.bank to it.root }

    private fun parseMapBankAuthorityAt(rom: RomImage, offset: Int): MapBankAuthority? = runCatching {
        if (
            rom.u8(offset) != PUSH_HL || rom.u8(offset + 1) != PUSH_BC ||
            rom.u8(offset + 2) != LOAD_C_A || rom.u8(offset + 3) != LOAD_B_IMMEDIATE ||
            rom.u8(offset + 4) != 0 || rom.u8(offset + 5) != LOAD_A_IMMEDIATE ||
            rom.u8(offset + 7) != CALL || rom.u8(offset + 10) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 13) != ADD_HL_BC || rom.u8(offset + 14) != LOAD_A_HL ||
            rom.u8(offset + 15) != LOAD_HIGH_A
        ) {
            return@runCatching null
        }
        val bank = rom.u8(offset + 6)
        val root = rom.gbBankAddress(bank, rom.u16le(offset + 11)) ?: return@runCatching null
        require(root.toLong() + MAP_ID_COUNT <= bankEnd(rom, bank).toLong())
        MapBankAuthority(bank, root)
    }.getOrNull()

    private fun findMapPointerAuthorities(rom: RomImage): List<MapPointerAuthority> = buildList {
        val end = minOf(BANK_BYTES, rom.size)
        var offset = 0
        while (offset + DIRECT_POINTER_CONSUMER_BYTES <= end) {
            parseDirectPointerAuthorityAt(rom, offset)?.let(::add)
            parseBankedPointerAuthorityAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { it.bank to it.root }

    private fun parseDirectPointerAuthorityAt(rom: RomImage, offset: Int): MapPointerAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_HL_IMMEDIATE || rom.u8(offset + 3) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 6) != PREFIX || rom.u8(offset + 7) != SHIFT_LEFT_A ||
            rom.u8(offset + 8) != JR_NC || rom.u8(offset + 10) != INC_H ||
            rom.u8(offset + 11) != ADD_L || rom.u8(offset + 12) != LOAD_L_A ||
            rom.u8(offset + 13) != JR_NC || rom.u8(offset + 15) != INC_H ||
            rom.u8(offset + 16) != LOAD_A_HL_INCREMENT || rom.u8(offset + 17) != LOAD_H_HL ||
            rom.u8(offset + 18) != LOAD_L_A
        ) {
            return@runCatching null
        }
        val address = rom.u16le(offset + 1)
        val root = rom.gbBankAddress(0, address) ?: return@runCatching null
        require(root + MAP_ID_COUNT * 2 <= minOf(BANK_BYTES, rom.size))
        MapPointerAuthority(0, root)
    }.getOrNull()

    private fun parseBankedPointerAuthorityAt(rom: RomImage, offset: Int): MapPointerAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_A_IMMEDIATE || rom.u8(offset + 2) != CALL ||
            rom.u8(offset + 5) != PUSH_DE || rom.u8(offset + 6) != LOAD_A_ABSOLUTE ||
            rom.u8(offset + 9) != LOAD_E_A || rom.u8(offset + 10) != LOAD_D_IMMEDIATE ||
            rom.u8(offset + 11) != 0 || rom.u8(offset + 12) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 15) != ADD_HL_DE || rom.u8(offset + 16) != ADD_HL_DE ||
            rom.u8(offset + 17) != LOAD_A_HL_INCREMENT || rom.u8(offset + 18) != LOAD_H_HL ||
            rom.u8(offset + 19) != LOAD_L_A
        ) {
            return@runCatching null
        }
        val bank = rom.u8(offset + 1)
        val root = rom.gbBankAddress(bank, rom.u16le(offset + 13)) ?: return@runCatching null
        require(root.toLong() + MAP_ID_COUNT * 2L <= bankEnd(rom, bank).toLong())
        MapPointerAuthority(bank, root)
    }.getOrNull()

    private fun findTilesetAuthorities(rom: RomImage): List<TilesetAuthority> = buildList {
        var offset = 0
        while (offset + TILESET_CONSUMER_BYTES <= rom.size) {
            parseRedBlueTilesetAuthorityAt(rom, offset)?.let(::add)
            parseYellowTilesetAuthorityAt(rom, offset)?.let(::add)
            offset++
        }
    }.distinctBy { it.bank to it.root }

    private fun parseRedBlueTilesetAuthorityAt(rom: RomImage, offset: Int): TilesetAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_D_IMMEDIATE || rom.u8(offset + 1) != 0 ||
            rom.u8(offset + 2) != LOAD_A_ABSOLUTE || rom.u8(offset + 5) != ADD_A ||
            rom.u8(offset + 6) != ADD_A || rom.u8(offset + 7) != LOAD_B_A ||
            rom.u8(offset + 8) != ADD_A || rom.u8(offset + 9) != ADD_B ||
            rom.u8(offset + 10) != JR_NC || rom.u8(offset + 12) != INC_D ||
            rom.u8(offset + 13) != LOAD_E_A || rom.u8(offset + 14) != LOAD_HL_IMMEDIATE ||
            rom.u8(offset + 17) != ADD_HL_DE || rom.u8(offset + 18) != LOAD_DE_IMMEDIATE ||
            rom.u8(offset + 21) != LOAD_C_IMMEDIATE || rom.u8(offset + 22) != TILESET_COPIED_BYTES
        ) {
            return@runCatching null
        }
        tilesetAuthority(rom, offset, rom.u16le(offset + 15))
    }.getOrNull()

    private fun parseYellowTilesetAuthorityAt(rom: RomImage, offset: Int): TilesetAuthority? = runCatching {
        if (
            rom.u8(offset) != LOAD_D_IMMEDIATE || rom.u8(offset + 1) != 0 ||
            rom.u8(offset + 2) != LOAD_A_ABSOLUTE || rom.u8(offset + 5) != ADD_A ||
            rom.u8(offset + 6) != ADD_A || rom.u8(offset + 7) != LOAD_E_A ||
            rom.u8(offset + 8) != LOAD_HL_IMMEDIATE || rom.u8(offset + 11) != ADD_HL_DE ||
            rom.u8(offset + 12) != ADD_HL_DE || rom.u8(offset + 13) != ADD_HL_DE ||
            rom.u8(offset + 14) != LOAD_DE_IMMEDIATE || rom.u8(offset + 17) != LOAD_BC_IMMEDIATE ||
            rom.u16le(offset + 18) != TILESET_COPIED_BYTES
        ) {
            return@runCatching null
        }
        tilesetAuthority(rom, offset, rom.u16le(offset + 9))
    }.getOrNull()

    private fun tilesetAuthority(rom: RomImage, consumerOffset: Int, address: Int): TilesetAuthority? {
        val bank = consumerOffset / BANK_BYTES
        val root = rom.gbBankAddress(bank, address) ?: return null
        return TilesetAuthority(bank, root)
    }

    private fun buildAuthority(
        rom: RomImage,
        bankAuthority: MapBankAuthority,
        pointerAuthority: MapPointerAuthority,
        tilesetAuthority: TilesetAuthority,
        requiredMaps: Set<Int>,
        format: Gen1LocalMapFormat,
    ): MapAuthority? = runCatching {
        require(
            tilesetAuthority.root.toLong() + format.tilesetCount.toLong() * TILESET_HEADER_BYTES <=
                bankEnd(rom, tilesetAuthority.bank).toLong(),
        )
        val basic = MAP_ID_RANGE.mapNotNull { mapId ->
            readDescriptor(rom, mapId, bankAuthority, pointerAuthority, format)
        }
        val descriptors = basic.filter { descriptor ->
            validatesRenderReferences(rom, descriptor, tilesetAuthority, format)
        }
        val resolvedIds = descriptors.mapTo(hashSetOf(), MapDescriptor::mapId)
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println(
                "local-map-trace gen1 bank=0x${bankAuthority.root.toString(16)} " +
                    "pointers=0x${pointerAuthority.root.toString(16)} " +
                    "tilesets=0x${tilesetAuthority.root.toString(16)} " +
                    "basic=${basic.size} rendered=${descriptors.size} " +
                    "missing=${requiredMaps - resolvedIds}",
            )
        }
        require(resolvedIds.containsAll(requiredMaps))
        val tilesets = loadTilesets(rom, descriptors, tilesetAuthority, format)
        MapAuthority(
            bankAuthority,
            pointerAuthority,
            tilesetAuthority,
            descriptors.sortedBy(MapDescriptor::mapId),
            tilesets,
        )
    }.getOrNull()

    private fun readDescriptor(
        rom: RomImage,
        mapId: Int,
        bankAuthority: MapBankAuthority,
        pointerAuthority: MapPointerAuthority,
        format: Gen1LocalMapFormat,
    ): MapDescriptor? = runCatching {
        val bank = rom.u8(bankAuthority.root + mapId)
        val headerAddress = rom.u16le(pointerAuthority.root + mapId * 2)
        val header = rom.gbBankAddress(bank, headerAddress) ?: return@runCatching null
        require(header + MAP_FIXED_HEADER_BYTES <= bankEnd(rom, bank))
        val tilesetId = rom.u8(header)
        val blockHeight = rom.u8(header + 1)
        val blockWidth = rom.u8(header + 2)
        require(tilesetId in 0 until format.tilesetCount)
        require(blockWidth in 1..MAX_BLOCK_DIMENSION && blockHeight in 1..MAX_BLOCK_DIMENSION)
        val blockCount = blockWidth * blockHeight
        val mapBlocks = rom.gbBankAddress(bank, rom.u16le(header + 3)) ?: return@runCatching null
        requireNotNull(rom.gbBankAddress(bank, rom.u16le(header + 5)))
        requireNotNull(rom.gbBankAddress(bank, rom.u16le(header + 7)))
        require(rom.u8(header + 9) and CONNECTION_MASK.inv() == 0)
        require(mapBlocks.toLong() + blockCount <= bankEnd(rom, bank).toLong())
        val pixelCount = blockCount.toLong() * BLOCK_PIXELS * BLOCK_PIXELS
        require(pixelCount <= MAX_MAP_PIXELS)
        MapDescriptor(
            mapId = mapId,
            blockWidth = blockWidth,
            blockHeight = blockHeight,
            tilesetId = tilesetId,
            blockIds = rom.slice(mapBlocks, blockCount),
        )
    }.getOrNull()

    private fun validatesRenderReferences(
        rom: RomImage,
        descriptor: MapDescriptor,
        authority: TilesetAuthority,
        format: Gen1LocalMapFormat,
    ): Boolean = runCatching {
        val header = readTilesetHeader(rom, authority, descriptor.tilesetId, format)
        descriptor.usedBlockIds.forEach { blockId ->
            val block = header.blockset + blockId * TILES_PER_BLOCK
            require(block + TILES_PER_BLOCK <= bankEnd(rom, header.bank))
            repeat(TILES_PER_BLOCK) { index ->
                val tileId = rom.u8(block + index)
                require(tileId < TILES_PER_TILESET)
                require(header.graphics + (tileId + 1) * TILE_BYTES <= bankEnd(rom, header.bank))
            }
        }
        true
    }.getOrDefault(false)

    private fun loadTilesets(
        rom: RomImage,
        descriptors: List<MapDescriptor>,
        authority: TilesetAuthority,
        format: Gen1LocalMapFormat,
    ): Map<Int, TilesetData> = descriptors.groupBy(MapDescriptor::tilesetId).mapValues { (tilesetId, maps) ->
        val header = readTilesetHeader(rom, authority, tilesetId, format)
        val usedBlocks = maps.flatMapTo(sortedSetOf()) { it.usedBlockIds }
        val blockBytes = (requireNotNull(usedBlocks.maxOrNull()) + 1) * TILES_PER_BLOCK
        require(header.blockset + blockBytes <= bankEnd(rom, header.bank))
        val blocks = rom.slice(header.blockset, blockBytes)
        val usedTiles = usedBlocks.flatMapTo(sortedSetOf()) { blockId ->
            List(TILES_PER_BLOCK) { index -> blocks[blockId * TILES_PER_BLOCK + index].toInt() and 0xff }
        }
        val tileBytes = (requireNotNull(usedTiles.maxOrNull()) + 1) * TILE_BYTES
        require(tileBytes <= TILES_PER_TILESET * TILE_BYTES)
        require(header.graphics + tileBytes <= bankEnd(rom, header.bank))
        TilesetData(
            blocks = blocks,
            tiles = TileRenderer.gameBoy2Bpp(rom.slice(header.graphics, tileBytes), tileBytes / TILE_BYTES, 1),
        )
    }

    private fun readTilesetHeader(
        rom: RomImage,
        authority: TilesetAuthority,
        tilesetId: Int,
        format: Gen1LocalMapFormat,
    ): TilesetHeader {
        require(tilesetId in 0 until format.tilesetCount)
        val offset = authority.root + tilesetId * TILESET_HEADER_BYTES
        require(offset + TILESET_HEADER_BYTES <= bankEnd(rom, authority.bank))
        val bank = rom.u8(offset)
        val blockset = requireNotNull(rom.gbBankAddress(bank, rom.u16le(offset + 1)))
        val graphics = requireNotNull(rom.gbBankAddress(bank, rom.u16le(offset + 3)))
        return TilesetHeader(bank, blockset, graphics)
    }

    private fun render(map: MapDescriptor, tileset: TilesetData): RgbaSprite {
        val pixelWidth = map.gridWidth * METATILE_PIXELS
        val pixels = IntArray(map.pixelCount.toInt())
        repeat(map.blockHeight) { blockY ->
            repeat(map.blockWidth) { blockX ->
                val blockId = map.blockIds[blockY * map.blockWidth + blockX].toInt() and 0xff
                repeat(TILES_PER_BLOCK) { tileIndex ->
                    val tileId = tileset.blocks[blockId * TILES_PER_BLOCK + tileIndex].toInt() and 0xff
                    drawTile(
                        tileset.tiles,
                        tileId,
                        pixels,
                        pixelWidth,
                        blockX * BLOCK_PIXELS + tileIndex % BLOCK_TILE_EDGE * TILE_PIXELS,
                        blockY * BLOCK_PIXELS + tileIndex / BLOCK_TILE_EDGE * TILE_PIXELS,
                    )
                }
            }
        }
        return RgbaSprite(pixelWidth, map.gridHeight * METATILE_PIXELS, pixels)
    }

    private fun drawTile(
        tiles: IndexedSprite,
        tileId: Int,
        pixels: IntArray,
        pixelWidth: Int,
        originX: Int,
        originY: Int,
    ) {
        repeat(TILE_PIXELS) { y ->
            repeat(TILE_PIXELS) { x ->
                val color = tiles.indexAt(tileId * TILE_PIXELS + x, y)
                pixels[(originY + y) * pixelWidth + originX + x] = DMG_PALETTE[color]
            }
        }
    }

    private fun bankEnd(rom: RomImage, bank: Int): Int =
        minOf(rom.size.toLong(), (bank.toLong() + 1L) * BANK_BYTES).toInt()

    private data class Gen1LocalMapFormat(val label: String, val tilesetCount: Int) {
        companion object {
            fun forFamily(family: EngineFamily): Gen1LocalMapFormat? = when (family) {
                EngineFamily.RED_BLUE -> Gen1LocalMapFormat("Red/Blue", 24)
                EngineFamily.YELLOW -> Gen1LocalMapFormat("Yellow", 25)
                else -> null
            }
        }
    }

    private data class MapBankAuthority(val bank: Int, val root: Int)
    private data class MapPointerAuthority(val bank: Int, val root: Int)
    private data class TilesetAuthority(val bank: Int, val root: Int)
    private data class TilesetHeader(val bank: Int, val blockset: Int, val graphics: Int)
    private data class TilesetData(val blocks: ByteArray, val tiles: IndexedSprite)
    private data class MapAuthority(
        val bankAuthority: MapBankAuthority,
        val pointerAuthority: MapPointerAuthority,
        val tilesetAuthority: TilesetAuthority,
        val descriptors: List<MapDescriptor>,
        val tilesets: Map<Int, TilesetData>,
    )

    private data class MapDescriptor(
        val mapId: Int,
        val blockWidth: Int,
        val blockHeight: Int,
        val tilesetId: Int,
        val blockIds: ByteArray,
    ) {
        val gridWidth: Int = blockWidth * BLOCK_METATILE_EDGE
        val gridHeight: Int = blockHeight * BLOCK_METATILE_EDGE
        val pixelCount: Long = gridWidth.toLong() * METATILE_PIXELS * gridHeight * METATILE_PIXELS
        val usedBlockIds: Set<Int> = blockIds.mapTo(sortedSetOf()) { it.toInt() and 0xff }
        val key: String = "local/${mapId.toString(16).padStart(4, '0')}"
        val assetKey: String = "$key/map"

        fun toLocalMap(displayName: String?): LocalMap = LocalMap(
            key = key,
            displayName = displayName,
            baseAreaId = mapId,
            pixelWidth = gridWidth * METATILE_PIXELS,
            pixelHeight = gridHeight * METATILE_PIXELS,
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            imageAssetKey = assetKey,
        )
    }

    private const val BANK_BYTES = 0x4000
    private const val MAP_ID_COUNT = 256
    private val MAP_ID_RANGE = 0 until MAP_ID_COUNT
    private const val MAP_FIXED_HEADER_BYTES = 10
    private const val CONNECTION_MASK = 0x0f
    private const val TILESET_HEADER_BYTES = 12
    private const val TILESET_COPIED_BYTES = 11
    private const val MAX_BLOCK_DIMENSION = 128
    private const val BLOCK_METATILE_EDGE = 2
    private const val BLOCK_TILE_EDGE = 4
    private const val TILES_PER_BLOCK = BLOCK_TILE_EDGE * BLOCK_TILE_EDGE
    private const val TILES_PER_TILESET = 96
    private const val TILE_BYTES = 16
    private const val TILE_PIXELS = 8
    private const val METATILE_PIXELS = 16
    private const val BLOCK_PIXELS = BLOCK_TILE_EDGE * TILE_PIXELS
    private const val MAX_MAP_PIXELS = 2_000_000L
    private const val MAX_TOTAL_PIXELS = 100_000_000L
    private const val MAX_ENCODED_BYTES = 64L * 1024 * 1024

    private const val MAP_BANK_CONSUMER_BYTES = 16
    private const val DIRECT_POINTER_CONSUMER_BYTES = 19
    private const val TILESET_CONSUMER_BYTES = 23
    private const val PUSH_HL = 0xe5
    private const val PUSH_BC = 0xc5
    private const val PUSH_DE = 0xd5
    private const val LOAD_C_A = 0x4f
    private const val LOAD_B_IMMEDIATE = 0x06
    private const val LOAD_D_IMMEDIATE = 0x16
    private const val LOAD_E_A = 0x5f
    private const val LOAD_B_A = 0x47
    private const val LOAD_A_IMMEDIATE = 0x3e
    private const val LOAD_A_ABSOLUTE = 0xfa
    private const val LOAD_HL_IMMEDIATE = 0x21
    private const val LOAD_DE_IMMEDIATE = 0x11
    private const val LOAD_BC_IMMEDIATE = 0x01
    private const val LOAD_C_IMMEDIATE = 0x0e
    private const val LOAD_A_HL = 0x7e
    private const val LOAD_A_HL_INCREMENT = 0x2a
    private const val LOAD_H_HL = 0x66
    private const val LOAD_L_A = 0x6f
    private const val ADD_HL_BC = 0x09
    private const val ADD_HL_DE = 0x19
    private const val ADD_A = 0x87
    private const val ADD_B = 0x80
    private const val ADD_L = 0x85
    private const val INC_D = 0x14
    private const val INC_H = 0x24
    private const val CALL = 0xcd
    private const val JR_NC = 0x30
    private const val PREFIX = 0xcb
    private const val SHIFT_LEFT_A = 0x27
    private const val LOAD_HIGH_A = 0xe0

    private val DMG_PALETTE = intArrayOf(
        0xffffffff.toInt(),
        0xffaaaaaa.toInt(),
        0xff555555.toInt(),
        0xff000000.toInt(),
    )
}
