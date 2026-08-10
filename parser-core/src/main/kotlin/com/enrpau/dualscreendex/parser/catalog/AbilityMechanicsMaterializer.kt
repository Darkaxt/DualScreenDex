package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout

enum class AbilityMechanicKind { ACTIVATION_THRESHOLD, MULTIPLIER, AI_RATING, FLAG }

data class AbilityMechanic(
    val kind: AbilityMechanicKind,
    val label: String,
    val value: String,
    val numerator: Int,
    val denominator: Int,
)

data class AbilityMechanicsResult(
    val sourceOffset: Int,
    val confidence: Double,
    val mechanicsByAbility: Map<Int, List<AbilityMechanic>>,
)

object AbilityMechanicsMaterializer {
    private const val CODE_WINDOW = 0x1000

    fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        abilities: Map<Int, AbilityRecord>,
        types: Map<Int, TypeRecord>,
    ): AbilityMechanicsResult? {
        if (layout.generation != 3) return null
        layout.pokeemeraldExpansion?.let { expansion ->
            val table = layout.tables.abilities ?: return null
            val stride = table.stride ?: expansion.abilityRecordSize
            val mechanics = abilities.keys.associateWith { id ->
                val record = table.offset + id * stride
                val rating = rom.u8(record + expansion.abilityDescriptionPointerOffset + 4).toByte().toInt()
                val flags = rom.u8(record + expansion.abilityDescriptionPointerOffset + 5)
                buildList {
                    add(AbilityMechanic(AbilityMechanicKind.AI_RATING, "AI rating", rating.toString(), rating, 1))
                    EXPANSION_FLAG_LABELS.forEachIndexed { bit, label ->
                        if (flags and (1 shl bit) != 0) {
                            add(AbilityMechanic(AbilityMechanicKind.FLAG, label, "Yes", 1, 1))
                        }
                    }
                }
            }
            return AbilityMechanicsResult(
                sourceOffset = table.offset + expansion.abilityDescriptionPointerOffset + 4,
                confidence = 1.0,
                mechanicsByAbility = mechanics,
            )
        }
        val abilityIds = PINCH_ABILITIES.associate { definition ->
            definition.abilityName to abilities.values.singleOrNull {
                it.name.value.equals(definition.abilityName, ignoreCase = true)
            }?.id
        }
        val typeIds = PINCH_ABILITIES.associate { definition ->
            definition.typeName to types.values.singleOrNull {
                it.name.value.equals(definition.typeName, ignoreCase = true)
            }?.id
        }
        if (abilityIds.values.any { it == null } || typeIds.values.any { it == null }) return null

        val candidates = buildList {
            var offset = 0
            while (offset + 2 <= rom.size) {
                if (moveImmediate(rom.u16le(offset)) == 3 && startsUnsignedDivision(rom, offset)) {
                    val following = immediateMoves(rom, offset + 2, minOf(rom.size, offset + 0x80))
                    val numerator = following.firstOrNull { it.second == 150 }
                    val denominator = numerator?.let { first -> following.firstOrNull { it.first > first.first && it.second == 100 } }
                    if (numerator != null && denominator != null) {
                        val start = maxOf(0, offset - CODE_WINDOW)
                        val end = minOf(rom.size, offset + CODE_WINDOW)
                        val comparisons = immediateComparisons(rom, start, end)
                        val corroborated = PINCH_ABILITIES.all { definition ->
                            abilityIds.getValue(definition.abilityName) in comparisons &&
                                typeIds.getValue(definition.typeName) in comparisons
                        }
                        if (corroborated) add(CodeCandidate(offset, 3, numerator.second, denominator.second))
                    }
                }
                offset += 2
            }
        }
        val clusters = candidates.sortedBy(CodeCandidate::offset).fold(mutableListOf<MutableList<CodeCandidate>>()) { groups, candidate ->
            val previous = groups.lastOrNull()?.lastOrNull()
            if (previous != null && candidate.offset - previous.offset <= REPEATED_BLOCK_WINDOW &&
                candidate.hpDenominator == previous.hpDenominator &&
                candidate.multiplierNumerator == previous.multiplierNumerator &&
                candidate.multiplierDenominator == previous.multiplierDenominator
            ) {
                groups.last() += candidate
            } else {
                groups += mutableListOf(candidate)
            }
            groups
        }
        if (clusters.size != 1) return null
        val candidate = clusters.single().first()
        val sourceOffset = candidate.offset
        val hpDenominator = candidate.hpDenominator
        val multiplier = candidate.multiplierNumerator to candidate.multiplierDenominator
        val mechanics = PINCH_ABILITIES.associate { definition ->
            val id = abilityIds.getValue(definition.abilityName)!!
            id to listOf(
                AbilityMechanic(
                    AbilityMechanicKind.ACTIVATION_THRESHOLD,
                    "Activation",
                    "HP ≤ 1/$hpDenominator",
                    1,
                    hpDenominator,
                ),
                AbilityMechanic(
                    AbilityMechanicKind.MULTIPLIER,
                    "Power",
                    "${definition.typeName} move power ×${formatRatio(multiplier.first, multiplier.second)}",
                    multiplier.first,
                    multiplier.second,
                ),
            )
        }
        return AbilityMechanicsResult(sourceOffset, 1.0, mechanics)
    }

    private fun immediateMoves(rom: RomImage, start: Int, end: Int): List<Pair<Int, Int>> = buildList {
        var offset = start
        while (offset + 2 <= end) {
            moveImmediate(rom.u16le(offset))?.let { add(offset to it) }
            offset += 2
        }
    }

    private fun immediateComparisons(rom: RomImage, start: Int, end: Int): Set<Int> = buildSet {
        var offset = start
        while (offset + 2 <= end) {
            val instruction = rom.u16le(offset)
            if (instruction and 0xF800 == 0x2800) add(instruction and 0xFF)
            offset += 2
        }
    }

    private fun moveImmediate(instruction: Int): Int? =
        (instruction and 0xFF).takeIf { instruction and 0xF800 == 0x2000 }

    private fun startsUnsignedDivision(rom: RomImage, offset: Int): Boolean {
        if (offset + 8 > rom.size) return false
        val first = rom.u16le(offset + 2)
        return isThumbLongBranch(first, rom.u16le(offset + 4)) ||
            first and 0xF800 == 0x8800 && isThumbLongBranch(rom.u16le(offset + 4), rom.u16le(offset + 6))
    }

    private fun isThumbLongBranch(first: Int, second: Int): Boolean =
        first and 0xF800 == 0xF000 && second and 0xF800 == 0xF800

    private fun formatRatio(numerator: Int, denominator: Int): String {
        val value = numerator.toDouble() / denominator
        return if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value).trimEnd('0')
    }

    private data class PinchAbilityDefinition(val abilityName: String, val typeName: String)

    private data class CodeCandidate(
        val offset: Int,
        val hpDenominator: Int,
        val multiplierNumerator: Int,
        val multiplierDenominator: Int,
    )

    private const val REPEATED_BLOCK_WINDOW = 0x200

    private val PINCH_ABILITIES = listOf(
        PinchAbilityDefinition("Overgrow", "Grass"),
        PinchAbilityDefinition("Blaze", "Fire"),
        PinchAbilityDefinition("Torrent", "Water"),
        PinchAbilityDefinition("Swarm", "Bug"),
    )

    private val EXPANSION_FLAG_LABELS = listOf(
        "Cannot be copied",
        "Cannot be swapped",
        "Cannot be traced",
        "Cannot be suppressed",
        "Cannot be overwritten",
        "Breakable",
        "Fails on Imposter",
    )
}
