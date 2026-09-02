package com.enrpau.dualscreendex.parser.dataset.natures

enum class NatureStat {
    ATTACK,
    DEFENSE,
    SPEED,
    SPECIAL_ATTACK,
    SPECIAL_DEFENSE,
}

enum class NatureFlavor {
    SPICY,
    DRY,
    SWEET,
    BITTER,
    SOUR,
}

data class NatureRecord(
    val id: Int,
    val name: String?,
    val statModifiers: List<Int>,
    val positivePercent: Int,
    val negativePercent: Int,
    val flavorModifiers: List<Int>? = null,
) {
    init {
        require(id >= 0) { "Nature ID must be non-negative" }
        require(name == null || name.isNotBlank()) { "Nature name must not be blank" }
        require(statModifiers.size == NatureStat.entries.size) { "Nature stat row must have five values" }
        require(statModifiers.all { it in -1..1 }) { "Nature stat modifiers must be -1, 0, or 1" }
        require(statModifiers.count { it > 0 } <= 1 && statModifiers.count { it < 0 } <= 1) {
            "Nature stat row may raise and lower at most one stat"
        }
        require(positivePercent > 100) { "positive Nature multiplier must exceed 100 percent" }
        require(negativePercent in 1..99) { "negative Nature multiplier must be below 100 percent" }
        require(flavorModifiers == null || flavorModifiers.size == NatureFlavor.entries.size) {
            "Nature flavor row must have five values"
        }
        require(flavorModifiers == null || flavorModifiers.all { it in -1..1 }) {
            "Nature flavor modifiers must be -1, 0, or 1"
        }
        require(flavorModifiers == null || (
            flavorModifiers.count { it > 0 } <= 1 && flavorModifiers.count { it < 0 } <= 1
        )) { "Nature flavor row may like and dislike at most one flavor" }
    }

    val raisedStat: NatureStat?
        get() = statModifiers.indexOfFirst { it > 0 }.takeIf { it >= 0 }?.let(NatureStat.entries::get)

    val loweredStat: NatureStat?
        get() = statModifiers.indexOfFirst { it < 0 }.takeIf { it >= 0 }?.let(NatureStat.entries::get)

    val likedFlavor: NatureFlavor?
        get() = flavorModifiers?.indexOfFirst { it > 0 }?.takeIf { it >= 0 }?.let(NatureFlavor.entries::get)

    val dislikedFlavor: NatureFlavor?
        get() = flavorModifiers?.indexOfFirst { it < 0 }?.takeIf { it >= 0 }?.let(NatureFlavor.entries::get)

    fun multiplierPercent(stat: NatureStat): Int = when (statModifiers[stat.ordinal]) {
        1 -> positivePercent
        -1 -> negativePercent
        else -> 100
    }
}

data class NatureCatalog(
    val records: List<NatureRecord>,
    val nameTableOffset: Int?,
    val statTableOffset: Int,
    val flavorTableOffset: Int? = null,
) {
    init {
        require(records.isNotEmpty()) { "Nature catalog must not be empty" }
        require(records.map(NatureRecord::id) == records.indices.toList()) {
            "Nature records must preserve a dense ROM-native ID domain"
        }
        require(nameTableOffset == null || nameTableOffset >= 0) { "Nature name root must be non-negative" }
        require(statTableOffset >= 0) { "Nature stat root must be non-negative" }
        require(flavorTableOffset == null || flavorTableOffset >= 0) { "Nature flavor root must be non-negative" }
    }
}

sealed interface NatureResolution {
    data class Resolved(val catalog: NatureCatalog) : NatureResolution
    data class Unavailable(val reason: String) : NatureResolution
    data class Ambiguous(val candidates: Int) : NatureResolution
    data class BudgetExceeded(val reason: String) : NatureResolution
}
