package com.enrpau.dualscreendex.parser.catalog

object LearnsetNormalizer {
    fun normalize(entries: List<LearnsetEntry>): List<NormalizedLevelUpMove> {
        val grouped = linkedMapOf<Int, MutableList<LearnsetEntry>>()
        entries.forEach { entry -> grouped.getOrPut(entry.moveId) { mutableListOf() } += entry }
        return grouped.map { (moveId, occurrences) ->
            NormalizedLevelUpMove(
                moveId = moveId,
                initial = occurrences.any { it.level == 1 },
                levels = occurrences.asSequence()
                    .map { it.level }
                    .filter { it != 1 }
                    .distinct()
                    .sorted()
                    .toList(),
            )
        }
    }
}
