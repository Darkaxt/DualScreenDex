package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Resolves map labels from encounter-proven gMapGroups and the ROM's region-map entry table. */
object Gen3MapLocationResolver {
    fun resolve(rom: RomImage, encounterBaseIds: Set<Int>): Map<Int, String> {
        val requiredMaps = encounterBaseIds
            .filter { it in 0..0xFFFF }
            .groupBy({ it ushr 8 }, { it and 0xFF })
            .mapValues { (_, maps) -> maps.toSet() }
        if (requiredMaps.isEmpty()) return emptyMap()
        val mapGroupsRoot = findMapGroupsRoot(rom, requiredMaps) ?: return emptyMap()
        val mapSections = enumerateMapSections(rom, mapGroupsRoot) ?: return emptyMap()
        val names = findRegionNames(rom, mapSections.values.toSet()) ?: return emptyMap()
        return mapSections.mapNotNull { (baseId, sectionId) ->
            names[sectionId]?.let { baseId to it }
        }.toMap(linkedMapOf())
    }

    private fun findMapGroupsRoot(rom: RomImage, requiredMaps: Map<Int, Set<Int>>): Int? {
        val maxGroup = requiredMaps.keys.maxOrNull() ?: return null
        val roots = mutableListOf<Int>()
        var root = 0
        val last = rom.size - (maxGroup + 1) * 4
        while (root <= last) {
            val valid = requiredMaps.all { (group, maps) ->
                val groupRoot = rom.gbaPointer(root + group * 4) ?: return@all false
                maps.all { map ->
                    val pointerOffset = groupRoot.toLong() + map.toLong() * 4
                    pointerOffset + 4 <= rom.size.toLong() &&
                        rom.gbaPointer(pointerOffset.toInt())?.let { validMapHeader(rom, it) } == true
                }
            }
            if (valid) {
                roots += root
                if (roots.size > 1) return null
            }
            root += 4
        }
        return roots.singleOrNull()
    }

    private fun enumerateMapSections(rom: RomImage, root: Int): Map<Int, Int>? {
        val groupRoots = mutableListOf<Int>()
        var cursor = root
        while (cursor + 4 <= rom.size) {
            val groupRoot = rom.gbaPointer(cursor) ?: break
            groupRoots += groupRoot
            cursor += 4
        }
        if (groupRoots.isEmpty() || groupRoots.distinct().size != groupRoots.size) return null
        val boundaries = (groupRoots + root).distinct().sorted()
        val sections = linkedMapOf<Int, Int>()
        groupRoots.forEachIndexed { group, groupRoot ->
            val end = boundaries.firstOrNull { it > groupRoot } ?: return null
            if (end <= groupRoot || (end - groupRoot) % 4 != 0) return null
            val mapCount = (end - groupRoot) / 4
            if (mapCount <= 0) return null
            repeat(mapCount) { map ->
                val header = rom.gbaPointer(groupRoot + map * 4) ?: return null
                if (!validMapHeader(rom, header)) return null
                sections[(group shl 8) or map] = rom.u8(header + REGION_SECTION_OFFSET)
            }
        }
        return sections
    }

    private fun validMapHeader(rom: RomImage, offset: Int): Boolean {
        if (offset < 0 || offset.toLong() + MAP_HEADER_BYTES > rom.size.toLong()) return false
        val layout = rom.gbaPointer(offset) ?: return false
        val events = rom.gbaPointer(offset + 4) ?: return false
        if (layout == events) return false
        for (pointerOffset in listOf(offset + 8, offset + 12)) {
            val raw = rom.u32le(pointerOffset)
            if (raw != 0L && rom.gbaPointer(pointerOffset) == null) return false
        }
        return rom.u16le(offset + 0x12) != 0
    }

    private fun findRegionNames(rom: RomImage, sectionIds: Set<Int>): Map<Int, String>? {
        if (sectionIds.isEmpty()) return null
        val maxSection = sectionIds.maxOrNull() ?: return null
        val anchorIds = sectionIds.sortedDescending().take(3)
        val candidates = linkedMapOf<Int, Map<Int, String>>()
        var root = 0
        val last = rom.size - (maxSection + 1) * REGION_ENTRY_BYTES
        while (root <= last) {
            if (anchorIds.all { validRegionEntry(rom, root, it) }) {
                if (!(0..maxSection).all { validRegionEntryShell(rom, root, it) }) {
                    root += 4
                    continue
                }
                val names = sectionIds.mapNotNull { section ->
                    decodeRegionName(rom, root, section)?.let { section to it }
                }.toMap()
                candidates[root] = names
            }
            root += 4
        }
        if (candidates.isEmpty()) return null
        val referenceCounts = candidates.keys.associateWith { 0 }.toMutableMap()
        var pointerOffset = 0
        while (pointerOffset <= rom.size - 4) {
            val target = (rom.u32le(pointerOffset) - GBA_ROM_BASE).toInt()
            if (target in referenceCounts) referenceCounts[target] = referenceCounts.getValue(target) + 1
            pointerOffset += 4
        }
        val maximumReferences = referenceCounts.values.maxOrNull()?.takeIf { it > 0 } ?: return null
        val winner = referenceCounts.filterValues { it == maximumReferences }.keys.singleOrNull() ?: return null
        return candidates.getValue(winner)
    }

    private fun validRegionEntry(rom: RomImage, root: Int, sectionId: Int): Boolean {
        if (!validRegionEntryShell(rom, root, sectionId)) return false
        return decodeRegionName(rom, root, sectionId) != null
    }

    private fun validRegionEntryShell(rom: RomImage, root: Int, sectionId: Int): Boolean {
        val offset = root + sectionId * REGION_ENTRY_BYTES
        val x = rom.u8(offset)
        val y = rom.u8(offset + 1)
        val width = rom.u8(offset + 2)
        val height = rom.u8(offset + 3)
        if (x >= REGION_GRID_WIDTH || y >= REGION_GRID_HEIGHT) return false
        if (width !in 1..REGION_GRID_WIDTH || height !in 1..REGION_GRID_HEIGHT) return false
        if (x + width > REGION_GRID_WIDTH || y + height > REGION_GRID_HEIGHT) return false
        val text = rom.gbaPointer(offset + 4) ?: return false
        val available = minOf(MAX_REGION_NAME_BYTES, rom.size - text)
        return available > 0 && PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(text, available)).terminated
    }

    private fun decodeRegionName(rom: RomImage, root: Int, sectionId: Int): String? {
        val offset = root + sectionId * REGION_ENTRY_BYTES
        val text = rom.gbaPointer(offset + 4) ?: return null
        val available = minOf(MAX_REGION_NAME_BYTES, rom.size - text)
        if (available <= 0) return null
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(text, available))
        return decoded.text.takeIf {
            decoded.terminated && decoded.validRatio >= MIN_TEXT_RATIO && it.any(Char::isLetterOrDigit)
        }
    }

    private const val MAP_HEADER_BYTES = 28
    private const val GBA_ROM_BASE = 0x08000000L
    private const val REGION_SECTION_OFFSET = 0x14
    private const val REGION_ENTRY_BYTES = 8
    private const val REGION_GRID_WIDTH = 32
    private const val REGION_GRID_HEIGHT = 32
    private const val MAX_REGION_NAME_BYTES = 32
    private const val MIN_TEXT_RATIO = 0.85
}
