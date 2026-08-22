package com.darkaxt.dualdex.save

data class TrainerIdentity(
    val name: String,
    val gender: Int,
) {
    init {
        require(name.isNotBlank()) { "trainer name must not be blank" }
        require(gender in 0..1) { "trainer gender must be 0 or 1" }
    }
}

data class TrainerSnapshot(
    val name: String,
    val gender: Int,
    val publicTrainerId: Int,
    val money: Long,
    val playTimeHours: Int,
    val playTimeMinutes: Int,
    val badgeFlags: Int,
    val dexSeen: Int,
    val dexCaught: Int,
    val stars: Int? = null,
) {
    init {
        require(name.isNotBlank()) { "trainer name must not be blank" }
        require(gender in 0..1) { "trainer gender must be 0 or 1" }
        require(publicTrainerId in 0..0xFFFF) { "public trainer ID must fit in 16 bits" }
        require(money >= 0) { "money must not be negative" }
        require(playTimeHours >= 0) { "play time hours must not be negative" }
        require(playTimeMinutes in 0..59) { "play time minutes must be in 0..59" }
        require(badgeFlags in 0..0xFF) { "badge flags must fit in 8 bits" }
        require(dexSeen >= 0 && dexCaught >= 0) { "Pokédex counts must not be negative" }
        require(dexCaught <= dexSeen) { "caught count must not exceed seen count" }
        require(stars == null || stars >= 0) { "Trainer Card stars must not be negative" }
    }
}

data class PartyMemberDetails(
    val nickname: String? = null,
    val personality: Long? = null,
    val gender: Int? = null,
    val natureId: Int? = null,
    val heldItemId: Int? = null,
    val friendship: Int? = null,
    val abilitySlot: Int? = null,
    val abilityId: Int? = null,
    val currentHp: Int? = null,
    val maximumHp: Int? = null,
    val status: Long? = null,
    /** Maximum HP followed by Attack, Defense, Speed, Special Attack, and Special Defense. */
    val stats: List<Int> = emptyList(),
    val moveIds: List<Int> = emptyList(),
    val movePp: List<Int> = emptyList(),
    val movePpBonuses: List<Int> = emptyList(),
    val experienceProgress: Double? = null,
) {
    init {
        require(nickname == null || nickname.isNotBlank()) { "nickname must not be blank when present" }
        require(personality == null || personality in 0..0xFFFF_FFFFL) { "personality must fit in 32 bits" }
        require(gender == null || gender in 0..2) { "Pokémon gender must be male, female, or genderless" }
        require(natureId == null || natureId in 0..24) { "nature ID must be in 0..24" }
        require(heldItemId == null || heldItemId > 0) { "held item ID must be positive" }
        require(friendship == null || friendship in 0..0xFF) { "friendship must fit in 8 bits" }
        require(abilitySlot == null || abilitySlot in 0..1) { "ability slot must be 0 or 1" }
        require(abilityId == null || abilityId > 0) { "ability ID must be positive" }
        require((currentHp == null) == (maximumHp == null)) { "current and maximum HP must be present together" }
        require(currentHp == null || currentHp >= 0) { "current HP must not be negative" }
        require(maximumHp == null || maximumHp > 0) { "maximum HP must be positive" }
        require(currentHp == null || maximumHp == null || currentHp <= maximumHp) {
            "current HP must not exceed maximum HP"
        }
        require(status == null || status in 0..0xFFFF_FFFFL) { "status must fit in 32 bits" }
        require(stats.isEmpty() || stats.size == STAT_SLOT_COUNT) { "party stats must contain six values" }
        require(stats.all { it > 0 }) { "party stats must be positive" }
        require(moveIds.size == movePp.size) { "move and PP slots must align" }
        require(movePpBonuses.isEmpty() || movePpBonuses.size == moveIds.size) {
            "PP bonus slots must align with move slots"
        }
        require(moveIds.isEmpty() || moveIds.size == MOVE_SLOT_COUNT) { "party moves must contain four slots" }
        require(moveIds.all { it >= 0 }) { "move IDs must not be negative" }
        require(movePp.all { it >= 0 }) { "move PP must not be negative" }
        require(movePpBonuses.all { it in 0..3 }) { "PP bonuses must be in 0..3" }
        require(moveIds.indices.all { moveIds[it] != 0 || movePp[it] == 0 }) {
            "empty move slots must have zero PP"
        }
        require(experienceProgress == null || experienceProgress in 0.0..1.0) {
            "experience progress must be in 0.0..1.0"
        }
    }

    private companion object {
        const val STAT_SLOT_COUNT = 6
        const val MOVE_SLOT_COUNT = 4
    }
}

enum class BagPocket { ITEMS, KEY_ITEMS, BALLS, TM_HM, BERRIES }

data class BagEntry(val itemId: Int, val quantity: Int) {
    init {
        require(itemId > 0) { "bag item ID must be positive" }
        require(quantity > 0) { "bag item quantity must be positive" }
    }
}

data class BagPocketSnapshot(
    val pocket: BagPocket,
    val entries: List<BagEntry>,
) {
    init {
        require(entries.map(BagEntry::itemId).distinct().size == entries.size) {
            "bag pocket must not contain duplicate item IDs"
        }
    }
}
