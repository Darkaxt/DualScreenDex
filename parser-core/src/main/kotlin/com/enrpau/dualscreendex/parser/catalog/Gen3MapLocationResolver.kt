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
        val sections = resolveMapSections(rom, requiredMaps, references) ?: return null
        val entries = findRegionEntries(rom, sections.values.toSet()).orEmpty()
        return Gen3MapLocationResolution(sections, entries).takeIf { it.entriesBySection.isNotEmpty() }
    }

    internal fun resolveSectionByBaseArea(
        rom: RomImage,
        encounterBaseIds: Set<Int>,
        references: GbaReferenceIndex,
    ): Map<Int, Int> {
        val requiredMaps = requiredMaps(encounterBaseIds)
        if (requiredMaps.isEmpty()) return emptyMap()
        return resolveMapSections(rom, requiredMaps, references).orEmpty()
    }

    private fun resolveMapSections(
        rom: RomImage,
        requiredMaps: Map<Int, Set<Int>>,
        references: GbaReferenceIndex,
    ): Map<Int, Int>? {
        val compiledRoots = findCompiledMapGroupsConsumerRoots(rom)
        if (compiledRoots.isNotEmpty()) {
            val requiredCount = requiredMaps.values.sumOf { maps -> maps.size }
            val resolved = compiledRoots.mapNotNull { root ->
                enumerateRequiredMapSections(rom, root, requiredMaps)?.also { sections ->
                    mapTrace(
                        "map-groups root=0x${root.toString(16)} " +
                            "required=${sections.size}/$requiredCount",
                    )
                }
            }.distinct()
            mapTrace(
                "map-groups compiledRoots=${compiledRoots.joinToString { "0x${it.toString(16)}" }} " +
                    "validLayouts=${resolved.size} requiredGroups=${requiredMaps.keys.sorted()} " +
                    "maxMaps=${requiredMaps.mapValues { it.value.maxOrNull() }}",
            )
            return resolved.singleOrNull()
        }
        val roots = findMapGroupsRoots(rom, requiredMaps)
        val maximumReferences = roots.maxOfOrNull(references::referenceCount)?.takeIf { it > 0 } ?: return null
        val root = roots.filter { references.referenceCount(it) == maximumReferences }.singleOrNull() ?: return null
        return enumerateMapSections(rom, root)
    }

    private fun enumerateRequiredMapSections(
        rom: RomImage,
        root: Int,
        requiredMaps: Map<Int, Set<Int>>,
    ): Map<Int, Int>? {
        val requiredCount = requiredMaps.values.sumOf(Set<Int>::size)
        val sections = linkedMapOf<Int, Int>()
        var unbindableHeaders = 0
        requiredMaps.toSortedMap().forEach groupLoop@{ (group, maps) ->
            val groupPointerOffset = root.toLong() + group.toLong() * 4L
            if (groupPointerOffset < 0 || groupPointerOffset + 4L > rom.size.toLong()) {
                return@groupLoop
            }
            val groupRoot = rom.gbaPointer(groupPointerOffset.toInt()) ?: return@groupLoop
            maps.sorted().forEach mapLoop@{ map ->
                val headerPointerOffset = groupRoot.toLong() + map.toLong() * 4L
                if (headerPointerOffset < 0 || headerPointerOffset + 4L > rom.size.toLong()) {
                    return@mapLoop
                }
                val rawHeader = rom.u32le(headerPointerOffset.toInt())
                val header = rom.gbaPointer(headerPointerOffset.toInt())
                if (header == null) {
                    if (rawHeader != 0L) unbindableHeaders++
                    return@mapLoop
                }
                if (!validMapHeader(rom, header)) return@mapLoop
                sections[(group shl 8) or map] = rom.u8(header + REGION_SECTION_OFFSET)
            }
        }
        mapTrace(
            "map-groups root=0x${root.toString(16)} bound=${sections.size}/$requiredCount " +
                "unbindable=$unbindableHeaders",
        )
        return sections.takeIf {
            it.isNotEmpty() && it.size + unbindableHeaders == requiredCount
        }
    }

    /**
     * Matches the source consumer `gMapGroups[group][map]`, including the compiler's u16
     * zero-extension and four-byte pointer indexing. The data arrays may be sparse or relocated;
     * only the required encounter keys are authoritative.
     */
    private fun findCompiledMapGroupsConsumerRoots(rom: RomImage): List<Int> = buildList {
        var offset = 0
        while (offset.toLong() + MAP_GROUP_LOOKUP_NARROW_BYTES <= rom.size.toLong()) {
            val u16Arguments = offset.toLong() + MAP_GROUP_LOOKUP_U16_BYTES <= rom.size.toLong() &&
                rom.u16le(offset) == THUMB_LSL_R0_16 &&
                rom.u16le(offset + 2) == THUMB_LSL_R1_16 &&
                isLiteralLoadR2(rom.u16le(offset + 4)) &&
                rom.u16le(offset + 6) == THUMB_LSR_R0_14 &&
                rom.u16le(offset + 8) == THUMB_ADD_R0_R0_R2 &&
                rom.u16le(offset + 10) == THUMB_LDR_R0_R0 &&
                rom.u16le(offset + 12) == THUMB_LSR_R1_14 &&
                rom.u16le(offset + 14) == THUMB_ADD_R1_R1_R0 &&
                rom.u16le(offset + 16) == THUMB_LDR_R0_R1 &&
                rom.u16le(offset + 18) == THUMB_BX_LR
            val narrowArguments = isLiteralLoadR2(rom.u16le(offset)) &&
                rom.u16le(offset + 2) == THUMB_LSL_R0_2 &&
                rom.u16le(offset + 4) == THUMB_ADD_R0_R0_R2 &&
                rom.u16le(offset + 6) == THUMB_LDR_R0_R0 &&
                rom.u16le(offset + 8) == THUMB_LSL_R1_2 &&
                rom.u16le(offset + 10) == THUMB_ADD_R1_R1_R0 &&
                rom.u16le(offset + 12) == THUMB_LDR_R0_R1 &&
                rom.u16le(offset + 14) == THUMB_BX_LR
            if (u16Arguments || narrowArguments) {
                val literalOffset = if (u16Arguments) offset + 4 else offset
                val literalInstruction = rom.u16le(literalOffset)
                val literal = ((literalOffset + 4) and -4) + (literalInstruction and 0xff) * 4
                if (literal.toLong() + 4L <= rom.size.toLong()) rom.gbaPointer(literal)?.let(::add)
            }
            offset += 2
        }
    }.distinct()

    private fun isLiteralLoadR2(instruction: Int): Boolean =
        instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE &&
            instruction ushr THUMB_REGISTER_SHIFT and THUMB_REGISTER_MASK == 2

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
        for (pointerOffset in listOf(offset + 4, offset + 8, offset + 12)) {
            val raw = rom.u32le(pointerOffset)
            if (raw != 0L && rom.gbaPointer(pointerOffset) == null) return false
        }
        val events = rom.gbaPointer(offset + 4)
        if (events != null && layout == events) return false
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
                sectionIds.all { validRegionEntryShell(rom, root, it) }
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
        val winners = referenceCounts.filterValues { it == maximumReferences }.keys
        mapTrace(
            "region-entries candidates=${candidates.size} maxReferences=$maximumReferences " +
                "winners=${winners.joinToString { "0x${it.toString(16)}" }}",
        )
        val winner = winners.singleOrNull() ?: return null
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

    private fun mapTrace(message: String) {
        if (System.getenv("DUALDEX_MAP_TRACE") == "1") {
            println("world-map-trace $message")
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
    private const val MAP_GROUP_LOOKUP_U16_BYTES = 20L
    private const val MAP_GROUP_LOOKUP_NARROW_BYTES = 16L
    private const val THUMB_LSL_R0_16 = 0x0400
    private const val THUMB_LSL_R1_16 = 0x0409
    private const val THUMB_LSR_R0_14 = 0x0B80
    private const val THUMB_LSL_R0_2 = 0x0080
    private const val THUMB_ADD_R0_R0_R2 = 0x1880
    private const val THUMB_LDR_R0_R0 = 0x6800
    private const val THUMB_LSR_R1_14 = 0x0B89
    private const val THUMB_LSL_R1_2 = 0x0089
    private const val THUMB_ADD_R1_R1_R0 = 0x1809
    private const val THUMB_LDR_R0_R1 = 0x6808
    private const val THUMB_BX_LR = 0x4770
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
    private const val THUMB_REGISTER_SHIFT = 8
    private const val THUMB_REGISTER_MASK = 0x7
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
