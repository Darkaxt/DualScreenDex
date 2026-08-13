package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Resolves semantic map identities from encounter-proven map headers and region-map data. */
object Gen3MapLocationResolver {
    fun resolve(rom: RomImage, encounterBaseIds: Set<Int>): Map<Int, String> {
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return emptyMap()
        val root = findMapGroupsRoots(rom, requiredMaps).singleOrNull() ?: return emptyMap()
        return resolveFromRoot(rom, root).namesByBaseArea
    }

    fun resolve(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
    ): Map<Int, String> = resolveDetailed(rom, encounterBaseIds, references)?.namesByBaseArea.orEmpty()

    internal fun resolveDetailed(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
    ): Gen3MapLocationResolution? {
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return null
        val roots = findMapGroupsRoots(rom, requiredMaps)
        val maximumReferences = roots.maxOfOrNull(references::referenceCount)?.takeIf { it > 0 } ?: return null
        val root = roots.filter { references.referenceCount(it) == maximumReferences }.singleOrNull() ?: return null
        return resolveFromRoot(rom, root).takeIf { it.entriesBySection.isNotEmpty() }
    }

    internal fun resolveSectionByBaseArea(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
    ): Map<Int, Int> {
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return emptyMap()
        val roots = findMapGroupsRoots(rom, requiredMaps)
        val maximumReferences = roots.maxOfOrNull(references::referenceCount)?.takeIf { it > 0 } ?: return emptyMap()
        val root = roots.filter { references.referenceCount(it) == maximumReferences }.singleOrNull() ?: return emptyMap()
        return enumerateMapSections(rom, root).orEmpty()
    }

    private fun requiredMaps(encounterBaseIds: Set<Int>): Map<Int, Set<Int>> = encounterBaseIds
        .filter { it in 0..0xFFFF }
        .groupBy({ it ushr 8 }, { it and 0xFF })
        .mapValues { (_, maps) -> maps.toSet() }

    private fun resolveFromRoot(rom: RomImage, root: Int): Gen3MapLocationResolution {
        val sections = enumerateMapSections(rom, root).orEmpty()
        val entries = findRegionEntries(rom, sections.values.toSet()).orEmpty()
        return Gen3MapLocationResolution(sections, entries)
    }

    private fun findMapGroupsRoots(rom: RomImage, requiredMaps: Map<Int, Set<Int>>): List<Int> {
        val maxGroup = requiredMaps.keys.maxOrNull() ?: return emptyList()
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
            if (valid && enumerateMapSections(rom, root) != null) roots += root
            root += 4
        }
        return roots
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

    private fun findRegionEntries(rom: RomImage, sectionIds: Set<Int>): Map<Int, Gen3RegionMapEntry>? {
        if (sectionIds.isEmpty()) return null
        val maxSection = sectionIds.maxOrNull() ?: return null
        val anchorIds = sectionIds.sortedDescending().take(3)
        val candidates = linkedMapOf<Int, Map<Int, Gen3RegionMapEntry>>()
        var root = 0
        val last = rom.size - (maxSection + 1) * REGION_ENTRY_BYTES
        while (root <= last) {
            if (anchorIds.all { validRegionEntry(rom, root, it) } &&
                (0..maxSection).all { validRegionEntryShell(rom, root, it) }
            ) {
                candidates[root] = sectionIds.mapNotNull { section ->
                    decodeRegionEntry(rom, root, section)?.let { section to it }
                }.toMap()
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

    private fun validRegionEntry(rom: RomImage, root: Int, sectionId: Int): Boolean =
        validRegionEntryShell(rom, root, sectionId) && decodeRegionEntry(rom, root, sectionId) != null

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

    private fun decodeRegionEntry(rom: RomImage, root: Int, sectionId: Int): Gen3RegionMapEntry? {
        val offset = root + sectionId * REGION_ENTRY_BYTES
        val text = rom.gbaPointer(offset + 4) ?: return null
        val available = minOf(MAX_REGION_NAME_BYTES, rom.size - text)
        if (available <= 0) return null
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(text, available))
        val name = decoded.text.takeIf {
            decoded.terminated && decoded.validRatio >= MIN_TEXT_RATIO && it.any(Char::isLetterOrDigit)
        } ?: return null
        return Gen3RegionMapEntry(
            sectionId,
            rom.u8(offset),
            rom.u8(offset + 1),
            rom.u8(offset + 2),
            rom.u8(offset + 3),
            name,
        )
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

internal data class Gen3MapLocationResolution(
    val sectionByBaseArea: Map<Int, Int>,
    val entriesBySection: Map<Int, Gen3RegionMapEntry>,
) {
    val namesByBaseArea: Map<Int, String> = sectionByBaseArea.mapNotNull { (baseId, sectionId) ->
        entriesBySection[sectionId]?.let { baseId to it.displayName }
    }.toMap(linkedMapOf())
}

internal data class Gen3RegionMapEntry(
    val sectionId: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val displayName: String,
)
