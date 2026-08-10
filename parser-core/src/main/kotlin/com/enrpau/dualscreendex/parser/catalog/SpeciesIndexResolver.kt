package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout

object SpeciesIndexResolver {
    fun resolve(rom: RomImage, layout: ResolvedRomLayout): Map<Int, Int> = when (layout.generation) {
        1 -> resolveGen1(rom, layout)
        2 -> (1..(layout.speciesCount ?: 0)).associateWith { it }
        3 -> resolveGen3(rom, layout)
        else -> emptyMap()
    }

    private fun resolveGen1(rom: RomImage, layout: ResolvedRomLayout): Map<Int, Int> {
        val internalCount = layout.speciesCount ?: layout.tables.speciesNames?.count ?: return emptyMap()
        val dexCount = layout.tables.baseStats?.count ?: return identity(1, internalCount)
        for (offset in 0..rom.size - internalCount) {
            val values = IntArray(internalCount) { index -> rom.u8(offset + index) }
            if (values.all { it in 0..dexCount } &&
                values.count { it != 0 } == dexCount &&
                values.filter { it != 0 }.toSet() == (1..dexCount).toSet()
            ) {
                return (1..internalCount).associateWith { id -> values[id - 1] }
            }
        }
        return identity(1, internalCount)
    }

    private fun resolveGen3(rom: RomImage, layout: ResolvedRomLayout): Map<Int, Int> {
        val speciesCount = layout.speciesCount ?: return emptyMap()
        layout.pokeemeraldExpansion?.let { expansion ->
            val table = layout.tables.baseStats ?: return emptyMap()
            val stride = table.stride ?: expansion.speciesRecordSize
            return (0 until speciesCount).associateWith { id ->
                rom.u16le(table.offset + id * stride + expansion.nationalDexOffset)
            }
        }
        val storedCount = speciesCount - 1
        if (storedCount <= 0) return mapOf(0 to 0)
        val candidates = mutableListOf<Gen3IndexCandidate>()
        val prefixPattern = if (storedCount == 1) byteArrayOf(1, 0) else byteArrayOf(1, 0, 2, 0)
        val lastTableStart = rom.size - storedCount * 2
        rom.findAll(prefixPattern).asSequence()
            .filter { it % 2 == 0 && it <= lastTableStart }
            .forEach { offset ->
                val values = IntArray(storedCount) { index -> rom.u16le(offset + index * 2) }
                val maximumPlausible = maxOf(2048, speciesCount * 2)
                if (values.all { it in 0..maximumPlausible }) {
                    var prefix = 0
                    while (prefix < values.size && values[prefix] == prefix + 1) prefix++
                    val positive = values.filter { it > 0 }
                    val distinctRatio = positive.distinct().size.toDouble() / positive.size.coerceAtLeast(1)
                    if (prefix >= minOf(2, storedCount) && distinctRatio == 1.0) {
                        val canonicalBoundary = if (values.size >= 277 && values[276] == 252) 1 else 0
                        candidates += Gen3IndexCandidate(prefix, distinctRatio, canonicalBoundary, values)
                    }
                }
            }
        val values = candidates.maxWithOrNull(
            compareBy<Gen3IndexCandidate> { it.canonicalBoundary }
                .thenBy { it.distinctRatio }
                .thenBy { it.prefix }
                .thenBy { candidate -> candidate.values.count { it > 0 } },
        )?.values
        if (values == null) {
            findCompleteGen3Permutation(rom, speciesCount)?.let { permutation ->
                return permutation.indices.associateWith { permutation[it] }
            }
            return identity(0, speciesCount - 1)
        }
        return buildMap {
            put(0, 0)
            values.forEachIndexed { index, dex -> put(index + 1, dex) }
        }
    }

    /**
     * Expanded Gen III projects commonly replace the historical Hoenn-era index layout with a
     * complete internal-species -> Pokédex-number permutation. Unlike the stock table, its first
     * live entry does not have to be Dex #1, so it cannot be found from a 1,2 prefix signature.
     */
    private fun findCompleteGen3Permutation(rom: RomImage, speciesCount: Int): IntArray? {
        if (speciesCount <= 1 || speciesCount * 2 > rom.size) return null
        val byteLength = speciesCount * 2
        for (offset in 0..rom.size - byteLength step 2) {
            if (rom.u16le(offset) != 0) continue
            val seen = BooleanArray(speciesCount)
            val values = IntArray(speciesCount)
            var compatible = true
            for (index in 0 until speciesCount) {
                val value = rom.u16le(offset + index * 2)
                if (value !in 0 until speciesCount || seen[value]) {
                    compatible = false
                    break
                }
                seen[value] = true
                values[index] = value
            }
            if (compatible) return values
        }
        return null
    }

    private fun identity(first: Int, last: Int): Map<Int, Int> = (first..last).associateWith { it }

    private data class Gen3IndexCandidate(
        val prefix: Int,
        val distinctRatio: Double,
        val canonicalBoundary: Int,
        val values: IntArray,
    )
}
