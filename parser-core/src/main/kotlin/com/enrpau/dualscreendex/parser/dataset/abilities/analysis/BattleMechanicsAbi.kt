package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import java.util.Collections

enum class ScalarWidth(val bytes: Int) {
    U8(1),
    U16(2),
    U32(4),
}

data class ScalarField(
    val offset: Int,
    val width: ScalarWidth,
) {
    init {
        require(offset >= 0) { "scalar field offset must not be negative" }
    }

    internal fun requireContainedBy(recordName: String, stride: Int) {
        require(offset.toLong() + width.bytes <= stride.toLong()) {
            "$recordName field $this exceeds stride $stride"
        }
    }
}

data class BattleRecordAbi(
    val stride: Int,
    val attack: ScalarField,
    val defense: ScalarField? = null,
    val specialAttack: ScalarField? = null,
    val specialDefense: ScalarField? = null,
    val ability: ScalarField,
    val hp: ScalarField? = null,
    val maxHp: ScalarField? = null,
    val status: ScalarField? = null,
) {
    init {
        require(stride > 0) { "battle-record stride must be positive" }
        listOfNotNull(attack, defense, specialAttack, specialDefense, ability, hp, maxHp, status)
            .forEach { it.requireContainedBy("battle-record", stride) }
    }
}

data class MoveMechanicsAbi(
    val tableRoot: Int,
    val stride: Int,
    val effect: ScalarField,
    val power: ScalarField,
    val type: ScalarField,
    val category: ScalarField? = null,
    val effectiveSplitContextPointer: Int? = null,
    val effectiveSplitPackedField: ScalarField? = null,
    val effectiveSplitMask: Int? = null,
) {
    init {
        require(tableRoot in GBA_ROM_START until GBA_ROM_END_EXCLUSIVE) {
            "move table root must be a mapped GBA ROM address"
        }
        require(stride > 0) { "move-record stride must be positive" }
        listOfNotNull(effect, power, type, category)
            .forEach { it.requireContainedBy("move-record", stride) }
        val overrideParts = listOf(effectiveSplitContextPointer, effectiveSplitPackedField, effectiveSplitMask)
        require(overrideParts.all { it == null } || overrideParts.all { it != null }) {
            "effective split override requires pointer, packed field, and mask together"
        }
        effectiveSplitContextPointer?.let { pointer ->
            require(pointer in GBA_EWRAM_START until GBA_IWRAM_END_EXCLUSIVE) {
                "effective split context pointer must be mapped GBA work RAM"
            }
            require(effectiveSplitPackedField!!.width == ScalarWidth.U8)
            val mask = effectiveSplitMask!!
            val normalizedMask = mask ushr Integer.numberOfTrailingZeros(mask)
            require(mask in 1..0xFF && normalizedMask and normalizedMask + 1 == 0) {
                "effective split mask must be contiguous after normalization"
            }
        }
    }

    private companion object {
        const val GBA_ROM_START = 0x0800_0000
        const val GBA_ROM_END_EXCLUSIVE = 0x0A00_0000
        const val GBA_EWRAM_START = 0x0200_0000
        const val GBA_IWRAM_END_EXCLUSIVE = 0x0300_8000
    }
}

sealed interface BattleRoleContract {
    data class DirectPointers(
        val attackerParameterRegister: Int,
        val defenderParameterRegister: Int,
    ) : BattleRoleContract {
        init {
            requireParameterRegister(attackerParameterRegister, "attacker pointer")
            requireParameterRegister(defenderParameterRegister, "defender pointer")
            require(attackerParameterRegister != defenderParameterRegister) {
                "attacker and defender pointers require distinct parameters"
            }
        }
    }

    data class IndexedArray(
        val battleArrayRoot: Int,
        val attackerIndexParameterRegister: Int,
        val defenderIndexParameterRegister: Int,
    ) : BattleRoleContract {
        init {
            require(battleArrayRoot in GBA_EWRAM_START until GBA_IWRAM_END_EXCLUSIVE) {
                "battle array root must be a mapped GBA work-RAM address"
            }
            requireParameterRegister(attackerIndexParameterRegister, "attacker index")
            requireParameterRegister(defenderIndexParameterRegister, "defender index")
            require(attackerIndexParameterRegister != defenderIndexParameterRegister) {
                "attacker and defender indices require distinct parameters"
            }
        }
    }

    private companion object {
        const val GBA_EWRAM_START = 0x0200_0000
        const val GBA_IWRAM_END_EXCLUSIVE = 0x0300_8000

        fun requireParameterRegister(register: Int, label: String) {
            require(register in 0..3) { "$label must use an ARM procedure-call parameter register" }
        }
    }
}

class BattleMechanicsAbi(
    val record: BattleRecordAbi,
    val move: MoveMechanicsAbi,
    activeAbilityIds: Set<Int>,
    val roleContract: BattleRoleContract,
    val moveParameterRegister: Int? = null,
    withheldAbilityIds: Set<Int> = emptySet(),
) {
    val activeAbilityIds: List<Int> = Collections.unmodifiableList(activeAbilityIds.sorted())
    val withheldAbilityIds: Set<Int> = Collections.unmodifiableSet(withheldAbilityIds.toSet())

    init {
        require(activeAbilityIds.isNotEmpty()) { "ability domain must not be empty" }
        require(activeAbilityIds.all { it > 0 }) { "ability domain contains only direct positive IDs" }
        require(withheldAbilityIds.all { it in activeAbilityIds }) {
            "withheld ability IDs must belong to the active typed domain"
        }
        val maximumValue = when (record.ability.width) {
            ScalarWidth.U8 -> 0xFF
            ScalarWidth.U16 -> 0xFFFF
            ScalarWidth.U32 -> Int.MAX_VALUE
        }
        require(activeAbilityIds.all { it <= maximumValue }) {
            "ability domain exceeds the typed ability field width"
        }
        moveParameterRegister?.let { requireParameterRegister(it, "move ID") }
    }

    private fun requireParameterRegister(register: Int, label: String) {
        require(register in 0..3) { "$label must use an ARM procedure-call parameter register" }
    }
}
